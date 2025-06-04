package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Checks whether a PAG is maximal.
 */
public class Maximal implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public Maximal() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "Maximal";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph is maximal, 0 if not";
    }

    /**
     * Checks whether a PAG is maximal.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        boolean maximal = estGraph.paths().isMaximal();

        if (!maximal) {
            throw new IllegalArgumentException("Maximality condition fails in Maximal statistic.");
        }

        return maximal ? 1.0 : 0.0;
    }

    /**
     * Returns the normalized value of the given statistic value.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic, between 0 and 1.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
