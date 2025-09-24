// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/Candidate.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Graph;

record Candidate(Graph pag, double score, String move) {
}
