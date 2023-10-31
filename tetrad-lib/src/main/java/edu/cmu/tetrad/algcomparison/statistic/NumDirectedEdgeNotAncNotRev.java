package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumDirectedEdgeNotAncNotRev implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X->Y,!Anc!Rev";
    }

    @Override
    public String getDescription() {
        return "Number X-->Y for which for which both not X~~>Y and not Y~~>X in true (should be X<->Y or Xo->Y or X<-oY)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (!trueGraph.paths().isAncestorOf(x, y) && !trueGraph.paths().isAncestorOf(y, x)) {
                    tp++;
//                    System.out.println("Should be " + x + "<~->" + y + ": " + estGraph.getEdge(x, y));
                }
            }
        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
