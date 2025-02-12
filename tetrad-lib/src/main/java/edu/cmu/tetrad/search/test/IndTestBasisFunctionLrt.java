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

public class IndTestBasisFunctionLrt implements IndependenceTest {
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final Map<Integer, List<Integer>> embedding;
    private final SemBicScore bic;
    private final DataSet embeddedData;
    private double alpha = 0.01;
    private boolean verbose = false;

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

    public IndTestBasisFunctionLrt() {
        this.dataSet = null;
        this.variables = null;
        this.nodeHash = null;
        this.embedding = null;
        this.embeddedData = null;
        this.bic = null;
    }

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
//            meanMat.setColumn(j, 0, new double[rows]);
//            meanMat.setColumn(j, 0, mean);

            for (int i = 0; i < rows; i++) {
                meanMat.set(i, j, mean);
            }

        }
        return meanMat;
    }

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

//        System.out.println("Z_basis: " + Z_basis);

        double pValue = getPValue(X_basis, Y_basis, Z_basis);
        boolean independent = pValue > alpha;

        return new IndependenceResult(new IndependenceFact(x, y, z),
                independent, pValue, alpha - pValue);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return dataSet;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // Compute OLS coefficients: beta = (B^T B)^(-1) B^T X
//    private static SimpleMatrix computeOLS(SimpleMatrix B, SimpleMatrix X) {
//        return B.transpose().mult(B).invert().mult(B.transpose()).mult(X);
//    }

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
//        double LR_stat = N * Math.log(sigma0_sq / sigma1_sq);
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
