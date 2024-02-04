package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estiamted BIC scores.
 *
 * @author josephramsey
 */
public class BicDiffPerRecord implements Statistic {
    private static final long serialVersionUID = 23L;
    private boolean precomputeCovariances = true;

    @Override
    public String getAbbreviation() {
        return "BicDiffPerRecord";
    }

    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores, " +
                "divided by the sample size";
    }

    /**
     * Returns the difference between the true and estimated BIC scores, divided by the sample size.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The difference between the true and estimated BIC scores, divided by the sample size.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double _true = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
        double est = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        if (abs(_true) < 0.0001) _true = 0.0;
        if (abs(est) < 0.0001) est = 0.0;
        return (_true - est) / ((DataSet) dataModel).getNumRows();
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
     * Returns true if the covariances are precomputed.
     *
     * @param precomputeCovariances True if the covariances are precomputed.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

