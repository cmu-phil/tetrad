package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.Node;

import java.util.Collection;

/**
 * Interface for scoring a target node in a graphical model based on its local structure,
 * specifically under an additive scoring framework. This interface facilitates the calculation
 * of scores for a node given its parent nodes using customizable parameters such as penalty
 * discount and ridge regularization.
 */
public interface AdditiveLocalScorer {
    /**
     * Computes the local score for a target node given its set of parent nodes in a graphical model.
     *
     * @param y the target node for which the local score is being computed
     * @param parents the set of parent nodes for the target node
     * @return the computed local score as a double
     */
    double localScore(Node y, Collection<Node> parents);

    /**
     * Computes the local score for a target node's index given the indices of its parent nodes
     * in a graphical model.
     *
     * @param yIndex the index of the target node for which the local score is being computed
     * @param parentIdxs the indices of the parent nodes for the target node
     * @return the computed local score as a double
     */
    double localScore(int yIndex, int... parentIdxs);

    /**
     * Sets the penalty discount value used in the additive scoring framework.
     *
     * @param c the penalty discount value to be applied; this value influences
     *          the regularization applied during scoring
     * @return the current instance of {@code AdditiveLocalScorer}, allowing for method chaining
     */
    default AdditiveLocalScorer setPenaltyDiscount(double c) { return this; }

    /**
     * Sets the ridge regularization parameter to be used in the additive scoring framework.
     * The ridge parameter serves as a regularization factor to prevent overfitting and
     * stabilize the scoring process.
     *
     * @param r the ridge regularization value; a higher value increases regularization,
     *          reducing the influence of highly correlated variables
     * @return the current instance of {@code AdditiveLocalScorer}, allowing method chaining
     */
    default AdditiveLocalScorer setRidge(double r) { return this; }
}