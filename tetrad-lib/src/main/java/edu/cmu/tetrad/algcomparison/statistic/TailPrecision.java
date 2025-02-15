package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TailConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * TailPrecision is a class that implements the Statistic interface. It calculates the tail precision, which is the
 * ratio of true positive arrows to the sum of true positive arrows and false positive arrows.
 */
public class TailPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public TailPrecision() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "TP";
    }

    /**
     * Returns a short one-line description of this statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Tail precision";
    }

    /**
     * Calculates the tail precision, which is the ratio of true positive arrows to the sum of true positive arrows and
     * false positive arrows.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters
     * @return The calculated tail precision value.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        TailConfusion adjConfusion = new TailConfusion(trueGraph, estGraph);
        double arrowsTp = adjConfusion.getArrowsTp();
        double arrowsFp = adjConfusion.getArrowsFp();
        return arrowsTp / (arrowsTp + arrowsFp);
    }

    /**
     * Retrieves the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
