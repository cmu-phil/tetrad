package edu.cmu.tetrad.algcomparison.directory_joe.ps7z;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.util.Parameters;

public class ExampleSave {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 2);
//        parameters.set("numMeasures", 20,100,1000);
        parameters.set("numMeasures",100);
        parameters.set("numLatents", 10);
        parameters.set("avgDegree", 4);
        parameters.set("sampleSize", 1000);
//        parameters.set("maxCategories",3);

//        Simulation simulation = new BayesNetSimulation(new RandomForward());
        Simulation simulation = new SemSimulation(new RandomForward());
        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.saveToFiles("comparison12", simulation, parameters);
    }
}