package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Oracle ChangeTest backed by a known true graph, for exhaustive verification harnesses.
 * <p>
 * changes(y, Z, env) holds iff y and env are m-connected given Z in the true graph. The dataset
 * and alpha arguments are ignored (a dummy DataSet may be supplied to satisfy non-null contracts
 * elsewhere, e.g., {@link ChangeOracle}'s constructor).
 * <p>
 * Intended use: enumerate small augmented DAGs/MAGs with exogenous context nodes, drive
 * {@link CdnodPag}/{@link CdnodPagOrienter} with this oracle in place of a statistical test, and
 * check every committed arrowhead X *-&gt; Y against true ancestry (soundness requires
 * Y ∉ An(X) in the true graph).
 */
public final class MsepOracleChangeTest implements ChangeTest {

    /**
     * The true graph against which m-separation is evaluated.
     */
    private final Graph trueGraph;

    /**
     * Constructs the oracle over the given true graph.
     *
     * @param trueGraph the true graph; node names must match those used by the search.
     */
    public MsepOracleChangeTest(Graph trueGraph) {
        this.trueGraph = Objects.requireNonNull(trueGraph, "trueGraph");
    }

    /**
     * Returns true iff y and env are m-connected given Z in the true graph.
     *
     * @param data  ignored (may be a dummy dataset).
     * @param y     the target node.
     * @param Z     the conditioning set.
     * @param env   the context variable.
     * @param alpha ignored.
     * @return true iff y and env are m-connected given Z in the true graph.
     */
    @Override
    public boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha) {
        Node ty = node(y);
        Node te = node(env);
        Set<Node> tz = new LinkedHashSet<>();
        for (Node z : Z) tz.add(node(z));
        return !trueGraph.paths().isMSeparatedFrom(ty, te, tz, false);
    }

    private Node node(Node n) {
        Node t = trueGraph.getNode(n.getName());
        if (t == null) throw new IllegalArgumentException("Node not in true graph: " + n.getName());
        return t;
    }
}
