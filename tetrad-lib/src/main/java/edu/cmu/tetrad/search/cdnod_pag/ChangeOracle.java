package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Objects;
import java.util.Set;

/**
 * Thin wrapper around ChangeTest, fixing (data, env, alpha).
 */
public final class ChangeOracle {
    private final DataSet data;
    private final Node env;
    private final double alpha;
    private final ChangeTest test;

    public ChangeOracle(DataSet data, Node env, double alpha, ChangeTest test) {
        this.data = Objects.requireNonNull(data);
        this.env = Objects.requireNonNull(env);
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test);
    }

    /** true iff P(y | Z) varies with E. */
    public boolean changes(Node y, Set<Node> Z) {
        return test.changes(data, y, Z, env, alpha);
    }

    public Node env() { return env; }
}