package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import java.util.Set;

/**
 * Returns true iff P(y | Z) varies with the environment E.
 * Implement this using your preferred test (CG-LRT, DG-LRT, BF-LRT, kernel, ...).
 */
@FunctionalInterface
public interface ChangeTest {
    boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha);
}