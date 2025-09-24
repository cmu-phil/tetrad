// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/MakeColliderOperator.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;

final class MakeColliderOperator implements GpsOperator {
    public String name() {
        return "MakeCollider";
    }

    public List<Graph> propose(Graph pag, Mode mode) {
        List<Graph> out = new ArrayList<>();
        for (Triple t : listUnshieldedTriples(pag)) {
            Node x = t.getX(), z = t.getY(), y = t.getZ();
            if (pag.isAdjacentTo(x, y)) continue;
            Graph g2 = new EdgeListGraph(pag);
            forceInto(g2, x, z);
            forceInto(g2, y, z);
            if (Validity.noAlmostDirectedCycle(g2)) out.add(g2);
        }
        return out;
    }

    private List<Triple> listUnshieldedTriples(Graph g) {
        List<Triple> out = new ArrayList<>();
        for (Node z : g.getNodes()) {
            java.util.List<Node> adj = g.getAdjacentNodes(z);
            for (int i = 0; i < adj.size(); i++)
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i), y = adj.get(j);
                    if (!g.isAdjacentTo(x, y)) out.add(new Triple(x, z, y));
                }
        }
        return out;
    }

    private void forceInto(Graph g, Node from, Node to) {
        Edge e = g.getEdge(from, to);
        if (e == null) {
            g.addEdge(Edges.directedEdge(from, to));
            return;
        }
        if (e.getEndpoint(to) != Endpoint.ARROW) {
            g.removeEdge(e);
            g.addEdge(new Edge(from, to, Endpoint.CIRCLE, Endpoint.ARROW)); // conservative o->
        }
    }
}