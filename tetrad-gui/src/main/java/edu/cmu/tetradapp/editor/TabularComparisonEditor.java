package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.TabularComparison;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;

/**
 * <p>TabularComparisonEditor class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TabularComparisonEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 8455624852328328919L;

    /**
     * The comparison being edited.
     */
    private final TabularComparison comparison;

    /**
     * <p>Constructor for TabularComparisonEditor.</p>
     *
     * @param comparison a {@link edu.cmu.tetradapp.model.TabularComparison} object
     */
    public TabularComparisonEditor(TabularComparison comparison) {
        this.comparison = comparison;
        setup();
    }

    private void setup() {
        JTabbedPane pane = new JTabbedPane(SwingConstants.TOP);

        pane.addTab("Comparison", getTableDisplay());

        JTabbedPane pane2 = new JTabbedPane(SwingConstants.LEFT);

        JTabbedPane pane3 = new JTabbedPane(SwingConstants.TOP);

        GraphEditor graphEditor = new GraphEditor(new GraphWrapper(this.comparison.getTargetGraph()));
        graphEditor.enableEditing(false);
        pane3.add("Target Graph", graphEditor.getWorkbench());

        graphEditor = new GraphEditor(new GraphWrapper(this.comparison.getReferenceGraph()));
        graphEditor.enableEditing(false);
        pane3.add("True Graph", graphEditor.getWorkbench());

        pane2.add("Reference Graph", pane3);

        pane.addTab("Graphs", pane2);

        add(pane);
    }

    private Box getTableDisplay() {

        DataSet dataSet = this.comparison.getDataSet();

        TextTable table = getTextTable(dataSet, new DecimalFormat("0.00"));

        StringBuilder b0 = new StringBuilder();
        String trueGraphAndTarget = "Target graphs from " + this.comparison.getTargetName()
                                    + "\nTrue graphs from " + this.comparison.getReferenceName();
        b0.append(trueGraphAndTarget).append("\n\n");
        b0.append(table);

        Map<String, String> allParamsSettings = this.comparison.getAllParamSettings();

        if (allParamsSettings != null) {
            for (String key : allParamsSettings.keySet()) {
                b0.append(key).append(" = ").append(allParamsSettings.get(key)).append("\n");
            }
        }

        JTextArea area = new JTextArea(
                b0.toString()
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
        table.setDelimiter(TextTable.Delimiter.TAB);
        table.setToken(0, 0, "Run");

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            table.setToken(0, j + 1, dataSet.getVariable(j).getName());
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            table.setToken(i + 1, 0, Integer.toString(i + 1));
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                double d = dataSet.getDouble(i, j);

                if (Double.isNaN(d)) {
                    table.setToken(i + 1, j + 1, "*");
                } else {
                    table.setToken(i + 1, j + 1, nf.format(d));
                }
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double d = dataSet.getDouble(i, j);

                if (!Double.isNaN(d)) {
                    sum += d;
                    count++;
                }
            }

            double avg = sum / count;

            if (Double.isNaN(avg)) {
                table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, "*");
            } else {
                table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, nf2.format(avg));
            }
        }

        table.setToken(dataSet.getNumRows() + 2 - 1, 0, "Avg");

        return table;
    }

}
