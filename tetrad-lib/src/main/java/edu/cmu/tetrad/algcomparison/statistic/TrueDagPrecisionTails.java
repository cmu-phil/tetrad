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
 * @author jdramsey
 */
public class TrueDagPrecisionTails implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DTP";
    }

    @Override
    public String getDescription() {
        return "Precision for Tails (DTPT / (DTPT + DFPT) compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                if (trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    tp++;
                } else {
                    fp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.TAIL) {
                if (trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return tp / (double) (tp + fp);
    }


    @Override
    public double getNormValue(double value) {
        return value;
    }
}
