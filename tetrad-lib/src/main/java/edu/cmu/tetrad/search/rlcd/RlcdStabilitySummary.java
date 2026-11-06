///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Summarizes how stable an RLCD output is across repeated runs on resampled data. Latent names are not comparable
 * across runs (what one run calls L1 another calls L2, or splits in two), so the summary is written entirely in
 * terms of the observed variables, where names are stable:
 * <ul>
 * <li>co-clustering: for two observed variables, the fraction of runs in which they share a latent parent;</li>
 * <li>observed adjacencies: for two observed variables adjacent in the main graph, the fraction of runs in which
 * they are adjacent, orientation ignored;</li>
 * <li>the number of latents per run.</li>
 * </ul>
 * The main graph is the one reported to the user; the runs are the resampled ones. The text lists, for each latent in
 * the main graph, its observed children with each child's co-clustering support (the mean, over the other children,
 * of the pair's co-clustering fraction), then the observed pairs that were co-clustered in at least half the runs but
 * not in the main graph, then the observed adjacencies of the main graph with their frequencies, then the latent
 * count distribution. Nothing here changes the graph; it is a report.
 *
 * @author josephramsey
 */
public final class RlcdStabilitySummary {

    private final Graph main;
    private final List<Graph> runs;
    private final List<String> observed;
    private final Map<String, Integer> index = new HashMap<>();
    private final int[][] coCluster;
    private final int[][] adjacent;
    private final int[] latentCounts;

    /**
     * Tallies co-clustering, observed adjacencies and latent counts over the runs.
     *
     * @param main the graph reported to the user.
     * @param runs the graphs from the resampled runs; may be empty.
     */
    public RlcdStabilitySummary(Graph main, List<Graph> runs) {
        this.main = main;
        this.runs = new ArrayList<>(runs);
        this.observed = new ArrayList<>();
        for (Node v : main.getNodes()) if (v.getNodeType() != NodeType.LATENT) observed.add(v.getName());
        Collections.sort(observed);
        for (int i = 0; i < observed.size(); i++) index.put(observed.get(i), i);
        int p = observed.size();
        coCluster = new int[p][p];
        adjacent = new int[p][p];
        latentCounts = new int[runs.size()];
        for (int r = 0; r < runs.size(); r++) tally(runs.get(r), r);
    }

    private void tally(Graph g, int r) {
        boolean[][] co = coClusterMatrix(g);
        for (int i = 0; i < observed.size(); i++) {
            for (int j = i + 1; j < observed.size(); j++) {
                if (co[i][j]) {
                    coCluster[i][j]++;
                    coCluster[j][i]++;
                }
            }
        }
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() == NodeType.LATENT || b.getNodeType() == NodeType.LATENT) continue;
            Integer i = index.get(a.getName()), j = index.get(b.getName());
            if (i == null || j == null) continue;
            adjacent[i][j]++;
            adjacent[j][i]++;
        }
        int latents = 0;
        for (Node v : g.getNodes()) if (v.getNodeType() == NodeType.LATENT) latents++;
        latentCounts[r] = latents;
    }

    /**
     * co[i][j] iff observed i and j have a common latent parent in g.
     */
    private boolean[][] coClusterMatrix(Graph g) {
        int p = observed.size();
        boolean[][] co = new boolean[p][p];
        for (Node l : g.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            List<Integer> members = new ArrayList<>();
            for (Node c : g.getChildren(l)) {
                if (c.getNodeType() == NodeType.LATENT) continue;
                Integer i = index.get(c.getName());
                if (i != null) members.add(i);
            }
            for (int a : members) for (int b : members) if (a != b) co[a][b] = true;
        }
        return co;
    }

    /**
     * The number of resampled runs summarized.
     *
     * @return the count.
     */
    public int getNumRuns() {
        return runs.size();
    }

    /**
     * The fraction of runs in which two observed variables share a latent parent.
     *
     * @param a a variable name.
     * @param b a variable name.
     * @return the fraction, or NaN if there were no runs or a name is unknown.
     */
    public double coClusterFrequency(String a, String b) {
        Integer i = index.get(a), j = index.get(b);
        if (i == null || j == null || runs.isEmpty()) return Double.NaN;
        return coCluster[i][j] / (double) runs.size();
    }

    /**
     * The fraction of runs in which two observed variables are adjacent, orientation ignored.
     *
     * @param a a variable name.
     * @param b a variable name.
     * @return the fraction, or NaN if there were no runs or a name is unknown.
     */
    public double adjacencyFrequency(String a, String b) {
        Integer i = index.get(a), j = index.get(b);
        if (i == null || j == null || runs.isEmpty()) return Double.NaN;
        return adjacent[i][j] / (double) runs.size();
    }

    /**
     * One line for a graph attribute: the run count and the modal number of latents.
     *
     * @return the line.
     */
    public String oneLine() {
        if (runs.isEmpty()) return "RLCD stability: no resampled runs.";
        int mainLatents = 0;
        for (Node v : main.getNodes()) if (v.getNodeType() == NodeType.LATENT) mainLatents++;
        int same = 0;
        for (int c : latentCounts) if (c == mainLatents) same++;
        return "RLCD stability over " + runs.size() + " bootstrap runs: " + same + " of " + runs.size()
               + " runs found " + mainLatents + " latents as the main graph does; see the log for co-clustering "
               + "and adjacency frequencies.";
    }

    /**
     * The full report.
     *
     * @return the text, several lines.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        int n = runs.size();
        sb.append("RLCD stability summary over ").append(n).append(" bootstrap run").append(n == 1 ? "" : "s")
                .append(". Frequencies are fractions of these runs; the main graph is not counted.\n");
        if (n == 0) return sb.toString();

        // Clusters of the main graph, with per-member support.
        boolean[][] mainCo = coClusterMatrix(main);
        List<Node> latents = new ArrayList<>();
        for (Node v : main.getNodes()) if (v.getNodeType() == NodeType.LATENT) latents.add(v);
        latents.sort(Comparator.comparing(Node::getName));
        sb.append("\nClusters of the main graph (member: mean co-clustering frequency with the other members):\n");
        for (Node l : latents) {
            List<String> members = new ArrayList<>();
            for (Node c : main.getChildren(l)) {
                if (c.getNodeType() != NodeType.LATENT && index.containsKey(c.getName())) members.add(c.getName());
            }
            Collections.sort(members);
            sb.append("  ").append(l.getName()).append(": ");
            if (members.size() < 2) {
                sb.append(members).append(members.isEmpty() ? " (no observed children)" : " (single child)")
                        .append('\n');
                continue;
            }
            StringJoiner sj = new StringJoiner(", ");
            for (String m : members) {
                double sum = 0;
                for (String o : members) if (!o.equals(m)) sum += coClusterFrequency(m, o);
                sj.add(m + " " + fmt(sum / (members.size() - 1)));
            }
            sb.append(sj).append('\n');
        }

        // Pairs frequently co-clustered that the main graph does not cluster together.
        List<String> frequent = new ArrayList<>();
        for (int i = 0; i < observed.size(); i++) {
            for (int j = i + 1; j < observed.size(); j++) {
                double f = coCluster[i][j] / (double) n;
                if (f >= 0.5 && !mainCo[i][j]) {
                    frequent.add(observed.get(i) + "-" + observed.get(j) + " " + fmt(f));
                }
            }
        }
        sb.append("\nPairs co-clustered in at least half the runs but not in the main graph: ");
        sb.append(frequent.isEmpty() ? "none" : String.join(", ", frequent)).append('\n');

        // Observed adjacencies of the main graph.
        List<String> adj = new ArrayList<>();
        for (Edge e : main.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() == NodeType.LATENT || b.getNodeType() == NodeType.LATENT) continue;
            adj.add(e + " " + fmt(adjacencyFrequency(a.getName(), b.getName())));
        }
        Collections.sort(adj);
        sb.append("\nObserved adjacencies of the main graph (fraction of runs adjacent, orientation ignored): ");
        sb.append(adj.isEmpty() ? "none" : String.join(", ", adj)).append('\n');

        // Latent counts.
        Map<Integer, Integer> hist = new TreeMap<>();
        for (int c : latentCounts) hist.merge(c, 1, Integer::sum);
        StringJoiner sj = new StringJoiner(", ");
        for (Map.Entry<Integer, Integer> en : hist.entrySet()) {
            sj.add(en.getKey() + " latents in " + en.getValue() + " run" + (en.getValue() == 1 ? "" : "s"));
        }
        sb.append("\nLatents per run: ").append(sj).append(".\n");
        return sb.toString();
    }

    private static String fmt(double f) {
        return String.format(Locale.ROOT, "%.2f", f);
    }
}
