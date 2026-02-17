package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.graph.IndependenceFact;

public final class StatisticalFactEvaluator implements FactEvaluator {
    private final CachedIndependenceQueries Q;

    public StatisticalFactEvaluator(CachedIndependenceQueries Q) {
        this.Q = Q;
    }

    @Override public IndependenceResult evaluate(IndependenceFact fact) throws InterruptedException {
        // NOTE: Q.checkIndependence expects Nodes that belong to Q.getTest().getVariables().
        return Q.checkIndependence(fact.getX(), fact.getY(), fact.getZ());
    }

    @Override public boolean hasParams() { return true; }
    @Override public String name() { return "Statistical test (selected)"; }
}