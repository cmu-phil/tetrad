package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * Represents a markov check statistic that calculates the Kolmogorov-Smirnoff P value for whether the p-values for the
 * estimated graph are distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class MarkovCheckKsPasses implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     */
    public MarkovCheckKsPasses() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-KSPass";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Kolmogorov-Smirnoff P Passes (1 = p > 0.05, 0 = p <= 0.05)";
    }

    /**
     * Calculates whether Kolmogorov-Smirnoff P > 0.05.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return 1 if p > 0.05, 0 if not.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double p = new MarkovCheckKolmogorovSmirnoffP().getValue(trueGraph, estGraph, dataModel);
        return p > 0.05 ? 1 : 0;
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
