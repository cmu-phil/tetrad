package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScorer;

import static java.lang.Math.tanh;

/**
 * True BIC score.
 *
 * @author jdramsey
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
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final double _true = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(trueGraph), dataModel);
//        double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromCPDAG(estGraph), dataModel);
        return _true;
    }

    @Override
    public double getNormValue(final double value) {
        return tanh(value);
    }
}

