package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Bayes-style CPT editor JTable for a DISCRETE child in a Hybrid CG IM.
 * Left columns = parent assignments (discrete categories + continuous-bin labels),
 * Right columns = editable probabilities for the child's categories.
 */
final class HybridCgCptEditingTable extends JTable {

    private final Model model;
    private int lastX, lastY;

    HybridCgCptEditingTable(Node child, HybridCgIm im) {
        HybridCgPm pm = im.getPm();
        int y = pm.indexOf(child);
        if (y < 0) throw new IllegalArgumentException("Child not in PM: " + child);
        if (!pm.isDiscrete(y)) throw new IllegalArgumentException("Child is not discrete: " + child.getName());

        this.model = new Model(im, pm, y);
        setModel(model);

        setDefaultRenderer(Double.class, new NumberCellRenderer("0.###"));
        setDefaultEditor(Double.class,   new NumberCellEditor("0.###"));
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);
        getTableHeader().setReorderingAllowed(false);

        // Make sure columns are at least wide enough for headers
        SwingUtilities.invokeLater(this::adjustHeaderWidths);

        // Context menu
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                    lastX = e.getX(); lastY = e.getY();
                    showPopup(e);
                }
            }
        });
    }

    private void adjustHeaderWidths() {
        FontMetrics fm = getFontMetrics(getFont());
        for (int c = 0; c < getColumnCount(); c++) {
            TableColumn col = getColumnModel().getColumn(c);
            String name = getColumnName(c);
            int min = fm.stringWidth(name == null ? "" : name) + 18;
            if (col.getPreferredWidth() < min) col.setPreferredWidth(min);
        }
    }

    private void showPopup(MouseEvent e) {
        int rowAtPoint = rowAtPoint(e.getPoint());
        if (rowAtPoint < 0) rowAtPoint = 0;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem normRow  = new JMenuItem("Normalize this row");
        JMenuItem normAll  = new JMenuItem("Normalize all rows");
        JMenuItem randRow  = new JMenuItem("Randomize this row");
        JMenuItem randAll  = new JMenuItem("Randomize whole table");
        JMenuItem clearRow = new JMenuItem("Clear this row");
        JMenuItem clearAll = new JMenuItem("Clear whole table");

        final int r = rowAtPoint;

        normRow.addActionListener(ev -> { model.normalizeRow(r);  model.fireTableRowsUpdated(r, r); });
        normAll.addActionListener(ev -> { model.normalizeAll();   model.fireTableDataChanged(); });
        randRow.addActionListener(ev -> { model.randomizeRow(r);  model.fireTableRowsUpdated(r, r); });
        randAll.addActionListener(ev -> { model.randomizeAll();   model.fireTableDataChanged(); });
        clearRow.addActionListener(ev -> { model.clearRow(r);     model.fireTableRowsUpdated(r, r); });
        clearAll.addActionListener(ev -> { model.clearAll();      model.fireTableDataChanged(); });

        menu.add(normRow); menu.add(normAll);
        menu.add(randRow); menu.add(randAll);
        menu.addSeparator();
        menu.add(clearRow); menu.add(clearAll);

        menu.show(this, lastX, lastY);
    }

    // ----------------------------------------------------------------------

    private static final class Model extends AbstractTableModel {
        private final HybridCgIm im;
        private final HybridCgPm pm;
        private final int y;                      // child index
        private final List<String> childCats;     // child category labels
        private final int[] discParents;          // indices in PM
        private final int[] contParents;          // indices in PM
        private final String[] parentColNames;    // header labels (parents then categories)
        private final int[] dims;                 // mixed-radix dims for row decoding (parents only)
        private final double[][] contEdges;       // per-cont-parent edges (for labels), may be null

        private final DecimalFormat fmt = new DecimalFormat("0.###");

        Model(HybridCgIm im, HybridCgPm pm, int y) {
            this.im = im;
            this.pm = pm;
            this.y  = y;

            this.childCats = pm.getCategories(y);

            this.discParents = pm.getDiscreteParents(y);
            this.contParents = pm.getContinuousParents(y);

            // pull cutpoints for continuous parents (in PM's order)
            double[][] edges = null;
            if (contParents.length > 0) {
                var opt = pm.getContParentCutpointsForDiscreteChild(y);
                edges = opt.orElseGet(() -> new double[contParents.length][]);
            }
            this.contEdges = edges;

            // Build dims in the PM's “row” order: all discrete parents, then cont bins
            this.dims = new int[discParents.length + contParents.length];
            for (int i = 0; i < discParents.length; i++) dims[i] = pm.getCardinality(discParents[i]);
            for (int j = 0; j < contParents.length; j++) {
                int bins = (contEdges != null && contEdges[j] != null) ? contEdges[j].length + 1 : 1;
                dims[discParents.length + j] = Math.max(1, bins);
            }

            // Build column headers (parents followed by child category columns)
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < discParents.length; i++) headers.add(pm.getNodes()[discParents[i]].getName());
            for (int j = 0; j < contParents.length; j++) {
                headers.add(pm.getNodes()[contParents[j]].getName());
            }
            for (String cat : childCats) headers.add(pm.getNodes()[y].getName() + "=" + cat);
            this.parentColNames = headers.toArray(new String[0]);
        }

        // ---------- table shape ----------
        @Override public int getRowCount()    { return pm.getNumRows(y); }
        @Override public int getColumnCount() { return dims.length + childCats.size(); }
        @Override public String getColumnName(int c) { return parentColNames[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return (c < dims.length) ? Object.class : Double.class;
        }
        @Override public boolean isCellEditable(int r, int c) { return c >= dims.length; }

        // ---------- data ----------
        @Override public Object getValueAt(int row, int col) {
            int[] parentAssign = decode(row, dims); // indices per parent column

            if (col < dims.length) {
                // Parent label
                if (col < discParents.length) {
                    int pY = discParents[col];
                    String lab = pm.getCategories(pY).get(parentAssign[col]);
                    return lab;
                } else {
                    int j = col - discParents.length; // cont parent index in contParents
                    int pY = contParents[j];
                    return binLabel(pm.getNodes()[pY].getName(), parentAssign[col], contEdges[j]);
                }
            } else {
                int k = col - dims.length; // child category index
                return im.getProbability(y, row, k);
            }
        }

        @Override public void setValueAt(Object aValue, int row, int col) {
            if (col < dims.length) return;
            double v;
            if (aValue instanceof Number n) v = n.doubleValue();
            else {
                try { v = Double.parseDouble(String.valueOf(aValue).trim()); }
                catch (Exception ex) { v = Double.NaN; }
            }
            if (!Double.isFinite(v) || v < 0) v = 0.0;
            int k = col - dims.length;
            im.setProbability(y, row, k, v);
            fireTableCellUpdated(row, col);
        }

        // ---------- helpers ----------
        void normalizeRow(int row) {
            int K = childCats.size();
            double s = 0.0;
            for (int k = 0; k < K; k++) s += Math.max(0.0, im.getProbability(y, row, k));
            if (s <= 0) { // make uniform
                for (int k = 0; k < K; k++) im.setProbability(y, row, k, 1.0 / K);
            } else {
                for (int k = 0; k < K; k++) im.setProbability(y, row, k, Math.max(0.0, im.getProbability(y, row, k)) / s);
            }
        }
        void normalizeAll() {
            for (int r = 0; r < getRowCount(); r++) normalizeRow(r);
        }
        void randomizeRow(int row) {
            int K = childCats.size();
            double[] e = new double[K]; double s = 0;
            for (int k = 0; k < K; k++) { e[k] = -Math.log(1.0 - Math.random()); s += e[k]; }
            for (int k = 0; k < K; k++) im.setProbability(y, row, k, e[k] / s);
        }
        void randomizeAll() { for (int r = 0; r < getRowCount(); r++) randomizeRow(r); }
        void clearRow(int row) { for (int k = 0; k < childCats.size(); k++) im.setProbability(y, row, k, 0.0); }
        void clearAll() { for (int r = 0; r < getRowCount(); r++) clearRow(r); }

        private static int[] decode(int index, int[] dims) {
            // mixed-radix decode (least-significant = last dimension)
            int[] out = new int[dims.length];
            for (int i = dims.length - 1; i >= 0; i--) {
                int d = Math.max(1, dims[i]);
                out[i] = index % d;
                index /= d;
            }
            return out;
        }

        private String binLabel(String pname, int bin, double[] edges) {
            if (edges == null || edges.length == 0) return pname + " bin " + (bin + 1);
            double lo = (bin == 0) ? Double.NEGATIVE_INFINITY : edges[bin - 1];
            double hi = (bin == edges.length) ? Double.POSITIVE_INFINITY : edges[bin];
            String slo = Double.isInfinite(lo) ? "(-∞" : "[" + fmt.format(lo);
            String shi = Double.isInfinite(hi) ? "∞)"  : fmt.format(hi) + ")";
            return pname + " " + slo + ", " + shi;
        }
    }

    // ----- tiny render/editor helpers -----

    private static final class NumberCellRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat fmt;
        NumberCellRenderer(String pattern) { this.fmt = new DecimalFormat(pattern); setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object value) {
            if (value instanceof Number n) setText(fmt.format(n.doubleValue()));
            else super.setValue(value);
        }
    }

    private static final class NumberCellEditor extends DefaultCellEditor {
        private final DecimalFormat fmt;
        NumberCellEditor(String pattern) { super(new JTextField()); this.fmt = new DecimalFormat(pattern); }
        @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int r, int c) {
            ((JTextField)getComponent()).setHorizontalAlignment(SwingConstants.RIGHT);
            ((JTextField)getComponent()).setText(v instanceof Number n ? fmt.format(n.doubleValue()) : "");
            return getComponent();
        }
        @Override public Object getCellEditorValue() {
            String s = ((JTextField)getComponent()).getText().trim();
            try { return Double.valueOf(s); } catch (Exception e) { return 0.0; }
        }
    }
}