package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estiamted BIC scores.  The BIC is calculated as 2L - k ln N, so "higher is better."
 *
 * @author josephramsey
 */
public class BicDiff implements Statistic {
    private static final long serialVersionUID = 23L;
    private boolean precomputeCovariances = true;

    /**
     * Returns the name of the statistic.
     *
     * @return the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "BicDiff";
    }

    /**
     * Returns the description of the statistic.
     *
     * @return the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores";
    }

    /**
     * Returns the value of the statistic.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double _true = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
        double est = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        return (_true - est);
    }

    /**
     * Returns the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }

    /**
     * Returns the precompute covariances flag.
     *
     * @param precomputeCovariances The precompute covariances flag.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

