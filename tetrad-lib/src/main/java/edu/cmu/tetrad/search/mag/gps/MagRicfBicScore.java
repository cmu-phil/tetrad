// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/MagRicfBicScore.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;

final class MagRicfBicScore implements MagScore {
    private final boolean robust;
    private final int restarts;
    private final double ridge;

    MagRicfBicScore(boolean robust, int restarts, double ridge) {
        this.robust = robust;
        this.restarts = restarts;
        this.ridge = ridge;
    }

    @Override
    public double score(Graph magOrPag, CovarianceMatrix S) {
        Graph mag = magOrPag;
        if (containsCircle(magOrPag)) mag = PagToMag.arcAugment(magOrPag);
        double ll = MagRicfStub.likelihood(mag, S, ridge, robust ? restarts : 0);
        int k = MagParamCounter.count(mag);
        int N = S.getSampleSize();
        return 2 * ll - k * Math.log(N);
    }

    private boolean containsCircle(Graph g) {
        for (Edge e : g.getEdges())
            if (e.getEndpoint(e.getNode1()) == Endpoint.CIRCLE || e.getEndpoint(e.getNode2()) == Endpoint.CIRCLE)
                return true;
        return false;
    }
}