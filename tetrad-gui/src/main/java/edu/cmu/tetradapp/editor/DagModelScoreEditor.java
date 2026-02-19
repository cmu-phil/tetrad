package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.DagMetric;
import edu.cmu.tetrad.sem.DagMetricRegistry;
import edu.cmu.tetrad.sem.DagMetricResult;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.DagModelScoreModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public final class DagModelScoreEditor extends JPanel {

    private final List<DagMetric> metrics = new ArrayList<>();
    private final List<DagMetricResult> rows = new ArrayList<>();
    private final ResultTableModel model = new ResultTableModel();

    private DataSet data;
    private Graph dag;

    public DagModelScoreEditor(DagModelScoreModel model) {
        this();

        if (!model.getInputGraph().paths().isLegalDag()) {
            throw new IllegalArgumentException("Input graph is not a legal DAG");
        }

        setContext(model.getInputData(), model.getGraph());
    }

    public DagModelScoreEditor() {
        super(new BorderLayout(8, 8));

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);

        JButton recompute = new JButton("Recompute");
        recompute.addActionListener(e -> recompute());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(recompute);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void addMetrics(DataSet data) {
        metrics.clear();
        metrics.addAll(DagMetricRegistry.defaultMetricsFor(data));
    }

    public void setContext(DataSet data, Graph dag) {
        this.data = data;
        this.dag = dag;
        recompute();
    }

    public void recompute() {
        rows.clear();
        if (data == null || dag == null) {
            model.fireTableDataChanged();
            return;
        }

        metrics.clear();
        addMetrics(data);

        for (DagMetric m : metrics) {
            rows.add(m.compute(data, dag));
        }

        model.fireTableDataChanged();
    }

    private final class ResultTableModel extends AbstractTableModel {
        private final String[] cols = {"Metric", "Value", "Note"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            DagMetricResult x = rows.get(r);
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
            return switch (c) {
                case 0 -> x.name();
                case 1 -> Double.isFinite(x.value()) ? nf.format(x.value()) : Double.NaN;
                case 2 -> x.note();
                default -> "";
            };
        }
    }
}