package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * TrueDagFalsePositiveTails is a class that implements the Statistic interface. It calculates the number of false positives
 * for tails in the estimated graph compared to the true DAG.
 */
public class TrueDagFalsePositiveTails implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new TrueDagFalsePositiveTails object.
     */
    public TrueDagFalsePositiveTails() {
    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "DFPT";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "False Positives for Tails compared to true DAG";
    }

    /**
     * Calculates the number of false positives for tails in the estimated graph compared to the true DAG.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The number of false positives for tails.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                if (!trueGraph.paths().isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    fp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.TAIL) {
                if (!trueGraph.paths().isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    fp++;
                }
            }
        }

        return fp;
    }

    /**
     * Calculates the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
