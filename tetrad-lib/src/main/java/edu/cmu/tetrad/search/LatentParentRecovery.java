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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TMath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recovers measured parents of already recovered latent variables.
 *
 * <p>This class assumes that the input graph already contains latent variables,
 * their measured indicators, and possibly some latent-to-latent edges. Its job
 * is to attach measured parents to the latent variables using the supplied data.</p>
 *
 * <p>The procedure is:</p>
 *
 * <ol>
 *   <li>For each latent, construct a proxy score from its measured children using
 *   the mean of the standardized indicator columns.</li>
 *   <li>For each latent, regress that latent proxy on the proxies of its directed
 *   latent parents and keep the residual.</li>
 *   <li>Among measured variables not used as indicators of any latent, select those
 *   that are sufficiently correlated with the residualized latent proxy.</li>
 *   <li>Add the selected variables as measured parents of the latent.</li>
 *   <li>Optionally remove inherited measured-parent edges along latent ancestry.</li>
 * </ol>
 *
 * <p>This is intended as a first-pass post-processing method for turning a recovered
 * latent-indicator structure into a fuller MIMIC-style graph.</p>
 */
public final class LatentParentRecovery {

    /**
     * The dataset used to estimate latent proxies and measured-parent relations.
     */
    private final DataSet data;

    /**
     * The input graph containing latent variables, latent indicators, and optionally
     * latent-to-latent edges.
     */
    private final Graph latentGraph;

    /**
     * Background knowledge, if any.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Absolute correlation threshold for selecting a measured parent of a latent residual.
     */
    private double correlationThreshold = 0.10;

    /**
     * Whether to prune inherited measured-parent edges structurally after parent attachment.
     */
    private boolean pruneInheritedParents = true;

    /**
     * Constructs a latent-parent recovery procedure.
     *
     * @param data the dataset
     * @param latentGraph the latent graph
     */
    public LatentParentRecovery(DataSet data, Graph latentGraph) {
        if (data == null) {
            throw new NullPointerException("Data must not be null.");
        }

        if (latentGraph == null) {
            throw new NullPointerException("Latent graph must not be null.");
        }

        this.data = data;
        this.latentGraph = latentGraph;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the background knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the absolute correlation threshold used to select measured parents.
     *
     * @param correlationThreshold the threshold
     */
    public void setCorrelationThreshold(double correlationThreshold) {
        if (correlationThreshold < 0.0 || correlationThreshold > 1.0) {
            throw new IllegalArgumentException("Correlation threshold must be between 0 and 1.");
        }

        this.correlationThreshold = correlationThreshold;
    }

    /**
     * Sets whether inherited measured parents should be pruned structurally after selection.
     *
     * @param pruneInheritedParents true if inherited parents should be pruned
     */
    public void setPruneInheritedParents(boolean pruneInheritedParents) {
        this.pruneInheritedParents = pruneInheritedParents;
    }

    /**
     * Runs latent-parent recovery and returns the augmented graph.
     *
     * @return the augmented graph
     */
    public Graph search() {
        Graph result = new EdgeListGraph(this.latentGraph);

        Map<Node, double[]> latentScores = estimateLatentScores(result);
        List<Node> latents = getLatentNodes(result);
        List<Node> candidateMeasuredParents = getCandidateMeasuredParents(result);

        Map<Node, Set<Node>> selectedParents = new LinkedHashMap<>();

        for (Node latent : latents) {
            double[] target = residualizeOnLatentParents(latent, result, latentScores);
            Set<Node> parents = selectMeasuredParents(latent, target, candidateMeasuredParents);
            selectedParents.put(latent, parents);
        }

//        attachMeasuredParents(result, selectedParents);
//
//        if (this.pruneInheritedParents) {
//            pruneInheritedMeasuredParents(result);
//        }
//
//        removeDegenerateLatents(result);
//
//        return result;

        attachMeasuredParents(result, selectedParents);

        if (this.pruneInheritedParents) {
            pruneInheritedMeasuredParents(result);
        }

        return result;
    }

    /**
     * Estimates a proxy score for each latent using the mean of the standardized
     * indicator columns.
     *
     * @param graph the graph containing the latents
     * @return a map from each latent to its proxy score vector
     */
    private Map<Node, double[]> estimateLatentScores(Graph graph) {
        Map<Node, double[]> latentScores = new LinkedHashMap<>();

        for (Node latent : getLatentNodes(graph)) {
            Set<Node> indicators = getMeasuredChildren(latent, graph);

            if (indicators.isEmpty()) {
                continue;
            }

            List<double[]> indicatorColumns = new ArrayList<>();

            for (Node indicator : indicators) {
                int column = this.data.getColumnIndex(indicator);

                if (column < 0) {
                    continue;
                }

                double[] values = this.data.getDoubleData().getColumn(column).toArray();
                indicatorColumns.add(standardize(values));
            }

            if (indicatorColumns.isEmpty()) {
                continue;
            }

            int n = indicatorColumns.get(0).length;
            double[] score = new double[n];

            for (int i = 0; i < n; i++) {
                double sum = 0.0;

                for (double[] column : indicatorColumns) {
                    sum += column[i];
                }

                score[i] = sum / indicatorColumns.size();
            }

            latentScores.put(latent, score);
        }

        return latentScores;
    }

    /**
     * Residualizes the proxy score of a latent on the proxy scores of its directed
     * latent parents.
     *
     * @param latent the latent
     * @param graph the graph
     * @param latentScores the latent proxy scores
     * @return the residualized latent score
     */
    private double[] residualizeOnLatentParents(Node latent, Graph graph, Map<Node, double[]> latentScores) {
        double[] target = latentScores.get(latent);

        if (target == null) {
            return new double[0];
        }

        List<Node> latentParents = new ArrayList<>();

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT && latentScores.containsKey(parent)) {
                latentParents.add(parent);
            }
        }

        if (latentParents.isEmpty()) {
            return target.clone();
        }

        double[] residual = target.clone();

        for (Node parent : latentParents) {
            double[] parentScore = latentScores.get(parent);
            double beta = regressionCoefficient(parentScore, residual);

            for (int i = 0; i < residual.length; i++) {
                residual[i] -= beta * parentScore[i];
            }
        }

        return residual;
    }

    /**
     * Selects measured parents of a latent residual by marginal correlation screening.
     *
     * @param latent the latent whose parents are being selected
     * @param target the residualized latent score
     * @param candidateMeasuredParents the pool of candidate measured parents
     * @return the selected measured parents
     */
    private Set<Node> selectMeasuredParents(Node latent, double[] target, List<Node> candidateMeasuredParents) {
        Set<Node> parents = new LinkedHashSet<>();

        if (target.length == 0) {
            return parents;
        }

        for (Node candidate : candidateMeasuredParents) {
            if (!isAllowedParent(candidate, latent)) {
                continue;
            }

            int column = this.data.getColumnIndex(candidate);

            if (column < 0) {
                continue;
            }

            double[] predictor = this.data.getDoubleData().getColumn(column).toArray();
            predictor = standardize(predictor);

            double r = correlation(predictor, target);

            if (Double.isNaN(r)) {
                continue;
            }

            if (TMath.abs(r) >= this.correlationThreshold) {
                parents.add(candidate);
            }
        }

        return parents;
    }

    /**
     * Returns true if the candidate measured variable is allowed to be a parent of the
     * given latent according to the supplied knowledge.
     *
     * @param candidate the measured candidate parent
     * @param latent the latent
     * @return true if the edge is allowed
     */
    private boolean isAllowedParent(Node candidate, Node latent) {
        String from = candidate.getName();
        String to = latent.getName();

        return !this.knowledge.isForbidden(from, to);
    }

    /**
     * Attaches the selected measured parents to the corresponding latents.
     *
     * @param graph the graph to modify
     * @param selectedParents the selected measured parents for each latent
     */
    private void attachMeasuredParents(Graph graph, Map<Node, Set<Node>> selectedParents) {
        for (Map.Entry<Node, Set<Node>> entry : selectedParents.entrySet()) {
            Node latent = entry.getKey();

            for (Node parent : entry.getValue()) {
                if (!graph.isAdjacentTo(parent, latent)) {
                    graph.addDirectedEdge(parent, latent);
                }
            }
        }
    }

    /**
     * Returns all latent nodes in the graph.
     *
     * @param graph the graph
     * @return the latent nodes
     */
    private List<Node> getLatentNodes(Graph graph) {
        List<Node> latents = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        return latents;
    }

    /**
     * Returns the measured variables that are not indicators of any latent and hence
     * are eligible to become measured parents of latents.
     *
     * @param graph the graph
     * @return the candidate measured parents
     */
    private List<Node> getCandidateMeasuredParents(Graph graph) {
        Set<Node> indicators = new LinkedHashSet<>();

        for (Node latent : getLatentNodes(graph)) {
            indicators.addAll(getMeasuredChildren(latent, graph));
        }

        List<Node> candidates = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                continue;
            }

            if (!indicators.contains(node)) {
                candidates.add(node);
            }
        }

        return candidates;
    }

    /**
     * Returns the measured parents of the given node in the supplied graph.
     *
     * @param node the node
     * @param graph the graph
     * @return the measured parents
     */
    private Set<Node> getMeasuredParents(Node node, Graph graph) {
        Set<Node> measuredParents = new LinkedHashSet<>();

        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                measuredParents.add(parent);
            }
        }

        return measuredParents;
    }

    /**
     * Returns the measured children of the given node in the supplied graph.
     *
     * @param node the node
     * @param graph the graph
     * @return the measured children
     */
    private Set<Node> getMeasuredChildren(Node node, Graph graph) {
        Set<Node> measuredChildren = new LinkedHashSet<>();

        for (Node child : graph.getChildren(node)) {
            if (child.getNodeType() != NodeType.LATENT) {
                measuredChildren.add(child);
            }
        }

        return measuredChildren;
    }

    /**
     * Removes inherited measured-parent edges along latent ancestry.
     *
     * <p>If x is a measured parent of an immediate latent parent U of L, and x is also
     * a measured parent of L, then x -> L is treated as inherited and is removed, but
     * only if removing it leaves at least one other measured parent for L.</p>
     *
     * @param graph the graph to refine
     */
    private void pruneInheritedMeasuredParents(Graph graph) {
        for (Node latent : new ArrayList<>(graph.getNodes())) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredParents = getMeasuredParents(latent, graph);

            if (measuredParents.size() <= 1) {
                continue;
            }

            Set<Node> inheritedMeasuredParents = new LinkedHashSet<>();

            for (Node parent : graph.getParents(latent)) {
                if (parent.getNodeType() == NodeType.LATENT) {
                    inheritedMeasuredParents.addAll(getMeasuredParents(parent, graph));
                }
            }

            for (Node measuredParent : new ArrayList<>(measuredParents)) {
                if (!inheritedMeasuredParents.contains(measuredParent)) {
                    continue;
                }

                if (getMeasuredParents(latent, graph).size() > 1) {
                    graph.removeEdge(measuredParent, latent);
                }
            }
        }
    }

    /**
     * Removes latent variables that no longer have both measured parents and measured children.
     *
     * @param graph the graph to modify
     */
//    private void removeDegenerateLatents(Graph graph) {
//        for (Node node : new ArrayList<>(graph.getNodes())) {
//            if (node.getNodeType() != NodeType.LATENT) {
//                continue;
//            }
//
//            Set<Node> measuredParents = getMeasuredParents(node, graph);
//            Set<Node> measuredChildren = getMeasuredChildren(node, graph);
//
//            if (measuredParents.isEmpty() || measuredChildren.isEmpty()) {
//                graph.removeNode(node);
//            }
//        }
//    }

    private void removeDegenerateLatents(Graph graph) {
        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredChildren = getMeasuredChildren(node, graph);

            if (measuredChildren.isEmpty()) {
                graph.removeNode(node);
            }
        }
    }

    /**
     * Returns a standardized copy of the supplied array.
     *
     * @param values the values
     * @return the standardized values
     */
    private double[] standardize(double[] values) {
        double mean = StatUtils.mean(values);
        double sd = StatUtils.sd(values);

        double[] z = new double[values.length];

        if (sd == 0.0) {
            return z;
        }

        for (int i = 0; i < values.length; i++) {
            z[i] = (values[i] - mean) / sd;
        }

        return z;
    }

    /**
     * Returns the Pearson correlation of the two arrays.
     *
     * @param x the first array
     * @param y the second array
     * @return the correlation
     */
    private double correlation(double[] x, double[] y) {
        if (x.length != y.length || x.length == 0) {
            return Double.NaN;
        }

        return StatUtils.correlation(x, y);
    }

    /**
     * Returns the simple least-squares regression coefficient of y on x.
     *
     * @param x the predictor
     * @param y the response
     * @return the regression coefficient
     */
    private double regressionCoefficient(double[] x, double[] y) {
        if (x.length != y.length || x.length == 0) {
            return 0.0;
        }

        double sxx = 0.0;
        double sxy = 0.0;

        for (int i = 0; i < x.length; i++) {
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
        }

        if (sxx == 0.0) {
            return 0.0;
        }

        return sxy / sxx;
    }
}