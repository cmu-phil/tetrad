// =========================
// File: src/main/java/edu/cmu/tetrad/search/mag/gps/MagRicfStub.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.Ricf;

import java.util.ArrayList;
import java.util.List;

final class MagRicfStub {

    /**
     * Compute constant-free Gaussian log-likelihood for a MAG:
     * 1) Fit (B, Ω, Λ) via RICF on the given graph and covariance.
     * 2) Evaluate logLikMAG(B, Ω, Λ, ug, cov).
     *
     * NOTE: ridge/restarts are currently unused; keep them if your caller expects this signature.
     */
    static double likelihood(Graph mag, ICovarianceMatrix cov, double ridge, int restarts) {
        // Fit via RICF (uses your Colt-based implementation)
        Ricf.RicfResult fit = new Ricf().ricf2(mag, cov, 1e-8);

        // UG indices in the covariance variable order
        int[] ug = ugNodesInCovOrder(mag, cov);

        // Constant-free log-likelihood
        return Ricf.logLikMAG(fit.getBhat(), fit.getOhat(), fit.getLhat(), ug, cov);
    }

    /** Indices of nodes with no incoming ARROW endpoints, in cov.getVariableNames() order. */
    private static int[] ugNodesInCovOrder(Graph mag, ICovarianceMatrix cov) {
        List<String> names = cov.getVariableNames();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Node v = mag.getNode(names.get(i));
            if (mag.getNodesInTo(v, Endpoint.ARROW).isEmpty()) {
                idx.add(i);
            }
        }
        int[] out = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) out[i] = idx.get(i);
        return out;
    }
}