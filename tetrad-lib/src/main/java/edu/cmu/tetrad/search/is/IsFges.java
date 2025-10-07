package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.Score;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Instance-Specific FGES: overrides just the bump computations to use ISScore, while the FGES base still runs the rest
 * of the algorithm (caching, Meek, BES).
 */
public class IsFges extends Fges {

    private final IsScore isScore;
    private Graph populationGraph; // optional; may be set by caller

    /**
     * Constructs an instance of the IsFges class with the specified instance-specific score
     * and population score. Ensures that the instance-specific score is not null.
     *
     * @param isScore the instance-specific score used for FGES-style search
     * @param populationScore the population score used for FGES-style search
     * @throws NullPointerException if the provided instance-specific score is null
     */
    public IsFges(IsScore isScore, Score populationScore) {
        super(populationScore);
        if (isScore == null) throw new NullPointerException("isScore == null");
        this.isScore = isScore;
    }

    private static int[] asIdxArray(Collection<Node> nodes, ConcurrentMap<Node, Integer> idx) {
        int[] a = new int[nodes.size()];
        int i = 0;
        for (Node n : nodes) a[i++] = idx.get(n);
        return a;
    }

    // -------------------------------------------------------------------------
    // Hook overrides: instance-specific bumps
    // -------------------------------------------------------------------------

    /**
     * Sets the population graph for the current instance. If the provided graph is null,
     * the population graph is set to null. Otherwise, it ensures that the node identities
     * in the population graph match those used by FGES.
     *
     * @param pop the population graph to be set; null if no graph should be assigned.
     */
    public void setPopulationGraph(Graph pop) {
        if (pop == null) {
            this.populationGraph = null;
        } else {
            // Make sure node identity objects match those used by FGES.
            this.populationGraph = GraphUtils.replaceNodes(pop, getSearchVariables());
        }
    }

    @Override
    protected double initialPairBump(Node parent, Node child,
                                     ConcurrentMap<Node, Integer> idx) {
        final int p = idx.get(parent);
        final int c = idx.get(child);
        int[] popPa = asIdxArray(ensurePop().getParents(child), idx);
        int[] popCh = asIdxArray(ensurePop().getChildren(child), idx);
        // No conditioning set for the empty-graph seeding step.
        return isScore.localScoreDiff(p, c, new int[0], popPa, popCh);
    }

    @Override
    protected double initialPairBumpReverse(Node parent, Node child,
                                            ConcurrentMap<Node, Integer> idx) {
        final int p = idx.get(parent);
        final int c = idx.get(child);
        int[] popPa = asIdxArray(ensurePop().getParents(parent), idx);
        int[] popCh = asIdxArray(ensurePop().getChildren(parent), idx);
        return isScore.localScoreDiff(c, p, new int[0], popPa, popCh);
    }

    @Override
    protected double insertBump(Node x, Node y, Set<Node> T, Set<Node> naYX,
                                Set<Node> parents, ConcurrentMap<Node, Integer> idx)
            throws InterruptedException {
        // parents_for_eval = naYX ∪ T ∪ parents(y)
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(T);
        set.addAll(parents);
        int[] parentIdx = asIdxArray(set, idx);

        int[] popPa = asIdxArray(ensurePop().getParents(y), idx);
        int[] popCh = asIdxArray(ensurePop().getChildren(y), idx);

        return isScore.localScoreDiff(idx.get(x), idx.get(y), parentIdx, popPa, popCh);
    }

    /**
     * Ensure a population graph exists and uses FGES's node identities.
     */
    private Graph ensurePop() {
        if (populationGraph == null) {
            populationGraph = new EdgeListGraph(getSearchVariables());
        }
        return populationGraph;
    }
}