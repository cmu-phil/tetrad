package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The GraphLegalityCheck class provides utility methods for validating the legality of specific types of graphs,
 * including Directed Acyclic Graphs (PAGs) and Mixed Ancestral Graphs (MAGs). It includes methods to assess the
 * legality of PAGs and MAGs both explicitly, with detailed results, and quietly, returning only a boolean outcome.
 * <p>
 * The methods in this class can be utilized to verify structural and conditional validity of graphs in various
 * applications, such as causal inference and graphical modeling.
 */
public class GraphLegalityCheck {

    /**
     * Default private constructor to prevent instantiation of the GraphLegalityCheck class.
     *
     * This class provides static utility methods for checking the legality of Partial Ancestral Graphs (PAG)
     * and Mixed Ancestral Graphs (MAG). Since the class is utility-based, it should not be instantiated.
     */
    private GraphLegalityCheck() {

    }

    /**
     * Checks if the provided Directed Acyclic Graph (PAG) is a legal PAG.
     *
     * @param pag       The Directed Acyclic Graph (PAG) to be checked
     * @param selection The set of nodes to be conditioned on
     * @return A LegalPagRet object indicating whether the PAG is legal or not, along with a reason if it is not legal.
     */
    public static LegalPagRet isLegalPag(Graph pag, Set<Node> selection) {
        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) {
                return new LegalPagRet(false, "Node " + n + " is not measured");
            }
        }

        Graph mag = GraphTransforms.zhangMagFromPag(pag);
        LegalMagRet legalMag = isLegalMag(mag, selection);

        if (!legalMag.isLegalMag()) {
            return new LegalPagRet(false, legalMag.getReason() + " in a MAG implied by this graph");
        }

        Graph pag2 = GraphTransforms.dagToPag(mag);

        if (!pag.equals(pag2)) {
            String edgeMismatch = "";

            for (Edge e : pag.getEdges()) {
                Edge e2 = pag2.getEdge(e.getNode1(), e.getNode2());
                if (!e.equals(e2)) {
                    edgeMismatch = "For example, the original PAG has edge " + e + " whereas the reconstituted graph has edge " + e2;
                    break;
                }
            }

            String reason = legalMag.isLegalMag() ? "The MAG implied by this graph was a legal MAG, but one cannot recover the original graph " + "by finding the PAG of an implied MAG â this graph may lie between a MAG and a PAG" : "The MAG implied by this graph was not legal, and one cannot recover the original graph from its implied PAG";

            if (!edgeMismatch.isEmpty()) {
                reason += ". " + edgeMismatch;
            }

            if (!edgeMismatch.isEmpty()) {
                return new LegalPagRet(false, reason);
            }
        }

        return new LegalPagRet(true, "This is a legal PAG");
    }

    /**
     * Determines whether the given graph is a legal Mixed Ancestral Graph (MAG).
     *
     * @param mag       the graph to be checked
     * @param selection the set of nodes to be conditioned on
     * @return a LegalMagRet object indicating whether the graph is legal and providing an error message if it is not
     */
    public static LegalMagRet isLegalMag(Graph mag, Set<Node> selection) {
        for (Node n : mag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                return new LegalMagRet(false, "Node " + n + " is not measured");
            }
        }

        List<Node> nodes = mag.getNodes();

        for (Edge edge : mag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (!mag.isAdjacentTo(x, y)) continue;

            if (mag.getEdges(x, y).size() > 1) {
                return new LegalMagRet(false, "There is more than one edge between " + x + " and " + y);
            }

            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge) || Edges.isUndirectedEdge(edge))) {
                return new LegalMagRet(false, "Edge " + edge + " should be directed, bidirected, or undirected.");
            }
        }

        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPath(n, n)) {
                return new LegalMagRet(false, "Acyclicity violated: There is a directed cyclic path from " + n + " to itself");
            }
        }

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                List<List<Node>> forwardPaths = mag.paths().directedPaths(x, y, 1);
                if (!forwardPaths.isEmpty()) {
                    return new LegalMagRet(false, "Bidirected edge semantics is violated: Directed path exists from " + x + " to " + y + ". An example path is " + GraphUtils.pathString(mag, forwardPaths.getFirst(), false));
                }

                List<List<Node>> backwardPaths = mag.paths().directedPaths(y, x, 1);
                if (!backwardPaths.isEmpty()) {
                    return new LegalMagRet(false, "Bidirected edge semantics is violated: Directed path exists from " + y + " to " + x + ". An example path is " + GraphUtils.pathString(mag, backwardPaths.getFirst(), false));
                }
            }
        }

        Set<Node> sel = (selection == null) ? Collections.emptySet() : new HashSet<>(selection);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) {
                    if (mag.paths().existsInducingPath(x, y, sel)) {
                        return new LegalMagRet(false, "Not maximal: Inducing path exists between non-adjacent " + x + " and " + y);
                    }
                }
            }
        }

        for (Edge edge : mag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (Edges.isUndirectedEdge(edge)) {
                for (Node z : mag.getAdjacentNodes(x)) {
                    Edge zx = mag.getEdge(z, x);
                    if (mag.isParentOf(z, x) || Edges.isBidirectedEdge(zx)) {
                        return new LegalMagRet(false, "Undirected edge constraint violated: " + z + " is a parent or spouse of " + x);
                    }
                }

                for (Node z : mag.getAdjacentNodes(y)) {
                    Edge zy = mag.getEdge(z, y);
                    if (mag.isParentOf(z, y) || Edges.isBidirectedEdge(zy)) {
                        return new LegalMagRet(false, "Undirected edge constraint violated: " + z + " is a parent or spouse of " + y);
                    }
                }
            }
        }

        return new LegalMagRet(true, "This is a legal MAG");
    }

    /**
     * Determines whether the provided Partial Ancestral Graph (PAG) is a legal PAG without providing detailed error
     * messages.
     *
     * @param pag       The Partial Ancestral Graph (PAG) to be checked.
     * @param selection The set of nodes to be conditioned on.
     * @return true if the PAG is legal, false otherwise.
     */
    public static boolean isLegalPagQuiet(Graph pag, Set<Node> selection) {
        // 1) All nodes must be measured.
        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) return false;
        }

        // 2) The implied MAG must be legal.
        Graph mag = GraphTransforms.zhangMagFromPag(pag);
        if (!isLegalMagQuiet(mag, selection)) return false;

        // 3) Reconstitute PAG from the implied MAG.
        Graph pag2 = GraphTransforms.dagToPag(mag);

        // Mirror verbose logic:
        // If graphs differ, only fail if there exists an original edge that doesn't match.
        if (!pag.equals(pag2)) {
            for (Edge e : pag.getEdges()) {
                Edge e2 = pag2.getEdge(e.getNode1(), e.getNode2());
                if (!e.equals(e2)) {
                    // Verbose version only returns illegal when it finds a concrete mismatch
                    // for an original edge; extra edges present *only* in pag2 are allowed.
                    return false;
                }
            }
            // No mismatching original edge found → treat as legal (lenient)
        }

        return true;
    }

    /**
     * Determines whether the given graph is a legal Mixed Ancestral Graph (MAG) without providing detailed error
     * messages.
     *
     * @param mag       the graph to be checked
     * @param selection the set of nodes to be conditioned on
     * @return true if the graph is a legal MAG, false otherwise
     */
    public static boolean isLegalMagQuiet(Graph mag, Set<Node> selection) {
        // No LATENT nodes in a MAG over measured variables.
        for (Node n : mag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) return false;
        }

        // Edge sanity: at most one edge per pair; only allowed types.
        for (Edge edge : mag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (!mag.isAdjacentTo(x, y)) continue;
            if (mag.getEdges(x, y).size() > 1) return false;

            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge) || Edges.isUndirectedEdge(edge))) {
                return false;
            }
        }

        // Acyclicity of the directed component.
        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPath(n, n)) return false;
        }

        // Bidirected semantics: no directed path in either direction between spouses.
        for (Edge e : mag.getEdges()) {
            if (!Edges.isBidirectedEdge(e)) continue;
            Node x = e.getNode1();
            Node y = e.getNode2();
            if (!mag.paths().directedPaths(x, y, 1).isEmpty()) return false;
            if (!mag.paths().directedPaths(y, x, 1).isEmpty()) return false;
        }

        // Maximality: no inducing path between non-adjacent nodes given selection.
        Set<Node> sel = (selection == null) ? Collections.emptySet() : new HashSet<>(selection);
        List<Node> nodes = mag.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node x = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                Node y = nodes.get(j);
                if (!mag.isAdjacentTo(x, y) && mag.paths().existsInducingPath(x, y, sel)) return false;
            }
        }

        // Undirected-edge constraint: endpoints have no parents/spouses.
        for (Edge edge : mag.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) continue;

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Node z : mag.getAdjacentNodes(x)) {
                Edge zx = mag.getEdge(z, x); // mirror verbose: no null-guard
                if (mag.isParentOf(z, x) || Edges.isBidirectedEdge(zx)) return false;
            }
            for (Node z : mag.getAdjacentNodes(y)) {
                Edge zy = mag.getEdge(z, y);
                if (mag.isParentOf(z, y) || Edges.isBidirectedEdge(zy)) return false;
            }
        }

        return true;
    }

    /**
     * Stores a result for checking whether a graph is a legal MAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalMagRet {

        /**
         * Whether the graph is a legal MAG.
         */
        private final boolean legalMag;

        /**
         * The reason why the graph is not a legal MAG, if not.
         */
        private final String reason;

        /**
         * Constructs a new LegalMagRet object.
         *
         * @param legalPag Whether the graph is a legal MAG.
         * @param reason   The reason why the graph is not a legal MAG, if not.
         */
        public LegalMagRet(boolean legalPag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalMag = legalPag;
            this.reason = reason;
        }

        /**
         * Returns whether the graph is a legal MAG.
         *
         * @return Whether the graph is a legal MAG.
         */
        public boolean isLegalMag() {
            return legalMag;
        }

        /**
         * Returns the reason why the graph is not a legal MAG, if not.
         *
         * @return The reason why the graph is not a legal MAG, if not.
         */
        public String getReason() {
            return reason;
        }
    }

    /**
     * Stores a result for checking whether a graph is a legal PAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalPagRet {

        /**
         * Whether the graph is a legal PAG.
         */
        private final boolean legalPag;

        /**
         * The reason why the graph is not a legal PAG, if not.
         */
        private final String reason;

        /**
         * Constructs a new LegalPagRet object.
         *
         * @param legalPag Whether the graph is a legal PAG.
         * @param reason   The reason why the graph is not a legal PAG, if not.
         */
        public LegalPagRet(boolean legalPag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalPag = legalPag;
            this.reason = reason;
        }

        /**
         * Returns whether the graph is a legal PAG.
         *
         * @return Whether the graph is a legal PAG.
         */
        public boolean isLegalPag() {
            return legalPag;
        }

        /**
         * Returns the reason why the graph is not a legal PAG, if not.
         *
         * @return The reason why the graph is not a legal PAG, if not.
         */
        public String getReason() {
            return reason;
        }
    }
}