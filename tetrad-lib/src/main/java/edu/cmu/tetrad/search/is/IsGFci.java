/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Implements instance-specific FGES-FCI, following the idea introduced by Fattaneh Jabbari in her dissertation (pp.
 * 144–147). The goal is to adapt score-based causal discovery to make inferences not just at the population level, but
 * also for a given individual case (a single "test row").
 * <p>
 * Standard FGES searches for a population-wide graph by evaluating candidate edge additions, deletions, and reversals
 * with a decomposable score such as BIC or BDeu. Instance-specific FGES modifies this process by using an
 * {@link IsScore}, which augments the usual population likelihood with instance-specific likelihood terms that
 * condition on the values of the test case. In this way, the score rewards structures that explain not only the data
 * overall but also the observed values for the instance in question. A structure prior further penalizes deviations
 * from the population model (e.g., instance-only additions, deletions, or reversals).
 * <p>
 * The resulting search produces an instance-specific backbone graph that reflects both population regularities and
 * instance-specific refinements. This graph is then refined using FCI-style pruning and orientation rules, producing an
 * instance-specific PAG that accounts for possible latent confounders and selection effects. The outcome can therefore
 * differ across test cases: two individuals with different attribute values may yield different instance-specific
 * graphs, even when drawn from the same population.
 *
 * @author Fattaneh
 */
//@Deprecated
public final class IsGFci implements IGraphSearch {

    private final Score populationScore;
    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;
    // The sample size.
    int sampleSize;
    // The PAG being constructed.
    private Graph graph;
    // The background knowledge.
    private Knowledge knowledge = new Knowledge();
    // The conditional independence test.
    private IndependenceTest independenceTest;
    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = false;
    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;
    // The maxDegree for the fast adjacency search.
    private int maxDegree = -1;
    // The logger to use.
    private TetradLogger logger = TetradLogger.getInstance();
    // True iff verbose output should be printed.
    private boolean verbose = false;
    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // True iff one-edge faithfulness is assumed. Speed up the algorithm for very large searches. By default false.
    private boolean faithfulnessAssumed = true;

    // The instance-specific score.
    private IsScore score;

    // The population-wide graph
    private Graph populationGraph;

    private SepsetProducer sepsets;
    private long elapsedTime;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs an instance of IGFci with the provided independence test and score.
     *
     * @param test  the IndependenceTest instance to be used; must not be null.
     * @param score the ISScore instance to be used; must not be null.
     * @param populationScore the population-wide score used for scoring population-wide structures.
     * @throws NullPointerException if the provided score is null.
     */
    public IsGFci(IndependenceTest test, IsScore score, Score populationScore) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.populationScore = populationScore;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Executes the FCI algorithm using the provided independence test, score, and population graph, and returns the
     * resulting graph with edges oriented according to the algorithm's rules. The algorithm first constructs an initial
     * graph, prunes it based on separation sets, and further refines it using multiple phases of orientation rules. The
     * time taken to complete the search is recorded and stored in the elapsedTime field.
     *
     * @return the final oriented graph obtained after applying the FCI algorithm.
     */
    public Graph search() throws InterruptedException {
        long t0 = System.currentTimeMillis();

        final List<Node> nodes = getIndependenceTest().getVariables();
        // inside search()
        logger.log("Starting IG-FCI (instance-specific FGES→FCI).");
        this.graph = new EdgeListGraph(nodes);

        // 1) Instance-specific FGES (propagate settings)
        IsFges fges = new IsFges(score, populationScore);
        fges.setKnowledge(this.knowledge);
        fges.setVerbose(this.verbose);
        fges.setOut(this.out);
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        if (this.maxDegree >= 0) fges.setMaxDegree(this.maxDegree);
        if (this.populationGraph != null) {
            fges.setPopulationGraph(this.populationGraph);
            fges.setInitialGraph(this.populationGraph);
        }
        Graph fgesGraph = fges.search();
        this.graph = new EdgeListGraph(fgesGraph);

        // 2) Sepsets constrained by FGES adjacencies
        this.sepsets = new SepsetsGreedy(fgesGraph, independenceTest, this.maxDegree);

        // ... rest unchanged

        // Triangle-based prune: if a–c is present and test finds a sepset, remove a–c.
        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) break;

            final List<Node> adjB = fgesGraph.getAdjacentNodes(b);
            if (adjB.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjB.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) break;
                Node a = adjB.get(comb[0]);
                Node c = adjB.get(comb[1]);

                if (!graph.isAdjacentTo(a, c)) continue;

                Set<Node> s = sepsets.getSepset(a, c, this.maxDegree, null);
                if (s != null) {
                    graph.removeEdge(a, c);
                }
            }
        }

        // ----- 3) R0-like pass tied to FGES definites + test-based collider completion
        modifiedR0(fgesGraph);

        // ----- 4) FCI orientation phases (R0/R4 + rest)
        R0R4Strategy r0r4 = new R0R4StrategyTestBased(independenceTest);
        FciOrient fciOrient = new FciOrient(r0r4);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxPathLength);
        fciOrient.finalOrientation(graph);

        // Keep node identities aligned with the test’s variables
        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        this.elapsedTime = System.currentTimeMillis() - t0;
        logger.log("IG-FCI finished in " + this.elapsedTime + " ms.");
        return graph;
    }

    // Due to Spirtes; modified for interruptibility and guards.

    /**
     * Modifies the given FGES graph based on the FCI algorithm rules, reorienting edges and potentially identifying and
     * orienting definite colliders.
     *
     * @param fgesGraph the FGES Graph to be processed; must not be null.
     * @throws InterruptedException if the search is interrupted.
     */
    public void modifiedR0(Graph fgesGraph) throws InterruptedException {
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, graph, graph.getNodes());

        final List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) return;

            final List<Node> adjB = graph.getAdjacentNodes(b);
            if (adjB.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjB.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;

                Node a = adjB.get(comb[0]);
                Node c = adjB.get(comb[1]);

                // 1) Respect FGES-definite collider at b
                if (fgesGraph.isDefCollider(a, b, c)) {
                    orientToCollider(a, b, c);
                    continue;
                }

                // 2) If FGES kept a–c AND graph currently has no a–c, test-based collider check
                if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    Set<Node> s = sepsets.getSepset(a, c, this.maxDegree, null);
                    if (s != null && !s.contains(b)) {
                        orientToCollider(a, b, c);
                    }
                }
            }
        }
    }

    // Small safe-orient helper.
    private void orientToCollider(Node a, Node b, Node c) {
        Edge eab = graph.getEdge(a, b);
        Edge ecb = graph.getEdge(c, b);
        if (eab != null) {
            graph.setEndpoint(a, b, Endpoint.ARROW);
        }
        if (ecb != null) {
            graph.setEndpoint(c, b, Endpoint.ARROW);
        }
    }

    // Orients according to background knowledge (kept as-is with edge guards)
    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> vars) {
        logger.log("Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), vars);
            Node to = GraphSearchUtils.translate(edge.getTo(), vars);
            if (from == null || to == null) continue;

            Edge e = graph.getEdge(from, to);
            if (e == null) continue;

            // Orient to *-> from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), vars);
            Node to = GraphSearchUtils.translate(edge.getTo(), vars);
            if (from == null || to == null) continue;

            Edge e = graph.getEdge(from, to);
            if (e == null) continue;

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("Finishing BK Orientation.");
    }

    /**
     * Retrieves the elapsed time in milliseconds that the algorithm or search process has taken to execute.
     *
     * @return the elapsed time in milliseconds as a long value.
     */
    public long getElapsedTimeMillis() {
        return elapsedTime;
    }

    /**
     * Retrieves the maximum degree for the graph.
     *
     * @return the maximum degree, or -1 if it is unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * Sets the maximum degree for the graph.
     *
     * @param maxDegree the maximum degree, where -1 indicates unlimited. Must be -1 or a non-negative integer.
     * @throws IllegalArgumentException if maxDegree is less than -1.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    // Due to Spirtes.

//    /**
//     * Modifies the given FGES graph based on the FCI algorithm rules, reorienting edges and potentially identifying and
//     * orienting definite colliders.
//     *
//     * @param fgesGraph the FGES Graph to be processed; must not be null.
//     * @throws InterruptedException if the search is interrupted.
//     */
//    public void modifiedR0(Graph fgesGraph) throws InterruptedException {
//        graph.reorientAllWith(Endpoint.CIRCLE);
//        fciOrientbk(knowledge, graph, graph.getNodes());
//
//        List<Node> nodes = graph.getNodes();
//
//        for (Node b : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(b);
//
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                Node a = adjacentNodes.get(combination[0]);
//                Node c = adjacentNodes.get(combination[1]);
//
//                if (fgesGraph.isDefCollider(a, b, c)) {
//                    graph.setEndpoint(a, b, Endpoint.ARROW);
//                    graph.setEndpoint(c, b, Endpoint.ARROW);
//                } else if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
//                    Set<Node> sepset = sepsets.getSepset(a, c, -1, null);
//
//                    if (sepset != null && !sepset.contains(b)) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                    }
//                }
//            }
//        }
//    }

    /**
     * Returns the knowledge used in the IGFci algorithm.
     *
     * @return the Knowledge object currently used by the IGFci algorithm.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge for the IGFci algorithm.
     *
     * @param knowledge the Knowledge object to be set; must not be null.
     * @throws NullPointerException if the provided knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Checks if the complete rule set is being used in the IGFci algorithm.
     *
     * @return true if the complete rule set is used, false otherwise.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * Sets whether the complete rule set should be used in the IGFci algorithm.
     *
     * @param completeRuleSetUsed true if the complete rule set is to be used, false otherwise
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Retrieves the maximum length of any path, or -1 if unlimited.
     *
     * @return the maximum length of any path, or -1 if unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * Sets the maximum length for any path in the graph. The value must be -1 to indicate unlimited length or a
     * non-negative integer to specify the maximum path length.
     *
     * @param maxPathLength the maximum path length to be set, where -1 indicates unlimited. Must be -1 or a
     *                      non-negative integer.
     * @throws IllegalArgumentException if maxPathLength is less than -1.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Checks if verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose true if verbose output should be printed, false otherwise
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the current independence test object being used.
     *
     * @return the IndependenceTest object currently set for this instance.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * Sets the independence test for the IGFci algorithm.
     *
     * @param independenceTest the IndependenceTest object to be set; must not be null.
     */
    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    /**
     * Retrieves the current covariance matrix object used by the IGFci algorithm.
     *
     * @return the ICovarianceMatrix object currently set for this instance.
     */
    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    /**
     * Retrieves the current covariance matrix object used by the IGFci algorithm.
     *
     * @return the ICovarianceMatrix object currently set for this instance.
     */
    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    /**
     * Sets the covariance matrix to be used in the IGFci algorithm.
     *
     * @param covarianceMatrix the ICovarianceMatrix object to be set; must not be null.
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    /**
     * Retrieves the current PrintStream used for output by the IGFci algorithm.
     *
     * @return the PrintStream object currently set for this instance.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the PrintStream object used for output by the IGFci instance.
     *
     * @param out the PrintStream object to be used for output; must not be null.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets whether faithfulness is assumed in the IGFci algorithm.
     *
     * @param faithfulnessAssumed true if faithfulness is assumed, false otherwise
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    private static DataSet alignColumnsByName(DataSet instance, DataSet train) {
        // 1) Build column index mapping from train variable names -> instance column index.
        final List<edu.cmu.tetrad.graph.Node> trainVars = train.getVariables();
        final int p = trainVars.size();
        final int[] cols = new int[p];

        for (int i = 0; i < p; i++) {
            final String name = trainVars.get(i).getName();
            edu.cmu.tetrad.graph.Node instVar = instance.getVariable(name);
            if (instVar == null) {
                throw new IllegalArgumentException("Instance dataset is missing variable: " + name);
            }
            cols[i] = instance.getColumn(instVar);
        }

        // 2) Reorder instance columns to the train order.
        DataSet reordered = instance.subsetColumns(cols);

        // 3) For discrete variables, ensure categories match by LABELS.
        //    If label orders differ, remap ints in `reordered` to train’s label order.
        for (int j = 0; j < p; j++) {
            if (trainVars.get(j) instanceof edu.cmu.tetrad.data.DiscreteVariable tv
                && reordered.getVariable(j) instanceof edu.cmu.tetrad.data.DiscreteVariable iv) {

                // Build label->index maps
                List<String> tLabels = tv.getCategories();
                List<String> iLabels = iv.getCategories();

                // Fast path: same order & labels
                if (tLabels.equals(iLabels)) continue;

                // Check same label sets
                if (!(new java.util.HashSet<>(tLabels).equals(new java.util.HashSet<>(iLabels)))) {
                    throw new IllegalArgumentException(
                            "Discrete categories differ for variable '" + tv.getName() +
                            "'. Train labels=" + tLabels + ", Instance labels=" + iLabels);
                }

                // Build remap: instanceIndex -> trainIndex
                int K = iLabels.size();
                int[] remap = new int[K];
                java.util.Map<String, Integer> trainIndexByLabel = new java.util.HashMap<>();
                for (int k = 0; k < K; k++) trainIndexByLabel.put(tLabels.get(k), k);
                for (int k = 0; k < K; k++) remap[k] = trainIndexByLabel.get(iLabels.get(k));

                // Apply remap in place to column j
                for (int r = 0; r < reordered.getNumRows(); r++) {
                    int val = reordered.getInt(r, j);
                    if (val == -99) continue; // keep your missing sentinel
                    if (val < 0 || val >= K) {
                        throw new IllegalArgumentException("Out-of-range category at row " + r + ", var " + tv.getName());
                    }
                    reordered.setInt(r, j, remap[val]);
                }

                // Also replace the variable metadata with train’s variable to carry train labels/order
                reordered.setVariable(j, tv);
            }
        }

        return reordered;
    }

    /** Convenience: produce a SINGLE-ROW test case aligned to train, with range checks. */
    private static DataSet alignedSingleRow(DataSet instanceFull, DataSet train, int rowIndex) {
        if (rowIndex < 0 || rowIndex >= instanceFull.getNumRows()) {
            throw new IllegalArgumentException("Requested instance row " + rowIndex +
                                               " out of range [0, " + (instanceFull.getNumRows() - 1) + "].");
        }
        DataSet aligned = alignColumnsByName(instanceFull, train);
        return aligned.subsetRows(new int[]{rowIndex});
    }
}

