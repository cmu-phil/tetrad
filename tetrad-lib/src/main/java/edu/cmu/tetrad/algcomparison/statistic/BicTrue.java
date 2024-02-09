package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;

import java.io.Serial;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * True BIC score. The BIC is calculated as 2L - k ln N, so "higher is better."
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BicTrue implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;
    private boolean precomputeCovariances = true;

    /**
     * {@inheritDoc}
     *
     * No-arg constructor. Used for reflection; do not delete.
     */
    @Override
    public String getAbbreviation() {
        return "BicTrue";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "BIC of the true model";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        //        double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(estGraph), dataModel);
        return SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value);
    }

    /**
     * Returns whether to precompute covariances.
     *
     * @param precomputeCovariances whether to precompute covariances.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

