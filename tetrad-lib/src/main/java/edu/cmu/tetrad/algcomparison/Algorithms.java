package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of algorithms to be compared.
 * @author Joseph Ramsey
 */
public class Algorithms {
    private List<Algorithm> algorithms = new ArrayList<>();

    public Algorithms() {}

    /**
     * Adds an algorithm.
     * @param algorithm The algorithmt to add.
     */
    public void add(Algorithm algorithm) {
        algorithms.add(algorithm);
    }

    /**
     * Returns the list of algorithms.
     * @return A copy of the list of algorithms that have been added, in that order.
     */
    public List<Algorithm> getAlgorithms() {
        return new ArrayList<>(algorithms);
    }
}
