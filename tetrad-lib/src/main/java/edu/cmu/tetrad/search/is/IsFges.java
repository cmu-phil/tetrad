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
     * Constructs an instance of the IsFges class using the provided score objects.
     *
     * @param isScore the score used for evaluating individual search structures, must not be null
     * @param populationScore the score associated with the population graph, must not be null
     * @throws NullPointerException if any of the provided parameters is null
     */
    public IsFges(final IsScore isScore, final Score populationScore) {
        super(Objects.requireNonNull(populationScore, "populationScore"));
        this.isScore = Objects.requireNonNull(isScore, "isScore");
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Sets the population graph by either assigning it directly or replacing its nodes
     * with the corresponding search variables using the utility method.
     *
     * @param pop the graph representing the population; if null, the population graph is reset to null.
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

    /**
     * Computes the initial score bump for a given parent-child pair within the search
     * context using their indices and relevant population graph relationships.
     *
     * @param parent the parent node in the current calculation (non-null)
     * @param child the child node in the current calculation (non-null)
     * @param idx a mapping of nodes to their respective indices (non-null)
     * @return the score difference associated with the given parent-child relationship
     */
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

    /**
     * Computes the initial score adjustment for a given parent-child pair in reverse
     * within the search context, using their indices and relevant relationships in
     * the population graph.
     *
     * @param parent the parent node in the reverse score calculation (non-null)
     * @param child the child node in the reverse score calculation (non-null)
     * @param idx a mapping of nodes to their respective indices (non-null)
     * @return the reverse score difference associated with the given parent-child relationship
     */
    @Override
    protected double initialPairBumpReverse(final Node parent, final Node child,
                                            final ConcurrentMap<Node, Integer> idx) {
        final int p = idx.get(parent);
        final int c = idx.get(child);
        final int[] popPa = toSortedIdxArray(ensurePop().getParents(parent), idx);
        final int[] popCh = toSortedIdxArray(ensurePop().getChildren(parent), idx);
        return isScore.localScoreDiff(c, p, new int[0], popPa, popCh);
    }

    /**
     * Computes the score adjustment for adding an edge into the search graph by considering
     * the given sets of nodes and the relationships in the population graph.
     *
     * @param x the node being evaluated for addition to the graph (non-null)
     * @param y the target node being connected to (non-null)
     * @param T the set of nodes representing the current search phase context (non-null)
     * @param naYX the set of non-adjacents between node y and x (non-null)
     * @param parentsY the set of nodes that are parents of node y in the current context (non-null)
     * @param idx a mapping of nodes to their respective indices for score calculations (non-null)
     * @return the computed score difference for adding the edge between the given nodes
     * @throws InterruptedException if the operation is interrupted during execution
     */
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
