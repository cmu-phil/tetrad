// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/GpsSearchConfig.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

final class GpsSearchConfig {
    MagScore score;
    Mode mode = Mode.BASELINE;
    int maxIters = 200;
    int seed = 17;
    boolean verbose = true;
}