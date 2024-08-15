package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
 * distributed as U(0, 1). This version uses the best of 10 repetitions.
 *
 * @author josephramsey
 */
public class MarkovCheckAdPassesBestOf10 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     */
    public MarkovCheckAdPassesBestOf10() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-ADPass10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Anderson Darling P Passes (1 = p > 0.05, 0 = p <= 0.05); best of 10 repetitions.";
    }

    /**
     * Calculates the Anderson Darling p-value > 0.05.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return 1 if p > 0.05, 0 if not.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double p = new MarkovCheckAndersonDarlingPBestOf10().getValue(trueGraph, estGraph, dataModel);
        return p > 0.05 ? 1.0 : 0.0;
    }

    /**
     * Calculates the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
