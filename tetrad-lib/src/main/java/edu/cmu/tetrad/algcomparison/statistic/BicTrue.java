package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

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
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search
                .SemBicScore((DataSet) dataModel)).scoreDag(trueGraph);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value);
    }
}

