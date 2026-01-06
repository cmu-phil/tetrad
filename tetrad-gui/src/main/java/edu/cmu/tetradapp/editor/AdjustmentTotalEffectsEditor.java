package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.AdjustmentTotalEffectsModel;
import edu.cmu.tetradapp.model.AdjustmentTotalEffectsModel.ResultRow;

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
 * Editor panel for the {@code "Adjustment & Total Effects"} regression tool.
 *
 * <p>Lets the user:</p>
 *
 * <ul>
 *   <li>Specify treatments <i>X</i> and outcomes <i>Y</i> via text fields
 *       (names and/or wildcards).</li>
 *   <li>Choose between:
 *     <ul>
 *       <li><b>PAIRWISE</b>: total effects for all (x, y) in X&times;Y
 *           (single-pair recursive adjustment).</li>
 *       <li><b>JOINT</b>: joint intervention {@code p(Y | do(X))} using
 *           {@code RecursiveAdjustmentMultiple}.</li>
 *     </ul>
 *   </li>
 *   <li>Tweak recursive adjustment (RA) parameters.</li>
 *   <li>Run the computation.</li>
 *   <li>See a table of (X-set, Y, Z, total effects).</li>
 *   <li>View the full regression result for a selected row.</li>
 * </ul>
 */
public final class AdjustmentTotalEffectsEditor extends JPanel {

    private final AdjustmentTotalEffectsModel model;
    private final Graph graph;

    // Mode controls
    private final JRadioButton pairwiseRadio =
            new JRadioButton("Total effects for all X–Y pairs");
    private final JRadioButton jointRadio =
            new JRadioButton("Joint intervention: p(Y | do(X))");

    // Text fields for X and Y (used in both modes)
    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();

    // Buttons
    private final JButton runButton =
            new JButton("Compute adjustment sets and effects");
    private final JButton paramsButton = new JButton("Edit parameters...");
    private final JButton viewRegressionButton = new JButton("View regression...");

    // Table
    private final JTable resultTable;
    private final ResultTableModel tableModel;
    DefaultTableCellRenderer effectRenderer = new DefaultTableCellRenderer() {
        {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value == null) {
                setText("*");
                return;
            }
            if (value instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d)) {
                    setText("*");
                    return;
                }
                // Optional formatting:
                // setText(String.format("%.3f", d));
                setText(NumberFormatUtil.getInstance().getNumberFormat().format(d));
                return;
            }
            setText(String.valueOf(value));
        }
    };


    /**
     * Constructs an editor for managing and displaying total effects adjustments within the adjustment model. The
     * editor initializes user interface components and sets up event listeners to allow interaction with adjustment
     * data.
     *
     * @param model the adjustment total effects model to be edited; must not be null
     */
    public AdjustmentTotalEffectsEditor(AdjustmentTotalEffectsModel model) {
        this.model = Objects.requireNonNull(model);
        this.graph = model.getGraph();

        this.tableModel = new ResultTableModel(model);
        this.resultTable = new JTable(tableModel);
        this.resultTable.setTransferHandler(new DefaultTableTransferHandler(0));

        installEffectRenderers();

        this.resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.resultTable.setAutoCreateRowSorter(true); // enable column-header sorting

        this.treatmentsField.setText(model.getTreatmentsText());
        this.outcomesField.setText(model.getOutcomesText());

        this.treatmentsField.addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        model.setTreatmentsText(treatmentsField.getText());
                    }
                }
        );

        this.outcomesField.addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        model.setOutcomesText(outcomesField.getText());
                    }
                }
        );

        initUI();
        initListeners();
    }

    // ---------------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------------

    // Convert a shell-style wildcard pattern (*, ?) into a proper regex.
    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
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
        setLayout(new BorderLayout(5, 5));

        // --- Top: mode + X/Y selection -------------------------------------
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Mode radio buttons
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(pairwiseRadio);
        modeGroup.add(jointRadio);

        if (model.getEffectMode() == AdjustmentTotalEffectsModel.EffectMode.JOINT) jointRadio.setSelected(true);
        else pairwiseRadio.setSelected(true);

        JPanel modePanel = new JPanel(new GridLayout(0, 1));
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(pairwiseRadio);
        modePanel.add(jointRadio);

        topPanel.add(modePanel, BorderLayout.NORTH);

        // X/Y input panel
        JPanel xyPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        xyPanel.add(new JLabel("Treatments (X):"));
        xyPanel.add(treatmentsField);
        xyPanel.add(new JLabel("Outcomes (Y):"));
        xyPanel.add(outcomesField);

        topPanel.add(xyPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(paramsButton);
        buttonPanel.add(viewRegressionButton);

        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // --- Center: result table ------------------------------------------
        JScrollPane scrollPane = new JScrollPane(resultTable);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void initListeners() {
        runButton.addActionListener(this::onRun);
        paramsButton.addActionListener(this::onEditParams);
        viewRegressionButton.addActionListener(this::onViewRegression);
    }

    private void onRun(ActionEvent e) {
        try {
            updateModelFromUI();
            model.recompute();

            tableModel.fireTableStructureChanged();  // <-- important
            installEffectRenderers();                // <-- reapply renderers
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Invalid selection",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEditParams(ActionEvent e) {
        // Simple parameter editor dialog; you can replace with your usual parameter UI if desired.
        JTextField maxNumField = new JTextField(String.valueOf(model.getMaxNumSets()));
        JTextField radiusField = new JTextField(String.valueOf(model.getMaxRadius()));
        JTextField nearField = new JTextField(String.valueOf(model.getNearWhichEndpoint()));
        JTextField pathField = new JTextField(String.valueOf(model.getMaxPathLength()));
        JCheckBox doDiscreteRegressions = new JCheckBox("Do Discrete Regressions");
        doDiscreteRegressions.setSelected(model.getDoDiscreteRegressions());

        JCheckBox avoidAmenableBox = new JCheckBox(
                "Avoid amenable backbone (Perković GAC mode)",
                model.isAvoidAmenable()
        );

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Max number of adjustment sets:"));
        panel.add(maxNumField);
        panel.add(new JLabel("Max radius (-1 for no limit):"));
        panel.add(radiusField);
        panel.add(new JLabel("Near which endpoint (0 = X, 1 = Y, other = either):"));
        panel.add(nearField);
        panel.add(new JLabel("Max path length (-1 for no limit):"));
        panel.add(pathField);
        panel.add(doDiscreteRegressions);
        panel.add(avoidAmenableBox);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Recursive Adjustment parameters",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            try {
                model.setMaxNumSets(Integer.parseInt(maxNumField.getText().trim()));
                model.setMaxRadius(Integer.parseInt(radiusField.getText().trim()));
                model.setNearWhichEndpoint(Integer.parseInt(nearField.getText().trim()));
                model.setMaxPathLength(Integer.parseInt(pathField.getText().trim()));
                model.setAvoidAmenable(avoidAmenableBox.isSelected());
                model.setDoDiscreteRegressions(doDiscreteRegressions.isSelected());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "One or more parameter values are not valid integers.",
                        "Parameter error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Sync UI <-> model
    // ---------------------------------------------------------------------

    private void onViewRegression(ActionEvent e) {
        int[] selected = resultTable.getSelectedRows();

        if (selected.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a row first.",
                    "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (selected.length > 1) {
            JOptionPane.showMessageDialog(this,
                    "Multiple rows are selected. Please select exactly one row\n" +
                    "to view its regression result.",
                    "Multiple selections",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int viewIndex = selected[0];
        int modelIndex = resultTable.convertRowIndexToModel(viewIndex);
        ResultRow row = model.getResultRow(modelIndex);

        if (row.discreteRegression) {
            JOptionPane.showMessageDialog(this,
                    "No regression is available for this row because discrete variables are involved\n" +
                    "and \"Do Discrete Regressions\" is unchecked.",
                    "Discrete variables",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!row.amenable || row.regressionResult == null) {
            JOptionPane.showMessageDialog(this,
                    "No regression is available for this row because the pair is not amenable.",
                    "Not amenable",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showRegressionDialog(row);
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
                        ? AdjustmentTotalEffectsModel.EffectMode.PAIRWISE
                        : AdjustmentTotalEffectsModel.EffectMode.JOINT
        );
    }

    /**
     * Parse a comma- or space-separated list of node names, supporting simple wildcards:
     *
     * <ul>
     *   <li><code>*</code> matches any substring;</li>
     *   <li><code>?</code> matches any single character.</li>
     * </ul>
     *
     * <p>Example: {@code "X*, Y1, Z?"} matches all nodes starting with {@code "X"},
     * the node {@code "Y1"}, and nodes whose names start with {@code "Z"} and
     * have one extra character.</p>
     */
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
                if (n == null) {
                    throw new IllegalArgumentException("Unknown variable: " + name);
                }
                nodes.add(n);
            } else {
                // Use wildcardToRegex instead of Pattern.quote/replace.
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
                    throw new IllegalArgumentException(
                            "Wildcard pattern \"" + name + "\" matched no variables.");
                }
            }
        }
        return nodes;
    }

    // ---------------------------------------------------------------------
    // Regression dialog
    // ---------------------------------------------------------------------

    private void showRegressionDialog(ResultRow row) {
        RegressionResult result = row.regressionResult;

        // Reconstruct regressor list in the same order used in the model: X first, then Z.
        List<Node> regressors = new ArrayList<>();
        regressors.addAll(row.X);
        regressors.addAll(row.Z);

        final String[] colNames = {"Variable", "Beta", "SE", "t", "p"};

        double[] coef = result.getCoef();
        double[] se = result.getSe();
        double[] t = null;
        double[] p = null;

        try {
            t = result.getT();
            p = result.getP();
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            // If the RegressionResult implementation doesn't support t/p,
            // we'll just leave them as null and show NaN in those columns.
        }

        List<Object[]> rows = new ArrayList<>();

        // Skip intercept at index 0 (assuming standard layout).
        for (int i = 0; i < regressors.size(); i++) {
            Node var = regressors.get(i);
            int coefIndex = i + 1;

            double beta = (coefIndex < coef.length) ? coef[coefIndex] : Double.NaN;
            double seVal = (se != null && coefIndex < se.length) ? se[coefIndex] : Double.NaN;
            double tVal = (t != null && coefIndex < t.length) ? t[coefIndex] : Double.NaN;
            double pVal = (p != null && coefIndex < p.length) ? p[coefIndex] : Double.NaN;

            rows.add(new Object[]{
                    var.getName(),
                    beta,
                    seVal,
                    tVal,
                    pVal
            });
        }

        JTable table = new JTable(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return colNames.length;
            }

            @Override
            public String getColumnName(int column) {
                return colNames[column];
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return rows.get(rowIndex)[columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        });
        table.setAutoCreateRowSorter(true);
        table.setTransferHandler(new DefaultTableTransferHandler(0));

        JScrollPane scroll = new JScrollPane(table);

        String title = "Regression for " + row.formatXSet() + " → " + row.formatYSet();

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                title,
                Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout());
        dialog.add(scroll, BorderLayout.CENTER);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---------------------------------------------------------------------
    // Table model for summary view
    // ---------------------------------------------------------------------
//    private void installEffectRenderers() {
//        var cm = resultTable.getColumnModel();
//
//        for (int i = 0; i < cm.getColumnCount(); i++) {
//            String name = cm.getColumn(i).getHeaderValue().toString();
//
//            if (name.equals("Total effect")
//                || name.equals("Abs total effect")
//                || name.equals("True Total Effect")
//                || name.equals("Abs True Total Effect")) {
//                cm.getColumn(i).setCellRenderer(effectRenderer);
//            }
//        }
//    }

    private void installEffectRenderers() {
        var cm = resultTable.getColumnModel();

        for (int i = 0; i < cm.getColumnCount(); i++) {
            String name = cm.getColumn(i).getHeaderValue().toString();

            if (name.equals("Total effect")
                || name.equals("True Total Effect")) {
                cm.getColumn(i).setCellRenderer(effectRenderer);
            }
        }
    }

    private static final class ResultTableModel extends AbstractTableModel {

        private static final String COL_NUM = "#";
        private static final String COL_X = "X";
        private static final String COL_Y = "Y";
        private static final String COL_Z = "Adjustment set Z";
        private static final String COL_TE = "Total effect";
//        private static final String COL_ABS_TE = "Abs total effect";
        private static final String COL_TRUE_TE = "True Total Effect";
//        private static final String COL_ABS_TRUE_TE = "Abs True Total Effect";

        private final AdjustmentTotalEffectsModel model;

        ResultTableModel(AdjustmentTotalEffectsModel model) {
            this.model = model;
        }

        private boolean showTrueColumns() {
            return model.getEffectMode() == AdjustmentTotalEffectsModel.EffectMode.PAIRWISE
                   && model.isTrueSemImAvailable();
        }

//        private String[] columns() {
//            if (showTrueColumns()) {
//                return new String[] {
//                        COL_NUM, COL_X, COL_Y, COL_Z, COL_TE, COL_ABS_TE, COL_TRUE_TE, COL_ABS_TRUE_TE
//                };
//            } else {
//                return new String[] {
//                        COL_NUM, COL_X, COL_Y, COL_Z, COL_TE, COL_ABS_TE
//                };
//            }
//        }

        private String[] columns() {
            if (showTrueColumns()) {
                return new String[] { COL_NUM, COL_X, COL_Y, COL_Z, COL_TE, COL_TRUE_TE };
            } else {
                return new String[] { COL_NUM, COL_X, COL_Y, COL_Z, COL_TE };
            }
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

//        @Override
//        public Class<?> getColumnClass(int columnIndex) {
//            String col = columns()[columnIndex];
//            if (COL_NUM.equals(col)) return Integer.class;
//
//            if (COL_TE.equals(col) || COL_ABS_TE.equals(col)
//                || COL_TRUE_TE.equals(col) || COL_ABS_TRUE_TE.equals(col)) {
//                return Double.class;
//            }
//
//            return String.class;
//        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            String col = columns()[columnIndex];
            if (COL_NUM.equals(col)) return Integer.class;
            if (COL_TE.equals(col) || COL_TRUE_TE.equals(col)) return Double.class;
            return String.class;
        }

//        @Override
//        public Object getValueAt(int rowIndex, int columnIndex) {
//            List<ResultRow> rows = model.getResults();
//            if (rowIndex < 0 || rowIndex >= rows.size()) return null;
//
//            ResultRow r = rows.get(rowIndex);
//            String col = columns()[columnIndex];
//
//            // Estimated effect (first beta only; consistent with your current UI)
//            double est = Double.NaN;
//            if (r.amenable && r.betas != null && r.betas.length > 0) {
//                est = r.betas[0];
//            }
//
//            if (COL_NUM.equals(col)) return rowIndex + 1;
//            if (COL_X.equals(col)) return r.formatXSet();
//            if (COL_Y.equals(col)) return r.formatYSet();
//            if (COL_Z.equals(col)) return r.formatZSet();
//            if (COL_TE.equals(col)) return est;
//            if (COL_ABS_TE.equals(col)) return Math.abs(est);
//
//            // True effect columns (PAIRWISE + true SEM only)
//            if (COL_TRUE_TE.equals(col) || COL_ABS_TRUE_TE.equals(col)) {
//                // If regression not computed / not amenable / discrete, return NaN (renderer prints "*")
//                if (!r.amenable || r.discreteRegression) return Double.NaN;
//
//                // PAIRWISE rows should have singletons
//                if (r.X == null || r.Y == null || r.X.size() != 1 || r.Y.size() != 1) return Double.NaN;
//
//                Node x = r.X.iterator().next();
//                Node y = r.Y.iterator().next();
//
//                double truth;
//                try {
//                    truth = model.getTrueTotalEffect(x, y);
//                } catch (Exception ex) {
//                    truth = Double.NaN;
//                }
//
//                return COL_ABS_TRUE_TE.equals(col) ? Math.abs(truth) : truth;
//            }
//
//            return null;
//        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<ResultRow> rows = model.getResults();
            if (rowIndex < 0 || rowIndex >= rows.size()) return null;

            ResultRow r = rows.get(rowIndex);
            String col = columns()[columnIndex];

            double est = Double.NaN;
            if (r.amenable && r.betas != null && r.betas.length > 0) {
                est = r.betas[0];
            }

            if (COL_NUM.equals(col)) return rowIndex + 1;
            if (COL_X.equals(col)) return r.formatXSet();
            if (COL_Y.equals(col)) return r.formatYSet();
            if (COL_Z.equals(col)) return r.formatZSet();
            if (COL_TE.equals(col)) return est;

//            if (COL_TRUE_TE.equals(col)) {
//                if (!r.amenable || r.discreteRegression) return Double.NaN;
//                if (r.X == null || r.Y == null || r.X.size() != 1 || r.Y.size() != 1) return Double.NaN;
//
//                Node x = r.X.iterator().next();
//                Node y = r.Y.iterator().next();
//
//                try {
//                    return model.getTrueTotalEffect(x, y);
//                } catch (Exception ex) {
//                    return Double.NaN;
//                }
//            }

            if (COL_TRUE_TE.equals(col)) {
                // PAIRWISE rows should have singletons
                if (r.X == null || r.Y == null || r.X.size() != 1 || r.Y.size() != 1) return Double.NaN;

                Node x = r.X.iterator().next();
                Node y = r.Y.iterator().next();

                try {
                    return model.getTrueTotalEffect(x, y);
                } catch (Exception ex) {
                    return Double.NaN;
                }
            }

            return null;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}