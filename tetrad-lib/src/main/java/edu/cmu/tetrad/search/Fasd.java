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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;

/**
 * Adjusts FAS (see) for the deterministic case by refusing to removed edges based on conditional independence tests
 * that are judged to be deterministic. That is, if X _||_ Y | Z, but Z determines X or Y, then the edge X---Y is not
 * removed.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author josephramsey.
 * @version $Id: $Id
 * @see Fas
 * @see Knowledge
 */
public class Fasd implements IFas {

    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;
    /**
     * The logger, by default the empty logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The number formatter.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The search graph. It is assumed going in that all the true adjacencies of x are in this graph for every node x.
     * It is hoped (i.e., true in the large sample limit) that true adjacencies are never removed.
     */
    private final Graph graph;
    /**
     * Specification of which edges are forbidden or required.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;
    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;
    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();
    /**
     * The depth 0 graph, specified initially.
     */
    private Graph externalGraph;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The output stream.
     */
    private transient PrintStream out = System.out;

    /**
     * Constructs a new FastAdjacencySearch.
     *
     * @param test A test to use as a conditional independence oracle.
     */
    public Fasd(IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
    }

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent, conditional on some other set of variables in the graph (the "sepset"). These
     * are removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then
     * edges which are independent conditional on one other variable are removed, then two, then three, and so on, until
     * no more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a graph which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        TetradLogger.getInstance().log("Starting Fast Adjacency Search.");
        this.graph.removeEdges(this.graph.getEdges());

        this.sepset = new SepsetMap();

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        Map<Node, Set<Node>> adjacencies = new HashMap<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node node : nodes) {
            adjacencies.put(node, new TreeSet<>());
        }

        for (int d = 0; d <= _depth; d++) {
            boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, this.test, adjacencies);
            } else {
                more = searchAtDepth(nodes, this.test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    this.graph.addUndirectedEdge(x, y);
                }
            }
        }

        TetradLogger.getInstance().log("Finishing Fast Adjacency Search.");

        return this.graph;
    }


    /**
     * Sets the depth of the search.
     *
     * @param depth The maximum search depth. Must be -1 (unlimited) or >= 0.
     * @throws IllegalArgumentException if depth is less than -1
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    /**
     * Sets the knowledge for this object.
     *
     * @param knowledge The knowledge to set. Cannot be null.
     * @throws NullPointerException If knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    /**
     * Returns the number of conditional independence tests done in the course of search.
     *
     * @return This number.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Returns the map of node pairs to sepsets from the search.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets the external graph. Adjacencies not in this external graph will not be judged adjacent in the search
     * result.
     *
     * @param externalGraph This graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Returns the current value of the verbose flag.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose flag to control verbose output.
     *
     * @param verbose True, if verbose output is enabled. False otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void setStable(boolean stable) {

    }

    /**
     * Searches for adjacencies at depth 0 in the given list of nodes using the provided independence test and
     * adjacencies map.
     *
     * @param nodes       The list of nodes.
     * @param test        The independence test to use.
     * @param adjacencies The map of adjacencies.
     * @return True if there are free degrees in the graph after the search, false otherwise.
     */
    private boolean searchAtDepth0(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        Set<Node> empty = Collections.emptySet();
        for (int i = 0; i < nodes.size(); i++) {
            if (this.verbose) {
                if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));
            }

            Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {

                Node y = nodes.get(j);

                if (this.externalGraph != null) {
                    Node x2 = this.externalGraph.getNode(x.getName());
                    Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }


                IndependenceResult result;

                try {
                    this.numIndependenceTests++;
                    result = test.checkIndependence(x, y, empty);
                } catch (Exception e) {
                    result = new IndependenceResult(new IndependenceFact(x, y, empty), false, Double.NaN, Double.NaN);
                }

                boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());


                if (result.isIndependent() && noEdgeRequired) {
                    getSepsets().set(x, y, empty);

                    String message = LogUtilsSearch.independenceFact(x, y, empty) + " p = " +
                                     this.nf.format(result.getPValue());
                    TetradLogger.getInstance().log(message);

                    if (this.verbose) {
                        this.out.println(LogUtilsSearch.independenceFact(x, y, empty) + " p = " +
                                         this.nf.format(result.getPValue()));
                    }

                } else if (!forbiddenEdge(x, y)) {
                    adjacencies.get(x).add(y);
                    adjacencies.get(y).add(x);

                    String message = LogUtilsSearch.independenceFact(x, y, empty) + " p = " +
                                     this.nf.format(result.getPValue());
                    TetradLogger.getInstance().log(message);

                }
            }
        }

        return

                freeDegree(nodes, adjacencies)

                > 0;
    }

    /**
     * Calculates the free degree of a graph.
     *
     * @param nodes       The list of nodes in the graph.
     * @param adjacencies The map of adjacencies for each node.
     * @return The maximum free degree in the graph.
     */
    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    /**
     * Checks if a given edge between two nodes is forbidden based on background knowledge.
     *
     * @param x The first node.
     * @param y The second node.
     * @return True if the edge is forbidden, false otherwise.
     */
    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
            this.knowledge.isForbidden(name2, name1)) {
            String message = "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                             "forbidden by background knowledge.";
            TetradLogger.getInstance().log(message);

            return true;
        }

        return false;
    }

    /**
     * Search for adjacencies at a given depth in the given list of nodes using the provided independence test and
     * adjacencies map.
     *
     * @param nodes       The list of nodes.
     * @param test        The independence test to use.
     * @param adjacencies The map of adjacencies.
     * @param depth       The depth of the search.
     * @return True if there are free degrees in the graph after the search, false otherwise.
     */
    private boolean searchAtDepth(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        List<IndependenceFact> facts = new ArrayList<>();

        for (Node x : nodes) {
            if (this.verbose) {
                if (++count % 100 == 0) this.out.println("count " + count + " of " + nodes.size());
            }

            List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (Node y : adjx) {
                List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                List<Node> ppx = possibleParents(x, _adjx, this.knowledge);

                if (ppx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        Set<Node> condSet = GraphUtils.asSet(choice, ppx);

                        IndependenceFact fact = new IndependenceFact(x, y, condSet);
                        if (facts.contains(fact)) continue;
                        facts.add(fact);

                        boolean independent;

                        try {
                            this.numIndependenceTests++;
                            independent = test.checkIndependence(x, y, condSet).isIndependent();

                        } catch (Exception e) {
                            independent = false;
                        }

                        boolean noEdgeRequired =
                                this.knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);
                            getSepsets().set(x, y, condSet);

                            continue EDGE;
                        }
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    /**
     * Returns a list of possible parent nodes for a given node based on the adjacency list and knowledge.
     *
     * @param x         The node for which to find possible parent nodes.
     * @param adjx      The adjacency list of the given node.
     * @param knowledge The knowledge object containing background knowledge.
     * @return A list of possible parent nodes for the given node.
     */
    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       Knowledge knowledge) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * Returns whether a given node 'z' is a possible parent node of node 'x' based on the adjacency list and background
     * knowledge.
     *
     * @param z         The node to check if it is a possible parent of 'x'.
     * @param x         The node for which to find possible parent nodes.
     * @param knowledge The knowledge object containing background knowledge.
     * @return true if 'z' is a possible parent of 'x', false otherwise.
     */
    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }
}



