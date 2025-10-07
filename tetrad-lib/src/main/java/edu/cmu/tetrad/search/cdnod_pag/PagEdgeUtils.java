package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling operations related to partially oriented edges in a PAG (Partial Ancestral Graph).
 */
public final class PagEdgeUtils {

    /**
     * Private constructor to prevent instantiation of the utility class.
     *
     * This constructor is intentionally empty to ensure that the utility class is not instantiated,
     * as it only contains static methods for operations related to handling partially oriented
     * edges in Partial Ancestral Graphs (PAG).
     */
    private PagEdgeUtils() {
    }

    /**
     * Retrieves a list of edges from the specified graph (PAG) that are partially oriented.
     * An edge is considered partially oriented if at least one of its endpoints is a circle,
     * indicating an uncommitted orientation.
     *
     * @param pag the graph from which partially oriented edges are extracted
     * @return a list of edges where at least one endpoint is a circle
     */
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

    /**
     * Orients the edge in the given PAG (Partial Ancestral Graph) such that there is an arrowhead
     * at the specified child node coming from the specified parent candidate node.
     * The method modifies the endpoint from the parent candidate to the child to be an arrow,
     * indicating a directed edge. It does not alter the opposite endpoint of the edge.
     *
     * @param pag the PAG in which the edge orientation is modified
     * @param parentCand the parent candidate node from which the arrow points
     * @param child the child node where the arrowhead is set
     */
    public static void orientArrowheadAt(Graph pag, Node parentCand, Node child) {

        // Make endpoint at child an ARROW coming from parentCand
        pag.setEndpoint(parentCand, child, Endpoint.ARROW); // parentCand -> child (arrowhead at child)

        // If opposite endpoint at parentCand is ARROW (child -> parentCand), we just created a bi-arrow;
        // legal PAG handling will catch inconsistencies; we don't touch parentCand endpoint here
    }
}