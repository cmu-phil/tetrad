package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class NumBidirectedEdges implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BID";
    }

    @Override
    public String getDescription() {
        return "Percent Bidirected Edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        int numBidirected = 0;
        int numTotal = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                numBidirected++;
            }

            numTotal++;
        }

        return numBidirected;
    }

    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }
}
