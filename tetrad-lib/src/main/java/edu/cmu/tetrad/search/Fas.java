///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implements the Fast Adjacency Search (FAS), which is the adjacency search of the PC algorithm (see). This is a useful
 * algorithm in many contexts, including as the first step of FCI (see).
 * <p>
 * The idea of FAS is that at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S,
 * where S is a subset of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency
 * search performs this procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1.
 * Here, d1 is either the maximum depth or else the first such depth at which no edges can be removed. The
 * interpretation of this adjacency search is different for different algorithms, depending on the assumptions of the
 * algorithm. A mapping from {x, y} to S({x, y}) is returned for edges x *-* y that have been removed.
 * <p>
 * FAS may optionally use a heuristic from Causation, Prediction, and Search, which (like PC-Stable) renders the output
 * invariant to the order of the input variables.
 * <p>
 * This algorithm was described in the earlier edition of this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author clarkglymour
 * @author josephramsey.
 * @version $Id: $Id
 * @see Pc
 * @see Fci
 * @see Knowledge
 */
public class Fas implements IFas {

    /**
     * A separation set map used to store the results of conditional independence tests performed during the graph
     * search process. It holds information about the sets of variables that separate pairs of nodes in the graph.
     */
    private final SepsetMap sepset = new SepsetMap();
    /**
     * Represents the core independence test utilized in the FAS algorithm to determine conditional independence
     * relationships between variables. This test serves as the foundation for building and refining graphs during the
     * search process.
     */
    private IndependenceTest test;
    /**
     * A Knowledge object used to define constraints, such as forbidden or required edges, and other background
     * information necessary for the conditional independence search process.
     * <p>
     * This variable is integral to guiding various search methods and algorithms in the parent class, ensuring that the
     * results adhere to predetermined conditions or limitations imposed by the user or the context of the analysis.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Specifies the maximum depth to be considered during the conditional independence search process. The depth
     * determines the maximum number of conditioning variables used in independence tests, where a value of -1 indicates
     * that no limit is imposed. Values < -1 are treated as -1 (no cap).
     */
    private int depth = -1;

    /**
     * A flag indicating whether the search process will use a stable strategy. When set to {@code true}, the search
     * follows a stable approach (freeze adjacencies per depth). In this implementation, parallelization is only used
     * when stable = true (decision phase only).
     */
    private boolean stable = true;

    /**
     * A flag indicating whether verbose output is enabled during the search process. If true, additional logging or
     * debugging information is printed to the configured output stream. If false, only essential information is
     * displayed.
     */
    private boolean verbose = false;

    /**
     * The output stream used for logging or displaying messages during the search process. By default, it is set to
     * {@code System.out}. This stream can be replaced or customized by the user to redirect output messages to
     * alternative destinations.
     */
    private PrintStream out = System.out;

    /**
     * Constructs a new instance of the Fas algorithm using the specified independence test.
     *
     * @param test The independence test to be used by the Fas algorithm for conditional independence tests during the
     *             search process.
     */
    public Fas(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Returns a list of nodes that are possible parents of a given node, based on the adjacency list of the given node,
     * the knowledge object, and another node.
     *
     * @param x         The node for which to find possible parents.
     * @param adjx      The adjacency list of the node x.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @param y         Another node in the graph.
     * @return A list of nodes that are possible parents of the node x.
     */
    private static List<Node> possibleParents(Node x, List<Node> adjx, Knowledge knowledge, Node y) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            if (z == null) continue;
            if (z == x) continue;
            if (z == y) continue;
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * Determines if a given node is a possible parent of another node based on the provided knowledge.
     *
     * @param z         The first node.
     * @param x         The second node.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @return True if the node z is a possible parent of node x, false otherwise.
     */
    private static boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Performs a conditional independence graph search using the default set of variables from the initialized
     * independence test. The method delegates the search process to another method that takes a list of variables as
     * input.
     *
     * @return A graph where edges are updated based on the results of conditional independence tests and other provided
     * constraints.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    /**
     * Searches for conditional independence relationships in a graph constructed from the given list of nodes. Edges
     * are removed based on the provided set of constraints and the maximum depth for the search.
     *
     * @param nodes The list of nodes to construct the graph and perform the search on.
     * @return A graph with edges updated based on the search process and conditional independence tests.
     * @throws InterruptedException if interrupted.
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("Variables should be a subset of the ones in the test.");
        }

        Graph modify = new EdgeListGraph(nodes);
        modify = GraphUtils.completeGraph(modify);

        // Apply forbidden knowledge upfront.
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (knowledge.isForbidden(x.getName(), y.getName()) &&
                    knowledge.isForbidden(y.getName(), x.getName())) {
                    modify.removeEdge(x, y);

                    if (verbose) {
                        TetradLogger.getInstance().log("Edge removed by knowledge: " + x.getName() + " *-* " + y.getName());
                    }
                }
            }
        }

        // Normalize depth: -1 (or anything < -1) means "no cap" â up to n-1.
        final int n = test.getVariables().size();
        final int depthCap = (depth < 0) ? (n - 1) : depth;

        for (int d = 0; d <= depthCap; d++) {
            if (verbose) {
                System.out.println("Depth: " + d);
            }

            // Run one depth; stop if nothing was removed at this depth (PC-Stable termination)
            boolean anyRemovedAtThisDepth;
            if (this.stable) {
                // Freeze adjacencies for this depth.
                Graph checkAdj = new EdgeListGraph(modify);
                anyRemovedAtThisDepth = searchAtDepth(checkAdj, modify, d, true);
            } else {
                anyRemovedAtThisDepth = searchAtDepth(modify, modify, d, false);
            }

            if (!anyRemovedAtThisDepth && freeDegree(modify) <= d) {
                break;
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted");
            }
        }

        return modify;
    }

    public IndependenceTest getTest() {
        return test;
    }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();

        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(String.format("The nodes of the proposed new test are not equal list-wise\n" +
                                                             "to the nodes of the existing test."));
        }

        this.test = test;
    }

    /**
     * Searches for conditional independence relationships in a checkAdj up to a given depth d. If the checkAdj's nodes
     * meet the conditions specified, the edges may be removed based on the provided knowledge.
     *
     * <p>Stable semantics: parallel decision on frozen adjacency (checkAdj), then sequential application to
     * modify.</p>
     * <p>Unstable semantics: sequential mutate-as-you-go on modify.</p>
     *
     * @param checkAdj The checkAdj on which the search is performed.
     * @param modify   A copy of the checkAdj where changes are applied during the search process.
     * @param d        The maximum depth to consider for conditional independence tests.
     * @param stable   If true, performs the search using a parallel decision strategy; otherwise, a sequential
     *                 strategy.
     * @return True iff any edge was removed at this depth.
     */
    private boolean searchAtDepth(Graph checkAdj, Graph modify, int d, boolean stable) {
        boolean anyRemoved = false;

        if (stable) {
            // 1) Decide in parallel on the frozen adjacency.
            ConcurrentLinkedQueue<EdgeRemoval> removals = new ConcurrentLinkedQueue<>();
            List<Node> nodes = checkAdj.getNodes();

            nodes.parallelStream().forEach(x -> {
                for (Node y : checkAdj.getAdjacentNodes(x)) {
                    // Process each unordered pair once (canonical order x<y).
                    if (x.getName().compareTo(y.getName()) >= 0) continue;
                    decideOnePair(checkAdj, d, x, y, removals);
                    if (Thread.currentThread().isInterrupted()) return;
                }
            });

//            // 2) Apply sequentially to the live graph and sepsets.
//            for (EdgeRemoval r : removals) {
//                if (Thread.currentThread().isInterrupted()) break;
//
//                // Respect required-edge knowledge again at apply time.
//                if (knowledge.noEdgeRequired(r.x.getName(), r.y.getName())) {
//                    if (modify.isAdjacentTo(r.x, r.y)) {
//                        modify.removeEdge(r.x, r.y);
//                        this.sepset.set(r.x, r.y, r.S);
//                        anyRemoved = true;
//
//                        if (verbose) {
//                            TetradLogger.getInstance().log(
//                                    LogUtilsSearch.independenceFactMsg(r.x, r.y, r.S, r.pValue));
//                        }
//                    }
//                }
//            }

            // 2) Apply sequentially to the live graph and sepsets.
            List<EdgeRemoval> toApply = new ArrayList<>(removals);
            toApply.sort(Comparator
                    .comparing((EdgeRemoval r) -> r.x.getName())
                    .thenComparing(r -> r.y.getName())
                    .thenComparingInt(r -> r.S.size())); // tie-breaker: smaller S first

            for (EdgeRemoval r : toApply) {
                if (Thread.currentThread().isInterrupted()) break;

                if (knowledge.noEdgeRequired(r.x.getName(), r.y.getName())) {
                    if (modify.isAdjacentTo(r.x, r.y)) {
                        modify.removeEdge(r.x, r.y);
                        Set<Node> SSaved = new LinkedHashSet<>(r.S);
                        this.sepset.set(r.x, r.y, SSaved);
                        anyRemoved = true;

//                        if (verbose) {
//                            TetradLogger.getInstance().log(
//                                    LogUtilsSearch.independenceFactMsg(r.x, r.y, r.S, r.pValue));
//                        }
                    }
                }
            }
        } else {
            // Classic, sequential/unstable pass: mutates as it goes.
            for (Node x : checkAdj.getNodes()) {
                int before = modify.getEdges().size();
                removeNodesAboutX(modify, modify, d, x);
                int after = modify.getEdges().size();
                if (after < before) anyRemoved = true;
                if (Thread.currentThread().isInterrupted()) break;
            }
        }

        return anyRemoved;
    }

    /**
     * Decide-only for a single (x, y) pair on the frozen graph (used in stable/parallel decision phase). Adds a
     * proposed removal to 'out' if an S of size d separates x and y. Tests subsets from adj(x)\{y} and adj(y)\{x} (both
     * sides).
     */
    private void decideOnePair(Graph checkAdj, int d, Node x, Node y,
                               ConcurrentLinkedQueue<EdgeRemoval> out) {
        // Side X: subsets of adj(x) \ {y}
        List<Node> adjx = new ArrayList<>(checkAdj.getAdjacentNodes(x));
        adjx.remove(y);
        List<Node> ppx = possibleParents(x, adjx, knowledge, y); // respects knowledge

        if (ppx.size() >= d) {
            ChoiceGenerator gen = new ChoiceGenerator(ppx.size(), d);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;

                Set<Node> S = GraphUtils.asSet(choice, ppx);

                IndependenceResult result;
                try {
                    result = test.checkIndependence(x, y, S);
                } catch (InterruptedException e) {
                    // Preserve interrupt status and stop working this pair.
                    Thread.currentThread().interrupt();
                    return;
                }

                if (result.isIndependent() && knowledge.noEdgeRequired(x.getName(), y.getName())) {
                    out.add(new EdgeRemoval(x, y, S, result.getPValue()));
                    return; // first separating set at this depth is enough
                }
            }
        }

        // Side Y: subsets of adj(y) \ {x}
        List<Node> adjy = new ArrayList<>(checkAdj.getAdjacentNodes(y));
        adjy.remove(x);
        List<Node> ppy = possibleParents(y, adjy, knowledge, x); // respects knowledge

        if (ppy.size() >= d) {
            ChoiceGenerator gen = new ChoiceGenerator(ppy.size(), d);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;

                Set<Node> S = GraphUtils.asSet(choice, ppy);

                IndependenceResult result;
                try {
                    result = test.checkIndependence(x, y, S);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (result.isIndependent() && knowledge.noEdgeRequired(x.getName(), y.getName())) {
                    out.add(new EdgeRemoval(x, y, S, result.getPValue()));
                    return; // first separating set at this depth is enough
                }
            }
        }
    }

    private void removeNodesAboutX(Graph checkAdj, Graph modify, int d, Node x) {
        for (Node y : checkAdj.getAdjacentNodes(x)) {
            List<Node> ppx = possibleParents(x, checkAdj.getAdjacentNodes(x), knowledge, y);

            if (ppx.size() >= d) {
                ChoiceGenerator generator = new ChoiceGenerator(ppx.size(), d);
                int[] choice;

                while ((choice = generator.next()) != null) {
                    Set<Node> S = GraphUtils.asSet(choice, ppx);

                    IndependenceResult result;
                    try {
                        result = test.checkIndependence(x, y, S);
                    } catch (InterruptedException e) {
                        // Preserve interrupt status and stop working this neighborhood.
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (result.isIndependent() && knowledge.noEdgeRequired(x.getName(), y.getName())) {
                        modify.removeEdge(x, y);
                        Set<Node> sSaved = new LinkedHashSet<>(S);
                        this.sepset.set(x, y, sSaved);

//                        if (verbose) {
//                            TetradLogger.getInstance().log(
//                                    LogUtilsSearch.independenceFactMsg(x, y, S, result.getPValue()));
//                        }

                        break;
                    }
                }
            }

            if (Thread.currentThread().isInterrupted()) return;
        }
    }

    /**
     * Calculates the maximum free degree among the nodes in the given graph. For any node n, the maximum
     * conditioning-set size needed about n is deg(n) - 1.
     *
     * @param graph The graph.
     * @return The maximum free degree among the nodes.
     */
    private int freeDegree(Graph graph) {
        int max = 0;
        for (Node n : graph.getNodes()) {
            int deg = graph.getAdjacentNodes(n).size();
            if (deg > 0 && deg - 1 > max) {
                max = deg - 1;
            }
        }
        return max;
    }

    /**
     * Sets the depth for the search process.
     *
     * @param depth The maximum depth to be considered during the search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the knowledge object used in the conditional independence search process.
     *
     * @param knowledge The knowledge object that provides information about constraints or background knowledge, such
     *                  as forbidden edges, required edges, or other conditional independencies.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the separation set map that is used during the search process to store information about conditional
     * independence tests.
     *
     * @return The separation set map represented by an instance of {@code SepsetMap}, containing conditional
     * independence information.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets the verbosity level for the logging or debugging output of the search process.
     *
     * @param verbose If true, enables verbose output; if false, disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the stability flag for the search process, which may determine the search strategy or algorithm's behavior.
     *
     * @param stable If true, enables a stable search strategy; otherwise, an unstable or alternative strategy is used.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Retrieves a list of nodes associated with the initialized independence test.
     *
     * @return A list of nodes derived from the variables in the associated independence test.
     */
    public List<Node> getNodes() {
        return test.getVariables();
    }

    // ---------------------------------------------------------------------
    // Helper record for proposed removals (used in stable/parallel decision)
    // ---------------------------------------------------------------------
    private static final class EdgeRemoval {
        final Node x, y;
        final Set<Node> S;
        final double pValue;

        EdgeRemoval(Node x, Node y, Set<Node> S, double pValue) {
            this.x = x;
            this.y = y;
            this.S = S;
            this.pValue = pValue;
        }
    }
}
