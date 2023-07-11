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
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Implements the Peter/Clark (PC) algorithm, which uses conditional independence testing as an oracle to first of
 * all remove extraneous edges from a complete graph, then to orient the unshielded colliders in the graph, and finally
 * to make any additional orientations that are capable of avoiding additional unshielded colliders in the graph. An
 * version of this algorithm was proposed earlier than this, but the standard reference for the algorithm is in Chapter
 * 6 of the following book:</p>
 *
 * <p>Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation,
 * prediction, and search. MIT press.</p>
 *
 * <p>A modified rule set capable of dealing effectively with knowledge of required
 * and forbidden edges is due to Chris Meek, with this reference:
 *
 * <p>Meek, C. (1995), "Causal inference and causal explanation with background
 * knowledge."</p>
 *
 * <p>See setter methods for "knobs" you can turn to control the output of PC and
 * their defaults.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author peterspirtes
 * @author chrismeek
 * @author clarkglymour
 * @author josephramsey
 * @see Fci
 * @see Knowledge
 */
public class Pc implements IGraphSearch {
    private final IndependenceTest independenceTest;
    private final TetradLogger logger = TetradLogger.getInstance();
    private Knowledge knowledge = new Knowledge();
    private SepsetMap sepsets;
    private int depth = 1000;
    private Graph graph;
    private long elapsedTime;
    private int numIndependenceTests;
    private boolean verbose = false;
    private PcCommon.ConflictRule conflictRule = PcCommon.ConflictRule.PRIORITIZE_EXISTING;
    private boolean stable = true;
    private boolean meekPreventCycles = true;
    private boolean useMaxPHeuristic = false;
    private PcCommon.PcHeuristicType pcHeuristicType = PcCommon.PcHeuristicType.NONE;


    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public Pc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException("Independence test is null.");
        }

        this.independenceTest = independenceTest;
    }


    /**
     * Runs PC starting with a complete graph over all nodes of the given conditional independence test, using the given
     * independence test and knowledge and returns the resultant graph. The returned graph will be a CPDAG if the
     * independence information is consistent with the hypothesis that there are no latent common causes. It may,
     * however, contain cycles or bidirected edges if this assumption is not born out, either due to the actual presence
     * of latent common causes, or due to statistical errors in conditional independence judgments.
     *
     * @return The found CPDAG. In some cases there may be some errant bidirected edges or cycles, depending on the
     * settings and whether the faithfulness assumption holds. If the faithfulness assumption holds, bidirected edges
     * will indicate the existence of latent variables, so a latent variable search like FCI should be run.
     * @see Fci
     */
    @Override
    public Graph search() {
        return search(new HashSet<>(this.independenceTest.getVariables()));
    }

    /**
     * Runs PC starting with a complete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a CPDAG if the independence information is
     * consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p>
     * All the given nodes must be in the domain of the given conditional independence test.
     *
     * @param nodes The sublist of nodes to search over.
     * @return The search graph.
     * @see #search()
     */
    public Graph search(Set<Node> nodes) {
        nodes = new HashSet<>(nodes);

        IFas fas = new Fas(getIndependenceTest());
        fas.setVerbose(this.verbose);
        return search(fas, nodes);
    }

    /**
     * Runs the search using a particular implementation of the fast adjacency search (FAS), over the given sublist of
     * nodes.
     *
     * @param fas   The fast adjacency search to use.
     * @param nodes The sublist of nodes.
     * @return The result graph
     * @see #search()
     * @see IFas
     */
    public Graph search(IFas fas, Set<Node> nodes) {
        this.logger.forceLogMessage("Starting PC algorithm");
        this.logger.forceLogMessage("Independence test = " + getIndependenceTest() + ".");

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
        search.setMeekPreventCycles(meekPreventCycles);
        search.setPcHeuristicType(pcHeuristicType);
        search.setKnowledge(this.knowledge);

        if (stable) {
            search.setFasType(PcCommon.FasType.STABLE);
        } else {
            search.setFasType(PcCommon.FasType.REGULAR);
        }

        if (useMaxPHeuristic) {
            search.setColliderDiscovery(PcCommon.ColliderDiscovery.MAX_P);
        } else {
            search.setColliderDiscovery(PcCommon.ColliderDiscovery.FAS_SEPSETS);
        }

        search.setConflictRule(conflictRule);
        search.setPcHeuristicType(pcHeuristicType);
        search.setVerbose(verbose);

        this.graph = search.search();
        this.sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();

        this.logger.forceLogMessage("Returning this graph: " + this.graph);

        this.elapsedTime = MillisecondTimes.timeMillis() - startTime;

        this.logger.forceLogMessage("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.forceLogMessage("Finishing PC Algorithm.");
        this.logger.flush();

        return this.graph;
    }

    /**
     * Sets whether cycles should be checked.
     *
     * @param meekPreventCycles Set to true just in case edges will not be added if they would create cycles.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Returns the independence test being used in the search.
     *
     * @return this test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Returns the knowledge specification used in the search. Non-null.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     *
     * @param knowledge The knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge is null.");
        }

        this.knowledge = knowledge;
    }

    /**
     * Returns the sepset map from the most recent search. Non-null after the first call to <code>search()</code>.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Returns the current depth of search--that is, the maximum number of conditioning nodes for any conditional
     * independence checked. Default is 1000.
     *
     * @return This depth.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the depth of the search--that is, the maximum number of conditioning nodes for any conditional independence
     * checked.
     *
     * @param depth The depth of the search. The default is 1000. A value of -1 may be used to indicate that the depth
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used, due to a bug on multicore
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
     * Returns the elapsed time of the search, in milliseconds.
     *
     * @return this time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns The edges of the searched graph.
     *
     * @return This set.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * Returns the non-adjacencies of the searched graph.
     *
     * @return This set.
     */
    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(this.graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(this.graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    /**
     * Returns the number of independence tests performed in the search.
     *
     * @return This number.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Sets whether verbose output should be given. Default is false.
     *
     * @param verbose True iff the case.
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
     * Sets which conflict rule to use for resolving collider orientation conflicts. Default is
     * ConflictRule.PRIORITIZE_EXISTING.
     *
     * @param conflictRule The rule.
     * @see edu.cmu.tetrad.search.utils.PcCommon.ConflictRule
     */
    public void setConflictRule(PcCommon.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * <p>Sets whether the max-p heuristic should be used for collider discovery. Default is true. See the following
     * reference for this:</p>
     *
     * <p>Ramsey, J. (2016). Improving accuracy and scalability of the pc algorithm by maximizing p-value. arXiv
     * preprint arXiv:1610.00378.</p>
     *
     * @param useMaxPHeuristic True, if so.
     */
    public void setUseMaxPHeuristic(boolean useMaxPHeuristic) {
        this.useMaxPHeuristic = useMaxPHeuristic;
    }

    /**
     * Sets the PC heuristic type. Default = PcHeuristicType.NONE.
     *
     * @param pcHeuristicType The type.
     * @see edu.cmu.tetrad.search.utils.PcCommon.PcHeuristicType
     */
    public void setPcHeuristicType(PcCommon.PcHeuristicType pcHeuristicType) {
        this.pcHeuristicType = pcHeuristicType;
    }
}




