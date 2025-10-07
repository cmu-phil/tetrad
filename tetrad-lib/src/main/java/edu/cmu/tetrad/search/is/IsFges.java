package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.Score;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Instance-Specific FGES: reuses the FGES search machinery but overrides the
 * bump computations to use {@link IsScore} (instance-specific) rather than the
 * pure population score.
 */
public class IsFges extends Fges {

    private final IsScore isScore;
    /** Optional population backbone; if set, it is kept in the same node-identity space as FGES. */
    private Graph populationGraph;

    /**
     * @param isScore          instance-specific score driving local deltas (non-null)
     * @param populationScore  population score passed to the FGES super-class (non-null)
     */
    public IsFges(final IsScore isScore, final Score populationScore) {
        super(Objects.requireNonNull(populationScore, "populationScore"));
        this.isScore = Objects.requireNonNull(isScore, "isScore");
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Provide a population graph to bias the IS bumps; nodes are remapped to FGES's variable identities.
     */
    public void setPopulationGraph(Graph pop) {
        if (pop == null) {
            this.populationGraph = null;
        } else {
            this.populationGraph = GraphUtils.replaceNodes(pop, getSearchVariables());
        }
    }

    // ---------------------------------------------------------------------
    // FGES hook overrides — compute move bumps via IsScore
    // ---------------------------------------------------------------------

    @Override
    protected double initialPairBump(final Node parent, final Node child,
                                     final ConcurrentMap<Node, Integer> idx) {
        final int p = idx.get(parent);
        final int c = idx.get(child);
        final int[] popPa = toSortedIdxArray(ensurePop().getParents(child), idx);
        final int[] popCh = toSortedIdxArray(ensurePop().getChildren(child), idx);
        // No IS parents in the empty seeding step
        return isScore.localScoreDiff(p, c, new int[0], popPa, popCh);
    }

    @Override
    protected double initialPairBumpReverse(final Node parent, final Node child,
                                            final ConcurrentMap<Node, Integer> idx) {
        final int p = idx.get(parent);
        final int c = idx.get(child);
        final int[] popPa = toSortedIdxArray(ensurePop().getParents(parent), idx);
        final int[] popCh = toSortedIdxArray(ensurePop().getChildren(parent), idx);
        return isScore.localScoreDiff(c, p, new int[0], popPa, popCh);
    }

    @Override
    protected double insertBump(final Node x, final Node y, final Set<Node> T, final Set<Node> naYX,
                                final Set<Node> parentsY, final ConcurrentMap<Node, Integer> idx)
            throws InterruptedException {
        // parents_for_eval = naYX ∪ T ∪ parents(y)
        final Set<Node> union = new HashSet<>(naYX);
        union.addAll(T);
        union.addAll(parentsY);
        final int[] parentsIS = toSortedIdxArray(union, idx);

        final int[] popPa = toSortedIdxArray(ensurePop().getParents(y), idx);
        final int[] popCh = toSortedIdxArray(ensurePop().getChildren(y), idx);

        return isScore.localScoreDiff(idx.get(x), idx.get(y), parentsIS, popPa, popCh);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /** Ensure a population graph exists and uses FGES's node identities. */
    private Graph ensurePop() {
        if (populationGraph == null) {
            populationGraph = new EdgeListGraph(getSearchVariables());
        }
        return populationGraph;
    }

    /** Convert a node collection to an <em>index-sorted</em> int[] for determinism across runs. */
    private static int[] toSortedIdxArray(final Collection<Node> nodes,
                                          final ConcurrentMap<Node, Integer> idx) {
        final int n = nodes.size();
        final int[] a = new int[n];
        int i = 0;
        for (Node node : nodes) a[i++] = idx.get(node);
        Arrays.sort(a);
        return a;
    }
}
