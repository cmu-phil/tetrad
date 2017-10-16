package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class SpecialExampleSimulationClark {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 20);
        parameters.set("sampleSize", 1000);
        parameters.set("twoCycleAlpha", 1);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new TwoCycleTruePositive());
//        statistics.add(new TwoCycleFalseNegative());
//        statistics.add(new TwoCycleFalsePositive());


        // For randomm forward graph
        parameters.set("numMeasures", 10);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 2);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

//
//        statistics.setWeight("AP", 1.0);
//        statistics.setWeight("AR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fask());

        Simulations simulations = new Simulations();

//        simulations.add(new SpecialDataClark(new SpecialGraphClark()));
        simulations.add(new SpecialDataClark(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(true);
        comparison.setSaveGraphs(true);
        comparison.setSavePatterns(true);
        comparison.setSavePags(true);

//        comparison.saveToFiles("comparison", new SpecialDataClark(new SpecialGraphClark()), parameters);
        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}




