package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.TextTable;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.sort;

/**
 * Gives the comparison of a target graph to a reference graph that is implemented in the interface. Three methods are
 * given, one to return the edgewise comparison, one to return the stats list comparison, and one to return the
 * misclassification comparison. Each returns a String, which can be printed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CompareTwoGraphs {

    /**
     * No constructor for utility class.
     */
    private CompareTwoGraphs() {
    }

    /**
     * Returns an edgewise comparison of two graphs. This says, edge by edge, what the differences and similarities are
     * between the two graphs.
     *
     * @param trueGraph   The true graph.
     * @param targetGraph The target graph.
     * @return The comparison string.
     */
    @NotNull
    public static String getEdgewiseComparisonString(Graph trueGraph, Graph targetGraph) {
        StringBuilder builder = new StringBuilder();
        GraphUtils.GraphComparison comparison = GraphSearchUtils.getGraphComparison(trueGraph, targetGraph);

        List<Edge> edgesAdded = comparison.getEdgesAdded();
        List<Edge> edgesAdded2 = new ArrayList<>();

        for (Edge e1 : edgesAdded) {
            Node n1 = e1.getNode1();
            Node n2 = e1.getNode2();

            boolean twoCycle1 = trueGraph.getDirectedEdge(n1, n2) != null && trueGraph.getDirectedEdge(n2, n1) != null;
            boolean twoCycle2 = targetGraph.getDirectedEdge(n1, n2) != null && targetGraph.getDirectedEdge(n2, n1) != null;

            if (!(twoCycle1 || twoCycle2)) {
                edgesAdded2.add(e1);
            }
        }

        sort(edgesAdded2);

        builder.append("\nAdjacencies added (not involving 2-cycles and not reoriented):");

        if (edgesAdded2.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesAdded2.size(); i++) {
                Edge _edge = edgesAdded2.get(i);

                builder.append("\n").append(i + 1).append(". ").append(_edge.toString());
            }
        }

        builder.append("\n\nAdjacencies removed:");
        List<Edge> edgesRemoved = comparison.getEdgesRemoved();
        sort(edgesRemoved);

        if (edgesRemoved.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesRemoved.size(); i++) {
                Edge edge = edgesRemoved.get(i);
                builder.append("\n").append(i + 1).append(". ").append(edge);
            }
        }

        List<Edge> edges1 = new ArrayList<>(trueGraph.getEdges());

        List<Edge> twoCycles = new ArrayList<>();
        List<Edge> allSingleEdges = new ArrayList<>();

        for (Edge edge : edges1) {
            if (edge.isDirected() && targetGraph.containsEdge(edge) && targetGraph.containsEdge(edge.reverse())) {
                twoCycles.add(edge);
            } else if (trueGraph.containsEdge(edge)) {
                allSingleEdges.add(edge);
            }
        }

        builder.append("""


                Two-cycles in true correctly adjacent in estimated""");

        sort(allSingleEdges);

        if (twoCycles.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < twoCycles.size(); i++) {
                Edge adj = edges1.get(i);
                builder.append("\n").append(i + 1).append(". ").append(adj).append(" ").append(adj.reverse())
                        .append(" ====> ").append(trueGraph.getEdge(twoCycles.get(i).getNode1(), twoCycles.get(i).getNode2()));
            }
        }

        List<Edge> incorrect = new ArrayList<>();

        for (Edge adj : allSingleEdges) {
            Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
            Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());

            if (!edge1.equals(edge2)) {
                incorrect.add(adj);
            }
        }

        {
            builder.append("""


                    Edges incorrectly oriented""");

            if (incorrect.isEmpty()) {
                builder.append("\n  --NONE--");
            } else {
                int j1 = 0;
                sort(incorrect);

                for (Edge adj : incorrect) {
                    Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
                    Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());
                    if (edge1 == null || edge2 == null) continue;
                    builder.append("\n").append(++j1).append(". ").append(edge1).append(" ====> ").append(edge2);
                }
            }
        }

        {
            builder.append("""


                    Edges correctly oriented""");

            List<Edge> correct = new ArrayList<>();

            for (Edge adj : allSingleEdges) {
                Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
                Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());
                if (edge1.equals(edge2)) {
                    correct.add(edge1);
                }
            }

            if (correct.isEmpty()) {
                builder.append("\n  --NONE--");
            } else {
                sort(correct);

                int j2 = 0;

                for (Edge edge : correct) {
                    builder.append("\n").append(++j2).append(". ").append(edge);
                }
            }
        }
        return builder.toString();
    }

    /**
     * Returns a string representing a table of statistics that can be printed.
     *
     * @param trueGraph   The true graph.
     * @param targetGraph The target graph.
     * @return The comparison string.
     */
    public static String getStatsListTable(Graph trueGraph, Graph targetGraph) {
        return getStatsListTable(trueGraph, targetGraph, null, -1);
    }

    /**
     * Returns a string representing a table of statistics that can be printed.
     *
     * @param trueGraph   The true graph.
     * @param targetGraph The target graph.
     * @param dataModel   The data model; some statistics (like BIC) may use this.
     * @param elapsedTime a long
     * @return The comparison string.
     */
    public static String getStatsListTable(Graph trueGraph, Graph targetGraph, DataModel dataModel, long elapsedTime) {
        Graph _targetGraph = GraphUtils.replaceNodes(targetGraph, trueGraph.getNodes());

        List<Statistic> statistics = statistics();

        TextTable table = new TextTable(statistics.size() + 1, 3);
        NumberFormat nf = new DecimalFormat("0.###");

        List<String> abbr = new ArrayList<>();
        List<String> desc = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (Statistic statistic : statistics) {
            try {
                values.add(statistic.getValue(trueGraph, _targetGraph, dataModel));
                abbr.add(statistic.getAbbreviation());
                desc.add(statistic.getDescription());
            } catch (Exception ignored) {
            }
        }

        if (elapsedTime >= 0) {
            abbr.add("Elapsed");
            desc.add("Wall time (s)");
            values.add((double) elapsedTime / 1000.0);
        }

        for (int i = 0; i < abbr.size(); i++) {
            double value = values.get(i);
            table.setToken(i, 1, Double.isNaN(value) ? "-" : nf.format(value));
            table.setToken(i, 0, abbr.get(i));
            table.setToken(i, 2, desc.get(i));
        }

        table.setJustification(TextTable.LEFT_JUSTIFIED);

        return table.toString();
    }

    private static List<Statistic> statistics() {
        List<Statistic> statistics = new ArrayList<>();

        // Others
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new AdjacencyTn());
        statistics.add(new AdjacencyTp());
        statistics.add(new AdjacencyTpr());
        statistics.add(new AdjacencyFpr());
        statistics.add(new AdjacencyFn());
        statistics.add(new AdjacencyFp());
        statistics.add(new AdjacencyFn());
        statistics.add(new ArrowheadTn());
        statistics.add(new ArrowheadTp());
        statistics.add(new F1Adj());
        statistics.add(new F1All());
        statistics.add(new F1Arrow());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumCorrectVisibleEdges());
        statistics.add(new PercentBidirectedEdges());
        statistics.add(new TailPrecision());
        statistics.add(new TailRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new AverageDegreeEst());
        statistics.add(new AverageDegreeTrue());
        statistics.add(new DensityEst());
        statistics.add(new DensityTrue());
        statistics.add(new StructuralHammingDistance());

        // Stats for PAGs.
        statistics.add(new NumDirectedEdges());
        statistics.add(new NumUndirectedEdges());
        statistics.add(new NumPartiallyOrientedEdges());
        statistics.add(new NumNondirectedEdges());
        statistics.add(new NumBidirectedEdgesEst());
        statistics.add(new TrueDagPrecisionTails());
        statistics.add(new TrueDagPrecisionArrow());
        statistics.add(new BidirectedLatentPrecision());
        statistics.add(new LegalPag());
        statistics.add(new Maximal());

        return statistics;
    }


    /**
     * Returns a misclassification comparison of two graphs. This includes both an edge misclassiifcation matrix as well
     * as an endpoint misclassification matrix.
     *
     * @param trueGraph   The true graph.
     * @param targetGraph The target graph.
     * @return The comparison string.
     */
    @NotNull
    public static String getMisclassificationTable(Graph trueGraph, Graph targetGraph) {
        return "Edge Misclassification Table:" +
               "\n" +
               MisclassificationUtils.edgeMisclassifications(targetGraph, trueGraph) +
               "\n\n" +
               "Endpoint Misclassification Table:" +
               "\n\n" +
               MisclassificationUtils.endpointMisclassification(targetGraph, trueGraph);
    }
}
