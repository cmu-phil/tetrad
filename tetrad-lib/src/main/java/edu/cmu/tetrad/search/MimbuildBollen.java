/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) ... (unchanged)                                             //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.*;

/**
 * MimbuildBollen (BlockSpec version)
 *
 * Takes a clustering of measured variables (disjoint blocks), one latent per block,
 * estimates the latent covariance via a simple ML-like objective, then learns a
 * structure over the latents using BOSS + SEM-BIC.
 *
 * Required input is a DataSet and a BlockSpec (blocks + latent nodes).
 *
 * See Spirtes et al., "Causation, Prediction, and Search".
 */
public class MimbuildBollen {

    // --- Inputs
    private final DataSet dataSet;
    private final BlockSpec blockSpec;

    // --- Outputs & knobs
    private ICovarianceMatrix latentsCov;
    private double minimum;        // objective minimum
    private double pValue;         // chi^2 p-value (fit of implied vs observed measures cov)
    private double penaltyDiscount = 1.0;

    // --- Working state for optimization
    private List<List<Integer>> blocks; // convenience view
    private List<Node> latentNodes;

    public MimbuildBollen(BlockSpec spec) {
        if (spec == null) throw new IllegalArgumentException("blockSpec == null");
        this.dataSet = spec.dataSet();
        this.blockSpec = spec;

        // Basic validation & snapshots
        this.blocks = new ArrayList<>(spec.blocks());              // List<List<Integer>>
        this.latentNodes = new ArrayList<>(spec.blockVariables()); // List<Node>

        if (blocks.isEmpty()) throw new IllegalArgumentException("No blocks in BlockSpec.");
        if (latentNodes.size() != blocks.size())
            throw new IllegalArgumentException("Latent count != block count.");

        // disjointness check
        Set<Integer> seen = new HashSet<>();
        for (List<Integer> b : blocks) {
            if (b == null || b.isEmpty()) throw new IllegalArgumentException("Empty block.");
            for (int col : b) {
                if (col < 0 || col >= dataSet.getNumColumns())
                    throw new IllegalArgumentException("Block references column out of range: " + col);
                if (!seen.add(col))
                    throw new IllegalArgumentException("Blocks must be disjoint; repeated column " + col);
            }
        }

        // unique latent names (defensive)
        Set<String> latentNames = new HashSet<>();
        for (Node L : latentNodes) {
            if (L == null) throw new IllegalArgumentException("Null latent node in BlockSpec.");
            if (!latentNames.add(L.getName()))
                throw new IllegalArgumentException("Duplicate latent name: " + L.getName());
        }
    }

    /** Run MIMBUILD: estimate latent covariance, then structure over latents using BOSS. */
    public Graph search() throws InterruptedException {
        // 1) Build measured-covariance for only variables in blocks (preserve block order)
        List<String> selectedNames = new ArrayList<>();
        for (List<Integer> b : blocks) {
            for (int col : b) selectedNames.add(dataSet.getVariable(col).getName());
        }

        ICovarianceMatrix full = new CovarianceMatrix(dataSet);
        ICovarianceMatrix measuresCov = full.getSubmatrix(selectedNames);

        // 2) Build indicators layout (Node[][]) consistent with measuresCov variable order
        List<Node> measuresVars = measuresCov.getVariables();
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < measuresVars.size(); i++) {
            nameToIdx.put(measuresVars.get(i).getName(), i);
        }

        Node[][] indicators = new Node[blocks.size()][];
        int[][] indicatorIndices = new int[blocks.size()][];

        for (int i = 0; i < blocks.size(); i++) {
            List<Integer> block = blocks.get(i);
            indicators[i] = new Node[block.size()];
            indicatorIndices[i] = new int[block.size()];

            for (int j = 0; j < block.size(); j++) {
                String varName = dataSet.getVariable(block.get(j)).getName();
                Integer idx = nameToIdx.get(varName);
                if (idx == null)
                    throw new IllegalStateException("Internal: missing var in measuresCov: " + varName);
                indicators[i][j] = measuresVars.get(idx);
                indicatorIndices[i][j] = idx;
            }
        }

        // 3) Estimate latent covariance via optimization (also estimates loadings, deltas)
        Matrix estimatedLatentCov = estimateLatentCovariance(measuresCov, indicators, indicatorIndices);

        // 4) Score & search structure over latents
        this.latentsCov = new CovarianceMatrix(latentNodes, estimatedLatentCov, measuresCov.getSampleSize());
        SemBicScore score = new SemBicScore(latentsCov);
        score.setPenaltyDiscount(this.penaltyDiscount);

        Graph g = new PermutationSearch(new Boss(score)).search();
        Graph out = new EdgeListGraph(g);
        LayoutUtil.fruchtermanReingoldLayout(out);
        return out;
    }

    /** Expose the estimated latent covariance (after search()). */
    public ICovarianceMatrix getLatentsCovariance() {
        return latentsCov;
    }

    /**
     * Retrieves the minimum value associated with the MimbuildBollen class.
     *
     * @return the minimum value as a double.
     */
    public double getMinimum() {
        return minimum;
    }

    /**
     * Retrieves the p-value associated with the MimbuildBollen class.
     *
     * @return the p-value as a double
     */
    public double getPValue() {
        return pValue;
    }

    /**
     * Sets the penalty discount value associated with the MimbuildBollen class.
     *
     * @param penaltyDiscount the penalty discount value to set, represented as a double
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    // -----------------------------------------------------------------------------------------
    // Estimation: given measures covariance, indicators, and their indices in that covariance,
    // optimize latent covariance, loadings, and unique variances (delta).
    // -----------------------------------------------------------------------------------------
    private Matrix estimateLatentCovariance(ICovarianceMatrix measuresCov,
                                            Node[][] indicators,
                                            int[][] indicatorIndices) {

        Matrix measures = measuresCov.getMatrix();
        int m = measures.getNumRows();
        int k = latentNodes.size();

        // Initialize latent covariance as identity (positive definite)
        Matrix latentCov = new Matrix(k, k);
        for (int i = 0; i < k; i++) latentCov.set(i, i, 1.0);

        // Initialize loadings to 1s within each block
        double[][] loadings = new double[k][];
        for (int i = 0; i < k; i++) {
            loadings[i] = new double[indicators[i].length];
            Arrays.fill(loadings[i], 1.0);
        }

        // Initialize unique variances (delta) from observed diagonals
        double[] delta = new double[m];
        for (int i = 0; i < m; i++) delta[i] = Math.max(1e-6, measures.get(i, i) * 0.1);

        // Optimize all parameters jointly (Powell)
        int numParams = optimizeAllParamsSimultaneously(indicators, measures, latentCov, loadings, indicatorIndices, delta);

        // Compute chi^2 p-value for fit
        double N = measuresCov.getSampleSize();
        int p = m;
        int df = p * (p + 1) / 2 - numParams;
        double x = (N - 1) * this.minimum;

        if (df < 1) {
            throw new IllegalStateException(
                    "Mimbuild error: degrees of freedom < 1. Perhaps the model isn't a proper multiple-indicator model?");
        }

        ChiSquaredDistribution chisq = new ChiSquaredDistribution(df);
        double pv;
        if (Double.isInfinite(x)) pv = 0.0;
        else if (x == 0.0) pv = 1.0;
        else pv = 1.0 - chisq.cumulativeProbability(x);
        this.pValue = pv;

        // Sanity checks
        for (int i = 0; i < k; i++) {
            double v = latentCov.get(i, i);
            if (!(v > 0.0) || Double.isNaN(v)) {
                throw new IllegalArgumentException("Non-positive variance in latent covariance at " + i + ".");
            }
        }
        for (int i = 0; i < k; i++) {
            for (int j = i; j < k; j++) {
                double v = latentCov.get(i, j);
                if (Double.isNaN(v)) throw new IllegalArgumentException("NaN in latent covariance.");
            }
        }
        return latentCov;
    }

    private int optimizeAllParamsSimultaneously(Node[][] indicators,
                                                Matrix measurescov,
                                                Matrix latentscov,
                                                double[][] loadings,
                                                int[][] indicatorIndices,
                                                double[] delta) {

        double[] values = packParams(latentscov, loadings, delta);

        Function2 objective = new Function2(latentscov, loadings, delta, indicatorIndices, measurescov);
        MultivariateOptimizer opt = new PowellOptimizer(1e-7, 1e-7);

        PointValuePair best = opt.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(objective),
                GoalType.MINIMIZE,
                new MaxEval(100000)
        );

        this.minimum = best.getValue();
        unpackParams(best.getPoint(), latentscov, loadings, delta);
        return values.length;
    }

    private double[] packParams(Matrix latentCov, double[][] loadings, double[] delta) {
        int k = latentCov.getNumRows();
        int count = 0;

        // upper-triangular latentCov (including diagonal)
        for (int i = 0; i < k; i++) for (int j = i; j < k; j++) count++;

        // loadings
        for (double[] row : loadings) count += row.length;

        // uniques
        count += delta.length;

        double[] v = new double[count];
        int t = 0;

        // latentCov (upper)
        for (int i = 0; i < k; i++) {
            for (int j = i; j < k; j++) {
                v[t++] = latentCov.get(i, j);
            }
        }

        // loadings
        for (double[] row : loadings) {
            for (double a : row) v[t++] = a;
        }

        // delta
        for (double d : delta) v[t++] = d;

        return v;
    }

    private void unpackParams(double[] v, Matrix latentCov, double[][] loadings, double[] delta) {
        int k = latentCov.getNumRows();
        int t = 0;

        // latentCov (upper) mirrored to lower
        for (int i = 0; i < k; i++) {
            for (int j = i; j < k; j++) {
                double x = v[t++];
                if (i == j && x <= 0) x = 1e-6; // enforce positivity
                latentCov.set(i, j, x);
                latentCov.set(j, i, x);
            }
        }

        // loadings
        for (int i = 0; i < loadings.length; i++) {
            for (int j = 0; j < loadings[i].length; j++) {
                loadings[i][j] = v[t++];
            }
        }

        // delta (uniques)
        for (int i = 0; i < delta.length; i++) {
            double x = v[t++];
            delta[i] = Math.max(1e-8, x);
        }
    }

    private Matrix impliedCovariance(int[][] indicatorIndices,
                                     double[][] loadings,
                                     Matrix latentCov,
                                     double[] delta,
                                     int measuresDim) {

        Matrix implied = new Matrix(measuresDim, measuresDim);

        // contribution from common factors
        for (int f1 = 0; f1 < loadings.length; f1++) {
            for (int f2 = 0; f2 < loadings.length; f2++) {
                double c = latentCov.get(f1, f2);
                if (c == 0.0) continue;
                for (int i = 0; i < loadings[f1].length; i++) {
                    int row = indicatorIndices[f1][i];
                    double li = loadings[f1][i];
                    for (int j = 0; j < loadings[f2].length; j++) {
                        int col = indicatorIndices[f2][j];
                        double lj = loadings[f2][j];
                        implied.set(row, col, implied.get(row, col) + li * lj * c);
                    }
                }
            }
        }

        // add unique variances
        for (int d = 0; d < measuresDim; d++) {
            implied.set(d, d, implied.get(d, d) + delta[d]);
        }
        return implied;
    }

    private class Function2 implements org.apache.commons.math3.analysis.MultivariateFunction {
        private final Matrix latentscov;
        private final double[][] loadings;
        private final double[] delta;
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;

        Function2(Matrix latentscov,
                  double[][] loadings,
                  double[] delta,
                  int[][] indicatorIndices,
                  Matrix measurescov) {
            this.latentscov = latentscov;
            this.loadings = loadings;
            this.delta = delta;
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.measuresCovInverse = measurescov.inverse();
        }

        @Override
        public double value(double[] params) {
            // unpack (enforce basic constraints inside)
            unpackParams(params, latentscov, loadings, delta);

            // implied Σ = Λ Φ Λ' + Ψ
            Matrix implied = impliedCovariance(indicatorIndices, loadings, latentscov, delta, measurescov.getNumRows());

            // objective: 0.5 * || I - implied * S^{-1} ||_F^2  (scale-free)
            Matrix I = Matrix.identity(implied.getNumRows());
            Matrix diff = I.minus(implied.times(measuresCovInverse));
            double obj = 0.5 * (diff.times(diff)).trace();

            if (Double.isNaN(obj) || Double.isInfinite(obj)) return Double.POSITIVE_INFINITY;
            return obj;
        }
    }
}