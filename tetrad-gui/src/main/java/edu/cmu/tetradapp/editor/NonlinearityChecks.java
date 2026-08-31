package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.NonlinearityTests;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetrad.util.TMath;

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

    private final JCheckBox includeCv = new JCheckBox("Include CV (slow)", false);
    private final JCheckBox includeAdditivity = new JCheckBox("Include Additivity (Parents)", false);

    private final ResultsTableModel tableModel = new ResultsTableModel();
    private final JTable table = new JTable(tableModel);

    private final DecimalFormat pFmt = new DecimalFormat("0.####");

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(NonlinearityChecks.class);

    private static final String KEY_TREATMENTS = "nonlin.treatments";
    private static final String KEY_OUTCOMES = "nonlin.outcomes";
    private static final String KEY_MODE = "nonlin.mode"; // "PAIRWISE" or "MULTIVARIATE"
    private static final String KEY_INCLUDE_CV = "nonlin.includeCv";
    private static final String KEY_INCLUDE_ADDITIVITY = "nonlin.includeAdditivity";

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
        buttons.add(includeCv);
        buttons.add(includeAdditivity);
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
//        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // CV
        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // MOMENT
        table.getColumnModel().getColumn(5).setPreferredWidth(140);  // ADDITIVE
        table.getColumnModel().getColumn(6).setPreferredWidth(160); // Additivity

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
                    case 3 -> "<html><b>RESET</b> (Ramsey)<br>" +
                            "F-test: do the square and cube of the fitted values improve the linear fit?<br>" +
                            "See Notes... for details.</html>";
                    case 4 -> "<html><b>CV</b> (linear vs. nonlinear)<br>" +
                            "Does a nonlinear (RBF kernel) predictor beat the linear one out of sample?<br>" +
                            "Robust to heteroskedasticity. Run only when \"Include CV\" is checked.</html>";
                    case 5 -> "<html><b>Moment</b> (LM test)<br>" +
                            "Chi-square LM test: do squares, cubes, and pairwise products of X explain the linear residuals?</html>";
                    case 6 -> "<html><b>Additive</b> (hinge basis)<br>" +
                            "F-test: do per-variable hinge (piecewise-linear) terms improve the linear fit? No interaction terms.</html>";
                    case 7 -> "<html><b>Additivity (Parents)</b><br>" +
                            "Tests whether nonlinear effects of the parents combine additively, or whether interactions among parents improve prediction.<br>" +
                            "Needs 2+ regressors; run only when \"Include Additivity (Parents)\" is checked.</html>";

                    default -> null;
                };

                header.setToolTipText(tip);
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Footer: a short note plus a "Notes..." button that opens the full interpretation guidance.
        JTextArea note = new JTextArea(
                "Notes:\n" +
                        "- Results are about nonlinearity in the conditional mean E(Y|X); “Nonlinear” means the " +
                        "test rejected linearity at alpha = 0.05.\n" +
                        "- Use “Show Stats” for full statistics and p-values for the selected row; see “Notes...” " +
                        "for what each test does, how to read disagreements between tests, and the effect of " +
                        "regression direction."
        );
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton notesButton = new JButton("Notes...");
        notesButton.setFocusable(false);
        notesButton.addActionListener(e -> showFullNotes());

        JButton copyTableButton = new JButton("Copy Table");
        copyTableButton.setFocusable(false);
        copyTableButton.setToolTipText("Copy the results table to the clipboard as tab-separated "
                + "text, for pasting into a spreadsheet or document.");
        copyTableButton.addActionListener(e -> copyTableToClipboard(copyTableButton));

        JPanel notesButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        notesButtonPanel.setOpaque(false);
        notesButtonPanel.add(copyTableButton);
        notesButtonPanel.add(notesButton);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(note, BorderLayout.CENTER);
        footer.add(notesButtonPanel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        installPrefsListeners();
        loadPrefs();

        includeCv.setToolTipText("Cross-validated linear vs. nonlinear (kernel ridge) prediction. " +
                "The slowest test: fits a 200-feature kernel model on every fold of every row.");
        includeAdditivity.setToolTipText("Cross-validated additive vs. interaction model for rows with 2+ regressors " +
                "(conditional mode). Moderately slow; always Skipped in pairwise mode.");
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

        includeCv.setSelected(PREFS.getBoolean(KEY_INCLUDE_CV, false));
        includeAdditivity.setSelected(PREFS.getBoolean(KEY_INCLUDE_ADDITIVITY, false));
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

        includeCv.addActionListener(e -> savePrefs());
        includeAdditivity.addActionListener(e -> savePrefs());
    }

    private void savePrefs() {
        PREFS.put(KEY_TREATMENTS, treatmentsArea.getText().trim());
        PREFS.put(KEY_OUTCOMES, outcomesArea.getText().trim());
        PREFS.put(KEY_MODE, rbConditional.isSelected() ? "MULTIVARIATE" : "PAIRWISE");
        PREFS.putBoolean(KEY_INCLUDE_CV, includeCv.isSelected());
        PREFS.putBoolean(KEY_INCLUDE_ADDITIVITY, includeAdditivity.isSelected());
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
                    final boolean doCv = includeCv.isSelected();
                    final boolean doAdditivity = includeAdditivity.isSelected();
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
                    int cores = TMath.max(1, Runtime.getRuntime().availableProcessors());
                    int threads = TMath.max(1, cores - 1); // leave one core for UI/GC
                    ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);

                    try {
                        @SuppressWarnings("unchecked")
                        Future<ResultRow>[] futures = new Future[jobs.size()];

                        for (int i = 0; i < jobs.size(); i++) {
                            Job job = jobs.get(i);
                            futures[i] = pool.submit(() ->
                                    runOne(job.index, job.xs, job.y, alpha, kfold, doCv, doAdditivity)
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

    /**
     * Shows the full interpretation guidance for the results table in a tabbed, scrollable dialog. The short footer
     * keeps the panel compact; the details live here. There is one tab per test column, plus an overview and an
     * interpretation tab. The guidance mirrors the caveats documented in
     * {@link edu.cmu.tetrad.search.utils.NonlinearityTests} and is grounded in the calibration harness results.
     */
    private void showFullNotes() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", notesTab(NOTES_OVERVIEW));
        tabs.addTab("RESET", notesTab(NOTES_RESET));
        tabs.addTab("CV", notesTab(NOTES_CV));
        tabs.addTab("Moment", notesTab(NOTES_MOMENT));
        tabs.addTab("Additive", notesTab(NOTES_ADDITIVE));
        tabs.addTab("Additivity (Parents)", notesTab(NOTES_ADDITIVITY));
        tabs.addTab("Interpreting", notesTab(NOTES_INTERPRETING));
        tabs.setPreferredSize(new Dimension(660, 480));

        JOptionPane.showMessageDialog(getThisComponent(), tabs,
                "Nonlinearity Checks - Notes", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Wraps a block of note text in a read-only, word-wrapped, scrollable text area for use as a tab.
     */
    private static JComponent notesTab(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setMargin(new Insets(8, 8, 8, 8));
        area.setCaretPosition(0);
        return new JScrollPane(area);
    }

    // ---------------- Notes text (one block per tab) ----------------

    private static final String NOTES_OVERVIEW = """
            What is tested
            Each row tests whether the conditional mean E(Y|X) of that row's regression is linear in X. \
            "Nonlinear" means the test rejected linearity at alpha = 0.05 (fixed in this tool). The tests say \
            nothing about the variance or the distribution of Y, only about the shape of its mean as a function \
            of X.

            Common setup
            Before every test, Y and each column of X are centered, so the linear null hypothesis includes an \
            intercept. Rows with a missing value in Y or in any X of that row are dropped (testwise deletion). \
            All tests are deterministic: repeated runs on the same data give the same numbers.

            Modes
            - Pairwise: each (X, Y) pair is tested with a single regressor. Additivity (Parents) is not \
            meaningful here and shows "Skipped".
            - Conditional: each Y is regressed on the whole treatment set at once (or on each k-subset if a \
            [k] or [k..m] suffix is given). Additivity (Parents) is meaningful whenever the set has 2+ regressors.

            Fast vs. slow tests
            RESET, Moment, and Additive are closed-form F or chi-square tests and always run. CV and Additivity \
            (Parents) fit nonlinear predictors inside 10-fold cross-validation and are opt-in: check "Include CV \
            (slow)" and/or "Include Additivity (Parents)". CV is by far the slower of the two; Additivity only \
            does work on rows with 2+ regressors.

            Show Stats
            Selecting a row and pressing "Show Stats" reports, for each test, the raw statistic, the p-value, and \
            the reject decision. The meaning of the statistic differs by test; each tab says what it is.""";

    private static final String NOTES_RESET = """
            RESET (Ramsey's Regression Equation Specification Error Test)

            What it does
            Fit the linear regression of Y on X and take the fitted values yhat. Then refit with yhat^2 and yhat^3 \
            added as two extra regressors. If the extra terms significantly reduce the residual sum of squares, \
            the linear specification is rejected.

            Statistic
            F with (2, n - d - 2) degrees of freedom, where d is the number of regressors. Larger F means the \
            squared and cubed fitted values explain more of what the linear fit missed. Needs n >= 10.

            What it is good at
            Smooth curvature along the direction of the linear fit, which is the typical "the relationship bends" \
            case. With a single regressor, yhat is just a rescaled X, so RESET is essentially a test for x^2 and \
            x^3 terms.

            Limitations
            - It only looks along the fitted linear direction. If the linear fit is nearly flat (a symmetric \
            U-shape, or a pure interaction with no main effects), yhat carries little information and RESET can \
            have low power where Moment or Additive still reject.
            - Assumes homoskedastic errors. Under a linear mean with heteroskedastic noise it over-rejects (about \
            10-35 percent at alpha = 0.05 in the calibration harness).""";

    private static final String NOTES_CV = """
            CV (cross-validated linear vs. nonlinear prediction)

            What it does
            Split the rows into 10 folds (fewer if n is small). On each fold, fit a linear ridge regression and a \
            nonlinear kernel ridge regression (RBF kernel approximated by 200 random Fourier features, bandwidth \
            set to the median pairwise distance among training rows) on the training part, and compute both \
            held-out mean squared errors on the test part. If the nonlinear predictor is reliably better across \
            folds, linearity is rejected.

            Statistic
            A one-sided paired t-statistic across the folds for "linear MSE minus nonlinear MSE is positive", \
            with kfold - 1 degrees of freedom. Needs n >= 20.

            What it is good at
            It is model-agnostic: any smooth nonlinearity that an RBF predictor can exploit, including \
            interactions, can trigger it. Because both predictors face exactly the same noise in each fold, it \
            keeps its false-rejection rate under heteroskedasticity, which the F/LM tests do not.

            Limitations
            - It is a prediction comparison, not a specification test, so with small n or weak nonlinearity the \
            nonlinear predictor's extra variance can cost it the comparison; expect less power than the F/LM \
            tests on clean homoskedastic data.
            - Only kfold paired differences enter the t-test, so the p-value is coarse.
            - It is the slowest test: it is run only when "Include CV (slow)" is checked.""";

    private static final String NOTES_MOMENT = """
            Moment (conditional-moment / nonlinear-features LM test)

            What it does
            Fit the linear regression and take its residuals e. Build a matrix G of nonlinear features of X: the \
            square and cube of every regressor, plus the pairwise product of every two regressors. Regress e on G. \
            Under linearity the residuals should be unpredictable from any function of X; if G explains them, \
            linearity is rejected.

            Statistic
            Lagrange multiplier statistic LM = n * R^2 from the residual-on-G regression, compared with a \
            chi-square distribution with df = number of columns of G (2d + d(d-1)/2 for d regressors). Needs \
            n >= 20.

            What it is good at
            Polynomial-type curvature in each regressor, and pairwise interactions between regressors, tested \
            directly rather than through the fitted linear index. Unlike RESET, it does not need the linear fit \
            to be informative, so it retains power for symmetric shapes and pure interactions.

            Limitations
            - The cube terms make it sensitive to outliers and heavy-tailed X.
            - The number of features grows quadratically in d, so with many regressors and modest n the \
            chi-square approximation weakens and power is spread thin.
            - Assumes homoskedastic errors; over-rejects under heteroskedastic linear data.""";

    private static final String NOTES_ADDITIVE = """
            Additive (additive-component hinge test)

            What it does
            Fit the linear regression. Then, for each regressor separately, add three hinge functions \
            max(0, x - knot) with knots at the 25th, 50th, and 75th percentiles of that regressor, and refit. \
            This is a small additive spline model: each regressor gets its own piecewise-linear curve, with no \
            interaction terms. If the hinges significantly reduce the residual sum of squares, linearity is \
            rejected.

            Statistic
            F with (3d, n - 4d) degrees of freedom, where d is the number of regressors. Needs n >= 20.

            What it is good at
            Per-variable curvature that is not well described by low-order polynomials: thresholds, saturation, \
            kinks, and other piecewise shapes. It is also the test whose alternative most resembles a \
            generalized additive model, so a rejection here with the Additivity (Parents) column reading \
            "Additive OK" is a good indication that an additive nonlinear model would fit.

            Limitations
            - No interaction terms, so it has essentially no power against pure interactions such as \
            y = x1 * x2 with no main effects; Moment and CV are the tests to look at for that.
            - Three knots per regressor is coarse; high-frequency oscillation between knots can be missed.
            - Assumes homoskedastic errors; over-rejects under heteroskedastic linear data.""";

    private static final String NOTES_ADDITIVITY = """
            Additivity (Parents)

            This column answers a different question from the other four. It does not ask whether E(Y|X) is \
            linear; it asks whether the (possibly nonlinear) effects of the regressors combine additively, \
            f1(x1) + f2(x2) + ..., or whether interactions among them improve prediction. It needs 2+ regressors \
            and n >= 30, and runs only when "Include Additivity (Parents)" is checked; otherwise it shows "Skipped".

            What it does
            Two nested models are compared by 10-fold cross-validation. The additive model uses, per regressor, \
            a linear term, six hinge functions at quantile knots, and a one-dimensional random Fourier feature \
            block, so it can represent smooth additive nonlinearity of each regressor separately. The full model \
            is the same additive design plus a joint multivariate random Fourier feature block, which is the \
            only part that can represent interactions. Because the models are nested, the joint block only has \
            to explain the non-additive remainder. A single ridge penalty is chosen by GCV on the full model \
            and shared by both, which makes the comparison conservative under additive truth.

            Statistic
            The mean per-observation cross-validated improvement, additive squared error minus full squared \
            error, pooled over all held-out points; positive favors non-additivity. The p-value is from a \
            one-sided Wilcoxon signed-rank test on those pooled per-observation differences (rank-based because \
            squared-error differences are heavy-tailed). "Non-additive" means the interaction block improved \
            out-of-sample prediction at alpha = 0.05.

            Reading it
            - "Additive OK" with some of RESET/Moment/Additive rejecting suggests an additive nonlinear model \
            (a GAM-style fit) is adequate.
            - "Non-additive" means interactions among the listed regressors matter for predicting Y.
            - Like CV, both models face the same noise, so this comparison keeps its size under \
            heteroskedasticity.""";

    private static final String NOTES_INTERPRETING = """
            Direction matters
            The verdict is direction-relative. A linear non-Gaussian pair has a linear mean from cause to effect \
            but a genuinely nonlinear mean in the reverse regression, so a rejection can mean "nonlinear \
            relationship" or "linear non-Gaussian pair tested in the anticausal direction." Testing both \
            directions disambiguates: linear both ways suggests a linear near-Gaussian pair; linear one way only \
            suggests a linear non-Gaussian pair, with the linear direction the plausible causal one; nonlinear \
            both ways suggests a genuinely nonlinear relationship.

            Reading disagreements between tests
            RESET, Moment, and Additive assume homoskedastic errors and over-reject when errors are \
            heteroskedastic but the mean is linear. CV and Additivity (Parents) keep their false-rejection rates \
            under heteroskedasticity, so RESET/Moment/Additive rejecting while CV does not is a hint of \
            heteroskedasticity rather than a nonlinear mean; all tests rejecting together is stronger evidence \
            that the mean really is nonlinear.

            Which test sees what
            - Smooth curvature along the fitted line: all four; RESET is the most direct.
            - Symmetric shapes with a flat linear fit (e.g. a U): Moment, Additive, CV; RESET may miss.
            - Thresholds, kinks, saturation: Additive is the most direct; CV also sees them.
            - Pure interactions between regressors (conditional mode only): Moment (via product terms) and CV; \
            RESET and Additive have little power. Additivity (Parents) is the dedicated test.
            - Heteroskedastic but linear mean: RESET, Moment, and Additive tend to reject; CV and Additivity \
            (Parents) do not.

            Multiple comparisons
            Each row is tested at alpha = 0.05 with no correction. In pairwise mode with many variables, expect \
            roughly 5 percent of truly linear pairs to read "Nonlinear" for each fast test; look for agreement \
            across tests and for small p-values in "Show Stats" rather than trusting any single rejection.""";

    private ResultRow runOne(int index, List<Node> xs, Node y, double alpha, int kfold,
                             boolean doCv, boolean doAdditivity) {
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

        NonlinearityTests.TestResult cv =
                doCv ? NonlinearityTests.cvLinearVsNonlinear(yy, XX, kfold, alpha) : null;

        NonlinearityTests.TestResult mom =
                NonlinearityTests.conditionalMomentTest(yy, XX, alpha);

        NonlinearityTests.TestResult add =
                NonlinearityTests.additiveHingeTest(yy, XX, alpha);

        // NEW: additivity check (additive hinge predictor vs full RFF predictor)
        NonlinearityTests.TestResult addit =
                doAdditivity ? NonlinearityTests.cvAdditiveVsRff(yy, XX, kfold, alpha) : null;

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

    /**
     * Copies the results table (header plus all rows, in current model order) to the system
     * clipboard as tab-separated text, and briefly flashes "Copied" on the given button as
     * feedback. Cell values are the same formatted strings shown in the table.
     */
    private void copyTableToClipboard(JButton feedback) {
        StringBuilder sb = new StringBuilder();

        for (int c = 0; c < tableModel.getColumnCount(); c++) {
            if (c > 0) sb.append('\t');
            sb.append(tableModel.getColumnName(c));
        }
        sb.append('\n');

        for (int r = 0; r < tableModel.getRowCount(); r++) {
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                if (c > 0) sb.append('\t');
                Object v = tableModel.getValueAt(r, c);
                sb.append(v == null ? "" : v.toString());
            }
            sb.append('\n');
        }

        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);

        String label = feedback.getText();
        feedback.setText("Copied");
        javax.swing.Timer t = new javax.swing.Timer(1200, ev -> feedback.setText(label));
        t.setRepeats(false);
        t.start();
    }

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