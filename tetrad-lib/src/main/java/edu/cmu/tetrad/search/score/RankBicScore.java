package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

/**
 * RankBicScore: BIC-style local score based on (partial) canonical correlations.
 * <p>
 * For a scalar target Y with parent set X, this collapses to the usual SEM-BIC: r in {0,1}, rho^2 = multiple R^2(Y ~
 * X), score = max{ 0,  -n * log(1 - rho^2) - c * p * log n }.
 * <p>
 * For block targets (q > 1), it uses reduced-rank regression BIC: RBIC(Y|X) = max_{0<=r<=min(p,q)} { -n * sum_{i=1..r}
 * log(1 - rho_i^2) - c * r (p + q - r) log n }.
 * <p>
 * All computations are from the sample covariance; conditioning on existing parents is implicit in the local score
 * definition (Y | Pa(Y)).
 */
public class RankBicScore implements Score {

    // --- data & bookkeeping ---
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> idx;
    private final SimpleMatrix S;   // covariance matrix over variables()
    private final int n;            // sample size

    // --- knobs ---
    private double penaltyDiscount = 1.0; // c in your convention
    private double ridge = 1e-8;          // small ridge for whitening inverses

    public RankBicScore(DataSet dataSet) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.idx = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) idx.put(variables.get(j), j);
        this.n = dataSet.getNumRows();

        // Use covariance (not correlation) to match SEM-BIC behavior
        this.S = new CovarianceMatrix(dataSet).getMatrix().getSimpleMatrix();
    }

    /**
     * Optional: construct directly from covariance and matching variable list.
     */
    public RankBicScore(SimpleMatrix covariance, List<Node> variables, int sampleSize) {
        this.dataSet = null;
        this.variables = new ArrayList<>(variables);
        this.idx = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) idx.put(variables.get(j), j);
        this.S = covariance;
        this.n = sampleSize;
    }

    // ------------------------------------------------------------------------
    // Public API (mirrors SemBicScore-style usage)
    // ------------------------------------------------------------------------

    /**
     * Extract block S[rows, cols].
     */
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

    /**
     * Symmetric PSD inverse square root via eigendecomposition with ridge.
     */
    private static SimpleMatrix invSqrtPSD(SimpleMatrix A, double ridge) {
        // Symmetrize
        SimpleMatrix As = A.plus(A.transpose()).scale(0.5);
        // Ridge
        int d = As.numRows();
        for (int i = 0; i < d; i++) {
            As.set(i, i, As.get(i, i) + ridge);
        }
        SimpleEVD<SimpleMatrix> evd = As.eig();

        SimpleMatrix V = new SimpleMatrix(d, d);
        SimpleMatrix Dinvh = new SimpleMatrix(d, d);
        for (int i = 0; i < d; i++) {
            double eig = Math.max(1e-12, evd.getEigenvalue(i).getReal());
            double invh = 1.0 / Math.sqrt(eig);
            Dinvh.set(i, i, invh);
            SimpleMatrix vi = evd.getEigenVector(i);
            if (vi == null) throw new IllegalStateException("Null eigenvector in invSqrtPSD");
            for (int r = 0; r < d; r++) V.set(r, i, vi.get(r, 0));
        }
        return V.mult(Dinvh).mult(V.transpose());
    }

    private static double clamp01(double x) {
        if (!Double.isFinite(x)) return 0.0;
        if (x < 0) return 0.0;
        if (x > 1) return 1.0;
        return x;
    }

    /**
     * Local score for Y given parent set Pa.
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = indexOf(y);
        int[] Xi = toIndices(parents);

        // Empty parents → null model score = 0 (constant terms drop in deltas)
        if (Xi.length == 0) return 0.0;

        // Blocks (no extra Z beyond the local-parent definition)
        SimpleMatrix Syy = block(S, new int[]{yi}, new int[]{yi});         // q=1 typical
        SimpleMatrix Sxx = block(S, Xi, Xi);
        SimpleMatrix Syx = block(S, new int[]{yi}, Xi);                    // 1 x p

        // Whiten and SVD to get canonical correlations
        SimpleMatrix Wyy = invSqrtPSD(Syy, ridge);
        SimpleMatrix Wxx = invSqrtPSD(Sxx, ridge);
        SimpleMatrix M = Wyy.mult(Syx).mult(Wxx);                        // q x p
        SimpleSVD<SimpleMatrix> svd = M.svd();
        int m = Math.min(M.numRows(), M.numCols());

        // Collect singular values (canonical correlations)
        double[] rho = new double[m];
        for (int i = 0; i < m; i++) {
            rho[i] = clamp01(svd.getSingleValue(i));
        }

        // Prefix sums of -n * log(1 - rho^2)
        double[] prefix = new double[m + 1];
        for (int i = 0; i < m; i++) {
            double oneMinus = Math.max(1e-16, 1.0 - rho[i] * rho[i]);
            prefix[i + 1] = prefix[i] - n * Math.log(oneMinus);
        }

        // Scan ranks r = 0..m: score_r = fit - penalty
        double best = 0.0;
        int p = Sxx.numRows(), q = Syy.numRows();
        for (int r = 0; r <= m; r++) {
            int k = r * (p + q - r);                  // reduced-rank parameter count
            double fit = prefix[r];                   // -n * sum_{i<=r} log(1 - rho_i^2)
            double pen = penaltyDiscount * k * Math.log(n);
            double score = fit - pen;
            if (r == 0 || score > best) best = score;
        }
        return best;
    }

    // Convenience: local score by indices (if you have your own Node bookkeeping)
    public double localScore(int yIndex, int[] parentIndices) {
        return localScore(variables.get(yIndex), toNodes(parentIndices));
    }

    // ------------------------------------------------------------------------
    // Getters / setters
    // ------------------------------------------------------------------------
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
    }

    public double getRidge() {
        return ridge;
    }

    public void setRidge(double ridge) {
        this.ridge = ridge;
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    public int getSampleSize() {
        return n;
    }

    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    public SimpleMatrix getCovariance() {
        return S;
    }

    private int indexOf(Node v) {
        Integer i = idx.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node: " + v);
        return i;
    }

    private int[] toIndices(List<Node> nodes) {
        int[] out = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) out[i] = indexOf(nodes.get(i));
        return out;
    }

    private List<Node> toNodes(int[] indices) {
        List<Node> out = new ArrayList<>(indices.length);
        for (int i : indices) out.add(variables.get(i));
        return out;
    }
}