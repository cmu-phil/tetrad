package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

/**
 * Estimates whether the p-values under the null are Uniform usign the Markov Checker. This estimates whether the
 * p-value of the Kolmogorov-Smirnov test for distribution of p-values under the null using the Fisher Z test for the
 * local Markov check is uniform, so is only applicable to continuous data and really strictly only for Gaussian data.
 *
 * @author josephramsey
 */
public class PvalueSupremacyScore implements Statistic {
    static final long serialVersionUID = 23L;
    private double alpha = 0.01;

    @Override
    public String getAbbreviation() {
        return "PSS";
    }

    @Override
    public String getDescription() {
        return "P-value Supremacy Score";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, alpha));
        markovCheck.generateResults();
        boolean isUniform = markovCheck.getKsPValue(true) > alpha;

        if (isUniform) {
            return markovCheck.getFractionDependent(false);
        } else {
            return 0;
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}
