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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * <p></p>This is experimental; you should use it. It will probably be
 * removed from the repository.</p>
 * <p></p>This is experimental; you should use it. It will probably be
 * removed from the repository.</p>
 * <p>
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey.
 */
public class PcMax implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of nodes conditioned on in the search. The default it 1000.
     */
    private int depth = -1;

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
     * The initial graph for the Fast Adjacency Search, or null if there is none.
     */
    private Graph initialGraph = null;

    private boolean verbose = false;

    private boolean fdr = false;

    private Graph trueDag = null;
    private IndTestDSep dsep = null;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public PcMax(IndependenceTest independenceTest) {
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
     * <p>
     * All of the given nodes must be in the domain of the given conditional independence test.
     */
    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        if (trueDag != null) {
            this.dsep = new IndTestDSep(trueDag);
        }

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        IFas fas = new FasStableConcurrent(getIndependenceTest());
        fas.setInitialGraph(initialGraph);
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        graph = fas.search();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);

//        independenceTest.setAlpha(0.6);

        SepsetsMinScore sepsetProducer = new SepsetsMinScore(graph, independenceTest, null, getDepth());

        addColliders(graph, sepsetProducer, knowledge);

        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return graph;
    }


    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, verbose);

        List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

//        Collections.shuffle(colliders);

        Collections.sort(colliders, new Comparator<Triple>() {
            public int compare(Triple o1, Triple o2) {
                return -Double.compare(collidersPs.get(o1), collidersPs.get(o2));
            }
        });

        for (Triple collider : colliders) {
//            if (collidersPs.get(collider) < 0.2) continue;

            Node a = collider.getX();
            Node b = collider.getY();
            Node c = collider.getZ();

            if (!(isArrowpointAllowed(a, b, knowledge) && isArrowpointAllowed(c, b, knowledge))) {
                continue;
            }

            if (!graph.isAncestorOf(b, a) && !graph.isAncestorOf(b, c)) {
//            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);
            }
        }
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public Map<Triple, Double> findCollidersUsingSepsets(SepsetProducer sepsetProducer, Graph graph, boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        Map<Triple, Double> colliders = new HashMap<>();

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            findColliders(sepsetProducer, graph, verbose, colliders, b);
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
        return colliders;
    }

    private void findColliders(SepsetProducer sepsetProducer, Graph graph, boolean verbose, Map<Triple, Double> colliders, Node b) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
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

            List<Node> sepset = sepsetProducer.getSepset(a, c);

            if (sepset == null) continue;

            if (!sepset.contains(b)) {
                if (verbose) {
                    System.out.println("\nCollider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                }

                colliders.put(new Triple(a, b, c), sepsetProducer.getScore());

                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
            }
        }
    }


    /**
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return elapsedTime;
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

//    public int getNumIndependenceTests() {
//        return numIndependenceTests;
//    }

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

    private boolean missingColliders(Graph graph) {
        List<Triple> colliders = getUnshieldedCollidersFromGraph(graph);
        Graph copy = new EdgeListGraphSingleConnections(graph);
        new MeekRules().orientImplied(copy);
        if (copy.existsDirectedCycle()) return true;
        List<Triple> newColliders = getUnshieldedCollidersFromGraph(copy);
        newColliders.removeAll(colliders);
        if (!newColliders.isEmpty()) {
            return true;
        }
        return false;
    }

    public List<Triple> getUnshieldedCollidersFromGraph(Graph graph) {
        List<Triple> colliders = new ArrayList<>();

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

                if (graph.isDefCollider(a, b, c)) {
                    colliders.add(new Triple(a, b, c));
                }
            }
        }

        return colliders;
    }

    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public Graph getTrueDag() {
        return trueDag;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }
}




