package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

import java.io.Serial;

/**
 * Estimates whether the p-values under the null are Uniform usign the Markov Checker. This estimate the fraction of
 * dependent judgements from the local Fraithfulness check, under the alternative hypothesis of dependence. This is only
 * applicable to continuous data and really strictly only for Gaussian data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FractionDependentUnderNull implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The significance level.
     */
    private double alpha = 0.01;

    /**
     * <p>Constructor for FractionDependentUnderNull.</p>
     */
    public FractionDependentUnderNull() {
    }

    /**
     * <p>Constructor for FractionDependentUnderNull.</p>
     *
     * @param alpha a double
     */
    public FractionDependentUnderNull(double alpha) {
        this.alpha = alpha;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "DN";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Fraction Dependent Under the Null (depends only on the estimated DAG and the data)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        MarkovCheck markovCheck = new MarkovCheck(estGraph, new IndTestFisherZ((DataSet) dataModel, alpha), ConditioningSetType.LOCAL_MARKOV);
        markovCheck.generateResults(true, true);
        return markovCheck.getFractionDependent(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }

    /**
     * <p>Setter for the field <code>alpha</code>.</p>
     *
     * @param alpha a double
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}
