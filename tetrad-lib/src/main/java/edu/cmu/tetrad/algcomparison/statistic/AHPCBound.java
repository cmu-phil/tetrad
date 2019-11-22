package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.HashSet;
import java.util.Set;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class AHPCBound implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AHPCBound";
    }

    @Override
    public String getDescription() {
        return "Bound for AHPC";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);

        int uti = confusion.getInvolvedUtFp();

        int numEdges = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (trueGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {// && Edges.isDirectedEdge(edge)) {
                numEdges++;
            }
        }

        return (numEdges - uti / 2.0) / numEdges;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
