package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.Node;

import java.util.Collection;

/** Minimal local-score interface used by CAM. */
public interface AdditiveLocalScorer {
    double localScore(Node y, Collection<Node> parents);
    double localScore(int yIndex, int... parentIdxs);

    // Optional knobs (no-ops by default so adapters can chain)
    default AdditiveLocalScorer setPenaltyDiscount(double c) { return this; }
    default AdditiveLocalScorer setRidge(double r) { return this; }
}