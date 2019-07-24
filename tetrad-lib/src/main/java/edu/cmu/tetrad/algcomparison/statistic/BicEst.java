package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScorer;
import edu.cmu.tetrad.search.SearchGraphUtils;

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
        return "BIC of the estimated pattern";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        double _true = SemBicScorer.scoreDag(SearchGraphUtils.dagFromPattern(trueGraph), dataModel);
        double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromPattern(estGraph), dataModel);
        return est;
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

