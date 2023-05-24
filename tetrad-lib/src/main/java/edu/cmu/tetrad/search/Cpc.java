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
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally. The reference is here:</p>
 *
 * <p>Ramsey, J., Zhang, J., &amp; Spirtes, P. L. (2012). Adjacency-faithfulness and conservative causal inference.
 * arXiv preprint arXiv:1206.6843.</p>
 *
 * <p>Conservative triple orientation is a method for orienting unshielded triples X*=-*Y*-*Z as one of the following:
 * (a) Collider, X->Y<-Z, (b) Noncollider, X-->Y-->Z, or X<-Y<-Z, or X<-Y->Z, (c) ambiguous between (a) or (b). One does
 * this by conditioning on subsets of adj(X) or adj(Z). One first checks conditional independence of X and Z conditional
 * on each of these subsets, then lists all of these subsets conditional on which X and Z are *independent*, then looks
 * thoough this list to see if Y is in them. If Y is in all of these subset, the triple is judged to be a noncollider;
 * if it is in none of these subsets, the triple is judged to be a collider, and if it is in some of these subsets and
 * not in others of the subsets, then it is judged to be ambiguous.</p>
 *
 * <p>Ambiguous triple are marked in the final graph using an underline, and the final graph is called an "e-pattern",
 * and represents a collection of CPDAGs. To find an element of this collection, one first needs to choose for each
 * ambiguous triple whether it should be a collider or a noncollider and then run the Meek rules given the result of
 * these decisions.</p>
 *
 * <p>See setter methods for "knobs" you can turn to control the output of PC and their defaults.</p> *
 *
 * <p>This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.</p>
 *
 * @author josephramsey (this version).
 * @see Pc
 * @see Knowledge
 * @see edu.cmu.tetrad.search.utils.MeekRules
 */
public final class Cpc implements IGraphSearch {
    private final IndependenceTest independenceTest;
    private Knowledge knowledge = new Knowledge();
    private Graph graph;
    private long elapsedTime;
    private Set<Triple> colliderTriples;
    private Set<Triple> noncolliderTriples;
    private Set<Triple> ambiguousTriples;
    private final TetradLogger logger = TetradLogger.getInstance();
    private SepsetMap sepsets;
    private int depth = 1000;
    private boolean stable = false;
    private boolean meekPreventCycles = false;
    private PcCommon.ConflictRule conflictRule = PcCommon.ConflictRule.PRIORITIZE_EXISTING;
    private boolean verbose = false;
    private PcCommon.PcHeuristicType pcHeuristicType = PcCommon.PcHeuristicType.NONE;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     *
     * @param independenceTest The test to user for oracle conditional independence information.
     */
    public Cpc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Runs CPC starting with a fully connected graph over all the variables in the domain of the independence test. See
     * PC for caveats. The number of possible cycles and bidirected edges is far less with CPC than with PC.
     *
     * @return The e-pattern for the search, which is a graphical representation of a set of possible CPDAGs.
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
        search.setConflictRule(conflictRule);
        search.setPcHeuristicType(pcHeuristicType);
        search.setMeekPreventCycles(meekPreventCycles);
        search.setKnowledge(this.knowledge);

        if (stable) {
            search.setFasType(PcCommon.FasType.STABLE);
        } else {
            search.setFasType(PcCommon.FasType.REGULAR);
        }

        search.setColliderDiscovery(PcCommon.ColliderDiscovery.CONSERVATIVE);
        search.setConflictRule(conflictRule);
        search.setVerbose(verbose);

        this.graph = search.search();
        this.sepsets = fas.getSepsets();

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
     * Sets to true just in case edges will not be added if they would create cycles.
     *
     * @param meekPreventCycles True if so.
     */
    public void meekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multicore systems.
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
     * Returns the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns the knowledge specification used in the search. Non-null.
     *
     * @return this knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Rreturn the independence test used in the search, set in the constructor.
     *
     * @return This.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Returns the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     *
     * @return This.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Returns the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     *
     * @return This set.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * Returns the edges in the search graph as a set of undirected edges.
     *
     * @return These edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(GraphUtils.undirectedGraph(this.graph).getEdges());
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
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>Sets whether the stable adjacency search should be used. Default is false. Default is false. See the
     * following reference for this:</p>
     *
     * <p>Colombo, D., & Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. J. Mach.
     * Learn. Res., 15(1), 3741-3782.</p>
     *
     * @param stable True iff the case.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets which conflict rule to use for resolving collider orientation conflicts.
     *
     * @param conflictRule The rule.
     * @see edu.cmu.tetrad.search.utils.PcCommon.ConflictRule
     */
    public void setConflictRule(PcCommon.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * Sets the PC heuristic type. Default = NONE.
     *
     * @param pcHeuristicType The type.
     * @see edu.cmu.tetrad.search.utils.PcCommon.PcHeuristicType
     */
    public void setPcHeuristicType(PcCommon.PcHeuristicType pcHeuristicType) {
        this.pcHeuristicType = pcHeuristicType;
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


