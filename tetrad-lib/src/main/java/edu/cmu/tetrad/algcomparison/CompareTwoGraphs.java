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
 * Compares two graphs, returning a table of statistics.
 *
 * @author josephramsey
 */
public class CompareTwoGraphs {
    private final Graph targetGraph;
    private final Graph referenceGraph;
    private final DataModel dataModel;

    /**
     * Constructor
     *
     * @param targetGraph    The target graph.
     * @param referenceGraph The reference graph.
     */
    public CompareTwoGraphs(Graph targetGraph, Graph referenceGraph) {
        this(targetGraph, referenceGraph, null);
    }

    /**
     * Constructor
     *
     * @param targetGraph    The target graph.
     * @param referenceGraph The reference graph.
     * @param dataModel      A data model, used for some statistics. May be null.
     */
    public CompareTwoGraphs(Graph targetGraph, Graph referenceGraph, DataModel dataModel) {
        if (targetGraph == null) throw new IllegalArgumentException("Target graph can't be null.");
        if (referenceGraph == null) throw new IllegalArgumentException("Reference graph can't be null.");

        this.targetGraph = targetGraph;
        this.referenceGraph = referenceGraph;
        this.dataModel = dataModel;
    }

    @NotNull
    public static String getEdgewiseComparisonString(Graph trueGraph, Graph targetGraph, boolean printStars) {
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

//                if (targetGraph.getEdge(_edge.getNode1(), _edge.getNode2()) == null) {
//                    continue;
//                }

//                Node node1 = targetGraph.getNode(targetGraph.getEdge(_edge.getNode1(), _edge.getNode2()).getNode1().getName());
//                Node node2 = targetGraph.getNode(targetGraph.getEdge(_edge.getNode1(), _edge.getNode2()).getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(_edge.toString());

//                if (printStars) {
//                    boolean directedInGraph2 = false;
//
//                    if (Edges.isDirectedEdge(targetGraph.getEdge(_edge.getNode1(), _edge.getNode2())) && targetGraph.paths().existsSemidirectedPath(node1, node2)) {
//                        directedInGraph2 = true;
//                    } else if ((Edges.isUndirectedEdge(targetGraph.getEdge(_edge.getNode1(), _edge.getNode2())) || Edges.isBidirectedEdge(targetGraph.getEdge(_edge.getNode1(), _edge.getNode2())))
//                            && (targetGraph.paths().existsSemidirectedPath(node1, node2)
//                            || targetGraph.paths().existsSemidirectedPath(node2, node1))) {
//                        directedInGraph2 = true;
//                    }
//
//                    if (directedInGraph2) {
//                        builder.append(" *");
//                    }
//                }
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

                Node node1 = trueGraph.getNode(edge.getNode1().getName());
                Node node2 = trueGraph.getNode(edge.getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(edge);

                if (printStars) {
                    boolean directedInGraph1 = false;

                    if (Edges.isDirectedEdge(edge) && trueGraph.paths().existsSemidirectedPath(node1, node2)) {
                        directedInGraph1 = true;
                    } else if ((Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge))
                            && (trueGraph.paths().existsSemidirectedPath(node1, node2)
                            || trueGraph.paths().existsSemidirectedPath(node2, node1))) {
                        directedInGraph1 = true;
                    }

                    if (directedInGraph1) {
                        builder.append(" *");
                    }
                }
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

        builder.append("\n\n"
                + "Two-cycles in true correctly adjacent in estimated");

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
            builder.append("\n\n" + "Edges incorrectly oriented");

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
            builder.append("\n\n" + "Edges correctly oriented");

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
     * @return This string.
     */
    public String getStatsListTable() {
        if (this.targetGraph == this.referenceGraph) {
            throw new IllegalArgumentException();
        }

        Graph _targetGraph = GraphUtils.replaceNodes(this.targetGraph, this.referenceGraph.getNodes());

        List<Statistic> statistics = statistics();

        TextTable table = new TextTable(statistics.size(), 3);
        NumberFormat nf = new DecimalFormat("0.###");

        List<String> abbr = new ArrayList<>();
        List<String> desc = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (Statistic statistic : statistics) {
            try {
                values.add(statistic.getValue(this.referenceGraph, _targetGraph, this.dataModel));
                abbr.add(statistic.getAbbreviation());
                desc.add(statistic.getDescription());
            } catch (Exception ignored) {
            }
        }

        for (int i = 0; i < abbr.size(); i++) {
            double value = values.get(i);
            table.setToken(i, 1, Double.isNaN(value) ? "-" : "" + nf.format(value));
            table.setToken(i, 0, abbr.get(i));
            table.setToken(i, 2, desc.get(i));
        }

        table.setJustification(TextTable.LEFT_JUSTIFIED);

        return table.toString();
    }

    private List<Statistic> statistics() {
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
        statistics.add(new NumCorrectVisibleAncestors());
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


        // Joe table.
        statistics.add(new NumDirectedEdges());
        statistics.add(new NumUndirectedEdges());
        statistics.add(new NumPartiallyOrientedEdges());
        statistics.add(new NumNondirectedEdges());
        statistics.add(new NumBidirectedEdgesEst());
        statistics.add(new TrueDagPrecisionTails());
        statistics.add(new TrueDagPrecisionArrow());
        statistics.add(new BidirectedLatentPrecision());

        // Greg table
//        statistics.add(new AncestorPrecision());
//        statistics.add(new AncestorRecall());
//        statistics.add(new AncestorF1());
//        statistics.add(new SemidirectedPrecision());
//        statistics.add(new SemidirectedRecall());
//        statistics.add(new SemidirectedPathF1());
//        statistics.add(new NoSemidirectedPrecision());
//        statistics.add(new NoSemidirectedRecall());
//        statistics.add(new NoSemidirectedF1());

        return statistics;
    }

    @NotNull
    public static String getMisclassificationTable(Graph targetGraph, Graph comparisonGraph) {
        return "Edge Misclassification Table:" +
                "\n" +
                MisclassificationUtils.edgeMisclassifications(targetGraph, comparisonGraph) +
                "\n\n" +
                "Endpoint Misclassification Table:" +
                "\n\n" +
                MisclassificationUtils.endpointMisclassification(targetGraph, comparisonGraph);
    }
}
