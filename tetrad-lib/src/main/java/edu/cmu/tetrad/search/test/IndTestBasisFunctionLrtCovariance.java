package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Optimized version of IndTestBasisFunctionLrt using covariance matrices for GLRT computation.
 */
public class IndTestBasisFunctionLrtCovariance implements IndependenceTest {
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix covarianceMatrix;
    private final int sampleSize;
    /**
     * A mapping structure used to represent the embedding of variables or indices for specific computations in the
     * IndTestBasisFunctionLrt class. The keys are integers representing certain identifiers or indices, while the
     * values are lists of integers representing associated embedded data or structural relationships. This data
     * structure facilitates efficient management and lookup of embedding-related information during various statistical
     * or computational processes within the class.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * Represents a dataset embedded within the context of the independence test. This data is used internally by the
     * `IndTestBasisFunctionLrt` class to support testing for conditional independence and related computations.
     * <p>
     * The `embeddedData` field stores a `DataSet` object that may contain information such as the variables and
     * observations relevant to the test. It is a final field, indicating that its reference cannot be changed after
     * initialization, ensuring consistency and immutability with respect to its reference.
     */
    private final SimpleMatrix embeddedData;
    private double alpha = 0.01;
    private boolean verbose = false;
    private List<Integer> rows;

    /**
     * Constructs an instance of IndTestBasisFunctionLrtCovariance. This constructor initializes the object using a
     * dataset and parameters related to truncation limit, basis type, and basis scale. It processes the input data
     * into an embedded format, computes its covariance matrix, and sets up internal variables for further use.
     *
     * @param dataSet         the input dataset containing the variables and data rows to be analyzed.
     * @param truncationLimit the limit to truncate the embeddings or basis functions in the data.
     * @param basisType       the type of basis functions to use for transformation.
     * @param basisScale      the scale factor associated with the basis functions.
     */
    public IndTestBasisFunctionLrtCovariance(DataSet dataSet, int truncationLimit,
                                             int basisType, double basisScale) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodeHash = nodesHash;
        boolean usePseudoInverse = false;

        // Expand the discrete columns to give indicators for each category. We want to leave a category out if
        // we're not using the pseudoinverse option.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, truncationLimit, basisType, basisScale, usePseudoInverse);
        this.embeddedData = embeddedData.embeddedData().getDoubleData().getDataCopy();
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();

        this.covarianceMatrix = DataUtils.cov(this.embeddedData);
    }

    private double getPValue(Node x, Node y, Set<Node> z) {
        List<Node> zList = new ArrayList<>(z);
        Collections.sort(zList);

        int _x = this.nodeHash.get(x);
        int _y = this.nodeHash.get(y);
        int[] _z = new int[zList.size()];
        for (int i = 0; i < zList.size(); i++) {
            _z[i] = this.nodeHash.get(zList.get(i));
        }

        // Grab the embedded data for _x, _y, and _z.
        List<Integer> embedded_x = embedding.get(_x);
        List<Integer> embedded_y = embedding.get(_y);
        List<Integer> embedded_z = new ArrayList<>();

        for (int value : _z) {
            embedded_z.addAll(embedding.get(value));
        }

        // Convert to index arrays
        int[] xIndices = embedded_x.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = embedded_y.stream().mapToInt(Integer::intValue).toArray();
        int[] zIndices = embedded_z.stream().mapToInt(Integer::intValue).toArray();

        // Compute variance estimates
        double sigma0_sq = computeResidualVariance(xIndices, zIndices);
        double sigma1_sq = computeResidualVariance(xIndices, concatArrays(yIndices, zIndices));

        // Log-likelihood ratio statistic
        double LR_stat = sampleSize * Math.log(sigma0_sq / sigma1_sq);

        // Degrees of freedom is the number of additional basis columns in Y
        int df = yIndices.length;
        if (df == 0) return 1.0;

        // Compute p-value
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p_value = 1.0 - chi2.cumulativeProbability(LR_stat);

        if (verbose) {
            System.out.printf("LR Stat: %.4f | df: %d | p: %.4f%n", LR_stat, df, p_value);
        }

        return p_value;
    }

    /**
     * Computes the variance of residuals given the indices of predictors.
     */
    private double computeResidualVariance(int[] xIndices, int[] predictorIndices) {
        SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices);
        SimpleMatrix Sigma_XP = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, predictorIndices);
        SimpleMatrix Sigma_PP = StatUtils.extractSubMatrix(covarianceMatrix, predictorIndices, predictorIndices);

        // Compute OLS estimate of X given predictors P
        SimpleMatrix beta = Sigma_PP.pseudoInverse().mult(Sigma_XP.transpose());

        // Compute residual variance
        return Sigma_XX.minus(Sigma_XP.mult(beta)).trace() / xIndices.length;
    }

    /**
     * Extracts a submatrix given row and column indices.
     */
    private SimpleMatrix getSubmatrix(SimpleMatrix matrix, int[] indices) {
        int n = indices.length;
        SimpleMatrix subMatrix = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                subMatrix.set(i, j, matrix.get(indices[i], indices[j]));
            }
        }
        return subMatrix;
    }

    /**
     * Concatenates two integer arrays.
     */
    private int[] concatArrays(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Tests for the conditional independence of two nodes given a set of conditioning nodes.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = pValue > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    /**
     * Retrieves the list of nodes (variables) associated with this instance.
     *
     * @return a list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model associated with this instance.
     *
     * @return the current DataModel instance held by this class.
     */
    @Override
    public DataModel getData() {
        return dataSet;
    }

    /**
     * Indicates whether verbose mode is enabled. Verbose mode, when enabled, typically results in detailed logging or
     * diagnostic information being output.
     *
     * @return true if verbose mode is enabled; false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Enables or disables verbose output.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1.");
        }
        this.alpha = alpha;
    }

    public double getAlpha() {
        return alpha;
    }

    /**
     * Retrieves the rows from the dataSet that contain valid values for all variables.
     *
     * @return a list of row indices that contain valid values for all variables
     */
    private List<Integer> listRows() {
        if (this.rows != null) {
            return this.rows;
        }

        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }
}
