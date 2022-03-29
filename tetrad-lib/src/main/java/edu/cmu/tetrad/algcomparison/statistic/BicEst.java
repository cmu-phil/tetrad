package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScorer;

import static java.lang.Math.tanh;

/**
 * Estimated BIC score.
 *
 * @author jdramsey
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
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
//        double _true = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(trueGraph), dataModel);
        final double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(estGraph), dataModel);
        return est;
    }

    @Override
    public double getNormValue(final double value) {
        return tanh(value / 1e6);
    }
}

