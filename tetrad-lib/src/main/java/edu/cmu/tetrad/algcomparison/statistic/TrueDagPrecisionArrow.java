package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagPrecisionArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DAHP";
    }

    @Override
    public String getDescription() {
        return "Proportion of X*->Y in the estimated graph for which there is no path Y->...->X in the true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                if (!trueGraph.existsDirectedPathFromTo(edge.getNode1(), edge.getNode2())) {
                    tp++;
                } else {
                    fp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                if (!trueGraph.existsDirectedPathFromTo(edge.getNode2(), edge.getNode1())) {
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
