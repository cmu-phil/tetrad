package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Basis-function Conditional Independence test using a Rank/CCA statistic (Bartlett–Wilks).
 * Works from the embedded covariance matrix produced by Embedding.getEmbeddedData(...).
 *
 * H0: rank( X ⟂̸ Y | Z ) ≤ 0  (i.e., canonical correlations are all zero after conditioning)
 *
 * Implementation notes:
 * - Conditionalization via Schur complements on the embedded covariance (partial covariances).
 * - Whitening with eigendecomposition of S_xx.z and S_yy.z (ridge-stabilized).
 * - Bartlett’s approximation: T = -kappa * log Λ,  df = p*q  (for r=0)
 *   where Λ = ∏_{i=1..m} (1 - ρ_i^2), m = min(p, q).
 */
public class IndTestBasisFunctionRank implements IndependenceTest, RawMarginalIndependenceTest {

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;

    private final SimpleMatrix covarianceMatrix; // embedded covariance
    private final int sampleSize;

    private final Map<Integer, List<Integer>> embedding;
    private final double lambda;            // ridge/singularity guard for inverses
    private final int truncationLimit;

    private double alpha = 0.01;
    private boolean verbose = false;
    private boolean doOneEquationOnly = false;

    public IndTestBasisFunctionRank(DataSet dataSet, int truncationLimit, double lambda) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }
        this.nodeHash = nodesHash;
        this.truncationLimit = truncationLimit;
        this.lambda = lambda;

        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, truncationLimit, /*basisType*/ 1, /*basisScale*/ 1
        );
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();

        // Covariance of the (centered) embedded columns
        this.covarianceMatrix = DataUtils.cov(
                embeddedData.embeddedData().getDoubleData().getSimpleMatrix()
        );
    }

    // === Public API ===

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = pValue > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return dataSet;
    }

    @Override
    public boolean isVerbose() { return this.verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha;
    }

    /** If true, only use the first embedded column for X (mirrors LRT class option). */
    public void setDoOneEquationOnly(boolean doOneEquationOnly) { this.doOneEquationOnly = doOneEquationOnly; }

    @Override
    public double computePValue(double[] x, double[] y) {
        double[][] combined = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            combined[i][0] = x[i];
            combined[i][1] = y[i];
        }
        Node _x = new ContinuousVariable("X_computePValue");
        Node _y = new ContinuousVariable("Y_computePValue");
        List<Node> nodes = Arrays.asList(_x, _y);
        DataSet ds = new BoxDataSet(new DoubleDataBox(combined), nodes);

        IndTestBasisFunctionRank test = new IndTestBasisFunctionRank(ds, truncationLimit, lambda);
        return test.getPValue(_x, _y, Collections.emptySet());
    }

    // === Core RCCA/Bartlett test ===

    private double getPValue(Node x, Node y, Set<Node> z) {
        return getPValue(x, y, z, nodeHash, embedding, doOneEquationOnly,
                lambda, covarianceMatrix, sampleSize);
    }

    private static double getPValue(Node x, Node y, Set<Node> z,
                                    Map<Node, Integer> nodeHash,
                                    Map<Integer, List<Integer>> embedding,
                                    boolean doOneEquationOnly,
                                    double lambda,
                                    SimpleMatrix S, // embedded covariance
                                    int n) {

        // 1) Indices in the embedded space
        List<Node> zList = new ArrayList<>(z);
        Collections.sort(zList);

        int xIdx = nodeHash.get(x);
        int yIdx = nodeHash.get(y);

        List<Integer> xCols = new ArrayList<>(embedding.get(xIdx));
        if (doOneEquationOnly && !xCols.isEmpty()) {
            xCols = xCols.subList(0, 1);
        }
        List<Integer> yCols = embedding.get(yIdx);
        List<Integer> zCols = new ArrayList<>();
        for (Node zn : zList) {
            List<Integer> cols = embedding.get(nodeHash.get(zn));
            if (cols != null) zCols.addAll(cols);
        }

        int[] xi = xCols.stream().mapToInt(Integer::intValue).toArray();
        int[] yi = yCols.stream().mapToInt(Integer::intValue).toArray();
        int[] zi = zCols.stream().mapToInt(Integer::intValue).toArray();

        // Guard
        if (xi.length == 0 || yi.length == 0) return 1.0;

        // 2) Partial covariance blocks given Z via Schur complements
        Blocks blocks = partialBlocks(S, xi, yi, zi, lambda);

        // If conditioning annihilates variance numerically, treat as independent
        if (isDegenerate(blocks.Sxx_z) || isDegenerate(blocks.Syy_z)) return 1.0;

        // 3) Whiten & SVD for canonical correlations
        SimpleMatrix Sxx_invhalf = invSqrtPSD(blocks.Sxx_z, lambda);
        SimpleMatrix Syy_invhalf = invSqrtPSD(blocks.Syy_z, lambda);

        SimpleMatrix M = Sxx_invhalf.mult(blocks.Sxy_z).mult(Syy_invhalf);
        // singular values are canonical correlations
        org.ejml.simple.SimpleSVD<SimpleMatrix> svd = M.svd();
        double[] s = svd.getSingularValues();

        int p = blocks.Sxx_z.numRows();
        int q = blocks.Syy_z.numRows();
        int m = Math.min(p, q);
        if (m == 0) return 1.0;

        // 4) Wilks’ Lambda for rank ≤ 0 (independence): Λ = ∏ (1 - ρ_i^2)
        double lambdaWilks = 1.0;
        for (int i = 0; i < Math.min(m, s.length); i++) {
            double term = Math.max(1e-15, 1.0 - s[i]*s[i]);
            lambdaWilks *= term;
        }

        // 5) Bartlett approx: T = -kappa * log Λ, df = p*q
        double kappa = n - 1 - 0.5 * (p + q + 1);
        if (kappa < 1.0) kappa = Math.max(1.0, n - 1); // small-sample guard

        double T = -kappa * Math.log(Math.max(lambdaWilks, 1e-15));
        int df = p * q;
        if (df == 0) return 1.0;

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double pValue = 1.0 - chi2.cumulativeProbability(T);

        return clip01(pValue);
    }

    // === Helpers ===

    private static class Blocks {
        final SimpleMatrix Sxx_z, Syy_z, Sxy_z;
        Blocks(SimpleMatrix Sxx_z, SimpleMatrix Syy_z, SimpleMatrix Sxy_z) {
            this.Sxx_z = Sxx_z; this.Syy_z = Syy_z; this.Sxy_z = Sxy_z;
        }
    }

    /**
     * Compute partial covariance blocks given Z:
     * Sxx.z = Sxx - Sxz Szz^{-1} Szx, etc.
     * Uses ridge lambda inside the inverse via Matrix.chooseInverse(lambda).
     */
    private static Blocks partialBlocks(SimpleMatrix S, int[] xi, int[] yi, int[] zi, double lambda) {
        SimpleMatrix Sxx = StatUtils.extractSubMatrix(S, xi, xi);
        SimpleMatrix Syy = StatUtils.extractSubMatrix(S, yi, yi);
        SimpleMatrix Sxy = StatUtils.extractSubMatrix(S, xi, yi);

        if (zi.length == 0) {
            return new Blocks(Sxx, Syy, Sxy);
        }

        SimpleMatrix Sxz = StatUtils.extractSubMatrix(S, xi, zi);
        SimpleMatrix Syz = StatUtils.extractSubMatrix(S, yi, zi);
        SimpleMatrix Szz = StatUtils.extractSubMatrix(S, zi, zi);

        SimpleMatrix Szz_inv = new Matrix(Szz).chooseInverse(lambda).getData();

        SimpleMatrix Sxx_z = Sxx.minus(Sxz.mult(Szz_inv).mult(Sxz.transpose()));
        SimpleMatrix Syy_z = Syy.minus(Syz.mult(Szz_inv).mult(Syz.transpose()));
        SimpleMatrix Sxy_z = Sxy.minus(Sxz.mult(Szz_inv).mult(Syz.transpose()));

        return new Blocks(Sxx_z, Syy_z, Sxy_z);
    }

    /** Eigen-based inverse square root for symmetric PSD, ridge-stabilized. */
    private static SimpleMatrix invSqrtPSD(SimpleMatrix A, double ridge) {
        SimpleMatrix Ar = A.plus(SimpleMatrix.identity(A.numRows()).scale(Math.max(0.0, ridge)));
        org.ejml.simple.SimpleEVD<SimpleMatrix> evd = Ar.eig();
        int n = A.numRows();
        SimpleMatrix V = new SimpleMatrix(n, n);
        SimpleMatrix Dinvh = new SimpleMatrix(n, n);

        for (int i = 0; i < n; i++) {
            double val = Math.max(1e-12, evd.getEigenvalue(i).getReal());
            // eigenvector as column i
            SimpleMatrix vi = evd.getEigenVector(i);
            if (vi == null) {
                // fallback: identity if EVD fails (should be rare)
                return SimpleMatrix.identity(n);
            }
            V.insertIntoThis(0, i, vi);
            Dinvh.set(i, i, 1.0 / Math.sqrt(val));
        }
        // V * D^{-1/2} * V^T
        return V.mult(Dinvh).mult(V.transpose());
    }

    private static boolean isDegenerate(SimpleMatrix A) {
        // very small trace or NaN/Inf guard
        double tr = A.trace();
        return !(Double.isFinite(tr)) || tr < 1e-10;
    }

    private static double clip01(double p) {
        if (Double.isNaN(p)) return 1.0;
        return Math.max(0.0, Math.min(1.0, p));
    }
}