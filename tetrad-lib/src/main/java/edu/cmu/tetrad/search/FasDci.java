///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements a modified version of the the "fast adjacency search" for use in the Distributed Causal Inference (DCI)
 * algorithm. This version accepts an independence test for a particular dataset and a supergraph containing varialbes
 * from each dataset. At a given stage, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset of
 * size d either of adj(X) or of adj(Y), where d is the depth of the search. This procedure  is performed for each pair
 * of adjacent edges in the graph that are jointly measured with S in the dataset and for d = 0, 1, 2, ..., d1, where d1
 * is either the maximum depth or else the first such depth at which no edges can be removed. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Robert Tillman.
 */
public class FasDci {

    /**
     * The search graph over every variable included in a dataset. It is assumed going in that all of the true
     * adjacencies of x are in this graph for every node x. It is hoped (i.e. true in the large sample limit) that true
     * adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The variables in the dataset.
     */
    private Set<Node> variables = new HashSet<Node>();

    /**
     * The independence tests for each dataset. This should be appropriate to the data.
     */
    private IndependenceTest independenceTest;

    /**
     * Specification of which edges are forbidden or required. NOTE: to be implemented later
     */
    private IKnowledge knowledge = new Knowledge2();

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
     * The method used to resolve independencies.
     */
    private ResolveSepsets.Method method;

    /**
     * If resolving independencies, the sets of variables in each "Marginal" dataset
     */
    private List<Set<Node>> marginalVars;

    /**
     * If resolving independenceis, the set of independence tests for other datasets
     */
    private List<IndependenceTest> independenceTests;

    /**
     * Independencies known prior to the search
     */
    private SepsetMapDci knownIndependencies;

    /**
     * Associations known prior to the search
     */
    private SepsetMapDci knownAssociations;

    /**
     * The logger, by default the empty logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();

//    private List<Double> pValues = new ArrayList<Double>();

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch for DCI.
     */
    public FasDci(Graph graph, IndependenceTest independenceTest) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

    /**
     * Constructs a new FastAdjacencySearch for DCI with independence test pooling to resolve inconsistencies.
     */
    public FasDci(Graph graph, IndependenceTest independenceTest,
                  ResolveSepsets.Method method, List<Set<Node>> marginalVars,
                  List<IndependenceTest> independenceTests,
                  SepsetMapDci knownIndependencies, SepsetMapDci knownAssociations) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
        this.method = method;
        this.marginalVars = marginalVars;
        this.independenceTests = independenceTests;
        this.knownIndependencies = knownIndependencies;
        this.knownAssociations = knownAssociations;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public SepsetMapDci search() {
        this.logger.log("info", "Starting Fast Adjacency Search (DCI).");
        // Remove edges forbidden both ways.
        Set<Edge> edges = graph.getEdges();

//        logger.log("info", "Edges: " + edges);

        for (Edge _edge : edges) {
            String name1 = _edge.getNode1().getName();
            String name2 = _edge.getNode2().getName();

            if (knowledge.isForbidden(name1, name2) &&
                    knowledge.isForbidden(name2, name1)) {
                graph.removeEdge(_edge);

                this.logger.log("edgeRemoved", "Removed " + _edge + " because it was " +
                        "forbidden by background knowledge.");

            }
        }

//        this.logger.info("Depth = " + ((depth == Integer
//               .MAX_VALUE) ? "Unlimited" : Integer.toString(depth)));

        SepsetMapDci sepset = new SepsetMapDci();

        int _depth = depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        for (int d = 0; d <= _depth; d++) {
            boolean more = searchAtDepth(graph, independenceTest, new Knowledge2(),
                    sepset, d);

            if (!more) {
                break;
            }
        }

//        verifySepsetIntegrity(sepset);

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return sepset;
    }

//    private void verifySepsetIntegrity(SepsetMap sepset) {
//        for (Node x : graph.getNodes()) {
//            for (Node y : graph.getNodes()) {
//                if (x == y) {
//                    continue;
//                }
//
//                if (graph.isAdjacentTo(y, x) && sepset.get(x, y) != null) {
//                    System.out.println(x + " " + y + " integrity check failed.");
//                }
//            }
//        }
//    }


    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    /**
     * Removes from the list of nodes any that cannot be parents of x given the background knowledge.
     */
    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       IKnowledge knowledge) {
        List<Node> possibleParents = new LinkedList<Node>();
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
     * @return true just in case z is a possible parent of x, in the sense that edges are not forbidden from z to x, and
     * edges are not required from either x to z, according to background knowledge.
     */
    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Performs one depth step of the adjacency search.
     *
     * @param graph            The search graph. This will be modified.
     * @param independenceTest The independence test for the dataset.
     * @param knowledge        Background knowledge.
     * @param sepset           A mapping from {x, y} node sets to separating sets.
     * @param depth            The depth at which this step will be done.
     * @return true if there are more changes possible, false if not.
     */
    private boolean searchAtDepth(Graph graph, IndependenceTest independenceTest,
                                  IKnowledge knowledge, SepsetMapDci sepset, int depth) {

        boolean more = false;

        for (Node x : variables) {
            List<Node> b = new LinkedList<Node>();
            for (Node node : graph.getAdjacentNodes(x)) {
                if (variables.contains(node)) {
                    b.add(node);
                }
            }

            nextEdge:
            for (Node y : b) {

                // This is the standard algorithm, without the v1 bias.
                List<Node> adjx = new ArrayList<Node>(b);
                adjx.remove(y);
                List<Node> ppx = possibleParents(x, adjx, knowledge);

//                System.out.println("Possible parents for removing " + x + " --- " + y + " are " + ppx);

                boolean noEdgeRequired =
                        knowledge.noEdgeRequired(x.getName(), y.getName());

                if (ppx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent = false;
                        boolean known = false;
                        if (knownIndependencies != null && knownIndependencies.get(x, y) != null) {
                            for (List<Node> set : knownIndependencies.getSet(x, y)) {
                                if (set.containsAll(condSet) && set.size() == condSet.size()) {
                                    independent = true;
                                    known = true;
                                    break;
                                }
                            }
                        }
                        if (knownAssociations != null && knownAssociations.get(x, y) != null) {
                            for (List<Node> set : knownAssociations.getSet(x, y)) {
                                if (set.containsAll(condSet) && set.size() == condSet.size()) {
                                    independent = false;
                                    known = true;
                                    break;
                                }
                            }
                        }
                        if (!known) {
                            independent = independenceTest.isIndependent(x, y, condSet);
                            if (method != null) {
                                List<IndependenceTest> testsWithVars = new ArrayList<IndependenceTest>();
                                for (int k = 0; k < marginalVars.size(); k++) {
                                    Set<Node> marginalSet = marginalVars.get(k);
                                    if (marginalSet.contains(x) && marginalSet.contains(y) &&
                                            marginalSet.containsAll(condSet)) {
                                        testsWithVars.add(independenceTests.get(k));
                                    }
                                }
                                boolean inconsistency = false;
                                for (IndependenceTest testWithVars : testsWithVars) {
                                    if (testWithVars.isIndependent(x, y, condSet) != independent) {
                                        inconsistency = true;
                                        break;
                                    }
                                }
                                if (inconsistency) {
                                    independent = ResolveSepsets.isIndependentPooled(method,
                                            testsWithVars, x, y, condSet);
                                }
                            }
                            numIndependenceTests++;
                        }

                        if (independent && noEdgeRequired) {
//                            Edge edge = graph.getEdge(x, y);
                            graph.removeEdge(x, y);
                            sepset.set(x, y, new LinkedList<Node>(condSet));
                            continue nextEdge;
                        }
                    }
                }
            }

            List<Node> currentAdjNodes = new ArrayList();
            for (Node node : graph.getAdjacentNodes(x)) {
                if (variables.contains(node)) {
                    currentAdjNodes.add(node);
                }
            }
            if (currentAdjNodes.size() - 1 > depth) {
                more = true;
            }
        }

//        System.out.println("more = " + more);

        return more;
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }
}




