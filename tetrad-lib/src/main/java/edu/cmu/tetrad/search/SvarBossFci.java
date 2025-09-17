///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * SVAR-*GFCI* variant that uses BOSS to obtain the CPDAG (BOSS-FCI), then runs the FCI orientation phase on a
 * SvarEdgeListGraph so that all edge mutations mirror across time lags automatically.
 * <p>
 * - Stage 1 (score): BOSS (PermutationSearch over Boss(score)) - Stage 2 (test):  R0 + FCI orientation rules
 * (test-based) - SVAR: all orientations/removals/additions happen on SvarEdgeListGraph
 * <p>
 * Background knowledge (including temporal tiers) is respected.
 */
public final class SvarBossFci implements IGraphSearch {

    /* ---------- Inputs ---------- */

    /**
     * Represents a conditional independence test used within the context of causal discovery algorithms
     * to check for statistical independence relationships between variables given a set of conditions.
     * This test serves as a key component for constraint-based searches, providing the ability to
     * determine whether variables are independent or dependent with respect to a given data set.
     */
    private final IndependenceTest independenceTest;
    /**
     * Represents the score utilized in the algorithm for evaluating structures or
     * relationships within the graphical model. This variable typically provides
     * a numerical measure of the goodness-of-fit or suitability of a particular
     * model structure given the data. Specific scoring methods and metrics are
     * determined by the {@link Score} interface implementation being used.
     */
    private Score score;
    /**
     * Encapsulates background knowledge for the causal discovery process.
     * This variable allows the specification of prior knowledge, such as forbidden
     * or required causal relationships, that can guide the search algorithm.
     * The {@code Knowledge} object contains information that helps to refine and constrain
     * the causal graph produced during the search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Indicates whether the complete rule set is used during the execution
     * of the algorithm. When set to {@code true}, the algorithm will apply
     * all available rules to refine the graph. When set to {@code false},
     * only a subset of rules will be applied.
     */
    private boolean completeRuleSetUsed = false;
    /**
     * Represents the maximum allowed length of a discriminating path in the
     * causal search algorithm executed by the SvarBossFci class. A discriminating
     * path is a specific type of path used during causal inference to resolve
     * ambiguous orientations or identify specific structural dependencies between
     * variables in the graph.
     *
     * Defaults to -1, which typically indicates that no limit is imposed on the
     * discriminating path length. This configuration may influence the complexity
     * and scope of the causal search.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * A flag indicating whether verbose output is enabled.
     *
     * When set to true, the system will generate detailed logging or
     * descriptive output that provides additional information about its
     * internal processes and computations.
     *
     * By default, this is set to false to reduce unnecessary output
     * in standard operations.
     */
    private boolean verbose = false;
    /**
     * A flag indicating whether the algorithm should attempt to resolve
     * paths that are almost cyclic in the graph.
     *
     * When set to {@code true}, additional processing is performed to detect
     * and address paths that nearly form cycles, which can influence the
     * accuracy of the resultant graph. This functionality is optional and,
     * depending on the use case, may introduce additional computational overhead.
     *
     * The default value is {@code false}.
     */
    private boolean resolveAlmostCyclicPaths = false;

    /**
     * The number of random starting configurations for a search algorithm.
     * This variable is used to control the number of independent runs initiated
     * by the algorithm. Each starting configuration may lead to different results
     * due to randomization in the process, enabling better exploration of the
     * solution space.
     */
    private int numStarts = 1;
    /**
     * A flag indicating whether the "Boss" search method utilizes
     * Best Equivalence Search (BES) for model selection during the search process.
     *
     * When set to true, the bossUseBes variable enables BES as part of the
     * search algorithm to identify causal structures. Otherwise, BES
     * is not employed.
     */
    private boolean bossUseBes = false;
    /**
     * Specifies the number of threads to be used for parallel operations during execution.
     * A higher value may improve performance for multi-threaded tasks but can also increase
     * resource usage. Defaults to 1, indicating a single-threaded execution.
     */
    private int numThreads = 1;

    /* ---------- Internals ---------- */

    /**
     * Represents the working PAG (Partial Ancestral Graph) within the SvarBossFci algorithm,
     * specifically implemented as a SvarEdgeListGraph after the CPDAG (Completed Partially Directed Acyclic Graph) phase.
     * This graph is utilized throughout the execution of the causal discovery process.
     */
    private Graph graph;                 // working PAG (SvarEdgeListGraph after CPDAG)
    /**
     * Used to store and manage MinP sepsets for test-based orientations
     * in the SvarBossFci search procedure. The SepsetProducer interface
     * provides the underlying functionality for generating and accessing
     * sepsets, which represent sets of nodes that separate two given nodes
     * in a graph.
     */
    private SepsetProducer sepsets;      // MinP sepsets for test-based orientations
    /**
     * Represents the covariance matrix associated with the search procedure in the {@code SvarBossFci} algorithm.
     * Serves as an encapsulation or interface to access covariance-related operations and data during execution.
     * Implementations of {@code ICovarianceMatrix} are used to manage and retrieve information about the
     * covariance structure, such as matrix values, dimensions, variables, and related metadata.
     */
    private ICovarianceMatrix covarianceMatrix;

    /**
     * Constructs an instance of SvarBossFci with the given independence test and score.
     *
     * @param test The independence test to be used for the structure discovery process.
     *             It provides an interface for conditional independence testing.
     * @param score The score function to evaluate the quality of a given graph structure.
     *              It is used for searching and scoring candidate structures.
     * @throws NullPointerException If either the independence test or score is null.
     */
    public SvarBossFci(IndependenceTest test, Score score) {
        if (test == null) throw new NullPointerException("IndependenceTest is null");
        if (score == null) throw new NullPointerException("Score is null");
        this.independenceTest = test;
        this.score = score;
    }
    /**
     * Executes the SVAR BOSS-FCI algorithm to perform causal structure discovery.
     * <p>
     * The method integrates multiple stages:
     * </p>
     * <ul>
     *   <li>Performs a baseline structure discovery using BOSS (permutation search) to obtain a CPDAG.</li>
     *   <li>Converts the CPDAG into a SVAR-mirroring graph for further processing.</li>
     *   <li>Builds sepsets for independence constraints using the Min-P criterion.</li>
     *   <li>Applies a modified R0 bootstrap for basic orientations.</li>
     *   <li>Executes further orientation using the FCI approach with SVAR-specific strategies.</li>
     *   <li>Optionally resolves almost cyclic paths to refine the structure.</li>
     * </ul>
     *
     * <p>This method utilizes the specified independence test and score to guide the search
     * and orientation of the output graph. Additional configurable options such as knowledge
     * constraints, complete rule sets, or cyclic path resolution are incorporated into the process.</p>
     *
     * <p>The final output is a directed graph structure that represents the discovered causal relationships.</p>
     *
     * @return A causal graph that reflects the SVAR-specific constraints, orientations,
     *         and independence relationships found during the execution of the SVAR BOSS-FCI algorithm.
     * @throws InterruptedException if the execution of the search is interrupted.
     */
    @Override
    public Graph search() throws InterruptedException {
        independenceTest.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Starting SVAR BOSS-FCI.");
            TetradLogger.getInstance().log("Independence test = " + this.independenceTest + ".");
        }

        if (this.score == null) {
            chooseScore(); // fallback if constructed with null score in future use
        }

        // -------- Stage 1: BOSS to get CPDAG --------
        if (verbose) TetradLogger.getInstance().log("Running BOSS (permutation search)...");
        Boss boss = new Boss(this.score);
        boss.setUseBes(bossUseBes);
        boss.setNumStarts(numStarts);
        boss.setNumThreads(numThreads);
        boss.setVerbose(verbose);

        PermutationSearch perm = new PermutationSearch(boss);
        perm.setKnowledge(this.knowledge);
        Graph cpdag = perm.search();

        if (verbose) TetradLogger.getInstance().log("BOSS complete.");

        // -------- Switch to SVAR-mirroring graph --------
        // Copy CPDAG into a SvarEdgeListGraph so all future mutations are mirrored across lags.
        this.graph = new SvarEdgeListGraph(cpdag);
        Graph bossGraph = new EdgeListGraph(cpdag); // frozen view for def-collider checks below

        // -------- Build Min-P sepsets from the BOSS graph --------
        int maxIndegree = -1;
        this.sepsets = new SepsetsMinP(bossGraph, this.independenceTest, maxIndegree);

        // -------- R0 bootstrap (same spirit as SvarGfci) --------
        modifiedR0(bossGraph);

        // -------- FCI orientation (test-based), using SVAR endpoint strategy --------
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.setEndpointStrategy(new SvarSetEndpointStrategy(this.independenceTest, this.knowledge));
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(this.maxDiscriminatingPathLength);
        fciOrient.setVerbose(this.verbose);

        fciOrient.finalOrientation(this.graph);

        // Optional cleanup for almost cyclic paths
        if (resolveAlmostCyclicPaths) {
            for (Edge edge : new ArrayList<>(graph.getEdges())) {
                if (Edges.isBidirectedEdge(edge)) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();
                    if (graph.paths().existsDirectedPath(x, y)) {
                        graph.setEndpoint(y, x, Endpoint.TAIL);
                    } else if (graph.paths().existsDirectedPath(y, x)) {
                        graph.setEndpoint(x, y, Endpoint.TAIL);
                    }
                }
            }
        }

        GraphUtils.replaceNodes(this.graph, this.independenceTest.getVariables());

        if (verbose) TetradLogger.getInstance().log("Finished SVAR BOSS-FCI.");
        return this.graph;
    }

    /* ---------- Public knobs (mirrors SvarGfci/BossFci) ---------- */

    /**
     * Sets the knowledge used for constraining the structure discovery process.
     *
     * @param knowledge The background knowledge specifying allowable edges and orientations
     *                  to guide the causal structure search. Must not be null.
     * @throws NullPointerException If the provided knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Sets whether the complete rule set should be used in the causal structure discovery process.
     *
     * @param completeRuleSetUsed A boolean value indicating whether the complete set of rules
     *                            should be applied during the structure discovery. True means
     *                            that all the rules will be used, while false restricts the process
     *                            to a subset of rules.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum discriminating path length to be used in the structure discovery process.
     * The discriminating path length determines the maximum number of steps to consider in identifying
     * discriminating paths during causal analysis. A value of -1 indicates no limit on the path length.
     *
     * @param maxDiscriminatingPathLength The maximum length of discriminating paths. Must be -1 or a non-negative integer.
     *                                    If a value less than -1 is provided, an {@link IllegalArgumentException} is thrown.
     *                                    A value of -1 implies no constraints on path length.
     * @throws IllegalArgumentException If the provided value is less than -1.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 or >= 0: " + maxDiscriminatingPathLength);
        }
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the verbosity level for logging and debugging information.
     * @param verbose If true, enables verbose logging; otherwise, disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether to resolve almost cyclic paths during causal analysis.
     * @param resolveAlmostCyclicPaths If true, resolves almost cyclic paths; otherwise, ignores them.
     */
    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
    }

    /**
     * Sets the number of starting points for the search algorithm.
     * @param numStarts The number of starting points for the search algorithm.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether to use Bayesian Estimation (BE) in the search algorithm.
     * @param useBes If true, uses BE; otherwise, uses other methods.
     */
    public void setBossUseBes(boolean useBes) {
        this.bossUseBes = useBes;
    }

    /**
     * Sets the number of threads for parallel processing.
     * @param numThreads The number of threads for parallel processing.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) throw new IllegalArgumentException("numThreads must be >= 1");
        this.numThreads = numThreads;
    }

    /* ---------- Helpers ---------- */

    private void chooseScore() {
        double penaltyDiscount = 2.0;

        DataSet dataSet = (DataSet) this.independenceTest.getData();
        ICovarianceMatrix cov = this.independenceTest.getCov();

        if (this.independenceTest instanceof MsepTest msep) {
            this.score = new GraphScore(msep.getGraph());
            return;
        }
        if (cov != null) {
            this.covarianceMatrix = cov;
            SemBicScore s = new SemBicScore(cov);
            s.setPenaltyDiscount(penaltyDiscount);
            this.score = s;
            return;
        }
        if (dataSet.isContinuous()) {
            this.covarianceMatrix = new CovarianceMatrix(dataSet);
            SemBicScore s = new SemBicScore(this.covarianceMatrix);
            s.setPenaltyDiscount(penaltyDiscount);
            this.score = s;
            return;
        }
        if (dataSet.isDiscrete()) {
            BdeuScore s = new BdeuScore(dataSet);
            s.setSamplePrior(10.0);
            s.setStructurePrior(1.0);
            this.score = s;
            return;
        }
        throw new IllegalArgumentException("Mixed data not supported.");
    }

    /**
     * Bootstrap orientations (R0-style) using BK and Min-P structure, matching the pattern used in SvarGfci. All
     * setEndpoint calls operate on {@link SvarEdgeListGraph}, so orientations mirror across lags.
     */
    private void modifiedR0(Graph bossGraph) throws InterruptedException {
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        List<Node> nodes = this.graph.getNodes();
        for (Node b : nodes) {
            List<Node> adj = new ArrayList<>(this.graph.getAdjacentNodes(b));
            if (adj.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                Node a = adj.get(comb[0]);
                Node c = adj.get(comb[1]);

                if (bossGraph.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                    orientSimilarPairs(this.graph, this.knowledge, a, b);
                    orientSimilarPairs(this.graph, this.knowledge, c, b);

                } else if (bossGraph.isAdjacentTo(a, c) && !this.graph.isAdjacentTo(a, c)) {
                    Set<Node> sepset = this.sepsets.getSepset(a, c, -1, null);
                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                        orientSimilarPairs(this.graph, this.knowledge, a, b);
                        orientSimilarPairs(this.graph, this.knowledge, c, b);
                    }
                }
            }
        }
    }

    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        if (verbose) TetradLogger.getInstance().log("Starting BK Orientation.");
        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);
            if (from == null || to == null) continue;
            if (graph.getEdge(from, to) == null) continue;

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);

            if (verbose) TetradLogger.getInstance().log(
                    LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);
            if (from == null || to == null) continue;
            if (graph.getEdge(from, to) == null) continue;

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (verbose) TetradLogger.getInstance().log(
                    LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        if (verbose) TetradLogger.getInstance().log("Finishing BK Orientation.");
    }

    private String getNameNoLag(Object obj) {
        String s = obj.toString();
        int i = s.indexOf(':');
        return (i == -1) ? s : s.substring(0, i);
    }

    private void removeSimilarEdges(Node x, Node y) {
        List<List<Node>> sim = returnSimilarPairs(x, y);
        if (sim.isEmpty()) return;
        List<Node> xs = sim.get(0);
        List<Node> ys = sim.get(1);
        Iterator<Node> itx = xs.iterator();
        Iterator<Node> ity = ys.iterator();
        while (itx.hasNext() && ity.hasNext()) {
            Node x1 = itx.next();
            Node y1 = ity.next();
            Edge e = this.graph.getEdge(x1, y1);
            if (e != null) this.graph.removeEdge(e);
        }
    }

    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y) {
        if ("time".equals(x.getName()) || "time".equals(y.getName())) return;

        int ntiers = knowledge.getNumTiers();
        int tx = knowledge.isInWhichTier(x);
        int ty = knowledge.isInWhichTier(y);
        int diff = FastMath.max(tx, ty) - FastMath.min(tx, ty);

        List<String> tierX = knowledge.getTier(tx);
        List<String> tierY = knowledge.getTier(ty);

        int ix = -1, iy = -1;
        for (int i = 0; i < tierX.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tierX.get(i)))) {
                ix = i;
                break;
            }
        }
        for (int i = 0; i < tierY.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tierY.get(i)))) {
                iy = i;
                break;
            }
        }

        for (int i = 0; i < ntiers - diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;

            List<String> t1 = (tx >= ty) ? knowledge.getTier(i + diff) : knowledge.getTier(i);
            List<String> t2 = (tx >= ty) ? knowledge.getTier(i) : knowledge.getTier(i + diff);

            String A = t1.get(ix);
            String B = t2.get(iy);
            if (A.equals(B)) continue;
            if (A.equals(tierX.get(ix)) && B.equals(tierY.get(iy))) continue;
            if (B.equals(tierX.get(ix)) && A.equals(tierY.get(iy))) continue;

            Node x1 = this.independenceTest.getVariable(A);
            Node y1 = this.independenceTest.getVariable(B);

            if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                graph.setEndpoint(x1, y1, Endpoint.ARROW);
                if (verbose) {
                    TetradLogger.getInstance().log("Orient edge " + graph.getEdge(x1, y1));
                    TetradLogger.getInstance().log(" by structure knowledge as: " + graph.getEdge(x1, y1));
                }
            }
        }
    }

    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        if ("time".equals(x.getName()) || "time".equals(y.getName())) {
            return new ArrayList<>();
        }

        int ntiers = this.knowledge.getNumTiers();
        int tx = this.knowledge.isInWhichTier(x);
        int ty = this.knowledge.isInWhichTier(y);
        int diff = FastMath.max(tx, ty) - FastMath.min(tx, ty);

        List<String> tierX = this.knowledge.getTier(tx);
        List<String> tierY = this.knowledge.getTier(ty);

        int ix = -1, iy = -1;
        for (int i = 0; i < tierX.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tierX.get(i)))) {
                ix = i;
                break;
            }
        }
        for (int i = 0; i < tierY.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tierY.get(i)))) {
                iy = i;
                break;
            }
        }

        List<Node> simX = new ArrayList<>();
        List<Node> simY = new ArrayList<>();

        for (int i = 0; i < ntiers - diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;

            List<String> t1, t2;
            if (tx >= ty) {
                t1 = this.knowledge.getTier(i + diff);
                t2 = this.knowledge.getTier(i);
            } else {
                t1 = this.knowledge.getTier(i);
                t2 = this.knowledge.getTier(i + diff);
            }

            String A = t1.get(ix);
            String B = t2.get(iy);
            if (A.equals(B)) continue;
            if (A.equals(tierX.get(ix)) && B.equals(tierY.get(iy))) continue;
            if (B.equals(tierX.get(ix)) && A.equals(tierY.get(iy))) continue;

            Node x1 = this.graph.getNode(A);
            Node y1 = this.graph.getNode(B);
            simX.add(x1);
            simY.add(y1);
        }

        List<List<Node>> out = new ArrayList<>();
        out.add(simX);
        out.add(simY);
        return out;
    }
}
