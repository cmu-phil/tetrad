package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.Embedding;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * The IndTestBasisFunctionLrt class performs conditional independence testing using basis functions within the context
 * of a generalized likelihood ratio test (GLRT). This class is designed for evaluating whether two variables are
 * conditionally independent given a set of conditioning variables, leveraging statistical and matrix-based
 * computations.
 *
 * @author josephramsey
 */
public class IndTestBasisFunctionLrt implements IndependenceTest {
    /**
     * The `dataSet` field holds a reference to the DataSet object used as the primary data structure for representing
     * and processing data within the `IndTestBasisFunctionLrt` class.
     * <p>
     * It serves as the main data source for independence testing and related computations performed by methods of the
     * class. This field is immutable once initialized via the constructor and cannot be modified thereafter.
     */
    private final DataSet dataSet;
    /**
     * A list of Node objects representing the variables of interest for the independence test. These variables are used
     * throughout the class to perform statistical computations, evaluate dependencies, and maintain information about
     * the data structure being analyzed.
     */
    private final List<Node> variables;
    /**
     * A map that associates a Node with its corresponding unique identifier (integer value). This mapping is used for
     * managing and referencing nodes efficiently within the context of the independence test computations in the
     * `IndTestBasisFunctionLrt` class.
     * <p>
     * It is a final field, meaning its reference is immutable once initialized. This map plays a crucial role in
     * maintaining the structure and relationships of nodes during the analysis process.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * A mapping structure used to represent the embedding of variables or indices for specific computations in the
     * IndTestBasisFunctionLrt class. The keys are integers representing certain identifiers or indices, while the
     * values are lists of integers representing associated embedded data or structural relationships. This data
     * structure facilitates efficient management and lookup of embedding-related information during various statistical
     * or computational processes within the class.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * The bic (Bayesian Information Criterion) score used for evaluating the fit of a statistical model. This score is
     * typically utilized in the context of model selection and comparison when applying the test for independence among
     * variables in the associated dataset.
     * <p>
     * This field is immutable and is initialized during the construction of the {@code IndTestBasisFunctionLrt} class.
     * The bic object encapsulates scoring computations based on the given data and constraints.
     */
    private final SemBicScore bic;
    /**
     * Represents a dataset embedded within the context of the independence test. This data is used internally by the
     * `IndTestBasisFunctionLrt` class to support testing for conditional independence and related computations.
     * <p>
     * The `embeddedData` field stores a `DataSet` object that may contain information such as the variables and
     * observations relevant to the test. It is a final field, indicating that its reference cannot be changed after
     * initialization, ensuring consistency and immutability with respect to its reference.
     */
    private final DataSet embeddedData;
    /**
     * Represents the significance level (alpha) used for statistical tests in the IndTestBasisFunctionLrt class. This
     * value determines the threshold for rejecting the null hypothesis in conditional independence testing, where lower
     * values indicate stricter criteria for rejecting the null hypothesis.
     * <p>
     * The default value of alpha is 0.01.
     */
    private double alpha = 0.01;
    /**
     * A boolean flag indicating whether verbose mode is enabled for the class. When set to true, verbose mode may
     * result in detailed logging or diagnostic output during the execution of methods in the class. When set to false,
     * verbose output is suppressed.
     */
    private boolean verbose = false;

    /**
     * Constructs an instance of the IndTestBasisFunctionLrt class. This constructor initializes the object using the
     * provided dataset and configuration parameters for truncation limit, basis type, and basis scale. It processes the
     * input dataset to create the necessary embeddings and initializes key components such as the BIC score for later
     * use in independence testing.
     *
     * @param dataSet         the input data set to be used for the analysis. It must not be null. May contain a mixture
     *                        of continuous and discrete variables.
     * @param truncationLimit the maximum number of basis function truncations to be used.
     * @param basisType       an integer indicating the type of basis function to use in the embeddings.
     * @param basisScale      a scaling factor for the basis functions used in the embeddings.
     * @throws NullPointerException if the provided dataSet is null.
     */
    public IndTestBasisFunctionLrt(DataSet dataSet, int truncationLimit,
                                   int basisType, double basisScale) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

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
        this.embeddedData = embeddedData.embeddedData();
        this.embedding = embeddedData.embedding();
        this.bic = new SemBicScore(this.embeddedData, false);
        this.bic.setUsePseudoInverse(usePseudoInverse);
        this.bic.setStructurePrior(0);
    }

    /**
     * A private no-argument constructor for the IndTestBasisFunctionLrt class. This constructor initializes the fields
     * of the class to null, effectively creating an uninitialized state. This is typically used to prevent direct
     * instantiation of the class without proper initialization. Used for testing.
     */
    private IndTestBasisFunctionLrt() {
        this.dataSet = null;
        this.variables = null;
        this.nodeHash = null;
        this.embedding = null;
        this.embeddedData = null;
        this.bic = null;
    }

    /**
     * Main method for testing the IndTestBasisFunctionLrt class.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // Simulation Parameters
        int N = 100;  // Number of samples
        int p_X = 5, p_Y = 5, p_Z = 5;  // Basis function dimensions
        Random rand = new Random();

        // Generate random basis matrices for X, Y, Z
        SimpleMatrix Y_basis = randomMatrix(N, p_Y, rand);
        SimpleMatrix Z_basis = randomMatrix(N, p_Z, rand);

        // True beta coefficients for X ~ Z (Null Model)
        SimpleMatrix trueBeta_Z = randomMatrix(p_Z, p_X, rand);
        SimpleMatrix X_basis = Z_basis.mult(trueBeta_Z).plus(randomMatrix(N, p_X, rand).scale(0.5));  // Add noise

        IndTestBasisFunctionLrt test = new IndTestBasisFunctionLrt();
        test.getPValue(X_basis, Y_basis, Z_basis);
    }

    // Generate a random matrix of size (rows x cols) with standard normal values
    private static SimpleMatrix randomMatrix(int rows, int cols, Random rand) {
        SimpleMatrix mat = new SimpleMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                mat.set(i, j, rand.nextGaussian());  // Standard normal distribution
            }
        }
        return mat;
    }

    private static SimpleMatrix computeOLS(SimpleMatrix B, SimpleMatrix X) {
        SimpleMatrix BtB = B.transpose().mult(B);
        if (BtB.determinant() < 1e-10) {  // Check if matrix is nearly singular
            return BtB.pseudoInverse().mult(B.transpose()).mult(X);
        } else {
            return BtB.invert().mult(B.transpose()).mult(X);
        }
    }

    // Compute variance of residuals: Var(R) = sum(R^2) / N
    private static double computeVariance(SimpleMatrix residuals) {
        double sumSquares = residuals.elementMult(residuals).elementSum();
        return sumSquares / residuals.getNumRows();
    }

    // Compute column-wise mean as a matrix
    private static SimpleMatrix columnMean(SimpleMatrix X) {
        int rows = X.getNumRows();
        int cols = X.getNumCols();
        SimpleMatrix meanMat = new SimpleMatrix(rows, cols);
        for (int j = 0; j < cols; j++) {
            double mean = X.extractVector(false, j).elementSum() / rows;
            for (int i = 0; i < rows; i++) {
                meanMat.set(i, j, mean);
            }
        }
        return meanMat;
    }

    /**
     * Tests for the conditional independence of two nodes, x and y, given a set of conditioning nodes z. The method
     * evaluates the independence using a generalized likelihood ratio test and p-value computation.
     *
     * @param x the first Node to test for independence.
     * @param y the second Node to test for independence.
     * @param z a set of conditioning nodes; the test checks the independence of x and y conditioned on these nodes.
     * @return an IndependenceResult object containing the result of the independence test, including whether x and y
     * are independent, the computed p-value, and other associated data.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        List<Node> zList = new ArrayList<>(z);
        Collections.sort(zList);

        int _x = this.nodeHash.get(x);
        int _y = this.nodeHash.get(y);
        int[] _z = new int[zList.size()];
        for (int i = 0; i < zList.size(); i++) {
            _z[i] = this.nodeHash.get(zList.get(i));
        }

        // Grab the embedded data for _x, _y, and _z. These are columns in the embeddedData dataset.
        List<Integer> embedded_x = embedding.get(_x);
        List<Integer> embedded_y = embedding.get(_y);
        List<Integer> embedded_z = new ArrayList<>();
        for (int value : _z) {
            embedded_z.addAll(embedding.get(value));
        }

        // For each variable, form a SimpleMatrix of the embedded data for that variable.
        SimpleMatrix X_basis = new SimpleMatrix(embeddedData.getNumRows(), embedded_x.size());
        for (int i = 0; i < embedded_x.size(); i++) {
            for (int j = 0; j < embeddedData.getNumRows(); j++) {
                X_basis.set(j, i, embeddedData.getDouble(j, embedded_x.get(i)));
            }
        }

        SimpleMatrix Y_basis = new SimpleMatrix(embeddedData.getNumRows(), embedded_y.size());

        for (int i = 0; i < embedded_y.size(); i++) {
            for (int j = 0; j < embeddedData.getNumRows(); j++) {
                Y_basis.set(j, i, embeddedData.getDouble(j, embedded_y.get(i)));
            }
        }

        SimpleMatrix Z_basis = new SimpleMatrix(embeddedData.getNumRows(), embedded_z.size() + 1);

        for (int i = 0; i < embedded_z.size(); i++) {
            for (int j = 0; j < embeddedData.getNumRows(); j++) {
                Z_basis.set(j, i, embeddedData.getDouble(j, embedded_z.get(i)));
            }
        }

        for (int j = 0; j < embeddedData.getNumRows(); j++) {
            Z_basis.set(j, embedded_z.size(), 1);
        }

        double pValue = getPValue(X_basis, Y_basis, Z_basis);
        boolean independent = pValue > alpha;

        return new IndependenceResult(new IndependenceFact(x, y, z),
                independent, pValue, alpha - pValue);
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
     * Sets the verbose mode for logging or output behavior. When verbose mode is enabled, detailed information about
     * the processing can be printed or logged, depending on the implementation.
     *
     * @param verbose a boolean flag indicating whether to enable or disable verbose mode. If true, verbose mode is
     *                enabled; if false, it is disabled.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return this level, default 0.01.
     */
    public double getAlpha() {
        return this.alpha;
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

    /**
     * Computes the p-value for the generalized likelihood ratio test (GLRT) to compare two nested models. The method
     * calculates the p-value based on the residual variances of the null model (X predicted by Z) and the full model (X
     * predicted by both Z and Y).
     *
     * @param X_basis the data matrix for the dependent variable (X), where rows correspond to observations and columns
     *                correspond to variables/features.
     * @param Y_basis the data matrix for the additional predictors (Y), where rows correspond to observations and
     *                columns correspond to variables/features.
     * @param Z_basis the data matrix for the baseline predictors (Z), where rows correspond to observations and columns
     *                correspond to variables/features. It can be an empty matrix if there is no baseline model.
     * @return the computed p-value for the likelihood ratio test.
     */
    private double getPValue(SimpleMatrix X_basis, SimpleMatrix Y_basis, SimpleMatrix Z_basis) {
        int df = Y_basis.getNumCols(); // Degrees of freedom
        int N = X_basis.getNumRows();
        int p_Z = Z_basis.getNumCols();

        double sigma0_sq, sigma1_sq;

        if (Z_basis.getNumCols() == 0) {
            // Null Model: X ~ mean(X)
            SimpleMatrix meanX = columnMean(X_basis);
            SimpleMatrix residualsNull = X_basis.minus(meanX);
            sigma0_sq = computeVariance(residualsNull);

            // Full Model: X ~ Y
            SimpleMatrix betaFull = computeOLS(Y_basis, X_basis);
            SimpleMatrix residualsFull = X_basis.minus(Y_basis.mult(betaFull));
            sigma1_sq = computeVariance(residualsFull);
        } else {
            SimpleMatrix betaZ = computeOLS(Z_basis, X_basis);
            SimpleMatrix residualsNull = X_basis.minus(Z_basis.mult(betaZ));
            sigma0_sq = computeVariance(residualsNull);

            // Compute residual variance for the full model (X ~ Z + Y)
            SimpleMatrix B_full = Z_basis.combine(0, p_Z, Y_basis);  // Concatenation
            SimpleMatrix betaFull = computeOLS(B_full, X_basis);
            SimpleMatrix residualsFull = X_basis.minus(B_full.mult(betaFull));
            sigma1_sq = computeVariance(residualsFull);
        }

        // Compute Likelihood Ratio Statistic
        double epsilon = 1e-10;  // Small regularization
        double LR_stat = N * Math.log((sigma0_sq + epsilon) / (sigma1_sq + epsilon));

        // Compute p-value using chi-square distribution
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p_value = 1.0 - chi2.cumulativeProbability(LR_stat);

        // Output results
        if (verbose) {
            System.out.printf("LR Stat: %.4f | df: %d | p: %.4f%n", LR_stat, df, p_value);
        }

        return p_value;
    }
}
