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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the Iterative Causal Discovery (ICD) algorithm for learning a Partial Ancestral Graph (PAG)
 * from observational data. ICD is an anytime algorithm that refines the PAG iteratively, increasing the
 * conditioning-set size r by 1 each iteration. At iteration r it produces an r-representing PAG.
 *
 * <p>Key differences from FCI:
 * <ul>
 *   <li>The skeleton is learned incrementally; orientations are reset and reapplied after every round
 *       in which at least one edge is removed.</li>
 *   <li>Conditioning sets are drawn from ICD-Sep rather than Possible-DSep: they must have size exactly r,
 *       must not contain either tested node, and every member must be a possible ancestor of node_i or node_j.</li>
 *   <li>Sets are ranked by sum-of-minimal-distances in the PDS-tree so that "closer" sets are tested first,
 *       mirroring the Python implementation.</li>
 * </ul>
 *
 * <p>Orientation schedule (faithful to the Python):
 * <ul>
 *   <li>r=0: R0, R1-R4 only.</li>
 *   <li>r=1: R0, R1-R4, then R5-R7 (if selection bias possible), then R8-R10 (if tail-complete).
 *       Always applied regardless of whether edges were removed.</li>
 *   <li>r>=2, not done: R0, R1-R4 only.</li>
 *   <li>r>=2, done: R5-R7 (if selection bias possible), R8-R10 (if tail-complete).
 *       R0-R4 are NOT re-run on the concluding iteration, matching the Python exactly.</li>
 * </ul>
 *
 * <p>Reference: Rohekar et al., "Iterative Causal Discovery in the Possible Presence of Latent Confounders
 * and Selection Bias" (NeurIPS 2021).
 */
public final class Icd implements IGraphSearch {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final IndependenceTest test;
    private final List<Node> variables;

    private Knowledge knowledge = new Knowledge();
    private boolean completeRuleSetUsed = true;
    private boolean isSelectionBias = true;    // enables R5, R6, R7
    private boolean isTailCompleteness = true; // enables R8, R9, R10
    private boolean verbose = false;
    private long elapsedTime;

    /** The PAG being built; exposed so callers can inspect intermediate states. */
    private Graph graph;

    /** Separation sets accumulated across all iterations. */
    private SepsetMap sepsets;

    // -----------------------------------------------------------------------
    // State carried across learnStructureIteration() calls
    // -----------------------------------------------------------------------

    private boolean done = false;
    private int condSetSize = 0; // r-value for the next iteration

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs an ICD search using the supplied independence test.
     *
     * @param test the conditional-independence oracle; must not be null.
     */
    public Icd(IndependenceTest test) {
        if (test == null) throw new NullPointerException("IndependenceTest must not be null.");
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
    }

    // -----------------------------------------------------------------------
    // IGraphSearch
    // -----------------------------------------------------------------------

    /**
     * Runs ICD to completion and returns the resulting PAG.
     *
     * @return a PAG over the variables of the independence test.
     * @throws InterruptedException if the calling thread is interrupted.
     */
    @Override
    public Graph search() throws InterruptedException {
        long start = MillisecondTimes.timeMillis();

        initGraph();

        this.done = false;
        this.condSetSize = 0;

        while (!done) {
            learnStructureIteration();
        }

        this.elapsedTime = MillisecondTimes.timeMillis() - start;
        return graph;
    }

    // -----------------------------------------------------------------------
    // Public anytime API
    // -----------------------------------------------------------------------

    /**
     * Executes a single ICD iteration (one r-value), advancing the internal state so the next call
     * processes r+1. May be called repeatedly by an anytime wrapper.
     *
     * @return the r-value used in this iteration.
     * @throws InterruptedException  if the calling thread is interrupted.
     * @throws IllegalStateException if ICD has already concluded.
     */
    public int learnStructureIteration() throws InterruptedException {
        if (done) throw new IllegalStateException("ICD has already concluded; no further iterations possible.");
        if (graph == null) initGraph();

        if (condSetSize == 0) {
            learnBaseStep0();
        } else if (condSetSize == 1) {
            done = learnBaseStep1();
        } else {
            done = learnIncrementalStep(condSetSize);
        }

        return condSetSize++;
    }

    // -----------------------------------------------------------------------
    // Core iteration methods
    // -----------------------------------------------------------------------

    /**
     * ICD iteration r=0: test unconditional independence for every adjacent pair.
     * Orientation: R0, R1-R4 only (no R5-R10).
     */
    private void learnBaseStep0() throws InterruptedException {
        List<Node> nodes = sortedNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node ni = nodes.get(i);
                Node nj = nodes.get(j);
                if (!graph.isAdjacentTo(ni, nj)) continue;

                if (test.checkIndependence(ni, nj, Collections.emptySet()).isIndependent()) {
                    graph.removeEdge(ni, nj);
                    sepsets.set(ni, nj, Collections.emptySet());
                    if (verbose) TetradLogger.getInstance().log(
                            "Removed " + ni + " -- " + nj + " | {} (r=0)");
                }
            }
        }

        // r=0: R0 + R1-R4 only, never R5-R10
        orientR0toR4();
    }

    /**
     * ICD iteration r=1: test independence conditioned on a single adjacent node.
     * Orientation: R0, R1-R4, then always R5-R7 and R8-R10 (matching Python behaviour
     * where the full rule set is applied unconditionally at r=1).
     *
     * @return true if no size-1 conditioning set existed for any remaining edge.
     */
    private boolean learnBaseStep1() throws InterruptedException {
        List<Node> nodes = sortedNodes();
        boolean anySetsExist = false;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node ni = nodes.get(i);
                Node nj = nodes.get(j);
                if (!graph.isAdjacentTo(ni, nj)) continue;

                // Unique singleton candidates from neighbours of ni OR nj, excluding the other endpoint
                Set<Node> adjI = new HashSet<>(graph.getAdjacentNodes(ni));
                adjI.remove(nj);
                Set<Node> adjJ = new HashSet<>(graph.getAdjacentNodes(nj));
                adjJ.remove(ni);

                // Preserve insertion order; deduplicate across both sides
                Set<Node> candidates = new LinkedHashSet<>();
                candidates.addAll(adjI);
                candidates.addAll(adjJ);

                for (Node z : candidates) {
                    anySetsExist = true; // a candidate of size 1 exists
                    Set<Node> condSet = Collections.singleton(z);
                    if (test.checkIndependence(ni, nj, condSet).isIndependent()) {
                        graph.removeEdge(ni, nj);
                        sepsets.set(ni, nj, condSet);
                        if (verbose) TetradLogger.getInstance().log(
                                "Removed " + ni + " -- " + nj + " | " + condSet + " (r=1)");
                        break;
                    }
                }
            }
        }

        // Python always applies the full rule set at r=1, regardless of done.
        orientR0toR4();
        orientR5toR10();

        return !anySetsExist;
    }

    /**
     * ICD iteration r>=2: the general incremental step.
     * <ul>
     *   <li>Not done: R0, R1-R4 only.</li>
     *   <li>Done: R5-R10 only (R0-R4 are NOT re-run, faithful to the Python).</li>
     * </ul>
     *
     * @param r conditioning-set size for this iteration.
     * @return true if no candidate conditioning set of size r existed for any remaining edge.
     */
    private boolean learnIncrementalStep(int r) throws InterruptedException {
        List<Node> nodes = sortedNodes();
        boolean anySetsExist = false;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node ni = nodes.get(i);
                Node nj = nodes.get(j);
                if (!graph.isAdjacentTo(ni, nj)) continue;

                List<IcdSepCandidate> condSets = getIcdSepSets(ni, nj, r);
                if (!condSets.isEmpty()) anySetsExist = true;

                for (IcdSepCandidate cand : condSets) {
                    if (test.checkIndependence(ni, nj, cand.condSet).isIndependent()) {
                        graph.removeEdge(ni, nj);
                        sepsets.set(ni, nj, cand.condSet);
                        if (verbose) TetradLogger.getInstance().log(
                                "Removed " + ni + " -- " + nj + " | " + cand.condSet + " (r=" + r + ")");
                        break;
                    }
                }
            }
        }

        if (anySetsExist) {
            // Not done: skeleton was still active at this r; re-orient with R0-R4.
            orientR0toR4();
        } else {
            // Done: apply only R5-R10, exactly as the Python does.
            orientR5toR10();
        }

        return !anySetsExist;
    }

    // -----------------------------------------------------------------------
    // Orientation helpers — split to match Python's rule-set schedule exactly
    // -----------------------------------------------------------------------

    /**
     * Resets all marks to Circle, then applies R0 (v-structures) and R1-R4.
     * Used at r=0 and at non-concluding r>=1 iterations.
     */
    private void orientR0toR4() throws InterruptedException {
        graph.reorientAllWith(Endpoint.CIRCLE);
        FciOrient fciOrient = buildFciOrient();
        fciOrient.fciOrientbk(knowledge, graph, graph.getNodes(), !isSelectionBias);
        fciOrient.ruleR0(graph, new HashSet<>(), false);
        fciOrient.rulesR1R2cycle(graph);
        fciOrient.ruleR3(graph);
        fciOrient.ruleR4(graph);
    }

    /**
     * Applies R5-R7 (selection bias) and R8-R10 (tail completeness) to the current graph,
     * without resetting marks or re-running R0-R4. Used on the concluding iteration (r>=2)
     * and always at r=1, matching the Python schedule.
     */
    private void orientR5toR10() throws InterruptedException {
        FciOrient fciOrient = buildFciOrient();
        if (isSelectionBias) {
            fciOrient.rulesR5R6R7(graph);
        }
        if (isTailCompleteness) {
            fciOrient.rulesR8R9R10(graph);
        }
    }

    /**
     * Constructs and configures a fresh {@link FciOrient} instance, matching the stateless
     * use pattern of the Python implementation.
     */
    private FciOrient buildFciOrient() {
        R0R4StrategyTestBased strategy =
                (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(test, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(-1);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);
        return fciOrient;
    }

    // -----------------------------------------------------------------------
    // ICD-Sep: PDS-tree construction and conditioning-set enumeration
    // -----------------------------------------------------------------------

    /**
     * Returns ICD-Sep conditioning sets for the pair (ni, nj) at size r, sorted by
     * sum-of-minimal-distances ascending.
     */
    private List<IcdSepCandidate> getIcdSepSets(Node ni, Node nj, int r) {
        PdsResult pdsI = buildPdsTree(ni, r);
        PdsResult pdsJ = buildPdsTree(nj, r);

        List<IcdSepCandidate> allCandidates = new ArrayList<>();
        allCandidates.addAll(enumerateSubsets(pdsI, r));
        allCandidates.addAll(enumerateSubsets(pdsJ, r));

        Set<String> seen = new LinkedHashSet<>();
        List<IcdSepCandidate> result = new ArrayList<>();

        for (IcdSepCandidate cand : allCandidates) {
            if (cand.condSet.contains(ni) || cand.condSet.contains(nj)) continue;
            if (!allPossibleAncestors(cand.condSet, ni, nj)) continue;
            if (seen.add(setKey(cand.condSet))) result.add(cand);
        }

        result.sort(Comparator.comparingInt(c -> c.distanceSum));
        return result;
    }

    /**
     * BFS over the PAG skeleton building a PDS-tree rooted at {@code root} up to depth
     * {@code maxDepth}.
     *
     * <p>A node {@code node3} is added when {@code node2} is a possible collider on
     * {@code node1 -- node2 -- node3}, i.e. {@code !graph.isDefNoncollider(node1, node2, node3)}.
     *
     * <p>Edge removal is simulated via a canonical string-key set (symmetric, blocking both
     * traversal directions), mirroring the Python's {@code adj_graph.remove_edge}.
     */
    private PdsResult buildPdsTree(Node root, int maxDepth) {
        Map<Node, Integer> minDist = new LinkedHashMap<>();
        Set<String> removedEdges = new HashSet<>();
        Queue<NodePair> queue = new LinkedList<>();

        // Seed: all neighbours of root at depth 1; block root--nb to prevent looping back.
        for (Node nb : graph.getAdjacentNodes(root)) {
            minDist.put(nb, 1);
            queue.offer(new NodePair(root, nb, 1));
            removedEdges.add(edgeKey(root, nb));
        }

        while (!queue.isEmpty()) {
            NodePair pair = queue.poll();
            Node node1 = pair.node1;
            Node node2 = pair.node2;
            int depth = pair.depth;

            // Python: if node_2_tree.depth_level >= max_depth: continue
            if (depth >= maxDepth) continue;

            for (Node node3 : graph.getAdjacentNodes(node2)) {
                String eKey = edgeKey(node2, node3);
                if (removedEdges.contains(eKey)) continue;

                // Possible collider iff NOT a definite non-collider.
                if (!graph.isDefNoncollider(node1, node2, node3)) {
                    int d = depth + 1;
                    minDist.merge(node3, d, Math::min);
                    removedEdges.add(eKey);
                    queue.offer(new NodePair(node2, node3, d));
                }
            }
        }

        minDist.remove(root);
        return new PdsResult(root, minDist);
    }

    /**
     * Enumerates all subsets of size {@code r} from the PDS set, annotating each with the
     * sum of minimal distances of its members.
     */
    private List<IcdSepCandidate> enumerateSubsets(PdsResult pds, int r) {
        List<Node> nodes = new ArrayList<>(pds.minDist.keySet());
        if (nodes.size() < r) return Collections.emptyList();

        List<IcdSepCandidate> result = new ArrayList<>();
        chooseR(nodes, r, 0, new ArrayList<>(), pds.minDist, result);
        return result;
    }

    /** Recursive combination generator. */
    private void chooseR(List<Node> pool, int r, int start,
                         List<Node> current, Map<Node, Integer> dist,
                         List<IcdSepCandidate> out) {
        if (current.size() == r) {
            int distSum = current.stream().mapToInt(n -> dist.getOrDefault(n, 0)).sum();
            out.add(new IcdSepCandidate(new HashSet<>(current), distSum));
            return;
        }
        for (int i = start; i < pool.size(); i++) {
            current.add(pool.get(i));
            chooseR(pool, r, i + 1, current, dist, out);
            current.remove(current.size() - 1);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Initialises a complete PAG with o--o edges and a fresh SepsetMap. */
    private void initGraph() {
        graph = new EdgeListGraph(variables);
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                graph.addEdge(Edges.nondirectedEdge(variables.get(i), variables.get(j)));
            }
        }
        graph.reorientAllWith(Endpoint.CIRCLE);
        sepsets = new SepsetMap();
    }

    /**
     * Returns true iff every node in {@code condSet} is a possible ancestor of {@code ni} or {@code nj}.
     * Tetrad's {@code existsPotentiallyDirectedPath} returns true when from == to, so no guard needed.
     */
    private boolean allPossibleAncestors(Set<Node> condSet, Node ni, Node nj) {
        for (Node z : condSet) {
            if (!graph.paths().existsPotentiallyDirectedPath(z, ni)
                    && !graph.paths().existsPotentiallyDirectedPath(z, nj)) {
                return false;
            }
        }
        return true;
    }

    private List<Node> sortedNodes() {
        List<Node> nodes = new ArrayList<>(graph.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));
        return nodes;
    }

    private String setKey(Set<Node> nodes) {
        List<String> names = new ArrayList<>();
        for (Node n : nodes) names.add(n.getName());
        Collections.sort(names);
        return String.join("\u0001", names);
    }

    private static String edgeKey(Node a, Node b) {
        String na = a.getName(), nb = b.getName();
        return na.compareTo(nb) <= 0 ? na + "\u0001" + nb : nb + "\u0001" + na;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public Graph getGraph() { return graph; }
    public SepsetMap getSepsets() { return sepsets; }
    public long getElapsedTime() { return elapsedTime; }
    public Knowledge getKnowledge() { return knowledge; }

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * If true (default), orientation rules R5/R6/R7 for possible selection bias are applied.
     * Set to false when selection bias is known to be absent.
     */
    public void setSelectionBias(boolean selectionBias) {
        this.isSelectionBias = selectionBias;
    }

    /**
     * If true (default), orientation rules R8/R9/R10 for tail-completeness are applied.
     */
    public void setTailCompleteness(boolean tailCompleteness) {
        this.isTailCompleteness = tailCompleteness;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    private static final class PdsResult {
        @SuppressWarnings("unused")
        final Node root;
        final Map<Node, Integer> minDist;

        PdsResult(Node root, Map<Node, Integer> minDist) {
            this.root = root;
            this.minDist = minDist;
        }
    }

    private static final class IcdSepCandidate {
        final Set<Node> condSet;
        final int distanceSum;

        IcdSepCandidate(Set<Node> condSet, int distanceSum) {
            this.condSet = condSet;
            this.distanceSum = distanceSum;
        }
    }

    private static final class NodePair {
        final Node node1;
        final Node node2;
        final int depth;

        NodePair(Node node1, Node node2, int depth) {
            this.node1 = node1;
            this.node2 = node2;
            this.depth = depth;
        }
    }
}
