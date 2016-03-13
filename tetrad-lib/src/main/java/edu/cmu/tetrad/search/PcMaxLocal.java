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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private boolean recordSepsets = true;
    private SepsetMap sepsetMap = new SepsetMap();
    private SepsetProducer sepsetProducer;
    private SemBicScore score;
    private ConcurrentMap<Node, Integer> hashIndices;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a PC Local search with the given independence oracle.
     */
    public PcMaxLocal(IndependenceTest independenceTest) {
        this(independenceTest, null);
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

        if (graph == null) {
            graph = new EdgeListGraph(getIndependenceTest().getVariables());
        }

        sepsetProducer = new SepsetsMinScore(graph, getIndependenceTest(), null, -1);

        meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge(knowledge);
        meekRules.setUndirectUnforcedEdges(true);

        List<Node> nodes = getIndependenceTest().getVariables();
        buildIndexing(nodes);

        int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ++index;

                if (index % 100 == 0) {
                    log("info", index + " of " + numEdges);
                }

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                tryAddingEdge(x, y);
            }
        }

        addColliders(graph, sepsetProducer, knowledge);
        applyMeek(nodes);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return graph;
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();
        for (Node node : nodes) {
            this.hashIndices.put(node, nodes.indexOf(node));
        }
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

        if (sepset(x, y) == null) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            graph.addUndirectedEdge(x, y);
            reorient(x, y);

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

        if (sepset(x, y) != null) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return;
            }

            graph.removeEdge(x, y);
            reorient(x, y);

            for (Node w : graph.getAdjacentNodes(x)) {
                tryAddingEdge(w, x);
            }

            for (Node w : graph.getAdjacentNodes(y)) {
                tryAddingEdge(w, y);
            }
        }
    }

    //================================PRIVATE METHODS=======================//

    private List<Node> sepset(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Can't have x == y.");

        {
            List<Node> adj = graph.getAdjacentNodes(x);
            adj.remove(y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().isIndependent(x, y, cond)) {
                    if (recordSepsets) sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        {
            List<Node> adj = graph.getAdjacentNodes(y);
            adj.remove(x);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().isIndependent(x, y, cond)) {
                    if (recordSepsets) sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        return null;
    }

    private List<Node> sepset2(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Can have x == y.");
        return sepsetProducer.getSepset(x, y);
    }


    private void reorient(Node x, Node y) {
        if (true) return;
        List<Node> n = new ArrayList<>();
        n.add(x);
        n.add(y);

        reorientNode(y);
        reorientNode(x);

        for (Node c : getCommonAdjacents(x, y)) {
            reorientNode(c);
            n.add(c);
        }

        applyMeek(n);
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(graph.getAdjacentNodes(x));
        commonChildren.retainAll(graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private void reorientNode(Node y) {
        unorientAdjacents(y);
        orientLocalColliders(y);
    }

    private void reorientNode2(Node y) {
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjy.removeAll(graph.getChildren(y));


        DepthChoiceGenerator gen = new DepthChoiceGenerator(adjy.size(), adjy.size());
        int[] choice;
        double maxScore = Double.NEGATIVE_INFINITY;
        List<Node> maxParents = new ArrayList<>();
        unorientAdjacents(y);

        while ((choice = gen.next()) != null) {
            List<Node> parents = GraphUtils.asList(choice, adjy);

            Iterator<Node> pi = parents.iterator();
            int parentIndices[] = new int[parents.size()];
            int count = 0;

            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            int yIndex = hashIndices.get(y);

            double _score = score.localScore(yIndex, parentIndices);

            if (_score > maxScore) {
                maxScore = _score;
                maxParents = parents;
            }
        }

        for (Node v : maxParents) {
            graph.removeEdge(v, y);
            graph.addDirectedEdge(v, y);
        }
    }

    private void applyMeek(List<Node> y) {
        List<Node> start = new ArrayList<>();
        for (Node n : y) start.add(n);
        meekRules.orientImplied(graph, start);
    }

    private void unorientAdjacents(Node y) {
        for (Node z : graph.getAdjacentNodes(y)) {
            if (graph.isParentOf(y, z)) continue;
            graph.removeEdge(z, y);
            graph.addUndirectedEdge(z, y);
        }
    }

    private void orientLocalColliders(Node y) {
        List<Node> adjy = graph.getAdjacentNodes(y);

        for (int i = 0; i < adjy.size(); i++) {
            for (int j = i + 1; j < adjy.size(); j++) {
                Node z = adjy.get(i);
                Node w = adjy.get(j);

                if (graph.isAncestorOf(y, z)) continue;
                if (graph.isAncestorOf(y, w)) continue;

//                if (z == w) continue;
                if (graph.isAdjacentTo(z, w)) continue;
                List<Node> cond = sepset(z, w);

                if (cond != null && !cond.contains(y)) {
                    graph.setEndpoint(z, y, Endpoint.ARROW);
                    graph.setEndpoint(w, y, Endpoint.ARROW);
                }
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
        return sepsetMap;
    }

    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, false);

        List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

//        Collections.shuffle(colliders);
//
        Collections.sort(colliders, new Comparator<Triple>() {
            public int compare(Triple o1, Triple o2) {
                return -Double.compare(collidersPs.get(o1), collidersPs.get(o2));
            }
        });

        for (Triple collider : colliders) {
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

}


