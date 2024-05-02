package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.Serial;

/**
 * Counts the number of X<->Y edges for which a latent confounder of X and Y exists.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCorrectBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Counts the number of bidirectional edges for which a latent confounder of X and Y exists.
     */
    public NumCorrectBidirected() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "<-> Correct";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistics as a String.
     */
    @Override
    public String getDescription() {
        return "Number of bidirected edges for which a latent confounder exists";
    }

    /**
     * Returns the number of bidirected edges for which a latent confounder exists.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The number of bidirected edges with a latent confounder.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (GraphUtils.isCorrectBidirectedEdge(edge, trueGraph)) {
                    tp++;
                }
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of the given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
