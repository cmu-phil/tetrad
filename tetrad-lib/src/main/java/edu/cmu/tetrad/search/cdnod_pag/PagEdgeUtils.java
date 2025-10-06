package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;

public final class PagEdgeUtils {
    private PagEdgeUtils() {}

    /** Return all edges X o-o Y (both endpoints circle) or other partially oriented pairs. */
    public static List<Edge> partiallyOriented(Graph pag) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : pag.getEdges()) {
            Endpoint a = pag.getEndpoint(e.getNode1(), e.getNode2());
            Endpoint b = pag.getEndpoint(e.getNode2(), e.getNode1());
            // candidates where at least one end is a circle (uncommitted)
            if (a == Endpoint.CIRCLE || b == Endpoint.CIRCLE) out.add(e);
        }
        return out;
    }

    /** Set arrowhead at child (parentCand ?-?> child), keep other end as-is unless illegal. */
    public static void orientArrowheadAt(Graph pag, Node parentCand, Node child) {

        // Make endpoint at child an ARROW coming from parentCand
        pag.setEndpoint(parentCand, child, Endpoint.ARROW); // parentCand -> child (arrowhead at child)

        // If opposite endpoint at parentCand is ARROW (child -> parentCand), we just created a bi-arrow;
        // legal PAG handling will catch inconsistencies; we don't touch parentCand endpoint here
    }
}