package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

/**
 * Number of Xo->Y in est.
 *
 * @author josephramsey
 */
public class NumPartiallyOrientedEdges implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#Xo->Y";
    }

    @Override
    public String getDescription() {
        return "Number of Xo->Y in est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isPartiallyOrientedEdge(edge)) {
                tp++;
            }
        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
