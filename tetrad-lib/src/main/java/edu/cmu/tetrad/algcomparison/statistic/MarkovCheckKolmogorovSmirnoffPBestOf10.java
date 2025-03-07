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
    /**
     * An instance of the IndependenceWrapper interface used for conducting independence tests. This variable is
     * critical for determining whether variables in a dataset are independent based on specified conditions. It enables
     * the evaluation of statistical independence necessary for assessing the structure of causal graphs and for the
     * execution of Markov-related validation checks.
     * <p>
     * IndependenceWrapper provides methods for obtaining independence tests, retrieving test descriptions, handling
     * parameter requirements, and verifying the compatibility of data types used for independence assessments. This
     * interface serves as a foundational component in algorithms implementing statistical structure learning and
     * evaluation.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Specifies the type of conditioning set to be used for the Markov check in calculations. The conditioning set
     * determines how independence facts are tested and is defined by the {@link ConditioningSetType} enum.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     *
     * @param independenceWrapper An instance of {@link IndependenceWrapper} used to encapsulate and perform
     *                            independence tests on the dataset with specific configurations.
     * @param conditioningSetType The type of conditioning set employed during Markov checks, represented by the
     *                            {@link ConditioningSetType} enum; this dictates how variables are conditioned in
     *                            independence tests.
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
