// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/PagToMag.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

final class PagToMag {
    static Graph arcAugment(Graph cpag) {
        return new EdgeListGraph(cpag);
    }
}