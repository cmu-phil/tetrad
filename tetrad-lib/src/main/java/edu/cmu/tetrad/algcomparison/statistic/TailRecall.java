package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TailConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Implements the TailRecall statistic, which calculates the tail recall value for a given true graph, estimated graph,
 * and data model.
 */
public class TailRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public TailRecall() {
    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "TR";
    }

    /**
     * Returns a short one-line description of this statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Tail recall";
    }

    /**
     * Calculates the tail recall value for a given true graph, estimated graph, and data model.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters.
     * @return The tail recall value.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        TailConfusion confusion = new TailConfusion(trueGraph, estGraph);
        double arrowsTp = confusion.getArrowsTp();
        double arrowsFn = confusion.getArrowsFn();
        double den = arrowsTp + arrowsFn;
        return arrowsTp / den;
    }

    /**
     * Returns the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
