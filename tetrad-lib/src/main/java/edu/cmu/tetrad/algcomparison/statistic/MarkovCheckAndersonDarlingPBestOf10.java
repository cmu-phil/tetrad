package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
 * distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class MarkovCheckAndersonDarlingPBestOf10 implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * An instance of the IndependenceWrapper interface, which provides methods to assess statistical independence
     * relationships in datasets. This wrapper is used in conjunction with various tests to evaluate dependencies
     * and supports the execution of algorithms that rely on independence test results. Its specific implementation
     * determines the behavior of the test and the parameters required for its execution.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Specifies the type of conditioning set used for Markov checks in statistical tests. This variable determines
     * how the independence tests are conducted, influencing the set of variables considered to condition on when
     * evaluating independence relationships in a graph.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param independenceWrapper An instance of {@link IndependenceWrapper} used to encapsulate and perform
     *                            independence tests on the dataset with specific configurations.
     * @param conditioningSetType The type of conditioning set employed during Markov checks, represented by the
     *                            {@link ConditioningSetType} enum; this dictates how variables are conditioned in
     *                            independence tests.
     */
    public MarkovCheckAndersonDarlingPBestOf10(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
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
        return "MC-ADP10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Anderson Darling P, best of 10 reps";
    }

    /**
     * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The Anderson Darling P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        IndependenceTest test = independenceWrapper.getTest(dataModel, parameters);
        MarkovCheck markovCheck = new MarkovCheck(estGraph, test, conditioningSetType);

        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 10; i++) {
            markovCheck.generateResults(true, true);
            double adp = markovCheck.getAndersonDarlingP(true);
            if (adp > max) {
                max = adp;
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
