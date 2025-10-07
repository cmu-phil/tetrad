package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Change/instability test used by CD-NOD-PAG.
 *
 * The single abstract method checks change w.r.t. a single context variable (env).
 * Default helpers provide multi-context "any-change" and "all-stable" logic.
 */
@FunctionalInterface
public interface ChangeTest {

    /**
     * @return true iff P(y | Z) varies with the given context/environment variable 'env'.
     */
    boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha);

    /** Convenience: changes w.r.t. any context in 'contexts'. */
    default boolean changesAny(DataSet data, Node y, Set<Node> Z,
                               Collection<Node> contexts, double alpha) {
        if (contexts == null) return false;
        for (Node e : contexts) {
            if (e != null && changes(data, y, Z, e, alpha)) return true;
        }
        return false;
    }

    /** Convenience: stable (no change) w.r.t. a single context. */
    default boolean stable(DataSet data, Node y, Set<Node> Z, Node env, double alpha) {
        return !changes(data, y, Z, env, alpha);
    }

    /** Convenience: stable (no change) w.r.t. all contexts in 'contexts'. */
    default boolean stableAll(DataSet data, Node y, Set<Node> Z,
                              Collection<Node> contexts, double alpha) {
        if (contexts == null) return true;
        for (Node e : contexts) {
            if (e != null && changes(data, y, Z, e, alpha)) return false;
        }
        return true;
    }

    /** Null-safe helper. */
    static void requireNonNulls(DataSet data, Node y, Set<Node> Z) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(Z, "Z");
    }
}