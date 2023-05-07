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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modifies the PC algorithm to use the Max P rule for orienting ushielded colliders.
 *
 * @author josephramsey.
 * @see PcMax
 * @see Pc
 * @see Fasd
 */
public class PcMax implements IGraphSearch {

    /**
     * The independence test used for the PC search.g
     */
    private final IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private Knowledge knowledge = new Knowledge();

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
    private boolean aggressivelyPreventCycles;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The number of indepdendence tests in the last search.
     */
    private int numIndependenceTests;

    private boolean verbose;
    private boolean stable;
    //    private boolean concurrent;
    private boolean useMaxP = false;
    private int maxPPathLength = -1;
    private PcCommon.ConflictRule conflictRule = PcCommon.ConflictRule.OVERWRITE;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public PcMax(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException("Independence test is null.");
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
        return this.independenceTest;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge is null.");
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
        return this.depth;
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
     * independence test and knowledge and returns the resultant graph. The returned graph will be a CPDAG if the
     * independence information is consistent with the hypothesis that there are no latent common causes. It may,
     * however, contain cycles or bidirected edges if this assumption is not born out, either due to the actual presence
     * of latent common causes, or due to statistical errors in conditional independence judgments.
     */
    @Override
    public Graph search() {
        return search(this.independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a commplete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a CPDAG if the independence information
     * is consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p>
     * All of the given nodes must be in the domatein of the given conditional independence test.
     */
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);

        IFas fas = new Fas(getIndependenceTest());
        fas.setVerbose(this.verbose);
        return search(fas, nodes);
    }

    public Graph search(IFas fas, List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        long startTime = MillisecondTimes.timeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException("Null independence test.");
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        PcCommon search = new PcCommon(independenceTest);
        search.setDepth(depth);
        search.setHeuristic(1);
        search.setKnowledge(this.knowledge);

        if (stable) {
            search.setFasType(PcCommon.FasType.STABLE);
        } else {
            search.setFasType(PcCommon.FasType.REGULAR);
        }

//        if (concurrent) {
//            search.setConcurrent(PcAll.Concurrent.YES);
//        } else {
//            search.setConcurrent(PcAll.Concurrent.NO);
//        }

        search.setColliderDiscovery(PcCommon.ColliderDiscovery.MAX_P);
        search.setConflictRule(conflictRule);
        search.setUseHeuristic(useMaxP);
        search.setMaxPathLength(maxPPathLength);
//        search.setExternalGraph(externalGraph);
        search.setVerbose(verbose);

//        fas.setKnowledge(getKnowledge());
//        fas.setDepth(getDepth());
//        fas.setVerbose(this.verbose);

        this.graph = search.search();
        this.sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();

        GraphUtilsSearch.pcOrientbk(this.knowledge, this.graph, nodes);
        GraphUtilsSearch.orientCollidersUsingSepsets(this.sepsets, this.knowledge, this.graph, this.verbose, false);

        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        this.elapsedTime = MillisecondTimes.timeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return this.graph;
    }

    /**
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(this.graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(this.graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setStable(boolean stable) {
        this.stable = stable;
    }

//    public void setConcurrent(boolean concurrent) {
//        this.concurrent = concurrent;
//    }

    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }

    public void setMaxPPathLength(int maxPPathLength) {
        this.maxPPathLength = maxPPathLength;
    }

    public void setConflictRule(PcCommon.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }
}




