// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/GpsOperator.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Graph;

import java.util.List;

interface GpsOperator {
    String name();

    List<Graph> propose(Graph pag, Mode mode);
}