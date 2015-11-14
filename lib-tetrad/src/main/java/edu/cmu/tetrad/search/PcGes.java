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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * Note that GES has been used for an adjacency search here, which is totally silly since GES already does the
 * Meek rules.
 *
 * @author Joseph Ramsey.
 */
public class PcGes implements GraphSearch {

    /**
     * The independence test used for the PC search.
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
     * In an enumeration of triple types, these are the noncollider triples.
     */
    private Set<Triple> unshieldedNoncolliders;

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
    private ICovarianceMatrix covarianceMatrix;
    private int sampleSize;
    private double penaltyDiscount = 2;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public PcGes(IndependenceTest independenceTest) {
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
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a commplete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a pattern if the independence information
     * is consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p/>
     * All of the given nodes must be in the domain of the given conditional independence test.
     */
    public Graph search(List<Node> nodes) {
//        if (getIndependenceTest() instanceof IndTestFisherZConcatenateResiduals2) {
//            return search(new FasICov2(getIndependenceTest()), nodes);
////            return search(new FasICov(getIndependenceTest()), nodes);
//        }

        return search(new Fas(getIndependenceTest()), nodes);
//        return search(new Fas(getIndependenceTest()), nodes);
    }

    public Graph search(IFas fas, List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

//        this.logger.log("info", "Variables " + independenceTest.getVariables());

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }


        fas.setInitialGraph(initialGraph);
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);
        graph = fas.search();
        sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();
        this.numFalseDependenceJudgements = fas.getNumFalseDependenceJudgments();
        this.numDependenceJudgements = fas.getNumDependenceJudgments();

//        enumerateTriples();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);

        double alpha = getIndependenceTest().getAlpha();
        getIndependenceTest().setAlpha(0.2);

        orientColliders();

        getIndependenceTest().setAlpha(alpha);

//        SearchGraphUtils.orientCollidersUsingSepsets(this.sepsets, knowledge, graph, verbose);

        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return graph;
    }

    private void orientColliders() {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (isCollider(a, b, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    TetradLogger.getInstance().log("info", "Collider " + a + "*->" + b + "<-*" + c);
                }
            }
        }
    }

    private List<List<Node>> getSepsets(Node i, Node k) {
        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<List<Node>>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }

    private boolean isCollider(Node i, Node j, Node k) {
//        final List<Node> sepset = getSepset(i, k, graph);
//        return sepset != null && !sepset.contains(j);
//
        List<List<Node>> sepsets = getSepsets(i, k);

        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
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

    /**
     * @return the set of unshielded noncolliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedNoncolliders() {
        return unshieldedNoncolliders;
    }

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<Edge>();
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
        return new HashSet<Edge>(nonAdjacencies);
    }

    //===============================PRIVATE METHODS=======================//

    private void enumerateTriples() {
        this.unshieldedColliders = new HashSet<Triple>();
        this.unshieldedNoncolliders = new HashSet<Triple>();

        for (Node y : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(y);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Node x = adj.get(choice[0]);
                Node z = adj.get(choice[1]);

                List<Node> nodes = getSepset(x, z);

                // Note that checking adj(x, z) does not suffice when knowledge
                // has been specified.
                if (nodes == null) {
                    continue;
                }

                if (nodes.contains(y)) {
                    getUnshieldedNoncolliders().add(new Triple(x, y, z));
                } else {
                    getUnshieldedColliders().add(new Triple(x, y, z));
                }
            }
        }
    }

    private List<Node> getSepset(Node i, Node k) {
        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) return v;
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) return v;
                }
            }
        }

        return null;
    }

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

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }
}




