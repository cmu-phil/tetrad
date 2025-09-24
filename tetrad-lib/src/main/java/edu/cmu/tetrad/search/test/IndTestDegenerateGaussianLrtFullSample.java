///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * The IndTestBasisFunctionLrt class performs conditional independence testing using basis functions within the context
 * of a generalized likelihood ratio test (GLRT). This class is designed for evaluating whether two variables are
 * conditionally independent given a set of conditioning variables, leveraging statistical and matrix-based
 * computations.
 * <p>
 * This class may be compared to the covariance version (see), which is more scalable to large sample sizes. The
 * advantage of this implementation is that rows may be subsetted randomly for individual conditional independence
 * tests. This is not something that can be done using a covariance matrix as a sufficient statistic.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see IndTestBasisFunctionLrt
 */
@Deprecated(since = "7.9", forRemoval = false)
public class IndTestDegenerateGaussianLrtFullSample implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable {
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
     * Represents a dataset embedded within the context of the independence test. This data is used internally by the
     * `IndTestBasisFunctionLrt` class to support testing for conditional independence and related computations.
     * <p>
     * The `embeddedData` field stores a `DataSet` object that may contain information such as the variables and
     * observations relevant to the test. It is a final field, indicating that its reference cannot be changed after
     * initialization, ensuring consistency and immutability with respect to its reference.
     */
    private final DataSet embeddedData;
    /**
     * A list holding all indices of rows that are considered during data processing or analysis. This typically
     * includes all rows from the dataset except those with missing or invalid values for the required variables.
     * <p>
     * This field is immutable and represents the default set of rows to be used in analysis unless explicitly modified
     * by other methods.
     */
    private final List<Integer> allRows;
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
     * The sample size used in computations within the class. This variable may represent an effective sample size that
     * differs from the original dataset size, depending on configurations or preprocessing steps. It is particularly
     * relevant to statistical and independence testing procedures where the sample size influences the results.
     */
    private int sampleSize;
    /**
     * Represents the specific rows being utilized during the independence test. This field holds a list of integers
     * corresponding to the indices of the rows from the data set. If not explicitly set, all rows without missing
     * values will be used by default.
     */
    private List<Integer> rows;
    /**
     * Singularity lambda.
     */
    private double lambda;
    private int nEff;

    /**
     * Constructs an instance of the IndTestBasisFunctionLrt class. This constructor initializes the object using the
     * provided dataset and configuration parameters for truncation limit, basis type, and basis scale. It processes the
     * input dataset to create the necessary embeddings and initializes key components such as the BIC score for later
     * use in independence testing.
     *
     * @param dataSet the input data set to be used for the analysis. It must not be null. May contain a mixture of
     *                continuous and discrete variables.
     * @throws NullPointerException if the provided dataSet is null.
     */
    public IndTestDegenerateGaussianLrtFullSample(DataSet dataSet) {
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
        double lambda = 0.0; // We always use c - 1 categories per contract.

        // Expand the discrete columns to give indicators for each category. For the continuous variables, we
        // wet the truncation limit to 1, on the contract that the first polynomial for any basis will be just
        // x itself. These are asssumed to be Gaussian for this test. Basis scale -1 will do no scaling.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, 1, 1, -1);

        this.embeddedData = embeddedData.embeddedData();
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();
        this.allRows = listRows();

        setEffectiveSampleSize(-1);
    }

    /**
     * Computes the Ordinary Least Squares (OLS) solution for a linear system. The method applies regularization to the
     * OLS problem to stabilize the solution, particularly in cases where the design matrix B is ill-conditioned or near
     * singular. Regularization is controlled by the lambda parameter, which adds a scaled identity matrix to the design
     * matrix's normal equation.
     *
     * @param B      the design matrix, where rows correspond to observations and columns correspond to features.
     * @param X      the response matrix, where rows correspond to observations and columns to dependent variable
     *               outputs.
     * @param lambda the regularization parameter used to stabilize the solution. Larger values result in stronger
     *               regularization.
     * @return the computed OLS solution as a SimpleMatrix object.
     */
    public static SimpleMatrix computeOLS(SimpleMatrix B, SimpleMatrix X, double lambda) {
        int numCols = B.getNumCols();
        SimpleMatrix BtB = B.transpose().mult(B);
        BtB = StatUtils.chooseMatrix(BtB, lambda);

        // Parallelized inversion using EJML's lower-level operations
        SimpleMatrix inverse = new Matrix(BtB).chooseInverse(lambda).getData();

//        SimpleMatrix inverse = new SimpleMatrix(numCols, numCols);
//        CommonOps_DDRM.invert(BtB.getDDRM(), inverse.getDDRM());

        return inverse.mult(B.transpose()).mult(X);
    }


    /**
     * Computes variance of residuals: Var(R) = sum(R^2) / N
     */
    private double computeVariance(SimpleMatrix residuals) {
        return residuals.elementMult(residuals).elementSum() / this.nEff;
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

        List<Integer> rows = this.rows == null ? allRows : this.rows;

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
        SimpleMatrix X_basis = new SimpleMatrix(rows.size(), embedded_x.size());
        for (int i = 0; i < embedded_x.size(); i++) {
            for (int j = 0; j < rows.size(); j++) {
                X_basis.set(j, i, embeddedData.getDouble(rows.get(j), embedded_x.get(i)));
            }
        }

        SimpleMatrix Y_basis = new SimpleMatrix(rows.size(), embedded_y.size());

        for (int i = 0; i < embedded_y.size(); i++) {
            for (int j = 0; j < rows.size(); j++) {
                Y_basis.set(j, i, embeddedData.getDouble(rows.get(j), embedded_y.get(i)));
            }
        }

        SimpleMatrix Z_basis = new SimpleMatrix(rows.size(), embedded_z.size() + 1);

        for (int i = 0; i < embedded_z.size(); i++) {
            for (int j = 0; j < rows.size(); j++) {
                Z_basis.set(j, i, embeddedData.getDouble(rows.get(j), embedded_z.get(i)));
            }
        }

        for (int j = 0; j < rows.size(); j++) {
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
        int p_Z = Z_basis.getNumCols();
        double sigma0_sq, sigma1_sq;

        // Ensure Z_basis always includes an intercept
        if (Z_basis.getNumCols() == 0) {
            Z_basis = new SimpleMatrix(rows.size(), 1);
            for (int i = 0; i < rows.size(); i++) {
                Z_basis.set(i, 0, 1);  // Adds intercept
            }
            df++;
        }

        double lambda = 0.0; // c - 1 categories per contract.

        // Null Model: X ~ Z
        SimpleMatrix betaZ = computeOLS(Z_basis, X_basis, lambda);
        SimpleMatrix residualsNull = X_basis.minus(Z_basis.mult(betaZ));
        sigma0_sq = computeVariance(residualsNull);

        // Full Model: X ~ Z + Y
        SimpleMatrix B_full = Z_basis.combine(0, p_Z, Y_basis);
        SimpleMatrix betaFull = computeOLS(B_full, X_basis, lambda);
        SimpleMatrix residualsFull = X_basis.minus(B_full.mult(betaFull));
        sigma1_sq = computeVariance(residualsFull);

        // Compute Likelihood Ratio Statistic
        double epsilon = 1e-10;
        double LR_stat = this.nEff * Math.log((sigma0_sq + epsilon) / (sigma1_sq + epsilon));

        // Compute p-value using chi-square distribution
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p_value = 1.0 - chi2.cumulativeProbability(LR_stat);

//        if (verbose) {
//            System.out.printf("LR Stat: %.4f | df: %d | p: %.4f%n", LR_stat, df, p_value);
//        }

        return p_value;
    }

    @Override
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    /**
     * Sets the sample size to use for the independence test, which may be different from the sample size of the data
     * set or covariance matrix. If not set, the sample size of the data set or covariance matrix is used.
     *
     * @param effectiveSampleSize The sample size to use.
     */
    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.nEff = effectiveSampleSize < 0 ? this.sampleSize : effectiveSampleSize;
    }

    /**
     * Returns the rows used in the test.
     *
     * @return The rows used in the test.
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test. Otherwise, all rows are used, except those with missing
     * values.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }

            this.rows = rows;
        }
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

    /**
     * Sets the regularization parameter lambda used in statistical computations or tests. The lambda parameter often
     * helps to stabilize computations, particularly in the presence of ill-conditioned problems.
     *
     * @param lambda the regularization parameter to be set. Larger values typically result in stronger regularization.
     *               Must be a non-negative value.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }
}

