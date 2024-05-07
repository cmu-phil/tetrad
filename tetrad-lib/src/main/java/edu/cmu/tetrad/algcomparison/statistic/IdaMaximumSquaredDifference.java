package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemIm;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * IdaMaximumSquaredDifference is a statistic that calculates the "IDA Average Maximum Squared Difference" between a
 * true graph and an estimated graph. It implements the Statistic interface.
 * <p>
 * This is the average of the maximum squared difference between the true and estimated total effects for each pair of
 * variables.
 */
public class IdaMaximumSquaredDifference implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The true SEM IM. This stat can only be used if the true SEM IM is known.
     */
    private final SemIm semIm;

    /**
     * Initializes a new instance of the {@code IdaMaximumSquaredDifference} class.
     *
     * @param semIm The true SEM IM.
     */
    public IdaMaximumSquaredDifference(SemIm semIm) {
        this.semIm = semIm;
    }

    /**
     * Retrieves the abbreviation for the statistic. This abbreviation will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "AMaxSD";
    }

    /**
     * Retrieves the description for this statistic.
     *
     * @return The description for this statistic.
     */
    @Override
    public String getDescription() {
        return "IDA Average Maximum Squared Difference";
    }

    /**
     * Calculates the value of the statistic "IDA Average Maximum Squared Difference".
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        IdaCheck idaCheck = new IdaCheck(trueGraph, (DataSet) dataModel, semIm);
        return idaCheck.getAvgMaxSquaredDiffEstTrue(idaCheck.getOrderedPairs());
    }

    /**
     * Returns a normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return FastMath.tanh(value);
    }
}
