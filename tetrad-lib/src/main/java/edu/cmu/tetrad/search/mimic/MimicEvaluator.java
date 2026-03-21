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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Evaluates estimated MIMIC-style graphs against a true {@link MimicModel}.
 *
 * <p>The current evaluation is latent-centric. Each latent is summarized by its measured
 * input-parent set and measured output-child set. Similarity between a true latent and an
 * estimated latent is defined as the average of the Jaccard similarities of these two sets.
 * A greedy maximum-similarity matching is then computed between true and estimated latents.</p>
 *
 * <p>This is intended as a first practical score for comparing MIMIC algorithms. More
 * structural scores can be added later.</p>
 *
 * @author josephramsey
 */
public final class MimicEvaluator {

    /**
     * Constructs a new instance of the MimicEvaluator class.
     *
     * This constructor initializes the MimicEvaluator, which is responsible for conducting
     * evaluations of estimated graphs against true MIMIC models. The class provides various
     * methods to perform comparisons, calculate similarity metrics, and determine optimal
     * matching between latent signatures of true and estimated models.
     */
    public MimicEvaluator() {}

    /**
     * Evaluates an estimated graph against the true model.
     *
     * @param trueModel the true MIMIC model
     * @param estimatedGraph the estimated graph
     * @return the evaluation summary
     */
    public MimicEvaluation evaluate(MimicModel trueModel, Graph estimatedGraph) {
        List<LatentSignature> trueLatents      = getTrueLatentSignatures(trueModel);
        List<LatentSignature> estimatedLatents = getEstimatedLatentSignatures(estimatedGraph);

        // optimalMatch replaces greedyMatch: guaranteed globally maximum similarity.
        List<Match> matches = optimalMatch(trueLatents, estimatedLatents);

        double sumLatent = 0.0;
        double sumInput  = 0.0;
        double sumOutput = 0.0;

        for (Match match : matches) {
            sumLatent += match.totalSimilarity;
            sumInput  += match.inputSimilarity;
            sumOutput += match.outputSimilarity;
        }

        int matched = matches.size();

        double avgLatent = matched == 0 ? 0.0 : sumLatent / matched;
        double avgInput  = matched == 0 ? 0.0 : sumInput  / matched;
        double avgOutput = matched == 0 ? 0.0 : sumOutput / matched;

        return new MimicEvaluation(
                trueLatents.size(),
                estimatedLatents.size(),
                matched,
                avgLatent,
                avgInput,
                avgOutput
        );
    }

    /**
     * Returns the latent signatures of the true model.
     *
     * @param trueModel the true model
     * @return the latent signatures
     */
    private List<LatentSignature> getTrueLatentSignatures(MimicModel trueModel) {
        List<LatentSignature> signatures = new ArrayList<>();

        for (Node latent : trueModel.getLatents()) {
            Set<String> inputs = nodeNames(trueModel.getInputParents(latent));
            Set<String> outputs = nodeNames(trueModel.getOutputChildren(latent));
            signatures.add(new LatentSignature(latent.getName(), inputs, outputs));
        }

        return signatures;
    }

    /**
     * Returns the latent signatures of the estimated graph.
     *
     * @param graph the estimated graph
     * @return the latent signatures
     */
    private List<LatentSignature> getEstimatedLatentSignatures(Graph graph) {
        List<LatentSignature> signatures = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<String> inputs = new LinkedHashSet<>();
            Set<String> outputs = new LinkedHashSet<>();

            for (Node parent : graph.getParents(node)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    inputs.add(parent.getName());
                }
            }

            for (Node child : graph.getChildren(node)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    outputs.add(child.getName());
                }
            }

            signatures.add(new LatentSignature(node.getName(), inputs, outputs));
        }

        return signatures;
    }

    /**
     * Performs a greedy maximum-similarity matching between true and estimated latent
     * signatures.
     *
     * @param truths the true latent signatures
     * @param estimates the estimated latent signatures
     * @return the list of matches
     */
    /**
     * Finds the optimal maximum-similarity matching between true and estimated
     * latent signatures using the Hungarian algorithm. Unlike greedy matching,
     * this is guaranteed to maximise total similarity across all matched pairs.
     *
     * <p>When the counts differ, at most min(nTrue, nEst) pairs are returned.
     * Unmatched true latents (when nTrue > nEst) simply produce no entry in the
     * result — they are counted in the latent count error metric elsewhere.</p>
     *
     * @param truths    the true latent signatures
     * @param estimates the estimated latent signatures
     * @return the optimal list of matches
     */
    private List<Match> optimalMatch(List<LatentSignature> truths,
                                     List<LatentSignature> estimates) {
        int nTrue = truths.size();
        int nEst  = estimates.size();

        if (nTrue == 0 || nEst == 0) {
            return new ArrayList<>();
        }

        int n = Math.max(nTrue, nEst);

        // Cost matrix for minimisation: cost = 1 - similarity, so minimising cost
        // maximises similarity. Dummy rows/columns (padding for rectangular case)
        // use UNMATCHED_COST > 1 so the algorithm always prefers a real pairing
        // over a dummy one, and only fills dummies when the matrix forces it.
        double unmatched = 2.0;
        double[][] cost  = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i < nTrue && j < nEst) {
                    double inputSim  = jaccard(truths.get(i).inputs,
                            estimates.get(j).inputs);
                    double outputSim = jaccard(truths.get(i).outputs,
                            estimates.get(j).outputs);
                    cost[i][j] = 1.0 - 0.5 * (inputSim + outputSim);
                } else {
                    cost[i][j] = unmatched;
                }
            }
        }

        int[] assignment = hungarianAssign(cost, n);

        List<Match> matches = new ArrayList<>();

        for (int i = 0; i < nTrue; i++) {
            int j = assignment[i];

            if (j >= nEst) {
                // This true latent was assigned to a dummy column: unmatched.
                continue;
            }

            LatentSignature truth    = truths.get(i);
            LatentSignature estimate = estimates.get(j);

            double inputSim  = jaccard(truth.inputs,  estimate.inputs);
            double outputSim = jaccard(truth.outputs, estimate.outputs);
            double totalSim  = 0.5 * (inputSim + outputSim);

            matches.add(new Match(truth, estimate, inputSim, outputSim, totalSim));
        }

        return matches;
    }

    /**
     * Solves the linear assignment problem for a square cost matrix using the
     * O(n³) Hungarian algorithm (shortest augmenting path variant).
     *
     * @param cost n×n cost matrix; not mutated
     * @param n    dimension
     * @return assignment array where {@code result[i] = j} means row i is
     *         assigned to column j
     */
    private static int[] hungarianAssign(double[][] cost, int n) {
        // All arrays are 1-indexed; index 0 is a sentinel.
        double[] u    = new double[n + 1]; // row potentials
        double[] v    = new double[n + 1]; // column potentials
        int[]    p    = new int[n + 1];    // p[j] = row assigned to column j
        int[]    way  = new int[n + 1];    // predecessor column in augmenting path

        for (int i = 1; i <= n; i++) {
            // Insert row i by finding the shortest augmenting path from it.
            p[0] = i;
            int     j0      = 0;
            double[] minDist = new double[n + 1];
            boolean[] used  = new boolean[n + 1];
            Arrays.fill(minDist, Double.MAX_VALUE);

            do {
                used[j0] = true;
                int    i0    = p[j0];
                double delta = Double.MAX_VALUE;
                int    j1    = -1;

                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double reduced = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (reduced < minDist[j]) {
                            minDist[j] = reduced;
                            way[j]     = j0;
                        }
                        if (minDist[j] < delta) {
                            delta = minDist[j];
                            j1    = j;
                        }
                    }
                }

                // Update potentials along the path found so far.
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j]    -= delta;
                    } else {
                        minDist[j] -= delta;
                    }
                }

                j0 = j1;
            } while (p[j0] != 0);

            // Augment: flip assignments along the path.
            do {
                int j1 = way[j0];
                p[j0]  = p[j1];
                j0     = j1;
            } while (j0 != 0);
        }

        // Convert from column-indexed p[] to row-indexed result[].
        int[] result = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] != 0) {
                result[p[j] - 1] = j - 1;
            }
        }
        return result;
    }

    /**
     * Returns the Jaccard similarity of two sets.
     *
     * @param a the first set
     * @param b the second set
     * @return the Jaccard similarity
     */
    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }

        Set<String> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);

        if (union.isEmpty()) {
            return 1.0;
        }

        return ((double) intersection.size()) / union.size();
    }

    /**
     * Converts a node set to a set of node names.
     *
     * @param nodes the nodes
     * @return the node names
     */
    private Set<String> nodeNames(Set<Node> nodes) {
        Set<String> names = new LinkedHashSet<>();

        for (Node node : nodes) {
            names.add(node.getName());
        }

        return names;
    }

    /**
     * Latent signature for evaluation.
     */
    private static final class LatentSignature {
        /**
         * Stored for debugging; not used in matching or scoring. Matching is
         * performed purely on set similarity so that algorithm-assigned latent
         * names do not influence evaluation.
         */
        @SuppressWarnings("unused")
        private final String name;
        private final Set<String> inputs;
        private final Set<String> outputs;

        private LatentSignature(String name, Set<String> inputs, Set<String> outputs) {
            this.name    = name;
            this.inputs  = new LinkedHashSet<>(inputs);
            this.outputs = new LinkedHashSet<>(outputs);
        }
    }

    /**
     * Match between a true latent and an estimated latent.
     */
    private static final class Match {
        private final LatentSignature truth;
        private final LatentSignature estimate;
        private final double inputSimilarity;
        private final double outputSimilarity;
        private final double totalSimilarity;

        private Match(LatentSignature truth,
                      LatentSignature estimate,
                      double inputSimilarity,
                      double outputSimilarity,
                      double totalSimilarity) {
            this.truth = truth;
            this.estimate = estimate;
            this.inputSimilarity = inputSimilarity;
            this.outputSimilarity = outputSimilarity;
            this.totalSimilarity = totalSimilarity;
        }
    }
}