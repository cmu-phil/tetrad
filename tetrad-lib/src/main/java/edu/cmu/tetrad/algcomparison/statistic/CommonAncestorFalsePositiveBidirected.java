package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.algcomparison.statistic.CommonAncestorTruePositiveBidirected.existsCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class CommonAncestorFalsePositiveBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CAFPB";
    }

    @Override
    public String getDescription() {
        return "Common Ancestor False Positive Bidirected";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (!existsCommonAncestor(trueGraph, edge)) fp++;
            }
        }

        return fp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
