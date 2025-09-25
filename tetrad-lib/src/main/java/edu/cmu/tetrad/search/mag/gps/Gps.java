// src/main/java/edu/cmu/tetrad/search/mag/gps/Gps.java
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IGraphSearch;

import java.util.*;

public final class Gps implements IGraphSearch {
    public static final class Params {
        public int maxIt = 10_000;
        public int restarts = 10;
        public boolean useTabu = true;
        public int tabuLength = 25;
        public double ricfTol = 1e-8;
        public long seed = 1234L;
        public double ridge = 0.0; // reserved
    }

    private final ICovarianceMatrix cov;
    private final Params p;
    private final Random rnd;

    public Gps(ICovarianceMatrix cov, Params p) {
        this.cov = cov; this.p = p; this.rnd = new Random(p.seed);
    }

    @Override
    public Graph search() {
        ScoreRicfMag scoreFn = new ScoreRicfMag(cov, p.ricfTol);
        Graph bestGlobal = null;
        double bestGlobalScore = Double.NEGATIVE_INFINITY;

        for (int r = 0; r < p.restarts; r++) {
            Graph g = emptyMAG(cov.getVariableNames());
            double s = scoreFn.score(g);

            Deque<String> tabu = new ArrayDeque<>();
            int it = 0;

            while (it++ < p.maxIt) {
                Move bestMove = null;
                double bestDelta = 0.0;

                for (Move m : GpsMoves.enumerateLocalMoves(g)) {
                    if (p.useTabu && tabu.contains(m.key)) continue;
                    if (!GpsMoves.isValid(m, g)) continue;

                    Graph g2 = new EdgeListGraph(g);
                    GpsMoves.apply(m, g2);

                    double s2 = scoreFn.score(g2);
                    double delta = s2 - s;
                    if (delta > bestDelta) { bestDelta = delta; bestMove = m; }
                }
                if (bestMove == null) break; // local optimum

                GpsMoves.apply(bestMove, g);
                s += bestDelta;

                if (p.useTabu) {
                    tabu.addLast(bestMove.key);
                    if (tabu.size() > p.tabuLength) tabu.removeFirst();
                }
            }
            if (s > bestGlobalScore) { bestGlobalScore = s; bestGlobal = g; }
        }
        return bestGlobal;
    }

    private static Graph emptyMAG(List<String> names) {
        Graph g = new EdgeListGraph();
        for (String n : names) g.addNode(new GraphNode(n));
        return g; // no edges → valid MAG
    }

    // You’ll also implement getComparisonGraph(), getParams(), etc., per your GraphSearch conventions.
}