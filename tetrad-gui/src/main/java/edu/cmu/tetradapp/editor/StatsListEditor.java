package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.model.TabularComparison;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.getComparisonGraph;

public class StatsListEditor extends JPanel {

    private static final long serialVersionUID = 8455624852328328919L;

    private final TabularComparison comparison;
    private final Parameters params;
    private final DataModel dataModel;
    private final Graph targetGraph;
    private Graph referenceGraph;
    private JTextArea area;

    public StatsListEditor(TabularComparison comparison) {
        this.comparison = comparison;
        this.params = comparison.getParams();
        this.targetGraph = comparison.getTargetGraph();
        this.referenceGraph = getComparisonGraph(comparison.getReferenceGraph(), this.params);
        this.dataModel = comparison.getDataModel();
        setup();
    }

    private void setup() {
        JMenuBar menubar = menubar();
        show(menubar);
    }

    private void show(JMenuBar menubar) {
        setLayout(new BorderLayout());
        add(menubar, BorderLayout.NORTH);
        add(getTableDisplay(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent getTableDisplay() {
        this.area = new JTextArea();
        this.area.setText(tableTextWithHeader());
        this.area.moveCaretPosition(0);
        this.area.setSelectionStart(0);
        this.area.setSelectionEnd(0);

        this.area.setBorder(new EmptyBorder(5, 5, 5, 5));

        this.area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        this.area.setPreferredSize(new Dimension(700, 1200));

        JScrollPane pane = new JScrollPane(this.area);
        pane.setPreferredSize(new Dimension(700, 700));

        Box b = Box.createVerticalBox();
        b.add(pane);

        return b;
    }

    @NotNull
    private String tableTextWithHeader() {
        TextTable table = tableText();
        return "Comparing target " + this.comparison.getTargetName() + " to reference " + this.comparison.getReferenceName()
                + "\n\n" + table;
    }

    @NotNull
    private TextTable tableText() {
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
            table.setToken(i, 2, Double.isNaN(value) ? "-" : "" + nf.format(value));
            table.setToken(i, 0, abbr.get(i));
            table.setToken(i, 1, desc.get(i));
        }

        table.setJustification(TextTable.LEFT_JUSTIFIED);
        return table;
    }

    @NotNull
    private List<Statistic> statistics() {
        List<Statistic> statistics = new ArrayList<>();

        statistics.add(new BicTrue());
        statistics.add(new BicEst());
        statistics.add(new BicDiff());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new AdjacencyTN());
        statistics.add(new AdjacencyTP());
        statistics.add(new AdjacencyTPR());
        statistics.add(new AdjacencyFPR());
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
        statistics.add(new AverageDegreeEst());
        statistics.add(new AverageDegreeTrue());
        statistics.add(new DensityEst());
        statistics.add(new DensityTrue());
        return statistics;
    }

    @NotNull
    private JMenuBar menubar() {
        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Compare To...");
        JMenuItem graph = new JCheckBoxMenuItem("DAG");
        graph.setBackground(Color.WHITE);
        JMenuItem cpdag = new JCheckBoxMenuItem("CPDAG");
        cpdag.setBackground(Color.YELLOW);
        JMenuItem pag = new JCheckBoxMenuItem("PAG");
        pag.setBackground(Color.GREEN.brighter().brighter());

        ButtonGroup group = new ButtonGroup();
        group.add(graph);
        group.add(cpdag);
        group.add(pag);

        menu.add(graph);
        menu.add(cpdag);
        menu.add(pag);

        menubar.add(menu);

        switch (this.params.getString("graphComparisonType")) {
            case "CPDAG":
                menu.setText("Compare to CPDAG...");
                cpdag.setSelected(true);
                break;
            case "PAG":
                menu.setText("Compare to PAG...");
                pag.setSelected(true);
                break;
            default:
                menu.setText("Compare to DAG...");
                graph.setSelected(true);
                break;
        }

        graph.addActionListener(e -> {
            this.params.set("graphComparisonType", "DAG");
            menu.setText("Compare to DAG...");
            menu.setBackground(Color.WHITE);
            this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

            this.area.setText(tableTextWithHeader());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);

            this.area.repaint();

        });

        cpdag.addActionListener(e -> {
            this.params.set("graphComparisonType", "CPDAG");
            menu.setText("Compare to CPDAG...");
            menu.setBackground(Color.YELLOW);
            this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

            this.area.setText(tableTextWithHeader());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);

            this.area.repaint();

        });

        pag.addActionListener(e -> {
            this.params.set("graphComparisonType", "PAG");
            menu.setText("Compare to PAG...");
            menu.setBackground(Color.GREEN.brighter().brighter());
            this.referenceGraph = getComparisonGraph(this.comparison.getReferenceGraph(), this.params);

            this.area.setText(tableTextWithHeader());
            this.area.moveCaretPosition(0);
            this.area.setSelectionStart(0);
            this.area.setSelectionEnd(0);
            this.area.repaint();
        });

        return menubar;
    }
}
