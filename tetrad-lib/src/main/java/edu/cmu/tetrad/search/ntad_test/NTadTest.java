package edu.cmu.tetrad.search.ntad_test;

import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class NTadTest {
    protected SimpleMatrix df;
    protected int n;
    protected int p;
    protected SimpleMatrix S;

    public NTadTest(SimpleMatrix df) {
        this.df = df;
        this.n = df.getNumRows();
        this.p = df.getNumCols();
        this.S = computeCovariance(df);
    }

    public List<String> variables() {
        return IntStream.range(0, p).mapToObj(String::valueOf).collect(Collectors.toList());
    }

    public abstract double tetrad(int[][] tet, boolean resample, double frac);

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

    protected boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) return true;
        }
        return false;
    }

    protected double tetrads(List<int[][]> tets, boolean resample, double frac) {
        return tets.stream().mapToDouble(tet -> tetrad(tet, resample, frac)).sum();
    }

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

