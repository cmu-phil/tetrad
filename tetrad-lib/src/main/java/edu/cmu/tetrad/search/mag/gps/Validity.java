// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/Validity.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.HashSet;
import java.util.Set;

final class Validity {
    static boolean quickReject(Graph g) {
        for (Edge e : g.getEdges()) {
            if (Edges.isUndirectedEdge(e) && (e.getEndpoint(e.getNode1()) == Endpoint.ARROW || e.getEndpoint(e.getNode2()) == Endpoint.ARROW))
                return true;
        }
        return false;
    }

    static boolean noAlmostDirectedCycle(Graph g) {
        for (Node s : g.getNodes()) if (hasCycleFrom(g, s, new HashSet<>())) return false;
        return true;
    }

    private static boolean hasCycleFrom(Graph g, Node v, Set<Node> stack) {
        if (!stack.add(v)) return true;
        for (Node w : g.getAdjacentNodes(v)) {
            Edge e = g.getEdge(v, w);
            if (e.getEndpoint(w) == Endpoint.ARROW) {
                if (hasCycleFrom(g, w, stack)) return true;
            }
        }
        stack.remove(v);
        return false;
    }
}