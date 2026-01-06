package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Thin wrapper around ChangeTest providing multi-context checks for the orienter.
 */
public final class ChangeOracle {

    /**
     * The primary dataset used for conducting change tests and context evaluations. This dataset contains the data
     * required by the `ChangeTest` to assess changes and stability within given contexts.
     */
    private final DataSet data;
    /**
     * A list of tier-1 context variables used for evaluating changes and stability. This collection represents one or
     * more primary contexts against which the change tests are performed.
     */
    private final List<Node> contexts;   // tier-1 context variables (one or more)
    /**
     * The significance level used for statistical hypothesis testing within the change tests. It represents the
     * probability of rejecting the null hypothesis when it is true (Type I error). A smaller alpha value indicates a
     * stricter threshold for identifying changes or instability within the given contexts.
     */
    private final double alpha;
    /**
     * The change test mechanism employed to assess variations or stability in the dataset.
     * <p>
     * This variable holds a reference to an implementation of the {@link ChangeTest} interface, which defines the logic
     * for evaluating whether a target variable's conditional distribution changes with respect to specific context
     * variables. The implementation may include default helpers for multi-context assessment, such as determining if
     * the target shows any change or is stable across all provided contexts.
     */
    private final ChangeTest test;

    /**
     * Constructor for the ChangeOracle class using a single context.
     *
     * @param data  the DataSet object that contains the data to be analyzed
     * @param env   the Node representing the single context environment; if null, no context will be used
     * @param alpha the significance level for the change test
     * @param test  the ChangeTest object used to determine changes in the data
     */
    public ChangeOracle(DataSet data, Node env, double alpha, ChangeTest test) {
        this(data,
                env == null ? Collections.emptyList() : Collections.singletonList(env),
                alpha, test);
    }

    /**
     * Constructs a new instance of the ChangeOracle class with multiple contexts.
     *
     * @param data     the DataSet object that contains the data to be analyzed
     * @param contexts the collection of Node objects representing the contexts; must not be null
     * @param alpha    the significance level for the change test
     * @param test     the ChangeTest object used to determine changes in the data; must not be null
     */
    public ChangeOracle(DataSet data, Collection<Node> contexts, double alpha, ChangeTest test) {
        this.data = Objects.requireNonNull(data, "data");
        this.contexts = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(contexts, "contexts")));
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
    }

    /**
     * Retrieves the first context (environment) from the list of contexts. If no contexts are available, returns null.
     *
     * @return the first Node from the list of contexts, or null if the list is empty
     */
    public Node env() {
        return contexts.isEmpty() ? null : contexts.get(0);
    }

    /**
     * Retrieves the list of contexts associated with this ChangeOracle instance.
     *
     * @return a list of Node objects representing the contexts; returns an empty list if no contexts are available
     */
    public List<Node> contexts() {
        return contexts;
    }

    /**
     * Determines if any changes occur with respect to the specified node and set of nodes given the current data,
     * contexts, and alpha significance level.
     *
     * @param y the target node to be tested for changes
     * @param S the set of nodes to be considered as the conditioning set
     * @return true if changes are detected concerning the specified node and set of nodes, false otherwise
     */
    public boolean changes(Node y, Set<Node> S) {
        return test.changesAny(data, y, S, contexts, alpha);
    }

    /**
     * Determines if the given node and set of nodes are stable with respect to the current data, contexts, and alpha
     * significance level.
     *
     * @param y the target node to be tested for stability
     * @param S the set of nodes to be used as the conditioning set
     * @return true if the target node and conditioning set are stable; false otherwise
     */
    public boolean stable(Node y, Set<Node> S) {
        return test.stableAll(data, y, S, contexts, alpha);
    }
}