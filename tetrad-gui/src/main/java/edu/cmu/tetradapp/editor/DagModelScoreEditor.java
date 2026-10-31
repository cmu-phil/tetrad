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

/** Editor that displays a table of model scores (metrics) for a DAG on a dataset. */
public final class DagModelScoreEditor extends JPanel {

    /** The metrics to compute. */
    private final List<DagMetric> metrics = new ArrayList<>();
    /** The computed metric results, one per table row. */
    private final List<DagMetricResult> rows = new ArrayList<>();
    /** The table model backing the results table. */
    private final ResultTableModel model = new ResultTableModel();

    /** The dataset on which the DAG is scored. */
    private DataSet data;
    /** The DAG being scored. */
    private Graph dag;

    /**
     * Constructs an editor that scores the model's graph on its input data.
     *
     * @param model the DAG model score model
     * @throws IllegalArgumentException if the model's input graph is not a legal DAG
     */
    public DagModelScoreEditor(DagModelScoreModel model) {
        this();

        if (!model.getInputGraph().paths().isLegalDag()) {
            throw new IllegalArgumentException("Input graph is not a legal DAG");
        }

        setContext(model.getInputData(), model.getGraph());
    }

    /** Constructs an empty editor with a results table and a Recompute button. */
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

    /**
     * Sets the dataset and DAG to score and recomputes the metrics.
     *
     * @param data the dataset
     * @param dag  the DAG
     */
    public void setContext(DataSet data, Graph dag) {
        this.data = data;
        this.dag = dag;
        recompute();
    }

    /** Recomputes the default metrics for the current dataset and DAG and refreshes the table. */
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