package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Arrays;
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
        trueGraph = SearchGraphUtils.patternForDag(trueGraph);
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);

        int uti = confusion.getInvolvedUtFp().size();

        int numEdges = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (trueGraph.isAdjacentTo(edge.getNode1(), edge.getNode2()) && Edges.isDirectedEdge(edge)) {
                numEdges++;
            }
        }

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        Set<Edge> edges = confusion.getInvolvedUtFp();

        if (edges.isEmpty()) return 1;

        int wrong = 0;

        for (Edge edge : edges) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (trueGraph.isDirectedFromTo(a, b) != estGraph.isDirectedFromTo(a, b)) {
                wrong++;
            }
        }

        double sumDegree = 0;

        for (Node node : estGraph.getNodes()) {
            sumDegree += estGraph.getDegree(node);
        }

        int VV = 0;

        for (Node node : estGraph.getNodes()) {
            if (estGraph.getDegree(node) >= 5) {
                VV++;
            }
        }

        int[] hist = new int[30];

        for (Node node : estGraph.getNodes()) {
            hist[estGraph.getDegree(node)]++;
        }

        int V = trueGraph.getNumNodes();

        System.out.println("HISTOGRAM " + Arrays.toString(hist));

        int E = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                E++;
            }
        }

        double aT = new AvgDegreeTrueGraph().getValue(trueGraph, estGraph, dataModel);
        double aE = new AvgDegreeEstGraph().getValue(trueGraph, estGraph, dataModel);
//        double aE = 2 * E / (double) (V);// new AvgDegreeEstGraph().getValue(trueGraph, estGraph, dataModel);

        double ar = new AdjacencyRecall().getValue(trueGraph, estGraph, dataModel);

//        return 1.0 - (aT) / (double) (V - 1);
//        return 1.0 - (aE) / ((double) (V - 1) * 0.8);

        return 1.0 - aT / (double) (V - 1);

    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
