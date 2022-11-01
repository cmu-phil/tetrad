package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NumPossiblyDirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X-->Y-PossDir";
    }

    @Override
    public String getDescription() {
        return "Number of X-->Y in est where semi(X, Y) && !anc(X, Y) in true CPDAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        Graph cpdag = SearchGraphUtils.cpdagForDag(trueGraph);

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (GraphUtils.existsSemiDirectedPath(x, y, cpdag)) {
                    if (!GraphUtils.existsDirectedPathFromTo(x, y, cpdag)) {
                        count++;
                    }
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
