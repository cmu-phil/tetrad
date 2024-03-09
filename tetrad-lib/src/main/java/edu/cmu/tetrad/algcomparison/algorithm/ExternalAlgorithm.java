package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Tags an an algorithm that loads up external graphs for inclusion in reports.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public abstract class ExternalAlgorithm implements Algorithm {

    /**
     * The path to the external graph.
     */
    protected String path;

    /**
     * The simulation.
     */
    protected Simulation simulation;

    /**
     * The index of the simulation.
     */
    protected int simIndex = -1;

    /**
     * The parameters used in the search.
     */
    protected List<String> usedParameters = new ArrayList<>();

    /**
     * <p>Constructor for ExternalAlgorithm.</p>
     */
    public ExternalAlgorithm() {

    }

    /**
     * <p>Setter for the field <code>path</code>.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * <p>Setter for the field <code>simIndex</code>.</p>
     *
     * @param simIndex a int
     */
    public void setSimIndex(int simIndex) {
        this.simIndex = simIndex;
    }

    /**
     * <p>Getter for the field <code>simulation</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     */
    public Simulation getSimulation() {
        return this.simulation;
    }

    /**
     * <p>Setter for the field <code>simulation</code>.</p>
     *
     * @param simulation a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     */
    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    /**
     * <p>getElapsedTime.</p>
     *
     * @param dataSet    a {@link edu.cmu.tetrad.data.DataModel} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a long
     */
    public abstract long getElapsedTime(DataModel dataSet, Parameters parameters);

    /**
     * <p>getParameters.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getParameters() {
        return this.usedParameters;
    }

    /**
     * <p>getNumDataModels.</p>
     *
     * @return a int
     */
    public int getNumDataModels() {
        return this.simulation.getNumDataModels();
    }

    /**
     * <p>getIndex.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataModel} object
     * @return a int
     */
    public int getIndex(DataModel dataSet) {
        int index = -1;

        for (int i = 0; i < getNumDataModels(); i++) {
            if (dataSet == this.simulation.getDataModel(i)) {
                index = i + 1;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("Not a dataset for this simulation.");
        }
        return index;
    }

}
