package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

/**
 * Mimbuild over the first principal components of (pure) clusters, driven by a BlockSpec.
 * Each cluster's latent is PC1 of its standardized indicators; latent scores are re-scaled
 * to unit variance. A latent–latent covariance is built using (n-1) and a tiny ridge,
 * then BOSS (+PermutationSearch) learns a structure over the latents using SEM-BIC.
 */
public class MimbuildPca {

    private final BlockSpec blockSpec;

    private double penaltyDiscount = 1.0;

    // output
    private ICovarianceMatrix latentsCovariance;  // covariance over latents (after search)

    public MimbuildPca(BlockSpec blockSpec) {
        if (blockSpec == null) throw new IllegalArgumentException("blockSpec == null");
        if (blockSpec.blocks() == null || blockSpec.blocks().isEmpty())
            throw new IllegalArgumentException("BlockSpec has no blocks.");
        if (blockSpec.blockVariables() == null
            || blockSpec.blockVariables().size() != blockSpec.blocks().size())
            throw new IllegalArgumentException("#latents != #blocks in BlockSpec.");
        if (blockSpec.dataSet() == null)
            throw new IllegalArgumentException("BlockSpec has no DataSet.");
        this.blockSpec = blockSpec;

        // disjointness/indices sanity (against the spec's dataset)
        DataSet ds = blockSpec.dataSet();
        int D = ds.getNumColumns();
        Set<Integer> seen = new HashSet<>();
        for (int bi = 0; bi < blockSpec.blocks().size(); bi++) {
            List<Integer> blk = blockSpec.blocks().get(bi);
            if (blk == null || blk.isEmpty())
                throw new IllegalArgumentException("Empty block at index " + bi);
            for (int col : blk) {
                if (col < 0 || col >= D)
                    throw new IllegalArgumentException("Block " + bi + " references out-of-range column " + col);
                if (!seen.add(col))
                    throw new IllegalArgumentException("Blocks must be disjoint; repeated column " + col);
            }
        }

        // latent name uniqueness
        Set<String> names = new HashSet<>();
        for (Node L : blockSpec.blockVariables()) {
            if (L == null) throw new IllegalArgumentException("Null latent node in BlockSpec.");
            if (!names.add(L.getName()))
                throw new IllegalArgumentException("Duplicate latent name: " + L.getName());
        }
    }

    /** Main entry: build PC1 latents per block, learn latent structure with SEM-BIC + BOSS. */
    public Graph search() throws InterruptedException {
        final DataSet dataSet = blockSpec.dataSet().copy();
        final List<List<Integer>> blocks = blockSpec.blocks();
        final List<Node> blockVariables = blockSpec.blockVariables();

        final int n = dataSet.getNumRows();
        final int B = blocks.size();

        // Build n x B matrix of PC1 scores (standardized per-block, then unit variance)
        double[][] latentData = new double[n][B];
        for (int bi = 0; bi < B; bi++) {
            List<Integer> cols = blocks.get(bi);

            // Extract block data X (n x p_b)
            SimpleMatrix X = new SimpleMatrix(n, cols.size());
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < cols.size(); c++) {
                    X.set(r, c, dataSet.getDouble(r, cols.get(c)));
                }
            }

            // standardize columns (z-score)
            standardizeColumnsInPlace(X);

            // PC1 = X * v1 (right singular vector #0)
            SimpleSVD<SimpleMatrix> svd = X.svd();
            SimpleMatrix v1 = svd.getV().extractVector(false, 0);
            SimpleMatrix pc1 = X.mult(v1); // n x 1

            // Fix sign so average loading is positive (for stability across runs)
            double sign = 0.0;
            for (int c = 0; c < v1.getNumRows(); c++) sign += v1.get(c);
            if (sign < 0) pc1 = pc1.negative();

            // Rescale to unit variance
            double mean = 0, m2 = 0;
            for (int r = 0; r < n; r++) mean += pc1.get(r);
            mean /= Math.max(1, n);
            for (int r = 0; r < n; r++) {
                double d = pc1.get(r) - mean;
                m2 += d * d;
            }
            double sd = Math.sqrt(m2 / Math.max(1, n - 1));
            if (!Double.isFinite(sd) || sd == 0.0) sd = 1.0;

            for (int r = 0; r < n; r++) {
                latentData[r][bi] = (pc1.get(r) - mean) / sd;
            }
        }

        // Covariance over latents with (n-1) divisor + tiny ridge
        SimpleMatrix L = new SimpleMatrix(latentData);          // n x B
        int dof = Math.max(1, n - 1);
        SimpleMatrix latentCov = L.transpose().mult(L).divide(Math.max(1, n - 1));

        double diagMean = 0.0;
        for (int k = 0; k < B; k++) diagMean += latentCov.get(k, k);
        double eps = 1e-8 * (diagMean / Math.max(1, B));
        if (!Double.isFinite(eps) || eps <= 0) eps = 1e-8;

        for (int k = 0; k < B; k++) {
            latentCov.set(k, k, latentCov.get(k, k) + eps);
        }

        this.latentsCovariance = new CovarianceMatrix(blockVariables, new Matrix(latentCov), n);

        // 4) Learn structure over latents with SEM-BIC + BOSS
        SemBicScore score = new SemBicScore(latentsCovariance);
        score.setPenaltyDiscount(this.penaltyDiscount);

        PermutationSearch ps = new PermutationSearch(new Boss(score));
        Graph structureGraph = ps.search();

        LayoutUtil.fruchtermanReingoldLayout(structureGraph);
        return structureGraph;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /** The estimated covariance over latents (after search). */
    public ICovarianceMatrix getLatentsCovariance() {
        return latentsCovariance;
    }

    // ------------------------- helpers -------------------------

    private static void standardizeColumnsInPlace(SimpleMatrix X) {
        int n = X.getNumRows();
        int p = X.getNumCols();
        for (int j = 0; j < p; j++) {
            double mean = 0, m2 = 0;
            for (int i = 0; i < n; i++) mean += X.get(i, j);
            mean /= Math.max(1, n);
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
}