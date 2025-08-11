package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

public class RankConditionalIndependenceTest implements IndependenceTest {

    // numerics
    private static final double RIDGE = 1e-10;
//    private static final double MIN_EIG = 1e-12;
    private final ICovarianceMatrix covMatrix;
    private final List<Node> variables;
    private final Map<Node, Integer> idx;
    private final SimpleMatrix S; // full covariance/correlation
    private boolean verbose = false;
    // RCCA params (used only for the diagnostic rank)
    private final double alpha;
    private final double regLambda;
    private final double condThreshold;
    // rank-0 detection tolerance
//    private final double smaxTol = .1;   // tweak: singular-value threshold after whitening

    public RankConditionalIndependenceTest(ICovarianceMatrix covMatrix,
                                           double alpha, double regLambda, double condThreshold) {
        this.covMatrix = new CorrelationMatrix(covMatrix);
        this.variables = this.covMatrix.getVariables();
        this.idx = indexMap(variables);
        this.S = this.covMatrix.getMatrix().getSimpleMatrix();
        this.alpha = alpha;
        this.regLambda = regLambda;
        this.condThreshold = condThreshold;
    }

    private static Map<Node, Integer> indexMap(List<Node> vars) {
        Map<Node, Integer> m = new HashMap<>(vars.size() * 2);
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i), i);
        return m;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ---------- Helpers ----------

//    /** Decide if the cross-block S_xy|Z is numerically rank-0 (i.e., all canonical cors ~ 0). */
//    private static boolean isRankZeroCrossBlock(SimpleMatrix Scond, int[] xLoc, int[] yLoc, double tol) {
//        // Partition
//        SimpleMatrix Sxx = block(Scond, xLoc, xLoc);
//        SimpleMatrix Syy = block(Scond, yLoc, yLoc);
//        SimpleMatrix Sxy = block(Scond, xLoc, yLoc);
//
//        // Whiten: M = Sxx^{-1/2} * Sxy * Syy^{-1/2}
//        SimpleMatrix SxxInvSqrt = invSqrtPSD(Sxx);
//        SimpleMatrix SyyInvSqrt = invSqrtPSD(Syy);
//        SimpleMatrix M = SxxInvSqrt.mult(Sxy).mult(SyyInvSqrt);
//
//        // Largest singular value
//        SimpleSVD<SimpleMatrix> svd = M.svd();
//        double smax = 0.0;
//        double[] singularValues = svd.getSingularValues();
//        for (double s : singularValues) smax = Math.max(smax, s);
//
//        return smax <= tol;
//    }

    private static int[] range(int startInclusive, int endExclusive) {
        int[] r = new int[endExclusive - startInclusive];
        for (int i = 0; i < r.length; i++) r[i] = startInclusive + i;
        return r;
    }

    // Extract block S[rows, cols]
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(ri, cols[j]));
            }
        }
        return out;
    }

    // Solve A X = B with tiny ridge fallback
    private static SimpleMatrix safeSolve(SimpleMatrix A, SimpleMatrix B) {
        try {
            return A.solve(B);
        } catch (RuntimeException e) {
            SimpleMatrix Areg = A.copy();
            int n = Math.min(Areg.getNumRows(), Areg.getNumCols());
            for (int i = 0; i < n; i++) {
                Areg.set(i, i, Areg.get(i, i) + RIDGE);
            }
            return Areg.solve(B);
        }
    }

//    /**
//     * If you want to tighten/loosen the rank-0 decision.
//     */
//    public void setSmaxTolerance(double tol) {
//        this.smaxTol = tol;
//    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        final int n = covMatrix.getSampleSize();

        // map nodes to indices
        int[] xIdxOrig = new int[]{idx.get(x)};
        int[] yIdxOrig = new int[]{idx.get(y)};
        int[] zIdxOrig = (z == null || z.isEmpty())
                ? new int[0]
                : z.stream().map(idx::get).mapToInt(Integer::intValue).toArray();

        // Build S_VV|Z  (V = X ∪ Y)
        int[] vIdx = concat(xIdxOrig, yIdxOrig);
        SimpleMatrix Scond; // |V|×|V|

        if (zIdxOrig.length == 0) {
            Scond = block(S, vIdx, vIdx);
        } else {
            SimpleMatrix S_VV = block(S, vIdx, vIdx);
            SimpleMatrix S_VZ = block(S, vIdx, zIdxOrig);
            SimpleMatrix S_ZZ = block(S, zIdxOrig, zIdxOrig);
            SimpleMatrix S_ZV = S_VZ.transpose();
            // Schur complement: S_VV - S_VZ * (S_ZZ \ S_ZV)
            SimpleMatrix solved = safeSolve(S_ZZ, S_ZV);
            Scond = S_VV.minus(S_VZ.mult(solved));
        }

        // Local indices for X and Y inside Scond
        int[] xLoc = range(0, xIdxOrig.length);
        int[] yLoc = range(xIdxOrig.length, xIdxOrig.length + yIdxOrig.length);

        // --- Step 1: dedicated rank-0 test via whitening + SVD ---
//        boolean independent = isRankZeroCrossBlock(Scond, xLoc, yLoc, smaxTol);
//        boolean independent = independentByWilks(Scond, xLoc, yLoc, n, alpha);

        // --- Step 2: optional diagnostic rank from your RCCA (never returns 0) ---
//        int estRank = -1;
//        try {
        int estRank = RankTests.estimateRccaRank(Scond, xLoc, yLoc, n, alpha, regLambda, condThreshold);
//        } catch (Throwable t) {
//             ignore; keep estRank = -1 as "unknown"
//        }

        boolean independent = estRank == 0;

        if (verbose) {
            System.out.printf(Locale.ROOT,
                    "Hybrid RCCA: X=%s Y=%s |Z|=%d -> indep=%s, rccaRank=%d%n",
                    x.getName(), y.getName(), zIdxOrig.length, independent, estRank);
        }

        // pValue unknown; score uses estRank (or 0 if independent by our rank-0 detector)
        double score = independent ? 0.0 : (estRank >= 0 ? estRank : 1.0);
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, Double.NaN, score);
    }

//    // Symmetric PSD inverse square root with eigen floor + ridge
//    private static SimpleMatrix invSqrtPSD(SimpleMatrix A) {
//        SimpleMatrix Asym = A.plus(A.transpose()).divide(2.0); // symmetrize
//        // small ridge to avoid negative/zero eigs
//        int n = Asym.getNumRows();
//        SimpleMatrix Areg = Asym.copy();
//        for (int i=0;i<n;i++) {
//            Areg.set(i, i, Areg.get(i, i) + RIDGE);
//        }
//        SimpleEVD<SimpleMatrix> evd = Areg.eig();
//        SimpleMatrix V = new SimpleMatrix(n, n);
//        SimpleMatrix DinvSqrt = new SimpleMatrix(n, n);
//        for (int i=0;i<n;i++) {
//            double eig = Math.max(evd.getEigenvalue(i).getReal(), MIN_EIG);
//            double invs = 1.0 / Math.sqrt(eig);
//            DinvSqrt.set(i, i, invs);
//            // eigenvectors are columns of V
//            SimpleMatrix vi = evd.getEigenVector(i);
//            for (int r=0;r<n;r++) V.set(r, i, vi.get(r, 0));
//        }
//        // V * D^{-1/2} * V^T
//        return V.mult(DinvSqrt).mult(V.transpose());
//    }

//    // Decide independence by Wilks’ Lambda on the conditioned cross block.
//// H0: all canonical correlations are 0  (i.e., rank == 0)
//    private static boolean independentByWilks(SimpleMatrix Scond, int[] xLoc, int[] yLoc, int n, double alpha) {
//        SimpleMatrix Sxx = block(Scond, xLoc, xLoc);
//        SimpleMatrix Syy = block(Scond, yLoc, yLoc);
//        SimpleMatrix Sxy = block(Scond, xLoc, yLoc);
//
//        // Whiten: M = Sxx^{-1/2} * Sxy * Syy^{-1/2}
//        SimpleMatrix Wxx = invSqrtPSD(Sxx);
//        SimpleMatrix Wyy = invSqrtPSD(Syy);
//        SimpleMatrix M = Wxx.mult(Sxy).mult(Wyy);
//
//        // Canonical correlations = singular values of M (clipped to [0,1])
//        double[] s = M.svd().getSingularValues();
//        int p = Sxx.getNumRows();
//        int q = Syy.getNumRows();
//        int r = Math.min(p, q);
//
//        double lambda = 1.0;
//        for (int i = 0; i < Math.min(r, s.length); i++) {
//            double rho = Math.max(0.0, Math.min(1.0, s[i]));
//            lambda *= (1.0 - rho * rho);
//        }
//
//        // Bartlett approx: X2 = -c * ln(lambda) ~ chi2_{p*q}
//        // c = (n - 1) - 0.5*(p + q + 1). If this is too aggressive, let c = n.
//        double c = (n - 1) - 0.5 * (p + q + 1);
//        if (c < 1) c = 1; // guard small n
//        double stat = -c * Math.log(Math.max(lambda, 1e-16));
//        int df = p * q;
//
//        // p-value = 1 - CDF_chi2(stat; df)  (use your math lib)
//        // Here we return the decision; wire in your chi-square CDF where you keep stats.
//        // For a quick decision without a lib, you can fallback to a tol: stat < chi2_{1-alpha,df}.
//        // But ideally call Commons Math: new ChiSquaredDistribution(df).cumulativeProbability(stat).
//        double pval = 1.0 - new ChiSquaredDistribution(df). cumulativeProbability(stat);

    /// /        double pval = 1.0; // TODO: replace with real pval
//        boolean indep = pval > alpha;
//
//        return indep;
//    }

    // Boilerplate
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataModel getData() {
        return covMatrix;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return covMatrix;
    }

    @Override
    public String toString() {
        return "RccaHybridRankIndependenceTest";
    }
}