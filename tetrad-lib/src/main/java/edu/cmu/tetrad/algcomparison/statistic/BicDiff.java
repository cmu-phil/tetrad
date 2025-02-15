package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estimated BIC scores.  The BIC is calculated as 2L - k ln N, so "higher is better."
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BicDiff implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Whether to precompute covariances.
     */
    private boolean precomputeCovariances = true;

    /**
     * Constructs a new instance of the statistic.
     */
    public BicDiff() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "BicDiff";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double _true = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
        double est = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        return (_true - est);
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
     * Returns the precompute covariances flag.
     *
     * @param precomputeCovariances The precompute covariances flag.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

