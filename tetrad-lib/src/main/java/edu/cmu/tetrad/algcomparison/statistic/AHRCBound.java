package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

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

        int uti = confusion.getInvolvedUtFn();

        int numEdges = 0;

        for (Edge edge : estGraph.getEdges()) {
//            if (trueGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                numEdges++;
//            }
        }

//        return (numEdges - confusion.getTriangles().size()) / (double) numEdges;
        return (numEdges - uti / 2.) / (double) numEdges;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
