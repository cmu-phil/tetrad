// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/GpsSearch.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.Arrays;

public final class GpsSearch {
    private final GpsSearchConfig cfg;
    private final NeighborGenerator gen;

    public GpsSearch(GpsSearchConfig cfg) {
        this.cfg = cfg;
        java.util.List<GpsOperator> ops = Arrays.asList(new AddEdgeOperator(), new DeleteEdgeOperator(), new MakeColliderOperator(), new MakeNoncolliderOperator());
        this.gen = new NeighborGenerator(ops, cfg.mode);
    }

    public Graph run(CovarianceMatrix S, Graph startPag) {
        Graph current = new EdgeListGraph(startPag);
        double best = cfg.score.score(current, S);
        if (cfg.verbose)
            TetradLogger.getInstance().log(String.format("GPS start: score=%.3f, edges=%d", best, current.getNumEdges()));

        for (int iter = 0; iter < cfg.maxIters; iter++) {
            java.util.List<Graph> nbrs = gen.generate(current);
            Candidate bestCand = null;
            for (Graph g2 : nbrs) {
                double s2 = cfg.score.score(g2, S);
                if (bestCand == null || s2 > bestCand.score()) bestCand = new Candidate(g2, s2, "move");
            }
            if (bestCand == null || bestCand.score() <= best + 1e-9) break;
            current = bestCand.pag();
            best = bestCand.score();
            if (cfg.verbose)
                TetradLogger.getInstance().log(String.format("GPS iter %d: score=%.3f, edges=%d", iter + 1, best, current.getNumEdges()));
        }
        return current;
    }
}