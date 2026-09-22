package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * JTable to edit all regression rows (strata) for a continuous child Y in a Hybrid CG IM.
 *
 * Columns:
 *   [discrete parent 1] ... [discrete parent d] | Mean | (coeffs for continuous parents) | Variance
 *
 * Rows:
 *   One per combination of discrete-parent categories (pm.getNumRows(y)).
 */
final class HybridCgRegEditingTable extends JTable {

    private static final DecimalFormat DF3 = new DecimalFormat("0.###");

    /** Foreground for coefficient cells whose per-stratum t-test misses the level. */
    private static final Color NOT_SIGNIFICANT = new Color(0x9E9E9E);

    /** Text shown in place of a NaN parameter, which means the row was never fitted. */
    private static final String UNESTIMATED = "not estimated";

    /**
     * Per-stratum t-test p-values for the coefficient columns, indexed [stratum row][continuous-parent order index]
     * as returned by {@code HybridCgEdgeSignificance.coefficientPValues} for this child; null for no overlay.
     */
    private double[][] coefPValues;

    /** The level the overlay greys against. */
    private double sigAlpha = 0.05;

    HybridCgRegEditingTable(HybridCgIm im, HybridCgPm pm, int yIndex) {
        setModel(new Model(im, pm, yIndex));

        // Renderers/editors for doubles. A NaN parameter is not a number the user should read as an
        // estimate; it means the row was never fitted, so say so rather than printing "NaN".
        setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (value instanceof Number n && Double.isNaN(n.doubleValue())) setText(UNESTIMATED);
                else if (value instanceof Number n) setText(DF3.format(n.doubleValue()));
                else super.setValue(value);
            }
        });

        setDefaultEditor(Double.class, new HybridCgImEditor.DoubleCellEditor("0.###"));

        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setRowHeight(22);
        getTableHeader().setReorderingAllowed(false);

        // Reasonable initial column widths: parents compact, params wider, variance moderate
        SwingUtilities.invokeLater(() -> {
            var fm = getFontMetrics(getFont());
            int pad = 24;
            Model m = (Model) getModel();
            int d = m.discParents.size();
            int mcoeff = m.contParents.size();

            for (int c = 0; c < getColumnCount(); c++) {
                int w;
                if (c < d) {
                    // discrete parent label width
                    String maxLabel = m.maxParentLabelWidthSample(c);
                    w = TMath.max(80, fm.stringWidth(maxLabel) + pad);
                } else if (c == d) {
                    w = TMath.max(fm.stringWidth("mean"), fm.stringWidth(UNESTIMATED)) + pad;
                } else if (c == d + 1 + mcoeff) {
                    w = TMath.max(fm.stringWidth("Variance"), fm.stringWidth(UNESTIMATED)) + pad;
                } else {
                    // coefficient columns
                    w = TMath.max(100, TMath.max(fm.stringWidth(getColumnName(c)),
                            fm.stringWidth(UNESTIMATED)) + pad);
                }
                getColumnModel().getColumn(c).setPreferredWidth(w);
            }
        });
    }

    /**
     * Sets or clears the significance overlay. When set, coefficient cells whose per-stratum t-test p-value exceeds
     * {@code alpha} are drawn grey, and hovering any coefficient cell shows its p-value; cells whose test is
     * unavailable (NaN) are drawn normally with a tooltip saying so. The mean and variance columns are not edges and
     * are never greyed.
     *
     * @param coefPValues p-values indexed [stratum row][continuous-parent order index], or null to clear
     * @param alpha       the level to grey against
     */
    void setSignificance(double[][] coefPValues, double alpha) {
        this.coefPValues = coefPValues;
        this.sigAlpha = alpha;
        repaint();
    }

    /** True if the column holds a coefficient for a continuous parent. */
    private boolean isCoefficientColumn(int col) {
        Model m = (Model) getModel();
        int d = m.discParents.size();
        return col > d && col < d + 1 + m.contParents.size();
    }

    /** The p-value for a coefficient cell, or NaN if none is available. */
    private double pValueAt(int row, int col) {
        if (this.coefPValues == null || !isCoefficientColumn(col)) return Double.NaN;
        Model m = (Model) getModel();
        int j = col - (m.discParents.size() + 1);
        if (row < 0 || row >= this.coefPValues.length) return Double.NaN;
        double[] rowP = this.coefPValues[row];
        return (rowP == null || j >= rowP.length) ? Double.NaN : rowP[j];
    }

    /** True if the column holds a fitted parameter: mean, a coefficient, or the variance. */
    private boolean isParameterColumn(int col) {
        return col >= ((Model) getModel()).discParents.size();
    }

    /** True if the parameter cell holds NaN, meaning the row was never fitted. */
    private boolean isUnestimated(int row, int col) {
        if (row < 0 || col < 0 || !isParameterColumn(col)) return false;
        Object v = getModel().getValueAt(row, col);
        return v instanceof Number n && Double.isNaN(n.doubleValue());
    }

    @Override
    public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int col) {
        Component c = super.prepareRenderer(renderer, row, col);
        if (!isCellSelected(row, col)) {
            int mRow = convertRowIndexToModel(row);
            int mCol = convertColumnIndexToModel(col);
            double p = pValueAt(mRow, mCol);
            boolean grey = isUnestimated(mRow, mCol)
                           || (this.coefPValues != null && !Double.isNaN(p) && p > this.sigAlpha);
            c.setForeground(grey ? NOT_SIGNIFICANT : getForeground());
        }
        return c;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        int row = rowAtPoint(event.getPoint());
        int col = columnAtPoint(event.getPoint());
        if (row < 0 || col < 0) return super.getToolTipText(event);

        int mRow = convertRowIndexToModel(row);
        int mCol = convertColumnIndexToModel(col);

        if (isUnestimated(mRow, mCol)) {
            Model m = (Model) getModel();
            int n = m.im.getRowCaseCount(m.y, mRow);
            if (n == 0) {
                return "Not estimated: no complete cases for this combination of discrete parents. "
                       + "A case counts only if the child and all of its parents are observed.";
            }
            if (n > 0) {
                int needed = m.contParents.size() + 2;
                return "Not estimated: " + n + (n == 1 ? " complete case" : " complete cases")
                       + " for this combination of discrete parents, and " + needed
                       + " are needed before a residual variance can be estimated.";
            }
            return "Not estimated for this combination of discrete parents.";
        }

        if (this.coefPValues != null && isCoefficientColumn(mCol)) {
            double p = pValueAt(mRow, mCol);
            if (Double.isNaN(p)) {
                return "No t-test for this stratum (insufficient cases or singular design).";
            }
            return String.format("Per-stratum t-test p = %.4g%s", p,
                    p > this.sigAlpha ? String.format(" (not significant at %.3g)", this.sigAlpha) : "");
        }
        return super.getToolTipText(event);
    }

    // ---------------- Table Model ----------------

    static final class Model extends AbstractTableModel {
        private final HybridCgIm im;
        private final HybridCgPm pm;
        private final int y;

        // parents
        final List<Integer> discParents = new ArrayList<>();
        final List<Integer> contParents = new ArrayList<>();

        // per-disc-parent: category labels
        final List<List<String>> discParentCategories = new ArrayList<>();
        // row decoding
        final int[] dims;     // cardinalities of each discrete parent (in discParents order)
        final int[] strides;  // mixed-radix strides

        Model(HybridCgIm im, HybridCgPm pm, int y) {
            this.im = im; this.pm = pm; this.y = y;

            // Partition parents
            int[] allParents = pm.getParents(y);
            for (int p : allParents) {
                if (pm.isDiscrete(p)) discParents.add(p);
                else contParents.add(p);
            }

            // Build category names for discrete parents
            for (int p : discParents) {
                discParentCategories.add(pm.getCategories(p));
            }

            // Build dims/strides for row decoding
            if (discParents.isEmpty()) {
                dims = new int[0];
                strides = new int[0];
            } else {
                dims = new int[discParents.size()];
                for (int i = 0; i < dims.length; i++) dims[i] = pm.getCardinality(discParents.get(i));
                strides = new int[dims.length];
                int s = 1;
                for (int i = dims.length - 1; i >= 0; i--) {
                    strides[i] = s;
                    s *= dims[i];
                }
            }
        }

        // Mixed-radix decode: row -> parent value indices
        private int[] decodeRow(int row) {
            int[] vals = new int[dims.length];
            int r = row;
            for (int i = 0; i < dims.length; i++) {
                vals[i] = (r / strides[i]) % dims[i];
            }
            return vals;
        }

        @Override public int getRowCount() {
            return pm.getNumRows(y);
        }

        @Override public int getColumnCount() {
            // [d discrete parents] + [mean] + [cont coeffs] + [Variance]
            return discParents.size() + 1 + contParents.size() + 1;
        }

        @Override public String getColumnName(int col) {
            int d = discParents.size();
            if (col < d) {
                return pm.getNodes()[discParents.get(col)].getName();
            } else if (col == d) {
                return "Mean";
            } else if (col == d + 1 + contParents.size()) {
                return "Variance";
            } else {
                // coefficient for a continuous parent
                int j = col - (d + 1);
                return pm.getNodes()[contParents.get(j)].getName();
            }
        }

        @Override public Class<?> getColumnClass(int col) {
            return (col < discParents.size()) ? String.class : Double.class;
        }

        @Override public boolean isCellEditable(int row, int col) {
            return col >= discParents.size();
        }

        @Override public Object getValueAt(int row, int col) {
            int d = discParents.size();
            if (col < d) {
                if (d == 0) return ""; // no discrete parents
                int[] vals = decodeRow(row);
                var labels = discParentCategories.get(col);
                int idx = vals[col];
                return (idx >= 0 && idx < labels.size()) ? labels.get(idx) : "";
            } else if (col == d) {
                return im.getMean(y, row);
            } else if (col == d + 1 + contParents.size()) {
                return im.getVariance(y, row);
            } else {
                int j = col - (d + 1);
                return im.getCoefficient(y, row, j);
            }
        }

        @Override public void setValueAt(Object aValue, int row, int col) {
            int d = discParents.size();
            if (col < d) return; // read-only labels

            double v;
            try { v = Double.parseDouble(String.valueOf(aValue).trim()); }
            catch (Exception e) { v = Double.NaN; }

            if (col == d) {
                if (Double.isFinite(v)) im.setMean(y, row, v);
            } else if (col == d + 1 + contParents.size()) {
                if (!Double.isFinite(v) || v <= 0) v = 1e-12;
                im.setVariance(y, row, v);
            } else {
                int j = col - (d + 1);
                if (Double.isFinite(v)) im.setCoefficient(y, row, j, v);
            }
            fireTableCellUpdated(row, col);
        }

        /** A simple label sample used to size discrete-parent columns. */
        String maxParentLabelWidthSample(int discCol) {
            String parentName = pm.getNodes()[discParents.get(discCol)].getName();
            // pick the longest category label for this parent (fallback parentName)
            List<String> cats = discParentCategories.get(discCol);
            String longest = parentName;
            for (String s : cats) if (s.length() > longest.length()) longest = s;
            return longest;
        }
    }

    // inside HybridCgImEditor (at class level, alongside your other inner classes/utilities)

    /**
     * Simple reusable Double cell editor with number formatting.
     */
    public static final class DoubleCellEditor extends DefaultCellEditor {
        private final DecimalFormat fmt;

        public DoubleCellEditor(String pattern) {
            super(new JTextField());
            this.fmt = new DecimalFormat(pattern);
            JTextField tf = (JTextField) getComponent();
            tf.setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Object getCellEditorValue() {
            String s = ((JTextField)getComponent()).getText().trim();
            try {
                return Double.valueOf(s);
            } catch (Exception ex) {
                return Double.NaN;
            }
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            String text = "";
            if (value instanceof Number n) {
                text = fmt.format(n.doubleValue());
            } else if (value != null) {
                text = value.toString();
            }
            ((JTextField)getComponent()).setText(text);
            return getComponent();
        }
    }
}