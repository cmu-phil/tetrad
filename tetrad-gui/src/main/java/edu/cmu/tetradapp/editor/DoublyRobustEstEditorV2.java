package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.DoublyRobustEstModelV2;
import edu.cmu.tetradapp.model.DoublyRobustEstModelV2.ResultRowV2;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * v2: Editor panel for "Mixed DR Adjustment Effect".
 *
 * Mirrors the interface style of LinearAdjustmentTotalEffectsEditor:
 *  - PAIRWISE vs JOINT
 *  - wildcard/list parsing for X and Y
 *  - "Compute adjustment sets and effects"
 *  - simple table rows
 *  - "View Details..." dialog (copy/paste friendly) for a selected row
 *
 * v2.1:
 *  - adds "Binarize..." button for derived binary treatments (stored in model)
 *  - table includes a "Note" column and is safe for rows where x/y are null
 *  - "View details..." enabled only for OK rows
 */
public final class DoublyRobustEstEditorV2 extends JPanel {

    private final DoublyRobustEstModelV2 model;
    private final Graph graph;

//    private final JRadioButton pairwiseRadio =
//            new JRadioButton("ATE for all X–Y pairs (binary X only)");
//    private final JRadioButton jointRadio =
//            new JRadioButton("Joint conditioning: p(Y | joint(X)) (v2 supports |X|=1)");

    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();

    private final JButton runButton = new JButton("Compute adjustment sets and ATE (DR)");
    private final JButton paramsButton = new JButton("Edit parameters...");
    private final JButton viewDetailsButton = new JButton("View details...");

    private final JTable resultTable;
    private final DoublyRobustEstResultTableModelV2 tableModel;

    private final DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer() {
        {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value == null) {
                setText("");
                return;
            }
            if (value instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    setText("");
                    return;
                }
                setText(NumberFormatUtil.getInstance().getNumberFormat().format(d));
                return;
            }
            setText(String.valueOf(value));
        }
    };

    public DoublyRobustEstEditorV2(DoublyRobustEstModelV2 model) {
        this.model = Objects.requireNonNull(model, "model");
        this.graph = Objects.requireNonNull(model.getGraph(), "graph");

        this.tableModel = new DoublyRobustEstResultTableModelV2(this.model);
        this.resultTable = new JTable(this.tableModel);

        this.resultTable.setFillsViewportHeight(true);
        this.resultTable.setTransferHandler(new DefaultTableTransferHandler(0));
        this.resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        this.resultTable.setAutoCreateRowSorter(true);

        installSorter();

        treatmentsField.setText(model.getTreatmentsText());
        outcomesField.setText(model.getOutcomesText());

        treatmentsField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                model.setTreatmentsText(treatmentsField.getText());
            }
        });

        outcomesField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                model.setOutcomesText(outcomesField.getText());
            }
        });

        initUI();
        initListeners();
        installRenderers();
        updateViewDetailsEnabled();
    }

    // ------------------------
    // UI
    // ------------------------

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

//        ButtonGroup modeGroup = new ButtonGroup();
//        modeGroup.add(pairwiseRadio);
//        modeGroup.add(jointRadio);
//
//        if (model.getEffectMode() == DoublyRobustEstModelV2.EffectMode.JOINT) jointRadio.setSelected(true);
//        else pairwiseRadio.setSelected(true);

//        JPanel modePanel = new JPanel(new GridLayout(0, 1));
//        modePanel.add(new JLabel("Mode:"));

        JPanel modePanel = new JPanel(new BorderLayout());
        modePanel.add(new JLabel(
                "<html><b>Mode:</b> Average treatment effects computed for all X–Y pairs (binary X only).</html>"
        ), BorderLayout.CENTER);

        topPanel.add(modePanel, BorderLayout.NORTH);

//        modePanel.add(pairwiseRadio);
//        modePanel.add(jointRadio);
        topPanel.add(modePanel, BorderLayout.NORTH);

        JPanel xyPanel = new JPanel(new GridLayout(2, 2, 5, 5));

        JButton binarizeButton = new JButton("Binarize...");

        JPanel xRow = new JPanel(new BorderLayout(5, 0));
        xRow.add(treatmentsField, BorderLayout.CENTER);
        xRow.add(binarizeButton, BorderLayout.EAST);

        xyPanel.add(new JLabel("Treatments (X):"));
        xyPanel.add(xRow);

        xyPanel.add(new JLabel("Outcomes (Y):"));
        xyPanel.add(outcomesField);

        topPanel.add(xyPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(paramsButton);
        buttonPanel.add(viewDetailsButton);

        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // hook binarize
        binarizeButton.addActionListener(e -> onBinarize());
    }

    // ------------------------
    // Listeners
    // ------------------------

    private void initListeners() {
        runButton.addActionListener(this::onRun);
        paramsButton.addActionListener(this::onEditParams);
        viewDetailsButton.addActionListener(this::onViewDetails);

        ListSelectionModel sel = resultTable.getSelectionModel();
        sel.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                updateViewDetailsEnabled();
            }
        });
    }

    private void onRun(ActionEvent e) {
        try {
            updateModelFromUI();
            model.recomputeAsync(() -> {
                tableModel.fireTableDataChanged();
                updateViewDetailsEnabled();
            });

            installRenderers();
            updateViewDetailsEnabled();
            installSorter();
            tableModel.fireTableDataChanged();
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid selection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshTableView() {
        // If a RowSorter exists, this helps it pick up row count changes immediately.
        RowSorter<?> sorter = resultTable.getRowSorter();
        if (sorter instanceof DefaultRowSorter<?, ?> drs) {
            drs.allRowsChanged();
        }

        // Revalidate & repaint the table and its parent viewport.
        resultTable.revalidate();
        resultTable.repaint();

        Container p = resultTable.getParent();
        if (p != null) {
            p.revalidate();
            p.repaint();
        }
    }


    private void onEditParams(ActionEvent e) {
        JTextField maxNumField = new JTextField(String.valueOf(model.getMaxNumSets()));
        JTextField radiusField = new JTextField(String.valueOf(model.getMaxRadius()));
        JTextField nearField = new JTextField(String.valueOf(model.getNearWhichEndpoint()));
        JTextField pathField = new JTextField(String.valueOf(model.getMaxPathLength()));
        JCheckBox avoidAmenableBox = new JCheckBox("Avoid amenable backbone (GAC mode)", model.isAvoidAmenable());

        var cfg = model.getCfg();
        JTextField basisDegreeField = new JTextField(String.valueOf(cfg.basisDegree));
        JCheckBox interactionsBox = new JCheckBox("Include X*phi(Z) interactions", cfg.includeTreatmentInteractions);
        JTextField clipField = new JTextField(String.valueOf(cfg.propensityClipEps));
        JTextField bootField = new JTextField(String.valueOf(cfg.bootstrapB));

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Max number of adjustment sets:"));
        panel.add(maxNumField);
        panel.add(new JLabel("Max radius (-1 for no limit):"));
        panel.add(radiusField);
        panel.add(new JLabel("Near which endpoint (0 = X, 1 = Y, other = either):"));
        panel.add(nearField);
        panel.add(new JLabel("Max path length (-1 for no limit):"));
        panel.add(pathField);
        panel.add(avoidAmenableBox);

        panel.add(new JSeparator());
        panel.add(new JLabel("Estimator (v2)"));
        panel.add(new JLabel("Basis degree (continuous Z):"));
        panel.add(basisDegreeField);
        panel.add(interactionsBox);
        panel.add(new JLabel("Propensity clip epsilon:"));
        panel.add(clipField);
        panel.add(new JLabel("Bootstrap B (0 disables):"));
        panel.add(bootField);

        int res = JOptionPane.showConfirmDialog(
                this, panel,
                "v2 Parameters",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (res == JOptionPane.OK_OPTION) {
            try {
                model.setMaxNumSets(Integer.parseInt(maxNumField.getText().trim()));
                model.setMaxRadius(Integer.parseInt(radiusField.getText().trim()));
                model.setNearWhichEndpoint(Integer.parseInt(nearField.getText().trim()));
                model.setMaxPathLength(Integer.parseInt(pathField.getText().trim()));
                model.setAvoidAmenable(avoidAmenableBox.isSelected());

                cfg.basisDegree = Integer.parseInt(basisDegreeField.getText().trim());
                cfg.includeTreatmentInteractions = interactionsBox.isSelected();
                cfg.propensityClipEps = Double.parseDouble(clipField.getText().trim());
                cfg.bootstrapB = Integer.parseInt(bootField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "One or more parameter values are not valid numbers.",
                        "Parameter error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onViewDetails(ActionEvent e) {
        int viewIndex = resultTable.getSelectedRow();
        if (viewIndex < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row first.", "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelIndex = resultTable.convertRowIndexToModel(viewIndex);
        ResultRowV2 row = model.getResultRow(modelIndex);

        if (row == null || row.status != ResultRowV2.Status.OK || row.details == null) {
            String msg = (row == null) ? "No row selected." : ("No detailed estimate is available for this row.\n\n" + safe(row.message));
            JOptionPane.showMessageDialog(this, msg, "No details", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showDetailsDialog(row);
    }

    private void onBinarize() {
        var spec = edu.cmu.tetradapp.editor.BinarizeTreatmentDialogV2.showDialog(
                this, model.getDataSet(), model.getGraph()
        );
        if (spec == null) return;

        model.addOrReplaceDerivedTreatment(spec);

        String cur = treatmentsField.getText().trim();
        if (cur.isEmpty() || "*".equals(cur)) {
            treatmentsField.setText(spec.getDerivedName());
        } else {
            treatmentsField.setText(cur + ", " + spec.getDerivedName());
        }

//        model.setTreatmentsText(treatmentsField.getText());

        // v2: optional – immediately recompute and refresh table after creating a derived treatment
        model.setTreatmentsText(treatmentsField.getText());
        try {
            updateModelFromUI();
            model.recomputeAsync(() -> {
                tableModel.fireTableDataChanged();
                updateViewDetailsEnabled();
            });
            tableModel.fireTableStructureChanged();
            installSorter();          // <-- add this
            installRenderers();
            updateViewDetailsEnabled();
        } catch (Exception ex) {
            tableModel.fireTableStructureChanged();
            installSorter();          // <-- add this
        }
    }

    // ------------------------
    // Details dialog
    // ------------------------

    private void showDetailsDialog(ResultRowV2 row) {
        var d = row.details;

        StringBuilder sb = new StringBuilder();
        sb.append("Mixed DR Adjustment Effect (v2)\n");
        sb.append("--------------------------------\n");
        sb.append("X: ").append(row.formatX()).append("\n");
        sb.append("Y: ").append(row.formatY()).append("\n");
        sb.append("Z: ").append(row.formatZ()).append("\n\n");

        sb.append("ATE_OR: ").append(d.ateOr).append("\n");
        sb.append("ATE_DR: ").append(d.ateDr).append("\n\n");

        sb.append("SE_OR (boot): ").append(d.seOrBoot).append("\n");
        sb.append("SE_DR (boot): ").append(d.seDrBoot).append("\n\n");

        sb.append("CI_OR: [").append(d.ciLoOr).append(", ").append(d.ciHiOr).append("]\n");
        sb.append("CI_DR: [").append(d.ciLoDr).append(", ").append(d.ciHiDr).append("]\n\n");

        sb.append("Propensity min: ").append(d.minProp).append("\n");
        sb.append("Propensity max: ").append(d.maxProp).append("\n");
        sb.append("Frac clipped: ").append(d.fracClipped).append("\n");
        sb.append("n (complete cases): ").append(d.n).append("\n");

        JTextArea area = new JTextArea(sb.toString(), 24, 60);
        area.setEditable(false);
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);

        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(ev -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(area.getText()), null
            );
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(copy);

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Details: " + row.formatX() + " → " + row.formatY(),
                Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout());
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ------------------------
    // Model sync
    // ------------------------

    private void updateModelFromUI() {
        Set<Node> X = parseNodeList(treatmentsField.getText().trim(), true);
        Set<Node> Y = parseNodeList(outcomesField.getText().trim(), false);

        if (X.isEmpty() || Y.isEmpty()) {
            throw new IllegalArgumentException("Treatments and outcomes sets must not be empty.");
        }

        model.setX(X);
        model.setY(Y);

        model.setTreatmentsText(treatmentsField.getText());
        model.setOutcomesText(outcomesField.getText());

//        model.setEffectMode(
//                pairwiseRadio.isSelected()
//                        ? DoublyRobustEstModelV2.EffectMode.PAIRWISE
//                        : DoublyRobustEstModelV2.EffectMode.JOINT
//        );

        model.setEffectMode(DoublyRobustEstModelV2.EffectMode.PAIRWISE);
    }

    private Set<Node> parseNodeList(String text, boolean allowDerived) {
        LinkedHashSet<Node> nodes = new LinkedHashSet<>();
        if (text == null) return nodes;

        // Manual tokenization: split on comma or any whitespace.
        List<String> tokens = tokenizeCsvWhitespace(text);

        for (String tok : tokens) {
            String name = tok.trim();
            if (name.isEmpty()) continue;

            boolean hasWildcard = name.indexOf('*') >= 0 || name.indexOf('?') >= 0;
            if (!hasWildcard) {

                // v2: guard against accidental huge tokens (paste mishaps)
                if (name.length() > 5000) {
                    throw new IllegalArgumentException("Token is too long to be a variable name.");
                }

                // v2: for treatments, allow derived names WITHOUT querying graph.getNode(...)
                if (allowDerived && model.hasDerivedTreatment(name)) {
                    nodes.add(new GraphNode(name));   // placeholder by name
                    continue;
                }

                Node n = graph.getNode(name);
                if (n == null) {
                    throw new IllegalArgumentException("Unknown variable: " + name);
                }
                nodes.add(n);
            } else {
                // Wildcard against real graph variables only
                String regex = wildcardToRegex(name);
                Pattern p = Pattern.compile(regex);

                boolean matchedAny = false;
                for (Node n : graph.getNodes()) {
                    if (p.matcher(n.getName()).matches()) {
                        nodes.add(n);
                        matchedAny = true;
                    }
                }
                if (!matchedAny) {
                    throw new IllegalArgumentException("Wildcard pattern \"" + name + "\" matched no variables.");
                }
            }
        }

        return nodes;
    }

    private static List<String> tokenizeCsvWhitespace(String s) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }

        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void installRenderers() {
        var cm = resultTable.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            String header = String.valueOf(cm.getColumn(i).getHeaderValue());
            if ("ATE_DR".equals(header)) {
                cm.getColumn(i).setCellRenderer(numberRenderer);
            }
        }
    }

    private void updateViewDetailsEnabled() {
        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) {
            viewDetailsButton.setEnabled(false);
            return;
        }

        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        ResultRowV2 r;
        try {
            r = model.getResultRow(modelRow);
        } catch (Exception ex) {
            viewDetailsButton.setEnabled(false);
            return;
        }

        viewDetailsButton.setEnabled(r != null && r.status == ResultRowV2.Status.OK && r.details != null);
    }

    // ------------------------
    // Wildcard
    // ------------------------

    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append("."); break;
                case '\\': sb.append("\\\\"); break;
                case '.':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '+':
                case '-':
                case '^':
                case '$':
                case '|':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    // ------------------------
    // Table model
    // ------------------------

    private static final class DoublyRobustEstResultTableModelV2 extends AbstractTableModel {

        private static final String COL_NUM  = "#";
        private static final String COL_X    = "X";
        private static final String COL_Y    = "Y";
        private static final String COL_Z    = "Adjustment set Z";
        private static final String COL_ATE  = "ATE_DR";
        private static final String COL_NOTE = "Note";

        private final DoublyRobustEstModelV2 model;

        DoublyRobustEstResultTableModelV2(DoublyRobustEstModelV2 model) {
            this.model = Objects.requireNonNull(model, "model");
        }

        private String[] columns() {
            return new String[] { COL_NUM, COL_X, COL_Y, COL_Z, COL_ATE, COL_NOTE };
        }

        @Override
        public int getRowCount() {
            return model.getResults().size();
        }

        @Override
        public int getColumnCount() {
            return columns().length;
        }

        @Override
        public String getColumnName(int column) {
            return columns()[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            String col = columns()[columnIndex];
            if (COL_NUM.equals(col)) return Integer.class;
            if (COL_ATE.equals(col)) return Double.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<ResultRowV2> rows = model.getResults();
            if (rowIndex < 0 || rowIndex >= rows.size()) return null;

            ResultRowV2 r = rows.get(rowIndex);
            String col = columns()[columnIndex];

            if (COL_NUM.equals(col)) return rowIndex + 1;
            if (COL_X.equals(col))   return safe(r.formatX());
            if (COL_Y.equals(col))   return safe(r.formatY());
            if (COL_Z.equals(col))   return safe(r.formatZ());

            if (COL_ATE.equals(col)) {
                return (r.status == ResultRowV2.Status.OK) ? r.ateDr : Double.NaN;
            }

            if (COL_NOTE.equals(col)) {
                if (r.message != null && !r.message.isBlank()) return r.message;
                return switch (r.status) {
                    case OK -> "";
                    case NO_ADJUSTMENT_SET -> "No adjustment set";
                    case INELIGIBLE -> "Ineligible";
                    case NOT_SUPPORTED -> "Not supported";
                };
            }

            return null;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    // ------------------------
    // Helpers
    // ------------------------

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private void installSorter() {
        TableRowSorter<DoublyRobustEstEditorV2.DoublyRobustEstResultTableModelV2> sorter = new TableRowSorter<>(this.tableModel);
        sorter.setComparator(1, NaturalSort.naturalComparator()); // X
        sorter.setComparator(2, NaturalSort.naturalComparator()); // Y
        this.resultTable.setRowSorter(sorter);
    }
}