///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsbi;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Scores a recovered structural graph against the true structural graph using
 * adjacency and arrowhead precision and recall.
 *
 * <h2>Matching convention</h2>
 *
 * <p>Nodes are matched by name.  When {@link TsbiRunner} builds the block
 * variables it names them after the true latent group leaders, so the recovered
 * graph and the true structural graph share node names and no external bijection
 * is needed.
 *
 * <h2>Adjacency metrics</h2>
 *
 * <p>An <em>adjacency</em> is an unordered pair {@code {u, v}} for which an
 * edge of any orientation exists.
 * <ul>
 *   <li><b>TP_adj</b>: pairs adjacent in both graphs.</li>
 *   <li><b>FP_adj</b>: pairs adjacent in recovered but not in true.</li>
 *   <li><b>FN_adj</b>: pairs adjacent in true but not in recovered.</li>
 *   <li><b>Precision_adj</b> = TP / (TP + FP), <b>Recall_adj</b> = TP / (TP + FN).</li>
 * </ul>
 *
 * <h2>Arrowhead metrics</h2>
 *
 * <p>An <em>arrowhead</em> is an ordered pair {@code (u → v)}: the endpoint at
 * {@code v} carries an arrowhead mark on the {@code u}–{@code v} edge.  In a
 * DAG every directed edge {@code u → v} contributes exactly one arrowhead (at
 * {@code v}).  In a CPDAG (the output of PC) directed edges contribute
 * arrowheads and undirected edges contribute none.
 * <ul>
 *   <li><b>TP_ahd</b>: arrowheads at {@code v} from {@code u} present in both
 *       graphs (adjacency exists in both and both have an arrowhead at {@code v}
 *       on the {@code u}–{@code v} edge).</li>
 *   <li><b>FP_ahd</b>: arrowheads in recovered graph not matched in true graph.</li>
 *   <li><b>FN_ahd</b>: arrowheads in true graph not present in recovered graph.</li>
 * </ul>
 *
 * <p>Denominator for precision: total arrowheads in the recovered graph.
 * Denominator for recall: total arrowheads in the true graph.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   Graph trueStructural = StructuralGraphScorer.extractTrueStructural(mim);
 *   Graph recovered      = tsbiRunner.run(data, trueClusters, leaders);
 *   GraphScore score     = StructuralGraphScorer.score(trueStructural, recovered);
 * }</pre>
 *
 * @author josephramsey
 */
public final class StructuralGraphScorer {

    private StructuralGraphScorer() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Extracts the true structural graph (latent-to-latent edges only) from a
     * full MIM graph produced by {@code RandomMim}.
     *
     * <p>Only edges between two latent-type nodes are included.  Measurement
     * edges (latent → observed) are excluded.  The returned graph contains
     * exactly the set of latent nodes that participate in at least one
     * structural edge, plus any isolated latents.
     *
     * @param mim the full MIM graph; must not be {@code null}.
     * @return a new graph over the latent nodes of {@code mim} with only the
     *         latent-to-latent edges retained.
     */
    public static Graph extractTrueStructural(Graph mim) {
        if (mim == null) throw new IllegalArgumentException("MIM graph must not be null.");

        // Collect all latent nodes.
        List<Node> latents = new ArrayList<>();
        for (Node node : mim.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        // Build a new graph retaining only latent-to-latent edges.
        Graph structural = new EdgeListGraph(latents);
        for (Edge e : mim.getEdges()) {
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();
            if (n1.getNodeType() == NodeType.LATENT
                    && n2.getNodeType() == NodeType.LATENT) {
                structural.addEdge(e);
            }
        }
        return structural;
    }

    /**
     * Scores the recovered structural graph against the true structural graph.
     *
     * <p>Both graphs must contain nodes of matching names; nodes present in one
     * graph but absent in the other are treated as isolated (contributing false
     * negatives or false positives for adjacency as appropriate).
     *
     * @param trueGraph      true structural graph (DAG over latent nodes).
     * @param recoveredGraph recovered structural graph (CPDAG over block
     *                       variables named to match true latents).
     * @return a {@link GraphScore} containing adjacency and arrowhead metrics.
     */
    public static GraphScore score(Graph trueGraph, Graph recoveredGraph) {
        if (trueGraph == null || recoveredGraph == null)
            throw new IllegalArgumentException("Graphs must not be null.");

        // Build name-indexed node maps for both graphs.
        Map<String, Node> trueNodes = nameMap(trueGraph);
        Map<String, Node> recNodes  = nameMap(recoveredGraph);

        // All node names that appear in either graph.
        Set<String> allNames = new LinkedHashSet<>(trueNodes.keySet());
        allNames.addAll(recNodes.keySet());
        List<String> names = new ArrayList<>(allNames);

        // ---- Adjacency ----
        int tpAdj = 0, fpAdj = 0, fnAdj = 0;

        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String ni = names.get(i);
                String nj = names.get(j);

                boolean inTrue = isAdjacent(trueGraph,      trueNodes, ni, nj);
                boolean inRec  = isAdjacent(recoveredGraph, recNodes,  ni, nj);

                if (inTrue && inRec)  tpAdj++;
                else if (inRec)       fpAdj++;
                else if (inTrue)      fnAdj++;
            }
        }

        double adjP = (tpAdj + fpAdj == 0) ? Double.NaN
                : (double) tpAdj / (tpAdj + fpAdj);
        double adjR = (tpAdj + fnAdj == 0) ? Double.NaN
                : (double) tpAdj / (tpAdj + fnAdj);

        // ---- Arrowhead ----
        // Iterate over ordered pairs (u, v): count arrowheads at v on u-v edge.
        int tpAhd = 0, fpAhd = 0, fnAhd = 0;

        for (String nu : names) {
            for (String nv : names) {
                if (nu.equals(nv)) continue;

                boolean trueAhd = hasArrowhead(trueGraph,      trueNodes, nu, nv);
                boolean recAhd  = hasArrowhead(recoveredGraph, recNodes,  nu, nv);

                if (trueAhd && recAhd)  tpAhd++;
                else if (recAhd)        fpAhd++;
                else if (trueAhd)       fnAhd++;
            }
        }

        double ahdP = (tpAhd + fpAhd == 0) ? Double.NaN
                : (double) tpAhd / (tpAhd + fpAhd);
        double ahdR = (tpAhd + fnAhd == 0) ? Double.NaN
                : (double) tpAhd / (tpAhd + fnAhd);

        return new GraphScore(adjP, adjR, ahdP, ahdR, tpAdj, fpAdj, fnAdj, tpAhd, fpAhd, fnAhd);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static Map<String, Node> nameMap(Graph g) {
        Map<String, Node> map = new LinkedHashMap<>();
        for (Node n : g.getNodes()) map.put(n.getName(), n);
        return map;
    }

    /** Returns true if nodes named {@code n1} and {@code n2} are adjacent in {@code g}. */
    private static boolean isAdjacent(Graph g, Map<String, Node> nodes,
                                      String n1, String n2) {
        Node u = nodes.get(n1);
        Node v = nodes.get(n2);
        if (u == null || v == null) return false;
        return g.isAdjacentTo(u, v);
    }

    /**
     * Returns true if there is an arrowhead at {@code nv} on the edge between
     * {@code nu} and {@code nv} in graph {@code g}.
     *
     * <p>An arrowhead at {@code nv} means: the edge endpoint at {@code nv} has
     * mark {@link Endpoint#ARROW}.  For a directed edge {@code nu → nv} this
     * is always true; for an undirected edge {@code nu -- nv} it is false.
     */
    private static boolean hasArrowhead(Graph g, Map<String, Node> nodes,
                                        String nu, String nv) {
        Node u = nodes.get(nu);
        Node v = nodes.get(nv);
        if (u == null || v == null) return false;
        Edge e = g.getEdge(u, v);
        if (e == null) return false;
        // Determine which endpoint corresponds to v and check for ARROW mark.
        if (e.getNode1().equals(v)) {
            return e.getEndpoint1() == Endpoint.ARROW;
        } else {
            return e.getEndpoint2() == Endpoint.ARROW;
        }
    }

    // -----------------------------------------------------------------------
    // Result record
    // -----------------------------------------------------------------------

    /**
     * Immutable result of a structural graph scoring comparison.
     *
     * @param adjPrecision  adjacency precision in [0, 1] (or NaN if undefined).
     * @param adjRecall     adjacency recall in [0, 1] (or NaN if undefined).
     * @param ahdPrecision  arrowhead precision in [0, 1] (or NaN if undefined).
     * @param ahdRecall     arrowhead recall in [0, 1] (or NaN if undefined).
     * @param tpAdj         true-positive adjacency count.
     * @param fpAdj         false-positive adjacency count.
     * @param fnAdj         false-negative adjacency count.
     * @param tpAhd         true-positive arrowhead count.
     * @param fpAhd         false-positive arrowhead count.
     * @param fnAhd         false-negative arrowhead count.
     */
    public record GraphScore(
            double adjPrecision,
            double adjRecall,
            double ahdPrecision,
            double ahdRecall,
            int tpAdj, int fpAdj, int fnAdj,
            int tpAhd, int fpAhd, int fnAhd) {

        /**
         * F1 score for adjacency recovery.
         *
         * @return harmonic mean of adjacency precision and recall, or NaN.
         */
        public double adjF1() {
            return f1(adjPrecision, adjRecall);
        }

        /**
         * F1 score for arrowhead recovery.
         *
         * @return harmonic mean of arrowhead precision and recall, or NaN.
         */
        public double ahdF1() {
            return f1(ahdPrecision, ahdRecall);
        }

        private static double f1(double p, double r) {
            if (Double.isNaN(p) || Double.isNaN(r)) return Double.NaN;
            double denom = p + r;
            return (denom == 0.0) ? 0.0 : 2.0 * p * r / denom;
        }

        @Override
        public String toString() {
            return String.format(
                    "GraphScore{AdjP=%.4f AdjR=%.4f AdjF1=%.4f  AhdP=%.4f AhdR=%.4f AhdF1=%.4f}",
                    adjPrecision, adjRecall, adjF1(),
                    ahdPrecision, ahdRecall, ahdF1());
        }
    }
}
