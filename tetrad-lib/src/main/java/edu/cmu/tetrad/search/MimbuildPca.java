package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

/**
 * A simplified Mimbuild implementation that replaces optimization with PCA
 * over pure clusters. Each latent is represented by the first principal
 * component of its corresponding indicator cluster.
 */
public class MimbuildPca {

    private List<List<Node>> clustering;
    private ICovarianceMatrix latentsCov;
    private List<Node> latents;
    private Graph structureGraph;
    private double penaltyDiscount = 1;
    private double[][] latentData;

    public MimbuildPca() {
    }

    public Graph search(List<List<Node>> clustering, List<String> latentNames, DataSet dataSet) throws InterruptedException {
        if (clustering == null || latentNames == null || dataSet == null) {
            throw new NullPointerException();
        }

        if (clustering.size() != latentNames.size()) {
            throw new IllegalArgumentException("#clusters != #latent names");
        }

        this.clustering = clustering;
        this.latents = new ArrayList<>();
        for (String name : latentNames) {
            GraphNode node = new GraphNode(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        int nSamples = dataSet.getNumRows();
        int nLatents = clustering.size();
        latentData = new double[nSamples][nLatents];

        for (int i = 0; i < clustering.size(); i++) {
            List<Node> cluster = clustering.get(i);
            int[] cols = cluster.stream().mapToInt(n -> dataSet.getColumnIndex(dataSet.getVariable(n.getName()))).toArray();

            SimpleMatrix clusterData = new SimpleMatrix(nSamples, cols.length);
            for (int r = 0; r < nSamples; r++) {
                for (int c = 0; c < cols.length; c++) {
                    clusterData.set(r, c, dataSet.getDouble(r, cols[c]));
                }
            }

            centerColumns(clusterData);
            SimpleSVD<SimpleMatrix> svd = clusterData.svd();
            SimpleMatrix v = svd.getV();

            SimpleMatrix pc1 = clusterData.mult(v.extractVector(false, 0));
            for (int r = 0; r < nSamples; r++) latentData[r][i] = pc1.get(r);
        }

        SimpleMatrix latentMat = new SimpleMatrix(latentData);
        SimpleMatrix latentCov = latentMat.transpose().mult(latentMat).divide(nSamples);

        SimpleMatrix covMatrix = new SimpleMatrix(nLatents, nLatents);
        for (int i = 0; i < nLatents; i++) {
            for (int j = 0; j < nLatents; j++) {
                covMatrix.set(i, j, latentCov.get(i, j));
            }
        }

        this.latentsCov = new edu.cmu.tetrad.data.CovarianceMatrix(latents, new Matrix(covMatrix), nSamples);

        SemBicScore score = new SemBicScore(this.latentsCov);
        score.setPenaltyDiscount(this.penaltyDiscount);
        PermutationSearch search = new PermutationSearch(new Boss(score));

        this.structureGraph = search.search();
        LayoutUtil.fruchtermanReingoldLayout(this.structureGraph);
        return this.structureGraph;
    }

    public Graph searchFromCovariance(List<List<Node>> clustering, List<String> latentNames, ICovarianceMatrix measureCov) throws InterruptedException {
        SimpleMatrix cov = measureCov.getMatrix().getDataCopy();
        int nSamples = measureCov.getSampleSize();
        int p = cov.getNumRows();
        double[][] simData = simulateDataFromCovariance(cov, nSamples);

        DataSet simulated = new BoxDataSet(new DoubleDataBox(simData), measureCov.getVariables());

//        DataSet simulated = DataUtils.doubleDataToDataSet(simData, measureCov.getVariables());
        return search(clustering, latentNames, simulated);
    }

    public ICovarianceMatrix getLatentsCov() {
        return latentsCov;
    }

    public List<List<Node>> getClustering() {
        return clustering;
    }

    public Graph getFullGraph(List<Node> includeNodes) {
        Graph graph = new EdgeListGraph(this.structureGraph);

        for (int i = 0; i < this.latents.size(); i++) {
            Node latent = this.latents.get(i);
            List<Node> measured = clustering.get(i);
            for (Node m : measured) {
                if (!graph.containsNode(m)) graph.addNode(m);
                graph.addDirectedEdge(latent, m);
            }
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);
        return graph;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public double[][] getLatentData() {
        return latentData;
    }

    private void centerColumns(SimpleMatrix mat) {
        int rows = mat.numRows();
        int cols = mat.numCols();
        for (int j = 0; j < cols; j++) {
            double mean = 0;
            for (int i = 0; i < rows; i++) mean += mat.get(i, j);
            mean /= rows;
            for (int i = 0; i < rows; i++) mat.set(i, j, mat.get(i, j) - mean);
        }
    }

    private double[][] simulateDataFromCovariance(SimpleMatrix cov, int nSamples) {
        int p = cov.getNumRows();
        double[][] L = new double[p][p];
        for (int i = 0; i < p; i++) L[i][i] = Math.sqrt(cov.get(i, i));
        Random rand = new Random();
        double[][] data = new double[nSamples][p];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < p; j++) {
                data[i][j] = rand.nextGaussian() * L[j][j];
            }
        }
        return data;
    }
}