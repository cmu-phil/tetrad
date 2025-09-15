package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mixed CG IM node editor model with inline validation (no popups).
 * - Mean / Variance are edited as numbers (variance may be <= 0 temporarily; renderer paints red).
 * - Betas are stored as raw text; renderer validates/counts vs PM and paints red when mismatched.
 * - applyToIm(...) parses and writes only when the editor says the table is consistent.
 */
public class HybridCgImNodeEditingTable extends AbstractTableModel {

    public static final int COL_NODE = 0;
    public static final int COL_CFG  = 1;
    public static final int COL_MEAN = 2;
    public static final int COL_VAR  = 3;
    public static final int COL_BETA = 4;

    private final String[] columns = {
            "Node",
            "Config (discrete parents)",
            "Mean (intercept)",
            "Variance",
            "Betas (comma- or space-separated, cont-parent order)"
    };

    /** One editable row = one (continuous child, discrete-parent stratum) */
    public static final class Row {
        public final String nodeName;
        public final String discreteConfig;
        public double mean;
        public double variance;

        // Keep both: raw text (for inline invalid states) and parsed values (we recompute on apply).
        public String betasText;

        public Row(String nodeName, String discreteConfig,
                   double mean, double variance, double[] betas) {
            this.nodeName = nodeName;
            this.discreteConfig = discreteConfig;
            this.mean = mean;
            this.variance = variance;
            this.betasText = betasToString(betas);
        }
    }

    private final List<Row> rows = new ArrayList<>();

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int c) { return columns[c]; }

    @Override public Class<?> getColumnClass(int c) {
        return switch (c) {
            case COL_NODE, COL_CFG, COL_BETA -> String.class;
            case COL_MEAN, COL_VAR -> Double.class;
            default -> Object.class;
        };
    }

    @Override public boolean isCellEditable(int r, int c) {
        return c != COL_NODE && c != COL_CFG;
    }

    @Override
    public Object getValueAt(int r, int c) {
        Row row = rows.get(r);
        return switch (c) {
            case COL_NODE -> row.nodeName;
            case COL_CFG  -> row.discreteConfig;
            case COL_MEAN -> row.mean;
            case COL_VAR  -> row.variance;
            case COL_BETA -> row.betasText == null ? "" : row.betasText;
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int r, int c) {
        Row row = rows.get(r);
        if (aValue == null) return;

        try {
            switch (c) {
                case COL_MEAN -> {
                    // JFormattedTextField with NumberFormatter commits a Number
                    double v = (aValue instanceof Number n) ? n.doubleValue()
                            : Double.parseDouble(aValue.toString().trim());
                    row.mean = v;
                }
                case COL_VAR -> {
                    double v = (aValue instanceof Number n) ? n.doubleValue()
                            : Double.parseDouble(aValue.toString().trim());
                    // allow temporarily invalid (<=0); renderer paints red, apply gate prevents commit
                    row.variance = v;
                }
                case COL_BETA -> {
                    // keep raw text; renderer will reformat/validate; apply will parse
                    row.betasText = aValue.toString().trim();
                }
            }
        } catch (Exception ignore) {
            // Inline validation: leave old value; no popups.
        }
        fireTableCellUpdated(r, c);
    }

    // ------------ Public API for the editor ------------

    /** Fill the table from a PM/IM (continuous children only). */
    public void populateFrom(HybridCgModel.HybridCgPm pm, HybridCgModel.HybridCgIm im) {
        rows.clear();

        final Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (pm.isDiscrete(y)) continue; // only continuous children here

            int[] dps = pm.getDiscreteParents(y);
            int[] cps = pm.getContinuousParents(y);

            // Build "radices" for discrete parents: cardinalities in parent order
            int[] radices = new int[dps.length];
            for (int i = 0; i < dps.length; i++) {
                radices[i] = pm.getCardinality(dps[i]);
            }

            // Iterate over all discrete-parent configurations
            int[] discVals = new int[dps.length];
            boolean wrapped;
            do {
                int rowIndex = pm.getRowIndex(y, discVals, null);

                double mean = im.getIntercept(y, rowIndex);
                double variance = im.getVariance(y, rowIndex);

                double[] betas = new double[cps.length];
                for (int t = 0; t < cps.length; t++) {
                    betas[t] = im.getCoefficient(y, rowIndex, t);
                }

                String cfg = formatDiscreteConfig(pm, dps, discVals);

                rows.add(new Row(nodes[y].getName(), cfg, mean, variance, betas));

                wrapped = advanceOdometer(discVals, radices);
            } while (!wrapped);
        }

        fireTableDataChanged();
    }

    /** Apply edited values back into the IM in-place (assumes caller checked consistency). */
    public void applyToIm(HybridCgModel.HybridCgPm pm, HybridCgModel.HybridCgIm im) {
        final Node[] nodes = pm.getNodes();

        for (Row r : rows) {
            // locate child by name
            int y = -1;
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].getName().equals(r.nodeName)) { y = i; break; }
            }
            if (y < 0 || pm.isDiscrete(y)) continue;

            int[] dps = pm.getDiscreteParents(y);
            int[] cps = pm.getContinuousParents(y);

            int[] discVals = parseDiscreteConfig(pm, dps, r.discreteConfig);
            int rowIndex = pm.getRowIndex(y, discVals, null);

            // write intercept/variance
            im.setIntercept(y, rowIndex, r.mean);
            im.setVariance(y, rowIndex, r.variance);

            // parse betas text using either commas or whitespace
            double[] betas = parseBetas(r.betasText);
            for (int t = 0; t < cps.length; t++) {
                double v = (betas != null && t < betas.length) ? betas[t] : 0.0;
                im.setCoefficient(y, rowIndex, t, v);
            }
        }
    }

    // ------------ helpers ------------

    /** Advance a mixed-radix odometer; returns true if it wrapped back to zero (done). */
    private static boolean advanceOdometer(int[] digits, int[] radices) {
        if (digits.length == 0) return true;
        for (int i = digits.length - 1; i >= 0; i--) {
            digits[i]++;
            if (digits[i] < radices[i]) return false;   // normal advance
            digits[i] = 0;                               // carry
        }
        return true; // wrapped
    }

    /** Pretty config like "A=red, B=1" using category labels where available. */
    private static String formatDiscreteConfig(HybridCgModel.HybridCgPm pm, int[] dps, int[] discVals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dps.length; i++) {
            if (i > 0) sb.append(", ");
            Node parent = pm.getNodes()[dps[i]];
            sb.append(parent.getName()).append("=");

            List<String> cats = pm.getCategories(dps[i]);
            int idx = discVals[i];
            if (cats != null && idx >= 0 && idx < cats.size()) {
                sb.append(cats.get(idx));
            } else {
                sb.append(idx);
            }
        }
        return sb.toString();
    }

    /**
     * Parse config like "A=red, B=1" into index array in the PM’s discrete-parent order.
     * Accepts either category names or integer indices; trims whitespace.
     */
    private static int[] parseDiscreteConfig(HybridCgModel.HybridCgPm pm, int[] dps, String cfg) {
        int[] out = new int[dps.length];
        if (cfg == null || cfg.isBlank()) return out;

        // Build a quick map name -> token value
        java.util.Map<String,String> map = new java.util.LinkedHashMap<>();
        String[] pairs = cfg.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=");
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }

        for (int i = 0; i < dps.length; i++) {
            Node parent = pm.getNodes()[dps[i]];
            String v = map.get(parent.getName());
            int idx = 0;
            if (v != null) {
                try { idx = Integer.parseInt(v); }
                catch (NumberFormatException nfe) {
                    List<String> cats = pm.getCategories(dps[i]);
                    if (cats != null) {
                        int found = -1;
                        for (int k = 0; k < cats.size(); k++) {
                            if (cats.get(k).equals(v)) { found = k; break; }
                        }
                        if (found >= 0) idx = found;
                    }
                }
            }
            out[i] = idx;
        }
        return out;
    }

    private static String betasToString(double[] b) {
        if (b == null || b.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.US, "%.6f", b[i]));
        }
        return sb.toString();
    }

    /** Split on commas or whitespace; tolerate empty/extra separators. */
    private static double[] parseBetas(String s) {
        if (s == null || s.isBlank()) return new double[0];
        String[] parts = s.trim().split("[,\\s]+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

    // API for editor
    public void addDiscreteConfig() { /* shape is driven by PM; do nothing */ }
    public void deleteSelectedConfig(int rowIndex) { /* not supported; shape is PM-driven */ }
    public void validateAndNormalize() { /* no-op; editor validates inline */ }
}