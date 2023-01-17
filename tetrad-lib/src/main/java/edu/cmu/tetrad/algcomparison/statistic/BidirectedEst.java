package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class BidirectedEst implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X<->Y";
    }

    @Override
    public String getDescription() {
        return "Number of True Bidirected Edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int e = 0;

        for (Edge edge : estGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x == y) continue;

            if (Edges.isBidirectedEdge(edge)) e++;
        }

        return e;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
