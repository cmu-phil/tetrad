package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/** Thin wrapper around ChangeTest providing multi-context checks for the orienter. */
public final class ChangeOracle {

    private final DataSet data;
    private final List<Node> contexts;   // tier-1 context variables (one or more)
    private final double alpha;
    private final ChangeTest test;

    /** Single-context convenience ctor (kept for back-compat). */
    public ChangeOracle(DataSet data, Node env, double alpha, ChangeTest test) {
        this(data,
                env == null ? Collections.emptyList() : Collections.singletonList(env),
                alpha, test);
    }

    /** Multi-context ctor. */
    public ChangeOracle(DataSet data, Collection<Node> contexts, double alpha, ChangeTest test) {
        this.data = Objects.requireNonNull(data, "data");
        this.contexts = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(contexts, "contexts")));
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
    }

    /** First context if present (back-compat with older code that expects a single env). */
    public Node env() { return contexts.isEmpty() ? null : contexts.get(0); }

    /** All contexts. */
    public List<Node> contexts() { return contexts; }

    /** True iff P(Y | S) varies w.r.t. at least one context. */
    public boolean changes(Node y, Set<Node> S) {
        return test.changesAny(data, y, S, contexts, alpha);
    }

    /** True iff P(Y | S) is stable (no change) w.r.t. all contexts. */
    public boolean stable(Node y, Set<Node> S) {
        return test.stableAll(data, y, S, contexts, alpha);
    }
}