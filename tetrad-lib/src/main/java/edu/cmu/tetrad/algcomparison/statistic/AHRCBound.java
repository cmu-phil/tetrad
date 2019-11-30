package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Set;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class AHRCBound implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AHRCBound";
    }

    @Override
    public String getDescription() {
        return "Bound for AHRC";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);

        Set<Edge> uti = confusion.getInvolvedUtFn();

        int numEdges = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (trueGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                numEdges++;
            }
        }


        Set<Edge> edges = confusion.getInvolvedUtFn();

        if (edges.isEmpty()) return 1;

        int wrong = 0;

        for (Edge edge : edges) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (trueGraph.isDirectedFromTo(a, b) != estGraph.isDirectedFromTo(a, b)) {
                wrong++;
            }
        }

//        return (numEdges - confusion.getTriangles().size()) / (double) numEdges;
        return (numEdges - wrong) / (double) numEdges;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
