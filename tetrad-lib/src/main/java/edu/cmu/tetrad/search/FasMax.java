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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.log;

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
public class FasMax implements IFas {

    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The search nodes.
     */
    private List<Node> nodes;

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

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
    private TetradLogger logger = TetradLogger.getInstance();

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

    private int numIndependenceJudgements;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    private NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    private PrintStream out = System.out;
    private boolean sepsetsReturnEmptyIfNotFixed;
    private Map<NodePair, Double> ps = new HashMap<>();

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public FasMax(Graph initialGraph, IndependenceTest test) {
        this.test = test;
        this.nodes = test.getVariables();
    }

    public FasMax(IndependenceTest test) {
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

        sepset = new SepsetMap();
        sepset.setReturnEmptyIfNotSet(sepsetsReturnEmptyIfNotFixed);

        for (int i = 0; i < 1; i++) {
            graph = new EdgeListGraph(nodes);
            graph = GraphUtils.completeGraph(graph);

            int d = 0;
            boolean more;

            do {
                more = adjust(graph, d++, test);
            } while (more);

            final OrientCollidersMaxP orientCollidersMaxP = new OrientCollidersMaxP(test);
            orientCollidersMaxP.setConflictRule(PcAll.ConflictRule.PRIORITY);
            orientCollidersMaxP.setDepth(depth);
            orientCollidersMaxP.orient(graph);

            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            meekRules.orientImplied(graph);
        }

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return graph;
    }

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

    private boolean adjust(Graph graph, int depth, IndependenceTest test) {

        for (int i = 0; i < graph.getNumNodes(); i++) {
            for (int j = i + 1; j < graph.getNumNodes(); j++) {
                Node x = graph.getNodes().get(i);
                Node y = graph.getNodes().get(j);

                boolean existsSepset = existsSepset(x, y, depth, graph, test);

                if (existsSepset) {
                    graph.removeEdge(x, y);
                } else {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        return freeDegree(nodes, graph) > depth;
    }

    private static class ScoredS {
        List<Node> S;
        double p;

        public ScoredS(List<Node> S, double p) {
            this.S = S;
            this.p = p;
        }
    }

    private int freeDegree(List<Node> nodes, Graph graph) {
        int max = 0;

        for (Node x : nodes) {
            List<Node> opposites = graph.getAdjacentNodes(x);

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

    private static List<Node> possibleParents(Node x, List<Node> adjx,
                                              Graph reference) {
        List<Node> possibleParents = new LinkedList<>();

        for (Node z : adjx) {
            Edge edge = reference.getEdge(x, z);
            if (edge != null && !edge.pointsTowards(z)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private static boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x);// && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgments() {
        return numFalseDependenceJudgments;
    }

    public int getNumDependenceJudgments() {
        return numDependenceJudgement;
    }

    public SepsetMap getSepsets() {
        return sepset;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }

    @Override
    public Graph search(List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public List<Node> getNodes() {
        return test.getVariables();
    }

    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return null;
    }

    public int getNumIndependenceJudgements() {
        return numIndependenceJudgements;
    }

    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }

    public boolean isSepsetsReturnEmptyIfNotFixed() {
        return sepsetsReturnEmptyIfNotFixed;
    }

    public void setSepsetsReturnEmptyIfNotFixed(boolean sepsetsReturnEmptyIfNotFixed) {
        this.sepsetsReturnEmptyIfNotFixed = sepsetsReturnEmptyIfNotFixed;
    }


    public static synchronized boolean existsSepset(Node a, Node c, int depth, Graph graph, IndependenceTest test) {
        System.out.println("--");
        System.out.println("Calculating max sum setpset for " + a + " --- " + c + " depth = " + depth);

        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);

        adja.remove(c);
        adjc.remove(a);

        System.out.println("adja = " + adja);
        System.out.println("adjc = " + adjc);

        List<Node> S = null;

        depth = depth == -1 ? 1000 : depth;

        List<List<Node>> adj = new ArrayList<>();
        adj.add(adja);
        adj.add(adjc);

        Set<Set<Node>> allS = new HashSet<>();
        List<Double> pValues = new ArrayList<>();

        for (List<Node> _adj : adj) {
            DepthChoiceGenerator cg1 = new DepthChoiceGenerator(_adj.size(), depth);
            int[] comb2;

            while ((comb2 = cg1.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> s = GraphUtils.asList(comb2, _adj);

                if (allS.contains(new HashSet<>(s))) continue;
                allS.add(new HashSet<>(s));

                test.isIndependent(a, c, s);
                double _p = test.getPValue();

                pValues.add(_p);
            }
        }

        return existsSepsetFromList(pValues, test.getAlpha());
    }

    public static boolean existsSepsetFromList(List<Double> pValues, double alpha) {
        Collections.sort(pValues);

        double avg = 0;

        if (pValues.size() == 1) {
            avg = log(pValues.get(0));
        } else if (pValues.size() > 1) {
            Double max1 = log(pValues.get(pValues.size() - 1));
            Double next1 = log(pValues.get(pValues.size() - 2));
            avg = (next1 + max1) / 2.;
        }

//        if (pValues.size() == 1) {
//            avg = pValues.get(0);
//        } else if (pValues.size() > 1) {
//            Double next1 = pValues.get(pValues.size() - 1);
//            avg = (next1);
//        }

        return log(avg) > log(alpha);
    }


    private IKnowledge forbiddenKnowledge(Graph graph) {
        IKnowledge knowledge = new Knowledge2(graph.getNodeNames());

        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) continue;
            ;

            Node n1 = Edges.getDirectedEdgeTail(edge);
            Node n2 = Edges.getDirectedEdgeHead(edge);

            if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                continue;
            }

            knowledge.setForbidden(n1.getName(), n2.getName());
        }

        return knowledge;
    }

}

