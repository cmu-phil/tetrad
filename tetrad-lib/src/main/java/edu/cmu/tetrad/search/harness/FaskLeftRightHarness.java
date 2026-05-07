package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Harness for tuning and comparing FASK left-right pairwise orientation rules
 * on random residualized linear SEMs with skewed errors.
 *
 * x
 * <ul>
 *   <li>Generate a random residualized graph using RandomGraph.randomCyclicGraph3(...)</li>
 *   <li>Simulate data from a linear SEM with Exp(1) errors</li>
 *   <li>Standardize the data</li>
 *   <li>For each singly-directed true edge Xi -> Xj, evaluate
 *       Fask.leftRightDiff(data[i], data[j], pwRule)</li>
 *   <li>If diff > 0, predict Xi -> Xj; otherwise predict Xj -> Xi</li>
 *   <li>Count misorientations</li>
 * </ul>
 *
 * <p>Two-cycles are skipped, since the pairwise rule returns only one direction.
 */
public final class FaskLeftRightHarness {

    // ----------------------------
    // User-facing defaults
    // ----------------------------

    /**
     * Number of independent graph/data replicates.
     */
    private static final int NUM_REPLICATES = 100;

    /**
     * Sample size per replicate.
     */
    private static final int SAMPLE_SIZE = 1000;

    /**
     * Number of measured variables.
     */
    private static final int NUM_MEASURES = 10;

    /**
     * Average degree used in the graph generator.
     */
    private static final int AVG_DEGREE = 2;

    private static final DecimalFormat DF = new DecimalFormat("0.000");

    private FaskLeftRightHarness() {
    }

    /**
     * Main method for executing the FASK leftRightDiff harness.
     * This method runs experiments for different pairwise rules,
     * applying various configurations of cyclic and standardization parameters.
     * Results from each configuration are summarized and printed to the console.
     *
     * @param args Command-line arguments (not used in this implementation).
     */
    public static void main(String[] args) {
        System.out.println("FASK leftRightDiff harness");
        System.out.println("Replicates = " + NUM_REPLICATES);
        System.out.println("Sample size = " + SAMPLE_SIZE);
        System.out.println("Num measures = " + NUM_MEASURES);
        System.out.println("Average degree = " + AVG_DEGREE);
        System.out.println();

        for (int rule = 1; rule <= 5; rule++) {
            HarnessSummary summary = runHarness(rule, NUM_REPLICATES, true, true);
            printSummary("rule=" + rule, summary);
        }

        System.out.println();

        for (int rule = 1; rule <= 5; rule++) {
            HarnessSummary summary = runHarness(rule, NUM_REPLICATES, false, true);
            printSummary("rule=" + rule, summary);
        }

        System.out.println();

        for (int rule = 1; rule <= 5; rule++) {
            HarnessSummary summary = runHarness(rule, NUM_REPLICATES, true, false);
            printSummary("rule=" + rule, summary);
        }

        System.out.println();

        for (int rule = 1; rule <= 5; rule++) {
            HarnessSummary summary = runHarness(rule, NUM_REPLICATES, false, false);
            printSummary("rule=" + rule, summary);
        }
    }

    /**
     * Runs the harness for a given pairwise rule across multiple replicates.
     *
     * @param pwRule      pairwise rule number, 1..5
     * @param replicates  number of graph/data replicates
     * @param residualized  whether to residualize the data
     * @param standardize   whether to standardize the data
     * @return aggregated summary
     */
    private static HarnessSummary runHarness(int pwRule, int replicates, boolean residualized, boolean standardize) {
        long totalMisoriented = 0L;
        long totalEligible = 0L;
        long totalSkippedTwoCycles = 0L;

        List<Double> replicateErrorRates = new ArrayList<>();

        for (int r = 0; r < replicates; r++) {
            Graph graph = generateRandomAcyclicGraph();

            DataSet dataSet = simulateData(graph);

            graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

            DataSet z = standardize ? DataTransforms.standardizeData(dataSet) : DataTransforms.center(dataSet);

            double[][] data = z.getDoubleData().transpose().toArray(); // vars x N
            List<Node> nodes = z.getVariables();

            int misoriented = 0;
            int eligible = 0;
            int skippedTwoCycles = 0;

            for (Edge edge : graph.getEdges()) {
                Node xi = edge.getNode1();
                Node xj = edge.getNode2();

                int i = nodes.indexOf(xi);
                int j = nodes.indexOf(xj);

                boolean iToJ = graph.isParentOf(xi, xj);
                boolean jToI = graph.isParentOf(xj, xi);

                // Only evaluate adjacent pairs with exactly one true direction.
                if (!iToJ && !jToI) {
                    continue;
                }

                if (iToJ && jToI) {
                    skippedTwoCycles++;
                    continue;
                }

                eligible++;

                double diff;

                if (pwRule >= 1 && pwRule <= 5) {
                    if (residualized) {
                        diff = Fask.leftRightDiffResidualized(pwRule, graph, xi, xj, nodes, data);
                    } else {
                        diff = Fask.leftRightDiff(data[i], data[j], pwRule);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid pairwise rule: " + pwRule);
                }

                boolean predictIToJ = diff > 0.0;
                boolean correct = (predictIToJ && iToJ) || (!predictIToJ && jToI);

                if (!correct) {
                    misoriented++;
                }
            }

            totalMisoriented += misoriented;
            totalEligible += eligible;
            totalSkippedTwoCycles += skippedTwoCycles;

            double replicateRate = eligible == 0 ? Double.NaN : ((double) misoriented / eligible);
            replicateErrorRates.add(replicateRate);
        }

        return new HarnessSummary(standardize, residualized, totalMisoriented, totalEligible, totalSkippedTwoCycles, replicateErrorRates);
    }

    /**
     * Generates a random residualized graph using the settings described by the user.
     */
    private static Graph generateRandomCyclicGraph() {
        Parameters parameters = new Parameters();

        // Keep user's requested settings.
        // If your Params constant is actually NUM_MEASURES rather than NUM_MEASAURES,
        // replace accordingly.
        parameters.set(Params.NUM_MEASURES, NUM_MEASURES);
        parameters.set(Params.AVG_DEGREE, AVG_DEGREE);
//        parameters.set(Params.COEF_LOW, 0.1);
//        parameters.set(Params.COEF_HIGH, 0.7);
//        parameters.set(Params.CYCLIC_COEF_LOW, 0.1);
//        parameters.set(Params.CYCLIC_COEF_HIGH, 0.7);

        return RandomGraph.randomCyclicGraph3(
                parameters.getInt(Params.NUM_MEASURES),
                parameters.getInt(Params.AVG_DEGREE) * parameters.getInt(Params.NUM_MEASURES) / 2,
                parameters.getInt(Params.MAX_DEGREE),
                parameters.getDouble(Params.PROB_CYCLE),
                parameters.getDouble(Params.PROB_TWO_CYCLE)
        );
    }

    private static Graph generateRandomAcyclicGraph() {
        Parameters parameters = new Parameters();

        // Keep user's requested settings.
        // If your Params constant is actually NUM_MEASURES rather than NUM_MEASAURES,
        // replace accordingly.
        parameters.set(Params.NUM_MEASURES, NUM_MEASURES);
        parameters.set(Params.AVG_DEGREE, AVG_DEGREE);

        return RandomGraph.randomGraph(
                parameters.getInt("numMeasures"),
                0,
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                100,
                100,
                100,
                false
        );
    }

    /**
     * Simulates skewed linear SEM data using Exp(1) errors.
     */
    private static DataSet simulateData(Graph graph) {
        Parameters parameters = new Parameters();

        parameters.set(Params.CUSTOM_NOISE_OPTION, 2);
        parameters.set(Params.CUSTOM_NOISE_EXPRESSION, "Gumbel(0, 1)");

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm, parameters);

        try {
            return im.simulateData(SAMPLE_SIZE, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printSummary(String label, HarnessSummary summary) {
        double overallErrorRate = summary.totalEligible == 0
                ? Double.NaN
                : ((double) summary.totalMisoriented / summary.totalEligible);

        double meanReplicateError = mean(summary.replicateErrorRates);
        double sdReplicateError = sd(summary.replicateErrorRates, meanReplicateError);

        System.out.println(
                label
                        + " | " + (summary.standardize ? "standardized" : "center")
                        + " | " + (summary.residualized ? "residualized" : "non-residualized")
                        + " | misoriented=" + summary.totalMisoriented
                        + " / eligible=" + summary.totalEligible
                        + " | overallError=" + DF.format(overallErrorRate)
//                        + " | meanRepError=" + DF.format(meanReplicateError)
                        + " | sdRepError=" + DF.format(sdReplicateError)
                        + " | skippedTwoCycles=" + summary.totalSkippedTwoCycles
        );
    }

    private static double mean(List<Double> values) {
        double sum = 0.0;
        int count = 0;

        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += v;
                count++;
            }
        }

        return count == 0 ? Double.NaN : sum / count;
    }

    private static double sd(List<Double> values, double mean) {
        if (Double.isNaN(mean)) {
            return Double.NaN;
        }

        double sumSq = 0.0;
        int count = 0;

        for (double v : values) {
            if (!Double.isNaN(v)) {
                double d = v - mean;
                sumSq += d * d;
                count++;
            }
        }

        return count <= 1 ? 0.0 : Math.sqrt(sumSq / (count - 1));
    }

    private record HarnessSummary(
            boolean standardize,
            boolean residualized,
            long totalMisoriented,
            long totalEligible,
            long totalSkippedTwoCycles,
            List<Double> replicateErrorRates
    ) {
    }
}