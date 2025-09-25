// src/main/java/edu/cmu/tetrad/search/mag/gps/Gps.java
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4Strategy;

import java.util.*;

public final class Gps {

    private final ICovarianceMatrix cov;
    private final double tolerance;
    private final double ridge;      // if you want to pass through to Ricf later
    private final int restarts;
    private final int maxIters;
    private final FciOrient orienter;

    public Gps(ICovarianceMatrix cov, double tolerance, double ridge, int restarts, int maxIters) {
        this.cov = cov;
        this.tolerance = tolerance;
        this.ridge = ridge;
        this.restarts = Math.max(1, restarts);
        this.maxIters = Math.max(1, maxIters);

        R0R4Strategy rRules = new R0R4StrategyGpsRicf(cov, /*tolerance*/ 1e-6)
                .setEps(1e-8)
                .setVerbose(false);
        orienter = new FciOrient(rRules);
    }

    public Graph searchInMagSpace() {
        ScoreRicfMag score = new ScoreRicfMag(cov, tolerance);

        Graph best = emptyMag(cov.getVariableNames());
        double bestScore = score.score(best);

        Random rnd = new Random(42);

        for (int r = 0; r < restarts; r++) {

            Graph g = (r == 0) ? best.copy() : randomStart(cov.getVariableNames(), rnd);
            // keep regenerating until legal
            int guard = 0;
            while (!g.paths().isLegalMag() && guard++ < 20) g = randomStart(cov.getVariableNames(), rnd);

            // R0/R4-closure before scoring this start
            orienter.finalOrientation(g);              // or the exact method in your FciOrient, e.g. doOrientation(g)
            if (!g.paths().isLegalMag()) {   // safety
                // if your orienter returns illegal (shouldn’t), fall back to empty
                g = emptyMag(cov.getVariableNames());
            }
            double s = score.score(g);

//            // start from current best on r==0; otherwise from a random legal MAG
//            Graph g = (r == 0) ? best.copy() : randomStart(cov.getVariableNames(), rnd);
//            // ensure random start is legal; regenerate until legal (safety net)
//            int guard = 0;
//            while (!g.paths().isLegalMag() && guard++ < 20) {
//                g = randomStart(cov.getVariableNames(), rnd);
//            }
//
//            double s = score.score(g);

            boolean improved;
            int iters = 0;

            do {
                improved = false;

                List<Move> moves = GpsMoves.enumerateLocalMoves(g);
                Collections.shuffle(moves, rnd); // de-bias

                for (Move m : moves) {
                    if (!GpsMoves.isValid(m, g)) continue;

                    // tentatively apply
                    GpsMoves.apply(m, g);

                    GpsMoves.apply(m, g);

                    // still legal under MAG constraints?
                    if (!g.paths().isLegalMag()) {
                        GpsMoves.undo(m, g);
                        continue;
                    }

                    // CLOSE under R0/R4 with likelihood-based strategy
                    orienter.finalOrientation(g);                          // or doOrientation(g) depending on your API
                    if (!g.paths().isLegalMag()) {               // belt & suspenders
                        GpsMoves.undo(m, g);
                        continue;
                    }

                    double s2 = score.score(g);
                    if (s2 > s) {
                        s = s2;
                        improved = true;
                        break; // keep first improvement
                    } else {
                        GpsMoves.undo(m, g);
                    }

//                    // hard legality check; revert immediately if illegal
//                    if (!g.paths().isLegalMag()) {
//                        GpsMoves.undo(m, g);
//                        continue;
//                    }
//
//                    // score only legal candidates
//                    double s2 = score.score(g);
//
//                    if (s2 > s) {           // first-improvement
//                        s = s2;
//                        improved = true;     // keep the applied move
//                        break;
//                    } else {
//                        GpsMoves.undo(m, g); // discard and try next move
//                    }
                }

                iters++;
            } while (improved && iters < maxIters);

//            if (s > bestScore) {
//                bestScore = s;
//                best = g.copy();
//            }

            if (s > bestScore) {
//                if (!g.paths().isLegalMag()) {
//                    // should never happen now, but be explicit
//                    continue;
//                }
                bestScore = s;
                best = g.copy();
            }
        }

        Graph result = best.copy();
        orienter.finalOrientation(result);
        if (!result.paths().isLegalMag()) result = best; // fall back if needed
        return result;

//        return best;
    }

    // inside edu.cmu.tetrad.search.mag.gps.Gps

    public Graph search() {
        // start from empty PAG over the variables
        Graph pag = new EdgeListGraph();
        for (String v : cov.getVariableNames()) pag.addNode(new GraphNode(v));

        // optional: do a few random Zhang-lift neighbor steps even from empty
        // loop: greedy hill climb in PAG space
        int it = 0;
        while (it++ < maxIters) {
            Graph next = PagNeighbors.bestNeighborByRicf(pag, cov, tolerance);
            if (next == pag) break;     // no improvement
            pag = next;
        }

        // return a MAG for downstream code, e.g., the Zhang representative of the final PAG
        return pag;//GraphTransforms.zhangMagFromPag(pag);
    }

    private static Graph emptyMag(List<String> names) {
        Graph g = new EdgeListGraph();
        for (String v : names) g.addNode(new GraphNode(v));
        return g;
    }

//    private static Graph randomStart(List<String> names, Random rnd) {
//        Graph g = emptyMag(names);
//        List<Node> nodes = g.getNodes();
//        int p = nodes.size();
//        for (int i = 0; i < p; i++) {
//            for (int j = i+1; j < p; j++) {
//                Node x = nodes.get(i), y = nodes.get(j);
//                int draw = rnd.nextInt(6); // sparse
//                Move m = switch (draw) {
//                    case 0 -> new Move(Move.Type.ADD_DIR, x, y);
//                    case 1 -> new Move(Move.Type.ADD_DIR, y, x);
//                    case 2 -> new Move(Move.Type.ADD_BI, x, y);
//                    case 3 -> new Move(Move.Type.ADD_UG, x, y);
//                    default -> null;
//                };
//                if (m != null && GpsMoves.isValid(m, g)) GpsMoves.apply(m, g);
//            }
//        }
//        return g;
//    }

    private static Graph randomStart(List<String> names, Random rnd) {
        Graph g = emptyMag(names);
        List<Node> nodes = g.getNodes();
        int p = nodes.size();

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                Node x = nodes.get(i), y = nodes.get(j);
                int draw = rnd.nextInt(6); // sparse
                Move m = switch (draw) {
                    case 0 -> new Move(Move.Type.ADD_DIR, x, y);
                    case 1 -> new Move(Move.Type.ADD_DIR, y, x);
                    case 2 -> new Move(Move.Type.ADD_BI, x, y);
                    case 3 -> new Move(Move.Type.ADD_UG, x, y);
                    default -> null;
                };
                if (m != null && GpsMoves.isValid(m, g)) {
                    GpsMoves.apply(m, g);
                    if (!g.paths().isLegalMag()) {
                        GpsMoves.undo(m, g);
                    }
                }
            }
        }
        return g;
    }
}