///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final SepsetMap sepsetMap = new SepsetMap();
    private boolean verbose;
    private Graph externalGraph;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a PC Local search with the given independence oracle.
     */
    public PcLocal(IndependenceTest independenceTest) {
        this(independenceTest, null);
    }

    public PcLocal(IndependenceTest independenceTest, Graph graph) {
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
        return this.independenceTest;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
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
        long time1 = System.currentTimeMillis();

        if (this.externalGraph != null) {
            this.graph = new EdgeListGraph(this.externalGraph);
            this.graph.reorientAllWith(Endpoint.TAIL);
        } else if (this.graph == null) {
            this.graph = new EdgeListGraph(getIndependenceTest().getVariables());
        } else {
            this.graph = new EdgeListGraph(this.graph);
            this.graph.reorientAllWith(Endpoint.TAIL);
        }

        this.meekRules = new MeekRules();
        this.meekRules.setKnowledge(this.knowledge);

        // This is the list of all changed nodes from the last iteration
        List<Node> nodes = getIndependenceTest().getVariables();

        int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ++index;

                if (this.verbose && index % 100 == 0) {
                    log(index + " of " + numEdges);
                }

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                tryAddingEdge(x, y);
            }
        }

        for (Node node : nodes) {
            reorientNode(node);
//            applyMeek(Collections.singletonList(node));
        }

        applyMeek();

        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return this.graph;
    }

    private void log(String message) {
        TetradLogger.getInstance().log("info", message);
    }

    private void tryAddingEdge(Node x, Node y) {
        if (this.graph.isAdjacentTo(x, y)) {
            return;
        }

        if (sepset(x, y) == null) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            this.graph.addUndirectedEdge(x, y);
            reorient(x, y);

            for (Node w : this.graph.getAdjacentNodes(x)) {
                tryRemovingEdge(w, x);
            }

            for (Node w : this.graph.getAdjacentNodes(y)) {
                tryRemovingEdge(w, y);
            }
        }
    }

    private void tryRemovingEdge(Node x, Node y) {
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

    private List<Node> sepset(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Can't have x == y.");

        {
            List<Node> adj = this.graph.getAdjacentNodes(x);
            adj.remove(y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().checkIndependence(x, y, cond).independent()) {
                    this.sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        {
            List<Node> adj = this.graph.getAdjacentNodes(y);
            adj.remove(x);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, adj);

                if (getIndependenceTest().checkIndependence(x, y, cond).independent()) {
                    this.sepsetMap.set(x, y, cond);
                    return cond;
                }
            }
        }

        return null;
    }

    private void reorient(Node x, Node y) {
        reorientNode(y);
        reorientNode(x);

        for (Node c : getCommonAdjacents(x, y)) {
            reorientNode(c);
        }
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(this.graph.getAdjacentNodes(x));
        commonChildren.retainAll(this.graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private void reorientNode(Node y) {
        unorientAdjacents(y);
        orientLocalColliders(y);
    }

    private void applyMeek() {
        this.meekRules.orientImplied(this.graph);
    }

    private void unorientAdjacents(Node y) {
        for (Node z : this.graph.getAdjacentNodes(y)) {
            if (this.graph.isParentOf(y, z)) continue;
            this.graph.removeEdge(z, y);
            this.graph.addUndirectedEdge(z, y);
        }
    }

    private void orientLocalColliders(Node y) {
        List<Node> adjy = this.graph.getAdjacentNodes(y);

        for (int i = 0; i < adjy.size(); i++) {
            for (int j = i + 1; j < adjy.size(); j++) {
                Node z = adjy.get(i);
                Node w = adjy.get(j);

                if (this.graph.isAncestorOf(y, z)) continue;
                if (this.graph.isAncestorOf(y, w)) continue;

//                if (z == w) continue;
                if (this.graph.isAdjacentTo(z, w)) continue;
                List<Node> cond = sepset(z, w);

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
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public SepsetMap getSepsets() {
        return this.sepsetMap;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }
}


