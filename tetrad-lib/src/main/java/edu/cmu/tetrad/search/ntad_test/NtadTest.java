package edu.cmu.tetrad.search.ntad_test;

import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * NtadTest is an abstract base class for implementing tetrad-based statistical tests.
 * A tetrad specifies structural relationships among variables, and this class provides methods
 * to compute covariance matrices, generate combinations, and perform resampling for such tests.
 *
 * @author bryanandrews
 */
public abstract class NtadTest {
    protected SimpleMatrix df;
    protected int n;
    protected int p;
    protected SimpleMatrix S;

    /**
     * Constructs an NtadTest object based on the given data matrix. This method
     * initializes the instance by computing the number of rows, number of
     * columns, and the covariance matrix of the given data.
     *
     * @param df the input data matrix as a SimpleMatrix object, where each row
     *           represents an observation and each column represents a variable.
     */
    public NtadTest(SimpleMatrix df) {
        this.df = df;
        this.n = df.getNumRows();
        this.p = df.getNumCols();
        this.S = computeCovariance(df);
    }

    /**
     * Generates a list of strings representing the variable indices from 0 to p-1.
     *
     * @return a list of strings where each string represents a variable index
     *         in the range from 0 (inclusive) to p (exclusive).
     */
    public List<String> variables() {
        return IntStream.range(0, p).mapToObj(String::valueOf).collect(Collectors.toList());
    }

    /**
     * Computes the value of a statistical test based on the given tetrad configuration, with optional resampling.
     * A tetrad is a set of indices representing structural relationships among variables. This method evaluates
     * the statistical consistency of such configurations.
     *
     * @param tet a 2D integer array where each inner array defines a tetrad configuration. Each configuration
     *            specifies indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the computation.
     * @param frac a double value representing the fraction of data to use during resampling, ignored if resample is false.
     * @return a double value representing the computed result of the statistical tetrad test.
     */
    public abstract double tetrad(int[][] tet, boolean resample, double frac);

    /**
     * Generates all possible combinations of size k from a list of integers.
     * Each combination is represented as an array of integers, and all generated
     * combinations are returned as a list.
     *
     * @param elements the list of integers to generate combinations from
     * @param k the size of each combination
     * @return a list of integer arrays, where each array represents a unique combination
     *         of size k from the input list
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
     * Computes the aggregate statistical measure based on a list of tetrad configurations. Each configuration
     * specifies sets of indices representing structural relationships among variables. This method evaluates
     * and combines results for all provided configurations, with optional resampling.
     *
     * @param tets a list of 2D integer arrays where each array contains multiple tetrad configurations. Each
     *             configuration defines sets of indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the computation.
     * @param frac a double value representing the fraction of data to use during resampling, ignored if resample is false.
     * @return a double value representing the sum of the statistical measures for all provided tetrad configurations.
     */
    protected double tetrads(List<int[][]> tets, boolean resample, double frac) {
        return tets.stream().mapToDouble(tet -> tetrad(tet, resample, frac)).sum();
    }

    /**
     * Computes the covariance matrix for the given data matrix. The covariance matrix is calculated
     * using the centered data and normalizing by the number of observations minus one.
     *
     * @param data the input data matrix as a SimpleMatrix object, where each row represents an observation
     *             and each column represents a variable
     * @return a SimpleMatrix object representing the covariance matrix of the input data
     */
    protected SimpleMatrix computeCovariance(SimpleMatrix data) {
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
        return centeredData.transpose().mult(centeredData).scale(1.0 / (n - 1));
    }

    /**
     * Samples a subset of rows from the input matrix based on the specified fraction.
     * The method randomly selects unique rows from the given matrix and creates a new
     * matrix containing the sampled rows.
     *
     * @param matrix the input matrix from which rows are sampled, represented as a SimpleMatrix object
     * @param frac a double value representing the fraction of rows to sample, where 0.0 <= frac <= 1.0
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
     * Extracts a submatrix from the specified matrix by selecting the rows and columns
     * indicated by the provided indices. The resulting submatrix is composed of values
     * at the intersection of the specified rows and columns.
     *
     * @param matrix the input matrix as a SimpleMatrix object from which the submatrix will be extracted
     * @param rows an array of integers representing the row indices to include in the submatrix
     * @param cols an array of integers representing the column indices to include in the submatrix
     * @return a SimpleMatrix object representing the extracted submatrix
     */
    protected static SimpleMatrix extractSubMatrix(SimpleMatrix matrix, int[] rows, int[] cols) {
        SimpleMatrix subMatrix = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                subMatrix.set(i, j, matrix.get(rows[i], cols[j]));
            }
        }
        return subMatrix;
    }

    /**
     * Computes the value of a statistical test based on the input tetrad configuration. A tetrad is a set of indices
     * specifying structural relationships between variables, and this method evaluates the statistical consistency of
     * such configurations.
     *
     * @param tet a 2D integer array where each inner array defines a tetrad configuration. Each tetrad specifies
     *            indices representing a structural relationship among variables.
     * @return a double value representing the computed result of the statistical tetrad test.
     */
    public abstract double tetrad(int[][] tet);

    /**
     * Computes the statistical test results for multiple sets of tetrad configurations. A tetrad is a set of indices
     * specifying structural relationships among variables, and this method evaluates the statistical consistency for
     * each provided configuration.
     *
     * @param tets a series of 2D integer arrays, where each array contains multiple tetrad configurations. Each
     *             configuration specifies a set of indices representing structural relationships among variables.
     * @return a double value representing the aggregated or combined result of the statistical tests applied to the
     * provided tetrad configurations.
     */
    public abstract double tetrads(int[][]... tets);

    /**
     * Computes a statistical measure based on the input list of tetrad configurations. Each tetrad configuration
     * represents a set of structural relationships among variables. This method evaluates and combines the statistical
     * results of all provided configurations.
     *
     * @param tets a list of 2D integer arrays where each array contains multiple tetrad configurations. Each
     *             configuration is a set of integer indices representing structural relationships.
     * @return a double value representing the combined statistical measure for the provided tetrad configurations.
     */
    public abstract double tetrads(List<int[][]> tets);
}

