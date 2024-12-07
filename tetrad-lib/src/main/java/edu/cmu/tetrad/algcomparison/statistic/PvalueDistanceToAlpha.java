package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

import java.io.Serial;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * Estimates whether the p-values under the null are Uniform usign the Markov Checker. This estimates whether the
 * p-value of the Kolmogorov-Smirnov test for distribution of p-values under the null using the Fisher Z test for the
 * local Markov check is uniform, so is only applicable to continuous data and really strictly only for Gaussian data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PvalueDistanceToAlpha implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The significance level for the independence tests.
     */
    private double alpha = 0.01;

    /**
     * <p>Constructor for PvalueDistanceToAlpha.</p>
     *
     * @param alpha a double
     */
    public PvalueDistanceToAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "DistAlpha";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "P-value Distance for Alpha";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, alpha), ConditioningSetType.LOCAL_MARKOV);
        markovCheck.generateResults(true, true);
        return abs(alpha - markovCheck.getKsPValue(true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
