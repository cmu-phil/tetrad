package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

/**
 * Tests whether the p-values under the null distribution are distributed as Uniform, and if so, returns the proportion
 * of judgements of dependence under the Alternative Hypothesis. If the p-values are not distributed as Uniform, zero is
 * returned.
 *
 * @author josephramsey
 */
public class MarkovAdequacyScore implements Statistic {
    static final long serialVersionUID = 23L;
    private double alpha = 0.05;

    @Override
    public String getAbbreviation() {
        return "MAS";
    }

    @Override
    public String getDescription() {
        return "Markov Adequacy Score (depends only on the estimated DAG and the data)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, 0.01), MarkovCheck.ConditioningSetType.PARENTS);
        markovCheck.generateResults();
        return markovCheck.getMarkovAdequacyScore(alpha);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}
