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
import java.util.Random;

/**
 * A simplified Mimbuild implementation that replaces optimization with PCA over pure clusters. Each latent is
 * represented by the first principal component of its corresponding indicator cluster.
 *
 * @author jdramsey
 */
public class MimbuildPca {

    /**
     * Represents the clustering structure within the class.
     * The clustering is stored as a list of clusters, where each cluster
     * is represented by a list of Node objects. Each cluster groups related
     * nodes based on some predefined logic or algorithm, which may depend
     * on the context of the containing class.
     *
     * This variable is utilized in methods that require a grouping of nodes
     * for analysis or processing, as well as for retrieving or updating the
     * clustering structure.
     */
    private List<List<Node>> clustering;
    /**
     * Represents the covariance matrix of the latent variables in the PCA model.
     * This covariance matrix characterizes the variance and covariance structure
     * between the latent variables used in the analysis. It serves as a critical
     * component in understanding and modeling the relationships among these latent
     * variables.
     */
    private ICovarianceMatrix latentsCov;
    /**
     * A list of latent variables represented as Node objects. Each Node in the list corresponds
     * to a latent variable within the model. These latent variables are used to capture and
     * represent underlying, unobserved patterns or structures in the data. The list provides
     * access to and storage of the latent variables for further processing and computations.
     */
    private List<Node> latents;
    /**
     * Represents the internal structure graph utilized by the MimbuildPca class. The structureGraph
     * encapsulates the graphical representation of relationships between latent variables and potentially
     * observed variables or other nodes. This graph serves as a foundational component for operations
     * related to structure discovery, clustering, and model representation within this class. The specific
     * characteristics and properties of the graph may depend on the methods employed and data supplied.
     */
    private Graph structureGraph;
    /**
     * Represents a penalty discount factor used in computations or adjustments within the class.
     * This variable is a multiplier that influences penalty-based calculations, optimizations,
     * or evaluations, allowing the adjustment of the penalty strength in the model.
     *
     * The default value is set to 1, which may indicate no discount or adjustment
     * to the penalties, depending on the context in which it is applied.
     */
    private double penaltyDiscount = 1;
    /**
     * A two-dimensional array of doubles representing the latent data in the
     * context of the PCA-based model within the MimbuildPca class. Each row of
     * the array corresponds to a data point, and each column represents a latent
     * variable.
     *
     * The latentData variable is used internally to store the computed latent
     * variable values for further analysis or processing. The data is typically
     * derived from the underlying relationships in the input dataset and serves
     * as an intermediate representation for tasks such as clustering, covariance
     * analysis, and graph construction.
     */
    private double[][] latentData;

    /**
     * Default constructor for the MimbuildPca class.
     *
     * This constructor initializes an instance of the MimbuildPca class. It sets
     * up the internal structure for the object but does not perform any specific
     * operations or calculations. Additional setup or initialization of properties
     * and fields may be required using other methods provided by the class.
     */
    public MimbuildPca() {
    }

    /**
     * Performs a search operation to construct a graph representing the latent structure
     * derived from the provided clustering, latent variable names, and dataset.
     * This method processes the provided data, performs dimensionality reduction
     * using PCA (Principal Component Analysis), computes covariance matrices,
     * and applies a structure learning algorithm to identify dependency structures.
     *
     * @param clustering a list of clusters, where each cluster is represented as a
     *        list of Node objects. These clusters define groupings of variables that
     *        are considered in the construction of latent variables.
     * @param latentNames a list of names for the latent variables. Each name corresponds
     *        to a cluster in the `clustering` parameter.
     * @param dataSet the dataset containing observed variables. This dataset is used
     *        to compute the breakdown of latent structures and relationships among variables.
     * @return a Graph object representing the identified latent dependency structure
     *         based on the provided data, clusters, and latent variable names.
     * @throws InterruptedException if the execution is interrupted during any processing step.
     * @throws NullPointerException if any of the input parameters (`clustering`,
     *         `latentNames`, or `dataSet`) are null.
     * @throws IllegalArgumentException if the size of the `clustering` list does not
     *         match the size of the `latentNames` list.
     */
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

    /**
     * Retrieves the covariance matrix of the latent variables stored within the instance. This covariance matrix
     * represents the relationships between the latent variables in terms of their variances and covariances.
     *
     * @return An instance of ICovarianceMatrix that encapsulates the covariance structure of the latent variables.
     */
    public ICovarianceMatrix getLatentsCov() {
        return latentsCov;
    }

    /**
     * Retrieves the clustering structure stored within the instance. The clustering is represented as a list of
     * clusters, where each cluster is a list of nodes.
     *
     * @return A list of clusters, with each cluster represented as a list of Node objects.
     */
    public List<List<Node>> getClustering() {
        return clustering;
    }

    /**
     * Constructs and returns a complete directed graph based on the provided nodes and the internal structure of the
     * class. The returned graph integrates the latent and measured nodes with directed edges, using a specified layout
     * algorithm to structure the graph visually.
     *
     * @param includeNodes a list of nodes to be included in the graph. These nodes will be added to the graph if they
     *                     are not already present.
     * @return a Graph object representing the full directed graph constructed from the latent and measured nodes.
     */
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

        for (Node node : includeNodes) {
            if (!graph.containsNode(node)) graph.addNode(node);
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);
        return graph;
    }

    /**
     * Sets the penalty discount value used in computations or adjustments related to the model. The penalty discount
     * may be used as a parameter to influence calculations, optimizations, or evaluations within the associated methods
     * of the class.
     *
     * @param penaltyDiscount the penalty discount value to be set, represented as a double.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Retrieves the latent data stored within the instance.
     *
     * @return A two-dimensional array of doubles representing the latent data.
     */
    public double[][] getLatentData() {
        return latentData;
    }

    /**
     * Centers the columns of the provided matrix by subtracting their respective means. Each column's mean is
     * calculated and then subtracted from each element in that column.
     *
     * @param mat the matrix whose columns are to be centered; each column's mean will be computed and subtracted from
     *            its elements. This parameter must be an instance of SimpleMatrix.
     */
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

    /**
     * Simulates a dataset from a given covariance matrix. The method generates random samples based on the provided
     * covariance structure.
     *
     * @param cov      the covariance matrix from which to simulate the data. Must be an instance of SimpleMatrix with
     *                 dimensions p x p, where p is the number of variables.
     * @param nSamples the number of samples to generate. This indicates the number of data points (rows) in the
     *                 simulated dataset.
     * @return a two-dimensional array of doubles, where each row represents a generated sample and each column
     * corresponds to a variable.
     */
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