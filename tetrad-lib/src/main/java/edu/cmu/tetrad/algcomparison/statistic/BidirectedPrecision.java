package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;

/**
 * The bidirected edge precision.
 *
 * @author josephramsey
 */
public class BidirectedPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PBP";
    }

    @Override
    public String getDescription() {
        return "Precision of bidirected edges compared to true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Edge edge2 = pag.getEdge(edge.getNode1(), edge.getNode2());

                if (edge2 != null && Edges.isBidirectedEdge(edge2)) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
