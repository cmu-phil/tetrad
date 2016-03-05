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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the PC Local algorithm.
 *
 * @author Joseph Ramsey (this version).
 */
public class PcMaxLocal implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

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
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    private Graph graph;
    private MeekRules meekRules;
    private SepsetsMinScore sepsetProducer;
    private boolean recordSepsets;
    private SepsetMap sepsets = new SepsetMap();
    private boolean underlingNoncolldiers;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a PC Local search with the given independence oracle.
     */
    public PcMaxLocal(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    public PcMaxLocal(IndependenceTest independenceTest, Graph graph) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.graph = graph;
    }

    //==============================PUBLIC METHODS========================//

    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }


    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
    public Graph search() {
        long time1 = System.currentTimeMillis();

        graph = new EdgeListGraph(getIndependenceTest().getVariables());
        meekRules = new MeekRules();
        meekRules.setKnowledge(knowledge);
        meekRules.setUndirectUnforcedEdges(false);

        sepsetProducer = new SepsetsMinScore(graph, getIndependenceTest(), null, -1);

        // This is the list of all changed nodes from the last iteration
        List<Node> nodes = graph.getNodes();

        Graph outGraph = null;

        int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {

                if (++index % 1000 == 0) {
                    log("info", index + " of " + numEdges);
                }

                tryAddingEdge(nodes.get(i), nodes.get(j));
            }
        }

        addColliders(graph, sepsetProducer, knowledge);


//        for (Node y : graph.getNodes()) {
//            orientColliders(y);
//        }

        meekRules.orientImplied(graph);

        outGraph = graph;

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return outGraph;
    }

    private void log(String info, String message) {
        TetradLogger.getInstance().log(info, message);
        if ("info".equals(info)) {
            System.out.println(message);
        }
    }

    private void tryAddingEdge(Node x, Node y) {
        if (graph.isAdjacentTo(x, y)) {
            return;
        }

        List<Node> sepset = sepset(x, y);

        if (sepset == null) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            Edge edge = Edges.undirectedEdge(x, y);
            graph.addEdge(edge);

            for (Node w : graph.getAdjacentNodes(x)) {
                tryRemovingEdge(w, x);
            }

            for (Node w : graph.getAdjacentNodes(y)) {
                tryRemovingEdge(w, y);
            }
        }
    }

    private void tryRemovingEdge(Node x, Node y) {
        if (!graph.isAdjacentTo(x, y)) return;

        List<Node> sepsetX = sepset(x, y);

        if (sepsetX != null) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return;
            }

            graph.removeEdge(x, y);
//
//            List<Node> start = new ArrayList<>();
//            start.add(x);
//            start.add(y);
//
//            meekRules.orientImplied(graph, start);
        }
    }

    //================================PRIVATE METHODS=======================//

    private List<Node> sepset(Node x, Node y) {
        List<Node> adj = graph.getAdjacentNodes(x);
        adj.remove(y);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> cond = GraphUtils.asList(choice, adj);

            if (getIndependenceTest().isIndependent(x, y, cond)) {
                if (recordSepsets) sepsets.set(x, y, cond);
                return cond;
            }
        }

        adj = graph.getAdjacentNodes(y);
        adj.remove(x);

        gen = new DepthChoiceGenerator(adj.size(), adj.size());

        while ((choice = gen.next()) != null) {
            List<Node> cond = GraphUtils.asList(choice, adj);

            if (getIndependenceTest().isIndependent(x, y, cond)) {
                if (recordSepsets) sepsets.set(x, y, cond);
                return cond;
            }
        }

        return null;
    }

    private boolean orientColliders(Node y) {
        boolean oriented = false;
        List<Node> start = new ArrayList<>();
        start.add(y);
        List<Node> adjy = graph.getAdjacentNodes(y);

        for (int i = 0; i < adjy.size(); i++) {
            for (int j = i + 1; j < adjy.size(); j++) {
                Node x = adjy.get(i);
                Node z = adjy.get(j);
                if (graph.isAdjacentTo(x, z)) continue;
                if (graph.isParentOf(y, x) || graph.isParentOf(y, z)) continue;

                List<Node> sepset = sepsetProducer.getSepset(x, z);

                if (sepset != null && !sepset.contains(y)) {
                    graph.removeEdge(x, y);
                    graph.removeEdge(z, y);
                    graph.addDirectedEdge(x, y);
                    graph.addDirectedEdge(z, y);
                    oriented = true;
                }
            }
        }

        return oriented;
    }

    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, false);

        List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

        Collections.shuffle(colliders);

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

            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
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
            } else {
                graph.addUnderlineTriple(a, b, c);
            }
        }
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

    public void setRecordSepsets(boolean recordSepsets) {
        this.recordSepsets = recordSepsets;
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    public void setUnderlingNoncolliders(boolean underlingNoncolldiers) {
        this.underlingNoncolldiers = underlingNoncolldiers;
    }
}


