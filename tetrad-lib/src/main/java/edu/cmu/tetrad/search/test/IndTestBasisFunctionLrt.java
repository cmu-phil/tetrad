package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * The IndTestBasisFunctionLrtCovariance class implements the IndependenceTest interface and provides functionality to
 * perform conditional independence tests using basis functions and likelihood ratio tests based on residual variances
 * and covariance matrices. It handles the transformations, embeddings, and statistical computations required to compute
 * p-values for testing conditional independence of variables.
 * <p>
 * This class is designed to work with a given dataset, perform embedding transformations based on specified basis
 * function parameters, and efficiently compute the required statistics for hypothesis testing over sets of variables.
 * <p>
 * Note that compared to the full sample version of this class (see), this class uses a covariance matrix as a
 * sufficient statistic and thus cannot do random row subsetting per test.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see IndTestBasisFunctionLrtFullSample
 */
public class IndTestBasisFunctionLrt implements IndependenceTest {
    /**
     * Represents the dataset used within the class for statistical analyses and computations.
     * <p>
     * The dataset contains variables and their associated data, which are utilized in various methods to perform tasks
     * such as conditional independence testing, covariance matrix computation, residual variance calculation, and
     * embedding transformations.
     * <p>
     * This variable is final, indicating that the dataset is immutable and cannot be reassigned once initialized.
     */
    private final DataSet dataSet;
    /**
     * A list of Node objects representing the set of variables associated with the current instance. These variables
     * are derived from the input dataset and are used internally for computations such as conditional independence
     * testing and variance analysis.
     */
    private final List<Node> variables;
    /**
     * Stores a mapping between Node objects and their associated integer identifiers or values, used internally within
     * this class to represent nodes and manage their relationships or properties. This structure facilitates efficient
     * lookups and manipulations in computations and tests related to the functionality of this class.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * Represents the covariance matrix of the dataset, which is computed based on the processed input data and used for
     * various statistical computations within the class. This matrix encapsulates the covariances between pairs of
     * variables in the dataset and is essential for determining relationships among variables, such as conditional
     * independence.
     */
    private final SimpleMatrix covarianceMatrix;
    /**
     * Represents the sample size of the dataset being analyzed. This variable is used in statistical computations, such
     * as variance and covariance calculations, to determine the scale and reliability of the analysis.
     */
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
     * Singularity lambda.
     */
    private final double lambda;
    /**
     * Represents the significance level for statistical tests within the class. It is used to determine the threshold
     * for rejecting the null hypothesis in various statistical computations and hypothesis testing methods. The default
     * value is set to 0.01.
     */
    private double alpha = 0.01;
    /**
     * A boolean flag indicating whether verbose output is enabled or not. When set to true, additional logging or
     * diagnostic information may be produced by the methods in this class to aid in debugging or understanding the
     * internal processing steps.
     */
    private boolean verbose = false;

    /**
     * Constructs an instance of IndTestBasisFunctionLrtCovariance. This constructor initializes the object using a
     * dataset and parameters related to truncation limit, basis type, and basis scale. It processes the input data into
     * an embedded format, computes its covariance matrix, and sets up internal variables for further use.
     *
     * @param dataSet         the input dataset containing the variables and data rows to be analyzed.
     * @param truncationLimit the limit to truncate the embeddings or basis functions in the data.
     * @param lambda          Singularity lambda
     */
    public IndTestBasisFunctionLrt(DataSet dataSet, int truncationLimit, double lambda) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodeHash = nodesHash;
        this.lambda = lambda;

        // Expand the discrete columns to give indicators for each category. We want to leave a category out if
        // we're not using the enable-regularization option.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, truncationLimit, 1, 1);
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();

        this.covarianceMatrix = DataUtils.cov(embeddedData.embeddedData().getDoubleData().getDataCopy());
    }

    /**
     * Computes the p-value for the null hypothesis that two variables, represented as nodes, are conditionally
     * independent given a set of conditioning variables.
     * <p>
     * The method transforms the input nodes and conditioning set into their respective embedded representations and
     * computes the likelihood ratio statistic based on residual variances. It then calculates the corresponding p-value
     * using a Chi-squared distribution.
     *
     * @param x the first node representing one of the variables to be tested.
     * @param y the second node representing the other variable to be tested.
     * @param z the set of nodes representing the conditioning variables.
     * @return the computed p-value for the hypothesis test of conditional independence.
     */
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
            List<Integer> embeddedValues = embedding.get(value);
            if (embeddedValues != null) {
                embedded_z.addAll(embeddedValues);
            }
        }

        // Convert to index arrays
        int[] xIndices = embedded_x.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = embedded_y.stream().mapToInt(Integer::intValue).toArray();
        int[] zIndices = embedded_z.stream().mapToInt(Integer::intValue).toArray();

        // Compute variance estimates
        double eps = 1e-20;
        double sigma0_sq = Math.max(eps, computeResidualVariance(xIndices, zIndices));
        double sigma1_sq = Math.max(eps, computeResidualVariance(xIndices, concatArrays(yIndices, zIndices)));

        // Log-likelihood ratio statistic
        double LR_stat = sampleSize * Math.log(sigma0_sq / sigma1_sq);

        // Degrees of freedom is the number of additional basis columns in Y
        int df = yIndices.length;
        if (df == 0) return 1.0;

        // Compute p-value
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p_value = 1.0 - chi2.cumulativeProbability(LR_stat);

//        if (verbose) {
//            System.out.printf("LR Stat: %.4f | df: %d | p: %.4f%n", LR_stat, df, p_value);
//        }

        return p_value;
    }

    /**
     * Computes the variance of residuals given the indices of predictors.
     */
    private double computeResidualVariance(int[] xIndices, int[] predictorIndices) {
        if (predictorIndices.length == 0) {
            return StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices).trace() / xIndices.length;
        }

        SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices);
        SimpleMatrix Sigma_XP = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, predictorIndices);
        SimpleMatrix Sigma_PP = StatUtils.extractSubMatrix(covarianceMatrix, predictorIndices, predictorIndices);
//        Sigma_PP = StatUtils.chooseMatrix(Sigma_PP, lambda);

        // Compute OLS estimate of X given predictors P
        SimpleMatrix beta = new Matrix(Sigma_PP).chooseInverse(lambda).getData().mult(Sigma_XP.transpose());

        // Compute residual variance
        return Sigma_XX.minus(Sigma_XP.mult(beta)).trace() / xIndices.length;
    }

    /**
     * Concatenates two integer arrays.
     */
    private int[] concatArrays(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
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
     * Retrieves the significance level (alpha) for the statistical tests performed by this instance.
     *
     * @return the current significance level as a double.
     */
    public double getAlpha() {
        return alpha;
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
}
