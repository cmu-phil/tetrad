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
public class PcLocal implements GraphSearch {

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
    private boolean verbose;
    private Graph externalGraph;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a PC Local search with the given independence oracle.
     */
    public PcLocal(final IndependenceTest independenceTest) {
        this(independenceTest, null);
    }

    public PcLocal(final IndependenceTest independenceTest, final Graph graph) {
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

        if (this.externalGraph != null) {
            this.graph = new EdgeListGraph(this.externalGraph);
            this.graph.reorientAllWith(Endpoint.TAIL);
        } else if (this.graph == null) {
            this.graph = new EdgeListGraph(getIndependenceTest().getVariables());
        } else {
            this.graph = new EdgeListGraph(this.graph);
            this.graph.reorientAllWith(Endpoint.TAIL);
        }

        this.sepsetProducer = new SepsetsMinScore(this.graph, getIndependenceTest(), -1);

        this.meekRules = new MeekRules();
        this.meekRules.setKnowledge(this.knowledge);

        // This is the list of all changed nodes from the last iteration
        final List<Node> nodes = getIndependenceTest().getVariables();
        buildIndexing(nodes);

        final int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ++index;

                if (this.verbose && index % 100 == 0) {
                    log("info", index + " of " + numEdges);
                }

                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                tryAddingEdge(x, y);
            }
        }

        for (final Node node : nodes) {
            reorientNode(node);
//            applyMeek(Collections.singletonList(node));
        }

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
        final List<Node> n = new ArrayList<>();
        n.add(x);
        n.add(y);

        reorientNode(y);
        reorientNode(x);

        for (final Node c : getCommonAdjacents(x, y)) {
            reorientNode(c);
            n.add(c);
        }

//        applyMeek(n);
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
        final List<Node> start = new ArrayList<>();
        for (final Node n : y) start.add(n);
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

                if (cond != null && !cond.contains(y) && !this.knowledge.isForbidden(z.getName(), y.getName())
                        && !this.knowledge.isForbidden(w.getName(), y.getName())) {
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

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    private boolean existsUnblockedSemiDirectedPath(final Node from, final Node to, final Set<Node> cond, final int bound) {
        final Queue<Node> Q = new LinkedList<>();
        final Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            final Node t = Q.remove();
            if (from != to && t == to) {
                return true;
            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) return false;
            }

            for (final Node u : this.graph.getAdjacentNodes(t)) {
                final Edge edge = this.graph.getEdge(t, u);
                final Node c = PcLocal.traverseSemiDirected(t, edge);
                if (c == null) continue;
                if (cond.contains(c)) continue;

                if (c == to) {
                    return true;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return false;
    }

    // Used to find semidirected paths for cycle checking.
    private static Node traverseSemiDirected(final Node node, final Edge edge) {
        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL) {
                return edge.getNode1();
            }
        }
        return null;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }
}


