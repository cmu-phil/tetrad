package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.score.SemBicScorer;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * True BIC score.
 *
 * @author josephramsey
 */
public class BicTrue implements Statistic {
    static final long serialVersionUID = 23L;

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
        return SemBicScorer.scoreDag(GraphSearchUtils.dagFromCPDAG(trueGraph), dataModel);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value);
    }
}

