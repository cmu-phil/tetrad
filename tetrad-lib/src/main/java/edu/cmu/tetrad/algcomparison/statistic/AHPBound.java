package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class AHPBound implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AHPBound";
    }

    @Override
    public String getDescription() {
        return "Bound for AHP";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);

        double fplb = confusion.getInvolvedUtFp() / 2.0;

//        Set<Edge> adjTrue = new HashSet<>();
//
//        for (Edge edge : trueGraph.getEdges()) {
//            adjTrue.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
//        }
//
        Set<Edge> adjEst = new HashSet<>();
//
        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                adjEst.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
            }
        }
//
//        Set<Edge> edges = adjTrue;
//        edges.retainAll(adjEst);



        double tpub = adjEst.size() - fplb;

        return tpub / adjEst.size();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
