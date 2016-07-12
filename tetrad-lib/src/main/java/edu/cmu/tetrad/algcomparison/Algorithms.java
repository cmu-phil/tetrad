package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 7/12/16.
 */
public class Algorithms {
    private List<Algorithm> algorithms = new ArrayList<>();

    public Algorithms() {}

    public void add(Algorithm algorithm) {
        algorithms.add(algorithm);
    }

    public List<Algorithm> getAlgorithms() {
        return new ArrayList<>(algorithms);
    }
}
