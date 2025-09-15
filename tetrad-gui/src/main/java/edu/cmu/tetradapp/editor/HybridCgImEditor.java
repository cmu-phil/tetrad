package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.Objects;

public class HybridCgImEditor extends JPanel {

    private final JTable table;
    private final HybridCgImNodeEditingTable model;

    // Keep a handle to the underlying PM/IM so we can roundtrip
    private final HybridCgModel.HybridCgPm pm;
    private final HybridCgModel.HybridCgIm im;

    private String name = "Hybrid CG IM Editor";

    // at top of class
    private boolean applyingModelChange = false;

    /**
     * 🔧 Reflection-friendly ctor: accepts the IM wrapper.
     */
    public HybridCgImEditor(edu.cmu.tetradapp.model.HybridCgImWrapper imWrapper) {
        Objects.requireNonNull(imWrapper, "imWrapper");
        this.im = imWrapper.getHybridCgIm();
        this.pm = im.getPm();

        setLayout(new BorderLayout());

        // Instructions
        JLabel instructions = new JLabel(
                "<html><body style='padding:6px'>"
                + "<b>How to use:</b> Edit <i>Mean</i>, <i>Variance</i>, or <i>Betas</i> directly.<br>"
                + "Numbers are formatted with the project’s number format. "
                + "Edits are applied immediately.<br>"
                + "Invalid entries are highlighted in "
                + "<span style='color:#b00020'>red</span> and are not applied."
                + "</body></html>"
        );
        instructions.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(instructions, BorderLayout.NORTH);

        this.model = new HybridCgImNodeEditingTable();
        this.table = new JTable(model);
        this.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.table.setRowSelectionAllowed(true);
        this.table.setColumnSelectionAllowed(true);
        this.table.setCellSelectionEnabled(true);
        this.table.setAutoCreateRowSorter(true);

        // NumberFormat & formatter factory
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        final NumberFormatter displayFmt = new NumberFormatter(nf);
        displayFmt.setValueClass(Double.class);
        displayFmt.setAllowsInvalid(false);
        displayFmt.setCommitsOnValidEdit(true);

        final NumberFormatter editFmt = new NumberFormatter(nf);
        editFmt.setValueClass(Double.class);
        editFmt.setAllowsInvalid(false);
        editFmt.setCommitsOnValidEdit(true);

        DefaultFormatterFactory factory =
                new DefaultFormatterFactory(displayFmt, displayFmt, editFmt);

        // Use custom formatted editors for Mean & Variance
        TableColumn meanCol = table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_MEAN);
        TableColumn varCol = table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_VAR);

        meanCol.setCellEditor(new FormattedNumberCellEditor(factory));
        varCol.setCellEditor(new FormattedNumberCellEditor(factory));

        // Editor for Betas: plain text, validated on commit (renderer shows red when wrong length)
        JTextField betasField = new JTextField();
        DefaultCellEditor betasEditor = new DefaultCellEditor(betasField);
        table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_BETA).setCellEditor(betasEditor);

        // Renderers: format numbers and color invalid cells red
        TableCellRenderer defaultRenderer = table.getDefaultRenderer(Object.class);
        table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_MEAN)
                .setCellRenderer(new NumberFormatRenderer(nf, defaultRenderer, pm, model));
        table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_VAR)
                .setCellRenderer(new NumberFormatRenderer(nf, defaultRenderer, pm, model));
        table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_BETA)
                .setCellRenderer(new BetasRenderer(nf, defaultRenderer, pm, model));

        // Column sizing
        SwingUtilities.invokeLater(() -> {
            autoSizeToContent(table, HybridCgImNodeEditingTable.COL_NODE, 16, 16, 2.0);
            autoSizeToContent(table, HybridCgImNodeEditingTable.COL_CFG, 16, 16, 2.0);
            table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_MEAN).setPreferredWidth(120);
            table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_VAR).setPreferredWidth(120);
            table.getColumnModel().getColumn(HybridCgImNodeEditingTable.COL_BETA).setPreferredWidth(380);
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Initial load
        model.populateFrom(pm, im);

        // Immediate apply on any model change
        model.addTableModelListener(e -> {
            SwingUtilities.invokeLater(() -> {
                if (applyingModelChange) return;
                applyingModelChange = true;
                try {
                    if (table.isEditing()) {
                        try {
                            table.getCellEditor().stopCellEditing();
                        } catch (Exception ignore) {
                        }
                    }
                    if (tableIsConsistent(pm, model)) {
                        model.applyToIm(pm, im);
                        HybridCgImEditor.this.firePropertyChange("modelChanged", false, true);
                    }
                    table.repaint();
                } finally {
                    applyingModelChange = false;
                }
            });
        });

        setPreferredSize(new Dimension(800, 400));
    }

    /**
     * Optional no-arg ctor (kept if other parts instantiate it directly)
     */
    public HybridCgImEditor() {
        // Minimal/disconnected editor; caller should not use without set/populate
        this.pm = null;
        this.im = null;

        setLayout(new BorderLayout());
        JLabel msg = new JLabel("No IM attached.");
        msg.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(msg, BorderLayout.NORTH);

        this.model = new HybridCgImNodeEditingTable();
        this.table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Basic auto-size: header/content measure with padding; clamp a bit.
     */
    private static void autoSizeToContent(JTable table, int col, int padPx, int minPadPx, double clamp) {
        TableColumn column = table.getColumnModel().getColumn(col);
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        Component headerComp = headerRenderer.getTableCellRendererComponent(
                table, column.getHeaderValue(), false, false, 0, col);
        int max = headerComp.getPreferredSize().width;

        for (int r = 0; r < Math.min(table.getRowCount(), 200); r++) { // guard against huge tables
            TableCellRenderer cellRenderer = table.getCellRenderer(r, col);
            Component comp = table.prepareRenderer(cellRenderer, r, col);
            max = Math.max(max, comp.getPreferredSize().width);
        }
        int pref = (int) Math.min(max * clamp + padPx, 600);
        column.setPreferredWidth(Math.max(pref, max + minPadPx));
    }

    /**
     * Check if current table values would be acceptable for apply.
     */
    private static boolean tableIsConsistent(HybridCgModel.HybridCgPm pm, HybridCgImNodeEditingTable model) {
        int rows = model.getRowCount();
        for (int r = 0; r < rows; r++) {
            // Variance must be positive
            Object varObj = model.getValueAt(r, HybridCgImNodeEditingTable.COL_VAR);
            double varVal;
            try {
                varVal = (varObj instanceof Number n) ? n.doubleValue()
                        : Double.parseDouble(String.valueOf(varObj));
            } catch (Exception ex) {
                return false;
            }
            if (!(varVal > 0)) return false;

            // Betas count must match #continuous parents
            String nodeName = String.valueOf(model.getValueAt(r, HybridCgImNodeEditingTable.COL_NODE));
            int expected = BetasRenderer.expectedBetaCount(pm, nodeName);
            String betas = String.valueOf(model.getValueAt(r, HybridCgImNodeEditingTable.COL_BETA));
            int actual = BetasRenderer.countBetas(betas);
            if (actual != expected) return false;
        }
        return true;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = (n == null ? "Hybrid CG IM Editor" : n);
    }

    public JDialog getWizard(Window owner) {
        return HybridCgImEditorWizard.create(owner, this);
    }

    // ======= helpers =======

    public HybridCgModel.HybridCgIm retrieveHybridCgIm() {
        return im;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) { /* no-op for now */ }

    /**
     * Format numeric cells and color invalid ones red.
     */
    private static final class NumberFormatRenderer extends DefaultTableCellRenderer {
        private final NumberFormat nf;
        private final TableCellRenderer fallback;
        private final HybridCgModel.HybridCgPm pm;
        private final HybridCgImNodeEditingTable model;

        NumberFormatRenderer(NumberFormat nf, TableCellRenderer fallback,
                             HybridCgModel.HybridCgPm pm,
                             HybridCgImNodeEditingTable model) {
            this.nf = nf;
            this.fallback = fallback;
            this.pm = pm;
            this.model = model;
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Format
            String text;
            if (value instanceof Number num) text = nf.format(num.doubleValue());
            else text = String.valueOf(value);
            setText(text);

            // Invalid coloring (Variance <= 0)
            if (!isSelected) {
                if (column == HybridCgImNodeEditingTable.COL_VAR) {
                    try {
                        double var = (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
                        setForeground(var > 0 ? Color.BLACK : new Color(0xB00020));
                    } catch (Exception ex) {
                        setForeground(new Color(0xB00020));
                    }
                } else {
                    setForeground(Color.BLACK);
                }
            }
            return c;
        }
    }

    /**
     * Formats betas with project number format; colors red when count ≠ #continuous parents.
     */
    private static final class BetasRenderer extends DefaultTableCellRenderer {
        private final NumberFormat nf;
        private final TableCellRenderer fallback;
        private final HybridCgModel.HybridCgPm pm;
        private final HybridCgImNodeEditingTable model;

        BetasRenderer(NumberFormat nf, TableCellRenderer fallback,
                      HybridCgModel.HybridCgPm pm,
                      HybridCgImNodeEditingTable model) {
            this.nf = nf;
            this.fallback = fallback;
            this.pm = pm;
            this.model = model;
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        private static String reformatBetasString(String s, NumberFormat nf) {
            if (s == null || s.isBlank()) return "";
            // split on one-or-more commas or whitespace
            String[] parts = s.trim().split("[,\\s]+");
            StringBuilder out = new StringBuilder();
            for (String t : parts) {
                if (t.isEmpty()) continue;
                try {
                    double v = Double.parseDouble(t);
                    if (out.length() > 0) out.append(", ");
                    out.append(nf.format(v));
                } catch (NumberFormatException nfe) {
                    if (out.length() > 0) out.append(", ");
                    out.append(t); // show as-is; will still be validated elsewhere
                }
            }
            return out.toString();
        }

        private static int countBetas(String s) {
            if (s == null || s.isBlank()) return 0;
            int count = 0;
            for (String p : s.trim().split("[,\\s]+")) {
                if (!p.isBlank()) count++;
            }
            return count;
        }

        private static int expectedBetaCount(HybridCgModel.HybridCgPm pm, String nodeName) {
            if (pm == null || nodeName == null) return 0;
            var nodes = pm.getNodes();
            for (int i = 0; i < nodes.length; i++) {
                if (nodeName.equals(nodes[i].getName())) {
                    if (pm.isDiscrete(i)) return 0;
                    return pm.getContinuousParents(i).length;
                }
            }
            return 0;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Re-format betas string using NumberFormat (we have only the String via model.getValueAt)
            String s = String.valueOf(value);
            String formatted = reformatBetasString(s, nf);
            setText(formatted);

            // Validate count vs PM: find node & expected cps length
            String nodeName = String.valueOf(table.getValueAt(row, HybridCgImNodeEditingTable.COL_NODE));
            int expected = expectedBetaCount(pm, nodeName);
            int actual = countBetas(formatted);

            if (!isSelected) {
                setForeground(actual == expected ? Color.BLACK : new Color(0xB00020));
            }
            return c;
        }
    }

    /**
     * A number cell editor that keeps display/edit formats in sync.
     */
    private static final class FormattedNumberCellEditor extends DefaultCellEditor {
        private final JFormattedTextField field;

        FormattedNumberCellEditor(DefaultFormatterFactory factory) {
            super(new JFormattedTextField());
            this.field = (JFormattedTextField) getComponent();
            this.field.setHorizontalAlignment(SwingConstants.RIGHT);
            this.field.setFormatterFactory(factory);
            // Make edits commit or revert automatically on focus changes
            this.field.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
            // Optional: don’t beep on invalid keypress
            this.field.setInputVerifier(null);
            // Start editing on single click if you like:
            setClickCountToStart(1);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            // Put the *value* (Number) into the field so the formatter formats it for edit view.
            if (value instanceof Number n) {
                field.setValue(n.doubleValue());
            } else if (value != null) {
                // best effort parse → shows formatted or stays as text if it fails
                try {
                    field.setValue(Double.valueOf(value.toString()));
                } catch (Exception ignore) {
                    field.setValue(null);
                    field.setText(value.toString());
                }
            } else {
                field.setValue(null);
            }
            return field;
        }

        @Override
        public Object getCellEditorValue() {
            Object v = field.getValue();
            if (v instanceof Number n) return n.doubleValue();
            try {
                return Double.valueOf(field.getText().trim());
            } catch (Exception e) {
                return null;
            } // model can ignore if null
        }

        @Override
        public boolean stopCellEditing() {
            try {
                field.commitEdit(); // forces parse → value
            } catch (Exception ignore) { /* fall through; COMMIT_OR_REVERT handles */ }
            return super.stopCellEditing();
        }
    }
}