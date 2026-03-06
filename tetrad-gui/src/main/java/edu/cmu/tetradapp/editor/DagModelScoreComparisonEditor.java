package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.DagMetric;
import edu.cmu.tetrad.sem.DagMetricResult;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.DagModelScoreComparisonModel;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares multiple DAGs (columns) on a single dataset (shared),
 * displaying multiple metrics (rows), with per-row winners bolded.
 * <p>
 * Reflection constructor pattern: (DagModelScoreComparisonModel model).
 */
public final class DagModelScoreComparisonEditor extends JPanel {

    private final DagModelScoreComparisonModel model;
    private final List<DagMetricSpec> metricSpecs = new ArrayList<>();
    private final List<List<DagMetricResult>> resultsByMetric = new ArrayList<>(); // [metricRow][graphCol]
    private final JTable table;
    private final ResultTableModel tableModel;

    public DagModelScoreComparisonEditor(DagModelScoreComparisonModel model) {
        super(new BorderLayout(8, 8));
        if (model == null) throw new NullPointerException("model");
        this.model = model;

        this.tableModel = new ResultTableModel();
        this.table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Winner-bolding renderer for numeric cells.
        table.setDefaultRenderer(Object.class, new WinnerBoldRenderer());

        table.setTransferHandler(new DefaultTableTransferHandler(0));

        JButton recompute = new JButton("Recompute");
        recompute.addActionListener(e -> recompute());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(recompute);

        JScrollPane scroll = new JScrollPane(table);

        // Basic preferred sizing: grows with #graphs but capped.
        int prefWidth = computePreferredWidth(model.getGraphs().size());
        int prefHeight = 360;
        scroll.setPreferredSize(new Dimension(prefWidth, prefHeight));

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        recompute();
    }

    private static int computePreferredWidth(int graphCount) {
        // Columns: Metric + Note + N graphs
        // Heuristic widths:
        int wMetric = 160;
        int wNote = 220;
        int wGraph = 140;
        int total = wMetric + wNote + TMath.max(1, graphCount) * wGraph + 40; // padding
        int min = 650;
        int max = 1400;
        return TMath.max(min, TMath.min(max, total));
    }

    /**
     * Fill in whichever metrics you want here (and tweak tomorrow).
     */
    private void buildDefaultMetricSpecs(DataSet data) {
        metricSpecs.clear();

        // You can keep using your registry logic; this is just a starter set.
        // IMPORTANT: set Better direction correctly so bolding is meaningful.

        if (data.isContinuous()) {
            metricSpecs.add(new DagMetricSpec("LG BIC", "Linear Gaussian BIC", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.semBic()));
            metricSpecs.add(new DagMetricSpec("LG Chi Square", "Linear Gaussian Chi Square", Better.LOWER,
                    edu.cmu.tetrad.sem.DagMetrics.lgChiSquare()));
            metricSpecs.add(new DagMetricSpec("LG Model P", "Linear Gaussian Model P-Value", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.lgModelP()));
            metricSpecs.add(new DagMetricSpec("CFI", "Comparative Fit Index", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.cfi()));
            metricSpecs.add(new DagMetricSpec("RMSEA", "RMSEA", Better.LOWER,
                    edu.cmu.tetrad.sem.DagMetrics.rmsea()));

            metricSpecs.add(new DagMetricSpec("FFML", "General Mixed GP Likelihood Score", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.ffml()));
//            metricSpecs.add(new DagMetricSpec("Legendre BIC", "General Mixed BIC Score", Better.HIGHER,
//                    edu.cmu.tetrad.sem.DagMetrics.legendreBic()));
            metricSpecs.add(new DagMetricSpec("Minimax t-RFF BIC", "General Mixed BIC Score", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.minimaxTrffBic()));

//            metricSpecs.add(new DagMetricSpec("MMD2", "Maximum Mean Discrepancy squared", Better.LOWER,
//                    edu.cmu.tetrad.sem.DagMetrics.mmd2()));
        } else if (data.isMixed()) {
            metricSpecs.add(new DagMetricSpec("FFML", "General Mixed GP Likelihood Score", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.ffml()));
//            metricSpecs.add(new DagMetricSpec("Legendre BIC", "General Mixed BIC Score", Better.HIGHER,
//                    edu.cmu.tetrad.sem.DagMetrics.legendreBic()));
            metricSpecs.add(new DagMetricSpec("Minimax t-RFF BIC", "General Mixed BIC Score", Better.HIGHER,
                    edu.cmu.tetrad.sem.DagMetrics.minimaxTrffBic()));
//            metricSpecs.add(new DagMetricSpec("MMD2", "Maximum Mean Discrepancy squared", Better.LOWER,
//                    edu.cmu.tetrad.sem.DagMetrics.mmd2()));
        } else {
//            metricSpecs.add(new DagMetricSpec("MMD2", "Maximum Mean Discrepancy squared", Better.LOWER,
//                    edu.cmu.tetrad.sem.DagMetrics.mmd2()));
        }
    }

    public void recompute() {
        resultsByMetric.clear();

        DataSet data = model.getData();
        List<DagModelScoreComparisonModel.NamedGraph> graphs = model.getGraphs();

        buildDefaultMetricSpecs(data);

        for (DagMetricSpec spec : metricSpecs) {
            List<DagMetricResult> row = new ArrayList<>();
            for (DagModelScoreComparisonModel.NamedGraph ng : graphs) {
                Graph g = ng.getGraph();
                DagMetricResult r;
                try {
                    r = spec.metric().compute(data, g);
                } catch (Throwable t) {
                    // Keep UI alive: show NaN + message in note.
                    r = new DagMetricResult(spec.name(), Double.NaN, "ERROR: " + t.getMessage());
                }
                row.add(r);
            }
            resultsByMetric.add(row);
        }

        // Column widths
        applyColumnWidths(graphs.size());

        tableModel.fireTableStructureChanged();
        // Need to reapply renderer after structure change
        table.setDefaultRenderer(Object.class, new WinnerBoldRenderer());
    }

    private void applyColumnWidths(int graphCount) {
        // Must run after model columns are set; safest is later, but a best effort works here.
        SwingUtilities.invokeLater(() -> {
            if (table.getColumnModel().getColumnCount() == 0) return;

            int cMetric = 0;
            int cNote = 1;

            table.getColumnModel().getColumn(cMetric).setPreferredWidth(160);
            table.getColumnModel().getColumn(cNote).setPreferredWidth(220);

            for (int k = 0; k < graphCount; k++) {
                int col = 2 + k;
                if (col < table.getColumnModel().getColumnCount()) {
                    table.getColumnModel().getColumn(col).setPreferredWidth(140);
                }
            }
        });
    }

    // --- Direction of "better" for bolding ---
    public enum Better {HIGHER, LOWER, NA}

    /**
     * Metric spec = implementation + metadata for display/bolding.
     */
    public record DagMetricSpec(String name, String note, Better better, DagMetric metric) {
    }

    private final class ResultTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return metricSpecs.size();
        }

        @Override
        public int getColumnCount() {
            // Metric | Note | one column per graph
            return 2 + model.getGraphs().size();
        }

        @Override
        public String getColumnName(int col) {
            if (col == 0) return "Metric";
            if (col == 1) return "Note";
            int g = col - 2;
            if (g >= 0 && g < model.getGraphs().size()) {
                return model.getGraphs().get(g).getName();
            }
            return "";
        }

        @Override
        public Object getValueAt(int row, int col) {
            DagMetricSpec spec = metricSpecs.get(row);

            if (col == 0) return spec.name();
            if (col == 1) return spec.note();

            int g = col - 2;
            if (row >= resultsByMetric.size()) return Double.NaN;
            List<DagMetricResult> rrow = resultsByMetric.get(row);
            if (g < 0 || g >= rrow.size()) return Double.NaN;

            return rrow.get(g).value();
        }
    }

    /**
     * Renderer that bolds all tied best DAGs per metric row (numeric columns only).
     */
    private final class WinnerBoldRenderer extends DefaultTableCellRenderer {
        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        private static boolean ties(double a, double b) {
            // Absolute + relative tolerance, so it works across scales.
            double diff = TMath.abs(a - b);
            double scale = TMath.max(1.0, TMath.max(TMath.abs(a), TMath.abs(b)));
            double eps = 1e-12 * scale;   // tighten/loosen as you prefer
            return diff <= eps;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            // Default formatting
            if (value instanceof Number) {
                double v = ((Number) value).doubleValue();
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(Double.isFinite(v) ? nf.format(v) : "NaN");
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
            }

            // Default font
            Font base = c.getFont();
            c.setFont(base.deriveFont(Font.PLAIN));

            // Bold winners only in graph columns (>=2)
            if (col >= 2 && row >= 0 && row < metricSpecs.size()) {
                if (isWinnerColumnForRow(row, col)) {
                    c.setFont(base.deriveFont(Font.BOLD));
                }
            }

            return c;
        }

        private boolean isWinnerColumnForRow(int metricRow, int tableCol) {
            int graphIdx = tableCol - 2;
            if (graphIdx < 0) return false;
            if (metricRow < 0 || metricRow >= resultsByMetric.size()) return false;

            List<DagMetricResult> vals = resultsByMetric.get(metricRow);
            if (vals == null || graphIdx >= vals.size()) return false;

            // Determine better-direction from the first finite result in the row.
            DagMetricResult.Better b = DagMetricResult.Better.NA;
            for (DagMetricResult r : vals) {
                if (r != null && r.better() != null && r.better() != DagMetricResult.Better.NA) {
                    b = r.better();
                    break;
                }
            }
            if (b == DagMetricResult.Better.NA) return false;

            // Find best finite value.
            double best = (b == DagMetricResult.Better.HIGHER)
                    ? Double.NEGATIVE_INFINITY
                    : Double.POSITIVE_INFINITY;

            boolean found = false;
            for (DagMetricResult r : vals) {
                if (r == null) continue;
                double v = r.value();
                if (!Double.isFinite(v)) continue;

                if (!found) {
                    best = v;
                    found = true;
                } else if (b == DagMetricResult.Better.HIGHER) {
                    if (v > best) best = v;
                } else if (b == DagMetricResult.Better.LOWER) {
                    if (v < best) best = v;
                }
            }
            if (!found) return false;

            // Candidate value
            DagMetricResult r = vals.get(graphIdx);
            if (r == null) return false;
            double v = r.value();
            if (!Double.isFinite(v)) return false;

            // Tie check (tolerant). Bold if v ties best.
            return ties(v, best);
        }
    }
}