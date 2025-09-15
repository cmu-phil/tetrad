///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Utilities to stabilize arbitrary directed graphs with cycles.
 */
public final class CyclicStableUtils {

    private CyclicStableUtils() {
    }

    /* ========================= Public API ========================= */

    /**
     * Simulate from an arbitrary graph with SCC-wise fixed spectral radius s.
     */
    public static SemIm.CyclicSimResult simulateStableFixedRadius(
            Graph g, int n, double s, double coefLow, double coefHigh,
            long seed, Parameters params) {

        if (seed != -1) {
            RandomUtil.getInstance().setSeed(seed);
        } else {
            RandomUtil.getInstance().setSeed(RandomUtil.getInstance().nextLong());
        }

        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm, params);

        stabilizeAllSccsFixedRadius(im, g, s, coefLow, coefHigh);

        return new SemIm.CyclicSimResult(im.simulateData(n, false), im);
    }

    /**
     * Simulate from an arbitrary graph with SCC-wise radius capped by sqrt(maxProd).
     */
    public static SemIm.CyclicSimResult simulateStableProductCapped(
            Graph g, int n, double maxProd, double coefLow, double coefHigh,
            long seed, Parameters params) {

        double targetRadius = Math.sqrt(maxProd); // exact for 2-node cycles; safe upper bound otherwise
        return simulateStableFixedRadius(g, n, targetRadius, coefLow, coefHigh, seed, params);
    }

    // Scale-only: do not redraw; just rescale SCCs to target radius
    public static void stabilizeAllSccsFixedRadiusScaleOnly(SemIm im, Graph g, double s) {
        if (s <= 0.0 || s >= 1.0) throw new IllegalArgumentException("s in (0,1)");
        for (var scc : stronglyConnectedComponents(g)) {
            if (scc.size() < 2) continue;
            double rho = spectralRadiusAbs(im, g, scc);
            if (rho > s && rho > 0.0) {
                double scale = s / rho;
                scaleInternalEdges(im, g, scc, scale);
            }
        }
    }

    /**
     * Stabilize an existing SemIm in-place: enforce per-SCC spectral radius target s.
     */
    public static void stabilizeAllSccsFixedRadius(
            SemIm im, Graph g, double s, double coefLow, double coefHigh) {

        if (s <= 0.0 || s >= 1.0) throw new IllegalArgumentException("s must be in (0,1)");
        List<List<Node>> sccs = stronglyConnectedComponents(g);

        for (List<Node> scc : sccs) {
            if (scc.size() < 2) continue; // skip trivial SCCs
            initializeInternalEdgesRandom(im, g, scc, coefLow, coefHigh);

            double rho = spectralRadiusAbs(im, g, scc);
            if (rho > s) {
                double scale = s / rho;
                scaleInternalEdges(im, g, scc, scale);
            }
        }
    }

    /* ====================== Core operations ======================= */

    /**
     * Randomize existing internal edges (that already exist in the graph) within [low, high], positive.
     */
    public static void initializeInternalEdgesRandom(
            SemIm im, Graph g, List<Node> scc, double low, double high) {

        if (low <= 0 || high <= 0 || low > high) throw new IllegalArgumentException("Bad coef range");
        for (Node from : scc) {
            for (Node to : scc) {
                if (from == to) continue;
                if (g.getDirectedEdge(from, to) != null) {
                    double val = low + (high - low) * RandomUtil.getInstance().nextDouble();
                    setEdgeCoef(im, from, to, val);
                }
            }
        }
    }

    /**
     * Scale all internal edges of an SCC by a factor.
     */
    public static void scaleInternalEdges(SemIm im, Graph g, List<Node> scc, double factor) {
        for (Node from : scc) {
            for (Node to : scc) {
                if (from == to) continue;
                if (g.getDirectedEdge(from, to) != null) {
                    double cur = getEdgeCoef(im, from, to);
                    setEdgeCoef(im, from, to, cur * factor);
                }
            }
        }
    }

    /**
     * Spectral radius estimate of |B| (absolute coefficient matrix) for the SCC using power iteration.
     */
    public static double spectralRadiusAbs(SemIm im, Graph g, List<Node> scc) {
        int k = scc.size();
        if (k == 0) return 0.0;

        // Build |B| with ordering scc(0..k-1); B_{ij} = |coef(j->i)| (row = to, col = from)
        double[][] A = new double[k][k];
        for (int i = 0; i < k; i++) {
            Node to = scc.get(i);
            for (int j = 0; j < k; j++) {
                if (i == j) continue;
                Node from = scc.get(j);
                if (g.getDirectedEdge(from, to) != null) {
                    A[i][j] = Math.abs(getEdgeCoef(im, from, to));
                }
            }
        }

        // Power iteration on A >= 0
        double[] v = new double[k];
        Arrays.fill(v, 1.0 / k);
        double lambda = 0.0, prev = -1.0;

        for (int it = 0; it < 200; it++) {
            double[] w = new double[k];
            for (int i = 0; i < k; i++) {
                double sum = 0.0;
                for (int j = 0; j < k; j++) sum += A[i][j] * v[j];
                w[i] = sum;
            }
            double norm = 0.0;
            for (double x : w) norm += x;
            if (norm == 0.0) return 0.0; // no internal edges
            for (int i = 0; i < k; i++) v[i] = w[i] / norm;

            // Rayleigh quotient in 1-norm for nonnegative A: lambda â ||A v||_1 / ||v||_1 = sum(w)
            lambda = norm;
            if (Math.abs(lambda - prev) < 1e-9 * Math.max(1.0, lambda)) break;
            prev = lambda;
        }
        return lambda;
    }

    /* ===================== Graph / SCC utilities ===================== */

    public static List<List<Node>> stronglyConnectedComponents(Graph g) {
        // Kosaraju: DFS order, transpose, DFS assign
        List<Node> nodes = new ArrayList<>(g.getNodes());
        Set<Node> visited = new HashSet<>();
        Deque<Node> order = new ArrayDeque<>();

        // 1st pass
        for (Node v : nodes) if (!visited.contains(v)) dfs1(g, v, visited, order);

        // Build transpose adjacency list
        Map<Node, List<Node>> rev = new HashMap<>();
        for (Node v : nodes) rev.put(v, new ArrayList<>());
        for (Edge e : g.getEdges()) {
            if (e.getEndpoint1() == Endpoint.TAIL && e.getEndpoint2() == Endpoint.ARROW) {
                Node from = e.getNode1(), to = e.getNode2();
                rev.get(to).add(from); // reverse
            } else if (e.getEndpoint2() == Endpoint.TAIL && e.getEndpoint1() == Endpoint.ARROW) {
                Node from = e.getNode2(), to = e.getNode1();
                rev.get(to).add(from);
            }
        }

        // 2nd pass
        visited.clear();
        List<List<Node>> sccs = new ArrayList<>();
        while (!order.isEmpty()) {
            Node v = order.pop();
            if (!visited.contains(v)) {
                List<Node> comp = new ArrayList<>();
                dfs2(v, rev, visited, comp);
                sccs.add(comp);
            }
        }
        return sccs;
    }

    private static void dfs1(Graph g, Node v, Set<Node> vis, Deque<Node> order) {
        vis.add(v);
        for (Node w : g.getChildren(v)) if (!vis.contains(w)) dfs1(g, w, vis, order);
        order.push(v);
    }

    private static void dfs2(Node v, Map<Node, List<Node>> rev, Set<Node> vis, List<Node> comp) {
        vis.add(v);
        comp.add(v);
        for (Node w : rev.get(v)) if (!vis.contains(w)) dfs2(w, rev, vis, comp);
    }

    /* =================== SemIm coefficient IO =================== */

    private static double getEdgeCoef(SemIm im, Node from, Node to) {
        try {
            Method m = SemIm.class.getMethod("getEdgeCoef", Node.class, Node.class);
            Object val = m.invoke(im, from, to);
            return (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;
        } catch (ReflectiveOperationException e) {
            return 0.0;
        }
    }

    private static void setEdgeCoef(SemIm im, Node from, Node to, double val) {
        try {
            Method m = SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class);
            m.invoke(im, from, to, val);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set edge coef for " + from + "->" + to, e);
        }
    }

    /* ================= Convenience quick-start (optional) ================= */

    /**
     * Quick demo: build a graph, stabilize, simulate.
     */
    public static edu.cmu.tetrad.sem.SemIm.CyclicSimResult quickDemo() {
        Node x = new ContinuousVariable("x");
        Node y = new ContinuousVariable("y");
        Node z = new ContinuousVariable("z");
        Node w = new ContinuousVariable("w");

        Graph g = new EdgeListGraph(Arrays.asList(x, y, z, w));
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(y, z);
        g.addDirectedEdge(z, y); // cycle
        g.addDirectedEdge(w, z);

        Parameters p = new Parameters();
        long seed = 42L;
        return simulateStableFixedRadius(g, 10000, 0.6, 0.2, 1.0, seed, p);
    }
}
