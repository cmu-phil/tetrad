package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.TextTable;
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

        buildGui();
    }

    private void buildGui() {

        DataSet dataSet = comparison.getDataSet();

        TextTable table1 = getTextTable(dataSet, new int[]{0, 1, 2, 3, 4, 5}, new DecimalFormat("0"));
        TextTable table2 = getTextTable(dataSet, new int[]{6, 7, 8, 9, 10}, new DecimalFormat("0.00"));
//        TextTable table3 = getTextTable(dataSet, new int[]{10}, new DecimalFormat("0.00"));

        StringBuilder builder = new StringBuilder();
        Map<String, String> allParamsSettings = comparison.getAllParamSettings();

        if (allParamsSettings != null) {
            for (String key : allParamsSettings.keySet()) {
                builder.append(key).append(" = ").append(allParamsSettings.get(key)).append("\n");
            }
        }

        JTextArea area = new JTextArea(
                "\n" + builder.toString()
                + "\n" + table1.toString()
                + "\n\n" + table2.toString()
//                + "\n\n" + table3.toString()
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

        add(b);
    }

    private TextTable getTextTable(DataSet dataSet, int[] columns, NumberFormat nf) {
        TextTable table  = new TextTable(dataSet.getNumRows() + 2, columns.length + 1);

        table.setToken(0, 0, "Run #");

        for (int j = 0; j < columns.length; j++) {
            table.setToken(0, j + 1, dataSet.getVariable(columns[j]).getName());
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            table.setToken(i + 1, 0, Integer.toString(i + 1));
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < columns.length; j++) {
                table.setToken(i + 1, j + 1, nf.format(dataSet.getDouble(i, columns[j])));
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        for (int j = 0; j < columns.length; j++) {
            double sum = 0.0;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                sum += dataSet.getDouble(i, columns[j]);
            }

            double avg = sum / dataSet.getNumRows();

            table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, nf2.format(avg));
        }

        table.setToken(dataSet.getNumRows() + 2 - 1, 0, "Avg");

        return table;
    }


}
