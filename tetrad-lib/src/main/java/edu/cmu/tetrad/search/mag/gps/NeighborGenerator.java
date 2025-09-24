// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/NeighborGenerator.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;

final class NeighborGenerator {
    private final java.util.List<GpsOperator> ops;
    private final Mode mode;

    NeighborGenerator(java.util.List<GpsOperator> ops, Mode mode) {
        this.ops = ops;
        this.mode = mode;
    }

    java.util.List<Graph> generate(Graph pag) {
        java.util.List<Graph> out = new ArrayList<>();
        for (GpsOperator op : ops)
            for (Graph g2 : op.propose(pag, mode)) {
                if (!Validity.quickReject(g2) && Validity.noAlmostDirectedCycle(g2)) out.add(g2);
            }
        return out;
    }
}