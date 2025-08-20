package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mimbuild over first principal components of (pure) clusters.
 * Constructor matches IndTestBlocks: (dataSet, blocks, blockVariables).
 * Each cluster's latent is PC1 of its standardized indicators; latent scores are re-scaled to unit variance.
 * A latent–latent covariance is built using (n-1) and a tiny ridge, then BOSS (+PermutationSearch) scores it.
 */
public class MimbuildPca {

    private final DataSet dataSet;                 // observed data
    private final List<List<Integer>> blocks;      // clusters as column indices in dataSet
    private final List<Node> blockVariables;       // latent nodes corresponding to blocks (size must match)

    private double penaltyDiscount = 1.0;

    private List<Node> latents;                    // latent nodes (cloned with LATENT type)
    private double[][] latentData;                 // n x B matrix of PC1 scores (unit variance)
    private ICovarianceMatrix latentsCov;          // covariance over latents
    private Graph structureGraph;                  // learned latent structure

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

    /**
     * Run PCA per block to get latent scores, build latent covariance, and learn a structure over latents using BOSS.
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
        this.latentData = new double[nSamples][nLatents];
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

        this.latentsCov = new edu.cmu.tetrad.data.CovarianceMatrix(latents, new Matrix(latentCov), nSamples);

        // Learn structure over latents
        SemBicScore score = new SemBicScore(this.latentsCov);
        score.setPenaltyDiscount(this.penaltyDiscount);

        PermutationSearch ps = new PermutationSearch(new Boss(score));
        this.structureGraph = ps.search();
        LayoutUtil.fruchtermanReingoldLayout(this.structureGraph);
        return this.structureGraph;
    }

    // --- Helpers ---

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

    // --- Accessors / options ---

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public double[][] getLatentData() {
        return latentData;
    }

    public ICovarianceMatrix getLatentsCov() {
        return latentsCov;
    }

    public List<Node> getLatents() {
        return latents == null ? null : new ArrayList<>(latents);
    }

    public Graph getStructureGraph() {
        return structureGraph;
    }

    /**
     * Builds a full graph by adding measured variables and edges latent -> measured for each block.
     * You can call this after {@link #search()}.
     */
    public Graph getFullGraph(List<Node> includeNodes) {
        Graph graph = new edu.cmu.tetrad.graph.EdgeListGraph(this.structureGraph);

        for (int i = 0; i < this.latents.size(); i++) {
            Node latent = this.latents.get(i);
            List<Integer> cols = this.blocks.get(i);
            for (int col : cols) {
                Node measured = dataSet.getVariable(col);
                if (!graph.containsNode(measured)) graph.addNode(measured);
                graph.addDirectedEdge(latent, measured);
            }
        }

        if (includeNodes != null) {
            for (Node node : includeNodes) {
                if (!graph.containsNode(node)) graph.addNode(node);
            }
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);
        return graph;
    }
}