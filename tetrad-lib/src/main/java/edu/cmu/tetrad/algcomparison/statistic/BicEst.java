package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;
import edu.cmu.tetrad.search.score.SemBicScorer;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Estimated BIC score.
 *
 * @author josephramsey
 */
public class BicEst implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BicEst";
    }

    @Override
    public String getDescription() {
        return "BIC of the estimated CPDAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        double _true = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(trueGraph), dataModel);
        return SemBicScorer.scoreDag(GraphUtilsSearch.dagFromCPDAG(estGraph), dataModel);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

