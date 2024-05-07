package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of algorithm to be compared.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Algorithms implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;


    /**
     * The list of algorithm.
     */
    private final List<Algorithm> algorithms = new ArrayList<>();

    /**
     * Constructs an empty list of algorithms.
     */
    public Algorithms() {
    }

    /**
     * Adds an algorithm.
     *
     * @param algorithm The algorithmt to add.
     */
    public void add(Algorithm algorithm) {
        this.algorithms.add(algorithm);
    }

    /**
     * Returns the list of algorithm.
     *
     * @return A copy of the list of algorithm that have been added, in that order.
     */
    public List<Algorithm> getAlgorithms() {
        return new ArrayList<>(this.algorithms);
    }
}
