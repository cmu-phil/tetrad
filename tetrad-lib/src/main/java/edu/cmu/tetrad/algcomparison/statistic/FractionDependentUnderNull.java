package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
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
    private static final long serialVersionUID = 23L;
    private double alpha = 0.01;

    public FractionDependentUnderNull() {
    }

    public FractionDependentUnderNull(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public String getAbbreviation() {
        return "DN";
    }

    @Override
    public String getDescription() {
        return "Fraction Dependent Under the Null (depends only on the estimated DAG and the data)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, alpha), ConditioningSetType.LOCAL_MARKOV);
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
