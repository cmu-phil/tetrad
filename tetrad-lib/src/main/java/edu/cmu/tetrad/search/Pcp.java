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
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey.
 */
public class Pcp implements GraphSearch {

    /**
     * The independence test used for the PC search.g
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Sepset information accumulated in the search.
     */
    private SepsetMap sepsets;

    /**
     * The maximum number of nodes conditioned on in the search. The default it 1000.
     */
    private int depth = 1000;

    /**
     * The graph that's constructed during the search.
     */
    private Graph graph;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * In an enumeration of triple types, these are the collider triples.
     */
    private Set<Triple> unshieldedColliders;

    /**
     * The number of indepdendence tests in the last search.
     */
    private int numIndependenceTests;

    /**
     * The true graph, for purposes of comparison. Temporary.
     */
    private Graph trueGraph;

    /**
     * The number of false dependence judgements from FAS, judging from the true graph, if set. Temporary.
     */
    private int numFalseDependenceJudgements;

    /**
     * The number of dependence judgements from FAS. Temporary.
     */
    private int numDependenceJudgements;

    /**
     * The initial graph for the Fast Adjacency Search, or null if there is none.
     */
    private Graph initialGraph = null;

    private boolean verbose = false;

    private boolean fdr = false;

    // P-values from adjacency search
    private Map<NodePair, List<Double>> P1 = new HashMap<>();

    // P-values from collider orientation
    private Map<NodePair, Double> P2 = new HashMap<>();

    // Number of p-values used.
    private Map<NodePair, Set<Object>> I = new HashMap<>();

    //
    private Set<NodePair> A;


    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public Pcp(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true iff edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * @param aggressivelyPreventCycles Set to true just in case edges will not be addeds if they would create cycles.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * @return the independence test being used in the search.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return the sepset map from the most recent search. Non-null after the first call to <code>search()</code>.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * @return the current depth of search--that is, the maximum number of conditioning nodes for any conditional
     * independence checked.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth of the search--that is, the maximum number of conditioning nodes for any conditional independence
     * checked.
     *
     * @param depth The depth of the search. The default is 1000. A value of -1 may be used to indicate that the depth
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used, due to a bug on multi-core
     *              machines.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth > 1000) {
            throw new IllegalArgumentException("Depth must be <= 1000.");
        }

        this.depth = depth;
    }

    /**
     * Runs PC starting with a complete graph over all nodes of the given conditional independence test, using the given
     * independence test and knowledge and returns the resultant graph. The returned graph will be a pattern if the
     * independence information is consistent with the hypothesis that there are no latent common causes. It may,
     * however, contain cycles or bidirected edges if this assumption is not born out, either due to the actual presence
     * of latent common causes, or due to statistical errors in conditional independence judgments.
     */
    @Override
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a commplete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a pattern if the independence information
     * is consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p>
     * All of the given nodes must be in the domatein of the given conditional independence test.
     */
    public Graph search(List<Node> nodes) {
        FasStablePcp fas = null;

        if (initialGraph == null) {
            fas = new FasStablePcp(getIndependenceTest());
        } else {
            fas = new FasStablePcp(initialGraph, getIndependenceTest());
        }
        fas.setVerbose(verbose);
        return search(fas, nodes);
    }

    public Graph search(FasStablePcp fas, List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");


//        this.logger.log("info", "Variables " + independenceTest.getVariable());

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);

        graph = fas.search();
        sepsets = fas.getSepsets();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (Edges.isUndirectedEdge(edge)) {
                graph.removeEdge(edge);
                graph.addNondirectedEdge(x, y);
            }
        }

        P1 = fas.getP1();
        I = fas.getI();
        A = fas.getA();

        this.numIndependenceTests = fas.getNumIndependenceTests();
        this.numFalseDependenceJudgements = fas.getNumFalseDependenceJudgments();
        this.numDependenceJudgements = fas.getNumDependenceJudgments();

//        enumerateTriples();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);
        orientCollidersUsingSepsets(this.sepsets, knowledge, graph, verbose, false);

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                graph.removeEdge(edge.getNode1(), edge.getNode2());
                graph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        MeekRulesPcp rules = new MeekRulesPcp(P1, P2, A);
        rules.setAggressivelyPreventCycles(false);
        rules.setKnowledge(knowledge);
        rules.setUndirectUnforcedEdges(false);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        for (Edge edge : graph.getEdges()) {
            Edge edge2 = new Edge(edge);

            if (edge2.getEndpoint1() == Endpoint.CIRCLE) {
                edge2.setEndpoint1(Endpoint.TAIL);
            }

            if (edge2.getEndpoint2() == Endpoint.CIRCLE) {
                edge2.setEndpoint2(Endpoint.TAIL);
            }

            graph.removeEdge(edge);
            graph.addEdge(edge2);
        }

        Set<Object> unionI = new HashSet<>();
        for (NodePair key : I.keySet()) unionI.addAll(I.get(key));

        int m = unionI.size();

        int r = P2.keySet().size();

        double alpha = fdr(independenceTest.getAlpha(), m, r);

//        double alpha = independenceTest.getAlpha() * 2;

        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            if (getP(a, b, P2) > alpha) graph.removeEdge(edge);
        }

        for (NodePair key : P2.keySet()) {
            final double x = P2.get(key);
            System.out.println(max(x));
        }

        return graph;
    }

    /**
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * @return the set of unshielded colliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedColliders() {
        return unshieldedColliders;
    }

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    //===============================PRIVATE METHODS=======================//

//    private void enumerateTriples() {
//        this.unshieldedColliders = new HashSet<Triple>();
//        this.unshieldedNoncolliders = new HashSet<Triple>();
//
//        for (Node y : graph.getNodes()) {
//            List<Node> adj = graph.getAdjacentNodes(y);
//
//            if (adj.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
//            int[] choice;
//
//            while ((choice = gen.next()) != null) {
//                Node x = adj.get(choice[0]);
//                Node z = adj.get(choice[1]);
//
//                List<Node> nodes = this.sepsets.get(x, z);
//
//                // Note that checking adj(x, z) does not suffice when knowledge
//                // has been specified.
//                if (nodes == null) {
//                    continue;
//                }
//
//                if (nodes.contains(y)) {
//                    getUnshieldedNoncolliders().add(new Triple(x, y, z));
//                } else {
//                    getUnshieldedColliders().add(new Triple(x, y, z));
//                }
//            }
//        }
//    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgements() {
        return numFalseDependenceJudgements;
    }

    public int getNumDependenceJudgements() {
        return numDependenceJudgements;
    }

    public List<Node> getNodes() {
        return graph.getNodes();
    }

    public List<Triple> getColliders(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getNoncolliders(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getAmbiguousTriples(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getUnderlineTriples(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getDottedUnderlineTriples(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * True iff the algorithm should be run with False Discovery Rate tests.
     */
    public boolean isFdr() {
        return fdr;
    }

    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }

    public void setEnforcePattern(boolean enforcePattern) {
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public List<Triple> orientCollidersUsingSepsets(SepsetMap set, IKnowledge knowledge, Graph graph, boolean verbose,
                                                    boolean enforcePattern) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        List<Triple> colliders = new ArrayList<>();

        List<Node> nodes = graph.getNodes();

        Map<NodePair, List<Double>> Pp = new HashMap<>();

        for (Node c : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(c);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node b = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, b)) {
                    continue;
                }

                List<Node> sepset = set.get(a, b);

                //I think the null check needs to be here --AJ
                if (sepset != null && !sepset.contains(c) &&
                        isArrowpointAllowed(a, c, knowledge) &&
                        isArrowpointAllowed(b, c, knowledge)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + c + ", " + b + "> sepset = " + sepset);
                    }

                    if (enforcePattern) {
                        if (graph.getEndpoint(c, a) == Endpoint.ARROW || graph.getEndpoint(c, b) == Endpoint.ARROW)
                            continue;
                    }

//                    graph.setEndpoint(a, dd, Endpoint.ARROW);
//                    graph.setEndpoint(b, dd, Endpoint.ARROW);

                    graph.removeEdge(a, c);
                    graph.removeEdge(b, c);

                    graph.addDirectedEdge(a, c);
                    graph.addDirectedEdge(b, c);

                    colliders.add(new Triple(a, c, b));
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, c, b, sepset));

                    // Now add all p-values to Ppp that would have been considered by CPC
                    List<Double> Ppp = getPpp(a, b, c, graph);

                    Pp.computeIfAbsent(new NodePair(b, c), k -> new ArrayList<>());
                    Pp.computeIfAbsent(new NodePair(a, c), k -> new ArrayList<>());

                    Pp.get(new NodePair(b, c)).add(max(max(getP1(c, a)), max(Ppp)));
                    Pp.get(new NodePair(a, c)).add(max(max(getP1(c, b)), max(Ppp)));
                }
            }
        }

        for (Edge edge : graph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                Node a = Edges.getDirectedEdgeTail(edge);
                Node c = Edges.getDirectedEdgeHead(edge);

                final List<Double> ppac = Pp.get(new NodePair(a, c));
                P2.put(new NodePair(a, c), max(max(getP1(c, a)), sum(ppac)));

                final Object o = new Object();

                if (Pp.get(new NodePair(a, c)).size() == 1) {
                    insertI(new NodePair(a, c), o);

                    for (Node b : graph.getParents(c)) {
                        if (!graph.isAdjacentTo(a, b)) {
                            insertI(new NodePair(b, c), o);
                        }
                    }
                } else {
                    insertI(new NodePair(a, c), o);
                }
            }
        }

        for (Edge edge : graph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            if (Edges.isBidirectedEdge(edge)) {
                graph.removeEdge(edge);
                graph.addUndirectedEdge(x, y);
                A.add(new NodePair(x, y));
            }

            for (Node parent : graph.getParents(x)) {
                A.add(new NodePair(parent, x));
            }

            for (Node parent : graph.getParents(y)) {
                A.add(new NodePair(parent, y));
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        return colliders;
    }

    private Double getP(Node a, Node b, Map<NodePair, Double> p) {
        if (p.get(new NodePair(a, b)) == null) return 0.0;
        return p.get(new NodePair(a, b));
    }

    private double fdr(double alpha, int m, int k) {
        double m1 = 0;
        for (int i = 1; i <= m; i++) m1 += 1.0 / i;
        return m * alpha * m1 / (double) k;
    }

    private void insertI(NodePair key, Object o) {
        I.get(key).add(o);
    }

    public Map<NodePair, Set<Object>> getI() {
        return I;
    }

    private List<Double> getP1(Node a, Node c) {
        final List<Double> doubles = P1.get(new NodePair(a, c));
        return doubles == null ? new ArrayList<>() : doubles;
    }

    private double max(List<Double> p) {
        double max = Double.NEGATIVE_INFINITY;

        for (double d : p) {
            if (d > max) max = d;
        }

        return max;
    }

    private double max(double... p) {
        double max = Double.NEGATIVE_INFINITY;

        for (double d : p) {
            if (d > max) max = d;
        }

        return max;
    }

    private double sum(List<Double> p) {
        double sum = 0;

        for (double d : p) {
            sum += d;
        }

        return sum;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Orients according to background knowledge.
     */
    public static void pcOrientbk(IKnowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);
//            graph.setEndpoint(from, to, Endpoint.TAIL);
//            graph.setEndpoint(to, from, Endpoint.ARROW);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdges(from, to);
            graph.addDirectedEdge(from, to);

//            graph.setEndpoint(to, from, Endpoint.TAIL);
//            graph.setEndpoint(from, to, Endpoint.ARROW);
            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(from, to)));
        }


        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }

    /**
     * @return the string in nodelist which matches string in BK.
     */
    public static Node translate(String a, List<Node> nodes) {
        for (Node node : nodes) {
            if ((node.getName()).equals(a)) {
                return node;
            }
        }

        return null;
    }

    private List<Double> getPpp(Node i, Node k, Node c, Graph g) {
        List<Node> adji = g.getAdjacentNodes(i);
        List<Node> adjk = g.getAdjacentNodes(k);

        List<Double> p2 = new ArrayList<>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);

                    if (v.contains(c)) {
                        getIndependenceTest().isIndependent(i, k, v);
                        p2.add(getIndependenceTest().getPValue());
                    }
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);

                    if (v.contains(c)) {
                        getIndependenceTest().isIndependent(i, k, v);
                        p2.add(getIndependenceTest().getPValue());
                    }
                }
            }
        }

        return p2;
    }
}





