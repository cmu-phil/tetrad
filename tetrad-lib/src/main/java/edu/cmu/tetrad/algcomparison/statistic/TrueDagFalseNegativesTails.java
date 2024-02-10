package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TrueDagFalseNegativesTails implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "DFNT";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "False Negatives for Tails compared to true DAG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        int tp = 0;
        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().isAncestorOf(x, y)) {
                    Edge e = estGraph.getEdge(x, y);

                    if (e != null && e.getProximalEndpoint(x) != Endpoint.TAIL) {
                        fn++;
                    }
                }
            }
        }

//        for (Edge edge : estGraph.getEdges()) {
//            if (edge.getEndpoint1() == Endpoint.TAIL) {
//                if (trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())) {
//                    tp++;
//                }
//            }
//
//            if (edge.getEndpoint2() == Endpoint.TAIL) {
//                if (trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
//                    tp++;
//                }
//            }
//        }

        return fn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
