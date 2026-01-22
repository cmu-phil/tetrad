// File: edu/cmu/tetradapp/util/TrainAndResimulate.java
package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Simple driver:
 *   args[0] = input data path (tab-delimited with header)
 *   args[1] = input DAG path  (one directed edge per line, like "A -> B" or "A-->B" or "A->B")
 *   args[2] = output data path (tab-delimited)
 *   args[3] = output DAG path  (edge list)
 *   args[4] = (optional) nSamples (default = input N)
 *
 * Run example (from any directory, assuming your launch jar is on the classpath):
 *   java -cp tetrad-launch.jar:. edu.cmu.tetrad.sem.TrainAndResimulate in.tsv dag.txt out.tsv out_dag.txt 2000
 */
public final class TrainAndResimulate {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java ... TrainAndResimulate <inData.tsv> <inDag.txt> <outData.tsv> <outDag.txt> [nSamples]");
            System.exit(2);
        }

        String inData = args[0];
        String inDag  = args[1];
        String outData = args[2];
        String outDag  = args[3];

        DataSet data = SimpleDataLoader.loadMixedData(new File(inData), "//", '\"', "*", true, 5,
                Delimiter.TAB, false);

        Graph dag = GraphSaveLoadUtils.loadGraphTxt(new File(inDag));

        int nSamples = (args.length >= 5) ? Integer.parseInt(args[4]) : data.getNumRows();

    TrainedDagSimulatorGNM.Params p = new TrainedDagSimulatorGNM.Params();
        // You can tweak these quickly:
        p.hidden = 24;
        p.epochs = 300;
        p.lr = 0.01;
        p.l2 = 1e-4;
        p.batchSize = 64;
        p.seed = 12345L;

        TrainedDagSimulatorGNM sim = new TrainedDagSimulatorGNM(data, dag, p);
        sim.fit();

        TrainedDagSimulatorGNM.SimResult res = sim.simulate(nSamples);

        // Write outputs
        writeTabularMixed(outData, data.getVariables(), res.cont, res.disc);
        writeDagEdgeList(outDag, dag);

        System.out.println("Wrote: " + outData);
        System.out.println("Wrote: " + outDag);
        System.out.println("True DAG is the input DAG (saved to outDag).");
    }

    // -------------------- Data I/O (TSV) --------------------

    private static void writeTabularMixed(String path, List<Node> vars, double[][] cont, int[][] disc) throws IOException {
        int n = cont.length;
        int p = vars.size();

        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            // header
            for (int j = 0; j < p; j++) {
                if (j > 0) out.print('\t');
                out.print(vars.get(j).getName());
            }
            out.println();

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    if (j > 0) out.print('\t');
                    Node v = vars.get(j);
                    if (v instanceof DiscreteVariable) out.print(disc[i][j]);
                    else out.print(cont[i][j]);
                }
                out.println();
            }
        }
    }

    private static void writeDagEdgeList(String path, Graph dag) throws IOException {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            for (Edge e : dag.getEdges()) {
                if (e.isDirected()) {
                    out.println(e.getNode1().getName() + " -> " + e.getNode2().getName());
                }
            }
        }
    }
}