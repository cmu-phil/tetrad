package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class TrueDagTruePositiveTails implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DTPT";
    }

    @Override
    public String getDescription() {
        return "True Positives for Tails compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                if (trueGraph.paths().isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    tp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.TAIL) {
                if (trueGraph.paths().isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    tp++;
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
