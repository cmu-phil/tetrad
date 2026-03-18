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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.*;

/**
 * Implements a Murray-Watters/Glymour style Detect-Mimic search ("DM-MG").
 *
 * <p>This class is intended to follow the "Simple Search" procedure in
 * Murray-Watters and Glymour's paper on discovering hidden intermediate
 * structure in MIMIC models as closely as is practical in Tetrad code.</p>
 *
 * <p>The basic procedure is:</p>
 *
 * <ol>
 *   <li>Identify measured inputs and outputs, either from user-supplied sets or by running
 *   PC at depth 0 and classifying variables by indegree.</li>
 *   <li>Find unconditional dependence relations between measured inputs and outputs.</li>
 *   <li>For each measured input X, compute OUT(X), the set of measured outputs dependent on X.</li>
 *   <li>Partition measured inputs into equivalence classes having identical OUT-sets.</li>
 *   <li>Create one latent for each such input equivalence class, with directed edges from
 *   the inputs in the class into the latent.</li>
 *   <li>Order the OUT-sets by inclusion. Repeatedly select leaf/minimal OUT-sets, attach
 *   their currently unassigned outputs to the corresponding latent, remove those outputs
 *   from all larger OUT-sets, and continue until no outputs remain assignable.</li>
 *   <li>For every strict inclusion OUT(Xk) ⊂ OUT(Xj), add a directed latent-to-latent edge
 *   Uk -> Uj.</li>
 *   <li>Remove a latent-to-latent edge Uk -> Uj if there exist
 *   Or in OUT(Xj) \ OUT(Xk) and Os in OUT(Xk) that are conditionally independent given
 *   some subset of Xk ∪ Xj.</li>
 *   <li>Run a full-depth PC search on the measured variables. For each output that has no
 *   directed edge from any measured input in that pattern, remove latent-to-output edges
 *   into that output and add its output-to-output adjacencies using the PC pattern.</li>
 * </ol>
 *
 * <p>This is still a practical implementation, not a literal transcription of the paper.
 * In particular, step 8 is implemented by an exhaustive search over all subsets of the
 * union of the two relevant input classes.</p>
 *
 * @author josephramsey
 */
public class DmMgBoss implements IGraphSearch {

    /**
     * The sore.
     */
    private final Score score;
    /**
     * Inputs identified or supplied for the current run.
     */
    private final List<Node> inputs = new ArrayList<>();
    /**
     * Outputs identified or supplied for the current run.
     */
    private final List<Node> outputs = new ArrayList<>();
    /**
     * The conditional independence test used throughout the search.
     */
    private IndependenceTest test;
    /**
     * Optional background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Optional user-supplied measured inputs.
     * If non-null and nonempty, step 1 is skipped for these variables.
     */
    private List<Node> presetInputs;
    /**
     * Optional user-supplied measured outputs.
     * If non-null and nonempty, step 1 is skipped for these variables.
     */
    private List<Node> presetOutputs;
    /**
     * Counter used for latent names.
     */
    private int latentIndex = 1;

    /**
     * Jaccard threshold for considering two outputs to have effectively the same BOSS parent set.
     */
    private double bossParentJaccardThreshold = 0.75;

    /**
     * Constructs a new DM-MG search.
     *
     * @param test the independence test to use
     */
    public DmMgBoss(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }

        this.test = test;
        this.score = score;
    }

    /**
     * Runs the DM-MG search and returns the resulting graph.
     *
     * @return the discovered graph
     */
    @Override
    public Graph search() {
        resetState();

        identifyInputsAndOutputs();

        Graph graph = new EdgeListGraph();
        addMeasuredNodes(graph);

        Graph bossGraph = runBoss();
        Map<Set<Node>, InputClassInfo> inputClasses = buildInputClassesFromSimilarBossParents(bossGraph);

        for (InputClassInfo info : inputClasses.values()) {
            graph.addNode(info.latent());

            for (Node input : info.inputClass()) {
                if (!graph.containsNode(input)) {
                    graph.addNode(input);
                }

                if (!graph.isParentOf(input, info.latent())) {
                    graph.addDirectedEdge(input, info.latent());
                }
            }
        }

        assignOutputsDirectly(inputClasses, graph);
        addLatentEdgesByInputClassOverlap(inputClasses, graph);
        finalRefinement(graph);
        removeDegenerateLatents(graph);

        return graph;
    }

    /**
     * Assigns outputs directly to latents using the original output clusters.
     *
     * @param inputClasses the latent class information
     * @param graph the graph to update
     */
    private void assignOutputsDirectly(Map<Set<Node>, InputClassInfo> inputClasses, Graph graph) {
        for (InputClassInfo info : inputClasses.values()) {
            for (Node output : info.originalOut()) {
                if (!graph.containsNode(output)) {
                    graph.addNode(output);
                }

                if (!graph.isParentOf(info.latent(), output)) {
                    graph.addDirectedEdge(info.latent(), output);
                }
            }
        }
    }

    /**
     * Builds latent classes using a relaxed BOSS clustering rule:
     * outputs are clustered together when their measured-input parent sets
     * in the BOSS graph have sufficiently high Jaccard similarity.
     *
     * <p>For each measured output Y, let Pa(Y) be the set of measured inputs that are
     * parents of Y in the BOSS graph. We build an undirected graph on outputs, connecting
     * Y1 and Y2 whenever Jaccard(Pa(Y1), Pa(Y2)) is at least the configured threshold.
     * Connected components of that graph define output clusters. The input class for a
     * cluster is the union of the measured-input parent sets for outputs in that cluster.</p>
     *
     * @param bossGraph the BOSS graph on the measured variables
     * @return a map from input-class sets to their associated latent information
     */
    private Map<Set<Node>, InputClassInfo> buildInputClassesFromSimilarBossParents(Graph bossGraph) {
        Map<Node, Set<Node>> parentSetsByOutput = new LinkedHashMap<>();

        for (Node output : this.outputs) {
            Set<Node> parentSet = new LinkedHashSet<>();

            for (Node parent : bossGraph.getParents(output)) {
                if (this.inputs.contains(parent)) {
                    parentSet.add(parent);
                }
            }

            parentSetsByOutput.put(output, parentSet);
        }

        Graph outputGraph = new EdgeListGraph();

        for (Node output : this.outputs) {
            outputGraph.addNode(output);
        }

        for (int i = 0; i < this.outputs.size(); i++) {
            Node y1 = this.outputs.get(i);

            for (int j = i + 1; j < this.outputs.size(); j++) {
                Node y2 = this.outputs.get(j);

                Set<Node> p1 = parentSetsByOutput.get(y1);
                Set<Node> p2 = parentSetsByOutput.get(y2);

                if (p1 == null || p2 == null) {
                    continue;
                }

                if (p1.isEmpty() || p2.isEmpty()) {
                    continue;
                }

                double jaccard = jaccard(p1, p2);

                if (jaccard >= this.bossParentJaccardThreshold) {
                    outputGraph.addUndirectedEdge(y1, y2);
                }
            }
        }

        List<Set<Node>> outputClusters = connectedComponents(outputGraph);

        Map<Set<Node>, InputClassInfo> result = new LinkedHashMap<>();

        for (Set<Node> outputCluster : outputClusters) {
            if (outputCluster.isEmpty()) {
                continue;
            }

            Set<Node> inputClass = new LinkedHashSet<>();

            for (Node output : outputCluster) {
                Set<Node> parents = parentSetsByOutput.get(output);

                if (parents != null) {
                    inputClass.addAll(parents);
                }
            }

            if (inputClass.isEmpty()) {
                continue;
            }

            Node latent = createLatentNode();

            InputClassInfo info = new InputClassInfo(
                    inputClass,
                    new LinkedHashSet<>(outputCluster),
                    new LinkedHashSet<>(outputCluster),
                    latent
            );

            result.put(inputClass, info);
        }

        return result;
    }

    /**
     * Returns the connected components of the supplied graph.
     *
     * @param graph the graph
     * @return the connected components
     */
    private List<Set<Node>> connectedComponents(Graph graph) {
        List<Set<Node>> components = new ArrayList<>();
        Set<Node> visited = new LinkedHashSet<>();

        for (Node start : graph.getNodes()) {
            if (visited.contains(start)) {
                continue;
            }

            Set<Node> component = new LinkedHashSet<>();
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                Node current = queue.removeFirst();
                component.add(current);

                for (Node neighbor : graph.getAdjacentNodes(current)) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }

            components.add(component);
        }

        return components;
    }

    /**
     * Returns the Jaccard similarity of two sets.
     *
     * @param a the first set
     * @param b the second set
     * @return the Jaccard similarity
     */
    private double jaccard(Set<Node> a, Set<Node> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }

        Set<Node> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);

        Set<Node> union = new LinkedHashSet<>(a);
        union.addAll(b);

        if (union.isEmpty()) {
            return 1.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * Builds latent classes using Clark Glymour's suggested BOSS clustering rule:
     * outputs are clustered together iff they have the same measured-input parents
     * in the BOSS graph.
     *
     * <p>For each measured output Y, let Pa(Y) be the set of measured inputs that are
     * parents of Y in the BOSS graph. Outputs with identical Pa(Y) sets are grouped
     * into one cluster. Each such cluster defines one latent. The parent set Pa(Y)
     * becomes the input class for that latent.</p>
     *
     * @param bossGraph the BOSS graph on the measured variables
     * @return a map from input-class sets to their associated latent information
     */
    private Map<Set<Node>, InputClassInfo> buildInputClassesFromBossParents(Graph bossGraph) {
        Map<Set<Node>, Set<Node>> outputsByParentSet = new LinkedHashMap<>();

        for (Node output : this.outputs) {
            Set<Node> parentSet = new LinkedHashSet<>();

            for (Node parent : bossGraph.getParents(output)) {
                if (this.inputs.contains(parent)) {
                    parentSet.add(parent);
                }
            }

            if (parentSet.isEmpty()) {
                continue;
            }

            outputsByParentSet.computeIfAbsent(parentSet, k -> new LinkedHashSet<>()).add(output);
        }

        Map<Set<Node>, InputClassInfo> result = new LinkedHashMap<>();

        for (Map.Entry<Set<Node>, Set<Node>> entry : outputsByParentSet.entrySet()) {
            Set<Node> inputClass = new LinkedHashSet<>(entry.getKey());
            Set<Node> outSet = new LinkedHashSet<>(entry.getValue());

            if (inputClass.isEmpty() || outSet.isEmpty()) {
                continue;
            }

            Node latent = createLatentNode();

            InputClassInfo info = new InputClassInfo(
                    inputClass,
                    outSet,
                    new LinkedHashSet<>(outSet),
                    latent
            );

            result.put(inputClass, info);
        }

        return result;
    }

    /**
     * Returns the independence test in use.
     *
     * @return the test
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * Sets the independence test to use.
     *
     * <p>The variable list must match exactly, list-wise, with the current test.</p>
     *
     * @param test the new test
     */
    public void setTest(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }

        if (!this.test.getVariables().equals(test.getVariables())) {
            throw new IllegalArgumentException(
                    "The variables of the new test must equal the variables of the current test list-wise."
            );
        }

        this.test = test;
    }

    /**
     * Sets the Jaccard threshold for clustering outputs by similar BOSS parent sets.
     *
     * @param threshold the threshold
     */
    public void setBossParentJaccardThreshold(double threshold) {
        this.bossParentJaccardThreshold = threshold;
    }

    /**
     * Sets optional background knowledge.
     *
     * @param knowledge the knowledge to use
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets measured inputs explicitly. If both measured inputs and measured outputs are set,
     * step 1 is skipped.
     *
     * @param inputs the measured inputs
     */
    public void setMeasuredInputs(List<Node> inputs) {
        this.presetInputs = inputs == null ? null : new ArrayList<>(inputs);
    }

    /**
     * Sets measured outputs explicitly. If both measured inputs and measured outputs are set,
     * step 1 is skipped.
     *
     * @param outputs the measured outputs
     */
    public void setMeasuredOutputs(List<Node> outputs) {
        this.presetOutputs = outputs == null ? null : new ArrayList<>(outputs);
    }

    /**
     * Clears transient state.
     */
    private void resetState() {
        this.inputs.clear();
        this.outputs.clear();
        this.latentIndex = 1;
    }

    /**
     * Identifies measured inputs and outputs either from user-supplied lists or by running
     * depth-0 PC and classifying variables by indegree.
     */
    private void identifyInputsAndOutputs() {
        if (this.presetInputs != null && !this.presetInputs.isEmpty()
                && this.presetOutputs != null && !this.presetOutputs.isEmpty()) {
            this.inputs.addAll(this.presetInputs);
            this.outputs.addAll(this.presetOutputs);
            return;
        }

        Graph pattern = runBoss();

        for (Node node : pattern.getNodes()) {
            int indegree = pattern.getIndegree(node);
            int outdegree = pattern.getOutdegree(node);

            if (indegree == 0 && outdegree > 0) {
                this.inputs.add(node);
            } else if (indegree > 0) {
                this.outputs.add(node);
            }
        }
    }

    /**
     * Adds all measured input and output nodes to the graph.
     *
     * @param graph the graph to update
     */
    private void addMeasuredNodes(Graph graph) {
        for (Node node : this.inputs) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }
        }

        for (Node node : this.outputs) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }
        }
    }

    /**
     * Builds input equivalence classes by identical OUT-sets.
     *
     * @return a map from input-class sets to their class information
     */
    private Map<Set<Node>, InputClassInfo> buildInputClasses() {
        Map<Set<Node>, Set<Node>> groupedInputs = new LinkedHashMap<>();

        for (Node input : this.inputs) {
            Set<Node> outSet = computeOutSet(input);
            groupedInputs.computeIfAbsent(outSet, k -> new LinkedHashSet<>()).add(input);
        }

        Map<Set<Node>, InputClassInfo> result = new LinkedHashMap<>();

        for (Map.Entry<Set<Node>, Set<Node>> entry : groupedInputs.entrySet()) {
            Set<Node> outSet = new LinkedHashSet<>(entry.getKey());
            Set<Node> inputClass = new LinkedHashSet<>(entry.getValue());

            if (outSet.isEmpty() || inputClass.isEmpty()) {
                continue;
            }

            Node latent = createLatentNode();
            InputClassInfo info = new InputClassInfo(
                    inputClass,
                    outSet,
                    new LinkedHashSet<>(outSet),
                    latent
            );

            result.put(inputClass, info);
        }

        return result;
    }

    /**
     * Computes OUT(X), the set of measured outputs dependent on input X.
     *
     * @param input the measured input
     * @return the outputs dependent on that input
     */
    private Set<Node> computeOutSet(Node input) {
        Set<Node> outSet = new LinkedHashSet<>();

        for (Node output : this.outputs) {
            if (!isIndependent(input, output, Collections.emptySet())) {
                outSet.add(output);
            }
        }

        return outSet;
    }

    /**
     * Assigns outputs to latents by repeated leaf/minimal-set removal over residual OUT-sets.
     *
     * @param inputClasses the input class information
     * @param graph the graph to update
     */
    private void assignOutputsByLeafRemoval(Map<Set<Node>, InputClassInfo> inputClasses, Graph graph) {
        while (true) {
            List<InputClassInfo> active = new ArrayList<>();

            for (InputClassInfo info : inputClasses.values()) {
                if (!info.residualOut().isEmpty()) {
                    active.add(info);
                }
            }

            if (active.isEmpty()) {
                break;
            }

            List<InputClassInfo> leaves = new ArrayList<>();

            for (InputClassInfo candidate : active) {
                boolean minimal = true;

                for (InputClassInfo other : active) {
                    if (candidate == other) {
                        continue;
                    }

                    if (isProperSubset(other.residualOut(), candidate.residualOut())) {
                        minimal = false;
                        break;
                    }
                }

                if (minimal) {
                    leaves.add(candidate);
                }
            }

            if (leaves.isEmpty()) {
                break;
            }

            for (InputClassInfo leaf : leaves) {
                List<Node> toAssign = new ArrayList<>(leaf.residualOut());

                for (Node output : toAssign) {
                    if (!graph.containsNode(output)) {
                        graph.addNode(output);
                    }

                    if (!graph.isParentOf(leaf.latent(), output)) {
                        graph.addDirectedEdge(leaf.latent(), output);
                    }
                }
            }

            for (InputClassInfo leaf : leaves) {
                Set<Node> assigned = new LinkedHashSet<>(leaf.residualOut());

                for (InputClassInfo info : inputClasses.values()) {
                    if (info == leaf) {
                        info.residualOut().clear();
                    } else {
                        info.residualOut().removeAll(assigned);
                    }
                }
            }
        }
    }

    /**
     * Adds latent-to-latent edges according to strict inclusion of original OUT-sets.
     *
     * @param inputClasses the input class information
     * @param graph the graph to update
     */
    /**
     * Adds latent-to-latent edges using overlap and inclusion of measured input classes.
     *
     * <p>If two latent input classes overlap, the corresponding latents are adjacent.
     * If one input class is a proper subset of the other, orient from the smaller
     * class latent to the larger class latent. Otherwise add an undirected edge.</p>
     *
     * @param inputClasses the input class information
     * @param graph the graph to update
     */
    private void addLatentEdgesByInputClassOverlap(Map<Set<Node>, InputClassInfo> inputClasses, Graph graph) {
        List<InputClassInfo> infos = new ArrayList<>(inputClasses.values());

        for (int i = 0; i < infos.size(); i++) {
            InputClassInfo a = infos.get(i);

            for (int j = i + 1; j < infos.size(); j++) {
                InputClassInfo b = infos.get(j);

                Set<Node> pa = new LinkedHashSet<>(a.inputClass());
                Set<Node> pb = new LinkedHashSet<>(b.inputClass());

                Set<Node> intersection = new LinkedHashSet<>(pa);
                intersection.retainAll(pb);

                if (intersection.isEmpty()) {
                    continue;
                }

                if (isProperSubset(pa, pb)) {
                    if (!graph.isAdjacentTo(a.latent(), b.latent())) {
                        graph.addDirectedEdge(a.latent(), b.latent());
                    }
                } else if (isProperSubset(pb, pa)) {
                    if (!graph.isAdjacentTo(a.latent(), b.latent())) {
                        graph.addDirectedEdge(b.latent(), a.latent());
                    }
                } else {
                    if (!graph.isAdjacentTo(a.latent(), b.latent())) {
                        graph.addUndirectedEdge(a.latent(), b.latent());
                    }
                }
            }
        }
    }

    /**
     * Removes latent-to-latent edges when step 8 of the paper finds a separating witness.
     *
     * @param inputClasses the input class information
     * @param graph the graph to update
     */
    private void pruneLatentSubsetEdges(Map<Set<Node>, InputClassInfo> inputClasses, Graph graph) {
        List<InputClassInfo> infos = new ArrayList<>(inputClasses.values());

        for (InputClassInfo small : infos) {
            for (InputClassInfo large : infos) {
                if (small == large) {
                    continue;
                }

                if (!isProperSubset(small.originalOut(), large.originalOut())) {
                    continue;
                }

                Edge edge = graph.getEdge(small.latent(), large.latent());
                if (edge == null) {
                    continue;
                }

                if (existsStep8Witness(small, large)) {
                    graph.removeEdge(edge);
                }
            }
        }
    }

    /**
     * Returns true iff there exist
     * Or in OUT(Xj) \ OUT(Xk) and Os in OUT(Xk)
     * such that Or and Os are independent given some subset of Xk ∪ Xj.
     *
     * @param small the smaller OUT-set class Xk
     * @param large the larger OUT-set class Xj
     * @return true if a step-8 witness exists
     */
    private boolean existsStep8Witness(InputClassInfo small, InputClassInfo large) {
        Set<Node> largeOnly = new LinkedHashSet<>(large.originalOut());
        largeOnly.removeAll(small.originalOut());

        Set<Node> smallOut = new LinkedHashSet<>(small.originalOut());

        List<Node> conditioningUniverse = new ArrayList<>();
        conditioningUniverse.addAll(small.inputClass());

        for (Node node : large.inputClass()) {
            if (!conditioningUniverse.contains(node)) {
                conditioningUniverse.add(node);
            }
        }

        for (Node or : largeOnly) {
            for (Node os : smallOut) {
                if (existsSeparatingSubset(or, os, conditioningUniverse)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns true iff some subset of the supplied conditioning universe renders
     * a and b conditionally independent.
     *
     * @param a the first node
     * @param b the second node
     * @param universe the conditioning universe
     * @return true if some subset separates a and b
     */
    private boolean existsSeparatingSubset(Node a, Node b, List<Node> universe) {
        for (int size = 0; size <= universe.size(); size++) {
            if (existsSeparatingSubsetOfSize(a, b, universe, size, 0, new ArrayList<>())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Recursive helper to search all subsets of a given size.
     *
     * @param a the first node
     * @param b the second node
     * @param universe the conditioning universe
     * @param targetSize the target subset size
     * @param start the current start index
     * @param current the current subset
     * @return true if a separating subset is found
     */
    private boolean existsSeparatingSubsetOfSize(Node a, Node b,
                                                 List<Node> universe,
                                                 int targetSize,
                                                 int start,
                                                 List<Node> current) {
        if (current.size() == targetSize) {
            return isIndependent(a, b, new LinkedHashSet<>(current));
        }

        int remainingNeeded = targetSize - current.size();

        for (int i = start; i <= universe.size() - remainingNeeded; i++) {
            current.add(universe.get(i));

            if (existsSeparatingSubsetOfSize(a, b, universe, targetSize, i + 1, current)) {
                return true;
            }

            current.remove(current.size() - 1);
        }

        return false;
    }

    /**
     * Performs the paper's final PC-based cleanup.
     *
     * @param graph the graph to refine
     */
    private void finalRefinement(Graph graph) {
        Graph fullPattern = runPc(-1);

        for (Node output : this.outputs) {
            if (hasDirectedInputParent(output, fullPattern)) {
                continue;
            }

            removeLatentParents(output, graph);
            addMeasuredOutputAdjacencies(output, fullPattern, graph);
        }
    }

    /**
     * Returns true iff the supplied output has a directed parent among the measured inputs
     * in the full PC pattern.
     *
     * @param output the measured output
     * @param pattern the full PC pattern
     * @return true if some measured input is a directed parent
     */
    private boolean hasDirectedInputParent(Node output, Graph pattern) {
        for (Node input : this.inputs) {
            if (pattern.isParentOf(input, output)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes latent-to-output edges into the supplied output.
     *
     * @param output the measured output
     * @param graph the graph to modify
     */
    private void removeLatentParents(Node output, Graph graph) {
        List<Node> parents = new ArrayList<>(graph.getParents(output));

        for (Node parent : parents) {
            if (parent.getNodeType() == NodeType.LATENT) {
                graph.removeEdge(parent, output);
            }
        }
    }

    /**
     * Adds output-to-output adjacencies for the supplied output according to the full PC pattern.
     *
     * <p>If the PC pattern orients the edge, that direction is copied. Otherwise an undirected
     * edge is added.</p>
     *
     * @param output the output node
     * @param fullPattern the full PC pattern
     * @param graph the graph to update
     */
    private void addMeasuredOutputAdjacencies(Node output, Graph fullPattern, Graph graph) {
        for (Node neighbor : fullPattern.getAdjacentNodes(output)) {
            if (!this.outputs.contains(neighbor)) {
                continue;
            }

            if (graph.isAdjacentTo(output, neighbor)) {
                continue;
            }

            if (fullPattern.isParentOf(output, neighbor)) {
                graph.addDirectedEdge(output, neighbor);
            } else if (fullPattern.isParentOf(neighbor, output)) {
                graph.addDirectedEdge(neighbor, output);
            } else {
                graph.addUndirectedEdge(output, neighbor);
            }
        }
    }

    /**
     * Removes latent nodes having no measured parents or no measured children.
     *
     * @param graph the graph to modify
     */
    private void removeDegenerateLatents(Graph graph) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (getMeasuredParents(node, graph).isEmpty() || getMeasuredChildren(node, graph).isEmpty()) {
                graph.removeNode(node);
            }
        }
    }

    /**
     * Returns the measured parents of a node.
     *
     * @param node the node
     * @param graph the graph
     * @return the measured parents
     */
    private Set<Node> getMeasuredParents(Node node, Graph graph) {
        return removeLatents(new LinkedHashSet<>(graph.getParents(node)));
    }

    /**
     * Returns the measured children of a node.
     *
     * @param node the node
     * @param graph the graph
     * @return the measured children
     */
    private Set<Node> getMeasuredChildren(Node node, Graph graph) {
        return removeLatents(new LinkedHashSet<>(graph.getChildren(node)));
    }

    /**
     * Removes latent nodes from a set.
     *
     * @param nodes the nodes
     * @return only the measured nodes
     */
    private Set<Node> removeLatents(Collection<Node> nodes) {
        Set<Node> measured = new LinkedHashSet<>();

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.LATENT) {
                measured.add(node);
            }
        }

        return measured;
    }

    /**
     * Returns true if a and b are conditionally independent given the supplied set.
     *
     * @param a the first node
     * @param b the second node
     * @param conditioningSet the conditioning set
     * @return true if the test reports independence
     */
    private boolean isIndependent(Node a, Node b, Set<Node> conditioningSet) {
        try {
            return this.test.checkIndependence(a, b, conditioningSet).isIndependent();
        } catch (Exception e) {
            throw new RuntimeException("Conditional independence test failed.", e);
        }
    }

    /**
     * Runs PC with the supplied depth.
     *
     * @param depth the depth, with -1 meaning unrestricted
     * @return the resulting pattern
     */
    private Graph runPc(int depth) {
        try {
            Pc pc = new Pc(this.test);
            pc.setDepth(depth);
            pc.setKnowledge(this.knowledge);
            return pc.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PC search was interrupted.", e);
        }
    }

    private Graph runBoss() {
        try {
            PermutationSearch boss = new PermutationSearch(new Boss(this.score));
            boss.setKnowledge(this.knowledge);
            return boss.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PC search was interrupted.", e);
        }
    }

    /**
     * Returns true iff A is a proper subset of B.
     *
     * @param a set A
     * @param b set B
     * @return true iff A is a proper subset of B
     */
    private boolean isProperSubset(Set<Node> a, Set<Node> b) {
        return b.containsAll(a) && !a.equals(b);
    }

    /**
     * Creates a new latent node with a unique name.
     *
     * @return the new latent node
     */
    private Node createLatentNode() {
        Node latent = new GraphNode("L" + this.latentIndex++);
        latent.setNodeType(NodeType.LATENT);
        return latent;
    }

    /**
     * Data structure for one input equivalence class and its associated latent.
     */
    private record InputClassInfo(Set<Node> inputClass,
                                  Set<Node> originalOut,
                                  Set<Node> residualOut,
                                  Node latent) {
    }
}