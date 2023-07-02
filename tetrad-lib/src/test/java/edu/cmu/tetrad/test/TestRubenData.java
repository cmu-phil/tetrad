package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class TestRubenData {

    public static void main(String... arge) {
        new TestRubenData().test3();
    }

    public void test1() {
        NumberFormat nf = new DecimalFormat("0.000");
        int count = 40;
        double aps = 0.0, ars = 0.0, ops = 0.0, ors = 0.0, bds = 0.0, es = 0.0; // sums.

        for (int index = 0; index < count; index++) {
            String path1 = "/Users/josephramsey/Downloads/ruben_data/bold_data/bold_data_" + index + ".txt";
            String path2 = "/Users/josephramsey/Downloads/ruben_data/graphs/graph_" + index + ".txt";

            try {
                DataSet data = SimpleDataLoader.loadContinuousData(new File(path1),
                        "//",
                        '\"', "*", true, Delimiter.COMMA);

                Graph graph = GraphSaveLoadUtils.loadGraphTxt(new File(path2));

                graph = GraphSearchUtils.cpdagForDag(graph);

                SemBicScore score = new SemBicScore(data);
                score.setPenaltyDiscount(2);
//                Fges alg = new Fges(score);
                PermutationSearch alg = new PermutationSearch(new Boss(score));

                long start = MillisecondTimes.cpuTimeMillis();
                Graph g = alg.search();
                long stop = MillisecondTimes.cpuTimeMillis();
                double elapsed = (stop - start) / 1000.0;

                g = GraphUtils.replaceNodes(g, graph.getNodes());

                double ap = new AdjacencyPrecision().getValue(graph, g, data);
                double ar = new AdjacencyRecall().getValue(graph, g, data);
                double op = new OrientationPrecision().getValue(graph, g, data);
                double or = new OrientationRecall().getValue(graph, g, data);
                double bd = new BicDiff().getValue(graph, g, data);

                aps += ap;
                ars += ar;
                ops += op;
                ors += or;
                bds += bd;

                es += elapsed;

                System.out.println(index + ". AP = " + nf.format(ap) + " AR = " + nf.format(ar) + " OP = " + nf.format(op) + " OR = " + nf.format(or) + " BD = " + nf.format(bd) + " E = " + nf.format(elapsed));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // averages
        double apa = aps / count;
        double ara = ars / count;
        double opa = ops / count;
        double ora = ors / count;
        double bda = bds / count;
        double ea = es / count;

        System.out.println();
        System.out.println("BOSS averages:");
        System.out.println("AP = " + nf.format(apa) + " AR = " + nf.format(ara) + " OP = " + nf.format(opa) + " OR = " + nf.format(ora) + "BD = " + nf.format(bda) + " E = " + nf.format(ea));
    }

    /**
     * Test for Bryan.
     */
    public void test2() {
        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_MEASURES, 100);
        parameters.set(Params.AVG_DEGREE, 20);
        parameters.set(Params.COEF_LOW, 0);
        parameters.set(Params.COEF_HIGH, 1);
        parameters.set(Params.VAR_LOW, 1);
        parameters.set(Params.VAR_HIGH, 1);
        parameters.set(Params.PENALTY_DISCOUNT, 2);
        parameters.set(Params.NUM_RUNS, 1);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Grasp(
                new FisherZ(),
                new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Statistics statistics = new Statistics();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new OrientationPrecision());
        statistics.add(new OrientationRecall());
        statistics.add(new BicDiff());
        statistics.add(new ElapsedCpuTime());

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);
        comparison.compareFromSimulations("erich_out", simulations, algorithms, statistics, parameters);
    }

    public void test3() {
        String path = "/Users/josephramsey/Downloads/emails.txt";

        File file = new File(path);

        try {
            BufferedReader in = new BufferedReader(new FileReader(file));

            String line = in.readLine();
            String[] tokens = line.split(";");

            Set<String> emails = new HashSet<>();

            for (String token : tokens) {
                emails.add(token.trim());
            }

            System.out.println(emails.size());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
