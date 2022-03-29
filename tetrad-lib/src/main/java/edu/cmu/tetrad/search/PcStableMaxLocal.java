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
public class PcStableMaxLocal implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    private Graph graph;
    private MeekRules meekRules;
    private boolean recordSepsets = true;
    private final SepsetMap sepsetMap = new SepsetMap();
    private SepsetProducer sepsetProducer;
    private SemBicScore score;
    private ConcurrentMap<Node, Integer> hashIndices;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a PC Local search with the given independence oracle.
     */
    public PcStableMaxLocal(final IndependenceTest independenceTest) {
        this(independenceTest, null);
    }

    public PcStableMaxLocal(final IndependenceTest independenceTest, final Graph graph) {
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

    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
    public Graph search() {
        final long time1 = System.currentTimeMillis();

        if (this.graph == null) {
            this.graph = new EdgeListGraph(getIndependenceTest().getVariables());
        }

        this.sepsetProducer = new SepsetsMinScore(this.graph, getIndependenceTest(), -1);

        this.meekRules = new MeekRules();
        this.meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        this.meekRules.setKnowledge(this.knowledge);

        final List<Node> nodes = getIndependenceTest().getVariables();
        buildIndexing(nodes);

        final int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ++index;

                if (index % 100 == 0) {
                    log("info", index + " of " + numEdges);
                }

                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                tryAddingEdge(x, y);
            }
        }

        addColliders(this.graph, this.sepsetProducer, this.knowledge);
        applyMeek(nodes);

        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        final long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return this.graph;
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(final List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();
        for (final Node node : nodes) {
            this.hashIndices.put(node, nodes.indexOf(node));
        }
    }

    private void log(final String info, final String message) {
        TetradLogger.getInstance().log(info, message);
        if ("info".equals(info)) {
            System.out.println(message);
        }
    }

    private void tryAddingEdge(final Node x, final Node y) {
        if (this.graph.isAdjacentTo(x, y)) {
            return;
        }

        if (sepset(x, y) == null) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            this.graph.addUndirectedEdge(x, y);
            reorient(x, y);

            for (final Node w : this.graph.getAdjacentNodes(x)) {
                tryRemovingEdge(w, x);
            }

            for (final Node w : this.graph.getAdjacentNodes(y)) {
                tryRemovingEdge(w, y);
            }
        }
    }

    private void tryRemovingEdge(final Node x, final Node y) {
        if (!this.graph.isAdjacentTo(x, y)) return;

        if (sepset(x, y) != null) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return;
            }

            this.graph.removeEdge(x, y);
            reorient(x, y);

            for (final Node w : this.graph.getAdjacentNodes(x)) {
                tryAddingEdge(w, x);
            }

            for (final Node w : this.graph.getAdjacentNodes(y)) {
                tryAddingEdge(w, y);
            }
        }
    }

    //================================PRIVATE METHODS=======================//

    private List<Node> sepset(final Node x, final Node y) {
        if (x == y) throw new IllegalArgumentException("Can't have x == y.");

        {
            final List<Node> adj = this.graph.getAdjacentNodes(x);
            adj.remove(y);

            final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().isIndependent(x, y, cond)) {
                    if (this.recordSepsets) this.sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        {
            final List<Node> adj = this.graph.getAdjacentNodes(y);
            adj.remove(x);

            final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().isIndependent(x, y, cond)) {
                    if (this.recordSepsets) this.sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        return null;
    }

    private List<Node> sepset2(final Node x, final Node y) {
        if (x == y) throw new IllegalArgumentException("Can have x == y.");
        return this.sepsetProducer.getSepset(x, y);
    }


    private void reorient(final Node x, final Node y) {
        if (true) return;
        final List<Node> n = new ArrayList<>();
        n.add(x);
        n.add(y);

        reorientNode(y);
        reorientNode(x);

        for (final Node c : getCommonAdjacents(x, y)) {
            reorientNode(c);
            n.add(c);
        }

        applyMeek(n);
    }

    private Set<Node> getCommonAdjacents(final Node x, final Node y) {
        final Set<Node> commonChildren = new HashSet<>(this.graph.getAdjacentNodes(x));
        commonChildren.retainAll(this.graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private void reorientNode(final Node y) {
        unorientAdjacents(y);
        orientLocalColliders(y);
    }

    private void reorientNode2(final Node y) {
        final List<Node> adjy = this.graph.getAdjacentNodes(y);
        adjy.removeAll(this.graph.getChildren(y));


        final DepthChoiceGenerator gen = new DepthChoiceGenerator(adjy.size(), adjy.size());
        int[] choice;
        double maxScore = Double.NEGATIVE_INFINITY;
        List<Node> maxParents = new ArrayList<>();
        unorientAdjacents(y);

        while ((choice = gen.next()) != null) {
            final List<Node> parents = GraphUtils.asList(choice, adjy);

            final Iterator<Node> pi = parents.iterator();
            final int[] parentIndices = new int[parents.size()];
            int count = 0;

            while (pi.hasNext()) {
                final Node nextParent = pi.next();
                parentIndices[count++] = this.hashIndices.get(nextParent);
            }

            final int yIndex = this.hashIndices.get(y);

            final double _score = this.score.localScore(yIndex, parentIndices);

            if (_score > maxScore) {
                maxScore = _score;
                maxParents = parents;
            }
        }

        for (final Node v : maxParents) {
            this.graph.removeEdge(v, y);
            this.graph.addDirectedEdge(v, y);
        }
    }

    private void applyMeek(final List<Node> y) {
        this.meekRules.orientImplied(this.graph);
    }

    private void unorientAdjacents(final Node y) {
        for (final Node z : this.graph.getAdjacentNodes(y)) {
            if (this.graph.isParentOf(y, z)) continue;
            this.graph.removeEdge(z, y);
            this.graph.addUndirectedEdge(z, y);
        }
    }

    private void orientLocalColliders(final Node y) {
        final List<Node> adjy = this.graph.getAdjacentNodes(y);

        for (int i = 0; i < adjy.size(); i++) {
            for (int j = i + 1; j < adjy.size(); j++) {
                final Node z = adjy.get(i);
                final Node w = adjy.get(j);

                if (this.graph.isAncestorOf(y, z)) continue;
                if (this.graph.isAncestorOf(y, w)) continue;

//                if (z == w) continue;
                if (this.graph.isAdjacentTo(z, w)) continue;
                final List<Node> cond = sepset(z, w);

                if (cond != null && !cond.contains(y)) {
                    this.graph.setEndpoint(z, y, Endpoint.ARROW);
                    this.graph.setEndpoint(w, y, Endpoint.ARROW);
                }
            }
        }
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(final Object from, final Object to,
                                              final IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public void setRecordSepsets(final boolean recordSepsets) {
        this.recordSepsets = recordSepsets;
    }

    public SepsetMap getSepsets() {
        return this.sepsetMap;
    }

    private void addColliders(final Graph graph, final SepsetProducer sepsetProducer, final IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, false);

        final List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

//        Collections.shuffle(colliders);
//
        Collections.sort(colliders, new Comparator<Triple>() {
            public int compare(final Triple o1, final Triple o2) {
                return -Double.compare(collidersPs.get(o1), collidersPs.get(o2));
            }
        });

        for (final Triple collider : colliders) {
            final Node a = collider.getX();
            final Node b = collider.getY();
            final Node c = collider.getZ();

            if (!(PcStableMaxLocal.isArrowpointAllowed(a, b, knowledge) && PcStableMaxLocal.isArrowpointAllowed(c, b, knowledge))) {
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
    public Map<Triple, Double> findCollidersUsingSepsets(final SepsetProducer sepsetProducer, final Graph graph, final boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        final Map<Triple, Double> colliders = new HashMap<>();

        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {
            findColliders(sepsetProducer, graph, verbose, colliders, b);
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
        return colliders;
    }

    private void findColliders(final SepsetProducer sepsetProducer, final Graph graph, final boolean verbose, final Map<Triple, Double> colliders, final Node b) {
        final List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            final Node a = adjacentNodes.get(combination[0]);
            final Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (graph.isAdjacentTo(a, c)) {
                continue;
            }

            final List<Node> sepset = sepsetProducer.getSepset(a, c);

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


