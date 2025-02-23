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
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the Peter/Clark (PC) algorithm, which uses conditional independence testing as an oracle to first of all
 * remove extraneous edges from a complete graph, then to orient the unshielded colliders in the graph, and finally to
 * make any additional orientations that are capable of avoiding additional unshielded colliders in the graph. A version
 * of this algorithm was proposed earlier than this, but the standard reference for the algorithm is in Chapter 6 of the
 * following book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * A modified rule set capable of dealing effectively with knowledge of required and forbidden edges is due to Chris
 * Meek, with this reference:
 * <p>
 * Meek, C. (1995), "Causal inference and causal explanation with background knowledge."
 * <p>
 * See setter methods for "knobs" you can turn to control the output of PC and their defaults.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author chrismeek
 * @author clarkglymour
 * @author josephramsey
 * @version $Id: $Id
 * @see Fci
 * @see Knowledge
 */
public class Pc implements IGraphSearch {
    /**
     * The oracle for conditional independence facts.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The knowledge specification.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The sepset map from the most recent search.
     */
    private SepsetMap sepsets;
    /**
     * The depth of the search.
     */
    private int depth = 1000;
    /**
     * The graph from the most recent search.
     */
    private Graph graph;
    /**
     * Represents the start time of the algorithm or process execution within the PC search implementation.
     * Tracks the time when the execution begins, typically measured in milliseconds since the epoch.
     * This is used for calculating execution duration and performance analysis.
     */
    private final long startTime = -1L;
    /**
     * The timeout duration for the search process, in milliseconds.
     * If set to -1, it indicates that no timeout is enforced.
     */
    private final long timeout = -1L;
    /**
     * The elapsed time of the most recent search.
     */
    private long elapsedTime;
    /**
     * Whether the search is verbose.
     */
    private boolean verbose = false;
    /**
     * The rule to use for resolving collider orientation conflicts.
     */
    private PcCommon.ConflictRule conflictRule = PcCommon.ConflictRule.PRIORITIZE_EXISTING;
    /**
     * Whether the stable adjacency search should be used.
     */
    private boolean stable = true;
    /**
     * Whether cycles should be checked in the Meek rules.
     */
    private boolean guaranteeCpdag = true;
    /**
     * Whether the max-p heuristic should be used for collider discovery.
     */
    private boolean useMaxPHeuristic = false;
    /**
     * The PC heuristic type.
     */
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
     * @see Fci
     * @throws InterruptedException if any
     */
    @Override
    public Graph search() throws InterruptedException {
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
     * @throws InterruptedException if any
     */
    public Graph search(Set<Node> nodes) throws InterruptedException {
        nodes = new HashSet<>(nodes);

        IFas fas = new Fas(getIndependenceTest());
        fas.setVerbose(this.verbose);

        // These only work if you use Fas itself, not other implementations of IFas. This is needed is yo use,
        // e.g., Kci as a test, which can take a long time. In the interface you can stop the algorithm yourself,
        // but if you need to run PC/KCI e.g., in a loop, you need a timeout. jdramsey 2025-2-23
        fas.setStartTime(startTime);
        fas.setTimeout(timeout);

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
     * @throws InterruptedException if any
     */
    public Graph search(IFas fas, Set<Node> nodes) throws InterruptedException {
        if (verbose) {
            this.logger.log("Starting PC algorithm");
            this.logger.log("Independence test = " + getIndependenceTest() + ".");
        }

        long startTime = MillisecondTimes.timeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException("Null independence test.");
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                                               "be in the domain of the independence test provided.");
        }

        PcCommon search = getPcCommon();
        search.setStartTime(this.startTime);
        search.setTimout(this.timeout);

        this.graph = search.search();
        this.sepsets = fas.getSepsets();

        this.elapsedTime = MillisecondTimes.timeMillis() - startTime;

        if (verbose) {
            this.logger.log("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
            this.logger.log("Finishing PC Algorithm.");
            this.logger.flush();
        }

        return this.graph;
    }

    /**
     * Retrieves an instance of the {@link PcCommon} class with the specified configurations.
     *
     * @return The {@link PcCommon} instance.
     */
    @NotNull
    private PcCommon getPcCommon() {
        PcCommon search = new PcCommon(independenceTest);
        search.setDepth(depth);
        search.setGuaranteeCpdag(guaranteeCpdag);
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
        return search;
    }

    /**
     * Sets whether cycles should be checked.
     *
     * @param guaranteeCpdag Set to true just in case edges will not be added if they create cycles.
     */
    public void setGuaranteeCpdag(boolean guaranteeCpdag) {
        this.guaranteeCpdag = guaranteeCpdag;
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
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used due to a bug on multicore
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
     * Sets whether verbose output should be given. Default is false.
     *
     * @param verbose True iff the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether the stable adjacency search should be used. Default is false. Default is false. See the following
     * reference for this:
     * <p>
     * Colombo, D., &amp; Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. J. Mach.
     * Learn. Res., 15(1), 3741-3782.
     *
     * @param stable True iff the case.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets which conflict-rule to use for resolving collider orientation conflicts. Default is
     * ConflictRule.PRIORITIZE_EXISTING.
     *
     * @param conflictRule The rule.
     * @see edu.cmu.tetrad.search.utils.PcCommon.ConflictRule
     */
    public void setConflictRule(PcCommon.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * Sets whether the max-p heuristic should be used for collider discovery. Default is true. See the following
     * reference for this:
     * <p>
     * Ramsey, J. (2016). Improving the accuracy and scalability of the pc algorithm by maximizing p-value. arXiv
     * preprint arXiv:1610.00378.
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




