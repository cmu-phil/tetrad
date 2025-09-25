// src/main/java/edu/cmu/tetrad/search/mag/gps/PagNeighbors.java
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.sem.Ricf;

import java.util.*;

public final class PagNeighbors {

    private PagNeighbors() {}

    /** Build a small canonical string for a PAG to de-duplicate neighbors. */
    public static String canonicalPagKey(Graph pag) {
        List<String> marks = new ArrayList<>();
        for (Edge e : pag.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            String s = a.getName() + "-"
                       + endpointChar(pag.getEndpoint(a,b))
                       + endpointChar(pag.getEndpoint(b,a))
                       + "-" + b.getName();
            if (a.getName().compareTo(b.getName()) > 0) s = b.getName() + s.charAt(3) + s.charAt(2) + a.getName();
            marks.add(s);
        }
        Collections.sort(marks);
        return String.join("|", marks);
    }

    private static char endpointChar(Endpoint ep) {
        return switch (ep) {
            case TAIL -> '-';
            case ARROW -> '>';
            case CIRCLE -> 'o';
            default -> '?';
        };
    }

    /**
     * Generate neighbors of a PAG by:
     *   (1) lifting to the Zhang canonical MAG,
     *   (2) doing ONE legal MAG move,
     *   (3) projecting back to PAG.
     * All returned PAGs are realizable and legal (since they come from a legal MAG).
     */
    public static List<Graph> neighborsViaZhangLift(Graph pag) {
        Graph mag = GraphTransforms.zhangMagFromPag(pag);            // <-- your canonical lift
        if (!mag.paths().isLegalMag()) {
            throw new IllegalStateException("Canonical MAG is not legal; investigate PAG -> MAG lift.");
        }

        Set<String> seen = new HashSet<>();
        List<Graph> out = new ArrayList<>();

        for (Move m : GpsMoves.enumerateLocalMoves(mag)) {
            if (!GpsMoves.isValid(m, mag)) continue;

            GpsMoves.apply(m, mag);
            boolean ok = mag.paths().isLegalMag();
            Graph pag2 = null;
            if (ok) {
                pag2 = GraphTransforms.magToPag(mag);
                String key = canonicalPagKey(pag2);
                if (seen.add(key)) out.add(pag2);
            }
            GpsMoves.undo(m, mag);
        }
        return out;
    }

    /**
     * One greedy improvement step over PAG neighbors produced via Zhang-lift,
     * scoring each by running RICF on its canonical MAG.
     */
    public static Graph bestNeighborByRicf(Graph pag, ICovarianceMatrix cov, double tol) {
        ScoreRicfMag scorer = new ScoreRicfMag(cov, tol);
        double s0 = scorer.score(GraphTransforms.zhangMagFromPag(pag)); // score current PAG via its canonical MAG
        Graph best = null;
        double bestS = s0;

        for (Graph pag2 : neighborsViaZhangLift(pag)) {
            double s2 = scorer.score(GraphTransforms.zhangMagFromPag(pag2));
            if (s2 > bestS) {
                bestS = s2;
                best = pag2;
            }
        }
        return (best == null) ? pag : best;
    }
}