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

    @Override
    public String getAbbreviation() {
        return "BicDiff";
    }

    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double _true = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
        double est = SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        return (_true - est);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }

    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

