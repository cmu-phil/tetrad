package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.graph.RandomMim;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple harness / demo driver for NtadExplorer.
 * <p>
 * You can call runDemo(...) from a unit test or from main(...) once you have a DataSet and a corresponding Cca
 * instance.
 */
public final class NtadExplorerHarness {

    private NtadExplorerHarness() {
        // utility
    }

    /**
     * Run the ntad explorer on the given dataset and CCA test, using ALL variables in the dataset as the candidate
     * list.
     *
     * @param data       The dataset.
     * @param ccaTest    A Cca instance built from the same data (same column order).
     * @param blockSize  Size m of each block (A,B), so each ntad uses 2m variables.
     * @param maxResults Maximum number of rank-deficient ntads to report.
     * @param alpha      Significance level used for the rank test.
     */
    public static void runDemo(DataSet data,
                               Cca ccaTest,
                               int blockSize,
                               int maxResults,
                               double alpha) {

        // Use all variables in the dataset as the candidate sublist.
        List<Node> vars = new ArrayList<>(data.getVariables());

        List<NtadExplorer.NtadResult> results =
                NtadExplorer.listRankDeficientNtads(
                        new CorrelationMatrix(data),
                        vars,
                        blockSize,
                        maxResults,
                        alpha,
                        ccaTest
                );

        // Pretty-print results.
        System.out.println("=== NtadExplorer Demo ===");
        System.out.println("Variables: " + vars.size());
        System.out.println("Block size m: " + blockSize + " (ntads use 2m variables)");
        System.out.println("Max results: " + maxResults);
        System.out.println("Alpha: " + alpha);
        System.out.println("Found " + results.size() + " rank-deficient blocks.\n");

        int idx = 1;
        for (NtadExplorer.NtadResult r : results) {
            System.out.println("Result " + idx++ + ":");
            System.out.println("  A: " + names(r.getBlockA()));
            System.out.println("  B: " + names(r.getBlockB()));
            System.out.println("  blockSize: " + r.getBlockSize());
            System.out.println("  estimated rank: " + r.getRank());
            System.out.println("  p-value(H0: rank ≤ m-1): " + r.getPValue());
            System.out.println();
        }
    }

    /**
     * Runs the NTAD Explorer demo using the specified dataset, variable list, CCA test, and parameters.
     * This method outputs results with details on rank-deficient NTADs for the given configurations.
     *
     * @param data       The dataset to be analyzed.
     * @param vars       A list of variables to consider for NTAD analysis.
     * @param ccaTest    An instance of CCA (Canonical Correlation Analysis) prepared using the same dataset.
     * @param blockSize  The size m of each block (A, B) used in the NTAD analysis, where each NTAD uses 2m variables.
     * @param maxResults The maximum number of rank-deficient NTAD results to output.
     * @param alpha      The significance level for the rank-deficiency test.
     */
    public static void runDemo(DataSet data,
                               List<Node> vars,
                               Cca ccaTest,
                               int blockSize,
                               int maxResults,
                               double alpha) {

        List<NtadExplorer.NtadResult> results =
                NtadExplorer.listRankDeficientNtads(
                        new CorrelationMatrix(data),
                        vars,
                        blockSize,
                        maxResults,
                        alpha,
                        ccaTest
                );

        System.out.println("=== NtadExplorer Demo (custom var list) ===");
        System.out.println("Sublist size: " + vars.size());
        System.out.println("Block size m: " + blockSize + " (ntads use 2m variables)");
        System.out.println("Max results: " + maxResults);
        System.out.println("Alpha: " + alpha);
        System.out.println("Found " + results.size() + " rank-deficient blocks.\n");

        int idx = 1;
        for (NtadExplorer.NtadResult r : results) {
            System.out.println("Result " + idx++ + ":");
            System.out.println("  A: " + names(r.getBlockA()));
            System.out.println("  B: " + names(r.getBlockB()));
            System.out.println("  blockSize: " + r.getBlockSize());
            System.out.println("  estimated rank: " + r.getRank());
            System.out.println("  p-value(H0: rank ≤ m-1): " + r.getPValue());
            System.out.println();
        }
    }

    // Helper to turn a list of Nodes into a comma-separated string of names.
    private static String names(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nodes.get(i).getName());
        }
        return sb.toString();
    }

    /**
     * The entry point of the application. It demonstrates the creation of a random graph,
     * parameterized estimation, data simulation, and NTAD exploration using Canonical Correlation Analysis (CCA).
     *
     * @param args Command-line arguments passed to the application.
     * @throws Exception if any error occurs during the execution of the process.
     */
    public static void main(String[] args) throws Exception {
        Graph graph = new RandomMim().createGraph(new Parameters());

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(100, false);

        boolean correlations = true;
        int nEff = data.getNumRows();
        Cca ccaTest = new Cca(data.getCorrelationMatrix().getSimpleMatrix(), correlations, nEff); // <-- adjust constructor as needed.
        runDemo(data, ccaTest, 3, 50, 0.001);
    }
}