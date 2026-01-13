package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.NonlinearityTests;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * UI panel that runs four nonlinearity checks for E(Y|X):
 *  1) RESET (Ramsey)
 *  2) CV: linear vs nonlinear predictor
 *  3) Residual conditional-moment / nonlinear features LM test
 *  4) Additive-component nonlinearity test (hinge-basis per regressor)
 */
public final class NonlinearityChecks extends JPanel {

    private final DataSet dataSet;
    private final List<Node> variables;

    private final JTextArea treatmentsArea = new JTextArea(3, 30);
    private final JTextArea outcomesArea = new JTextArea(3, 30);

    private final JRadioButton rbPairwise = new JRadioButton("Nonlinear effects for all X/Y pairs (single regressor)", true);
    private final JRadioButton rbConditional = new JRadioButton("Nonlinear effects of each Y conditional on all X (multiple regressors)", false);

    private final JButton runButton = new JButton("Check Nonlinearity");
    private final JButton showStatsButton = new JButton("Show Stats");

    private final ResultsTableModel tableModel = new ResultsTableModel();
    private final JTable table = new JTable(tableModel);

    private final DecimalFormat pFmt = new DecimalFormat("0.####");

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(NonlinearityChecks.class);

    private static final String KEY_TREATMENTS = "nonlin.treatments";
    private static final String KEY_OUTCOMES   = "nonlin.outcomes";
    private static final String KEY_MODE       = "nonlin.mode"; // "PAIRWISE" or "MULTIVARIATE"

    public NonlinearityChecks(DataSet dataSet) {
        super(new BorderLayout());
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = dataSet.getVariables();

        buildUi();
        wireEvents();
    }

    // ---------------- UI ----------------

    private void buildUi() {
        treatmentsArea.setLineWrap(true);
        treatmentsArea.setWrapStyleWord(true);

        outcomesArea.setLineWrap(true);
        outcomesArea.setWrapStyleWord(true);

        // Suggest defaults (optional): empty means "all"
        treatmentsArea.setText("");
        outcomesArea.setText("");

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        // Treatments
        c.gridx = 0; c.gridy = 0;
        top.add(new JLabel("Treatments (X):"), c);
        c.gridx = 1; c.gridy = 0;
        top.add(new JScrollPane(treatmentsArea), c);

        // Outcomes
        c.gridx = 0; c.gridy = 1;
        top.add(new JLabel("Outcomes (Y):"), c);
        c.gridx = 1; c.gridy = 1;
        top.add(new JScrollPane(outcomesArea), c);

        // Mode
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbPairwise);
        bg.add(rbConditional);

        JPanel modePanel = new JPanel(new GridLayout(0, 1));
        modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
        modePanel.add(rbPairwise);
        modePanel.add(rbConditional);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        top.add(modePanel, c);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(runButton);
        buttons.add(showStatsButton);
        showStatsButton.setEnabled(false);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        top.add(buttons, c);

        add(top, BorderLayout.NORTH);

        // Table
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Some reasonable column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(40);   // #
        table.getColumnModel().getColumn(1).setPreferredWidth(160);  // X
        table.getColumnModel().getColumn(2).setPreferredWidth(160);  // Y
        table.getColumnModel().getColumn(3).setPreferredWidth(140);  // RESET
        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // CV
        table.getColumnModel().getColumn(5).setPreferredWidth(140);  // MOMENT
        table.getColumnModel().getColumn(6).setPreferredWidth(140);  // ADDITIVE

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Footer note
        JTextArea note = new JTextArea(
                "Notes:\n" +
                        "- Results are about nonlinearity in the conditional mean E(Y|X).\n" +
                        "- “Nonlinear” means the test rejected linearity at the chosen alpha.\n" +
                        "- Use “Show Stats” for full statistics and p-values for the selected row."
        );
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(note, BorderLayout.SOUTH);

        installPrefsListeners();
        loadPrefs();
    }

    private void loadPrefs() {
        treatmentsArea.setText(PREFS.get(KEY_TREATMENTS, ""));
        outcomesArea.setText(PREFS.get(KEY_OUTCOMES, ""));

        String mode = PREFS.get(KEY_MODE, "PAIRWISE");
        if ("MULTIVARIATE".equalsIgnoreCase(mode)) {
            rbConditional.setSelected(true);
        } else {
            rbPairwise.setSelected(true);
        }
    }

    private void installPrefsListeners() {
        treatmentsArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { savePrefs(); }
        });

        outcomesArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { savePrefs(); }
        });

        rbPairwise.addActionListener(e -> savePrefs());
        rbConditional.addActionListener(e -> savePrefs());
    }

    private void savePrefs() {
        PREFS.put(KEY_TREATMENTS, treatmentsArea.getText().trim());
        PREFS.put(KEY_OUTCOMES, outcomesArea.getText().trim());
        PREFS.put(KEY_MODE, rbConditional.isSelected() ? "MULTIVARIATE" : "PAIRWISE");
    }

    private void wireEvents() {
        runButton.addActionListener(e -> runChecks());
        showStatsButton.addActionListener(e -> showStats());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            showStatsButton.setEnabled(table.getSelectedRow() >= 0 && tableModel.getRowCount() > 0);
        });
    }

    // ---------------- logic ----------------

    private void runChecks() {
        try {
            List<Node> Xs = parseVars(treatmentsArea.getText(), /*allowEmptyAll*/ true);
            List<Node> Ys = parseVars(outcomesArea.getText(), /*allowEmptyAll*/ true);

            if (Ys.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No outcomes selected (Y).");
                return;
            }

            double alpha = 0.05; // could be a UI knob later
            int kfold = 10;      // could be a UI knob later

            List<ResultRow> rows = new ArrayList<>();

            if (rbPairwise.isSelected()) {
                if (Xs.isEmpty()) {
                    // if treatments empty => all variables except outcomes? we’ll just use all vars not in Ys
                    Set<Node> yset = new HashSet<>(Ys);
                    Xs = variables.stream().filter(v -> !yset.contains(v)).collect(Collectors.toList());
                }

                int idx = 1;
                for (Node x : Xs) {
                    for (Node y : Ys) {
                        if (x.equals(y)) continue;
                        rows.add(runOne(idx++, Collections.singletonList(x), y, alpha, kfold));
                    }
                }
            } else {
                // conditional: for each outcome y, regress on all Xs (multi-regressor)
                if (Xs.isEmpty()) {
                    // default Xs = all variables except Y itself
                    Xs = new ArrayList<>(variables);
                }
                int idx = 1;
                for (Node y : Ys) {
                    List<Node> parents = Xs.stream().filter(v -> !v.equals(y)).collect(Collectors.toList());
                    if (parents.isEmpty()) continue;
                    rows.add(runOne(idx++, parents, y, alpha, kfold));
                }
            }

            tableModel.setRows(rows);
            showStatsButton.setEnabled(false);

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private ResultRow runOne(int index, List<Node> xs, Node y, double alpha, int kfold) {
        double[] yy = col(y);

        double[][] XX = new double[yy.length][xs.size()];
        for (int j = 0; j < xs.size(); j++) {
            double[] xj = col(xs.get(j));
            for (int i = 0; i < yy.length; i++) XX[i][j] = xj[i];
        }

        // Drop rows with NaNs in any involved variable (testwise deletion)
        NonlinearityTests.CleanData cd = NonlinearityTests.clean(yy, XX);
        yy = cd.y;
        XX = cd.X;

        NonlinearityTests.TestResult reset = NonlinearityTests.resetTest(yy, XX, alpha);
        NonlinearityTests.TestResult cv    = NonlinearityTests.cvLinearVsNonlinear(yy, XX, kfold, alpha);
        NonlinearityTests.TestResult mom   = NonlinearityTests.conditionalMomentTest(yy, XX, alpha);
        NonlinearityTests.TestResult add   = NonlinearityTests.additiveHingeTest(yy, XX, alpha);

        String xLabel = (xs.size() == 1) ? xs.get(0).getName() : xs.stream().map(Node::getName).collect(Collectors.joining(", "));
        String yLabel = y.getName();

        return new ResultRow(index, xLabel, yLabel, reset, cv, mom, add);
    }

    private double[] col(Node v) {
        int j = variables.indexOf(v);
        int n = dataSet.getNumRows();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = dataSet.getDouble(i, j);
        return out;
    }

//    private List<Node> parseVars(String text, boolean allowEmptyAll) {
//        String s = (text == null) ? "" : text.trim();
//        if (s.isEmpty()) return allowEmptyAll ? Collections.emptyList() : Collections.emptyList();
//
//        // split on commas or whitespace
//        String[] toks = s.split("[,\\s]+");
//        List<Node> out = new ArrayList<>();
//        Map<String, Node> byName = variables.stream().collect(Collectors.toMap(Node::getName, n -> n, (a, b) -> a));
//
//        for (String t : toks) {
//            if (t == null || t.isBlank()) continue;
//            Node v = byName.get(t.trim());
//            if (v == null) throw new IllegalArgumentException("Unknown variable: " + t.trim());
//            out.add(v);
//        }
//        return out;
//    }

    private List<Node> parseVars(String text, boolean allowEmptyAll) {
        String s = (text == null) ? "" : text.trim();
        if (s.isEmpty()) return allowEmptyAll ? Collections.emptyList() : Collections.emptyList();

        // split on commas or whitespace
        String[] toks = s.split("[,\\s]+");

        // Map by exact name
        Map<String, Node> byName = variables.stream()
                .collect(Collectors.toMap(Node::getName, n -> n, (a, b) -> a));

        // Keep insertion order and avoid duplicates
        LinkedHashSet<Node> out = new LinkedHashSet<>();

        for (String raw : toks) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;

            // Exact match fast-path
            Node exact = byName.get(t);
            if (exact != null) {
                out.add(exact);
                continue;
            }

            // Wildcard?
            if (t.indexOf('*') >= 0 || t.indexOf('?') >= 0) {
                String regex = globToRegex(t);

                boolean matchedAny = false;
                for (Node v : variables) {
                    if (v.getName().matches(regex)) {
                        out.add(v);
                        matchedAny = true;
                    }
                }
                if (!matchedAny) {
                    throw new IllegalArgumentException("No variables match pattern: " + t);
                }
            } else {
                // Not exact, not wildcard => error (mirrors “unknown variable” behavior)
                throw new IllegalArgumentException("Unknown variable: " + t);
            }
        }

        return new ArrayList<>(out);
    }

    /** Convert glob pattern with '*' and '?' to a Java regex that matches the full string. */
    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                // escape regex metacharacters
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> sb.append("\\").append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private void showStats() {
        int r = table.getSelectedRow();
        if (r < 0) return;

        ResultRow row = tableModel.getRow(r);

        String msg =
                "Row #" + row.index + "\n\n" +
                        "X: " + row.xLabel + "\n" +
                        "Y: " + row.yLabel + "\n\n" +
                        "RESET: " + row.reset + "\n" +
                        "CV (linear vs nonlinear): " + row.cv + "\n" +
                        "Conditional-moment: " + row.moment + "\n" +
                        "Additive-component: " + row.additive + "\n";

        JOptionPane.showMessageDialog(this, msg, "Nonlinearity stats", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------- table model ----------------

    private final class ResultsTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "X", "Y", "RESET", "CV", "Moment", "Additive"};
        private List<ResultRow> rows = new ArrayList<>();

        void setRows(List<ResultRow> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        ResultRow getRow(int r) {
            return rows.get(r);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ResultRow row = rows.get(r);
            return switch (c) {
                case 0 -> row.index;
                case 1 -> row.xLabel;
                case 2 -> row.yLabel;
                case 3 -> summarize(row.reset);
                case 4 -> summarize(row.cv);
                case 5 -> summarize(row.moment);
                case 6 -> summarize(row.additive);
                default -> "";
            };
        }

        private String summarize(NonlinearityTests.TestResult tr) {
            if (tr == null) return "";
            String label = tr.reject ? "Nonlinear" : "Linear";
            if (!Double.isFinite(tr.pValue)) return label;
            return label + " (p=" + pFmt.format(tr.pValue) + ")";
        }
    }

    private static final class ResultRow {
        final int index;
        final String xLabel;
        final String yLabel;
        final NonlinearityTests.TestResult reset;
        final NonlinearityTests.TestResult cv;
        final NonlinearityTests.TestResult moment;
        final NonlinearityTests.TestResult additive;

        ResultRow(int index, String xLabel, String yLabel,
                  NonlinearityTests.TestResult reset,
                  NonlinearityTests.TestResult cv,
                  NonlinearityTests.TestResult moment,
                  NonlinearityTests.TestResult additive) {
            this.index = index;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.reset = reset;
            this.cv = cv;
            this.moment = moment;
            this.additive = additive;
        }
    }
}