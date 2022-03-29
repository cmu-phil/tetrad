///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Implements the "fast adjacency search" used in several causal algorithm in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithm, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Joseph Ramsey.
 */
public class FasOrig2 implements IFas {

    /**
     * The search nodes.
     */
    private final List<Node> nodes;

    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
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
     * The logger, by default the empty logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The number of dependence judgements. Temporary.
     */
    private int numDependenceJudgement;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    /**
     * The depth 0 graph, specified initially.
     */
    private Graph externalGraph;

    private final NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;

    private PrintStream out = System.out;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public FasOrig2(final Graph externalGraph, final IndependenceTest test) {
        if (externalGraph != null) {
            this.externalGraph = new EdgeListGraph(externalGraph);
        }
        this.test = test;
        this.nodes = test.getVariables();
    }

    public FasOrig2(final IndependenceTest test) {
        this.test = test;
        this.nodes = test.getVariables();
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
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");

        this.sepset = new SepsetMap();
//        sepset.setReturnEmptyIfNotSet(sepsetsReturnEmptyIfNotFixed);

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        final Map<Node, Set<Node>> adjacencies = new HashMap<>();

        for (final Node node : this.nodes) {
            adjacencies.put(node, new TreeSet<>());
        }

        for (int d = 0; d <= _depth; d++) {
            final boolean more;

            if (d == 0) {
                more = searchAtDepth0(this.nodes, this.test, adjacencies);
            } else {
                more = searchAtDepth(this.nodes, this.test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        final Graph graph = new EdgeListGraph(this.nodes);

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = i + 1; j < this.nodes.size(); j++) {
                final Node x = this.nodes.get(i);
                final Node y = this.nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return graph;
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(final int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    private boolean searchAtDepth0(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        final List<Node> empty = Collections.emptyList();
        for (int i = 0; i < nodes.size(); i++) {
            if (this.verbose) {
                if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {

                final Node y = nodes.get(j);

                if (this.externalGraph != null) {
                    final Node x2 = this.externalGraph.getNode(x.getName());
                    final Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }

                boolean independent;

                try {
                    this.numIndependenceTests++;
                    independent = test.isIndependent(x, y, empty);
                } catch (final Exception e) {
                    e.printStackTrace();
                    independent = false;
                }

                if (!independent) {
                    this.numDependenceJudgement++;
                }

                final boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());


                if (independent && noEdgeRequired) {
//                    if (!getSepsets().isReturnEmptyIfNotSet()) {
                    getSepsets().set(x, y, empty);
//                    }

                    if (this.verbose) {
                        TetradLogger.getInstance().forceLogMessage(
                                SearchLogUtils.independenceFact(x, y, empty) + " p-value = " +
                                        this.nf.format(test.getPValue()));
                        this.out.println(SearchLogUtils.independenceFact(x, y, empty) + " p-value = " +
                                this.nf.format(test.getPValue()));
                    }

                } else if (!forbiddenEdge(x, y)) {
                    adjacencies.get(x).add(y);
                    adjacencies.get(y).add(x);
                }
            }
        }

        return freeDegree(nodes, adjacencies) > 0;
    }

    private int freeDegree(final List<Node> nodes, final Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (final Node x : nodes) {
            final Set<Node> opposites = adjacencies.get(x);

            for (final Node y : opposites) {
                final Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    private boolean forbiddenEdge(final Node x, final Node y) {
        final String name1 = x.getName();
        final String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
                this.knowledge.isForbidden(name2, name1)) {
            System.out.println(Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

    private boolean searchAtDepth(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies, final int depth) {
        int count = 0;

        for (final Node x : nodes) {
            if (this.verbose) {
                if (++count % 100 == 0) this.out.println("count " + count + " of " + nodes.size());
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (final Node y : adjx) {
                final List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                final List<Node> ppx = possibleParents(x, _adjx, this.knowledge, y);

                if (ppx.size() >= depth) {
                    final ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        final List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent;

                        try {
                            this.numIndependenceTests++;
                            independent = test.isIndependent(x, y, condSet);
                        } catch (final Exception e) {
                            independent = false;
                        }

                        if (!independent) {
                            this.numDependenceJudgement++;
                        }

                        final boolean noEdgeRequired =
                                this.knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);

                            getSepsets().set(x, y, condSet);

                            if (this.verbose) {
                                TetradLogger.getInstance().forceLogMessage(SearchLogUtils.independenceFact(x, y, condSet) +
                                        " score = " + this.nf.format(test.getScore()));
                                this.out.println(SearchLogUtils.independenceFactMsg(x, y, condSet, test.getPValue()));
                            }

                            continue EDGE;
                        }
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(final Node x, final List<Node> adjx,
                                       final IKnowledge knowledge, final Node y) {
        final List<Node> possibleParents = new LinkedList<>();
        final String _x = x.getName();

        for (final Node z : adjx) {
            if (z == y) continue;
            final String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(final String z, final String x, final IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    public void setTrueGraph(final Graph trueGraph) {
    }

    public int getNumDependenceJudgments() {
        return this.numDependenceJudgement;
    }

    public SepsetMap getSepsets() {
        return this.sepset;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }

    @Override
    public Graph search(final List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public List<Node> getNodes() {
        return this.test.getVariables();
    }

    @Override
    public List<Triple> getAmbiguousTriples(final Node node) {
        return null;
    }

    @Override
    public void setOut(final PrintStream out) {
        this.out = out;
    }

}

