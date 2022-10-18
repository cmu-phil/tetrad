package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The bidirected edge precision.
 *
 * @author jdramsey
 */
public class BidirectedBothNonancestorAncestor implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BBNA";
    }

    @Override
    public String getDescription() {
        return "# X<->Y where neither X nor Y is an ancestor of the other in DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (!trueGraph.isAncestorOf(x, y) && !trueGraph.isAncestorOf(y, x)) {
                    count++;
                } else {
                    System.out.print("BBNA check: ");

                    if (trueGraph.isAncestorOf(x, y)) {
                        System.out.print("Ancestor(" + x + ", " + y + ")");
                    }

                    if (trueGraph.isAncestorOf(y, x)) {
                        System.out.print(" Ancestor(" + y + ", " + x + ")");
                    }

                    System.out.println();
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
