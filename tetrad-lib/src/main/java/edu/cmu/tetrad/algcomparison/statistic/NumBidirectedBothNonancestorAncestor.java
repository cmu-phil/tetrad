package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The bidirected edge precision.
 *
 * @author josephramsey
 */
public class NumBidirectedBothNonancestorAncestor implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#<->,!Anc!Rev";
    }

    @Override
    public String getDescription() {
        return "# X<->Y for which both not X~~>Y and not Y~~>X";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (!trueGraph.paths().isAncestorOf(x, y) && !trueGraph.paths().isAncestorOf(y, x)) {
                    count++;
                }
            }
        }

        return count;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
