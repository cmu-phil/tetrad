package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;

/**
 * Represents a markov check statistic that calculates the Kolmogorov-Smirnoff P value for whether the p-values for the
 * estimated graph are distributed as U(0, 1). This version reports whether the p-value is greater than 0.05 and
 * reports the best of 10 repetitions.
 *
 * @author josephramsey
 */
public class MarkovCheckKsPassesBestOf10 implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * An instance of {@link IndependenceWrapper} used to encapsulate and perform independence
     * tests on the dataset with specific configurations. This variable is intended to facilitate
     * independence testing within the context of Markov check calculations and other statistical
     * operations in the enclosing class.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Represents the type of conditioning set used during the Markov checks in the context of statistical dependence
     * testing. It specifies how variables are conditioned upon during independence tests and directly influences the
     * results of the Markov checks. The type is defined by the {@link ConditioningSetType} enumeration, which includes
     * various strategies for defining the conditioning set, such as GLOBAL_MARKOV, LOCAL_MARKOV, or MARKOV_BLANKET among
     * others. This field is immutable and must be set during the initialization of the object.
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
    public MarkovCheckKsPassesBestOf10(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
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
        return "MC-KSPass10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Kolmogorov-Smirnoff P Passes (1 = p > 0.05, 0 = p <= 0.05); best of 10 repetitions.";
    }

    /**
     * Calculates whether Kolmogorov-Smirnoff P > 0.05.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters  The parameters.
     * @return 1 if p > 0.05, 0 if not.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double p = new MarkovCheckKolmogorovSmirnoffPBestOf10(independenceWrapper, conditioningSetType).getValue(trueGraph, estGraph, dataModel, new Parameters());
        return p > parameters.getDouble(Params.MC_ALPHA) ? 1.0 : 0.0;
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
