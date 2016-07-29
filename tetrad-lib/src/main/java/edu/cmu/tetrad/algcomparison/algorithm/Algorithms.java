package edu.cmu.tetrad.algcomparison.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of algorithm to be compared.
 *
 * @author jdramsey
 */
public class Algorithms {
    private List<Algorithm> algorithms = new ArrayList<>();

    public Algorithms() {
    }

    /**
     * Adds an algorithm.
     *
     * @param algorithm The algorithmt to add.
     */
    public void add(Algorithm algorithm) {
        algorithms.add(algorithm);
    }

    /**
     * Returns the list of algorithm.
     *
     * @return A copy of the list of algorithm that have been added, in that order.
     */
    public List<Algorithm> getAlgorithms() {
        return new ArrayList<>(algorithms);
    }
}
