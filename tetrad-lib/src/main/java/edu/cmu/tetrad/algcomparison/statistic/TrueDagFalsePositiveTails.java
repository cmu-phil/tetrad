package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagFalsePositiveTails implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DFPT";
    }

    @Override
    public String getDescription() {
        return "False Positives for Tails compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                if (!trueGraph.getPaths().isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    fp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.TAIL) {
                if (!trueGraph.getPaths().isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    fp++;
                }
            }
        }

        return fp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
