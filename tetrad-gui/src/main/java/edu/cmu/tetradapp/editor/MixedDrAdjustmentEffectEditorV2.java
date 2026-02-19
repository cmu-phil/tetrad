package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.MixedDrAdjustmentEffectEditorModelV2;
import edu.cmu.tetradapp.model.MixedDrAdjustmentEffectEditorModelV2.ResultRowV2;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
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
 */
public final class MixedDrAdjustmentEffectEditorV2 extends JPanel {

    private final MixedDrAdjustmentEffectEditorModelV2 model;
    private final Graph graph;

    private final JRadioButton pairwiseRadio =
            new JRadioButton("ATE for all X–Y pairs (binary X only)");
    private final JRadioButton jointRadio =
            new JRadioButton("Joint intervention: p(Y | do(X)) (v2 supports |X|=1)");

    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();

    private final JButton runButton = new JButton("Compute adjustment sets and ATE (DR)");
    private final JButton paramsButton = new JButton("Edit parameters...");
    private final JButton viewDetailsButton = new JButton("View details...");

    private final JTable resultTable;
    private final ResultTableModel tableModel;

    private final DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer() {
        {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

//        @Override
//        protected void setValue(Object value) {
//            if (value == null) {
//                setText("*");
//                return;
//            }
//            if (value instanceof Number n) {
//                double d = n.doubleValue();
//                if (Double.isNaN(d)) {
//                    setText("*");
//                    return;
//                }
//                setText(NumberFormatUtil.getInstance().getNumberFormat().format(d));
//                return;
//            }
//            setText(String.valueOf(value));
//        }

        @Override
        protected void setValue(Object value) {
            if (value == null) {
                setText("");          // v2: show blank instead of "*"
                return;
            }
            if (value instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    setText("");      // v2: blank for missing/NA
                    return;
                }
                setText(NumberFormatUtil.getInstance().getNumberFormat().format(d));
                return;
            }
            setText(String.valueOf(value));
        }
    };

    public MixedDrAdjustmentEffectEditorV2(MixedDrAdjustmentEffectEditorModelV2 model) {
        this.model = Objects.requireNonNull(model);
        this.graph = model.getGraph();

        this.tableModel = new ResultTableModel(model);
        this.resultTable = new JTable(tableModel);
        this.resultTable.setFillsViewportHeight(true);
        this.resultTable.setTransferHandler(new DefaultTableTransferHandler(0));

        this.resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.resultTable.setAutoCreateRowSorter(true);

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
    }

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

    private void initUI() {
        setLayout(new BorderLayout(5,5));

        JPanel topPanel = new JPanel(new BorderLayout(5,5));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(pairwiseRadio);
        modeGroup.add(jointRadio);

        if (model.getEffectMode() == MixedDrAdjustmentEffectEditorModelV2.EffectMode.JOINT) jointRadio.setSelected(true);
        else pairwiseRadio.setSelected(true);

        JPanel modePanel = new JPanel(new GridLayout(0,1));
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(pairwiseRadio);
        modePanel.add(jointRadio);
        topPanel.add(modePanel, BorderLayout.NORTH);

        JPanel xyPanel = new JPanel(new GridLayout(2,2,5,5));
        xyPanel.add(new JLabel("Treatments (X):"));
        xyPanel.add(treatmentsField);
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
    }

    private void initListeners() {
        runButton.addActionListener(this::onRun);
        paramsButton.addActionListener(this::onEditParams);
        viewDetailsButton.addActionListener(this::onViewDetails);
    }

    private void onRun(ActionEvent e) {
        try {
            updateModelFromUI();
            model.recompute();

            tableModel.fireTableStructureChanged();
            installRenderers();
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid selection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEditParams(ActionEvent e) {
        // v2: keep this simple; borrow the RA panel feel from linear tool, plus a few estimator knobs.
        JTextField maxNumField = new JTextField(String.valueOf(model.getMaxNumSets()));
        JTextField radiusField = new JTextField(String.valueOf(model.getMaxRadius()));
        JTextField nearField = new JTextField(String.valueOf(model.getNearWhichEndpoint()));
        JTextField pathField = new JTextField(String.valueOf(model.getMaxPathLength()));
        JCheckBox avoidAmenableBox = new JCheckBox("Avoid amenable backbone (GAC mode)", model.isAvoidAmenable());

        // Estimator knobs (v2)
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
        int[] selected = resultTable.getSelectedRows();
        if (selected.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select a row first.", "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (selected.length > 1) {
            JOptionPane.showMessageDialog(this, "Please select exactly one row to view details.",
                    "Multiple selections", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int viewIndex = selected[0];
        int modelIndex = resultTable.convertRowIndexToModel(viewIndex);
        ResultRowV2 row = model.getResultRow(modelIndex);

        if (row.status != ResultRowV2.Status.OK || row.details == null) {
            JOptionPane.showMessageDialog(this,
                    "No detailed estimate is available for this row.\n\n" + row.message,
                    "No details",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showDetailsDialog(row);
    }

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

        JTextArea area = new JTextArea(sb.toString(), 24, 40);
        area.setEditable(false);
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);

        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(ev -> {
            area.selectAll();
            area.copy();
            area.select(0, 0);
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

    private void updateModelFromUI() {
        Set<Node> X = parseNodeList(treatmentsField.getText().trim());
        Set<Node> Y = parseNodeList(outcomesField.getText().trim());

        if (X.isEmpty() || Y.isEmpty()) {
            throw new IllegalArgumentException("Treatments and outcomes sets must not be empty.");
        }

        model.setX(X);
        model.setY(Y);

        model.setEffectMode(
                pairwiseRadio.isSelected()
                        ? MixedDrAdjustmentEffectEditorModelV2.EffectMode.PAIRWISE
                        : MixedDrAdjustmentEffectEditorModelV2.EffectMode.JOINT
        );
    }

    private Set<Node> parseNodeList(String text) {
        LinkedHashSet<Node> nodes = new LinkedHashSet<>();
        if (text.isEmpty()) return nodes;

        String[] tokens = text.split("[,\\s]+");
        for (String tok : tokens) {
            String name = tok.trim();
            if (name.isEmpty()) continue;

            boolean hasWildcard = name.contains("*") || name.contains("?");
            if (!hasWildcard) {
                Node n = graph.getNode(name);
                if (n == null) throw new IllegalArgumentException("Unknown variable: " + name);
                nodes.add(n);
            } else {
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

    private void installRenderers() {
        var cm = resultTable.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            String name = cm.getColumn(i).getHeaderValue().toString();
            if (name.equals(ResultTableModel.COL_ATE_DR)) {
                cm.getColumn(i).setCellRenderer(numberRenderer);
            }
        }
    }

    // ------------------------
    // Table model (simple rows)
    // ------------------------

    private static final class ResultTableModel extends AbstractTableModel {

        static final String COL_NUM = "#";
        static final String COL_X = "X";
        static final String COL_Y = "Y";
        static final String COL_Z = "Adjustment set Z";
        static final String COL_ATE_DR = "ATE_DR";

        private final MixedDrAdjustmentEffectEditorModelV2 model;

        ResultTableModel(MixedDrAdjustmentEffectEditorModelV2 model) {
            this.model = model;
        }

        private String[] columns() {
            return new String[]{COL_NUM, COL_X, COL_Y, COL_Z, COL_ATE_DR};
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
            if (COL_ATE_DR.equals(col)) return Double.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<ResultRowV2> rows = model.getResults();
            if (rowIndex < 0 || rowIndex >= rows.size()) return null;

            ResultRowV2 r = rows.get(rowIndex);
            String col = columns()[columnIndex];

            if (COL_NUM.equals(col)) return rowIndex + 1;
            if (COL_X.equals(col)) return r.formatX();
            if (COL_Y.equals(col)) return r.formatY();
            if (COL_Z.equals(col)) return r.formatZ();
            if (COL_ATE_DR.equals(col)) return r.ateDr;

            return null;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}