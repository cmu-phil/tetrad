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
 * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
 * distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class MarkovCheckFractionDependentH1 implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Encapsulates an independence test and provides methods for accessing it based on the given data model and
     * parameters. This wrapper abstracts different types of independence tests, making them accessible through a
     * unified interface.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Represents the type of conditioning set used in the Markov check. This variable determines the strategy for
     * selecting the set of variables to condition on when performing independence tests. The choice of conditioning set
     * type impacts the interpretation of the independence or dependence facts discovered during the analysis.
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
    public MarkovCheckFractionDependentH1(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
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
        return "MC-f_dep_H1";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Fraction Dependent H1";
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

        markovCheck.generateResults(false, true);
        return markovCheck.getFractionDependent(false);
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
