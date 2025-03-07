package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Represents a markov check statistic that calculates the Kolmogorov-Smirnoff P value for whether the p-values for the
 * estimated graph are distributed as U(0, 1). This version reports the best p-value out of 10 repetitions.
 *
 * @author josephramsey
 */
public class MarkovCheckKolmogorovSmirnoffPBestOf10 implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    private final IndependenceWrapper independenceWrapper;
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     */
    public MarkovCheckKolmogorovSmirnoffPBestOf10(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
        this.independenceWrapper = independenceWrapper;
        this.conditioningSetType = conditioningSetType;
    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-KSP10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Kolmogorov-Smirnoff P; best of 10 reps";
    }

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The Kolmogorov-Smirnoff P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        IndependenceTest test = independenceWrapper.getTest(dataModel, parameters);

        // Find the best of 11 repetitions
        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 11; i++) {
            MarkovCheck markovCheck = new MarkovCheck(estGraph, test, conditioningSetType);
            markovCheck.generateResults(true, true);
            double ksPValue = markovCheck.getKsPValue(true);
            if (ksPValue > max) {
                max = ksPValue;
            }
        }

        return max;
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
