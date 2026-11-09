package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.GRegression;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Writes everything needed to cross-check GRegression against the R package eff2 (Guo and Perković):
 * <ul>
 * <li>data.csv - simulated data from a random linear SEM (header row = variable names);</li>
 * <li>amat_dag.csv, amat_cpdag.csv, amat_mpdag.csv - pcalg-coded adjacency matrices (amat[i,j] = 1 and
 * amat[j,i] = 0 means i &lt;- j; both 1 means i - j), variables in the same order as data.csv;</li>
 * <li>cases.csv - one row per (graph, A, Y) case with Tetrad's identification verdict and estimate.</li>
 * </ul>
 * Then run gregression_eff2_check.R in the same directory.
 * <p>
 * Usage: GRegressionEff2CrossCheck [outputDir]
 */
public class GRegressionEff2CrossCheck {

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "gregression_crosscheck");
        if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("Could not create " + dir);

        RandomUtil.getInstance().setSeed(20260826L);
        Random rng = new Random(1);

        int numVars = 15, numEdges = 18, n = 2000, numCases = 40;

        Graph dag = RandomGraph.randomDag(numVars, 0, numEdges, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(dag));
        DataSet data = im.simulateData(n, false);
        List<Node> vars = data.getVariables();

        // Re-express the graphs over the data variables, in data column order.
        Graph dagV = relabel(dag, vars);
        Graph cpdag = GraphTransforms.dagToCpdag(dagV);
        Graph mpdag = withKnowledge(dagV, cpdag, 1);

        writeData(new File(dir, "data.csv"), data);
        writeAmat(new File(dir, "amat_dag.csv"), dagV, vars);
        writeAmat(new File(dir, "amat_cpdag.csv"), cpdag, vars);
        writeAmat(new File(dir, "amat_mpdag.csv"), mpdag, vars);

        CovarianceMatrix cov = new CovarianceMatrix(data);
        List<String> graphNames = List.of("dag", "cpdag", "mpdag");
        List<Graph> graphs = List.of(dagV, cpdag, mpdag);

        try (PrintWriter out = new PrintWriter(new File(dir, "cases.csv"))) {
            out.println("graph,A,Y,identified,estimate");

            for (int gi = 0; gi < graphs.size(); gi++) {
                Graph g = graphs.get(gi);
                GRegression greg = new GRegression(g, cov);
                int numIdentified = 0, numUnidentified = 0, attempts = 0;

                while (numIdentified + numUnidentified < numCases && attempts++ < 5000) {
                    List<Node> shuffled = new ArrayList<>(vars);
                    Collections.shuffle(shuffled, rng);
                    int sizeA = 1 + rng.nextInt(3);
                    List<Node> a = new ArrayList<>(shuffled.subList(0, sizeA));

                    // Prefer outcomes that are true descendants of a treatment, so effects are mostly nonzero.
                    Node y = shuffled.get(sizeA);
                    for (Node cand : shuffled.subList(sizeA, shuffled.size())) {
                        if (dagV.paths().isAncestorOf(a.get(0), cand) && rng.nextDouble() < 0.8) {
                            y = cand;
                            break;
                        }
                    }

                    boolean identified = greg.isIdentified(a, y);

                    // Keep the case mix informative: at most half identified where unidentified cases exist.
                    if (identified && numIdentified >= numCases / 2 && attempts < 2500) continue;
                    if (!identified && numUnidentified >= numCases / 2) continue;
                    if (identified) numIdentified++; else numUnidentified++;

                    StringBuilder aIdx = new StringBuilder();
                    for (Node ai : a) {
                        if (aIdx.length() > 0) aIdx.append(';');
                        aIdx.append(vars.indexOf(ai) + 1);   // 1-based for R
                    }

                    StringBuilder est = new StringBuilder();
                    if (identified) {
                        double[] tau = greg.totalEffect(a, y);
                        for (double t : tau) {
                            if (est.length() > 0) est.append(';');
                            est.append(String.format("%.12g", t));
                        }
                    } else {
                        est.append("NA");
                    }

                    out.println(graphNames.get(gi) + "," + aIdx + "," + (vars.indexOf(y) + 1) + ","
                                + identified + "," + est);
                }
            }
        }

        System.out.println("Wrote cross-check files to " + dir.getAbsolutePath());
    }

    private static Graph relabel(Graph g, List<Node> vars) {
        Graph out = new EdgeListGraph(vars);
        for (Edge e : g.getEdges()) {
            Node x = out.getNode(e.getNode1().getName());
            Node y = out.getNode(e.getNode2().getName());
            if (Edges.isUndirectedEdge(e)) out.addUndirectedEdge(x, y);
            else out.addDirectedEdge(out.getNode(Edges.getDirectedEdgeTail(e).getName()),
                    out.getNode(Edges.getDirectedEdgeHead(e).getName()));
        }
        return out;
    }

    /**
     * Orients up to k undirected CPDAG edges as in the true DAG and Meek-closes.
     */
    private static Graph withKnowledge(Graph dag, Graph cpdag, int k) {
        Graph mpdag = new EdgeListGraph(cpdag);
        Knowledge knowledge = new Knowledge();
        int oriented = 0;

        for (Edge e : new ArrayList<>(mpdag.getEdges())) {
            if (oriented >= k) break;
            if (!Edges.isUndirectedEdge(e)) continue;
            Node x = e.getNode1(), z = e.getNode2();
            Node tail = dag.isParentOf(dag.getNode(x.getName()), dag.getNode(z.getName())) ? x : z;
            Node head = tail == x ? z : x;
            mpdag.removeEdge(e);
            mpdag.addDirectedEdge(tail, head);
            knowledge.setRequired(tail.getName(), head.getName());
            oriented++;
        }

        MeekRules meek = new MeekRules();
        meek.setKnowledge(knowledge);
        meek.setRevertToUnshieldedColliders(false);
        meek.setVerbose(false);
        meek.orientImplied(mpdag);

        String problem = GRegression.mpdagProblem(mpdag);
        if (problem != null) throw new IllegalStateException(problem);
        return mpdag;
    }

    private static void writeData(File file, DataSet data) throws Exception {
        try (PrintWriter out = new PrintWriter(file)) {
            List<Node> vars = data.getVariables();
            StringBuilder header = new StringBuilder();
            for (int j = 0; j < vars.size(); j++) {
                if (j > 0) header.append(',');
                header.append(vars.get(j).getName());
            }
            out.println(header);
            for (int i = 0; i < data.getNumRows(); i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < vars.size(); j++) {
                    if (j > 0) row.append(',');
                    row.append(String.format("%.12g", data.getDouble(i, j)));
                }
                out.println(row);
            }
        }
    }

    /**
     * pcalg coding: amat[i][j] = 1 iff there is an edge j -&gt; i or i - j.
     */
    private static void writeAmat(File file, Graph g, List<Node> vars) throws Exception {
        int p = vars.size();
        int[][] amat = new int[p][p];

        for (Edge e : g.getEdges()) {
            int i = vars.indexOf(g.getNode(e.getNode1().getName()));
            int j = vars.indexOf(g.getNode(e.getNode2().getName()));
            if (Edges.isUndirectedEdge(e)) {
                amat[i][j] = 1;
                amat[j][i] = 1;
            } else {
                int tail = vars.indexOf(g.getNode(Edges.getDirectedEdgeTail(e).getName()));
                int head = vars.indexOf(g.getNode(Edges.getDirectedEdgeHead(e).getName()));
                amat[head][tail] = 1;
            }
        }

        try (PrintWriter out = new PrintWriter(file)) {
            for (int i = 0; i < p; i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < p; j++) {
                    if (j > 0) row.append(',');
                    row.append(amat[i][j]);
                }
                out.println(row);
            }
        }
    }
}
