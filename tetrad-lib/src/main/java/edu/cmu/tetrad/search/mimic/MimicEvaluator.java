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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * Evaluates an estimated graph against the true model.
     *
     * @param trueModel the true MIMIC model
     * @param estimatedGraph the estimated graph
     * @return the evaluation summary
     */
    public MimicEvaluation evaluate(MimicModel trueModel, Graph estimatedGraph) {
        List<LatentSignature> trueLatents = getTrueLatentSignatures(trueModel);
        List<LatentSignature> estimatedLatents = getEstimatedLatentSignatures(estimatedGraph);

        List<Match> matches = greedyMatch(trueLatents, estimatedLatents);

        double sumLatent = 0.0;
        double sumInput = 0.0;
        double sumOutput = 0.0;

        for (Match match : matches) {
            sumLatent += match.totalSimilarity;
            sumInput += match.inputSimilarity;
            sumOutput += match.outputSimilarity;
        }

        int matched = matches.size();

        double avgLatent = matched == 0 ? 0.0 : sumLatent / matched;
        double avgInput = matched == 0 ? 0.0 : sumInput / matched;
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
    private List<Match> greedyMatch(List<LatentSignature> truths, List<LatentSignature> estimates) {
        List<LatentSignature> remainingTruths = new ArrayList<>(truths);
        List<LatentSignature> remainingEstimates = new ArrayList<>(estimates);
        List<Match> matches = new ArrayList<>();

        while (!remainingTruths.isEmpty() && !remainingEstimates.isEmpty()) {
            Match best = null;

            for (LatentSignature truth : remainingTruths) {
                for (LatentSignature estimate : remainingEstimates) {
                    double inputSimilarity = jaccard(truth.inputs, estimate.inputs);
                    double outputSimilarity = jaccard(truth.outputs, estimate.outputs);
                    double totalSimilarity = 0.5 * (inputSimilarity + outputSimilarity);

                    Match match = new Match(truth, estimate, inputSimilarity, outputSimilarity, totalSimilarity);

                    if (best == null || match.totalSimilarity > best.totalSimilarity) {
                        best = match;
                    }
                }
            }

            if (best == null) {
                break;
            }

            matches.add(best);
            remainingTruths.remove(best.truth);
            remainingEstimates.remove(best.estimate);
        }

        return matches;
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
        private final String name;
        private final Set<String> inputs;
        private final Set<String> outputs;

        private LatentSignature(String name, Set<String> inputs, Set<String> outputs) {
            this.name = name;
            this.inputs = new LinkedHashSet<>(inputs);
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