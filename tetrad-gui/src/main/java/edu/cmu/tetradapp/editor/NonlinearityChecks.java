package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.NonlinearityTests;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * <p>
 * UI panel that runs four nonlinearity checks for the conditional mean
 * <code>E(Y | X)</code>:
 * </p>
 *
 * <ol>
 *   <li><strong>RESET</strong> (Ramsey)</li>
 *   <li><strong>Cross-validation</strong>: linear vs. nonlinear predictor</li>
 *   <li><strong>Conditional-moment / nonlinear-features LM</strong> test on residual structure</li>
 *   <li><strong>Additive-component</strong> nonlinearity test (hinge-basis per regressor)</li>
 * </ol>
 */
public final class NonlinearityChecks extends JPanel {

    private final DataSet dataSet;
    private final List<Node> variables;

    private final JTextField treatmentsArea = new JTextField(30);
    private final JTextField outcomesArea = new JTextField(30);

    private final JRadioButton rbPairwise = new JRadioButton("Nonlinear effects for all X/Y pairs (single regressor)", true);
    private final JRadioButton rbConditional = new JRadioButton("Nonlinear effects of each Y conditional on all X (multiple regressors)", false);

    private final JButton runButton = new JButton("Check Nonlinearity");
    private final JButton showStatsButton = new JButton("Show Stats");

    private final JCheckBox includeSlowTests = new JCheckBox("Include slow tests (CV + Additivity)", false);

    private final ResultsTableModel tableModel = new ResultsTableModel();
    private final JTable table = new JTable(tableModel);

    private final DecimalFormat pFmt = new DecimalFormat("0.####");

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(NonlinearityChecks.class);

    private static final String KEY_TREATMENTS = "nonlin.treatments";
    private static final String KEY_OUTCOMES = "nonlin.outcomes";
    private static final String KEY_MODE = "nonlin.mode"; // "PAIRWISE" or "MULTIVARIATE"
    private static final String KEY_INCLUDE_SLOW = "nonlin.includeSlow";

    /**
     * Constructs a new NonlinearityChecks panel with the provided dataset.
     * Initializes the user interface and sets up event handling for the panel.
     *
     * @param dataSet the dataset to be used for nonlinearity checks. Must not be null.
     * @throws NullPointerException if the provided dataset is null.
     */
    public NonlinearityChecks(DataSet dataSet) {
        super(new BorderLayout());
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = dataSet.getVariables();

        buildUi();
        wireEvents();
    }

    // ---------------- UI ----------------

    private void buildUi() {

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
        c.gridx = 0;
        c.gridy = 0;
        top.add(new JLabel("Treatments (X):"), c);
        c.gridx = 1;
        c.gridy = 0;
        top.add(new JScrollPane(treatmentsArea), c);

        // Outcomes
        c.gridx = 0;
        c.gridy = 1;
        top.add(new JLabel("Outcomes (Y):"), c);
        c.gridx = 1;
        c.gridy = 1;
        top.add(new JScrollPane(outcomesArea), c);

        // Mode
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbPairwise);
        bg.add(rbConditional);

        JPanel modePanel = new JPanel(new GridLayout(0, 1));
        modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
        modePanel.add(rbPairwise);
        modePanel.add(rbConditional);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        top.add(modePanel, c);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(runButton);
        buttons.add(includeSlowTests);
        buttons.add(showStatsButton);
        showStatsButton.setEnabled(false);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        top.add(buttons, c);

        add(top, BorderLayout.NORTH);

        // Table
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(true); // enable column-header sorting


        // Some reasonable column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(40);   // #
        table.getColumnModel().getColumn(1).setPreferredWidth(160);  // X
        table.getColumnModel().getColumn(2).setPreferredWidth(160);  // Y
        table.getColumnModel().getColumn(3).setPreferredWidth(140);  // RESET
        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // CV
        table.getColumnModel().getColumn(5).setPreferredWidth(140);  // MOMENT
        table.getColumnModel().getColumn(6).setPreferredWidth(140);  // ADDITIVE
        table.getColumnModel().getColumn(7).setPreferredWidth(160); // Additivity

        JTableHeader header = table.getTableHeader();
        header.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col < 0) {
                    header.setToolTipText(null);
                    return;
                }

                String tip = switch (col) {
                    case 7 -> "<html><b>Additivity</b><br>" +
                            "Tests whether nonlinear effects of the parents combine additively, or whether interactions among parents improve prediction.</html>";

                    default -> null;
                };

                header.setToolTipText(tip);
            }
        });

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

        includeSlowTests.setToolTipText("Includes slower tests such as cross-validation and additive hinge checks.");
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

        includeSlowTests.setSelected(PREFS.getBoolean(KEY_INCLUDE_SLOW, false));
    }

    private void installPrefsListeners() {
        treatmentsArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                savePrefs();
            }
        });

        outcomesArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                savePrefs();
            }
        });

        rbPairwise.addActionListener(e -> savePrefs());
        rbConditional.addActionListener(e -> savePrefs());

        includeSlowTests.addActionListener(e -> savePrefs());
    }

    private void savePrefs() {
        PREFS.put(KEY_TREATMENTS, treatmentsArea.getText().trim());
        PREFS.put(KEY_OUTCOMES, outcomesArea.getText().trim());
        PREFS.put(KEY_MODE, rbConditional.isSelected() ? "MULTIVARIATE" : "PAIRWISE");
        PREFS.putBoolean(KEY_INCLUDE_SLOW, includeSlowTests.isSelected());
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
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                try {
                    TreatmentsParsed tp = parseTreatmentsMaybeWithCombos(treatmentsArea.getText(), /*allowEmptyAll*/ true);

                    List<Node> Ys = parseVars(outcomesArea.getText(), /*allowEmptyAll*/ true);

                    if (Ys.isEmpty()) {
                        JOptionPane.showMessageDialog(getThisComponent(), "No outcomes selected (Y).");
                        return;
                    }

                    // --- build jobs (deterministic order) ---
                    final boolean runSlow = includeSlowTests.isSelected();
                    final double alpha = 0.05;
                    final int kfold = 10;

                    final List<Job> jobs = new ArrayList<>();

                    if (rbPairwise.isSelected()) {
                        List<Node> Xs = tp.base;

                        if (Xs.isEmpty()) {
                            Set<Node> yset = new HashSet<>(Ys);
                            Xs = variables.stream().filter(v -> !yset.contains(v)).collect(Collectors.toList());
                        }
                        int idx = 1;
                        for (Node x : Xs) {
                            for (Node y : Ys) {
                                if (x.equals(y)) continue;
                                jobs.add(new Job(idx++, Collections.singletonList(x), y));
                            }
                        }
                    } else {
                        List<Node> Xs = tp.base;
                        if (Xs.isEmpty()) Xs = new ArrayList<>(variables);

                        // Expand parent sets if user supplied [k] or [k..m]
                        List<List<Node>> parentSets = expandTreatmentsToParentSets(Xs, tp.combo);

                        int idx = 1;
                        final int MAX_JOBS = 5000; // guardrail; tweak as you like

                        for (Node y : Ys) {
                            for (List<Node> parents : parentSets) {
                                if (parents.isEmpty()) continue;
                                if (parents.contains(y)) continue;

                                jobs.add(new Job(idx++, parents, y));

                                if (jobs.size() > MAX_JOBS) {
                                    throw new IllegalArgumentException(
                                            "Too many (X-set, Y) checks (" + jobs.size() + "). " +
                                                    "Try fewer treatments, smaller [k], or a narrower wildcard."
                                    );
                                }
                            }
                        }
                    }

                    // --- parallel execute jobs ---
                    int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                    int threads = Math.max(1, cores - 1); // leave one core for UI/GC
                    ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);

                    try {
                        @SuppressWarnings("unchecked")
                        Future<ResultRow>[] futures = new Future[jobs.size()];

                        for (int i = 0; i < jobs.size(); i++) {
                            Job job = jobs.get(i);
                            futures[i] = pool.submit(() ->
                                    runOne(job.index, job.xs, job.y, alpha, kfold)
                            );
                        }

                        // Collect in submission order (which matches job order)
                        List<ResultRow> rows = new ArrayList<>(jobs.size());
                        for (Future<ResultRow> f : futures) {
                            rows.add(f.get()); // consider timeout if you want
                        }

                        SwingUtilities.invokeLater(() -> {
                            tableModel.setRows(rows);
                            showStatsButton.setEnabled(false);
                        });

                    } finally {
                        pool.shutdownNow();
                    }
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(getThisComponent(), ex.getMessage());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(getThisComponent(), "Error: " + ex.getMessage());
                }
            }
        }

        new MyWatchedProcess();
    }

    private static final class Job {
        final int index;
        final List<Node> xs;
        final Node y;

        Job(int index, List<Node> xs, Node y) {
            this.index = index;
            this.xs = xs;
            this.y = y;
        }
    }

    private Component getThisComponent() {
        return this;
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

        final boolean doSlow = includeSlowTests.isSelected();

        NonlinearityTests.TestResult cv =
                doSlow ? NonlinearityTests.cvLinearVsNonlinear(yy, XX, kfold, alpha) : null;

        NonlinearityTests.TestResult mom =
                NonlinearityTests.conditionalMomentTest(yy, XX, alpha);

        NonlinearityTests.TestResult add =
                NonlinearityTests.additiveHingeTest(yy, XX, alpha);

        // NEW: additivity check (additive hinge predictor vs full RFF predictor)
        NonlinearityTests.TestResult addit =
                doSlow ? NonlinearityTests.cvAdditiveVsRff(yy, XX, kfold, alpha) : null;

        String xLabel = (xs.size() == 1) ? xs.get(0).getName()
                : xs.stream().map(Node::getName).collect(Collectors.joining(", "));
        String yLabel = y.getName();

        return new ResultRow(index, xLabel, yLabel, reset, cv, mom, add, addit);//, addNoise);
    }

    private double[] col(Node v) {
        int j = variables.indexOf(v);
        int n = dataSet.getNumRows();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = dataSet.getDouble(i, j);
        return out;
    }

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

    /**
     * Convert glob pattern with '*' and '?' to a Java regex that matches the full string.
     */
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

    // ---------------- combo parsing for Treatments ----------------

    private static final class ComboSpec {
        final int kMin;
        final int kMax;

        ComboSpec(int kMin, int kMax) {
            this.kMin = kMin;
            this.kMax = kMax;
        }
    }

    private static final class TreatmentsParsed {
        final List<Node> base;            // expanded variables
        final ComboSpec combo;            // null if no [k] suffix

        TreatmentsParsed(List<Node> base, ComboSpec combo) {
            this.base = base;
            this.combo = combo;
        }
    }

    /**
     * Parse treatments text possibly ending with a combination suffix like:
     * "*[2]" or "X1,X2,X3[2..3]" or "X*[3]"
     * <p>
     * Returns base variables (expanded) plus optional combo spec.
     */
    private TreatmentsParsed parseTreatmentsMaybeWithCombos(String text, boolean allowEmptyAll) {
        String s = (text == null) ? "" : text.trim();
        if (s.isEmpty()) {
            return new TreatmentsParsed(
                    allowEmptyAll ? Collections.emptyList() : Collections.emptyList(),
                    null
            );
        }

        // Look for trailing [ ... ] as combo spec.
        // Examples: [2], [2..3]
        ComboSpec combo = null;

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.*)\\[(\\d+)(?:\\.\\.(\\d+))?\\]\\s*$")
                .matcher(s);

        String basePart = s;
        if (m.matches()) {
            basePart = m.group(1).trim();
            int k1 = Integer.parseInt(m.group(2));
            int k2 = (m.group(3) != null) ? Integer.parseInt(m.group(3)) : k1;

            if (k1 <= 0 || k2 <= 0) {
                throw new IllegalArgumentException("Combination size must be positive: [" + k1 + ".." + k2 + "]");
            }
            if (k2 < k1) {
                throw new IllegalArgumentException("Invalid combination range: [" + k1 + ".." + k2 + "]");
            }
            combo = new ComboSpec(k1, k2);

            // Reuse your existing parseVars for the base portion.
            List<Node> base = parseVars(basePart, allowEmptyAll);
            return new TreatmentsParsed(base, combo);
        } else {
            List<Node> base = parseVars(basePart, allowEmptyAll);
            combo = new ComboSpec(base.size(), base.size());
            return new TreatmentsParsed(base, combo);
        }
    }

    /**
     * Generate all k-subsets of the given list in deterministic lexicographic index order.
     */
    private static List<List<Node>> combinationsOfSize(List<Node> items, int k) {

        int n = items.size();
        if (k < 0 || k > n) return Collections.emptyList();
        if (k == 0) return Collections.singletonList(Collections.emptyList());

        List<List<Node>> out = new ArrayList<>();
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;

        while (true) {
            List<Node> comb = new ArrayList<>(k);
            for (int i = 0; i < k; i++) comb.add(items.get(idx[i]));
            out.add(comb);

            // next combination
            int t = k - 1;
            while (t >= 0 && idx[t] == n - k + t) t--;
            if (t < 0) break;
            idx[t]++;
            for (int i = t + 1; i < k; i++) idx[i] = idx[i - 1] + 1;
        }

        return out;
    }

    /**
     * Expand base treatments into parent-sets:
     * - if no combo spec: one set = base
     * - if combo spec: many sets = all kMin..kMax combinations
     */
    private static List<List<Node>> expandTreatmentsToParentSets(List<Node> base, ComboSpec combo) {
        if (combo == null) {
            return Collections.singletonList(new ArrayList<>(base));
        }

        List<List<Node>> sets = new ArrayList<>();
        for (int k = combo.kMin; k <= combo.kMax; k++) {
            sets.addAll(combinationsOfSize(base, k));
        }
        return sets;
    }

    private void showStats() {
        int r = table.getSelectedRow();
        if (r < 0) return;

        ResultRow row = tableModel.getRow(r);

        String msg =
                "Row #" + row.index + "\n\n" +
                        "X: " + row.xLabel + "\n" +
                        "Y: " + row.yLabel + "\n\n" +
                        "RESET: " + formatStats(row.reset) + "\n" +
                        "CV (linear vs nonlinear): " + formatStats(row.cv) + "\n" +
                        "Conditional-moment: " + formatStats(row.moment) + "\n" +
                        "Additive-component: " + formatStats(row.additive) + "\n" +
                        "Additivity (Additive vs RFF): " + row.additivity + "\n";
        ;

        JOptionPane.showMessageDialog(this, msg, "Nonlinearity stats", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String formatStats(NonlinearityTests.TestResult tr) {
        return (tr == null) ? "Skipped" : tr.toString();
    }

    // ---------------- table model ----------------

    private final class ResultsTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "X", "Y", "RESET", "CV", "Moment", "Additive", "Additivity (Parents)"};//, "Additive Noise"}; // NEW
        private List<ResultRow> rows = new ArrayList<>();

        @Override
        public Object getValueAt(int r, int c) {
            ResultRow row = rows.get(r);
            return switch (c) {
                case 0 -> row.index;
                case 1 -> row.xLabel;
                case 2 -> row.yLabel;
                case 3 -> summarize(row.reset, "");              // RESET always run
                case 4 -> summarize(row.cv, "Skipped");          // CV is slow
                case 5 -> summarize(row.moment, "");             // Moment is fast-ish
                case 6 -> summarize(row.additive, "");           // Additive-component is fast-ish
                case 7 -> summarizeAdditivity(row.additivity);
                default -> "";
            };
        }

        private String summarize(NonlinearityTests.TestResult tr, String ifNull) {
            if (tr == null) return ifNull;
            String label = tr.reject ? "Nonlinear" : "Linear";
            if (!Double.isFinite(tr.pValue)) return label;
            return label + " (p=" + pFmt.format(tr.pValue) + ")";
        }

        private String summarizeAdditivity(NonlinearityTests.TestResult tr) {
            if (tr == null) return "Skipped";
            if (Double.isNaN(tr.pValue)) return "Skipped";

            // For additivity, reject == "Non-additive" (full RFF wins)
            String label = tr.reject ? "Non-additive" : "Additive OK";

            if (!Double.isFinite(tr.pValue)) return label;
            return label + " (p=" + pFmt.format(tr.pValue) + ")";
        }

        void setRows(List<ResultRow> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : rows;
            fireTableDataChanged();
        }

        ResultRow getRow(int r) {
            return rows.get(r);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
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
        final NonlinearityTests.TestResult additivity;

        ResultRow(int index, String xLabel, String yLabel,
                  NonlinearityTests.TestResult reset,
                  NonlinearityTests.TestResult cv,
                  NonlinearityTests.TestResult moment,
                  NonlinearityTests.TestResult additive,
                  NonlinearityTests.TestResult additivity
        ) {
            this.index = index;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.reset = reset;
            this.cv = cv;
            this.moment = moment;
            this.additive = additive;
            this.additivity = additivity;
        }
    }
}