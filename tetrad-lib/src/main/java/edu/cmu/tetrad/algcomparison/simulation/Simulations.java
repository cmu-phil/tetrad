package edu.cmu.tetrad.algcomparison.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of simulations to be compared.
 *
 * @author jdramsey
 */
public class Simulations {
    static final long serialVersionUID = 23L;
    private List<Simulation> simulations = new ArrayList<>();

    public Simulations() {
    }

    /**
     * Adds an simulation.
     *
     * @param simulation The simulation to add.
     */
    public void add(Simulation simulation) {
        simulations.add(simulation);
    }

    /**
     * Returns the list of simulations.
     *
     * @return A copy of the list of simulations that have been added, in that order.
     */
    public List<Simulation> getSimulations() {
        return new ArrayList<>(simulations);
    }
}
