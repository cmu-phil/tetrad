package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.TextTable;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares two graphs, returning a table of statistics.
 *
 * @author josephramsey
 */
public class CompareTwoGraphs {
    private final Graph targetGraph;
    private final Graph referenceGraph;
    private final DataModel dataModel;

    public CompareTwoGraphs(Graph targetGraph, Graph referenceGraph) {
        this(targetGraph, referenceGraph, null);
    }

    public CompareTwoGraphs(Graph targetGraph, Graph referenceGraph, DataModel dataModel) {
        this.targetGraph = targetGraph;
        this.referenceGraph = referenceGraph;
        this.dataModel = dataModel;
    }

    @NotNull
    public String toString() {
        if (this.targetGraph == this.referenceGraph) {
            throw new IllegalArgumentException();
        }

        Graph _targetGraph = GraphUtils.replaceNodes(this.targetGraph, this.referenceGraph.getNodes());

        List<Statistic> statistics = statistics();

        TextTable table = new TextTable(statistics.size(), 3);
        NumberFormat nf = new DecimalFormat("0.###");

        List<String> abbr = new ArrayList<>();
        List<String> desc = new ArrayList<>();
        List<Double> vals = new ArrayList<>();

        for (Statistic statistic : statistics) {
            try {
                vals.add(statistic.getValue(this.referenceGraph, _targetGraph, this.dataModel));
                abbr.add(statistic.getAbbreviation());
                desc.add(statistic.getDescription());
            } catch (Exception ignored) {
            }
        }

        for (int i = 0; i < abbr.size(); i++) {
            double value = vals.get(i);
            table.setToken(i, 1, Double.isNaN(value) ? "-" : "" + nf.format(value));
            table.setToken(i, 0, abbr.get(i));
            table.setToken(i, 2, desc.get(i));
        }

        table.setJustification(TextTable.LEFT_JUSTIFIED);

        return table.toString();
    }

    @NotNull
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

//        statistics.add(new LegalPag());

        return statistics;
    }
}
