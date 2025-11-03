package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Change/instability test used by CD-NOD-PAG.
 * <p>
 * The single abstract method checks change w.r.t. a single context variable (env). Default helpers provide
 * multi-context "any-change" and "all-stable" logic.
 */
@FunctionalInterface
public interface ChangeTest {

    /**
     * Ensures that the specified objects are not null.
     *
     * @param data the DataSet object to be checked for nullity
     * @param y the Node object to be checked for nullity
     * @param Z the Set of Node objects to be checked for nullity
     * @throws NullPointerException if any of the specified objects is null
     */
    static void requireNonNulls(DataSet data, Node y, Set<Node> Z) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(Z, "Z");
    }

    /**
     * Evaluates whether there is a change or instability with respect to the given context variable.
     *
     * @param data the dataset to be analyzed for changes
     * @param y the target node being examined for changes
     * @param Z the set of conditioning nodes used in the analysis
     * @param env the context variable used to determine changes
     * @param alpha the significance level for the test
     * @return true if a change or instability is detected, otherwise false
     */
    boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha);

    /**
     * Determines whether there is any change or instability with respect to any of the specified context variables.
     *
     * @param data the dataset to be analyzed for changes
     * @param y the target node being examined for changes
     * @param Z the set of conditioning nodes used in the analysis
     * @param contexts the collection of context variables used to determine changes; if null, the method will return false
     * @param alpha the significance level for the test
     * @return true if a change or instability is detected with respect to any of the specified contexts, otherwise false
     */
    default boolean changesAny(DataSet data, Node y, Set<Node> Z,
                               Collection<Node> contexts, double alpha) {
        if (contexts == null) return false;
        for (Node e : contexts) {
            if (e != null && changes(data, y, Z, e, alpha)) return true;
        }
        return false;
    }

    /**
     * Determines whether the data, with respect to a given context variable, is stable
     * (i.e., there is no change or instability).
     *
     * @param data the dataset to be analyzed for stability
     * @param y the target node being examined for stability
     * @param Z the set of conditioning nodes used in the analysis
     * @param env the context variable used to evaluate stability
     * @param alpha the significance level for the stability check
     * @return true if the data is stable with respect to the given context variable, otherwise false
     */
    default boolean stable(DataSet data, Node y, Set<Node> Z, Node env, double alpha) {
        return !changes(data, y, Z, env, alpha);
    }

    /**
     * Determines whether the data is stable with respect to all specified context variables,
     * meaning no changes or instability are detected across any of the contexts provided.
     * If the collection of context variables is null, the method assumes stability and
     * returns true.
     *
     * @param data the dataset to be evaluated for stability
     * @param y the target node being examined for stability
     * @param Z the set of conditioning nodes used in the analysis
     * @param contexts the collection of context variables used to assess stability;
     *                 if null, the method assumes stability
     * @param alpha the significance level for the stability evaluation
     * @return true if the data is stable with respect to all specified contexts, otherwise false
     */
    default boolean stableAll(DataSet data, Node y, Set<Node> Z,
                              Collection<Node> contexts, double alpha) {
        if (contexts == null) return true;
        for (Node e : contexts) {
            if (e != null && changes(data, y, Z, e, alpha)) return false;
        }
        return true;
    }
}