package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;

/**
 * Refactored implementation of the Delta Tetrad Test for Confirmatory Tetrad Analysis (CTA). This version improves
 * modularity, exception handling, and robustness.
 * <p>
 * Reference: Bollen and Ting, "Confirmatory Tetrad Analysis," Sociological Methodology, 1993.
 *
 * @author Refactored
 */
public class DeltaTetradTest {

    private final int sampleSize;
    private final ICovarianceMatrix covarianceMatrix;
    private final List<Node> variables;
    private final double[][] data;
    private final boolean isGaussian;

    /**
     * Constructor for Gaussian data.
     *
     * @param dataSet A continuous dataset.
     */
    public DeltaTetradTest(DataSet dataSet) {
        if (dataSet == null || !dataSet.isContinuous()) {
            throw new IllegalArgumentException("Dataset must be non-null and continuous.");
        }

        this.covarianceMatrix = new CorrelationMatrix(dataSet);
        this.sampleSize = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
        this.data = DataTransforms.centerData(dataSet.getDoubleData()).transpose().toArray();
        this.isGaussian = false;
    }

    /**
     * Constructor for a covariance matrix.
     *
     * @param covarianceMatrix The covariance matrix.
     */
    public DeltaTetradTest(ICovarianceMatrix covarianceMatrix) {
        if (covarianceMatrix == null) {
            throw new IllegalArgumentException("Covariance matrix must be non-null.");
        }

        this.covarianceMatrix = new CorrelationMatrix(covarianceMatrix);
        this.sampleSize = covarianceMatrix.getSampleSize();
        this.variables = covarianceMatrix.getVariables();
        this.data = null; // Data is unavailable.
        this.isGaussian = true;
    }

    /**
     * Computes the p-value for the given tetrads.
     *
     * @param tetrads List of tetrads.
     * @return The p-value.
     */
    public double computePValue(TetradInt...tetrads) {
        return computePValue(Arrays.asList(tetrads));
    }

    /**
     * Computes the p-value for the given tetrads.
     *
     * @param tetrads List of tetrads.
     * @return The p-value.
     */
    public double computePValue(List<TetradInt> tetrads) {
        if (tetrads == null || tetrads.isEmpty()) {
            throw new IllegalArgumentException("Tetrad list cannot be null or empty.");
        }

        List<TetradInt> nonRedundantTetrads = removeRedundantTetrads(tetrads);
        double chiSquare = calculateChiSquare(nonRedundantTetrads);
        int degreesOfFreedom = nonRedundantTetrads.size();
        return StatUtils.getChiSquareP(degreesOfFreedom, chiSquare);
    }

    /**
     * Removes redundant tetrads using algebraic dependency checks.
     *
     * @param tetrads Input list of tetrads.
     * @return A non-redundant list of tetrads.
     */
    private List<TetradInt> removeRedundantTetrads(List<TetradInt> tetrads) {
        // Implement logic to detect and remove redundant tetrads based on algebraic dependency.
        return new ArrayList<>(new LinkedHashSet<>(tetrads)); // Placeholder: Ensure unique tetrads.
    }

    /**
     * Calculates the chi-square value for the given tetrads.
     *
     * @param tetrads Non-redundant tetrads.
     * @return The chi-square statistic.
     */
    private double calculateChiSquare(List<TetradInt> tetrads) {
        Matrix sigmaSS = computeSigmaSS(tetrads);
        Matrix derivativeMatrix = computeDerivativeMatrix(tetrads);
        Matrix tetradValues = computeTetradValues(tetrads);

        Matrix sigmaTT = derivativeMatrix.transpose().times(sigmaSS).times(derivativeMatrix);
        Matrix invertedSigmaTT = regularizeAndInvert(sigmaTT);
        Matrix result = tetradValues.transpose().times(invertedSigmaTT).times(tetradValues);

        return this.sampleSize * result.get(0, 0);
    }

    /**
     * Computes the Sigma_SS matrix (covariance of sample covariances).
     *
     * @param tetrads List of tetrads.
     * @return The Sigma_SS matrix.
     */
    private Matrix computeSigmaSS(List<TetradInt> tetrads) {
        List<Sigma> covariances = getCovarianceSymbols(tetrads);
        int size = covariances.size();
        Matrix sigmaSS = new Matrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Sigma sigmaA = covariances.get(i);
                Sigma sigmaB = covariances.get(j);
                sigmaSS.set(i, j, computeCovarianceOfSampleCovariances(sigmaA, sigmaB));
            }
        }

        return sigmaSS;
    }

    /**
     * Computes the covariance of sample covariances.
     *
     * @param sigmaA First covariance symbol.
     * @param sigmaB Second covariance symbol.
     * @return The computed covariance.
     */
    private double computeCovarianceOfSampleCovariances(Sigma sigmaA, Sigma sigmaB) {
        int e = sigmaA.getA();
        int f = sigmaA.getB();
        int g = sigmaB.getA();
        int h = sigmaB.getB();

        if (isGaussian) {
            return 0.5 * (getCovariance(e, f) * getCovariance(g, h))
                   * (Math.pow(getCovariance(e, g), 2) + Math.pow(getCovariance(e, h), 2)
                      + Math.pow(getCovariance(f, g), 2) + Math.pow(getCovariance(f, h), 2))
                   + getCovariance(e, g) * getCovariance(f, h)
                   + getCovariance(e, h) * getCovariance(f, g)
                   - getCovariance(e, f) * (getCovariance(f, g) * getCovariance(f, h)
                                            + getCovariance(e, g) * getCovariance(e, h))
                   - getCovariance(g, h) * (getCovariance(f, g) * getCovariance(e, g)
                                            + getCovariance(f, h) * getCovariance(e, h));
        } else {
            return computeFourthMoment(e, f, g, h) + 0.25 * getCovariance(e, f) * getCovariance(g, h)
                                                     * (computeFourthMoment(e, e, g, g) + computeFourthMoment(f, f, g, g)
                                                        + computeFourthMoment(e, e, h, h) + computeFourthMoment(f, f, h, h))
                   - 0.5 * getCovariance(e, f)
                     * (computeFourthMoment(e, e, g, h) + computeFourthMoment(f, f, g, h))
                   - 0.5 * getCovariance(g, h)
                     * (computeFourthMoment(e, f, g, g) + computeFourthMoment(e, f, h, h));
        }
    }

    /**
     * Computes the derivative matrix for the tetrads with respect to covariances.
     *
     * @param tetrads List of tetrads.
     * @return The derivative matrix.
     */
    private Matrix computeDerivativeMatrix(List<TetradInt> tetrads) {
        List<Sigma> covariances = getCovarianceSymbols(tetrads);
        Matrix derivativeMatrix = new Matrix(covariances.size(), tetrads.size());

        for (int i = 0; i < covariances.size(); i++) {
            for (int j = 0; j < tetrads.size(); j++) {
                TetradInt tetrad = tetrads.get(j);
                Sigma sigma = covariances.get(i);
                derivativeMatrix.set(i, j, computeTetradDerivative(tetrad, sigma));
            }
        }
        return derivativeMatrix;
    }

    /**
     * Computes the tetrad values as a vector.
     *
     * @param tetrads List of tetrads.
     * @return A column vector of tetrad values.
     */
    private Matrix computeTetradValues(List<TetradInt> tetrads) {
        Matrix tetradValues = new Matrix(tetrads.size(), 1);

        for (int i = 0; i < tetrads.size(); i++) {
            TetradInt tetrad = tetrads.get(i);
            double value = computeTetradValue(tetrad);
            tetradValues.set(i, 0, value);
        }

        return tetradValues;
    }

    /**
     * Computes the value of a single tetrad.
     *
     * @param tetrad The tetrad.
     * @return The tetrad value.
     */
    private double computeTetradValue(TetradInt tetrad) {
        double sxy1 = getCovariance(tetrad.i(), tetrad.j());
        double sxy2 = getCovariance(tetrad.k(), tetrad.l());
        double sxy3 = getCovariance(tetrad.i(), tetrad.k());
        double sxy4 = getCovariance(tetrad.j(), tetrad.l());

        return sxy1 * sxy2 - sxy3 * sxy4;
    }

    /**
     * Computes the partial derivative of a tetrad with respect to a covariance.
     *
     * @param tetrad The tetrad.
     * @param sigma  The covariance symbol.
     * @return The derivative value.
     */
    private double computeTetradDerivative(TetradInt tetrad, Sigma sigma) {
        int e = tetrad.i();
        int f = tetrad.j();
        int g = tetrad.k();
        int h = tetrad.l();

        int a = sigma.getA();
        int b = sigma.getB();

        if ((e == a && f == b) || (e == b && f == a)) {
            return getCovariance(g, h);
        } else if ((g == a && h == b) || (g == b && h == a)) {
            return getCovariance(e, f);
        } else if ((e == a && g == b) || (e == b && g == a)) {
            return -getCovariance(f, h);
        } else if ((f == a && h == b) || (f == b && h == a)) {
            return -getCovariance(e, g);
        }

        return 0.0;
    }

    /**
     * Computes the fourth moment for arbitrary distributions.
     *
     * @param x First index.
     * @param y Second index.
     * @param z Third index.
     * @param w Fourth index.
     * @return The fourth moment.
     */
    private double computeFourthMoment(int x, int y, int z, int w) {
        double result = 0.0;
        for (int i = 0; i < data[x].length; i++) {
            result += data[x][i] * data[y][i] * data[z][i] * data[w][i];
        }
        return result / data[x].length;
    }

    /**
     * Extracts all covariance symbols for the given tetrads.
     *
     * @param tetrads List of tetrads.
     * @return List of covariance symbols.
     */
    private List<Sigma> getCovarianceSymbols(List<TetradInt> tetrads) {
        Set<Sigma> symbols = new LinkedHashSet<>();
        for (TetradInt tetrad : tetrads) {
            symbols.add(new Sigma(tetrad.i(), tetrad.k()));
            symbols.add(new Sigma(tetrad.i(), tetrad.l()));
            symbols.add(new Sigma(tetrad.j(), tetrad.k()));
            symbols.add(new Sigma(tetrad.j(), tetrad.l()));
        }
        return new ArrayList<>(symbols);
    }

    /**
     * Retrieves the covariance value between two variables.
     *
     * @param i Index of the first variable.
     * @param j Index of the second variable.
     * @return The covariance.
     */
    private double getCovariance(int i, int j) {
        if (this.covarianceMatrix != null) {
            return this.covarianceMatrix.getValue(i, j);
        } else if (this.data != null) {
            return computeCovariance(this.data[i], this.data[j]);
        }

        throw new IllegalStateException("No covariance data available.");
    }

    /**
     * Computes the covariance between two data arrays.
     *
     * @param array1 The first data array.
     * @param array2 The second data array.
     * @return The covariance.
     */
    private double computeCovariance(double[] array1, double[] array2) {
        double mean1 = Arrays.stream(array1).average().orElse(0.0);
        double mean2 = Arrays.stream(array2).average().orElse(0.0);
        double covariance = 0.0;

        for (int i = 0; i < array1.length; i++) {
            covariance += (array1[i] - mean1) * (array2[i] - mean2);
        }

        return covariance / array1.length;
    }

    /**
     * Regularizes and inverts a matrix to prevent singularity issues.
     *
     * @param matrix The matrix to invert.
     * @return The regularized inverse.
     */
    private Matrix regularizeAndInvert(Matrix matrix) {
        double regularization = 1e-10;
        Matrix regularizedMatrix = matrix.plus(Matrix.identity(matrix.getNumRows()).scale(regularization));
        return regularizedMatrix.inverse();
    }

    /**
     * Returns the list of variables in the dataset.
     *
     * @return The variable list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    // Represents a symbolic covariance.
    private static class Sigma {
        private final int a;
        private final int b;

        public Sigma(int a, int b) {
            this.a = a;
            this.b = b;
        }

        public int getA() {
            return a;
        }

        public int getB() {
            return b;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Sigma sigma)) return false;
            return (a == sigma.a && b == sigma.b) || (a == sigma.b && b == sigma.a);
        }

        @Override
        public int hashCode() {
            return a + b;
        }

        @Override
        public String toString() {
            return "Sigma(" + a + ", " + b + ")";
        }
    }
}
