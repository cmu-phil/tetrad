package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.Set;

public final class MsepFactEvaluator implements FactEvaluator {
    private final Graph g;

    public MsepFactEvaluator(Graph g) {
        this.g = g;
    }

    @Override
    public IndependenceResult evaluate(IndependenceFact fact) {
        Node x = g.getNode(fact.getX().getName());
        Node y = g.getNode(fact.getY().getName());
        if (x == null || y == null) {
            // fabricate a "bad" result; or throw
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
        }

        Set<Node> z = fact.getZ(); // these Nodes might not be graph nodes—rebind by name:
        Set<Node> z2 = z.stream()
                .map(n -> g.getNode(n.getName()))
                .filter(n -> n != null)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        boolean indep = isMSeparated(g, x, y, z2);

        // p-value doesn’t exist for m-sep. Keep NaN.
        return new IndependenceResult(new IndependenceFact(x, y, z2), indep, Double.NaN, Double.NaN);
    }

    private static boolean isMSeparated(Graph g, Node x, Node y, Set<Node> z) {
        // TODO: replace with your actual m-sep call.
        // Common patterns in Tetrad codebases are:
        //   - g.isMSeparatedFrom(x, y, z)
        //   - GraphUtils.isMSeparatedFrom(g, x, y, z)
        //   - new MsepTest(g).isIndependent(x, y, z)
        throw new UnsupportedOperationException("Wire to your m-separation implementation here.");
    }

    @Override public boolean hasParams() { return false; }
    @Override public String name() { return "m-separation (current graph)"; }
}
