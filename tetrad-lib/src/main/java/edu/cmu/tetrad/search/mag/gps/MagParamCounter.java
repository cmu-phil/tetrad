// File: src/main/java/edu/cmu/tetrad/mag/gps/MagParamCounter.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Graph;

final class MagParamCounter {
    static int count(Graph mag) {
        return mag.getEdges().size();
    }
}