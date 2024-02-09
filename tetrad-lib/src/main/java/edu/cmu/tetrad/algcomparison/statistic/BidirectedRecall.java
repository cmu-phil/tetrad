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
public class BidirectedRecall implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "PBR";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Recall of bidirected edges compared to the true PAG";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);
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

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
