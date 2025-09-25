package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;

public final class GpsMoves {

    private GpsMoves() {
    }

    /**
     * Enumerate all legal local moves (add/remove edges).
     */
    public static List<Move> enumerateLocalMoves(Graph g) {
        List<Move> moves = new ArrayList<>();
        List<Node> nodes = g.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i), y = nodes.get(j);

                if (!g.isAdjacentTo(x, y)) {
                    moves.add(new Move(Move.Type.ADD_DIR, x, y));
                    moves.add(new Move(Move.Type.ADD_DIR, y, x));
                    moves.add(new Move(Move.Type.ADD_BI, x, y));
                    moves.add(new Move(Move.Type.ADD_UG, x, y));
                } else {
                    Edge e = g.getEdge(x, y);
                    if (e.isDirected()) {
                        moves.add(new Move(Move.Type.REM_DIR, e.getNode1(), e.getNode2()));
                    } else if (Edges.isBidirectedEdge(e)) {
                        moves.add(new Move(Move.Type.REM_BI, x, y));
                    } else if (Edges.isUndirectedEdge(e)) {
                        moves.add(new Move(Move.Type.REM_UG, x, y));
                    }
                }
            }
        }
        return moves;
    }

    /**
     * Apply the given move to the graph.
     */
    public static void apply(Move m, Graph g) {
        switch (m.type) {
            case ADD_DIR -> g.addDirectedEdge(m.x, m.y);
            case REM_DIR -> g.removeEdge(m.x, m.y);
            case ADD_BI -> g.addBidirectedEdge(m.x, m.y);
            case REM_BI -> g.removeEdge(m.x, m.y);
            case ADD_UG -> g.addUndirectedEdge(m.x, m.y);
            case REM_UG -> g.removeEdge(m.x, m.y);
        }
    }

    /**
     * Undo is optional but sometimes convenient.
     */
    public static void undo(Move m, Graph g) {
        switch (m.type) {
            case ADD_DIR -> g.removeEdge(m.x, m.y);
            case REM_DIR -> g.addDirectedEdge(m.x, m.y);
            case ADD_BI -> g.removeEdge(m.x, m.y);
            case REM_BI -> g.addBidirectedEdge(m.x, m.y);
            case ADD_UG -> g.removeEdge(m.x, m.y);
            case REM_UG -> g.addUndirectedEdge(m.x, m.y);
        }
    }

//    /**
//     * Check whether applying the move m to graph g would yield a valid MAG step.
//     */
//    private static boolean hasUndirectedIncident(Graph g, Node v) {
//        for (Edge e : g.getEdges(v)) {
//            if (Edges.isUndirectedEdge(e)) return true;
//        }
//        return false;
//    }

//    public static boolean isValid(Move m, Graph g) {
//        Node x = m.x;
//        Node y = m.y;
//
//        switch (m.type) {
//            case ADD_DIR -> {
//                // Already adjacent? Invalid.
//                if (g.isAdjacentTo(x, y)) return false;
//                // Would create a directed cycle? Invalid.
//                if (g.paths().existsDirectedPath(y, x)) return false;
//                // MAG rule: a node with an undirected edge cannot have arrowheads.
//                // y gets an arrowhead in x->y, so y must NOT be in an undirected component.
//                if (hasUndirectedIncident(g, y)) return false;
//                return true;
//            }
//            case ADD_BI -> {
//                // No bidirected if already adjacent.
//                if (g.isAdjacentTo(x, y)) return false;
//                // MAG rule: both endpoints of a bidirected edge get arrowheads,
//                // so neither endpoint may have any undirected edge incident.
//                if (hasUndirectedIncident(g, x) || hasUndirectedIncident(g, y)) return false;
//                return true;
//            }
//            case ADD_UG -> {
//                // No UG if already adjacent
//                if (g.isAdjacentTo(x, y)) return false;
//                // UG edges only valid if neither endpoint has ANY arrowhead (parents/spouses or children with ARROW at them)
//                if (!g.getNodesInTo(x, Endpoint.ARROW).isEmpty() || !g.getNodesInTo(y, Endpoint.ARROW).isEmpty())
//                    return false;
//                if (!g.getNodesOutTo(x, Endpoint.ARROW).isEmpty() || !g.getNodesOutTo(y, Endpoint.ARROW).isEmpty())
//                    return false;
//                return true;
//            }
//            case REM_DIR, REM_BI, REM_UG -> {
//                // Safe to remove as long as the edge exists
//                return g.isAdjacentTo(x, y);
//            }
//            default -> throw new IllegalStateException("Unexpected move type: " + m.type);
//        }
//    }

    public static boolean isValid(Move m, Graph g) {
        Node x = m.x;
        Node y = m.y;

        // helpers
        boolean xHasUG = hasUndirectedIncident(g, x);
        boolean yHasUG = hasUndirectedIncident(g, y);

        switch (m.type) {
            case ADD_DIR -> {
                // must not already be adjacent
                if (g.isAdjacentTo(x, y)) return false;

                // no directed cycles
                if (g.paths().existsDirectedPath(y, x)) return false;

                // MAG rule: nodes incident to ANY undirected edge cannot have arrowheads at all
                // so neither endpoint can currently be in the UG-component
                if (xHasUG || yHasUG) return false;

                // ok to mix directed and bidirected, as long as no almost-directed cycle
                // adding x->y is illegal if y is ancestor of x (handled by cycle test above)
                return true;
            }

            case ADD_BI -> {
                if (g.isAdjacentTo(x, y)) return false;

                // forbid ancestry in either direction (prevents almost-directed cycles)
                if (g.paths().existsDirectedPath(x, y)) return false;
                if (g.paths().existsDirectedPath(y, x)) return false;

                // UG-vertices cannot take spouses either; you already covered UG with the check below in ADD_UG,
                // but adding a spouse on a UG node would be illegal.
                if (xHasUG || yHasUG) return false;

                return true;
            }

            case ADD_UG -> {
                if (g.isAdjacentTo(x, y)) return false;

                // MAG rule: any node that has an undirected edge may have ONLY undirected edges.
                // So both endpoints must currently have NO arrowheads (no parents, no children, no spouses).
                if (hasAnyArrowheadIncident(g, x)) return false;
                if (hasAnyArrowheadIncident(g, y)) return false;

                return true;
            }

            case REM_DIR, REM_BI, REM_UG -> {
                // removing any existing edge is always OK; Graph.removeEdge(x,y) will handle type
                return g.isAdjacentTo(x, y);
            }

            default -> throw new IllegalStateException("Unexpected move type: " + m.type);
        }
    }

    private static boolean hasUndirectedIncident(Graph g, Node v) {
        for (Edge e : g.getEdges(v)) {
            if (Edges.isUndirectedEdge(e)) return true;
        }
        return false;
    }

    private static boolean hasAnyArrowheadIncident(Graph g, Node v) {
        for (Edge e : g.getEdges(v)) {
            if (Edges.isBidirectedEdge(e)) return true;      // arrowheads at both ends
            if (e.isDirected()) return true;                 // treat *any* directed incidence as incompatible with UG
        }
        return false;
    }
}