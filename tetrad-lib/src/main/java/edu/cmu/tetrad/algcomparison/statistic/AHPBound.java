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

        int V = estGraph.getNumNodes();

        System.out.println("HISTOGRAM " + Arrays.toString(hist));

        int Ec = 0;

        for (Edge edge : estGraph.getEdges()) {
//            if (Edges.isDirectedEdge(edge)) {
                Ec++;
//            }
        }

        int Et = trueGraph.getNumEdges();
        int Ee = estGraph.getNumEdges();

        double aT = 2 * Et / (double) V;
        double aE = 2 * Ee / (double) V;
        double aC = 2 * Ec / (double) V;

        return 1.0 - aE / (double) (V - 1);

    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
