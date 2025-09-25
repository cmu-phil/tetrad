// src/main/java/edu/cmu/tetrad/search/mag/gps/ScoreRicfMag.java
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.Ricf;

public final class ScoreRicfMag {
    private final ICovarianceMatrix cov;
    private final double tolerance;
    private final double penalty; // e.g., log(n) for BIC
    private final int n;

    public ScoreRicfMag(ICovarianceMatrix cov, double tolerance) {
        this.cov = cov;
        this.tolerance = tolerance;
        this.n = cov.getSampleSize();
        this.penalty = Math.log(Math.max(2, n));
    }

    public double score(Graph mag) {
        // Run RICF on the graph (graph version)
        Ricf.RicfResult fit = new Ricf().ricf2(mag, cov, tolerance);

        // log-likelihood from fitted precision (constant-free)
        double ll = Ricf.logLikMAG(
                fit.getBhat(), fit.getOhat(), fit.getLhat(),
                // UG set = nodes with no incoming arrowheads:
                GpsUtils.ugIndices(mag, cov.getVariableNames()),
                cov
        );

        int k = GpsUtils.numFreeParams(mag); // see below
        return ll - 0.5 * penalty * k;       // BIC
    }
}