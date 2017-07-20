package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author jdramsey
 */
public class ExampleSave {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 1);
//        parameters.set("numMeasures", 20,100,1000);
        parameters.set("numMeasures",1000);
        parameters.set("numLatents", 200);
        parameters.set("avgDegree", 2);
        parameters.set("sampleSize", 1000);
//        parameters.set("maxCategories",3);

//        Simulation simulation = new BayesNetSimulation(new RandomForward());
        Simulation simulation = new SemSimulation(new RandomForward());
        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.saveToFiles("comparison1", simulation, parameters);
    }
}



