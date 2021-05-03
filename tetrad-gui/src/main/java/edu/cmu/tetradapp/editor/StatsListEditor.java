package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.TabularComparison;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class StatsListEditor extends JPanel {

    private static final long serialVersionUID = 8455624852328328919L;

    private final TabularComparison comparison;

    private Graph targetGraph;
    private Graph referenceGraph;

    public StatsListEditor(TabularComparison comparison) {
        this.comparison = comparison;
        setup();
    }

    private void setup() {

        // We'll leave the underlying model the same but just complain if there's not exactly
        // one reference and one target graph.
        List<Graph> referenceGraphs = comparison.getReferenceGraphs();
        List<Graph> targetGraphs = comparison.getTargetGraphs();

        if (referenceGraphs.size() != 1) throw new IllegalArgumentException("Expecting one comparison graph.");
        if (targetGraphs.size() != 1) throw new IllegalArgumentException("Expecting one target graph.");

        referenceGraph = referenceGraphs.get(0);
        targetGraph = targetGraphs.get(0);

        add(getTableDisplay());
    }

    private JComponent getTableDisplay() {
        List<Statistic> statistics = new ArrayList<>();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new AdjacencyTN());
        statistics.add(new AdjacencyTN());
        statistics.add(new AdjacencyTP());
        statistics.add(new AdjacencyTPR());
        statistics.add(new AdjacencyFN());
        statistics.add(new AdjacencyFP());
        statistics.add(new AdjacencyFN());
        statistics.add(new ArrowheadTN());
        statistics.add(new ArrowheadTP());
        statistics.add(new F1Adj());
        statistics.add(new F1All());
        statistics.add(new F1Arrow());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new SHD());
//        statistics.add(new NodesInCyclesPrecision());
//        statistics.add(new NodesInCyclesRecall());
        statistics.add(new NumAmbiguousTriples());
        statistics.add(new PercentAmbiguous());
        statistics.add(new PercentBidirectedEdges());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new TailPrecision());
        statistics.add(new TailRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());

        TextTable table = new TextTable(statistics.size(), 3);
        NumberFormat nf = new DecimalFormat("0.###");

        for (int i = 0; i < statistics.size(); i++) {
            table.setToken(i, 0, statistics.get(i).getAbbreviation());
            table.setToken(i, 1, statistics.get(i).getDescription());
            double value = statistics.get(i).getValue(referenceGraph, targetGraph, null);
            table.setToken(i, 2, Double.isNaN(value) ? "-" : "" + nf.format(value));
        }

        table.setJustification(TextTable.LEFT_JUSTIFIED);

        JTextArea area = new JTextArea(
                "Comparing target " + comparison.getTargetName() + " to reference " + comparison.getReferenceName() + "\n\n" +
                        table.toString()
        );

        area.setBorder(new EmptyBorder(5, 5, 5, 5));

        area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        area.setPreferredSize(new Dimension(700, 1200));

        JScrollPane pane = new JScrollPane(area);
        pane.setPreferredSize(new Dimension(700, 700));

        Box b = Box.createVerticalBox();
        b.add(pane);
        b.add(new JPanel());

        return b;
    }
}
