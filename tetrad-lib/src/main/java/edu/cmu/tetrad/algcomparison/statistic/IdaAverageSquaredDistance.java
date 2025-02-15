package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * The IDA average squared distance. This stat can only be used if the true SEM IM is known.
 * <p>
 * This is the average of the squared distance between the true and estimated total effects for each pair of variables.
 */
public class IdaAverageSquaredDistance implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The true SEM IM. This stat can only be used if the true SEM IM is known.
     */
    private final SemIm semIm;

    /**
     * The IDA Average Squared Distance.
     * <p>
     * The IDA Average Squared Distance is a statistic that measures the average squared distance between the true and
     * estimated total effects for each pair of variables in a Structural Equation Model (SEM).
     *
     * @param semIm The true SEM IM. This statistic can only be used if the true SEM IM is known.
     */
    public IdaAverageSquaredDistance(SemIm semIm) {
        this.semIm = semIm;
    }

    /**
     * Retrieves the abbreviation for the statistic. This abbreviation will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "ASD";
    }

    /**
     * Retrieves the description for this statistic.
     *
     * @return The description for this statistic.
     */
    @Override
    public String getDescription() {
        return "IDA Average Squared Distance";
    }

    /**
     * Calculates the value of the IDA Average Squared Distance statistic. Assumes the true SEM IM has been passed in
     * through the constructor.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters
     * @return The calculated value of the IDA Average Squared Distance statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        if (!estGraph.paths().isLegalMpdag()) {
            return Double.NaN;
        }

        IdaCheck idaCheck = new IdaCheck(trueGraph, (DataSet) dataModel, semIm);
        return idaCheck.getAverageSquaredDistance(idaCheck.getOrderedPairs());
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
