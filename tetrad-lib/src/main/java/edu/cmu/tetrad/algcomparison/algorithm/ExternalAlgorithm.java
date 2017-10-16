package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Tags an an algorithm that loads up external graphs for inclusion in reports.
 *
 * @author jdramsey
 */
public abstract class ExternalAlgorithm implements Algorithm {
    protected String path;
    protected Simulation simulation;
    protected int simIndex = -1;
    protected List<String> usedParameters = new ArrayList<>();

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public void setSimIndex(int simIndex) {
        this.simIndex = simIndex;
    }
    public Simulation getSimulation() {
        return simulation;
    }
    public abstract long getElapsedTime(DataModel dataSet, Parameters parameters);
    public List<String> getParameters() {
        return usedParameters;
    }
    public int getNumDataModels() {
        return simulation.getNumDataModels();
    }
    public int getIndex(DataModel dataSet) {
        int index = -1;

        for (int i = 0; i < getNumDataModels(); i++) {
            if (dataSet == simulation.getDataModel(i)) {
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
