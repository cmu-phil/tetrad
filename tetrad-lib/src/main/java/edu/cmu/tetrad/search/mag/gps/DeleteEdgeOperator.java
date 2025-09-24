// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/DeleteEdgeOperator.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;
import java.util.List;

final class DeleteEdgeOperator implements GpsOperator {
    public String name() {
        return "DeleteEdge";
    }

    public List<Graph> propose(Graph pag, Mode mode) {
        List<Graph> out = new ArrayList<>();
        for (Edge e : new ArrayList<>(pag.getEdges())) {
            Graph g2 = new EdgeListGraph(pag);
            g2.removeEdge(e);
            out.add(g2);
        }
        return out;
    }
}