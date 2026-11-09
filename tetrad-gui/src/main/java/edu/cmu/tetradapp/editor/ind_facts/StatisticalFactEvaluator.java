package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.graph.IndependenceFact;

/**
 * A fact evaluator that judges independence facts with a statistical independence test, through a cache of
 * independence queries.
 */
public final class StatisticalFactEvaluator implements FactEvaluator {
    private final CachedIndependenceQueries Q;

    /**
     * Constructs an evaluator that uses the given cached independence queries.
     *
     * @param Q the cached independence queries used to check independence.
     */
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