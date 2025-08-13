package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

/**
 * BasisFunctionRankBic: BIC-style local score after Legendre embedding, using reduced-rank (canonical correlation) fit
 * between the target block and the concatenated parent blocks.
 * <p>
 * For scalar targets with 1 basis column and r in {0,1}, this collapses to SEM-BIC.
 */
public class BasisFunctionRankBicScore implements Score {
    // Data / bookkeeping
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeIndex;
    private final Map<Integer, List<Integer>> embedding; // map original-col -> embedded column indices
    private final SimpleMatrix Sphi;                      // covariance of embedded data
    private final int n;                                  // sample size

    // Knobs
    private double penaltyDiscount = 1.0;                 // c
    private double ridge = 1e-8;                          // whitening ridge
    private boolean doOneEquationOnly = false;            // use only first basis column of Y

    public BasisFunctionRankBicScore(DataSet dataSet, int truncationLimit) {
        this.dataSet = Objects.requireNonNull(dataSet);

        // index original variables
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.nodeIndex = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) nodeIndex.put(variables.get(j), j);

        this.n = dataSet.getNumRows();

        // Legendre embedding (same settings you used elsewhere)
        Embedding.EmbeddedData ed = Embedding.getEmbeddedData(dataSet, truncationLimit, /*basisType*/1, /*basisScale*/1);
        this.embedding = ed.embedding();

        // covariance in embedded space
        this.Sphi = DataUtils.cov(ed.embeddedData().getDoubleData().getSimpleMatrix());
    }

    // --- Public API (mirrors SemBIC-style usage) ---

    /**
     * Extract block S[rows, cols].
     */
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) out.set(i, j, S.get(ri, cols[j]));
        }
        return out;
    }

    /**
     * Symmetric PSD inverse square root with ridge.
     */
    private static SimpleMatrix invSqrtPSD(SimpleMatrix A, double ridge) {
        SimpleMatrix As = A.plus(A.transpose()).scale(0.5);
        for (int i = 0; i < As.numRows(); i++) As.set(i, i, As.get(i, i) + ridge);
        SimpleEVD<SimpleMatrix> evd = As.eig();
        int d = As.numRows();
        SimpleMatrix V = new SimpleMatrix(d, d), Dinvh = new SimpleMatrix(d, d);
        for (int i = 0; i < d; i++) {
            double eig = Math.max(1e-12, evd.getEigenvalue(i).getReal());
            Dinvh.set(i, i, 1.0 / Math.sqrt(eig));
            var vi = evd.getEigenVector(i);
            if (vi == null) throw new IllegalStateException("Null eigenvector");
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

    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> _parents = new ArrayList<>();
        for (int parent : parents) _parents.add(variables.get(parent));
        return localScore(y, _parents);
    }

    /**
     * Local BF-RankBIC score for Y given parents.
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = idx(y);
        int[] Yblock = blockFor(yi, /*oneEq*/ doOneEquationOnly);
        if (parents.isEmpty()) return 0.0; // null model baseline (constants cancel in deltas)

        int[] Xblock = concatBlocks(parents);

        // Syy, Sxx, Syx in embedded space (no extra Z; parents define the conditioning set)
        SimpleMatrix Syy = block(Sphi, Yblock, Yblock);
        SimpleMatrix Sxx = block(Sphi, Xblock, Xblock);
        SimpleMatrix Syx = block(Sphi, Yblock, Xblock);

        // Whiten & SVD to get canonical correlations
        SimpleMatrix Wyy = invSqrtPSD(Syy, ridge);
        SimpleMatrix Wxx = invSqrtPSD(Sxx, ridge);
        SimpleMatrix M = Wyy.mult(Syx).mult(Wxx);
        SimpleSVD<SimpleMatrix> svd = M.svd();

        int q = Syy.numRows(), p = Sxx.numRows(), m = Math.min(p, q);
        double[] rho = new double[m];
        for (int i = 0; i < m; i++) rho[i] = clamp01(svd.getSingleValue(i));

        // prefix of fit term: -n * sum log(1 - rho^2)
        double[] prefix = new double[m + 1];
        for (int i = 0; i < m; i++) {
            double oneMinus = Math.max(1e-16, 1.0 - rho[i] * rho[i]);
            prefix[i + 1] = prefix[i] - n * Math.log(oneMinus);
        }

        // scan rank r
        double best = 0.0;
        for (int r = 0; r <= m; r++) {
            int k = r * (p + q - r);                 // reduced-rank params
            double fit = prefix[r];
            double pen = penaltyDiscount * k * Math.log(n);
            double score = fit - pen;
            if (r == 0 || score > best) best = score;
        }
        return best;
    }

    /**
     * Optional: delta score helper (add/remove a single parent).
     */
    public double localScoreDelta(Node y, List<Node> oldParents, Node changedParent, boolean adding) {
        List<Node> newParents = new ArrayList<>(oldParents);
        if (adding) newParents.add(changedParent);
        else newParents.remove(changedParent);
        return localScore(y, newParents) - localScore(y, oldParents);
    }

    // --- Settings ---
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
    }

    public void setRidge(double ridge) {
        this.ridge = ridge;
    }

    public void setDoOneEquationOnly(boolean v) {
        this.doOneEquationOnly = v;
    }

    // --- Internals ---
    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    /**
     * Embedded columns for an original variable.
     */
    private int[] blockFor(int originalCol, boolean firstOnly) {
        List<Integer> cols = new ArrayList<>(embedding.get(originalCol));
        if (firstOnly && !cols.isEmpty()) cols = cols.subList(0, 1);
        return cols.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Concatenate embedded columns for all parents.
     */
    private int[] concatBlocks(List<Node> parents) {
        List<Integer> all = new ArrayList<>();
        for (Node p : parents) {
            for (int c : blockFor(idx(p), /*firstOnly*/ false)) all.add(c);
        }
        return all.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }
}