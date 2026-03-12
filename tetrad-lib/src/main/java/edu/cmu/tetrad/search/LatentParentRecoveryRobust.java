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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * Recovers measured parents of already recovered latent variables and cleans
 * the latent-to-latent structure using robust structural rules.
 *
 * <p>This class assumes that the input graph already contains latent variables,
 * their measured indicators, latent-to-latent edges or adjacencies, and all
 * remaining measured variables as isolated nodes. Its job is to attach measured
 * parents to the latent variables, prune inherited measured-parent edges, orient
 * latent-to-latent edges when possible, and remove transitive latent shortcuts.</p>
 *
 * <p>The procedure is:</p>
 *
 * <ol>
 *   <li>For each latent, construct a proxy score from its measured children using
 *   the mean of the standardized indicator columns.</li>
 *   <li>For each latent, regress that latent proxy on the proxies of its directed
 *   latent parents and keep the residual.</li>
 *   <li>Among measured variables not used as indicators of any latent, select
 *   measured parents using a forward stepwise residual-correlation procedure.</li>
 *   <li>Add the selected variables as measured parents of the latent.</li>
 *   <li>Remove inherited measured-parent edges along latent ancestry.</li>
 *   <li>Orient latent-to-latent edges using measured-parent set inclusion.</li>
 *   <li>Remove transitive latent-to-latent edges.</li>
 * </ol>
 *
 * <p>An optional second pass can be used to refine the result after the latent
 * edges have been oriented and cleaned.</p>
 */
public final class LatentParentRecoveryRobust {

    /**
     * The dataset used to estimate latent proxies and measured-parent relations.
     */
    private final DataSet data;
    /**
     * The input graph containing latent variables, indicators, and possibly
     * latent-to-latent edges.
     */
    private final Graph latentGraph;
    /**
     * Background knowledge, if any.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Minimum absolute correlation required to add a measured parent in the
     * forward stepwise selection procedure.
     */
    private double enterThreshold = 0.20;
    /**
     * Whether inherited measured-parent edges should be pruned.
     */
    private boolean pruneInheritedParents = true;
    /**
     * Whether latent-to-latent edges should be oriented from measured-parent
     * inclusion relations.
     */
    private boolean orientLatentEdges = true;
    /**
     * Whether transitive latent-to-latent shortcuts should be removed.
     */
    private boolean removeTransitiveLatentEdges = false;
    /**
     * Whether to run a second pass after latent-edge cleanup.
     */
    private boolean secondPass = false;
    /**
     * Whether measured-parent selection should use a greedy local SemBIC search
     * rather than forward residual-correlation screening.
     */
    private boolean useSemBicParentSelection = true;
    /**
     * Penalty discount for SemBIC parent selection.
     */
    private double semBicPenaltyDiscount = 2.0;

    /**
     * Constructs a robust latent-parent recovery procedure.
     *
     * @param data the dataset
     * @param latentGraph the latent graph
     */
    public LatentParentRecoveryRobust(DataSet data, Graph latentGraph) {
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
     * Sets the enter threshold used in measured-parent selection.
     *
     * @param enterThreshold the threshold
     */
    public void setEnterThreshold(double enterThreshold) {
        if (enterThreshold < 0.0 || enterThreshold > 1.0) {
            throw new IllegalArgumentException("Enter threshold must be between 0 and 1.");
        }

        this.enterThreshold = enterThreshold;
    }

    /**
     * Sets whether inherited measured parents should be pruned.
     *
     * @param pruneInheritedParents true if inherited parents should be pruned
     */
    public void setPruneInheritedParents(boolean pruneInheritedParents) {
        this.pruneInheritedParents = pruneInheritedParents;
    }

    /**
     * Sets whether latent-to-latent edges should be oriented.
     *
     * @param orientLatentEdges true if latent-to-latent edges should be oriented
     */
    public void setOrientLatentEdges(boolean orientLatentEdges) {
        this.orientLatentEdges = orientLatentEdges;
    }

    /**
     * Sets whether transitive latent-to-latent shortcuts should be removed.
     *
     * @param removeTransitiveLatentEdges true if transitive latent edges should be removed
     */
    public void setRemoveTransitiveLatentEdges(boolean removeTransitiveLatentEdges) {
        this.removeTransitiveLatentEdges = removeTransitiveLatentEdges;
    }

    /**
     * Sets whether a second refinement pass should be run.
     *
     * @param secondPass true if a second pass should be run
     */
    public void setSecondPass(boolean secondPass) {
        this.secondPass = secondPass;
    }

    public void setUseSemBicParentSelection(boolean useSemBicParentSelection) {
        this.useSemBicParentSelection = useSemBicParentSelection;
    }

    public void setSemBicPenaltyDiscount(double semBicPenaltyDiscount) {
        this.semBicPenaltyDiscount = semBicPenaltyDiscount;
    }

    /**
     * Runs robust latent-parent recovery and returns the augmented graph.
     *
     * @return the augmented graph
     */
    public Graph search() {
        Graph result = new EdgeListGraph(this.latentGraph);

        runOnePass(result);

        if (this.secondPass) {
            removeMeasuredParentEdges(result);
            runOnePass(result);
        }

//        new MeekRules().orientImplied(result);

        orientLatentEdgesToPreserveNoncolliders(result);

        pruneInheritedMeasuredParentsStructurally(result);

        removeDegenerateLatents(result);

        return result;
    }

    private void orientLatentEdgesToPreserveNoncolliders(Graph graph) {
        boolean changed = true;

        while (changed) {
            changed = false;

            List<Node> latents = getLatentNodes(graph);

            for (Node b : latents) {
                List<Node> adj = new ArrayList<>();

                for (Node x : graph.getAdjacentNodes(b)) {
                    if (x.getNodeType() == NodeType.LATENT) {
                        adj.add(x);
                    }
                }

                for (int i = 0; i < adj.size(); i++) {
                    for (int j = 0; j < adj.size(); j++) {
                        if (i == j) {
                            continue;
                        }

                        Node a = adj.get(i);
                        Node c = adj.get(j);

                        if (graph.isAdjacentTo(a, c)) {
                            continue;
                        }

                        Edge ab = graph.getEdge(a, b);
                        Edge bc = graph.getEdge(b, c);

                        if (ab == null || bc == null) {
                            continue;
                        }

                        boolean aIntoB =
                                Edges.isDirectedEdge(ab) &&
                                        ab.getNode1().equals(a) &&
                                        ab.getNode2().equals(b);

                        boolean bUndirectedC = !Edges.isDirectedEdge(bc);

                        if (aIntoB && bUndirectedC) {
                            graph.removeEdge(bc);
                            graph.addDirectedEdge(b, c);
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes measured-parent edges that are inherited through an already discovered
     * latent-to-latent path.
     *
     * <p>If x is a measured parent of an immediate latent parent U of L, and x is also
     * a measured parent of L, then x -> L is treated as inherited and is removed, but
     * only if removing it leaves at least one other measured parent for L. This guard
     * makes the pruning less aggressive in cases where a measured variable may truly
     * feed both latents.</p>
     *
     * @param graph the graph to refine
     */
    private void pruneInheritedMeasuredParentsStructurally(Graph graph) {
        for (Node latent : new ArrayList<>(graph.getNodes())) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredParents = getMeasuredParents(latent, graph);

            if (measuredParents.size() <= 1) {
                continue;
            }

            Set<Node> inheritedMeasuredParents = new LinkedHashSet<>();

            for (Node ancestor : getLatentAncestors(latent, graph)) {
                inheritedMeasuredParents.addAll(getMeasuredParents(ancestor, graph));
            }

            for (Node measuredParent : new ArrayList<>(measuredParents)) {
                if (!inheritedMeasuredParents.contains(measuredParent)) {
                    continue;
                }

                // Safety guard: only remove the inherited parent if some other measured
                // parent still remains for this latent.
                if (getMeasuredParents(latent, graph).size() > 1) {
                    graph.removeEdge(measuredParent, latent);
                }
            }
        }
    }

    private Set<Node> getLatentAncestors(Node latent, Graph graph) {
        Set<Node> ancestors = new LinkedHashSet<>();
        Deque<Node> stack = new ArrayDeque<>();

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT) {
                stack.push(parent);
            }
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();

            if (!ancestors.add(current)) {
                continue;
            }

            for (Node parent : graph.getParents(current)) {
                if (parent.getNodeType() == NodeType.LATENT) {
                    stack.push(parent);
                }
            }
        }

        return ancestors;
    }

    /**
     * Runs one full pass of measured-parent estimation and structural cleanup.
     *
     * @param graph the graph to modify
     */
    private void runOnePass(Graph graph) {
        Map<Node, double[]> latentScores = estimateLatentScores(graph);
        List<Node> latents = getLatentNodes(graph);
        List<Node> candidateMeasuredParents = getCandidateMeasuredParents(graph);

        Map<Node, Set<Node>> selectedParents = new LinkedHashMap<>();

        for (Node latent : latents) {
            double[] target = residualizeOnLatentParents(latent, graph, latentScores);
//            Set<Node> parents = selectMeasuredParents(latent, target, candidateMeasuredParents);

            Set<Node> parents;

            if (this.useSemBicParentSelection) {
                parents = selectMeasuredParentsSemBic(latent, target, candidateMeasuredParents);
            } else {
                parents = selectMeasuredParents(latent, target, candidateMeasuredParents);
            }

            selectedParents.put(latent, parents);
        }

        attachMeasuredParents(graph, selectedParents);

        if (this.pruneInheritedParents) {
            pruneInheritedMeasuredParents(graph);
        }

        if (this.orientLatentEdges) {
            orientLatentEdgesFromMeasuredParents(graph);
        }

        if (this.removeTransitiveLatentEdges) {
            removeTransitiveLatentEdges(graph);
        }
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
     * Selects measured parents of a latent residual using a forward stepwise
     * residual-correlation procedure.
     *
     * @param latent the latent whose parents are being selected
     * @param target the residualized latent score
     * @param candidateMeasuredParents the pool of candidate measured parents
     * @return the selected measured parents
     */
    private Set<Node> selectMeasuredParents(Node latent, double[] target, List<Node> candidateMeasuredParents) {
        Set<Node> selected = new LinkedHashSet<>();

        if (target.length == 0) {
            return selected;
        }

        double[] residual = target.clone();
        Set<Node> remaining = new LinkedHashSet<>();

        for (Node candidate : candidateMeasuredParents) {
            if (isAllowedParent(candidate, latent)) {
                remaining.add(candidate);
            }
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            Node bestNode = null;
            double[] bestPredictor = null;
            double bestAbsCorrelation = -1.0;

            for (Node candidate : remaining) {
                int column = this.data.getColumnIndex(candidate);

                if (column < 0) {
                    continue;
                }

                double[] predictor = this.data.getDoubleData().getColumn(column).toArray();
                predictor = standardize(predictor);

                double r = correlation(predictor, residual);

                if (Double.isNaN(r)) {
                    continue;
                }

                double absR = TMath.abs(r);

                if (absR > bestAbsCorrelation) {
                    bestAbsCorrelation = absR;
                    bestNode = candidate;
                    bestPredictor = predictor;
                }
            }

            if (bestNode != null && bestAbsCorrelation >= this.enterThreshold) {
                selected.add(bestNode);
                remaining.remove(bestNode);

                double beta = regressionCoefficient(bestPredictor, residual);

                for (int i = 0; i < residual.length; i++) {
                    residual[i] -= beta * bestPredictor[i];
                }

                changed = true;
            }
        }

        return selected;
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
        return !this.knowledge.isForbidden(candidate.getName(), latent.getName());
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
     * Removes all measured-parent-to-latent edges from the graph.
     *
     * @param graph the graph to modify
     */
    private void removeMeasuredParentEdges(Graph graph) {
        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (!Edges.isDirectedEdge(edge)) {
                continue;
            }

            if (a.getNodeType() != NodeType.LATENT && b.getNodeType() == NodeType.LATENT) {
                graph.removeEdge(edge);
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
     * Orients existing latent-to-latent adjacencies using measured-parent set inclusion.
     *
     * <p>This method preserves the latent-to-latent adjacency structure already present
     * in the graph. It never removes a latent-to-latent adjacency. If the measured-parent
     * set of one latent is a strict subset of the measured-parent set of the other, the
     * adjacency is oriented from the smaller parent set into the larger parent set.
     * If no inclusion relation holds, the existing adjacency is left unchanged.</p>
     *
     * @param graph the graph to refine
     */
    private void orientLatentEdgesFromMeasuredParents(Graph graph) {
        List<Node> latents = getLatentNodes(graph);

        for (int i = 0; i < latents.size(); i++) {
            for (int j = i + 1; j < latents.size(); j++) {
                Node a = latents.get(i);
                Node b = latents.get(j);

                if (!graph.isAdjacentTo(a, b)) {
                    continue;
                }

                Set<Node> parentsA = getMeasuredParents(a, graph);
                Set<Node> parentsB = getMeasuredParents(b, graph);

                boolean aIntoB = parentsB.containsAll(parentsA) && !parentsA.equals(parentsB);
                boolean bIntoA = parentsA.containsAll(parentsB) && !parentsA.equals(parentsB);

                if (aIntoB && !bIntoA) {
                    orientLatentEdge(graph, a, b);
                } else if (bIntoA && !aIntoB) {
                    orientLatentEdge(graph, b, a);
                }
            }
        }
    }

    /**
     * Orients latent-to-latent edges using measured-parent set inclusion.
     *
     * <p>If the measured-parent set of A is a strict subset of the measured-parent set
     * of B, then A is oriented into B. Existing latent-to-latent edges are removed and
     * rebuilt from these inclusion relations.</p>
     *
     * @param graph the graph to refine
     */

    /**
     * Orients the existing latent-to-latent adjacency from fromNode to toNode.
     *
     * <p>If the edge is already correctly oriented, nothing is changed. Otherwise the
     * existing adjacency is removed and replaced by a directed edge in the desired
     * direction.</p>
     *
     * @param graph the graph to modify
     * @param fromNode the tail of the desired directed edge
     * @param toNode the head of the desired directed edge
     */
    private void orientLatentEdge(Graph graph, Node fromNode, Node toNode) {
        Edge edge = graph.getEdge(fromNode, toNode);

        if (edge == null) {
            return;
        }

        if (Edges.isDirectedEdge(edge) && edge.getNode1().equals(fromNode) && edge.getNode2().equals(toNode)) {
            return;
        }

        graph.removeEdge(edge);
        graph.addDirectedEdge(fromNode, toNode);
    }

    /**
     * Removes all latent-to-latent edges from the graph.
     *
     * @param graph the graph to modify
     */
    private void removeLatentToLatentEdges(Graph graph) {
        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (a.getNodeType() == NodeType.LATENT && b.getNodeType() == NodeType.LATENT) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Removes transitive latent-to-latent edges.
     *
     * <p>If A -> C is implied by a directed path A -> ... -> C through other latent
     * nodes, the direct edge A -> C is removed.</p>
     *
     * @param graph the graph to refine
     */
    private void removeTransitiveLatentEdges(Graph graph) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());

        for (Edge edge : edges) {
            Node from = edge.getNode1();
            Node to = edge.getNode2();

            if (from.getNodeType() != NodeType.LATENT || to.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (!Edges.isDirectedEdge(edge)) {
                continue;
            }

            graph.removeEdge(edge);

            boolean reachable = graph.paths().existsDirectedPath(from, to);

            if (!reachable) {
                graph.addEdge(edge);
            }
        }
    }

    /**
     * Removes latent variables that no longer have measured children.
     *
     * @param graph the graph to modify
     */
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

    /**
     * Selects measured parents of a latent residual using a greedy local SemBIC search.
     *
     * <p>A temporary dataset is constructed containing the candidate measured parents
     * and one additional continuous target variable representing the residualized latent
     * proxy. Starting from the empty parent set, candidate parents are added greedily
     * whenever they improve the SemBIC local score of the target variable.</p>
     *
     * @param latent the latent whose parents are being selected
     * @param target the residualized latent proxy
     * @param candidateMeasuredParents the pool of candidate measured parents
     * @return the selected measured parents
     */
    private Set<Node> selectMeasuredParentsSemBic(Node latent,
                                                  double[] target,
                                                  List<Node> candidateMeasuredParents) {
        Set<Node> allowedCandidates = new LinkedHashSet<>();

        for (Node candidate : candidateMeasuredParents) {
            if (isAllowedParent(candidate, latent)) {
                allowedCandidates.add(candidate);
            }
        }

        if (target.length == 0 || allowedCandidates.isEmpty()) {
            return new LinkedHashSet<>();
        }

        DataSet augmented = buildAugmentedParentSelectionData(target, new ArrayList<>(allowedCandidates));
        SemBicScore score = new SemBicScore(new CovarianceMatrix(augmented));
        score.setPenaltyDiscount(this.semBicPenaltyDiscount);

        List<Node> vars = augmented.getVariables();
        Node targetNode = augmented.getVariable("__TARGET__");

        if (targetNode == null) {
            throw new IllegalStateException("Target variable __TARGET__ was not found in the augmented dataset.");
        }

        int targetIndex = vars.indexOf(targetNode);

        Map<Integer, Node> indexToOriginalNode = new LinkedHashMap<>();
        List<Integer> candidateIndices = new ArrayList<>();

        for (Node candidate : allowedCandidates) {
            Node augmentedNode = augmented.getVariable(candidate.getName());

            if (augmentedNode == null) {
                continue;
            }

            int index = vars.indexOf(augmentedNode);

            if (index >= 0) {
                candidateIndices.add(index);
                indexToOriginalNode.put(index, candidate);
            }
        }

        Set<Integer> selected = new LinkedHashSet<>();
        boolean changed = true;

        while (changed) {
            changed = false;

            double bestScore = localSemBicScore(score, targetIndex, selected);
            Integer bestAdd = null;
            double bestAddScore = bestScore;

            for (Integer candidateIndex : candidateIndices) {
                if (selected.contains(candidateIndex)) {
                    continue;
                }

                Set<Integer> proposed = new LinkedHashSet<>(selected);
                proposed.add(candidateIndex);

                double proposedScore = localSemBicScore(score, targetIndex, proposed);

                if (proposedScore > bestAddScore) {
                    bestAddScore = proposedScore;
                    bestAdd = candidateIndex;
                }
            }

            if (bestAdd != null) {
                selected.add(bestAdd);
                changed = true;
            }
        }

        Set<Node> parents = new LinkedHashSet<>();

        for (Integer index : selected) {
            Node original = indexToOriginalNode.get(index);

            if (original != null) {
                parents.add(original);
            }
        }

        return parents;
    }

    /**
     * Returns the SemBIC local score for the given target and parent set.
     *
     * @param score the SemBIC score object
     * @param targetIndex the index of the target variable
     * @param parents the indices of the parent variables
     * @return the local score
     */
    private double localSemBicScore(SemBicScore score, int targetIndex, Set<Integer> parents) {
        int[] parentArray = new int[parents.size()];
        int i = 0;

        for (Integer parent : parents) {
            parentArray[i++] = parent;
        }

        return score.localScore(targetIndex, parentArray);
    }

    /**
     * Builds a temporary dataset for SemBIC parent selection.
     *
     * <p>The dataset contains one column for each candidate measured parent and one
     * final continuous column named "__TARGET__" containing the supplied target vector.</p>
     *
     * @param target the residualized latent proxy
     * @param candidateMeasuredParents the candidate measured parents
     * @return the temporary augmented dataset
     */
    private DataSet buildAugmentedParentSelectionData(double[] target,
                                                      List<Node> candidateMeasuredParents) {
        int n = target.length;
        int p = candidateMeasuredParents.size() + 1;

        List<Node> variables = new ArrayList<>();

        for (Node candidate : candidateMeasuredParents) {
            variables.add(new ContinuousVariable(candidate.getName()));
        }

        variables.add(new ContinuousVariable("__TARGET__"));

        DoubleDataBox box = new DoubleDataBox(n, p);
        DataSet augmented = new BoxDataSet(box, variables);

        for (int j = 0; j < candidateMeasuredParents.size(); j++) {
            Node candidate = candidateMeasuredParents.get(j);
            int column = this.data.getColumnIndex(candidate);

            if (column < 0) {
                throw new IllegalArgumentException("Candidate variable not found in data: " + candidate.getName());
            }

            double[] values = this.data.getDoubleData().getColumn(column).toArray();

            for (int i = 0; i < n; i++) {
                augmented.setDouble(i, j, values[i]);
            }
        }

        int targetColumn = p - 1;

        for (int i = 0; i < n; i++) {
            augmented.setDouble(i, targetColumn, target[i]);
        }

        return augmented;
    }

    public enum ParentSelectionMethod {
        STEPWISE_CORRELATION,
        SEM_BIC
    }
}