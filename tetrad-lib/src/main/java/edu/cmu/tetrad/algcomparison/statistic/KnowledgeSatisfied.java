package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Implementation of the KnowledgeSatisfied class. This class represents a statistic that measures whether the provided
 * knowledge is satisfied for the estimated graph.
 */
public class KnowledgeSatisfied implements Statistic, HasKnowledge {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The `knowledge` variable represents a knowledge object.
     * <p>
     * This variable is a private member of the `KnowledgeSatisfied` class, which implements the `Statistic` and
     * `HasKnowledge` interfaces. It is used to measure whether the provided knowledge is satisfied for the estimated
     * graph.
     * <p>
     * It is initially set to `null`.
     *
     * @see KnowledgeSatisfied
     * @see Statistic
     * @see HasKnowledge
     */
    private Knowledge knowledge = null;

    /**
     * Constructs the statistic.
     */
    public KnowledgeSatisfied() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "KnowledgeSatisfied";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "The knowledge provided is satisfied for the estimated graph.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return knowledge.isViolatedBy(estGraph) ? 0 : 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
