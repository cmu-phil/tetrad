// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/MagScore.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;

interface MagScore {
    double score(Graph magOrPag, CovarianceMatrix S);
}
