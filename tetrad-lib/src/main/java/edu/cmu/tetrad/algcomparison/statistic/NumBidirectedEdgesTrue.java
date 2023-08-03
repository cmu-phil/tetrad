package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 */
public class NumBidirectedEdgesTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BIDT";
    }

    @Override
    public String getDescription() {
        return "Num Bidirected Edges in True";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int numBidirected = 0;

        for (Edge edge : trueGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                numBidirected++;
            }

        }

        return numBidirected;
    }

    @Override
    public double getNormValue(double value) {
        return 1.0 - value;
    }
}
