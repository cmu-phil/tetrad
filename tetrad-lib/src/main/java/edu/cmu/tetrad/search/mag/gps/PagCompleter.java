// File: src/main/java/edu/cmu/tetrad/mag/gps/PagCompleter.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.stream.Collectors;

final class PagCompleter {
    Graph complete(Graph core) {
        Graph g = new EdgeListGraph(core);
        boolean changed;
        int passes = 0;
        do {
            changed = false;
            changed |= RuleUtils.applyR1_Graph(g);
            changed |= RuleUtils.applyR2_Graph(g);
            changed |= RuleUtils.applyR3_Graph(g);
            changed |= applyR4PrimeTailPropagation(g);
            changed |= RuleUtils.applyR5_R10_TailRules(g);
        } while (changed && ++passes < 20);
        return g;
    }

    private boolean applyR4PrimeTailPropagation(Graph g) {
        boolean changed = false;
        for (Node z : g.getNodes()) {
            java.util.List<Node> pa = parentsOf(g, z);
            if (pa.size() >= 2) {
                for (int i = 0; i < pa.size(); i++)
                    for (int j = i + 1; j < pa.size(); j++) {
                        Node x = pa.get(i), y = pa.get(j);
                        Edge exy = g.getEdge(x, y);
                        if (exy != null && exy.getEndpoint(x) == Endpoint.CIRCLE && exy.getEndpoint(y) == Endpoint.CIRCLE) {
                            g.removeEdge(exy);
                            g.addEdge(Edges.undirectedEdge(x, y));
                            changed = true;
                        }
                    }
            }
        }
        return changed;
    }

    private java.util.List<Node> parentsOf(Graph g, Node v) {
        return g.getAdjacentNodes(v).stream()
                .filter(u -> {
                    Edge e = g.getEdge(u, v);
                    return e != null && e.getEndpoint(v) == Endpoint.ARROW; // u *-> v
                })
                .collect(Collectors.toList());
    }
}