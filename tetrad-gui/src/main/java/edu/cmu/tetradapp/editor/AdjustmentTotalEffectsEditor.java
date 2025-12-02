package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.AdjustmentTotalEffectsModel;
import edu.cmu.tetradapp.model.AdjustmentTotalEffectsModel.ResultRow;
import edu.cmu.tetrad.regression.RegressionResult;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Editor panel for the "Adjustment & Total Effects" regression tool.
 *
 * Lets the user:
 *  - specify treatments X and outcomes Y (single or sets),
 *  - tweak RA parameters,
 *  - run computation,
 *  - see a table of (X-set, Y, Z, total effects),
 *  - view the full regression result for a selected row.
 */
public final class AdjustmentTotalEffectsEditor extends JPanel {

    private final AdjustmentTotalEffectsModel model;
    private final Graph graph;
    private final DataSet dataSet;

    // UI controls
    private final JRadioButton singlePairRadio = new JRadioButton("Single pair (X, Y)");
    private final JRadioButton jointRadio = new JRadioButton("Joint intervention (X set, Y set)");

    private final JComboBox<Node> sourceCombo = new JComboBox<>();
    private final JComboBox<Node> targetCombo = new JComboBox<>();

    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();

    private final JButton runButton = new JButton("Compute adjustment sets and effects");
    private final JButton paramsButton = new JButton("Edit parameters...");
    private final JButton viewRegressionButton = new JButton("View regression...");

    private final JTable resultTable;
    private final ResultTableModel tableModel;

    // Card container for single vs joint selection
    private final JPanel modeCardPanel = new JPanel(new CardLayout());

    public AdjustmentTotalEffectsEditor(AdjustmentTotalEffectsModel model) {
        this.model = Objects.requireNonNull(model);
//        this.graph = model.getGraph();
        this.dataSet = model.getDataSet();
        this.graph = model.getGraph();

        this.tableModel = new ResultTableModel(model);
        this.resultTable = new JTable(tableModel);

        initUI();
        initListeners();
    }

    // ---------------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------------

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // --- Top: mode + X/Y selection -------------------------------------
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Mode radio buttons
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(singlePairRadio);
        modeGroup.add(jointRadio);
        singlePairRadio.setSelected(true);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(singlePairRadio);
        modePanel.add(jointRadio);

        topPanel.add(modePanel, BorderLayout.NORTH);

        // Single pair panel
        JPanel singlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        singlePanel.add(new JLabel("Source (X):"));
        List<Node> nodes = graph.getNodes();
        Collections.sort(nodes);

        for (Node n : nodes) {
            sourceCombo.addItem(n);
            targetCombo.addItem(n);
        }

        singlePanel.add(sourceCombo);
        singlePanel.add(new JLabel("Target (Y):"));
        singlePanel.add(targetCombo);

        // Joint mode panel
        JPanel jointPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        jointPanel.add(new JLabel("Treatments (X set):"));
        jointPanel.add(treatmentsField);
        jointPanel.add(new JLabel("Outcomes (Y set):"));
        jointPanel.add(outcomesField);

        modeCardPanel.add(singlePanel, "single");
        modeCardPanel.add(jointPanel, "joint");

        topPanel.add(modeCardPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(paramsButton);
        buttonPanel.add(viewRegressionButton);

        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // --- Center: result table ------------------------------------------
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setAutoCreateRowSorter(true);
        JScrollPane scrollPane = new JScrollPane(resultTable);
        add(scrollPane, BorderLayout.CENTER);

        // Initialize card layout
        updateModePanels();
    }

    private void initListeners() {
        singlePairRadio.addActionListener(e -> updateModePanels());
        jointRadio.addActionListener(e -> updateModePanels());

        runButton.addActionListener(this::onRun);
        paramsButton.addActionListener(this::onEditParams);
        viewRegressionButton.addActionListener(this::onViewRegression);
    }

    private void updateModePanels() {
        CardLayout cl = (CardLayout) modeCardPanel.getLayout();
        if (singlePairRadio.isSelected()) {
            cl.show(modeCardPanel, "single");
        } else {
            cl.show(modeCardPanel, "joint");
        }
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void onRun(ActionEvent e) {
        try {
            updateModelFromUI();
            model.recompute();
            tableModel.fireTableDataChanged();
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
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "One or more parameter values are not valid integers.",
                        "Parameter error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onViewRegression(ActionEvent e) {
        int rowIndex = resultTable.getSelectedRow();
        if (rowIndex < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a row first.",
                    "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ResultRow row = model.getResultRow(rowIndex);
        showRegressionDialog(row);
    }

    // ---------------------------------------------------------------------
    // Sync UI <-> model
    // ---------------------------------------------------------------------

    private void updateModelFromUI() {
        if (singlePairRadio.isSelected()) {
            Node x = (Node) sourceCombo.getSelectedItem();
            Node y = (Node) targetCombo.getSelectedItem();
            if (x == null || y == null) {
                throw new IllegalArgumentException("Please select both a source and a target.");
            }
            model.setX(Collections.singleton(x));
            model.setY(Collections.singleton(y));
        } else {
            Set<Node> X = parseNodeList(treatmentsField.getText().trim());
            Set<Node> Y = parseNodeList(outcomesField.getText().trim());
            if (X.isEmpty() || Y.isEmpty()) {
                throw new IllegalArgumentException("Treatments and outcomes sets must not be empty.");
            }
            model.setX(X);
            model.setY(Y);
        }
    }

    /**
     * Parse a comma/space-separated list of node names.
     * (Easy extension point for regex or wildcards later.)
     */
    private Set<Node> parseNodeList(String text) {
        LinkedHashSet<Node> nodes = new LinkedHashSet<>();
        if (text.isEmpty()) return nodes;

        String[] tokens = text.split("[,\\s]+");
        for (String tok : tokens) {
            String name = tok.trim();
            if (name.isEmpty()) continue;
            Node n = graph.getNode(name);
            if (n == null) {
                throw new IllegalArgumentException("Unknown variable: " + name);
            }
            nodes.add(n);
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

        // Columns: you may trim this if RegressionResult doesn't provide t/p values.
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

        JScrollPane scroll = new JScrollPane(table);

        String title = "Regression for " + row.formatXSet() + " → " + row.formatYSet();

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                title,
                Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout());
        dialog.add(scroll, BorderLayout.CENTER);

        // Optional: if RegressionResult has an R² method, you can show it here.
        // For example:
        // try {
        //     double r2 = result.getRSquared();  // or getRSquare() depending on API
        //     JLabel info = new JLabel("R² = " + String.format("%.4f", r2));
        //     info.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        //     dialog.add(info, BorderLayout.SOUTH);
        // } catch (NoSuchMethodError ignored) { }

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---------------------------------------------------------------------
    // Table model for summary view
    // ---------------------------------------------------------------------

    private static final class ResultTableModel extends AbstractTableModel {
        private final AdjustmentTotalEffectsModel model;

        private static final String[] COLS = {
                "#", "X set", "Y", "Adjustment set Z", "Total effects (betas)"
        };

        ResultTableModel(AdjustmentTotalEffectsModel model) {
            this.model = model;
        }

        @Override
        public int getRowCount() {
            return model.getResults().size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<ResultRow> rows = model.getResults();
            if (rowIndex < 0 || rowIndex >= rows.size()) return null;
            ResultRow r = rows.get(rowIndex);

            switch (columnIndex) {
                case 0:  return rowIndex + 1;
                case 1:  return r.formatXSet();
                case 2:  return r.formatYSet();
                case 3:  return r.formatZSet();
                case 4:  return r.formatBetas();
                default: return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}