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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author josephramsey (this version).
 */
public final class Cpc implements IGraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = 1000;

    private Graph graph;

    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * Set of unshielded colliders from the triple orientation step.
     */
    private Set<Triple> colliderTriples;

    /**
     * Set of unshielded noncolliders from the triple orientation step.
     */
    private Set<Triple> noncolliderTriples;

    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;

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
     * The sepsets.
     */
    private SepsetMap sepsets;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose;

    private boolean stable;
    private boolean useHeuristic = false;
    private int maxPPathLength = -1;
    private final PcCommon.ConflictRule conflictRule = PcCommon.ConflictRule.OVERWRITE;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public Cpc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     *
     * @param aggressivelyPreventCycles True if so.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    /**
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    /**
     * Returns the edges in the search graph.
     *
     * @return These edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * Returns the non-adjacencies in the seaarch graph.
     *
     * @return These non-adjacencies.
     */
    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(this.graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(this.graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    /**
     * Runs CPC starting with a fully connected graph over all of the variables in the domain of the independence test.
     * See PC for caveats. The number of possible cycles and bidirected edges is far less with CPC than with PC.
     */
    public Graph search() {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        Fas fas = new Fas(getIndependenceTest());

        long startTime = MillisecondTimes.timeMillis();

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        PcCommon search = new PcCommon(independenceTest);
        search.setDepth(depth);
        search.setHeuristic(1);
        search.setKnowledge(this.knowledge);

        if (stable) {
            search.setFasType(PcCommon.FasType.STABLE);
        } else {
            search.setFasType(PcCommon.FasType.REGULAR);
        }

        search.setColliderDiscovery(PcCommon.ColliderDiscovery.CONSERVATIVE);
        search.setConflictRule(conflictRule);
        search.setUseHeuristic(useHeuristic);
        search.setMaxPathLength(maxPPathLength);
        search.setVerbose(verbose);

        this.graph = search.search();
        this.sepsets = fas.getSepsets();

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, independenceTest.getVariables());
        GraphSearchUtils.orientCollidersUsingSepsets(this.sepsets, this.knowledge, this.graph, this.verbose, false);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        this.colliderTriples = search.getColliderTriples();
        this.noncolliderTriples = search.getNoncolliderTriples();
        this.ambiguousTriples = search.getAmbiguousTriples();

        logTriples();

        TetradLogger.getInstance().flush();
        return this.graph;
    }

    /**
     * Returns a map for x _||_ y | z1,..,zn from {x, y} to {z1,...,zn}.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * The graph that's constructed during the search.
     *
     * @return This graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * @param verbose Whether verbose output should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @param stable Whether the stable FAS search should be used.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * @param useHeuristic Whethe the heuristic should be used for max p.
     */
    public void setUseHeuristic(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    /**
     * Sets the max path length for the max p heuristic.
     *
     * @param maxPPathLength This length.
     */
    public void setMaxPPathLength(int maxPPathLength) {
        this.maxPPathLength = maxPPathLength;
    }

    //==========================PRIVATE METHODS===========================//

    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples:");

        for (Triple triple : this.colliderTriples) {
            TetradLogger.getInstance().log("info", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples:");

        for (Triple triple : this.noncolliderTriples) {
            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

}


