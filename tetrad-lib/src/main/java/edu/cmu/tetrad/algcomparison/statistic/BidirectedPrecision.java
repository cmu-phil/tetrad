package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;

/**
 * The bidirected edge precision.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BidirectedPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "PBP";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Precision of bidirected edges compared to true PAG";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);
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

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
