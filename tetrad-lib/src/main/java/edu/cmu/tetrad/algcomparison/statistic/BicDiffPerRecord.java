package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;

import java.io.Serial;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estiamted BIC scores.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BicDiffPerRecord implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public BicDiffPerRecord() {

    }

    /**
     * Whether to precompute covariances.
     */
    private boolean precomputeCovariances = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "BicDiffPerRecord";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores, " +
                "divided by the sample size";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the difference between the true and estimated BIC scores, divided by the sample size.
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
     * {@inheritDoc}
     * <p>
     * Returns the normalized value of the statistic.
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

