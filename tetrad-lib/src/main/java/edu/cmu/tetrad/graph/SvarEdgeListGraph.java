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

package edu.cmu.tetrad.graph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Graph implementation (via EdgeListGraph) that enforces SVAR-style lag mirroring: whenever an edge between A@lag_a
 * and B@lag_b is added (or removed), the same edge type is added (or removed) for all lag-aligned pairs A@t and B@(t +
 * (lag_b - lag_a)) that exist in the node set.
 * <p>
 * Node naming convention: - Base (lag 0) is "X" - Lagged versions are "X:1", "X:2", ...
 * <p>
 * Examples: add X:1 -> Y  â also add X:2 -> Y:1, X:3 -> Y:2, ... if nodes exist remove Z:2 --- W:1  â also remove Z:1
 * --- W, Z:3 --- W:2, ...
 * <p>
 * Only addEdge / removeEdge are overridden; everything else is inherited from EdgeListGraph.
 */
public class SvarEdgeListGraph extends EdgeListGraph {

    /**
     * Prevent recursive mirroring during internal add/remove operations.
     */
    private static final ThreadLocal<Boolean> IN_REPLICATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Pattern LAG_PATTERN = Pattern.compile("^(.*?)(?::(\\d+))?$");

    public SvarEdgeListGraph() {
        super();
    }

    /**
     * Copy constructor: builds an EdgeListGraph copy first, then augments with SVAR behavior.
     */
    public SvarEdgeListGraph(Graph g) {
        super(g);
    }

    /**
     * Construct from a collection of nodes.
     */
    public SvarEdgeListGraph(List<Node> nodes) {
        super(nodes);
    }

    private static Lagged parseLagged(String name) {
        Matcher m = LAG_PATTERN.matcher(name);
        if (!m.matches()) return null;
        String base = m.group(1);
        String lagStr = m.group(2);
        int lag = (lagStr == null || lagStr.isEmpty()) ? 0 : Integer.parseInt(lagStr);
        return new Lagged(base, lag);
    }

    /* ======================= Helpers ======================= */

    @Override
    public synchronized boolean addEdge(Edge e) throws IllegalArgumentException {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            // During replication, just do the raw add.
            return super.addEdge(e);
        }

        // First add the requested edge.
        boolean changed = super.addEdge(e);

        // Then mirror across all lags that exist.
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge mirror : buildMirroredEdges(e)) {
                if (!mirror.equals(e)) {
                    super.addEdge(mirror);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }

        return changed;
    }

    @Override
    public synchronized boolean removeEdge(Edge e) {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            // During replication, just do the raw remove.
            return super.removeEdge(e);
        }

        // Remove the requested edge first.
        boolean changed = super.removeEdge(e);

        // Then remove all mirrored counterparts.
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge mirror : buildMirroredEdges(e)) {
                if (!mirror.equals(e)) {
                    super.removeEdge(mirror);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }

        return changed;
    }

    private Map<String, Map<Integer, Node>> indexByBaseAndLag() {
        Map<String, Map<Integer, Node>> map = new HashMap<>();
        for (Node n : getNodes()) {
            Lagged ln = parseLagged(n.getName());
            if (ln == null) continue; // ignore non-conforming names
            map.computeIfAbsent(ln.base, k -> new HashMap<>()).put(ln.lag, n);
        }
        return map;
    }

    /**
     * Build the full set of mirrored edges corresponding to {@code e}, including {@code e} itself if lag parsing
     * succeeds. If parsing fails, returns a singleton set {e}.
     */
    private Set<Edge> buildMirroredEdges(Edge e) {
        Node a = e.getNode1();
        Node b = e.getNode2();

        Lagged la = parseLagged(a.getName());
        Lagged lb = parseLagged(b.getName());

        if (la == null || lb == null) {
            // If names don't follow the lag convention, do nothing special.
            return Collections.singleton(e);
        }

        int shift = lb.lag - la.lag;

        Map<String, Map<Integer, Node>> byBase = indexByBaseAndLag();
        Map<Integer, Node> aLags = byBase.get(la.base);
        Map<Integer, Node> bLags = byBase.get(lb.base);
        if (aLags == null || bLags == null) {
            return Collections.singleton(e);
        }

        Set<Edge> out = new LinkedHashSet<>();

        // For every available lag t of A, connect to B at t + shift if it exists.
        for (Map.Entry<Integer, Node> ent : aLags.entrySet()) {
            int t = ent.getKey();
            Node aT = ent.getValue();
            Node bT = bLags.get(t + shift);
            if (bT == null) continue;

            // Preserve endpoint roles relative to node1/node2.
            Edge mirror = new Edge(aT, bT, e.getEndpoint1(), e.getEndpoint2());
            out.add(mirror);
        }

        // Fallback: ensure the original edge is present if we found nothing else.
        if (out.isEmpty()) {
            out.add(e);
        }

        return out;
    }

    /**
     * Simple record to hold parsed base name and lag.
     */
    private record Lagged(String base, int lag) {
    }
}
