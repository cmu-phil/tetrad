package edu.cmu.tetrad.search.ntad_test;

import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.sqrt;

/**
 * NtadTest is an abstract base class for implementing ntad-based statistical tests. A ntad specifies structural
 * relationships among variables, and this class provides methods to compute covariance matrices, generate combinations,
 * and perform resampling for such tests.
 *
 * @author bryanandrews
 */
public abstract class NtadTest {
    protected SimpleMatrix df;
    protected int p;
    protected int sampleSize;
    protected int ess;
    protected SimpleMatrix S;

    /**
     * Constructs an instance of NtadTest using the provided data matrix, whether to compute correlations, and the
     * effective sample size (ESS).
     *
     * @param df           the input data matrix represented as a SimpleMatrix object, where each row is an observation
     *                     and each column is a variable.
     * @param correlations a boolean flag indicating whether the provided data matrix should be interpreted directly
     *                     as a correlation matrix. If false, correlations are computed from the data matrix.
     * @param ess          the effective sample size, which must be -1 (to use the sample size from the data matrix)
     *                     or greater than 1.
     * @throws IllegalArgumentException if ess is not -1 and not greater than 1.
     */
    public NtadTest(SimpleMatrix df, boolean correlations, int ess) {
        if (!(ess == -1  || ess > 1)) {
            throw new IllegalArgumentException("Ess should be -1 or > 0: " + ess);
        }

        this.df = df;
        this.sampleSize = df.getNumRows();
        this.ess = ess == -1 ? this.sampleSize : ess;
        this.p = df.getNumCols();

        if (correlations) {
            this.S = df;
        } else {
            this.S = computeCorrelations(df);
        }
    }

    /**
     * Generates a list of strings representing the variable indices from 0 to p-1.
     *
     * @return a list of strings where each string represents a variable index in the range from 0 (inclusive) to p
     * (exclusive).
     */
    public List<String> variables() {
        return IntStream.range(0, p).mapToObj(String::valueOf).collect(Collectors.toList());
    }

    /**
     * Computes the value of a statistical test based on the given ntad configuration, with optional resampling. An
     * ntad is a set of indices representing structural relationships among variables. This method evaluates the
     * statistical consistency of such configurations.
     *
     * @param ntad      a 2D integer array where each inner array defines a ntad configuration. Each configuration
     *                 specifies indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the
     *                 computation.
     * @param frac     a double value representing the fraction of data to use during resampling, ignored if resample is
     *                 false.
     * @return a double value representing the computed result of the statistical ntd test.
     */
    public abstract double ntad(int[][] ntad, boolean resample, double frac);

    /**
     * Generates all possible combinations of size k from a list of integers. Each combination is represented as an
     * array of integers, and all generated combinations are returned as a list.
     *
     * @param elements the list of integers to generate combinations from
     * @param k        the size of each combination
     * @return a list of integer arrays, where each array represents a unique combination of size k from the input list
     */
    protected List<int[]> generateCombinations(List<Integer> elements, int k) {
        List<int[]> combinations = new ArrayList<>();
        generateCombinationsRecursive(elements, new int[k], 0, 0, combinations);
        return combinations;
    }

    private void generateCombinationsRecursive(List<Integer> elements, int[] combination, int index, int start, List<int[]> result) {
        if (index == combination.length) {
            result.add(Arrays.copyOf(combination, combination.length));
            return;
        }
        for (int i = start; i < elements.size(); i++) {
            combination[index] = elements.get(i);
            generateCombinationsRecursive(elements, combination, index + 1, i + 1, result);
        }
    }

    /**
     * Checks whether a given value is present in the specified array.
     *
     * @param array the array of integers to search within
     * @param value the integer value to check for in the array
     * @return true if the specified value is found in the array; false otherwise
     */
    protected boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) return true;
        }
        return false;
    }

    /**
     * Computes the aggregate statistical measure based on a list of ntad configurations. Each configuration specifies
     * sets of indices representing structural relationships among variables. This method evaluates and combines results
     * for all provided configurations, with optional resampling.
     *
     * @param ntads     a list of 2D integer arrays where each array contains multiple ntad configurations. Each
     *                 configuration defines sets of indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the
     *                 computation.
     * @param frac     a double value representing the fraction of data to use during resampling, ignored if resample is
     *                 false.
     * @return a double value representing the sum of the statistical measures for all provided ntad configurations.
     */
    protected double ntads(List<int[][]> ntads, boolean resample, double frac) {
        return ntads.stream().mapToDouble(ntad -> ntad(ntad, resample, frac)).sum();
    }

    /**
     * Computes the correlation matrix for the given data matrix. The covariance matrix is calculated using the centered
     * data and normalizing by the number of observations minus one.
     *
     * @param data the input data matrix as a SimpleMatrix object, where each row represents an observation and each
     *             column represents a variable
     * @return a SimpleMatrix object representing the covariance matrix of the input data
     */
    protected SimpleMatrix computeCorrelations(SimpleMatrix data) {
        int n = data.getNumRows();
        int m = data.getNumCols();

        // Compute column means
        SimpleMatrix mean = new SimpleMatrix(1, m);
        for (int i = 0; i < m; i++) {
            mean.set(0, i, data.extractVector(false, i).elementSum() / n);
        }

        // Center the data
        SimpleMatrix centeredData = new SimpleMatrix(n, m);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                centeredData.set(i, j, data.get(i, j) - mean.get(0, j));
            }
        }

        // Covariance matrix: (X^T * X) / (n - 1)
        SimpleMatrix cov = centeredData.transpose().mult(centeredData).scale(1.0 / (n - 1));

        System.out.println("m = " + cov.getNumRows() + " n = " + cov.getNumCols());

        for (int i = 0; i < cov.getNumRows(); i++) {
            System.out.println(i + " " + cov.get(i, i));
        }

        SimpleMatrix corr = new SimpleMatrix(cov.getNumRows(), cov.getNumCols());

        for (int i = 0; i < cov.getNumRows(); i++) {
            for (int j = 0; j < cov.getNumCols(); j++) {
                corr.set(i, j, cov.get(i, j) / sqrt(cov.get(i, i) * cov.get(j, j)));
            }
        }

        return cov;
    }

    /**
     * Samples a subset of rows from the input matrix based on the specified fraction. The method randomly selects
     * unique rows from the given matrix and creates a new matrix containing the sampled rows.
     *
     * @param matrix the input matrix from which rows are sampled, represented as a SimpleMatrix object
     * @param frac   a double value representing the fraction of rows to sample, where 0.0 <= frac <= 1.0
     * @return a SimpleMatrix containing the sampled rows from the input matrix
     */
    protected SimpleMatrix sampleRows(SimpleMatrix matrix, double frac) {
        int numSamples = (int) Math.ceil(frac * matrix.getNumRows());
        List<Integer> sampledIndices = new ArrayList<>();
        Random rand = new Random();

        while (sampledIndices.size() < numSamples) {
            int idx = rand.nextInt(matrix.getNumRows());
            if (!sampledIndices.contains(idx)) {
                sampledIndices.add(idx);
            }
        }

        SimpleMatrix sampled = new SimpleMatrix(numSamples, matrix.getNumCols());
        for (int i = 0; i < sampledIndices.size(); i++) {
            sampled.setRow(i, 0, matrix.extractVector(true, sampledIndices.get(i)).getDDRM().getData());
        }
        return sampled;
    }

    /**
     * Computes the value of a statistical test based on the input ntad configuration. A ntad is a set of indices
     * specifying structural relationships between variables, and this method evaluates the statistical consistency of
     * such configurations.
     *
     * @param ntad a 2D integer array where each inner array defines an ntad configuration. Each ntad specifies indices
     *             representing a structural relationship among variables.
     * @return a double value representing the computed result of the statistical ntad test.
     */
    public abstract double ntad(int[][] ntad);

    /**
     * Computes the statistical test results for multiple sets of ntad configurations. A ntad is a set of indices
     * specifying structural relationships among variables, and this method evaluates the statistical consistency for
     * each provided configuration.
     *
     * @param ntads a series of 2D integer arrays, where each array contains multiple ntad configurations. Each
     *              configuration specifies a set of indices representing structural relationships among variables.
     * @return a double value representing the aggregated or combined result of the statistical tests applied to the
     * provided ntad configurations.
     */
    public abstract double ntads(int[][]... ntads);

    /**
     * Computes a statistical measure based on the input list of ntad configurations. Each ntad configuration represents
     * a set of structural relationships among variables. This method evaluates and combines the statistical results of
     * all provided configurations.
     *
     * @param ntads a list of 2D integer arrays where each array contains multiple ntad configurations. Each
     *              configuration is a set of integer indices representing structural relationships.
     * @return a double value representing the combined statistical measure for the provided ntad configurations.
     */
    public abstract double ntads(List<int[][]> ntads);

    /**
     * Checks if all ntads in the provided list have a value greater than the specified alpha.
     *
     * @param ntads The list of ntads to check.
     * @param alpha The threshold value.
     * @return true if all ntads are greater than alpha, false otherwise.
     */
    public boolean allGreaterThanAlpha(List<int[][]> ntads, double alpha) {
        return ntads.stream().allMatch(ntad -> ntad(ntad) > alpha);
    }

//    public int rank(int[][] ntad, boolean resample, double frac, double alpha) {
//        int[] a = ntad[0];
//        int[] b = ntad[1];
//
//        int minpq = Math.min(a.length, b.length);
//
//        for (int r = 0; r < a.length; r++) {
//            if (r >= minpq) {
//                continue;
//            }
//
//            if (ntad(ntad, resample, frac) > alpha) {
//                return r;
//            }
//        }
//
//        return minpq;
//    }
}

