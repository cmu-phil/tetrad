package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;

/**
 * Tags an an algorithm that loads up external graphs for inclusion in reports.
 *
 * @author jdramsey
 */
public interface ExternalAlgorithm extends Algorithm {
    void setSimulation(Simulation simulation);

    void setPath(String s);

    void setSimIndex(int simIndex);

    Simulation getSimulation();

    long getElapsedTime(DataModel dataSet, Parameters parameters);
}
