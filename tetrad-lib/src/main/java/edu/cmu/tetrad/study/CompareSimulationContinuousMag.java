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

import java.util.Calendar;
import java.text.SimpleDateFormat;

/**
 * A script to simulate data and run a comparison analysis on it.
 * 
 * MAG
 */

public class CompareSimulationContinuousMag {
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        // int sampleSize = 500;
        boolean hasLatent = true;

        parameters.set("numRuns", 6);
        parameters.set("numMeasures", 10, 15, 20);

        // Latents
        if (hasLatent) parameters.set("numLatents", 2, 4, 6);

        parameters.set("avgDegree", 2, 4, 6);
        parameters.set("sampleSize", 250, 500, 1000, 2000); // This varies.
        parameters.set("differentGraphs", true);

        parameters.set("alpha", 0.01);

        parameters.set("maxDegree", 20);

        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 0.7);

        parameters.set("varLow", 1.0);
        parameters.set("varHigh", 3.0);

//         parameters.set("colliderDiscoveryRule", 2, 3);
//         parameters.set("conflictRule", 1, 3);
        parameters.set("penaltyDiscount", 2); // originally is 1, 2, 3, 4
        parameters.set("discretize", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new ParameterColumn("numMeasures"));

        // Latents
        if (hasLatent) statistics.add(new ParameterColumn("numLatents"));

        statistics.add(new ParameterColumn("avgDegree"));
//        statistics.add(new ParameterColumn("alpha"));

//         statistics.add(new ParameterColumn("colliderDiscoveryRule"));
//         statistics.add(new ParameterColumn("conflictRule"));
//         statistics.add(new ParameterColumn("penaltyDiscount"));

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
        statistics.setWeight("TP", 1.0);
        statistics.setWeight("TR", 1.0);

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
        parameters.set("errorsNormal", false);

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(true);
        // comparison.setShowUtilities(true);
        // comparison.setParallelized(true);

        comparison.setComparisonGraph(Comparison.ComparisonGraph.MAG_of_the_true_DAG);

        // get time
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String timeNow = sdf.format(cal.getTime());

        // Latent
        if (hasLatent) {
            comparison.compareFromSimulations("comparison.continuous.mag", simulations,
                    "magLatent" + timeNow + ".txt", algorithms, statistics, parameters);
        } else {
            comparison.compareFromSimulations("comparison.continuous.mag", simulations,
                    "magNoLatent" + timeNow + ".txt", algorithms, statistics, parameters);
        }
    }
}