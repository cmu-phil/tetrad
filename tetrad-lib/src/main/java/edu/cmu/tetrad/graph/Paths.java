/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.*;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <p>Paths class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Paths implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final Graph graph;

    /**
     * <p>Constructor for Paths.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Paths(Graph graph) {
        if (graph == null) throw new NullPointerException("Null graph");
        this.graph = graph;
    }

    /**
     * Adds a node to a set within the given map. If there is no set associated with the specified node, a new set is
     * created and added to the map.
     *
     * @param previous The map containing sets of nodes.
     * @param b        The node to be added to the set.
     * @param c        The node associated with the set in the map.
     */
    private static void addToSet(Map<Node, Set<Node>> previous, Node b, Node c) {
        previous.computeIfAbsent(c, k -> new HashSet<>());
        Set<Node> list = previous.get(c);
        list.add(b);
    }

    /**
     * Get the prefix for a list of nodes up to a specified index.
     *
     * @param pi The list of nodes.
     * @param i  The index up to which to include nodes in the prefix.
     * @return A set of nodes representing the prefix.
     */
    private static Set<Node> getPrefix(List<Node> pi, int i) {
        Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(pi.get(j));
        }

        return prefix;
    }

    /**
     * Generates a directed acyclic graph (DAG) based on the given list of nodes using Raskutti and Uhler's method.
     *
     * @param pi      a list of nodes representing the set of vertices in the graph
     * @param g       the graph
     * @param verbose whether to print verbose output
     * @return a Graph object representing the generated DAG.
     */
    public static Graph getDag(List<Node> pi, Graph g, boolean verbose) {
        Graph graph = new EdgeListGraph(pi);

        for (int a = 0; a < pi.size(); a++) {
            for (Node b : getParents(pi, a, g, verbose, false)) {
                graph.addDirectedEdge(b, pi.get(a));
            }
        }

        return graph;
    }

    /**
     * Returns the parents of the node at index p, calculated using Pearl's method.
     *
     * @param pi                 The list of nodes.
     * @param p                  The index.
     * @param g                  The graph.
     * @param verbose            Whether to print verbose output.
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return The parents, as a Pair object (parents + score).
     */
    public static Set<Node> getParents(List<Node> pi, int p, Graph g, boolean verbose, boolean allowSelectionBias) {
        Node x = pi.get(p);
        Set<Node> parents = new HashSet<>();
        Set<Node> prefix = getPrefix(pi, p);

        for (Node y : prefix) {
            Set<Node> minus = new HashSet<>(prefix);
            minus.remove(y);
            minus.remove(x);
            Set<Node> z = new HashSet<>(minus);

            if (!g.paths().isMSeparatedFrom(x, y, z, allowSelectionBias)) {
                if (verbose) {
                    System.out.println("Adding " + y + " as a parent of " + x + " with z = " + z);
                }
                parents.add(y);
            }
        }

        return parents;
    }

    /**
     * Returns a valid causal order for either a DAG or a CPDAG. (bryanandrews)
     *
     * @param initialOrder Variables in the order will be kept as close to this initial order as possible, either the
     *                     forward order or the reverse order, depending on the next parameter.
     * @param forward      Whether the variables will be iterated over in forward or reverse direction.
     * @return The valid causal order found.
     */
    public List<Node> getValidOrder(List<Node> initialOrder, boolean forward) {
        List<Node> _initialOrder = new ArrayList<>(initialOrder);
        Graph _graph = new EdgeListGraph(this.graph);

        if (forward) Collections.reverse(_initialOrder);
        List<Node> newOrder = new ArrayList<>();

        while (!_initialOrder.isEmpty()) {
            Iterator<Node> itr = _initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else throw new IllegalArgumentException("This graph has a cycle.");
            } while (invalidSink(x, _graph));
            newOrder.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(newOrder);

        return newOrder;
    }

    /**
     * Generates a valid topological ordering of nodes in a directed graph without cycles. If the provided graph is
     * cyclic, an IllegalArgumentException is thrown.
     *
     * @param initialOrder the initial list of nodes representing a possible order to process
     * @param forward      a boolean indicating the direction of the list processing; if true, the initial order is
     *                     reversed
     * @return a valid list of nodes representing the order in which the directed graph can be traversed without
     * breaking dependency constraints
     */
    public List<Node> getValidOrderMag(List<Node> initialOrder, boolean forward) {
        List<Node> _initialOrder = new ArrayList<>(initialOrder);
        Graph _graph = new EdgeListGraph(this.graph);

        if (forward) Collections.reverse(_initialOrder);
        List<Node> newOrder = new ArrayList<>();

        while (!_initialOrder.isEmpty()) {
            Iterator<Node> itr = _initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else throw new IllegalArgumentException("This graph has a cycle.");
            } while (invalidSinkMag(x, _graph));
            newOrder.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(newOrder);

        return newOrder;
    }

    /**
     * Reorders the given order into a valid causal order for either a DAG or a CPDAG. (bryanandrews)
     *
     * @param order Variables in the order will be kept as close to this initial order as possible, either the forward
     *              order or the reverse order, depending on the next parameter.
     */
    public void makeValidOrder(List<Node> order) {
        List<Node> initialOrder = new ArrayList<>(order);
        Graph _graph = new EdgeListGraph(this.graph);

        Collections.reverse(initialOrder);
        order.clear();

        while (!initialOrder.isEmpty()) {
            Iterator<Node> itr = initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else
                    throw new IllegalArgumentException("The remaining graph does not have valid sink; there " + "could be a directed cycle or a non-chordal undirected cycle.");
            } while (invalidSink(x, _graph));
            order.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(order);
    }

    /**
     * The variable x is a valid sink if it has no children and its neighbors x--z form a clique; otherwise it is an
     * invalid sink.
     * <p>
     * Assume a DAG or CPDAG.
     *
     * @param x     The node to test.
     * @param graph The graph to test.
     * @return true if invalid, false if valid.
     */
    private boolean invalidSink(Node x, Graph graph) {
        LinkedList<Node> neighbors = new LinkedList<>();

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return true;
            if (edge.getEndpoint(x) == Endpoint.TAIL) neighbors.add(edge.getDistalNode(x));
        }

        while (!neighbors.isEmpty()) {
            Node y = neighbors.pop();
            for (Node z : neighbors) if (!graph.isAdjacentTo(y, z)) return true;
        }

        return false;
    }

    private boolean invalidSinkMag(Node x, Graph graph) {
//        LinkedList<Node> neighbors = new LinkedList<>();

        for (Edge edge : graph.getEdges(x)) {
//            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return true;
            if (edge.getEndpoint(x) == Endpoint.TAIL) {
                return true;
//                neighbors.add(edge.getDistalNode(x));
            }
        }

//        while (!neighbors.isEmpty()) {
//            Node y = neighbors.pop();
//            for (Node z : neighbors) if (!graph.isAdjacentTo(y, z)) return true;
//        }

        return false;
    }

    /**
     * Checks if the graph passed as parameter is a legal directed acyclic graph (DAG).
     *
     * @return true if the graph is a legal DAG, false otherwise.
     */
    public boolean isLegalDag() {
        return GraphUtils.isDag(graph);
    }

    /**
     * Checks if the current graph is a legal CPDAG (completed partially directed acyclic graph).
     *
     * @return true if the graph is a legal CPDAG, false otherwise.
     */
    public synchronized boolean isLegalCpdag() {

        // jdramsey 2024-2-17
        //
        // This is this idea I had for checking whether a graph is a CPDAG. What do you think?
        //
        // I'm using Bryan's method, validOrder, which repeatedly looks for a valid sink in the graph (no children,
        // neighbors forming a clique) and removes it, then reports the removed nodes in reverse order.
        //
        // Bryan gave this example: G = âX1-->X2,X2---X3,X3<--X4â. validOrder gets Ï = [X1, X2, X4, X3]. (This is
        // a malformed âvalid order,â but itâs what the algorithm says.) This makes sense because according to
        // the valid order method, X3 is a valid sink (only X2 is a neighbor, so the neighbors form a clique),
        // and the rest follows. So now youâre in a situation where youâre using d-separation on G, but itâs not
        // even a CPDAG. This is interesting to think about, just as an exercise in using the d-separation algorithm
        // on a malformed graph. What happens is you end up with an extra adjacency in the induced DAG because
        // there is no collider on the path X2--X3<-X4, and you only get to condition on the prefix of X2, which
        // is {X1} by the RU method, so there is no way to remove the adjacency X2--X4. So, G canât be a CPDAG.
        // But we need a test of CPDAG that can handle graphs like G; I had to think about it.
        //
        // So let validOrder be this method. Let DAG(Ï, dsep) be the Raskutti-Uhler method of forming a DAG given
        // permutation Ï and a d-separation relation dsep, taking G as the âtrue graphâ (possibly malformed) yielding
        // a (possibly malformed) oracle of d-separation facts. Let CPDAG(Gâ) for DAG Gâ be the MEC graph for Gâ.
        //
        // I propose that if the validOrder method throws an exception because it can't find a valid sink, then G is
        // not a CPDAG, since all CPDAGs have valid sinks, and any CPDAG with valid sinks removed in series also has
        // a valid sink. If validOrder returns an order, then I look to see whether G = CPDAG(DAG(validOrder(G)))
        // and return that judgment.
        //
        // I think I can prove that this works.
        //
        // Theorem 1: If validOrder(G) throws an exception, then G is not a CPDAG.
        //
        // Proof. We know the contrapositive. That is, we know that if G is a CPDAG, a valid order exists, so
        // validOrder in that case will not throw an exception.
        //
        // Theorem 2: For permutation Ï, DAG(Ï, dsep) always returns a DAG in cases where a valid order for G exists,
        // no matter the d-separation relation dsep (even based on possibly malformed information).
        //
        // Proof. DAG(Ï, dsep) always choos  es parents for a variable x from prefix(x, Ï), so the method will always
        // return a DAG.
        //
        // Theorem 3: When validOrder(G) returns an order Ï, G is a CPDAG if and only if G = CPDAG(DAG(Ï, dsep(G))),
        // where dsep(G) is the usual d-separation algorithm applied to (possibly malformed) G.
        //
        // Proof. Let Ï = validOrder(G). Note that if G is, in fact, a CPDAG, it follows from the construction of the
        // validOrder method and the Raskutti-Uhler method for building DAGs that G = CPDAG(DAG(Ï, dsep(G))). So,
        // let G not be a CPDAG and assume G = CPDAG(DAG(Ï, dsep(G))). But then G is, in fact, a CPDAG by construction
        // since DAG(Ï, dsep(G)) is a DAG (Theorem 2), which is a contradiction. It follows that
        // G != CPDAG(DAG(Ï, dsepG))), which proves the theorem.
        //
        // In any case, I can't get this method to fail, and it's easy to implement, given the other stuff we already
        // have implemented in Tetrad.

        Graph g = this.graph;

        for (Edge e : g.getEdges()) {
            if (!(Edges.isDirectedEdge(e) || Edges.isUndirectedEdge(e))) {
                return false;
            }
        }

        List<Node> pi = new ArrayList<>(g.getNodes());

        try {
            g.paths().makeValidOrder(pi);
            Graph cpdag = GraphTransforms.dagToCpdag(getDag(pi, g, false));
            return g.equals(cpdag);
        } catch (Exception e) {
            // There was no valid sink.
            TetradLogger.getInstance().log(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given graph is a legal Maximal Partial Directed Acyclic Graph (MPDAG). A MPDAG is considered legal
     * if it is equal to a CPDAG where additional edges have been oriented by Knowledge, with Meek rules applied for
     * maximum orientation. The test is performed by attemping to convert the graph to a CPDAG using the DAG to CPDAG
     * transformation and testing whether that graph is a legal CPDAG. Finally, we test to see whether the obtained
     * graph is equal to the original graph.
     *
     * @return true if the MPDAG is legal, false otherwise.
     */
    public boolean isLegalMpdag() {
        Graph g = this.graph;

        for (Edge e : g.getEdges()) {
            if (!(Edges.isDirectedEdge(e) || Edges.isUndirectedEdge(e))) {
                return false;
            }
        }

        List<Node> pi = new ArrayList<>(g.getNodes());

        try {
            g.paths().makeValidOrder(pi);
            Graph cpdag = GraphTransforms.dagToCpdag(getDag(pi, g, false));
            return cpdag.equals(GraphTransforms.dagToCpdag(g));
        } catch (Exception e) {
            // There was no valid sink.
            TetradLogger.getInstance().log(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given Maximal Ancestral Graph (MAG) is legal. A MAG is considered legal if it is equal to a PAG
     * where additional edges have been oriented by Knowledge, with final FCI rules applied for maximum orientation. The
     * test is performed by attemping to convert the graph to a PAG using the DAG to CPDAG transformation and testing
     * whether that graph is a legal PAG. Finally, we test to see whether the obtained graph is equal to the original
     * graph.
     * <p>
     * The user may choose to use the rules from Zhang (2008) or the rules from Spirtes et al. (2000).
     *
     * @return true if the MPDAG is legal, false otherwise.
     */
    public boolean isLegalMpag() {
        Graph g = this.graph;

        try {
            Graph pag = PagCache.getInstance().getPag(graph);

            if (pag.paths().isLegalPag()) {
                Graph _g = new EdgeListGraph(g);
                FciOrient fciOrient = new FciOrient(R0R4StrategyTestBased.defaultConfiguration(pag, new Knowledge()));
                fciOrient.finalOrientation(pag);
                return g.equals(_g);
            }

            return false;
        } catch (Exception e) {
            // There was no valid sink.
            System.out.println(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given graph is a legal mag.
     *
     * @return true if the graph is a legal mag, false otherwise
     */
    public boolean isLegalMag() {
        List<Node> selection = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        return PagLegalityCheck.isLegalMag(graph, new HashSet<>(selection)).isLegalMag();
    }

    /**
     * Checks if the given Directed Acyclic Graph (DAG) is a Legal Partial Ancestral Graph (PAG).
     *
     * @return true if the graph is a Legal PAG, false otherwise
     */
    public boolean isLegalPag() {
        List<Node> selection = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.SELECTION).toList();
        return PagLegalityCheck.isLegalPag(graph, new HashSet<>(selection)).isLegalPag();
    }

    /**
     * Returns a set of all maximum cliques in the graph.
     *
     * @return a set of sets of nodes representing the maximum cliques in the graph
     */
    public Set<Set<Node>> maxCliques() {
        int[][] graph = new int[this.graph.getNumNodes()][this.graph.getNumNodes()];
        List<Node> nodes = this.graph.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (this.graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    graph[i][j] = 1;
                    graph[j][i] = 1;
                }
            }
        }

        List<List<Integer>> _cliques = AllCliquesAlgorithm.findCliques(graph, graph.length);

        Set<Set<Node>> cliques = new HashSet<>();
        for (List<Integer> _clique : _cliques) {
            if (_clique.size() < 2) continue;
            Set<Node> clique = new HashSet<>();
            for (Integer i : _clique) {
                clique.add(nodes.get(i));
            }
            cliques.add(clique);
        }

        Set<Set<Node>> copy = new HashSet<>(cliques);
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Set<Node> clique : new HashSet<>(copy)) {
                for (Set<Node> other : new HashSet<>(copy)) {
                    if (clique == other) continue;
                    if (clique.containsAll(other)) {
                        copy.remove(other);
                        changed = true;
                    }
                }
            }
        }

        return copy;
    }

    /**
     * Returns a list of connected components in the graph.
     *
     * @return A list of connected components, where each component is represented as a list of nodes.
     */
    public List<List<Node>> connectedComponents() {
        List<List<Node>> components = new LinkedList<>();
        LinkedList<Node> unsortedNodes = new LinkedList<>(graph.getNodes());

        while (!unsortedNodes.isEmpty()) {
            Node seed = unsortedNodes.removeFirst();
            Set<Node> component = new ConcurrentSkipListSet<>();
            collectComponentVisit(seed, component, unsortedNodes);
            components.add(new ArrayList<>(component));
        }

        return components;
    }


    /**
     * Finds all directed paths from node1 to node2 with a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the destination node
     * @param maxLength the maximum length of the paths
     * @return a list of lists containing the directed paths from node1 to node2
     */
    public List<List<Node>> directedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        directedPaths(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void directedPaths(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        path.addLast(node1);

        if (node1 == node2) {
            LinkedList<Node> _path = new LinkedList<>(path);
            if (_path.size() > 1 && !paths.contains(_path)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            if (edge == null) continue;

            if (!edge.isDirected()) {
                continue;
            }

            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child != node2 && path.contains(child)) {
                continue;
            }

            directedPaths(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all semi-directed paths between two nodes up to a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the ending node
     * @param maxLength the maximum path length
     * @return a list of all semi-directed paths between the two nodes
     */
    public List<List<Node>> semidirectedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        semidirectedPathsVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    /**
     * Finds amenable paths from the given source node to the given destination node with a maximum length.
     *
     * @param node1     the source node
     * @param node2     the destination node
     * @param maxLength the maximum length of the paths
     * @return a list of amenable paths from the source node to the destination node, each represented as a list of
     * nodes
     */
    public List<List<Node>> amenablePathsMpdagMag(Node node1, Node node2, int maxLength) {
        List<List<Node>> amenablePaths = semidirectedPaths(node1, node2, maxLength);

        for (List<Node> path : new ArrayList<>(amenablePaths)) {
            Node a = path.getFirst();
            Node b = path.get(1);

            if (!graph.getEdge(a, b).pointsTowards(b)) {
                amenablePaths.remove(path);
            }
        }

        return amenablePaths;
    }


    /**
     * Finds amenable paths from the given source node to the given destination node with a maximum length, for a PAG.
     * These are semidirected paths that start with a visible edge out of node1.
     *
     * @param node1     the source node
     * @param node2     the destination node
     * @param maxLength the maximum length of the paths
     * @return a list of amenable paths from the source node to the destination node, each represented as a list of
     * nodes
     */
    public List<List<Node>> amenablePathsPag(Node node1, Node node2, int maxLength) {
        List<List<Node>> amenablePaths = semidirectedPaths(node1, node2, maxLength);

        for (List<Node> path : new ArrayList<>(amenablePaths)) {
            Node a = path.getFirst();
            Node b = path.get(1);

            boolean visible = graph.paths().defVisiblePag(a, b);

            if (!(visible && graph.getEdge(a, b).pointsTowards(b))) {
                amenablePaths.remove(path);
            }
        }

        return amenablePaths;
    }

    private void semidirectedPathsVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        path.addLast(node1);

        Set<Node> __path = new HashSet<>(path);
        if (__path.size() < path.size()) {
            return;
        }

        if (path.size() > 1 && node1 == node2) {
            LinkedList<Node> _path = new LinkedList<>(path);
            if (!paths.contains(path)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child != node2 && path.contains(child)) {
                continue;
            }

            semidirectedPathsVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all paths from node1 to node2 within a specified maximum length.
     *
     * @param node1         The starting node.
     * @param node2         The target node.
     * @param maxPathLength The maximum length of the paths.
     * @return A list of paths, where each path is a list of nodes.
     */
    public Set<List<Node>> allPaths(Node node1, Node node2, int maxPathLength) {
        Set<List<Node>> paths = new HashSet<>();
        allPathsVisit(node1, node2, new HashSet<>(), new LinkedList<>(), paths, -1, maxPathLength, new HashSet<>(), null, false);
        return paths;
    }

    /**
     * Finds all paths between two nodes within a given maximum length, considering optional condition set and selection
     * bias.
     *
     * @param node1              the starting node
     * @param node2              the target node
     * @param maxLength          the maximum length of each path
     * @param conditionSet       a set of nodes that need to be included in the path (optional)
     * @param allowSelectionBias if true, undirected edges are interpreted as selection bias; otherwise, as directed
     *                           edges in one direction or the other.
     * @return a set of paths between node1 and node2 that satisfy the conditions
     */
    public Set<List<Node>> allPaths(Node node1, Node node2, int maxLength, Set<Node> conditionSet, boolean allowSelectionBias) {
        Set<List<Node>> paths = new HashSet<>();
        allPathsVisit(node1, node2, new HashSet<>(), new LinkedList<>(), paths, -1, maxLength, conditionSet, null, allowSelectionBias);
        return paths;
    }

    /**
     * Finds all paths between two nodes satisfying certain conditions.
     *
     * @param node1              the starting node
     * @param node2              the ending node
     * @param minLength          the minimum length of paths to consider
     * @param maxLength          the maximum length of paths to consider
     * @param conditionSet       a set of nodes that must be present in the paths
     * @param ancestors          a map representing the ancestry relationships of nodes
     * @param allowSelectionBias true if selection bias is allowed, false otherwise
     * @return a set of lists representing all paths between node1 and node2
     */
    public Set<List<Node>> allPaths(Node node1, Node node2, int minLength, int maxLength, Set<Node> conditionSet, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
        Set<List<Node>> paths = new HashSet<>();
        allPathsVisit(node1, node2, new HashSet<>(), new LinkedList<>(), paths, minLength, maxLength, conditionSet, ancestors, allowSelectionBias);
        return paths;
    }

    /**
     * Generates all paths out of a given node within a specified maximum length and conditional set.
     *
     * @param node1              The starting node.
     * @param maxLength          The maximum length of each path.
     * @param conditionSet       The set of nodes that must be present in each path.
     * @param allowSelectionBias Determines whether to allow selection bias when choosing the next node to visit.
     * @return A set containing all generated paths as lists of nodes.
     */
    public Set<List<Node>> allPathsOutOf(Node node1, int maxLength, Set<Node> conditionSet, boolean allowSelectionBias) {
        Set<List<Node>> paths = new HashSet<>();
        allPathsVisitOutOf(null, node1, new HashSet<>(), new LinkedList<>(), paths, maxLength, conditionSet, allowSelectionBias);
        return paths;
    }

    private void allPathsVisit(Node node1, Node node2, Set<Node> pathSet, LinkedList<Node> path, Set<List<Node>> paths, int minLength, int maxLength, Set<Node> conditionSet, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
        if (minLength != -1 && path.size() - 1 < minLength) {
            return;
        }

        if (maxLength != -1 && path.size() - 1 > maxLength) {
            return;
        }

        if (pathSet.contains(node1)) {
            return;
        }

        path.addLast(node1);
        pathSet.add(node1);

        if (node1 == node2) {
            if (conditionSet != null) {
                LinkedList<Node> _path = new LinkedList<>(path);

                if (path.size() > 1) {
                    if (ancestors != null) {
                        if (isMConnectingPath(path, conditionSet, ancestors, allowSelectionBias)) {
                            paths.add(_path);
                        }
                    } else {
                        if (isMConnectingPath(path, conditionSet, allowSelectionBias)) {
                            paths.add(_path);
                        }
                    }
                }
            } else {
                paths.add(new LinkedList<Node>(path));
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (pathSet.contains(child)) {
                continue;
            }

            allPathsVisit(child, node2, pathSet, path, paths, minLength, maxLength, conditionSet, ancestors, allowSelectionBias);
        }

        path.removeLast();
        pathSet.remove(node1);
    }

    private void allPathsVisitOutOf(Node previous, Node node1, Set<Node> pathSet, LinkedList<Node> path, Set<List<Node>> paths, int maxLength, Set<Node> conditionSet, boolean allowSelectionBias) {
        if (maxLength != -1 && path.size() - 1 > maxLength) {
            return;
        }

        if (pathSet.contains(node1)) {
            return;
        }

        path.addLast(node1);
        pathSet.add(node1);

        LinkedList<Node> _path = new LinkedList<>(path);
        int maxPaths = 500;

        if (path.size() - 1 > 1) {
            if (paths.size() < maxPaths && isMConnectingPath(path, conditionSet, allowSelectionBias)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (pathSet.contains(child)) {
                continue;
            }

//            if (previous != null) {
//                Edge _previous = graph.getEdge(previous, node1);
//
//                if (!reachable(_previous, edge, edge.getDistalNode(node1), conditionSet)) {
//                    continue;
//                }
//            }

            if (paths.size() < maxPaths) {
                allPathsVisitOutOf(node1, child, pathSet, path, paths, maxLength, conditionSet, allowSelectionBias);
            }
        }

        path.removeLast();
        pathSet.remove(node1);
    }

    /**
     * Finds all directed paths from node1 to node2 with a maximum length.
     *
     * @param node1     The starting node.
     * @param node2     The target node.
     * @param maxLength The maximum length of the paths.
     * @return A list of lists of nodes representing the directed paths from node1 to node2.
     */
    public List<List<Node>> allDirectedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        allDirectedPathsVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void allDirectedPathsVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        path.addLast(node1);

        Set<Node> __path = new HashSet<>(path);
        if (__path.size() < path.size()) {
            return;
        }

        if (path.size() > 1 && node1 == node2) {
            LinkedList<Node> _path = new LinkedList<>(path);
            if (!paths.contains(path)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            allDirectedPathsVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all treks from node1 to node2 with a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the destination node
     * @param maxLength the maximum length of the treks
     * @return a list of lists of nodes representing each trek from node1 to node2
     */
    public List<List<Node>> treks(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        treks(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void treks(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 1) {
            return;
        }

        path.addLast(node1);

        Set<Node> __path = new HashSet<>(path);
        if (__path.size() < path.size()) {
            return;
        }

        if (path.size() > 1 && node1 == node2) {
            LinkedList<Node> _path = new LinkedList<>(path);
            if (!paths.contains(path)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge.
            if (!edge.isDirected()) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

//            // Found a path.
//            if (next == node2 && !path.isEmpty()) {
//                LinkedList<Node> _path = new LinkedList<>(path);
//                _path.add(next);
//                paths.add(_path);
//                continue;
//            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treks(next, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all possible treks between two nodes, including bidirectional treks.
     *
     * @param node1 The starting node.
     * @param node2 The ending node.
     * @return A List of Lists representing all treks between the given nodes.
     */
    public List<List<Node>> treksIncludingBidirected(Node node1, Node node2) {
        List<List<Node>> paths = new LinkedList<>();
        treksIncludingBidirected(node1, node2, new LinkedList<>(), paths);
        return paths;
    }

    private void treksIncludingBidirected(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths) {
        if (!(graph instanceof SemGraph _graph)) {
            throw new IllegalArgumentException("Expecting a SEM graph");
        }

        if (!_graph.isShowErrorTerms()) {
            throw new IllegalArgumentException("The SEM Graph must be showing its error terms; this method " + "doesn't traverse two edges between the same nodes well.");
        }

        if (path.contains(node1)) {
            return;
        }

        if (node1 == node2) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge or a bidirected edge.
            if (!(edge.isDirected() || Edges.isBidirectedEdge(edge))) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            // Found a path.
            if (next == node2 && !path.isEmpty()) {
                LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(next);
                if (!paths.contains(path)) {
                    paths.add(_path);
                }
                continue;
            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treksIncludingBidirected(next, node2, path, paths);
        }

        path.removeLast();
    }

    /**
     * Returns the Markov Blanket of a given node in the graph.
     *
     * @param node the node for which the Markov Blanket needs to be computed
     * @return a set of nodes that constitute the Markov Blanket of the given node
     */
    public Set<Node> markovBlanket(Node node) {
        return GraphUtils.markovBlanket(node, graph);
    }

    /**
     * Retrieves the set of nodes that belong to the same district as the given node.
     *
     * @param node the node from which to start the district search
     * @return the set of nodes that belong to the same district as the given node
     */
    public Set<Node> district(Node node) {
        return GraphUtils.district(node, graph);
    }

    /**
     * Checks if a directed path exists between two nodes within a certain depth.
     *
     * @param node1 the first node in the path
     * @param node2 the second node in the path
     * @param depth the maximum depth to search for the path
     * @return true if a directed path exists between the two nodes within the given depth, false otherwise
     */
    public boolean existsDirectedPath(Node node1, Node node2, int depth) {
        return node1 == node2 || existsDirectedPathVisit(node1, node2, new LinkedList<>(), depth);
    }

    private boolean existsDirectedPathVisit(Node node1, Node node2, LinkedList<Node> path, int depth) {
        path.addLast(node1);

        if (depth == -1) depth = Integer.MAX_VALUE;

        if (path.size() >= depth) {
            return false;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (graph.getEdges(node1, child).size() == 2) {
                return true;
            }

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsDirectedPathVisit(child, node2, path, depth)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }


    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.

    /**
     * <p>existsSemiDirectedPath.</p>
     *
     * @param from a {@link edu.cmu.tetrad.graph.Node} object
     * @param to   a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsSemiDirectedPath(Node from, Node to) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        for (Node u : graph.getAdjacentNodes(from)) {
            Edge edge = graph.getEdge(from, u);
            Node c = GraphUtils.traverseSemiDirected(from, edge);

            if (c == null) {
                continue;
            }

            if (!V.contains(c)) {
                V.add(c);
                Q.offer(c);
            }
        }

        while (!Q.isEmpty()) {
            Node t = Q.remove();

            if (t == to) {
                return true;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = GraphUtils.traverseSemiDirected(t, edge);

                if (c == null) {
                    continue;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    /**
     * Retrieves the set of nodes that are connected to the given node {@code y} and are also present in the set of
     * nodes {@code z}.
     *
     * @param y The node for which to find the connected nodes.
     * @param z The set of nodes to be considered for connecting nodes.
     * @return The set of nodes that are connected to {@code y} and present in {@code z}.
     */
    public Set<Node> getMConnectedVars(Node y, Set<Node> z) {
        Set<Node> Y = new HashSet<>();

        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge == this.edge && _o.node == this.node;
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        for (Edge edge : graph.getEdges(y)) {
            EdgeNode edgeNode = new EdgeNode(edge, y);
            Q.offer(edgeNode);
            V.add(edgeNode);
            Y.add(edge.getDistalNode(y));
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z)) {
                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                        Y.add(c);
                    }
                }
            }
        }

        return Y;
    }

    /**
     * <p>getMConnectedVars.</p>
     *
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param z         a {@link java.util.Set} object
     * @param ancestors a {@link java.util.Map} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getMConnectedVars(Node y, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        Set<Node> Y = new HashSet<>();

        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge == this.edge && _o.node == this.node;
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        for (Edge edge : graph.getEdges(y)) {
            EdgeNode edgeNode = new EdgeNode(edge, y);
            Q.offer(edgeNode);
            V.add(edgeNode);
            Y.add(edge.getDistalNode(y));
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z, ancestors)) {
                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                        Y.add(c);
                    }
                }
            }
        }

        return Y;
    }

    private boolean reachable(Edge e1, Edge e2, Node a, Set<Node> z) {
        Node b = e1.getDistalNode(a);
        Node c = e2.getDistalNode(b);

        boolean collider = e1.getEndpoint(b) == Endpoint.ARROW && e2.getEndpoint(b) == Endpoint.ARROW;

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestorOfAnyZ(b, z);
        return collider && ancestor;
    }

    // Return true if b is an ancestor of any node in z
    private boolean reachable(Edge e1, Edge e2, Node a, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        Node b = e1.getDistalNode(a);
        Node c = e2.getDistalNode(b);

        boolean collider = e1.getEndpoint(b) == Endpoint.ARROW && e2.getEndpoint(b) == Endpoint.ARROW;

        boolean ancestor = false;

        for (Node _z : ancestors.get(b)) {
            if (z.contains(_z)) {
                ancestor = true;
                break;
            }
        }

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        return collider && ancestor;
    }

    /**
     * Return a map from each node to its collection of descendants.
     *
     * @return This map.
     */
    public Map<Node, Set<Node>> getDescendantsMap() {
        Map<Node, Set<Node>> descendantsMap = new HashMap<>();
        List<Node> nodes = graph.getNodes();

        // Precompute children once (directed-only)
        Map<Node, List<Node>> children = new HashMap<>();
        for (Node n : nodes) {
            children.put(n, new ArrayList<>(graph.getChildren(n)));
        }

        for (Node s : nodes) {
            // Reflexive: include s itself as its own descendant (consistent with isAncestorOfAnyZ)
            Set<Node> desc = new HashSet<>();
            desc.add(s);

            ArrayDeque<Node> q = new ArrayDeque<>(children.get(s));
            desc.addAll(children.get(s));

            while (!q.isEmpty()) {
                Node u = q.poll();
                for (Node c : children.get(u)) {
                    if (desc.add(c)) q.add(c);
                }
            }

            descendantsMap.put(s, desc);
        }

        return descendantsMap;
    }

    /**
     * Return a map from each node to its collection of ancestors.
     *
     * @return This map.
     */
    public Map<Node, Set<Node>> getAncestorsMap() {
        Map<Node, Set<Node>> ancestorsMap = new HashMap<>();
        List<Node> nodes = graph.getNodes();

        // Precompute parent lists once (directed-only)
        Map<Node, List<Node>> parents = new HashMap<>();
        for (Node n : nodes) {
            parents.put(n, new ArrayList<>(graph.getParents(n)));
        }

        for (Node t : nodes) {
            // Reflexive: include t itself as its own ancestor (matches isAncestorOfAnyZ)
            Set<Node> anc = new HashSet<>();
            anc.add(t);

            ArrayDeque<Node> q = new ArrayDeque<>(parents.get(t));
            anc.addAll(parents.get(t));

            while (!q.isEmpty()) {
                Node u = q.poll();
                for (Node p : parents.get(u)) {
                    if (anc.add(p)) q.add(p);
                }
            }

            ancestorsMap.put(t, anc);
        }

        return ancestorsMap;
    }

    /**
     * Return true if b is an ancestor of any node in z
     *
     * @param b a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return true if b is an ancestor of any node in z
     */
    public boolean isAncestorOfAnyZ(Node b, Set<Node> z) {
        if (z == null || z.isEmpty()) return false;

        // Reflexive ancestry: if any z == b we consider it "ancestor of itself"
        for (Node zi : z) {
            if (b.equals(zi)) return true;
        }

        // Optional fast path — only use if your descendants map is known-good and directed-only + reflexive:
        // Map<Node, Set<Node>> desc = graph.paths().getDescendantsMap();
        // Set<Node> bDesc = desc.get(b);
        // if (bDesc != null) {
        //     for (Node zi : z) if (bDesc.contains(zi)) return true;
        // }

        // Fallback: explicit upward BFS from all z through directed parents
        ArrayDeque<Node> q = new ArrayDeque<>();
        HashSet<Node> seen = new HashSet<>();

        for (Node zi : z) {
            q.add(zi);
            seen.add(zi);
        }

        while (!q.isEmpty()) {
            Node t = q.poll();
            if (b.equals(t)) return true;

            for (Node p : graph.getParents(t)) {   // directed-only
                if (seen.add(p)) {
                    q.add(p);
                }
            }
        }

        return false;
    }

    /**
     * Determines whether an inducing path exists between node1 and node2, given a set O of observed nodes and a set sem
     * of conditioned nodes.
     *
     * @param x                  the first node.
     * @param y                  the second node.
     * @param selectionVariables the set of selection variables.
     * @return true if an inducing path exists, false if not.
     */
    public boolean existsInducingPathDFS(Node x, Node y, Set<Node> selectionVariables) {
        if (x.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }
        if (y.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(x, b, x, y, selectionVariables, path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether an inducing path exists between two nodes in a graph.
     *
     * @param a                  the first node in the graph
     * @param b                  the second node in the graph
     * @param x                  the first measured node in the graph
     * @param y                  the second measured node in the graph
     * @param selectionVariables the set of selection variables
     * @param path               the path to check
     * @return true if an inducing path exists, false if not
     */
    public boolean existsInducingPathVisit(Node a, Node b, Node x, Node y, Set<Node> selectionVariables, LinkedList<Node> path) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        if (b == y) {
            return true;
        }

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) {
                continue;
            }

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) {
                    continue;
                }

            }

            if (graph.isDefCollider(a, b, c)) {
                if (!(graph.paths().isAncestorOf(b, x) || graph.paths().isAncestorOf(b, y) || graph.paths().isAncestorOfAnyZ(b, selectionVariables))) {
                    continue;
                }
            }

            if (existsInducingPathVisit(b, c, x, y, selectionVariables, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * Breadth-first version of the âinducing-path exists?â test.
     *
     * @param x                  first measured node
     * @param y                  second measured node
     * @param selectionVariables set of selection variables (Z)
     * @return true iff there is an inducing path from x to y
     */
    public boolean existsInducingPathBFS(Node x, Node y, Set<Node> selectionVariables) {

        if (x.getNodeType() != NodeType.MEASURED || y.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException("x and y must be measured nodes");
        }

        // State = (prev, curr, path-so-far).
        record State(Node prev, Node curr, LinkedList<Node> path) {
        }

        Queue<State> queue = new LinkedList<>();

        // Seed the queue with every neighbour of x
        for (Node b : graph.getAdjacentNodes(x)) {
            LinkedList<Node> seedPath = new LinkedList<>();
            seedPath.add(x);
            seedPath.add(b);

            if (b == y) {                         // xâb where b==y  â  path of length-1
                return true;
            }
            queue.add(new State(x, b, seedPath));
        }

        // Standard BFS loop
        while (!queue.isEmpty()) {
            State s = queue.remove();
            Node a = s.prev();
            Node b = s.curr();
            LinkedList<Node> path = s.path();     // already contains a and b

            for (Node c : graph.getAdjacentNodes(b)) {

                if (c == a) continue;             // donât back-track
                if (path.contains(c)) continue;   // avoid cycles

                // --- Same admissibility checks as the DFS version -----------------
                if (b.getNodeType() == NodeType.MEASURED && !graph.isDefCollider(a, b, c)) {
                    continue;
                }

                if (graph.isDefCollider(a, b, c) && !(graph.paths().isAncestorOf(b, x) || graph.paths().isAncestorOf(b, y) || graph.paths().isAncestorOfAnyZ(b, selectionVariables))) {
                    continue;
                }
                // ------------------------------------------------------------------

                // Extend the path to c
                LinkedList<Node> newPath = new LinkedList<>(path);
                newPath.add(c);

                if (c == y) {                     // reached the target â success!
                    return true;
                }

                queue.add(new State(b, c, newPath));
            }
        }

        return false;                             // Exhausted queue â no inducing path
    }


    /**
     * Determines whether an inducing path exists between two nodes in a graph. This is a breadth-first implementation.
     *
     * @param x                  the first node in the graph
     * @param y                  the second node in the graph
     * @param selectionVariables the set of selection variables
     * @return true if an inducing path exists, false if not
     */
    public boolean existsInducingPath(Node x, Node y, Set<Node> selectionVariables) {

        // Note that whatever method is chosen for checking inducing paths, the method testZhangPagToMag in
        // TestFci must complete without errors for 1000 runs. (Methods that weren't doing that have been
        // deleted from the code.) jdramsey, 2025-6-1
        if (true) {
            return existsInducingPathBFS(x, y, selectionVariables);
        } else {
            return existsInducingPathDFS(x, y, selectionVariables);
        }
    }

    /**
     * This method calculates the inducing path between two measured nodes in a graph.
     *
     * @param x                  the first measured node in the graph
     * @param y                  the second measured node in the graph
     * @param selectionVariables the set of selection variables
     * @return the inducing path between node x and node y, or null if no inducing path exists
     * @throws IllegalArgumentException if either x or y is not of NodeType.MEASURED
     */
    public List<Node> getInducingPath(Node x, Node y, Set<Node> selectionVariables) {
        if (x.getNodeType() != NodeType.MEASURED && !selectionVariables.contains(x)) {
            throw new IllegalArgumentException();
        }
        if (y.getNodeType() != NodeType.MEASURED && !selectionVariables.contains(y)) {
            throw new IllegalArgumentException();
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(x, b, x, y, selectionVariables, path)) {
                return path;
            }
        }

        return null;
    }

    /**
     * Identifies and returns a list of possible d-separating nodes relative to a specified node in the graph. The
     * search is constrained by the specified maximum path length.
     *
     * @param x                         the target node for which possible d-separating nodes are to be identified
     * @param maxPossibleDsepPathLength the maximum allowable path length for determining d-separating nodes; a value of
     *                                  -1 indicates no explicit limit
     * @return a sorted list of nodes that are potential d-separators for the specified node, ordered in descending
     * order
     */
    public List<Node> possibleDsep(Node x, int maxPossibleDsepPathLength) {

        // Removing the second argument y to bring this in line with CPS 2001 p. 188.
        Set<Node> msep = new HashSet<>();

        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        Set<OrderedPair<Node>> V = new HashSet<>();

        Map<Node, Set<Node>> previous = new HashMap<>();
        previous.put(x, new HashSet<>());

        OrderedPair<Node> e = null;
        int distance = 0;

        Set<Node> adjacentNodes = new HashSet<>(graph.getAdjacentNodes(x));

        for (Node b : adjacentNodes) {
            OrderedPair<Node> edge = new OrderedPair<>(x, b);
            if (e == null) {
                e = edge;
            }
            Q.offer(edge);
            V.add(edge);
            addToSet(previous, b, x);
            msep.add(b);
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            if (e == t) {
                e = null;
                distance++;
                if (distance > 0 && distance > (maxPossibleDsepPathLength == -1 ? 1000 : maxPossibleDsepPathLength)) {
                    break;
                }
            }

            Node a = t.getFirst();
            Node b = t.getSecond();

            if (existOnePathWithPossibleParents(previous, b, x, b)) {
                msep.add(b);
            }

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) {
                    continue;
                }
                if (c == x) {
                    continue;
                }

                addToSet(previous, b, c);

                if (graph.isDefCollider(a, b, c) || graph.isAdjacentTo(a, c)) {

                    // Bug : This was <a, c> should have been <b, c> to continue the path.
                    OrderedPair<Node> u = new OrderedPair<>(b, c);
                    if (V.contains(u)) {
                        continue;
                    }

                    V.add(u);
                    Q.offer(u);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        msep.remove(x);

        List<Node> _msep = new ArrayList<>(msep);

        Collections.sort(_msep);
        Collections.reverse(_msep);

        return _msep;

    }

    /**
     * Remove edges by the possible m-separation rule.
     *
     * @param test    The independence test to use to remove edges.
     * @param sepsets A sepset map to which sepsets should be added. May be null, in which case sepsets will not be
     *                recorded.
     * @throws InterruptedException if any
     */
    public void removeByPossibleDsep(IndependenceTest test, SepsetMap sepsets) throws InterruptedException {
        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            {
                List<Node> possibleDsep = possibleDsep(a, -1);
                possibleDsep.remove(a);
                possibleDsep.remove(b);

                SublistGenerator gen = new SublistGenerator(possibleDsep.size(), possibleDsep.size());
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (choice.length < 2) continue;
                    Set<Node> sepset = GraphUtils.asSet(choice, possibleDsep);
                    if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                    if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                    if (test.checkIndependence(a, b, sepset).isIndependent()) {
                        graph.removeEdge(edge);

                        if (sepsets != null) {
                            sepsets.set(a, b, sepset);
                        }

                        break;
                    }
                }
            }

            if (graph.containsEdge(edge)) {
                {
                    List<Node> possibleDsep = possibleDsep(a, -1);
                    possibleDsep.remove(a);
                    possibleDsep.remove(b);

                    SublistGenerator gen = new SublistGenerator(possibleDsep.size(), possibleDsep.size());
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (choice.length < 2) continue;
                        Set<Node> sepset = GraphUtils.asSet(choice, possibleDsep);
                        if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                        if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                        if (test.checkIndependence(a, b, sepset).isIndependent()) {
                            graph.removeEdge(edge);

                            if (sepsets != null) {
                                sepsets.set(a, b, sepset);
                            }

                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean existOnePathWithPossibleParents(Map<Node, Set<Node>> previous, Node w, Node x, Node b) {
        if (w == x) {
            return true;
        }

        Set<Node> p = previous.get(w);
        if (p == null) {
            return false;
        }

        for (Node r : p) {
            if (r == b || r == x) {
                continue;
            }

            if ((existsSemiDirectedPath(r, x)) || existsSemiDirectedPath(r, b)) {
                return true;
            }
        }

        return false;
    }

//    /**
//     * Calculates the possible d-separation nodes between two given Nodes within a graph, using a maximum path length
//     * constraint.
//     *
//     * @param x                         the starting Node for the path
//     * @param y                         the ending Node for the path
//     * @param maxPossibleDsepPathLength the maximum length of the path, -1 for unlimited
//     * @return a List of Nodes representing the possible d-separation nodes
//     */
//    public List<Node> possibleDsep(Node x, Node y, int maxPossibleDsepPathLength) {
//        Set<Node> msep = new HashSet<>();
//
//        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
//        Set<OrderedPair<Node>> V = new HashSet<>();
//
//        Map<Node, Set<Node>> previous = new HashMap<>();
//        previous.put(x, new HashSet<>());
//
//        OrderedPair<Node> e = null;
//        int distance = 0;
//
//        Set<Node> adjacentNodes = new HashSet<>(graph.getAdjacentNodes(x));
//
//        for (Node b : adjacentNodes) {
//            if (y != null && b == y) {
//                continue;
//            }
//            OrderedPair<Node> edge = new OrderedPair<>(x, b);
//            if (e == null) {
//                e = edge;
//            }
//            Q.offer(edge);
//            V.add(edge);
//            addToSet(previous, b, x);
//            msep.add(b);
//        }
//
//        while (!Q.isEmpty()) {
//            OrderedPair<Node> t = Q.poll();
//
//            if (e == t) {
//                e = null;
//                distance++;
//                if (distance > 0 && distance > (maxPossibleDsepPathLength == -1 ? 1000 : maxPossibleDsepPathLength)) {
//                    break;
//                }
//            }
//
//            Node a = t.getFirst();
//            Node b = t.getSecond();
//
//            if (existOnePathWithPossibleParents(previous, b, x, b)) {
//                msep.add(b);
//            }
//
//            for (Node c : graph.getAdjacentNodes(b)) {
//                if (c == a) {
//                    continue;
//                }
//                if (c == x) {
//                    continue;
//                }
//                if (y !=null && c == y) {
//                    continue;
//                }
//
//                addToSet(previous, b, c);
//
//                if (graph.isDefCollider(a, b, c) || graph.isAdjacentTo(a, c)) {
//                    OrderedPair<Node> u = new OrderedPair<>(b, c);
//                    if (V.contains(u)) {
//                        continue;
//                    }
//
//                    V.add(u);
//                    Q.offer(u);
//
//                    System.out.println("Updated " + u + " = " + "Q = " + Q + " V = " + V +  " msep: " + msep);
//
//                    if (e == null) {
//                        e = u;
//                    }
//                }
//            }
//        }
//
//        msep.remove(x);
//        msep.remove(y);
//
//        List<Node> _msep = new ArrayList<>(msep);
//
//        Collections.sort(_msep);
//        Collections.reverse(_msep);
//
//        return _msep;
//    }

    /**
     * Returns D-SEP(x, y) for a maximal ancestral graph G (or inducing path graph G, as in Causation, Prediction and
     * Search).
     * <p>
     * We trust the user to make sure the given graph is a MAG or IPG; we don't check this.
     *
     * @param x The one endpoint.
     * @param y The other endpoint.
     * @return D-SEP(x, y) for MAG/IPG G.
     */
    public Set<Node> dsep(Node x, Node y) {
        return GraphUtils.dsep(x, y, graph);
    }

    /**
     * Check to see if a set of variables Z satisfies the back-door criterion relative to node x and node y. (author
     * Kevin V. Bui (March 2020).
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param z     a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean isSatisfyBackDoorCriterion(Graph graph, Node x, Node y, Set<Node> z) {
        Dag dag = new Dag(graph);

        // make sure no nodes in z is a descendant of x
        if (z.stream().anyMatch(zNode -> dag.paths().isDescendentOf(zNode, x))) {
            return false;
        }


        // make sure zNodes bock every path between node x and node y that contains an arrow into node x
        List<List<Node>> directedPaths = allDirectedPaths(x, y, -1);
        directedPaths.forEach(nodes -> {
            // remove all variables that are not on the back-door path
            nodes.forEach(node -> {
                if (!(node == x || node == y)) {
                    dag.removeNode(node);
                }
            });
        });

        return dag.paths().isMSeparatedFrom(x, y, z, false);
    }

    /**
     * Finds a sepset for x and y, if there is one; otherwise, returns null.
     *
     * @param x                  The first node.
     * @param y                  The second node.
     * @param allowSelectionBias Whether to allow selection bias.
     * @param test               The independence test to use.
     * @param depth              The maximum depth to search for a sepset.
     * @return A sepset for x and y, if there is one; otherwise, null.
     */
    public Set<Node> getSepset(Node x, Node y, boolean allowSelectionBias, IndependenceTest test, int depth) {
        return SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, x, y, Collections.emptySet(), test, depth);
    }

    /**
     * Retrieves a sepset (a set of nodes) between two given nodes.
     *
     * @param x             the first node
     * @param y             the second node
     * @param containing    the set of nodes that the sepset must contain
     * @param maxPathLength the maximum length of the path to search for the blocking set
     * @return the sepset between the two nodes
     * @throws InterruptedException if any.
     */
    public Set<Node> getSepsetContaining(Node x, Node y, Set<Node> containing, int maxPathLength) throws InterruptedException {
        Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(graph, x, y, containing, Set.of(), maxPathLength);

        // TODO - should allow the user to determine whether this is a PAG.
        if (isMSeparatedFrom(x, y, blocking, false)) {
            return blocking;
        }

        return null;
    }

    private boolean separates(Node x, Node y, boolean allowSelectionBias, Set<Node> combination) {
        if (graph.getNumEdges(x) < graph.getNumEdges(y)) {
            return !isMConnectedTo(x, y, combination, allowSelectionBias);
        } else {
            return !isMConnectedTo(y, x, combination, allowSelectionBias);
        }
    }

    /**
     * Determmines whether x and y are d-connected given z.
     *
     * @param x                  a {@link Node} object
     * @param y                  a {@link Node} object
     * @param z                  a {@link Set} object
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z, boolean allowSelectionBias) {
        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge.equals(this.edge) && _o.node.equals(this.node);
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        if (x == y) {
            return true;
        }

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalNode(x) == y) {
                return true;
            }
            EdgeNode edgeNode = new EdgeNode(edge, x);
            Q.offer(edgeNode);
            V.add(edgeNode);
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);

                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z)) {
                    if (c == y) {
                        return true;
                    }

                    // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
                    // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
                    // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
                    // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
                    // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
                    // than for PAGs, and we are trying to make an m-connection procedure that works for both.
                    // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
                    // A similar problem can occur in a PAG; we deal with that as well. The idea is to make
                    // "virtual edges" that are directed in the direction of the arrow, so that the reachability
                    // algorithm can eventually find any colliders along the path that may be implied.
                    // jdramsey 2024-04-14
                    if (!allowSelectionBias && edge1.getEndpoint(b) == Endpoint.ARROW) {
                        if (Edges.isUndirectedEdge(edge2)) {
                            edge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
                        } else if (Edges.isNondirectedEdge(edge2)) {
                            edge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                        }
                    }

                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if the given path is an m-connecting path.
     *
     * @param path            The path to check.
     * @param conditioningSet The set of nodes to check reachability against.
     * @param isPag           Determines if selection bias is allowed in the m-connection procedure.
     * @return {@code true} if the given path is an m-connecting path, {@code false} otherwise.
     */
    public boolean isMConnectingPath(List<Node> path, Set<Node> conditioningSet, boolean isPag) {
        Edge edge1, edge2;

        if (path.size() - 1 <= 1) return true;

        edge2 = graph.getEdge(path.getFirst(), path.get(1));

        for (int i = 0; i < path.size() - 2; i++) {
            edge1 = edge2;
            edge2 = graph.getEdge(path.get(i + 1), path.get(i + 2));
            Node b = path.get(i + 1);

            // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
            // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
            // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
            // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
            // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
            // than for PAGs, and we are trying to make an m-connection procedure that works for both.
            // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
            // A similar problem can occur in a PAG; we deal with that as well. The idea is to make
            // "virtual edges" that are directed in the direction of the arrow, so that the reachability
            // algorithm can eventually find any colliders along the path that may be implied.
            // jdramsey 2024-04-14
            if (edge1.getEndpoint(b) == Endpoint.ARROW) {
                if (!isPag && Edges.isUndirectedEdge(edge2)) {
                    edge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
                } else if (isPag && Edges.isNondirectedEdge(edge2)) {
                    edge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                }
            }

            if (!reachable(edge1, edge2, path.get(i), conditioningSet)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the given path is an m-connecting path and doens't contain duplicate nodes.
     *
     * @param path               The path to check.
     * @param conditioningSet    The set of nodes to check reachability against.
     * @param allowSelectionBias Determines if selection bias is allowed in the m-connection procedure.
     * @param ancestors          The ancestors of each node in the graph.
     * @return {@code true} if the given path is an m-connecting path, {@code false} otherwise.
     */
    public boolean isMConnectingPath(List<Node> path, Set<Node> conditioningSet, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
        Edge edge1, edge2;

        Set<Node> pathSet = new HashSet<>();

        for (int i = 0; i < path.size() - 1; i++) {
            Node node = path.get(i);

            if (pathSet.contains(node)) {
                return false;
            } else {
                pathSet.add(node);
            }
        }

        edge2 = graph.getEdge(path.getFirst(), path.get(1));

        for (int i = 0; i < path.size() - 2; i++) {
            edge1 = edge2;
            edge2 = graph.getEdge(path.get(i + 1), path.get(i + 2));
            Node b = path.get(i + 1);

            // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
            // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
            // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
            // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
            // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
            // than for PAGs, and we are trying to make an m-connection procedure that works for both.
            // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
            // A similar problem can occur in a PAG; we deal with that as well. The idea is to make
            // "virtual edges" that are directed in the direction of the arrow, so that the reachability
            // algorithm can eventually find any colliders along the path that may be implied.
            // jdramsey 2024-04-14
            if (edge1.getEndpoint(b) == Endpoint.ARROW) {
                if (!allowSelectionBias && Edges.isUndirectedEdge(edge2)) {
                    edge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
                } else if (allowSelectionBias && Edges.isNondirectedEdge(edge2)) {
                    edge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                }
            }

            if (!reachable(edge1, edge2, path.get(i), conditioningSet, ancestors)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Detemrmines whether x and y are d-connected given z.
     *
     * @param x         a {@link Node} object
     * @param y         a {@link Node} object
     * @param z         a {@link Set} object
     * @param ancestors a {@link Map} object
     * @param isPag     whether to allow selection bias; if true, then undirected edges X--Y are uniformly treated as
     *                  X-&gt;L&lt;-Y.
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z, Map<Node, Set<Node>> ancestors, boolean isPag) {
        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + 5 * this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge.equals(this.edge) && _o.node.equals(this.node);
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        if (x == y) {
            return true;
        }

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalNode(x) == y) {
                return true;
            }
            EdgeNode edgeNode = new EdgeNode(edge, x);
            Q.offer(edgeNode);
            V.add(edgeNode);
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z, ancestors)) {
                    if (c == y) {
                        return true;
                    }

                    // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
                    // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
                    // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
                    // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
                    // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
                    // than for PAGs, and we are trying to make an m-connection procedure that works for both.
                    // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
                    // A similar problem can occur in a PAG; we deal with that as well. The idea is to make
                    // "virtual edges" that are directed in the direction of the arrow, so that the reachability
                    // algorithm can eventually find any colliders along the path that may be implied.
                    // jdramsey 2024-04-14
                    if (isPag) {
                        if (edge1.getEndpoint(b) == Endpoint.ARROW) {
                            if (Edges.isNondirectedEdge(edge2)) {
                                edge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                            }
                        }
                    } else {
                        if (edge1.getEndpoint(b) == Endpoint.ARROW) {
                            if (Edges.isNondirectedEdge(edge2)) {
                                edge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                            }
                        }
                    }

                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Assumes node should be in component.
     */
    private void collectComponentVisit(Node node, Set<Node> component, List<Node> unsortedNodes) {
        if (TaskManager.getInstance().isCanceled()) {
            return;
        }

        component.add(node);
        unsortedNodes.remove(node);
        List<Node> adj = graph.getAdjacentNodes(node);

        for (Node anAdj : adj) {
            if (!component.contains(anAdj)) {
                collectComponentVisit(anAdj, component, unsortedNodes);
            }
        }
    }

    /*======================================================================
     * 1.  PUBLIC ENTRY POINT
     *====================================================================*/

    /**
     * Returns true if the edge form A to B is a definitely visible edge in a PAG.
     *
     * @param A The one node.
     * @param B The other node.
     * @return True if so.
     */
    public boolean defVisiblePag(Node A, Node B) {

        // Sanity: we only care about directed A â B edges that exist
        if (!graph.isParentOf(A, B)) return false;

        for (Node C : graph.getNodes()) {

            if (C == A || C == B) continue;           // trivial exclusions
            if (graph.isAdjacentTo(C, B)) continue;   // C must NOT touch B

            /* ---------- Clause 1: an edge into A from C ---------------- */
            if (graph.getEndpoint(C, A) == Endpoint.ARROW) {
                // Covers both  C â A  and  C â A  (arrowhead at A).
                return true;
            }

            /* ---------- Clause 2: collider path C â¦ A ------------------ */
            if (existsColliderPathInto(C, A, B)) {
                return true;
            }
        }
        return false;   // no qualifying C found â edge is invisible
    }

    /*======================================================================
     * 2.  COLLIDER-PATH SEARCH  (Zhang 2008, Def. 8, second bullet)
     *====================================================================*/

    /**
     * True iff there exists a collider path   C = v0 â¦ vk = A (k â¥ 1) that is arrow-headed into A and whose
     * **interior** vertices are all parents of B.  Endpoints C and A themselves are *not* required to point to B.
     */
    private boolean existsColliderPathInto(Node C, Node A, Node B) {
        return dfsColliderPath(null, C, A, B, new HashSet<>());
    }

    /* ------------------------------------------------------------------ */

    private boolean dfsColliderPath(Node prev,           // vertex before âcurâ
                                    Node cur,            // current vertex
                                    Node targetA,        // destination
                                    Node B,              // must receive arrows
                                    Set<Node> onBranch)  // cycle guard
    {
        /* (i) Interior-vertex parent-of-B condition */
        if (prev != null && !cur.equals(targetA)          // interior only
            && !graph.isParentOf(cur, B)) {
            return false;                                 // violates clause
        }

        /* (ii) Endpoint reached: arrowhead into A required */
        if (cur.equals(targetA)) {
            return prev != null && graph.getEndpoint(prev, targetA) == Endpoint.ARROW;
        }

        /* (iii) DFS expansion with collider check */
        onBranch.add(cur);
        for (Node nxt : graph.getAdjacentNodes(cur)) {
            if (onBranch.contains(nxt)) continue;         // avoid cycles

            // collider requirement applies once we *have* a predecessor
            if (prev == null || graph.isDefCollider(prev, cur, nxt)) {
                if (dfsColliderPath(cur, nxt, targetA, B, onBranch)) return true;
            }
        }
        onBranch.remove(cur);
        return false;
    }

//    /**
//     * Returns true just in case the given edge is definitely visible. The reference for this is Zhang, J. (2008).
//     * Causal Reasoning with Ancestral Graphs. Journal of Machine Learning Research, 9(7).
//     * <p>
//     * This definition will work for MAGs and PAGs. "Definite" here means for PAGs that the edge is visible in all MAGs
//     * in the equivalence class.
//     *
//     * @param edge the edge to check.
//     * @return true if the given edge is definitely visible.
//     * @throws java.lang.IllegalArgumentException if the given edge is not a directed edge in the graph
//     */
//    public boolean defVisible(Edge edge) {
//
//        // Zhang, J. (2008). Causal Reasoning with Ancestral Graphs. Journal of Machine Learning
//        // Research, 9(7)
//        //
//        // Definition 8 (Visibility) Given a MAG M, a directed edge A â B in M is visible
//        // if there is a vertex C not adjacent to B, such that either there is an edge between
//        // C and A that is into A, or there is a collider path between C and A that is into A
//        // and every vertex on the path is a parent of B. Otherwise A â B is said to be invisible.
//        // ...
//        // The definition of visibility still makes sense in PAGs, except that we will call a
//        // directed edge in a PAG definitely visible if it satisfies the condition for visibility
//        // in Definition 8, in order to emphasize that this edge is visible in all MAGs in the
//        // equivalence class. (p. 1452)
//
//        if (!edge.isDirected()) return false;
//
//        if (graph.containsEdge(edge)) {
//            Node A = Edges.getDirectedEdgeTail(edge);
//            Node B = Edges.getDirectedEdgeHead(edge);
//
//            for (Node C : graph.getAdjacentNodes(A)) {
//                if (C != B && !graph.isAdjacentTo(C, B)) {
//                    Edge e = graph.getEdge(C, A);
//
//                    if (e.getProximalEndpoint(A) == Endpoint.ARROW) {
//                        return true;
//                    } else if (existsColliderPathInto(C, A, B)) {
//                        return true;
//                    }
//                }
//            }
//
//            return false;
//        } else {
//            throw new IllegalArgumentException("Given edge is not in the graph.");
//        }
//    }

//    /**
//     * A helper method for the defVisible method. This implementation uses depth-first search and can be slow for large
//     * graphs.
//     *
//     * @param from the starting node of the path
//     * @param to   the target node of the path
//     * @param into the nodes that colliders along the path must all be parents of
//     * @return true if a collider path exists from 'from' to 'to' that is into 'into'
//     */
//    private boolean existsColliderPathIntoDfs(Node from, Node to, Node into) {
//        Set<Node> visited = new HashSet<>();
//        List<Node> currentPath = new ArrayList<>();
//
//        if (existsColliderPathIntoDfs(null, from, to, into, visited, currentPath)) {
//            return graph.getEndpoint(currentPath.get(currentPath.size() - 2), to) == Endpoint.ARROW;
//        }
//
//        return false;
//    }

//    /**
//     * A helper method for the existsColliderPathInto method.
//     *
//     * @param previous    the previous node in the path
//     * @param current     the current node in the path
//     * @param end         the target node of the path
//     * @param into        the nodes that colliders along the path must all be parents of
//     * @param visited     the set of visited nodes
//     * @param currentPath the current path
//     * @return true if a collider path exists from 'from' to 'to' that is into 'into'
//     */
//    private boolean existsColliderPathIntoDfs(Node previous, Node current, Node end, Node into, Set<Node> visited, List<Node> currentPath) {
//        visited.add(current);
//        currentPath.add(current);
//
//        if (current == end) {
//            return true;
//        } else {
//            for (Node next : graph.getAdjacentNodes(current)) {
//                if (!visited.contains(next) && (previous == null || (graph.isDefCollider(previous, current, next)
//                                                                     && graph.isParentOf(current, into)))) {
//                    if (existsColliderPathIntoDfs(current, next, end, into, visited, currentPath)) {
//                        return true;
//                    }
//                }
//            }
//        }
//
//        currentPath.remove(currentPath.size() - 1);
//        visited.remove(current);
//
//        return false;
//    }

    /**
     * A helper method for the defVisible method, using BFS.
     *
     * @param C the starting node of the path
     * @param A   the target node of the path
     * @param B the nodes that colliders along the path must all be parents of
     * @return true if a collider path exists C 'C' A 'A' that is B 'B'
     */

//    /**
//     * True iff there exists a path C = v0 â¦ vk = A such that
//     *   (i)  for every iâ{1,â¦,kâ1}, vi is a definite collider on (viâ1,vi,vi+1);
//     *   (ii) for every iâ{0,â¦,k},   vi â B   (i.e., vi is a parent of B);
//     *   (iii) vkâ1 *-> A (arrowhead at A).
//     *
//     * @param C The C node from Zhang's (2008) definition of visible edge.
//     * @param A The A node from Zhang's (2008) definition of visible edge.
//     * @param B The B node from Zhang's (2008) definition of visible edge.
//     */
//    private boolean existsColliderPathInto(Node C, Node A, Node B) {
//        // C itself must already be a parent of B, otherwise no path can qualify.
//        if (!graph.isParentOf(C, B)) return false;
//
//        return dfsColliderPath(/*prev*/ null, /*cur*/ C, A, B, new HashSet<>());
//    }
//    private boolean dfsColliderPath(Node prev,
//                                    Node cur,
//                                    Node targetA,
//                                    Node B,
//                                    Set<Node> path) {
//
//        // every node on the path must be a parent of B
//        if (!graph.isParentOf(cur, B)) return false;
//
//        // reached A: last edge must have an arrowhead at A
//        if (cur.equals(targetA)) {
//            return prev != null && graph.getEndpoint(prev, targetA) == Endpoint.ARROW;
//        }
//
//        path.add(cur);               // mark cur for this branch
//
//        for (Node nxt : graph.getAdjacentNodes(cur)) {
//            if (path.contains(nxt)) continue;          // avoid cycles
//
//            // first step out of C has no collider constraint (prev == null)
//            if (prev == null || graph.isDefCollider(prev, cur, nxt)) {
//                if (dfsColliderPath(cur, nxt, targetA, B, path)) return true;
//            }
//        }
//
//        path.remove(cur);            // back-track for other branches
//        return false;
//    }


//    private boolean existsColliderPathInto(Node C, Node A, Node B) {
//        Set<Node> visited = new HashSet<>();
//        Queue<List<Node>> queue = new LinkedList<>(); // Queue A store paths as lists
//
//        // Initialize the queue with the starting node
//        List<Node> initialPath = new ArrayList<>();
//        initialPath.add(C);
//        queue.add(initialPath);
//
//        while (!queue.isEmpty()) {
//            List<Node> currentPath = queue.poll();
//            Node current = currentPath.get(currentPath.size() - 1);
//
//            if (current.equals(A)) {
//                // Check if the path ends in an arrow pointing A 'A'
//                if (currentPath.size() > 1 &&
//                    graph.getEndpoint(currentPath.get(currentPath.size() - 2), A) == Endpoint.ARROW) {
//                    return true;
//                }
//            }
//
//            if (!visited.contains(current)) {
//                visited.add(current);
//
//                for (Node next : graph.getAdjacentNodes(current)) {
//                    Node previous = currentPath.size() > 1 ? currentPath.get(currentPath.size() - 2) : null;
//
//                    if (!visited.contains(next) &&
//                        (previous == null || (graph.isDefCollider(previous, current, next)
//                                              && graph.isParentOf(current, B)))) {
//                        // Create a new path extending the current path
//                        List<Node> newPath = new ArrayList<>(currentPath);
//                        newPath.add(next);
//                        queue.add(newPath);
//                    }
//                }
//            }
//        }
//
//        return false;
//    }

    /**
     * <p>existsDirectedCycle.</p>
     *
     * @return a boolean
     */
    public boolean existsDirectedCycle() {
        for (Node node : graph.getNodes()) {
            if (existsDirectedPath(node, node)) {
                TetradLogger.getInstance().log("Cycle found at node " + node.getName() + ".");
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a directed path exists between two nodes in a graph.
     *
     * @param node1 the starting node of the path
     * @param node2 the target node of the path
     * @return true if a directed path exists from node1 to node2, false otherwise
     */
    public boolean existsDirectedPath(Node node1, Node node2) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        Q.add(node1);
        V.add(node1);

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : graph.getChildren(t)) {
                if (c == node2) return true;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    /**
     * Checks if a directed path exists between two nodes in a graph, ignoring a specified edge.
     *
     * @param node1   the starting node of the path
     * @param node2   the target node of the path
     * @param without the edge to ignore. If null, no edge is ignored.
     * @return true if a directed path exists from node1 to node2, false otherwise
     */
    public boolean existsDirectedPath(Node node1, Node node2, Pair<Node, Node> without) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        Q.add(node1);
        V.add(node1);

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            List<Node> children = graph.getChildren(t);

            for (Node c : children) {
                if (c == node2) return true;

                if (without != null && c == without.getLeft() && t == without.getRight()) {
                    continue;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }

            for (Node c : children) {
                if (c == node2) return true;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    /**
     * <p>existsSemiDirectedPath.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodes a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean existsSemiDirectedPath(Node node1, Set<Node> nodes) {
        return existsSemiDirectedPathVisit(node1, nodes, new LinkedList<>());
    }

    /**
     * Determines whether a trek exists between two nodes in the graph. A trek exists if there is a directed path
     * between the two nodes or else, for some third node in the graph, there is a path to each of the two nodes in
     * question.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsTrek(Node node1, Node node2) {
        for (Node node : graph.getNodes()) {
            if (isAncestorOf((node), node1) && isAncestorOf((node), node2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a list of all descendants of the given node.
     *
     * @param node The node for which to find descendants.
     * @return A list of all descendant nodes.
     */
    public Set<Node> getDescendants(Node node) {
        Set<Node> descendants = new HashSet<>();

        for (Node n : graph.getNodes()) {
            if (isDescendentOf(n, node)) {
                descendants.add(n);
            }
        }

        return descendants;
    }

    /**
     * Retrieves the descendants of the given list of nodes.
     *
     * @param nodes The list of nodes to find descendants for.
     * @return A list of nodes that are descendants of the given nodes.
     */
    public List<Node> getDescendants(List<Node> nodes) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            for (Node m : nodes) {
                if (isDescendentOf(n, m)) {
                    ancestors.add(n);
                }
            }
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Determines whether one node is an ancestor of another.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        return graph.isAncestorOf(node1, node2);
//        return node1 == node2 || existsDirectedPath(node1, node2);
    }

    /**
     * Retrieves the ancestors of a specified `Node` in the graph.
     *
     * @param node The node whose ancestors are to be retrieved.
     * @return A list of ancestors for the specified `Node`.
     */
    public List<Node> getAncestors(Node node) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            if (isAncestorOf(n, node)) {
                ancestors.add(n);
            }
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Returns a list of all ancestors of the given nodes.
     *
     * @param nodes the list of nodes for which to find ancestors
     * @return a list containing all the ancestors of the given nodes
     */
    public List<Node> getAncestors(List<Node> nodes) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            for (Node m : nodes) {
                if (isAncestorOf(n, m)) {
                    ancestors.add(n);
                }
            }
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Determines whether one node is a descendent of another.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isDescendentOf(Node node1, Node node2) {
        return node1 == node2 || existsDirectedPath(node2, node1);
    }

    /**
     * added by ekorber, 2004/06/12
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff node2 is a definite nondecendent of node1
     */
    public boolean definiteNonDescendent(Node node1, Node node2) {
        return !(possibleAncestor(node1, node2));
    }

    /**
     * Determines whether one n ode is d-separated from another. According to Spirtes, Richardson and Meek, two nodes
     * are d- connected given some conditioning set Z if there is an acyclic undirected path U between them, such that
     * every collider on U is an ancestor of some element in Z and every non-collider on U is not in Z. Two elements are
     * d-separated just in case they are not d-connected. A collider is a node which two edges hold in common for which
     * the endpoints leading into the node are both arrow endpoints.
     * <p>
     * Precondition: This graph is a DAG. Please don't violate this constraint; weird things can happen!
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @param z     the conditioning set.
     * @param isPag whether to allow selection bias; if true, then undirected edges X--Y are uniformly treated as
     *              X-&gt;L&lt;-Y.
     * @return true if node1 is d-separated from node2 given set t, false if not.
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z, boolean isPag) {
        return separates(node1, node2, isPag, z);
    }

    /**
     * Checks if two nodes are M-separated.
     *
     * @param node1              The first node.
     * @param node2              The second node.
     * @param z                  The set of nodes to be excluded from the path.
     * @param ancestors          A map containing the ancestors of each node.
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return {@code true} if the two nodes are M-separated, {@code false} otherwise.
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
        return !isMConnectedTo(node1, node2, z, ancestors, allowSelectionBias);
    }

    /**
     * Checks if a semi-directed path exists between the given node and any of the nodes in the provided set.
     *
     * @param node1  The starting node for the path.
     * @param nodes2 The set of nodes to check for a path.
     * @param path   The current path (used for cycle detection).
     * @return {@code true} if a semi-directed path exists, {@code false} otherwise.
     */
    private boolean existsSemiDirectedPathVisit(Node node1, Set<Node> nodes2, LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (nodes2.contains(child)) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsSemiDirectedPathVisit(child, nodes2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * Checks if there is a directed edge from node1 to node2 in the graph.
     *
     * @param node1 the source node
     * @param node2 the destination node
     * @return true if there is a directed edge from node1 to node2, false otherwise
     */
    public boolean isDirected(Node node1, Node node2) {
        List<Edge> edges = graph.getEdges(node1, node2);
        if (edges.size() != 1) {
            return false;
        }
        Edge edge = edges.iterator().next();
        return edge.pointsTowards(node2);
    }

    /**
     * Checks if the edge between two nodes in the graph is undirected.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true if the edge is undirected, false otherwise
     */
    public boolean isUndirected(Node node1, Node node2) {
        Edge edge = graph.getEdge(node1, node2);
        return edge != null && edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL;
    }

    /**
     * <p>possibleAncestor.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean possibleAncestor(Node node1, Node node2) {
        return existsSemiDirectedPath(node1, Collections.singleton(node2));
    }

    /**
     * Returns the set of nodes that are in the anteriority of the given nodes in the graph.
     *
     * @param X the nodes for which the anteriority needs to be determined
     * @return the set of nodes in the anteriority of the given nodes
     */
    public Set<Node> anteriority(Node... X) {
        return GraphUtils.anteriority(graph, X);
    }

    /**
     * An adjustment set for a pair of nodes &lt;source, target&gt; for a CPDAG is a set of nodes that blocks all paths
     * from the source to the target that cannot contribute to a calculation for the total effect of the source on the
     * target in any DAG in a CPDAG while not blocking any path from the source to the target that could be causal. In
     * typical causal graphs, multiple adjustment sets may exist for a given pair of nodes. This method returns up to
     * maxNumSets adjustment sets for the pair of nodes &lt;source, target&gt; fitting a certain description.
     * <p>
     * The description is as follows. We look for adjustment sets of varaibles that are close to either the source or
     * the target (or either) in the graph. We take all possibly causal paths from the source to the target into account
     * but only consider other paths up to a certain specified length. (This maximum length can be unlimited for small
     * graphs.)
     * <p>
     * Within this description, we list adjustment sets in order or increasing size.
     * <p>
     * Hopefully, these parameters along with the size ordering can help to give guidance for the user to choose the
     * best adjustment set for their purposes when multiple adjustment sets are possible.
     * <p>
     * This currently will only work for DAGs and CPDAGs.
     *
     * @param source                  The source node whose sets will be used for adjustment.
     * @param target                  The target node whose sets will be adjusted to match the source node.
     * @param maxNumSets              The maximum number of sets to be adjusted. If this value is less than or equal to
     *                                0, all sets in the target node will be adjusted to match the source node.
     * @param maxDistanceFromEndpoint The maximum distance from the endpoint of the trek to consider for adjustment.
     * @param nearWhichEndpoint       The endpoint(s) to consider for adjustment; 1 = near the source, 2 = near the
     *                                target, 3 = near either.
     * @param maxPathLength           The maximum length of the path to consider for backdoor paths. If a value of -1 is
     *                                given, all paths will be considered.
     * @return A list of adjustment sets for the pair of nodes &lt;source, target&gt;. Return an smpty list if source ==
     * target or there is no amenable path from source to target.
     */
    public List<Set<Node>> adjustmentSets(Node source, Node target, int maxNumSets, int maxDistanceFromEndpoint, int nearWhichEndpoint, int maxPathLength) {
        if (source == target) {
            return new ArrayList<>();
//            throw new IllegalArgumentException("Source and target nodes must be different.");
        }

        boolean mpdag = false;
        boolean mag = false;
        boolean pag = false;

        if (graph.paths().isLegalMpdag()) {
            mpdag = true;
        } else if (graph.paths().isLegalMag()) {
            mag = true;
        } else if (!graph.paths().isLegalPag()) {
            pag = true;
        }

        List<List<Node>> amenable = semidirectedPaths(source, target, -1);

        // Remove any amenable path that does not start with a visible edge in the CPDAG case.
        // (The PAG case will be handled later.)
        for (List<Node> path : new ArrayList<>(amenable)) {
            if (path.size() < 2) {
                amenable.remove(path);
            }

            Node a = path.getFirst();
            Node b = path.get(1);
            Edge e = graph.getEdge(a, b);

            if (!e.pointsTowards(b)) {
                amenable.remove(path);
            }
        }

        if (amenable.isEmpty()) {
            return new ArrayList<>();
//            throw new IllegalArgumentException("No amenable paths found.");
        }

        Set<List<Node>> backdoorPaths = allPaths(source, target, maxPathLength);

        if (mpdag || mag) {
            backdoorPaths.removeIf(path -> path.size() < 2 || !(graph.getEdge(path.getFirst(), path.get(1)).pointsTowards(path.getFirst())));
        } else {
            backdoorPaths.removeIf(path -> {
                if (path.size() < 2) {
                    return false;
                }
                Node x = path.getFirst();
                Node w = path.get(1);
                Node y = target;
                return !(graph.getEdge(x, w).pointsTowards(x) || Edges.isUndirectedEdge(graph.getEdge(x, w)) || Edges.isBidirectedEdge(graph.getEdge(x, w)) && (graph.paths().existsDirectedPath(w, x) || (graph.paths().existsDirectedPath(w, x) && graph.paths().existsDirectedPath(w, y))));
            });
        }

        List<Set<Node>> adjustmentSets = new ArrayList<>();
        Set<Set<Node>> tried = new HashSet<>();

        int i = 1;

        while (i <= maxDistanceFromEndpoint) {
            Set<Node> _nearEndpoints = new HashSet<>();

            // Add nodes a distance of at most i from one end or the other of each trek, along the trek.
            // That is, if the trek is a list <a, b, c, d, e>, and i = 0, we would add a and e to the list.
            // If i = 1, we would add a, b, d, and e to the list. And so on.
            for (int j = 1; j <= i; j++) {
                for (List<Node> trek : backdoorPaths) {
                    if (j >= trek.size()) {
                        continue;
                    }

                    if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3) {
                        Node e1 = trek.get(j);

                        if (!(e1 == source || e1 == target)) {
                            _nearEndpoints.add(e1);
                        }
                    }

                    if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3) {
                        Node e2 = trek.get(trek.size() - 1 - j);

                        if (!(e2 == source || e2 == target)) {
                            _nearEndpoints.add(e2);
                        }
                    }
                }
            }

            List<Node> nearEndpoints = new ArrayList<>(_nearEndpoints);

            List<Set<Node>> possibleAdjustmentSets = new ArrayList<>();

            // Now, using SublistGenerator, we generate all possible subsets of the nodes we just added.
            SublistGenerator generator = new SublistGenerator(nearEndpoints.size(), nearEndpoints.size());
            int[] choice;

            while ((choice = generator.next()) != null) {
                Set<Node> possibleAdjustmentSet = new HashSet<>();
                for (int j : choice) {
                    possibleAdjustmentSet.add(nearEndpoints.get(j));
                }
                possibleAdjustmentSets.add(possibleAdjustmentSet);
            }

            // Now, for each set of nodes in possibleAdjustmentSets, we check if it is an adjustment set.
            // That is, we check if it blocks all backdoorPaths from source to target that are not semi-directed
            // without blocking any backdoorPaths that are semi-directed.

            ADJ:
            for (Set<Node> possibleAdjustmentSet : possibleAdjustmentSets) {
                if (tried.contains(possibleAdjustmentSet)) {
                    i++;
                    continue;
                }

                tried.add(possibleAdjustmentSet);

                for (List<Node> semi : amenable) {
                    if (!isMConnectingPath(semi, possibleAdjustmentSet, !mpdag)) {
                        i++;
                        continue ADJ;
                    }
                }

                for (List<Node> _backdoor : backdoorPaths) {
                    if (isMConnectingPath(_backdoor, possibleAdjustmentSet, !mpdag)) {
                        i++;
                        continue ADJ;
                    }
                }

                if (!adjustmentSets.contains(possibleAdjustmentSet)) {
                    adjustmentSets.add(possibleAdjustmentSet);
                }

                if (adjustmentSets.size() >= maxNumSets) {
                    return adjustmentSets;
                }
            }

            i++;
        }

        return adjustmentSets;
    }

// --- Public API: Recursive (mask-based) adjustment ---

    /**
     * Recursive adjustment-set finder (library call).
     *
     * Returns:
     *   - A (possibly non-unique) list of adjustment sets when amenable paths exist,
     *   - null if there are no amenable paths X ~~> Y, or if no recursive solution is found.
     *
     * Backward-compatible: does not alter the legacy adjustmentSets(...) method.
     */
    public List<Set<Node>> recursiveAdjustment(Node source,
                                               Node target,
                                               int maxNumSets,
                                               int maxPathLength,
                                               boolean minimizeEach) {
        // Degenerate: x ~~> x
        if (source == null || target == null || source == target) return Collections.emptyList();

        // 1) Get amenable (causal) paths X ~~> Y per graph semantics
        List<List<Node>> amenable = getAmenablePaths(source, target, -1);

        // 2) If none, spec says: return null
        if (amenable == null || amenable.isEmpty()) return null;

        // 3) Build latent/forbidden mask for total-effect adjustment
        //    (forbid interior non-colliders on amenable paths; also forbid Desc(X))
        Set<Node> latentMask = buildLatentMaskForTotalEffect(this.graph, source, target, amenable,
                Collections.emptySet(), /*includeDescOfX*/ true);

        // 4) Run recursive enumerator
        try {
            List<Set<Node>> sets = RecursiveAdjustment.findAdjustmentSets(
                    this.graph,
                    source,
                    target,
                    /*seedZ*/ Collections.emptySet(),
                    /*notFollowed*/ Collections.emptySet(),
                    /*maxPathLength*/ maxPathLength <= 0 ? -1 : maxPathLength,
                    /*latentMask*/ latentMask,
                    /*maxSets*/ (maxNumSets <= 0 ? -1 : maxNumSets),
                    /*minimizeEach*/ minimizeEach
            );

            // If the recursive method yields nothing, treat as "no answer" -> null (per your spec)
            if (sets == null || sets.isEmpty()) return null;

            return sets;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    static Set<Node> buildLatentMaskForTotalEffect(Graph graph,
                                                   Node X, Node Y,
                                                   Collection<List<Node>> amenablePaths,
                                                   Set<Node> containing,            // seed Z (may be empty)
                                                   boolean includeDescendantsOfX) { // Tier-B toggle
        Set<Node> mask = new HashSet<>();

        // --- Tier A: interior non-colliders along amenable X→Y paths ---
        for (List<Node> p : amenablePaths) {
            if (p.size() < 3) continue; // no interior nodes on length-2 path X–Y
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), b = p.get(i), c = p.get(i + 1);
                // Mask ONLY if b is a definite non-collider on this triple
                if (!graph.isDefCollider(a, b, c) && b.getNodeType() == NodeType.MEASURED) {
                    mask.add(b);
                }
            }
        }

        // --- Tier B (optional): Descendants of X (measured only) ---
        if (includeDescendantsOfX) {
            Map<Node, Set<Node>> descMap = graph.paths().getDescendantsMap(); // recompute per run
            Set<Node> descX = descMap.getOrDefault(X, Collections.emptySet());
            for (Node d : descX) {
                if (d != null && d.getNodeType() == NodeType.MEASURED) {
                    mask.add(d);
                }
            }
        }

        // --- Sanitize: remove endpoints, latents, and anything the user forced into Z ---
        mask.remove(X);
        mask.remove(Y);
        if (containing != null) {
            mask.removeAll(containing);
        }
        // Keep mask to measured nodes only; true latents are already handled by RB
        mask.removeIf(n -> n == null || n.getNodeType() != NodeType.MEASURED);

        return mask;
    }

    /**
     * When there are no amenable paths X ~~> Y, this returns candidate separating sets:
     * sets that block all backdoor (non-causal) paths, using the same recursive engine
     * but with an EMPTY mask (so it’s allowed to condition anywhere that’s measured).
     *
     * If amenable paths DO exist, this returns an empty list (use recursiveAdjustment instead).
     */
    public List<Set<Node>> recursiveSeparatingSets(Node source,
                                                   Node target,
                                                   int maxNumSets,
                                                   int maxPathLength,
                                                   boolean minimizeEach) {
        if (source == null || target == null || source == target) return Collections.emptyList();

        List<List<Node>> amenable = getAmenablePaths(source, target, -1);

        // Only produce separating sets when there are NO amenable paths
        if (amenable != null && !amenable.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return RecursiveAdjustment.findAdjustmentSets(
                    this.graph,
                    source,
                    target,
                    /*seedZ*/ Collections.emptySet(),
                    /*notFollowed*/ Collections.emptySet(),
                    /*maxPathLength*/ maxPathLength <= 0 ? -1 : maxPathLength,
                    /*latentMask*/ Collections.emptySet(),   // <-- key difference
                    /*maxSets*/ (maxNumSets <= 0 ? -1 : maxNumSets),
                    /*minimizeEach*/ minimizeEach
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    public boolean hasAmenablePaths(Node x, Node y, int maxLength) {
        List<List<Node>> aps = getAmenablePaths(x, y, maxLength);
        return aps != null && !aps.isEmpty();
    }

    private List<List<Node>> getAmenablePaths(Node source, Node target, int maxLength) {
        if (source == null || target == null || source == target) return Collections.emptyList();
        if (graph.paths().isLegalPag()) {
            return graph.paths().amenablePathsPag(source, target, maxLength);
        } else {
            // DAG/CPDAG/MPDAG/MAG handled here
            return graph.paths().amenablePathsMpdagMag(source, target, maxLength);
        }
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Determines whether the graph is acyclic, meaning it does not contain any directed cycles.
     *
     * @return true if the graph is acyclic; false otherwise
     */
    public boolean isAcyclic() {
        return !existsDirectedCycle();
    }

    /**
     * Determines if the current graph configuration is maximal. A graph configuration is considered maximal if for
     * every pair of non-adjacent nodes, there does not exist an inducing path between them considering the selected
     * nodes.
     *
     * @return true if the graph configuration is maximal, otherwise false.
     */
    public boolean isMaximal() {
        List<Node> selection = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        List<Node> _nodes = graph.getNodes();

        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                Node x = _nodes.get(i);
                Node y = _nodes.get(j);

                if (!graph.isAdjacentTo(x, y) && graph.paths().existsInducingPath(x, y, new HashSet<>(selection))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Convenience record for queue elements.
     */
    private static final class PathElem {
        final Node cur;      // current vertex
        final Node prev;     // vertex we arrived from (null at start)

        PathElem(Node cur, Node prev) {
            this.cur = cur;
            this.prev = prev;
        }
    }

    /**
     * (prev,cur) pair so we can mark *edges* as visited.
     */
    private static final class NodePair {
        final Node prev, cur;

        NodePair(Node prev, Node cur) {
            this.prev = prev;
            this.cur = cur;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodePair p)) return false;
            return Objects.equals(prev, p.prev) && Objects.equals(cur, p.cur);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prev, cur);
        }
    }

    /**
     * Represents an element in a navigation path consisting of a current node and the previous node.
     * <p>
     * This class is primarily used to track the traversal history in a pathfinding context or similar scenarios where
     * the relationship between nodes in a path needs to be maintained.
     */
    static class PathElement {
        Node current;  // The current node
        Node previous; // The previous node on the path

        PathElement(Node current, Node previous) {
            this.current = current;
            this.previous = previous;
        }
    }

    /**
     * An algorithm to find all cliques in a graph.
     */
    public static class AllCliquesAlgorithm {

        /**
         * Private constructor to prevent instantiation.
         */
        private AllCliquesAlgorithm() {

        }

        /**
         * Main method.
         *
         * @param args the command-line arguments
         */
        public static void main(String[] args) {
            int[][] graph = {{0, 1, 1, 0, 0}, {1, 0, 1, 1, 0}, {1, 1, 0, 1, 1}, {0, 1, 1, 0, 1}, {0, 0, 1, 1, 0}};
            int n = graph.length;

            List<List<Integer>> cliques = findCliques(graph, n);
            System.out.println("All Cliques:");
            for (List<Integer> clique : cliques) {
                System.out.println(clique);
            }
        }

        /**
         * Find all cliques in a graph.
         *
         * @param graph the graph
         * @param n     the number of vertices in the graph
         * @return a list of cliques
         */
        public static List<List<Integer>> findCliques(int[][] graph, int n) {
            List<List<Integer>> cliques = new ArrayList<>();
            Set<Integer> candidates = new HashSet<>();
            Set<Integer> excluded = new HashSet<>();
            Set<Integer> included = new HashSet<>();

            for (int i = 0; i < n; i++) {
                candidates.add(i);
            }

            bronKerbosch(graph, candidates, excluded, included, cliques);

            return cliques;
        }

        private static void bronKerbosch(int[][] graph, Set<Integer> candidates, Set<Integer> excluded, Set<Integer> included, List<List<Integer>> cliques) {
            if (candidates.isEmpty() && excluded.isEmpty()) {
                cliques.add(new ArrayList<>(included));
                return;
            }

            Set<Integer> candidatesCopy = new HashSet<>(candidates);
            for (int vertex : candidatesCopy) {
                Set<Integer> neighbors = new HashSet<>();
                for (int i = 0; i < graph.length; i++) {
                    if (graph[vertex][i] == 1 && candidates.contains(i)) {
                        neighbors.add(i);
                    }
                }

                bronKerbosch(graph, intersect(candidates, neighbors), intersect(excluded, neighbors), union(included, vertex), cliques);

                candidates.remove(vertex);
                excluded.add(vertex);
            }
        }

        private static Set<Integer> intersect(Set<Integer> set1, Set<Integer> set2) {
            Set<Integer> result = new HashSet<>(set1);
            result.retainAll(set2);
            return result;
        }

        private static Set<Integer> union(Set<Integer> set, int element) {
            Set<Integer> result = new HashSet<>(set);
            result.add(element);
            return result;
        }
    }
}


