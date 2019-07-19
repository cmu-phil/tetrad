package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;

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
        Graph g = SearchGraphUtils.dagFromPattern(estGraph);
        double est = new Fges(new SemBicScore((DataSet) dataModel)).scoreDag(g);
        double _true = new Fges(new SemBicScore((DataSet) dataModel)).scoreDag(trueGraph);
        return _true - est;
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

