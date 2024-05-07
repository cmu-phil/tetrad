package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;
import java.util.List;

/**
 * The BidirectedLatentPrecision class implements the Statistic interface and represents a statistic that calculates
 * the percentage of bidirected edges in an estimated graph for which a latent confounder exists in the true graph.
 */
public class BidirectedLatentPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BidirectedLatentPrecision class implements the Statistic interface and represents a statistic that calculates
     * the percentage of bidirected edges in an estimated graph for which a latent confounder exists in the true graph.
     */
    public BidirectedLatentPrecision() {
    }

    /**
     * Returns the abbreviation for the statistic. The abbreviation is a short string that represents the statistic.
     * For this statistic, the abbreviation is "&lt;-&gt;-Lat-Prec".
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "<->-Lat-Prec";
    }

    /**
     * Returns a short description of the statistic, which is the percentage of bidirected edges for which a latent confounder exists.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Percent of bidirected edges for which a latent confounder exists";
    }

    /**
     * Calculates the percentage of correctly identified bidirected edges in an estimated graph
     * for which a latent confounder exists in the true graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The percentage of correctly identified bidirected edges.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int pos = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (GraphUtils.isCorrectBidirectedEdge(edge, trueGraph)) {
                    tp++;
                }

                pos++;
            }
        }

        return tp / (double) pos;
    }

    /**
     * Calculates the normalized value of a given statistic value.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
