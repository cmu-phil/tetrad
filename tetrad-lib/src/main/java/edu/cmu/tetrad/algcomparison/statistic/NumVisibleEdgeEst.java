package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;

/**
 * NumVisibleEdgeEst is a class that implements the Statistic interface. It calculates the number of X-->Y edges that
 * are visible in the estimated PAG.
 */
public class NumVisibleEdgeEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumVisibleEdgeEst() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "#X->Y visible (E)";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Number of X-->Y for which X-->Y visible in estimated PAG";
    }

    /**
     * Returns the number of X-->Y edges that are visible in the estimated PAG.
     *
     * @param trueGraph  The true graph.
     * @param estGraph   The estimated graph.
     * @param dataModel  The data model.
     * @param parameters
     * @return The number of X-->Y edges that are visible in the estimated PAG.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        GraphUtils.addEdgeSpecializationMarkup(estGraph);

        for (Edge edge : new ArrayList<>(estGraph.getEdges())) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                tp++;
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of the given value.
     *
     * @param value The value to be normalized.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return FastMath.tan(value);
    }
}
