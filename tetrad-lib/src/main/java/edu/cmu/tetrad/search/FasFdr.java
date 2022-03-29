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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Implements the "fast adjacency search" used in several causal algorithm in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithm, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 * <p>
 * This variant does each depth twice, gathering up the p values in the first round, using FDR to estimate a cutoff
 * for acceptance, and rerunning using the specified cutoff.
 *
 * @author Joseph Ramsey.
 */
public class FasFdr implements IFas {

    private final Matrix cov;

    private final double alpha;
    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private final Graph graph;

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
     * The true graph, for purposes of comparison. Temporary.
     */
    private Graph trueGraph;

    /**
     * The number of false dependence judgements, judged from the true graph using d-separation. Temporary.
     */
    private int numFalseDependenceJudgments;

    /**
     * The number of dependence judgements. Temporary.
     */
    private int numDependenceJudgement;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    /**
     * True if this is being run by FCI--need to skip the knowledge forbid step.
     */
    private final boolean fci = false;

    /**
     * The depth 0 graph, specified initially.
     */
    private Graph externalGraph;

//    private List<Double> pValues = new ArrayList<Double>();

    private final NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private final List pValueList = new ArrayList();

    private PrintStream out = System.out;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public FasFdr(final IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
        this.alpha = test.getAlpha();
        this.cov = test.getCov().getMatrix();
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
        this.graph.removeEdges(this.graph.getEdges());

        this.sepset = new SepsetMap();

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }


        final List<Node> nodes = this.graph.getNodes();
        Map<Node, Set<Node>> adjacencies = emptyGraph(nodes);

        searchICov(nodes, this.test, adjacencies, true);
        searchiCovAll(nodes, this.test, adjacencies);

        for (int d = 0; d <= _depth; d++) {
            searchAtDepth(nodes, this.test, adjacencies, d);

            if (!(freeDegree(nodes, adjacencies) > this.depth)) {
                break;
            }
        }

        this.pValueList.clear();

        for (int d = 0; d <= _depth; d++) {
            this.test.setAlpha(this.alpha);
            final Map<Node, Set<Node>> _adjacencies = copy(adjacencies);
            searchAtDepth(nodes, this.test, adjacencies, d);
            final double cutoff = StatUtils.fdrCutoff(this.test.getAlpha(), this.pValueList, false);
            adjacencies = _adjacencies;
            this.test.setAlpha(cutoff);
            final boolean more = searchAtDepth(nodes, this.test, adjacencies, d);

            if (!more) {
                break;
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    this.graph.addUndirectedEdge(x, y);
                }
            }
        }

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return this.graph;
    }

    private Map<Node, Set<Node>> emptyGraph(final List<Node> nodes) {
        final Map<Node, Set<Node>> adjacencies = new HashMap<>();

        for (final Node node : nodes) {
            adjacencies.put(node, new TreeSet<Node>());
        }
        return adjacencies;
    }

    private void searchiCovAll(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        boolean removed;

        do {
            removed = false;

            for (final Node x : nodes) {
                final List<Node> adjx = new ArrayList<>(adjacencies.get(x));

                for (final Node y : adjx) {
                    if (!adjacencies.get(x).contains(y)) continue;
                    final List<Node> adjy = new ArrayList<>(adjacencies.get(y));
                    final List<Node> adj = new ArrayList<>(adjx);
                    for (final Node node : adjy) if (!adj.contains(node)) adj.add(node);
                    removed = removed || searchICov(adj, test, adjacencies, false);
                }
            }
        } while (removed);
    }


    private Map<Node, Set<Node>> completeGraph(final List<Node> nodes) {
        final Map<Node, Set<Node>> adjacencies = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            adjacencies.put(nodes.get(i), new HashSet<Node>());
        }

        for (int i = 0; i < nodes.size(); i++) {
            final Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {
                final Node y = nodes.get(j);
                adjacencies.get(x).add(y);
                adjacencies.get(y).add(x);
            }
        }

        return adjacencies;
    }

    private void searchiCovAdj(final List<Node> nodes, final List<Node> graphNodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        boolean removed;

        do {
            removed = false;

            for (final Node x : nodes) {
                final List<Node> adj = new ArrayList<>(adjacencies.get(x));
                adj.add(x);
                removed = removed || searchICov(adj, test, adjacencies, false);
            }
        } while (removed);
    }


    private Map<Node, Set<Node>> copy(final Map<Node, Set<Node>> adjacencies) {
        final Map<Node, Set<Node>> copy = new HashMap<>();

        for (final Node node : adjacencies.keySet()) {
            copy.put(node, new HashSet<>(adjacencies.get(node)));
        }

        return copy;
    }

//    public Map<Node, Set<Node>> searchMapOnly() {
//        this.logger.log("info", "Starting Fast Adjacency Search.");
//        graph.removeEdges(graph.getEdges());
//
//        sepset = new SepsetMap();
//
//        int _depth = depth;
//
//        if (_depth == -1) {
//            _depth = 1000;
//        }
//
//
//        pValueList.clear();
//
//        Map<Node, Set<Node>> adjacencies = new HashMap<Node, Set<Node>>();
//        List<Node> nodes = graph.getNodes();
//
//        Map<Node, Set<Node>> _adjacencies = copy(adjacencies);
//        test.setAlternativePenalty(alpha);
//        searchICov(nodes, test, adjacencies);
//        double cutoff = StatUtils.fdr(test.getAlternativePenalty(), pValueList, false);
//        test.setAlternativePenalty(cutoff);
//        adjacencies = _adjacencies;
//        searchICov(nodes, test, adjacencies);
//
////        adjacencies = new HashMap<Node, Set<Node>>();
////        nodes = graph.getNodes();
////
////        for (Node node : nodes) {
////            adjacencies.put(node, new TreeSet<Node>());
////        }
////
////        test.setAlternativePenalty(alpha);
////
////        searchAtDepth0(nodes, test, adjacencies);
////
////        cutoff = StatUtils.fdr(test.getAlternativePenalty(), pValueList, false);
////
////        test.setAlternativePenalty(cutoff);
////
////        searchAtDepth0(nodes, test, adjacencies);
//
//        for (int d = 0; d <= _depth; d++) {
//            boolean more;
//
//            test.setAlternativePenalty(alpha);
//
//            searchAtDepth(nodes, test, adjacencies, d);
//
//            cutoff = StatUtils.fdr(test.getAlternativePenalty(), pValueList, false);
//
//            test.setAlternativePenalty(cutoff);
//
//            more = searchAtDepth(nodes, test, adjacencies, d);
//
//            if (!more) {
//                break;
//            }
//        }
//
//        return adjacencies;
//    }

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

    private boolean searchICov(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies,
                               final boolean addDependencies) {
        if (nodes.size() < 2) return false;

        boolean removed = false;

        final int[] n = new int[nodes.size()];
        final List<Node> variables = test.getVariables();

        for (int i = 0; i < nodes.size(); i++) {
            n[i] = variables.indexOf(nodes.get(i));
        }

        final Matrix inv = this.cov.getSelection(n, n).inverse();
        final int sampleSize = test.getCov().getSampleSize();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                final double r = -inv.get(i, j) / sqrt(inv.get(i, i) * inv.get(j, j));

                final double fisherZ = sqrt(sampleSize - (nodes.size() - 2) - 3.0) *
                        0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));
                final double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fisherZ)));

                final boolean independent = pvalue > test.getAlpha();

                if (addDependencies) {
                    if (independent) {
                        final List<Node> theRest = new ArrayList<>();

                        for (final Node node : nodes) {
                            if (node != x && node != y) theRest.add(node);
                        }

//                        for (Node node : graphNodes) {
//                            if (!nodes.contains(node)) theRest.add(node);
//                        }

                        getSepsets().set(x, y, theRest);

                        if (this.verbose) {
                            this.out.println(SearchLogUtils.independenceFactMsg(x, y, theRest, test.getPValue()));
//                            out.println(x + " _||_ " + y + " | the rest" + " p = " +
//                                    nf.format(test.getScore()));
                        }

                        removed = true;
                    } else if (!forbiddenEdge(x, y)) {
                        adjacencies.get(x).add(y);
                        adjacencies.get(y).add(x);

//                    if (verbose) {
//                        out.println(SearchLogUtils.dependenceFactMsg(x, y, empty) + " p = " +
//                                nf.format(test.getScore()));
//                    }
                    }
                } else {
                    if (independent) {
                        if (!adjacencies.get(x).contains(y)) continue;

                        final List<Node> theRest = new ArrayList<>();

                        for (final Node node : nodes) {
                            if (node != x && node != y) theRest.add(node);
                        }

                        adjacencies.get(x).remove(y);
                        adjacencies.get(y).remove(x);

                        getSepsets().set(x, y, theRest);

                        if (this.verbose) {
                            this.out.println(x + " _||_ " + y + " | the rest" + " p = " +
                                    this.nf.format(test.getPValue()));
                        }

                        removed = true;
                    }
                }
            }
        }

        return removed;
    }

    private boolean searchAtDepth0(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        final List<Node> empty = Collections.emptyList();
        for (int i = 0; i < nodes.size(); i++) {
            if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));

            final Node x = nodes.get(i);

//            if (missingCol(test.getContinuousData(), x)) {
//                continue;
//            }

            for (int j = i + 1; j < nodes.size(); j++) {

                final Node y = nodes.get(j);

//                if (missingCol(test.getContinuousData(), y)) {
//                    continue;
//                }

                if (this.externalGraph != null) {
                    final Node x2 = this.externalGraph.getNode(x.getName());
                    final Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }


                boolean independent;

                try {
                    independent = test.isIndependent(x, y, empty);
                    this.pValueList.add(test.getPValue());
                } catch (final Exception e) {
                    e.printStackTrace();
                    independent = false;
                }

                this.numIndependenceTests++;

                final boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());


                if (independent && noEdgeRequired) {
                    getSepsets().set(x, y, empty);

                    if (this.verbose) {
                        this.out.println(SearchLogUtils.independenceFact(x, y, empty) + " p = " +
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

    // Returns true just in case there are no defined values in the column.
    private boolean missingCol(final DataModel data, final Node x) {
        return false;
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
            this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

    private boolean searchAtDepth(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies, final int depth) {
        int numRemoved = 0;
        int count = 0;

        for (final Node x : nodes) {
            if (++count % 100 == 0) this.out.println("count " + count + " of " + nodes.size());

            final List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (final Node y : adjx) {
                final List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                final List<Node> ppx = possibleParents(x, _adjx, this.knowledge);

                if (ppx.size() >= depth) {
                    final ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        final List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent;

                        try {
                            independent = test.isIndependent(x, y, condSet);
                            this.pValueList.add(test.getPValue());
                        } catch (final Exception e) {
                            independent = false;
                        }

                        final boolean noEdgeRequired =
                                this.knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);
                            numRemoved++;
                            getSepsets().set(x, y, condSet);

                            if (this.verbose) {
                                this.out.println(SearchLogUtils.independenceFact(x, y, condSet) + " p = " +
                                        this.nf.format(test.getPValue()));
                            }
                            continue EDGE;
                        }
//                        else {
//                            if (verbose) {
//                                out.println("Dependence: " + SearchLogUtils.independenceFact(x, y, condSet) + " p = " +
//                                        nf.format(test.getScore()));
//                            }
//                        }

                    }
                }
            }
        }

//        out.println("Num removed = " + numRemoved);
//        return numRemoved > 0;

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(final Node x, final List<Node> adjx,
                                       final IKnowledge knowledge) {
        final List<Node> possibleParents = new LinkedList<>();
        final String _x = x.getName();

        for (final Node z : adjx) {
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
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgments() {
        return this.numFalseDependenceJudgments;
    }

    public int getNumDependenceJudgments() {
        return this.numDependenceJudgement;
    }

    public SepsetMap getSepsets() {
        return this.sepset;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
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
        return null;
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


