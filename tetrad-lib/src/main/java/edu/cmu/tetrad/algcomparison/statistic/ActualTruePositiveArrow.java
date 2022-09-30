package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class ActualTruePositiveArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ATPA";
    }

    @Override
    public String getDescription() {
        return "Actual True Positives for Arrow";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                if (!trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    tp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                if (!trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
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
