package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mimbuild over the first principal components of (pure) clusters. Constructor matches IndTestBlocks: (dataSet, blocks,
 * blockVariables). Each cluster's latent is PC1 of its standardized indicators; latent scores are re-scaled to unit
 * variance. A latent–latent covariance is built using (n-1) and a tiny ridge, then BOSS (+PermutationSearch) scores
 * it.
 */
public class MimbuildPca {

    private final DataSet dataSet;                 // observed data
    private final List<List<Integer>> blocks;      // clusters as column indices in dataSet
    private final List<Node> blockVariables;       // latent nodes corresponding to blocks (size must match)

    private double penaltyDiscount = 1.0;

    private List<Node> latents;                    // latent nodes (cloned with LATENT type)

    /**
     * Constructor in the same form as IndTestBlocks.
     *
     * @param dataSet        observed data
     * @param blocks         clusters as lists of column indices into dataSet
     * @param blockVariables latent variables (size must equal blocks.size())
     */
    public MimbuildPca(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (blocks == null) throw new IllegalArgumentException("blocks == null");
        if (blockVariables == null) throw new IllegalArgumentException("blockVariables == null");

        final int B = blocks.size();
        if (blockVariables.size() != B) {
            throw new IllegalArgumentException("#blockVariables (" + blockVariables.size() + ") != #blocks (" + B + ")");
        }

        // Validate columns
        final int D = dataSet.getNumColumns();
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blocks.get(b);
            if (cols == null || cols.isEmpty()) {
                throw new IllegalArgumentException("Block " + b + " is null or empty.");
            }
            for (int c : cols) {
                if (c < 0 || c >= D) {
                    throw new IllegalArgumentException("Block " + b + " references column " + c + " outside dataset width " + D);
                }
            }
        }

        // Validate nodes are non-null and distinct
        for (int i = 0; i < blockVariables.size(); i++) {
            Node v = blockVariables.get(i);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + i + "] is null");
            for (int j = i + 1; j < blockVariables.size(); j++) {
                if (Objects.equals(v, blockVariables.get(j))) {
                    throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
                }
            }
        }

        this.dataSet = dataSet;
        this.blocks = new ArrayList<>(blocks);
        this.blockVariables = new ArrayList<>(blockVariables);
    }

    private static void standardizeColumnsInPlace(SimpleMatrix X) {
        int n = X.getNumRows();
        int p = X.getNumCols();
        for (int j = 0; j < p; j++) {
            double mean = 0, m2 = 0;
            for (int i = 0; i < n; i++) mean += X.get(i, j);
            mean /= n;
            for (int i = 0; i < n; i++) {
                double d = X.get(i, j) - mean;
                m2 += d * d;
            }
            double sd = Math.sqrt(m2 / Math.max(1, n - 1));
            if (!Double.isFinite(sd) || sd == 0.0) sd = 1.0;
            for (int i = 0; i < n; i++) {
                X.set(i, j, (X.get(i, j) - mean) / sd);
            }
        }
    }

    // --- Helpers ---

    /**
     * Run PCA per block to get latent scores, build latent covariance, and learn a structure over latents using BOSS.
     * @return the learned latent structure graph.
     * @throws InterruptedException if the search is interrupted.
     */
    public Graph search() throws InterruptedException {
        final int nSamples = dataSet.getNumRows();
        final int nLatents = blocks.size();

        // Prepare latent node list (typed as LATENT)
        this.latents = new ArrayList<>(nLatents);
        for (Node src : blockVariables) {
            GraphNode node = new GraphNode(src.getName());
            node.setNodeType(NodeType.LATENT);
            this.latents.add(node);
        }

        // Build latent scores (PC1 per standardized block), unit-variance per latent
        // n x B matrix of PC1 scores (unit variance)
        double[][] latentData = new double[nSamples][nLatents];
        for (int bi = 0; bi < nLatents; bi++) {
            List<Integer> cols = blocks.get(bi);

            // Extract X (n x p_b)
            SimpleMatrix X = new SimpleMatrix(nSamples, cols.size());
            for (int r = 0; r < nSamples; r++) {
                for (int c = 0; c < cols.size(); c++) {
                    X.set(r, c, dataSet.getDouble(r, cols.get(c)));
                }
            }

            // z-score within each block (standardize indicators)
            standardizeColumnsInPlace(X);

            // PC1 score vector: X * v1
            SimpleSVD<SimpleMatrix> svd = X.svd();
            SimpleMatrix v1 = svd.getV().extractVector(false, 0);
            SimpleMatrix pc1 = X.mult(v1); // (n x 1)

            // Stable sign (make average loading positive)
            double sign = 0.0;
            for (int c = 0; c < v1.getNumRows(); c++) sign += v1.get(c);
            if (sign < 0) pc1 = pc1.negative();

            // Unit variance per latent
            double mean = 0, m2 = 0;
            for (int r = 0; r < nSamples; r++) mean += pc1.get(r);
            mean /= nSamples;
            for (int r = 0; r < nSamples; r++) {
                double d = pc1.get(r) - mean;
                m2 += d * d;
            }
            double sd = Math.sqrt(m2 / Math.max(1, nSamples - 1));
            if (!Double.isFinite(sd) || sd == 0.0) sd = 1.0;

            for (int r = 0; r < nSamples; r++) {
                latentData[r][bi] = (pc1.get(r) - mean) / sd;
            }
        }

        // Covariance of latents with (n-1) divisor + tiny ridge for SPD
        SimpleMatrix L = new SimpleMatrix(latentData);              // n x B
        int dof = Math.max(1, nSamples - 1);
        SimpleMatrix latentCov = L.transpose().mult(L).divide(dof); // B x B

        double eps = 1e-8;
        for (int k = 0; k < nLatents; k++) {
            latentCov.set(k, k, latentCov.get(k, k) + eps);
        }

        // covariance over latents
        ICovarianceMatrix latentsCov = new edu.cmu.tetrad.data.CovarianceMatrix(latents, new Matrix(latentCov), nSamples);

        // Learn structure over latents
        SemBicScore score = new SemBicScore(latentsCov);
        score.setPenaltyDiscount(this.penaltyDiscount);

        PermutationSearch ps = new PermutationSearch(new Boss(score));
        // learned latent structure
        Graph structureGraph = ps.search();
        LayoutUtil.fruchtermanReingoldLayout(structureGraph);
        return structureGraph;
    }

    /**
     * Sets the penalty discount value, which is used as a parameter to adjust the penalty applied during the structure
     * learning process over latents.
     *
     * @param penaltyDiscount the penalty discount value to set
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Returns a list of latent variables. If no latent variables are defined, the method returns null. Otherwise, it
     * returns a new list as a copy of the existing latent variables to avoid exposing internal references.
     *
     * @return a list of latent variables, or null if no latent variables are defined
     */
    public List<Node> getLatents() {
        return latents == null ? null : new ArrayList<>(latents);
    }

}