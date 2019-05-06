package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.PcAll;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Cpc;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicDTest;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.FisherZScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

import static edu.cmu.tetrad.performance.ComparisonParameters.Algorithm.GFCI;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class CompareSimulationContinuousPag {
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        int sampleSize = 500;
        int avgDegree = 6;
        int numMeasures = 10;

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", numMeasures);

        // Latents
//        parameters.set("numLatents", 2, 4, 6);

        parameters.set("avgDegree", avgDegree);
        parameters.set("sampleSize", sampleSize); // This varies.
        parameters.set("differentGraphs", true);

        parameters.set("alpha", 0.05, 0.01, 0.001);
        parameters.set("colliderDiscoveryRule", 1, 2, 3);
        parameters.set("conflictRule", 1, 2, 3);

        parameters.set("maxDegree", 100);

        parameters.set("penaltyDiscount", 2); // originally is 1, 2, 3, 4
        parameters.set("discretize", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));

        // Latents
//        statistics.add(new ParameterColumn("numLatents"));

        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new ParameterColumn("colliderDiscoveryRule"));
        statistics.add(new ParameterColumn("conflictRule"));
        statistics.add(new ParameterColumn("alpha"));
//        statistics.add(new ParameterColumn("penaltyDiscount"));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TailPrecision());
        statistics.add(new TailRecall());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 1.0);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 1.0);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new PcAll(new FisherZ()));
        algorithms.add(new PcAll(new SemBicTest()));

        algorithms.add(new Cpc(new FisherZ()));
        algorithms.add(new Cpc(new SemBicTest()));

        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new Fges(new FisherZScore()));

        algorithms.add(new Fci(new FisherZ()));
        algorithms.add(new Fci(new SemBicTest()));

        algorithms.add(new Gfci(new FisherZ(), new FisherZScore()));
        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));
        algorithms.add(new Gfci(new SemBicTest(), new FisherZScore()));
        algorithms.add(new Gfci(new SemBicTest(), new SemBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new SemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(true);
        // comparison.setShowUtilities(true);
        // comparison.setParallelized(true);

        comparison.setComparisonGraph(Comparison.ComparisonGraph.PAG_of_the_true_DAG);

        comparison.compareFromSimulations("comparison.continuous.pag", simulations, "noLatent_" + numMeasures + "_avgD_" + avgDegree + "_" + sampleSize + ".txt",
                algorithms, statistics, parameters);
    }
}