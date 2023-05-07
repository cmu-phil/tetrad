package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.search.utils.GraphUtilsSearch.dagToPag;

/**
 * The bidirected edge precision.
 *
 * @author josephramsey
 */
public class BidirectedRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PBR";
    }

    @Override
    public String getDescription() {
        return "Recall of bidirected edges compared to the true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);
        int tp = 0;
        int fn = 0;

        for (Edge edge : pag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Edge edge2 = estGraph.getEdge(edge.getNode1(), edge.getNode2());

                if (edge2 != null && Edges.isBidirectedEdge(edge2)) {
                    tp++;
                } else {
                    fn++;
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
