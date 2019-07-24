package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScorer;
import edu.cmu.tetrad.search.SearchGraphUtils;

import static java.lang.Math.tanh;

/**
 * Difference between the true and estiamted BIC scores.
 *
 * @author jdramsey
 */
public class BicDiff implements Statistic {
    static final long serialVersionUID = 23L;

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
        double _true = SemBicScorer.scoreDag(SearchGraphUtils.dagFromPattern(trueGraph), dataModel);
        double est = SemBicScorer.scoreDag(SearchGraphUtils.dagFromPattern(estGraph), dataModel);
        return (_true - est);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

