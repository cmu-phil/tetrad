package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

/**
 * Estimates whether the p-values under the null are Uniform usign the Markov Checker. This estimate the fraction of
 * dependent judgements from the local Fraithfulness check, under the alternative hypothesis of dependence. This is only
 * applicable to continuous data and really strictly only for Gaussian data.
 *
 * @author josephramsey
 */
public class FractionDependentUnderNull implements Statistic {
    static final long serialVersionUID = 23L;
    private double alpha = 0.01;

    @Override
    public String getAbbreviation() {
        return "FDN";
    }

    @Override
    public String getDescription() {
        return "Fraction Dependent Under the Null";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, alpha));
        markovCheck.generateResults();
        return markovCheck.getFractionDependent(true);
    }

    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}
