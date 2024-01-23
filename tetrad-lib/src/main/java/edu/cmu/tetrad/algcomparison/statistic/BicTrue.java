package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.SemBicScorer;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * True BIC score. The BIC is calculated as 2L - k ln N, so "higher is better."
 *
 * @author josephramsey
 */
public class BicTrue implements Statistic {
    private static final long serialVersionUID = 23L;
    private boolean precomputeCovariances = true;

    @Override
    public String getAbbreviation() {
        return "BicTrue";
    }

    @Override
    public String getDescription() {
        return "BIC of the true model";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        //        double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(estGraph), dataModel);
        return SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(trueGraph, null), dataModel, precomputeCovariances);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value);
    }

    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

