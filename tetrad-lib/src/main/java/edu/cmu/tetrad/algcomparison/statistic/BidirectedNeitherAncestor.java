package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;

/**
 * The bidirected edge precision.
 *
 * @author jdramsey
 */
public class BidirectedNeitherAncestor implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BNA";
    }

    @Override
    public String getDescription() {
        return "Number of X<->Y where neither X nor Y is an ancestor of the other in the true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (!trueGraph.existsDirectedPathFromTo(x, y) && !trueGraph.existsDirectedPathFromTo(y, x)) {
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
