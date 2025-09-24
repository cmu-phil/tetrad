package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;

final class MakeNoncolliderOperator implements GpsOperator {
    public MakeNoncolliderOperator() {
    }

    public String name() {
        return "MakeNoncollider";
    }

    public List<Graph> propose(Graph pag, Mode mode) {
        List<Graph> out = new ArrayList<>();
        for (Triple t : listUnshieldedTriples(pag)) {
            Node x = t.getX(), z = t.getY(), y = t.getZ();
            if (pag.isAdjacentTo(x, y)) continue;
            if (mode == Mode.BASELINE) {
                Graph g2 = new EdgeListGraph(pag);
                relaxInto(g2, x, z);
                relaxInto(g2, y, z);
                if (Validity.noAlmostDirectedCycle(g2)) out.add(g2);
            } else {
                Graph a = new EdgeListGraph(pag);
                relaxInto(a, x, z);
                if (Validity.noAlmostDirectedCycle(a)) out.add(a);
                Graph b = new EdgeListGraph(pag);
                relaxInto(b, y, z);
                if (Validity.noAlmostDirectedCycle(b)) out.add(b);
                Graph c = new EdgeListGraph(pag);
                relaxInto(c, x, z);
                relaxInto(c, y, z);
                if (Validity.noAlmostDirectedCycle(c)) out.add(c);
            }
        }
        return out;
    }

    private void relaxInto(Graph g, Node from, Node to) {
        Edge e = g.getEdge(from, to);
        if (e == null) {
            g.addEdge(Edges.undirectedEdge(from, to));
            return;
        }
        Endpoint a = e.getEndpoint(from), b = e.getEndpoint(to);
        if (b == Endpoint.ARROW) {
            g.removeEdge(e);
            g.addEdge(new Edge(from, to, (a == Endpoint.ARROW ? Endpoint.CIRCLE : Endpoint.TAIL), Endpoint.CIRCLE));
        } else if (a == Endpoint.ARROW && b == Endpoint.CIRCLE) {
            g.removeEdge(e);
            g.addEdge(new Edge(from, to, Endpoint.CIRCLE, Endpoint.CIRCLE));
        }
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
}
