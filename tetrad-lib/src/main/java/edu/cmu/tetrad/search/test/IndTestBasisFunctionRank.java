package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Basis-function Conditional Independence test using a Rank/CCA statistic (Bartlett–Wilks).
 * Works from the embedded covariance matrix produced by Embedding.getEmbeddedData(...).
 *
 * H0: rank( X ⟂̸ Y | Z ) ≤ 0  (i.e., canonical correlations are all zero after conditioning)
 *
 * Implementation notes (unchanged externally):
 * - Conditionalization and whitening are now delegated to RankTests where possible.
 */
public class IndTestBasisFunctionRank implements IndependenceTest, RawMarginalIndependenceTest {

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;

    private final SimpleMatrix covarianceMatrix; // embedded covariance
    private final int sampleSize;

    private final Map<Integer, List<Integer>> embedding;
    private final double lambda;            // kept for API compatibility; RankTests handles its own ridge internally
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
                                    double lambda,               // kept for signature symmetry
                                    SimpleMatrix S,              // embedded covariance
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

        // 2) Delegate to RankTests’ partial-CCA p-value (Wilks/Bartlett, r=0)
        //    This encapsulates conditioning on Z (Schur complements), whitening, SVD, and chi-square approx.
        double pValue = RankTests.pValueIndepConditioned(S, xi, yi, zi, n);

        // Clip for safety (same behavior as original)
        if (Double.isNaN(pValue)) return 1.0;
        return Math.max(0.0, Math.min(1.0, pValue));
    }
}