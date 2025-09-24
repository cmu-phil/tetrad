// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/AddEdgeOperator.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

final class AddEdgeOperator implements GpsOperator {
    public String name() {
        return "AddEdge";
    }

    public List<Graph> propose(Graph pag, Mode mode) {
        List<Graph> out = new ArrayList<>();
        java.util.List<Node> V = pag.getNodes();
        for (int i = 0; i < V.size(); i++)
            for (int j = i + 1; j < V.size(); j++) {
                Node x = V.get(i), y = V.get(j);
                if (pag.isAdjacentTo(x, y)) continue;
                Graph g2 = new EdgeListGraph(pag);
                g2.addEdge(Edges.undirectedEdge(x, y));
                out.add(g2);
            }
        return out;
    }
}