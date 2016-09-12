package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.TabularComparison;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;

public class TabularComparisonEditor extends JPanel {

    private final TabularComparison comparison;

    public TabularComparisonEditor(TabularComparison comparison) {
        this.comparison = comparison;
        setup();
    }

    private void setup() {
        java.util.List<Graph> referenceGraphs = comparison.getReferenceGraphs();
        JTabbedPane pane = new JTabbedPane(JTabbedPane.TOP);

        pane.addTab("Comparison", getTableDisplay());

        JTabbedPane pane2 = new JTabbedPane(JTabbedPane.LEFT);

        for (int i = 0; i < referenceGraphs.size(); i++) {
            JTabbedPane pane3 = new JTabbedPane(JTabbedPane.TOP);
            pane3.add("Target", new GraphEditor(new GraphWrapper(comparison.getTargetGraphs().get(i))).getWorkbench());
            pane3.add("Reference", new GraphEditor(new GraphWrapper(comparison.getReferenceGraphs().get(i))).getWorkbench());
            pane2.add("" + (i + 1), pane3);
        }

        pane.addTab("Graphs", pane2);

        add(pane);
    }


    private Box getTableDisplay() {

        DataSet dataSet = comparison.getDataSet();

        TextTable table = getTextTable(dataSet, new DecimalFormat("0.00"));

        StringBuilder builder = new StringBuilder();
        Map<String, String> allParamsSettings = comparison.getAllParamSettings();

        if (allParamsSettings != null) {
            for (String key : allParamsSettings.keySet()) {
                builder.append(key).append(" = ").append(allParamsSettings.get(key)).append("\n");
            }
        }

        JTextArea area = new JTextArea(
                "\n" + table.toString()
        );

        area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
//        area.setPreferredSize(area.getMaximumSize());

        JScrollPane pane = new JScrollPane(area);
        pane.setPreferredSize(new Dimension(700, 400));

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Tabular Comparison\b"));
        b.add(b1);
        b.add(Box.createVerticalStrut(10));

        Box b3 = Box.createHorizontalBox();
        b3.add(pane);
        b.add(b3);

//        setPreferredSize(new Dimension(700,400));

        return b;
    }

    private TextTable getTextTable(DataSet dataSet, NumberFormat nf) {
        TextTable table = new TextTable(dataSet.getNumRows() + 2, dataSet.getNumColumns() + 1);

        table.setToken(0, 0, "Run #");

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            table.setToken(0, j + 1, dataSet.getVariable(j).getName());
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            table.setToken(i + 1, 0, Integer.toString(i + 1));
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                table.setToken(i + 1, j + 1, nf.format(dataSet.getDouble(i, j)));
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                sum += dataSet.getDouble(i, j);
            }

            double avg = sum / dataSet.getNumRows();

            table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, nf2.format(avg));
        }

        table.setToken(dataSet.getNumRows() + 2 - 1, 0, "Avg");

        return table;
    }


}
