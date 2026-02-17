/// ////////////////////////////////////////////////////////////////////////////
// IndependenceFactsDslEditor.java
//
// A modern replacement for the legacy IndependenceFactsEditor:
// - Free-form DSL in a text area (one template per line)
// - Expands templates using ? (single var wildcard) and + (choose-k conditioning vars)
// - Supports either a statistical independence test (chosen via dropdown + Params)
//   or m-separation against the current graph (if provided)
// - Highlights parse errors with red squiggly underline
// - Warns on duplicates; enforces Z ∩ {x,y} = ∅
// - Limits expansion count to protect UI
//
// NOTES / ASSUMPTIONS:
// 1) This is designed to live in tetradapp (Swing UI) codebase.
// 2) It expects typical Tetrad classes to exist (IndependenceResult, CachedIndependenceQueries,
//    IndependenceTestModels, ParamDescriptions, ParameterComponents, etc.).
// 3) For m-separation, you MUST wire `isMSeparated(...)` to your preferred implementation.
//    (There are multiple in Tetrad; pick the one you already trust.)
// 4) For statistical evaluation, this uses CachedIndependenceQueries if available.
//
// Joe’s requested semantics:
// - x,y unordered is fine (IndependenceFact already treats (x,y) as unordered in equals/compareTo).
// - One template per line.
// - Enforce Z ∩ {x,y} = ∅ (parse error).
// - Duplicate expanded facts => warning (non-fatal), run still proceeds.
// - Parse errors squiggly underlined.
//
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.IndependenceFactsDslModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.ParameterComponents;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static edu.cmu.tetradapp.util.ParameterComponents.toArray;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public final class IndependenceFactsDslEditor extends JPanel {

    // -------------------- state persistence --------------------
    private static final String PREF_NODE_PATH = "/edu/cmu/tetradapp/editor/IndependenceFactsDslEditor";

    // when model == null, we persist to userRoot() as requested
    private static final Preferences ROOT_PREFS =
            Preferences.userRoot().node(PREF_NODE_PATH);

    private final IndependenceFactsDslModel model; // may be null

    // keys (shared by model + prefs)
    private static final String KEY_DSL_TEXT = "dslText";
    private static final String KEY_ENGINE = "engine";     // class name or MSEP_ENGINE_LABEL
    private static final String KEY_LIMIT = "limit";
    private static final String KEY_VERBOSE = "dsl.verbose";

    // Keep your existing test key if you like, but KEY_ENGINE replaces the need for PREF_KEY_TEST.

    private static final String MSEP_ENGINE_LABEL = "m-separation (current graph)";
    // -------------------- UI --------------------
    private final JComboBox<Object> engineCombo = new JComboBox<>();
    private final JButton paramsButton = new JButton("Params");
    private final JButton previewButton = new JButton("Preview");
    private final JButton runButton = new JButton("Run");
    private final JButton clearButton = new JButton("Clear");
    //    private final JCheckBox showPValues = new JCheckBox("Show P-values / Scores");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel warningsLabel = new JLabel(" ");
    private final JTextPane dslPane = new JTextPane();
    private final JScrollPane dslScroll = new JScrollPane(dslPane);
    private final JTable resultsTable;
    private final ResultsTableModel resultsModel = new ResultsTableModel();
    private final IntTextField limitField;
    private final JCheckBox verboseBox = new JCheckBox("Verbose");

    // -------------------- context --------------------
    private final DataModel dataModel;
    private final Graph graph;                    // optional; enables m-sep engine
    private final Parameters parameters;          // shared parameter bag
    // -------------------- formatting --------------------
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    // -------------------- parse highlighting --------------------
    private final Highlighter highlighter;
    private final Highlighter.HighlightPainter errorPainter = new WavyUnderlineHighlightPainter(new Color(200, 0, 0));
    private final Highlighter.HighlightPainter warnPainter = new WavyUnderlineHighlightPainter(new Color(170, 120, 0));
    // -------------------- statistical engine wiring --------------------
    private IndependenceWrapper independenceWrapper;
    private IndependenceTest independenceTest;
    private CachedIndependenceQueries Q;
    // -------------------- last preview/run expansion --------------------
    private List<FactSpec> lastExpanded = List.of();

    // -------------------- public constructors --------------------
    private List<ParseProblem> lastProblems = List.of();

    private boolean restoring = false;

    /**
     * Minimal: data-only. This supports statistical tests only.
     */
    public IndependenceFactsDslEditor(DataModel dataModel, Parameters parameters) {
        this(null, dataModel, null, parameters, null);
    }

    /**
     * Data + graph: enables both statistical tests and m-separation.
     */

    public IndependenceFactsDslEditor(DataModel dataModel, Graph graph, Parameters parameters) {
        this(null, dataModel, graph, parameters, null);
    }


    /**
     * Constructs an instance of the {@code IndependenceFactsDslEditor} class using the provided
     * {@code IndependenceFactsDslModel}. This constructor facilitates initialization by extracting
     * the necessary components from the given model, such as the data model, graph, parameters,
     * and cached queries (if available), to properly set up the editor.
     *
     * @param model the {@code IndependenceFactsDslModel} containing the data model, graph,
     *              parameters, and optional cached queries to be used during initialization
     */
    public IndependenceFactsDslEditor(IndependenceFactsDslModel model) {
        this(model, model.getDataModel(), model.getGraph(), model.getParameters(), model.getCachedQueriesOrNull());
    }

    // -------------------- UI build --------------------

    /**
     * Data + graph + cached queries (optional): if provided, statistical eval uses cache.
     */
    public IndependenceFactsDslEditor(IndependenceFactsDslModel model,
                                      DataModel dataModel,
                                      Graph graph,
                                      Parameters parameters,
                                      CachedIndependenceQueries cachedQueriesOrNull) {

        if (dataModel == null) throw new NullPointerException("dataModel");
        if (parameters == null) throw new NullPointerException("parameters");

        this.model = model;
        this.dataModel = dataModel;
        this.graph = graph;
        this.parameters = parameters;

        this.Q = cachedQueriesOrNull;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(1100, 650));

        // DSL pane setup
        dslPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        dslPane.setText(defaultDslText());
        this.highlighter = dslPane.getHighlighter();

        // Results table
        resultsTable = new JTable(resultsModel);
        resultsTable.setRowSorter(new TableRowSorter<>(resultsModel));
        resultsTable.setFillsViewportHeight(true);

        configureResultsTableColumns();

        // list limit
        int initialLimit = (model != null ? modelGetInt(KEY_LIMIT, 10000) : ROOT_PREFS.getInt(KEY_LIMIT, 10000));
        limitField = new IntTextField(initialLimit, 7);

        limitField.setFilter((value, oldValue) -> {
            if (value < 1) return oldValue;
            // let the field update, then persist everything consistently
            SwingUtilities.invokeLater(this::persistState);
            return value;
        });

        buildUI();
        refreshEngines();
        restoreState();              // only restore once
        getEvaluatorFromSelection(true); // optional: make paramsButton correct for restored engine

        // lightweight live parse feedback
        dslPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                liveParse();
                persistState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                liveParse();
                persistState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                liveParse();
                persistState();
            }
        });

        // initial live parse
        liveParse();
    }

    @Override
    public void removeNotify() {
        // Called when this component is removed from the Swing component hierarchy
        // (e.g., tab closed, editor swapped out, session window closed).
        try {
            persistState();
        } finally {
            super.removeNotify();
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private void restoreState() {
        restoring = true;
        try {
            // Prefer MODEL when we have one; prefs are only a fallback
            String dsl = (model != null)
                    ? trimToNull(modelGet(KEY_DSL_TEXT))
                    : trimToNull(ROOT_PREFS.get(KEY_DSL_TEXT, null));

            String engine = (model != null)
                    ? trimToNull(modelGet(KEY_ENGINE))
                    : trimToNull(ROOT_PREFS.get(KEY_ENGINE, null));

            Integer limitPref = (model != null)
                    ? modelGetInt(KEY_LIMIT, 10000)
                    : ROOT_PREFS.getInt(KEY_LIMIT, 10000);

            dslPane.setText(dsl != null ? dsl : defaultDslText());
            limitField.setValue(limitPref);

            restoreEngineSelection(engine);

            int verboseInt;

            if (model != null) {
                verboseInt = modelGetInt(KEY_VERBOSE, 0);
            } else {
                verboseInt = ROOT_PREFS.getInt(KEY_VERBOSE, 0);
            }

            verboseBox.setSelected(verboseInt == 1);

        } finally {
            restoring = false;
        }
    }

    private void restoreEngineSelection(String engine) {
        if (engine == null) return;

        if (MSEP_ENGINE_LABEL.equals(engine)) {
            if (graph != null) engineCombo.setSelectedItem(MSEP_ENGINE_LABEL);
            return;
        }

        for (int i = 0; i < engineCombo.getItemCount(); i++) {
            Object it = engineCombo.getItemAt(i);
            if (it instanceof IndependenceTestModel m) {
                String wrapperName = m.getIndependenceTest().clazz().getName();
                if (engine.equals(wrapperName)) {
                    engineCombo.setSelectedItem(m);
                    return;
                }
            }
        }
    }

    private void persistState() {
        if (restoring) return;

        String dsl = dslPane.getText();

        Object sel = engineCombo.getSelectedItem();
        String engine = null;

        if (sel == null) {
            engine = null;
        } else if (MSEP_ENGINE_LABEL.equals(sel)) {
            engine = MSEP_ENGINE_LABEL;
        } else if (sel instanceof IndependenceTestModel m) {
            engine = m.getIndependenceTest().clazz().getName();
        } else {
            engine = String.valueOf(sel);
        }

        engine = trimToNull(engine);

        int limit = limitField.getValue();

        if (model != null) {
            modelPut(KEY_DSL_TEXT, dsl);
            if (engine != null) modelPut(KEY_ENGINE, engine);     // ✅ don’t write blank
            modelPutInt(KEY_LIMIT, limit);
        } else {
            ROOT_PREFS.put(KEY_DSL_TEXT, dsl);
            if (engine != null) ROOT_PREFS.put(KEY_ENGINE, engine); // ✅ don’t write blank
            ROOT_PREFS.putInt(KEY_LIMIT, limit);
            try { ROOT_PREFS.flush(); } catch (Exception ignored) {}
        }

        boolean verbose = verboseBox.isSelected();

        if (model != null) {
            modelPutInt(KEY_VERBOSE, verbose ? 1 : 0);
        } else {
            ROOT_PREFS.putInt(KEY_VERBOSE, verbose ? 1 : 0);
            try { ROOT_PREFS.flush(); } catch (Exception ignored) {}
        }
    }

    private String modelGet(String key) {
        // You implement these in IndependenceFactsDslModel (recommended)
        // e.g., model.getEditorState().get(key), or direct fields/getters.
        return model.getEditorStateString(key); // <-- replace with your actual
    }

    private int modelGetInt(String key, int defaultValue) {
        return model.getEditorStateInt(key, defaultValue); // <-- replace with your actual
    }

    private void modelPut(String key, String value) {
        model.setEditorStateString(key, value); // <-- replace with your actual
    }

    private void modelPutInt(String key, int value) {
        model.setEditorStateInt(key, value); // <-- replace with your actual
    }

    private static Token parseToken(String s) {
        if (s.equals("?")) return new Token(TokenKind.QMARK, s);
        if (s.equals("+")) return new Token(TokenKind.PLUS, s);
        return new Token(TokenKind.VAR, s);
    }

    private static boolean isGlob(String s) {
        return s != null && s.indexOf('*') >= 0;
    }


    // -------------------- glob/wildcard helpers --------------------

    private static String globToRegex(String glob) {
        // Anchor, treat '*' as ".*", escape everything else
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else {
                // escape regex metacharacters
                if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) sb.append("\\");
                sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static List<String> matchGlob(String glob, List<String> varNames) {
        String regex = globToRegex(glob);
        return varNames.stream()
                .filter(v -> v != null && v.matches(regex))
                .collect(Collectors.toList());
    }

    private static void validateTokenVar(Token tok,
                                         List<String> varNames,
                                         List<ParseProblem> problems,
                                         int lineIndex,
                                         int baseOffset,
                                         String rawLine) {
        if (tok.kind != TokenKind.VAR) return;

        if (!isGlob(tok.text)) {
            if (!varNames.contains(tok.text)) {
                // underline the whole line; keeping it simple
                problems.add(new ParseProblem(
                        Severity.ERROR,
                        "Unknown variable: '" + tok.text + "'.",
                        baseOffset,
                        baseOffset + rawLine.length(),
                        lineIndex
                ));
            }
            return;
        }

        // Glob: require at least one match
        List<String> matches = matchGlob(tok.text, varNames);
        if (matches.isEmpty()) {
            problems.add(new ParseProblem(
                    Severity.ERROR,
                    "No variables match pattern: '" + tok.text + "'.",
                    baseOffset,
                    baseOffset + rawLine.length(),
                    lineIndex
            ));
        }
    }

    private static ParseProblem error(String msg, int lineIndex, int start, int end) {
        return new ParseProblem(Severity.ERROR, msg, start, end, lineIndex);
    }

    private static DataType guessDataType(DataModel dm) {
        if (dm instanceof DataSet ds) {
            boolean hasContinuous = ds.getVariables().stream().anyMatch(v -> v instanceof ContinuousVariable);
            boolean hasDiscrete = ds.getVariables().stream().anyMatch(v -> v instanceof DiscreteVariable);
            if (hasContinuous && hasDiscrete) return DataType.Mixed;
            if (hasDiscrete) return DataType.Discrete;
            return DataType.Continuous;
        }
        return DataType.Continuous;
    }

    private static List<String> getVariableNames(DataModel dm) {
        if (dm instanceof DataSet ds) {
            return ds.getVariables().stream().map(Node::getName).collect(Collectors.toList());
        }
        if (dm instanceof ICovarianceMatrix cm) {
            return cm.getVariableNames();
        }
        // fallback: try to get variables via a dummy dataset interface if present
        return List.of();
    }

    // -------------------- engines --------------------

    private static String defaultDslText() {
        return """
                
                
                # One template per line.
                # Supported forms:
                #   X _||_ Y
                #   X _||_ Y | Z1, Z2
                #   Ind(X, Y)
                #   Ind(X, Y | Z1, Z2)
                #
                # Wildcards:
                #   ?  = pick one variable      
                #   +  = pick one variable into Z (choose-k where k = number of '+' tokens)
                #   *  = expand variables names with glob pattern, X* = X1,..,Xn e.g.
                #
                # Examples:
                #   ? _||_ ?                 # all ordered pairs (x != y); unordered is handled by IndependenceFact semantics
                #   ? _||_ ? | +             # all pairs with |Z| = 1
                #   X _||_ ? | Z, +, +       # X with everyone, conditioning on {Z} plus 2 others
                """;
    }

    private static int[] computeLineStartOffsets(String text) {
        // lineStarts[i] = offset of start of line i
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') starts.add(i + 1);
        }
        int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) out[i] = starts.get(i);
        return out;
    }

    /**
     * Converts Ind(X, Y | Z1, Z2) to "X _||_ Y | Z1, Z2".
     * Leaves already-normal strings unchanged.
     */
    private static String normalizeIndForm(String line) {
        String s = line.trim();
        if (!s.startsWith("Ind(") && !s.startsWith("ind(")) return line;

        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < 0 || close <= open) return line;

        String inside = s.substring(open + 1, close).trim(); // "X, Y | Z"
        // split on first '|'
        String left;
        String z = "";
        int bar = inside.indexOf('|');
        if (bar >= 0) {
            left = inside.substring(0, bar).trim();
            z = inside.substring(bar + 1).trim();
        } else {
            left = inside.trim();
        }

        // left should be "X, Y"
        String[] xy = left.split(",");
        if (xy.length < 2) return line;

        String x = xy[0].trim();
        String y = xy[1].trim();

        if (z.isBlank()) return x + " _||_ " + y;
        return x + " _||_ " + y + " | " + z;
    }

    /**
     * Choose k elements from pool (combinations, order-insensitive).
     */
    private static List<List<String>> chooseK(List<String> pool, int k) {
        if (k < 0) return List.of();
        if (k == 0) return List.of(List.of());
        if (pool.isEmpty() || k > pool.size()) return List.of();

        List<List<String>> out = new ArrayList<>();
        int n = pool.size();
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;

        while (true) {
            List<String> comb = new ArrayList<>(k);
            for (int i = 0; i < k; i++) comb.add(pool.get(idx[i]));
            out.add(comb);

            int i = k - 1;
            while (i >= 0 && idx[i] == n - k + i) i--;
            if (i < 0) break;
            idx[i]++;
            for (int j = i + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
        }
        return out;
    }

    private static JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());
        return createParamsPanel(testParameters, params);
    }

    public static JPanel createParamsPanel(Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        Box paramsBox = Box.createVerticalBox();
        Box[] boxes = toArray(createParameterComponents(params, parameters));
        if (boxes.length == 0) {
            paramsBox.add(new JLabel("(No parameters.)"));
        } else {
            int lastIndex = boxes.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                paramsBox.add(boxes[i]);
                paramsBox.add(Box.createVerticalStrut(10));
            }
            paramsBox.add(boxes[lastIndex]);
        }

        panel.add(new PaddingPanel(paramsBox), BorderLayout.CENTER);
        return panel;
    }

    // -------------------- preview/run --------------------

    private static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescriptions.get(e)),
                        (u, v) -> {
                            throw new IllegalStateException("Duplicate key " + u);
                        },
                        TreeMap::new
                ));
    }

    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc) {
        if (paramDesc == null) {
            // Fallback: treat as string
            JComponent c = ParameterComponents.getStringField(parameter, parameters, "");
            Box row = Box.createHorizontalBox();
            row.add(new JLabel(parameter));
            row.add(Box.createHorizontalGlue());
            row.add(c);
            return row;
        }

        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();

        if (defaultValue instanceof Double) {
            component = ParameterComponents.getDoubleField(parameter, parameters,
                    (Double) defaultValue,
                    paramDesc.getLowerBoundDouble(),
                    paramDesc.getUpperBoundDouble());
        } else if (defaultValue instanceof Integer) {
            component = ParameterComponents.getIntTextField(parameter, parameters,
                    (Integer) defaultValue,
                    paramDesc.getLowerBoundInt(),
                    paramDesc.getUpperBoundInt());
        } else if (defaultValue instanceof Long) {
            // LongTextField exists in tetradapp.util in most branches; if yours differs, adjust.
            component = ParameterComponents.getLongTextField(parameter, parameters,
                    (Long) defaultValue,
                    paramDesc.getLowerBoundLong(),
                    paramDesc.getUpperBoundLong());
        } else if (defaultValue instanceof Boolean) {
            component = ParameterComponents.getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
        } else if (defaultValue instanceof String) {
            component = ParameterComponents.getStringField(parameter, parameters, (String) defaultValue);
        } else {
            throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
        }

        Box paramRow = Box.createHorizontalBox();
        JLabel paramLabel = new JLabel(paramDesc.getShortDescription());
        String longDescription = paramDesc.getLongDescription();
        if (longDescription != null) paramLabel.setToolTipText(longDescription);

        paramRow.add(paramLabel);
        paramRow.add(Box.createHorizontalGlue());
        paramRow.add(component);
        return paramRow;
    }

    // -------------------- parsing + expansion --------------------

    private void configureResultsTableColumns() {
        // Turn off auto-resize so horizontal scrolling works and preferred widths matter.
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Optional: nicer row height for readability
        resultsTable.setRowHeight(Math.max(resultsTable.getRowHeight(), 20));

        // Column indices: "#", "Fact", "Engine", "Result", "p-value / score", "Error"
        int[] pref = {40, 200, 90, 110, 130, 200};

        for (int i = 0; i < pref.length && i < resultsTable.getColumnModel().getColumnCount(); i++) {
            var col = resultsTable.getColumnModel().getColumn(i);
            col.setPreferredWidth(pref[i]);
        }

        // Tighten a couple columns so they don't expand unexpectedly.
        resultsTable.getColumnModel().getColumn(0).setMinWidth(35);
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(60);

        resultsTable.getColumnModel().getColumn(2).setMinWidth(70);
        resultsTable.getColumnModel().getColumn(2).setMaxWidth(140);

        resultsTable.getColumnModel().getColumn(3).setMinWidth(90);
        resultsTable.getColumnModel().getColumn(3).setMaxWidth(160);

        resultsTable.getColumnModel().getColumn(4).setMinWidth(120);
        resultsTable.getColumnModel().getColumn(4).setMaxWidth(180);
    }

    private void buildUI() {
        add(buildTopControls(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildDslPanel(),
                buildResultsPanel());

        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JComponent buildTopControls() {
        Box row = Box.createHorizontalBox();

        row.add(new JLabel("Engine: "));
        engineCombo.setPreferredSize(new Dimension(320, 24));
        row.add(engineCombo);
        row.add(Box.createHorizontalStrut(6));
        row.add(paramsButton);

        row.add(Box.createHorizontalStrut(12));
        row.add(new JLabel("Limit: "));
        row.add(limitField);
        row.add(Box.createHorizontalStrut(12));
        row.add(verboseBox);
        row.add(Box.createHorizontalStrut(12));

        row.add(Box.createHorizontalGlue());
        row.add(previewButton);
        row.add(Box.createHorizontalStrut(6));
        row.add(runButton);
        row.add(Box.createHorizontalStrut(6));
        row.add(clearButton);


        JPanel p = new JPanel(new BorderLayout());
        p.add(row, BorderLayout.CENTER);

        // wiring
        engineCombo.addActionListener(e -> {
            FactEvaluator ev = getEvaluatorFromSelection(false);
            paramsButton.setEnabled(ev != null && ev.hasParams());
            // reset model; require preview/run
            resultsModel.setRows(List.of());
            persistState();
        });

        paramsButton.addActionListener(e -> {
            if (independenceWrapper == null) {
                JOptionPane.showMessageDialog(this, "Choose a statistical independence test first.");
                return;
            }
            JOptionPane pane = new JOptionPane(
                    createParamsPanel(independenceWrapper, parameters),
                    JOptionPane.PLAIN_MESSAGE
            );
            pane.createDialog(this, "Set Parameters").setVisible(true);

            // After params change, rebuild test/cache if statistical engine selected.
            getEvaluatorFromSelection(true);
        });

        previewButton.addActionListener(e -> preview());
        runButton.addActionListener(e -> run());
        clearButton.addActionListener(e -> {
            dslPane.setText("");
            resultsModel.setRows(List.of());
            statusLabel.setText("Cleared.");
            warningsLabel.setText(" ");
            clearHighlights();
            persistState();
        });

        verboseBox.addActionListener(e -> persistState());

        return p;
    }

    private JComponent buildDslPanel() {
        dslScroll.setBorder(BorderFactory.createTitledBorder("Independence templates (one per line)"));
        dslScroll.setPreferredSize(new Dimension(400, 520));
        return dslScroll;
    }

    private JComponent buildResultsPanel() {
        JScrollPane scroll = new JScrollPane(resultsTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Expanded facts and results"));
        scroll.setPreferredSize(new Dimension(520, 520));
        return scroll;
    }

    private JComponent buildBottomBar() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setBorder(new EmptyBorder(4, 2, 2, 2));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
        warningsLabel.setFont(warningsLabel.getFont().deriveFont(Font.PLAIN));
        warningsLabel.setForeground(new Color(130, 90, 0));
        p.add(statusLabel);
        p.add(warningsLabel);
        return p;
    }

    private void refreshEngines() {
        restoring = true;
        try {
            engineCombo.removeAllItems();

            DataType dt = guessDataType(dataModel);
            List<IndependenceTestModel> models =
                    new ArrayList<>(IndependenceTestModels.getInstance().getModels(dt));

            models.removeFirst();
            for (IndependenceTestModel m : models) {
                engineCombo.addItem(m);
            }

            if (graph != null) {
                engineCombo.addItem(MSEP_ENGINE_LABEL);
            }

        } finally {
            restoring = false;
        }
    }

    // -------------------- evaluation interface --------------------

    /**
     * Returns an evaluator from current selection.
     *
     * @param forceRebuild if true, rebuild independence test/cache even if already set.
     */
    private FactEvaluator getEvaluatorFromSelection(boolean forceRebuild) {
        Object sel = engineCombo.getSelectedItem();
        if (sel == null) return null;

        // m-sep
        if (MSEP_ENGINE_LABEL.equals(sel)) {
            paramsButton.setEnabled(false);
            return new MsepFactEvaluator(graph);
        }

        // statistical
        if (sel instanceof IndependenceTestModel itm) {
            paramsButton.setEnabled(true);
            try {
                @SuppressWarnings("unchecked")
                Class<IndependenceWrapper> clazz =
                        (Class<IndependenceWrapper>) itm.getIndependenceTest().clazz();

                if (!forceRebuild && independenceWrapper != null && independenceWrapper.getClass().equals(clazz)
                        && independenceTest != null) {
                    return new StatisticalFactEvaluator(this::nodeInTestByName, this::checkIndependence);
                }

                independenceWrapper = clazz.getDeclaredConstructor().newInstance();
                independenceTest = independenceWrapper.getTest(dataModel, parameters);
                independenceTest.setVerbose(verboseBox.isSelected());

                // cache path:
                // If caller supplied a cache, keep it; otherwise, build one if available in your codebase.
                // Many Tetrad editors build CachedIndependenceQueries(test) or similar.
                // If you have a preferred constructor, swap it in.
                if (Q == null || forceRebuild) {
                    try {
                        Q = new CachedIndependenceQueries(independenceTest);
                    } catch (Throwable t) {
                        // If CachedIndependenceQueries constructor differs in your branch,
                        // just fall back to direct test calls below.
                        Q = null;
                    }
                }

                return new StatisticalFactEvaluator(this::nodeInTestByName, this::checkIndependence);
            } catch (InstantiationException | IllegalAccessException |
                     InvocationTargetException | NoSuchMethodException e) {
                TetradLogger.getInstance().log("Error building independence test: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        throw new IllegalStateException("Unknown engine selection: " + sel);
    }

    private IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Q != null) return Q.checkIndependence(x, y, z);
        if (independenceTest == null) throw new IllegalStateException("No statistical test selected.");
        return independenceTest.checkIndependence(x, y, z);
    }

    private Node nodeInTestByName(String name) {
        if (name == null) return null;
        IndependenceTest test = (Q != null ? Q.getTest() : independenceTest);
        if (test == null) return null;
        for (Node n : test.getVariables()) {
            if (n != null && Objects.equals(n.getName(), name)) return n;
        }
        return null;
    }

    // -------------------- results table model --------------------

    private void preview() {
        ParseAndExpandResult pr = parseAndExpand();
        lastExpanded = pr.expandedFacts;
        lastProblems = pr.problems;

        // show preview rows (no evaluation)
        List<ResultRow> rows = new ArrayList<>(lastExpanded.size());
        for (int i = 0; i < lastExpanded.size(); i++) {
            FactSpec fs = lastExpanded.get(i);
            rows.add(ResultRow.preview(i + 1, fs));
        }
        resultsModel.setRows(rows);

        updateStatusAfterParse(pr);
    }

    private void run() {
        ParseAndExpandResult pr = parseAndExpand();
        lastExpanded = pr.expandedFacts;
        lastProblems = pr.problems;

        updateStatusAfterParse(pr);

        boolean hasErrors = pr.problems.stream()
                .anyMatch(p -> p.severity == Severity.ERROR);

        if (hasErrors) {
            statusLabel.setText("Parse errors present. Fix underlined lines before running.");
            return;
        }

        FactEvaluator ev = getEvaluatorFromSelection(true);
        if (ev == null) {
            statusLabel.setText("No engine selected.");
            return;
        }

        final int limit = limitField.getValue();

        new WatchedProcess() {
            @Override
            public void watch() {

                List<ResultRow> out = new ArrayList<>(Math.min(limit, lastExpanded.size()));
                int idx = 1;

                for (FactSpec fs : lastExpanded) {
                    if (out.size() >= limit) break;

                    try {
                        IndependenceResult r = ev.evaluate(fs);
                        out.add(ResultRow.evaluated(idx++, fs, r, ev.name()));
                    } catch (Exception ex) {
                        out.add(ResultRow.error(idx++, fs, ex.getMessage(), ev.name()));
                    }
                }

                long ok = out.stream().filter(rr -> rr.kind == RowKind.RESULT).count();
                long err = out.stream().filter(rr -> rr.kind == RowKind.ERROR).count();

                SwingUtilities.invokeLater(() -> {
                    resultsModel.setRows(out);
                    statusLabel.setText("Ran " + out.size() + " facts. ok=" + ok + ", errors=" + err + ".");
                });
            }
        };
    }

    private ParseAndExpandResult parseAndExpand() {
        clearHighlights();

        String text = dslPane.getText();
        List<String> varNames = getVariableNames(dataModel);

        int limit = limitField.getValue();

        List<ParseProblem> problems = new ArrayList<>();
        List<FactSpec> expanded = new ArrayList<>();

        // Collect duplicates for warning
        Set<String> seenCanonical = new LinkedHashSet<>();
        int duplicates = 0;

        // Precompute line start offsets for highlighting
        int[] lineStarts = computeLineStartOffsets(text);

        String[] lines = text.split("\\R", -1);

        outer:
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String raw = lines[lineIdx];
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("//")) continue;

            int baseOffset = lineStarts[lineIdx];

            TemplateParseResult tpr = parseTemplateLine(line, lineIdx, baseOffset, varNames);
            problems.addAll(tpr.problems);

            if (tpr.template == null) continue; // parse failed

            List<FactSpec> ex = expandTemplate(tpr.template, varNames, limit - expanded.size());
            for (FactSpec fs : ex) {
                String canon = fs.canonicalKey();
                if (!seenCanonical.add(canon)) {
                    duplicates++;
                    continue;
                }
                expanded.add(fs);
                if (expanded.size() >= limit) break outer;
            }
        }

        if (duplicates > 0) {
            problems.add(new ParseProblem(
                    Severity.WARNING,
                    "Duplicate expanded facts suppressed: " + duplicates,
                    0, 0, -1
            ));
        }

        // Apply highlights for problems that have locations
        applyHighlights(problems);

        return new ParseAndExpandResult(expanded, problems);
    }

    // -------------------- template / spec types --------------------

    private void updateStatusAfterParse(ParseAndExpandResult pr) {
        long errs = pr.problems.stream().filter(p -> p.severity == Severity.ERROR).count();
        long warns = pr.problems.stream().filter(p -> p.severity == Severity.WARNING).count();

        statusLabel.setText("Expanded: " + pr.expandedFacts.size()
                + "   (errors=" + errs + ", warnings=" + warns + ").");

        // show first few warnings concisely
        List<String> warnMsgs = pr.problems.stream()
                .filter(p -> p.severity == Severity.WARNING && p.message != null && !p.message.isBlank())
                .map(p -> p.message)
                .distinct()
                .limit(2)
                .collect(Collectors.toList());

        warningsLabel.setText(warnMsgs.isEmpty() ? " " : "Warning: " + String.join("   |   ", warnMsgs));
    }

    private void liveParse() {
        // Lightweight: parse-only highlighting; no table update.
        ParseAndExpandResult pr = parseAndExpand();
        lastProblems = pr.problems;

        // Only update status line; don't overwrite results table during typing.
        long errs = pr.problems.stream().filter(p -> p.severity == Severity.ERROR).count();
        long warns = pr.problems.stream().filter(p -> p.severity == Severity.WARNING).count();
        statusLabel.setText("Typing… (errors=" + errs + ", warnings=" + warns + ").");
    }

    /**
     * Supported line formats (examples):
     * X _||_ Y
     * X _||_ Y | Z1, Z2
     * Ind(X, Y)
     * Ind(X, Y | Z1, Z2)
     * <p>
     * Wildcards:
     * ?   = choose one variable (distinct)
     * +   = choose-k additional conditioning variables, where k = # of '+' tokens in Z
     * <p>
     * Examples:
     * ? _||_ ? | +          => all unordered pairs, conditioning set size 1
     * X _||_ ? | Z, +, +    => X with everyone, conditioning on {Z} plus 2 more vars
     */
    private TemplateParseResult parseTemplateLine(String line,
                                                  int lineIndex,
                                                  int baseOffset,
                                                  List<String> varNames) {

        List<ParseProblem> problems = new ArrayList<>();

        // Normalize Ind(...) form to the _||_ form for simplicity.
        String normalized = normalizeIndForm(line);

        // Find "_||_" separator
        int sep = normalized.indexOf("_||_");
        if (sep < 0) {
            problems.add(error("Expected '_||_' or 'Ind(...)' form.", lineIndex, baseOffset, baseOffset + line.length()));
            return new TemplateParseResult(null, problems);
        }

        String left = normalized.substring(0, sep).trim();
        String rest = normalized.substring(sep + 4).trim();

        if (left.isEmpty()) {
            problems.add(error("Missing left variable.", lineIndex, baseOffset, baseOffset + Math.min(1, line.length())));
            return new TemplateParseResult(null, problems);
        }

        // Split rest into y and optional conditioning set
        String yPart;
        String zPart = "";

        int bar = rest.indexOf('|');
        if (bar >= 0) {
            yPart = rest.substring(0, bar).trim();
            zPart = rest.substring(bar + 1).trim();
        } else {
            yPart = rest.trim();
        }

        if (yPart.isEmpty()) {
            problems.add(error("Missing right variable.", lineIndex, baseOffset + sep, baseOffset + line.length()));
            return new TemplateParseResult(null, problems);
        }

        // Parse tokens: allow "?" or "+" or variable name
        Token xTok = parseToken(left);
        Token yTok = parseToken(yPart);

        List<Token> zToks = new ArrayList<>();
        if (!zPart.isBlank()) {
            for (String s : zPart.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                zToks.add(parseToken(t));
            }
        }

        // Validate plus placement: '+' only allowed in Z (not in x or y)
        if (xTok.kind == TokenKind.PLUS || yTok.kind == TokenKind.PLUS) {
            problems.add(error("Wildcard '+' is only allowed in the conditioning set (after '|').",
                    lineIndex, baseOffset, baseOffset + line.length()));
            return new TemplateParseResult(null, problems);
        }

        // Validate fixed variable names exist
        validateTokenVar(xTok, varNames, problems, lineIndex, baseOffset, line);
        validateTokenVar(yTok, varNames, problems, lineIndex, baseOffset, line);
        for (Token zt : zToks) validateTokenVar(zt, varNames, problems, lineIndex, baseOffset, line);

        // Enforce '+' tokens appear only in Z (OK) and behave as choose-k.
        // Enforce Z ∩ {x,y} = ∅ for fixed vars immediately; for wildcards, enforced during expansion.
//        if (xTok.kind == TokenKind.VAR && yTok.kind == TokenKind.VAR && xTok.text.equals(yTok.text)) {
//            problems.add(error("x and y must be different variables.", lineIndex, baseOffset, baseOffset + line.length()));
//            return new TemplateParseResult(null, problems);
//        }

        if (xTok.kind == TokenKind.VAR && yTok.kind == TokenKind.VAR
                && !isGlob(xTok.text) && !isGlob(yTok.text)
                && xTok.text.equals(yTok.text)) {
            problems.add(error("x and y must be different variables.", lineIndex, baseOffset, baseOffset + line.length()));
            return new TemplateParseResult(null, problems);
        }

        // If x/y fixed and appear in fixed Z -> error
        if (xTok.kind == TokenKind.VAR && !isGlob(xTok.text)) {
            for (Token zt : zToks) {
                if (zt.kind == TokenKind.VAR && zt.text.equals(xTok.text)) {
                    problems.add(error("Conditioning set cannot contain x (Z ∩ {x,y} = ∅).",
                            lineIndex, baseOffset, baseOffset + line.length()));
                    return new TemplateParseResult(null, problems);
                }
            }
        }
        if (yTok.kind == TokenKind.VAR && !isGlob(yTok.text)) {
            for (Token zt : zToks) {
                if (zt.kind == TokenKind.VAR && zt.text.equals(yTok.text)) {
                    problems.add(error("Conditioning set cannot contain y (Z ∩ {x,y} = ∅).",
                            lineIndex, baseOffset, baseOffset + line.length()));
                    return new TemplateParseResult(null, problems);
                }
            }
        }

        Template t = new Template(lineIndex, baseOffset, line, xTok, yTok, zToks);

        // If there are unknown variables, treat as error.
        boolean hasHardError = problems.stream().anyMatch(p -> p.severity == Severity.ERROR);
        if (hasHardError) return new TemplateParseResult(null, problems);

        return new TemplateParseResult(t, problems);
    }

    private List<FactSpec> expandTemplate(Template t, List<String> varNames, int remainingBudget) {
        // Expansion semantics:
        // - '?' chooses one variable (distinct across x,y,Z)
        // - '+' tokens in Z indicate choose-k from remaining vars (k = # plus tokens)
        // - Fixed Z variables always included
        // - NEW: VAR tokens with '*' match multiple variable names
        //   * In x/y: expands into multiple facts (one per match)
        //   * In Z: contributes all matches as fixed conditioning vars
        // - Enforce Z ∩ {x,y} = ∅
        // - Limit results by remainingBudget

        int kPlus = (int) t.zTokens.stream().filter(z -> z.kind == TokenKind.PLUS).count();
        int qZ = (int) t.zTokens.stream().filter(z -> z.kind == TokenKind.QMARK).count();

        // 1) Expand fixed Z (including globs) into a concrete set of names
        LinkedHashSet<String> zFixed = new LinkedHashSet<>();
        for (Token zt : t.zTokens) {
            if (zt.kind != TokenKind.VAR) continue;
            if (!isGlob(zt.text)) {
                zFixed.add(zt.text);
            } else {
                zFixed.addAll(matchGlob(zt.text, varNames));
            }
        }

        List<FactSpec> out = new ArrayList<>();

        // 2) Determine x choices
        List<String> xChoices;
        if (t.x.kind == TokenKind.QMARK) {
            xChoices = new ArrayList<>(varNames);
        } else {
            // VAR
            if (!isGlob(t.x.text)) {
                xChoices = List.of(t.x.text);
            } else {
                xChoices = matchGlob(t.x.text, varNames);
            }
        }

        // 3) Determine y choices
        List<String> yChoices;
        if (t.y.kind == TokenKind.QMARK) {
            yChoices = new ArrayList<>(varNames);
        } else {
            // VAR
            if (!isGlob(t.y.text)) {
                yChoices = List.of(t.y.text);
            } else {
                yChoices = matchGlob(t.y.text, varNames);
            }
        }

        // 4) Iterate x,y choices and build Z expansions
        for (String x : xChoices) {
            if (out.size() >= remainingBudget) break;

            // Z ∩ {x,y} = ∅ implies x cannot be in fixed Z
            if (zFixed.contains(x)) continue;

            for (String y : yChoices) {
                if (out.size() >= remainingBudget) break;

                if (x.equals(y)) continue;
                if (zFixed.contains(y)) continue;

                // Build used set starts with fixed Z plus x,y
                LinkedHashSet<String> used = new LinkedHashSet<>(zFixed);
                used.add(x);
                used.add(y);

                // Pool for Z wildcards excludes used
                List<String> zCandidates = varNames.stream()
                        .filter(v -> !used.contains(v))
                        .collect(Collectors.toList());

                // Z '?' tokens: treat as choose-qZ from candidates
                List<List<String>> zQChoices = chooseK(zCandidates, qZ);

                for (List<String> zQ : zQChoices) {
                    if (out.size() >= remainingBudget) break;

                    LinkedHashSet<String> used2 = new LinkedHashSet<>(used);
                    used2.addAll(zQ);

                    List<String> zCandidates2 = varNames.stream()
                            .filter(v -> !used2.contains(v))
                            .collect(Collectors.toList());

                    // '+' tokens: choose kPlus from remaining
                    List<List<String>> zPlusChoices = chooseK(zCandidates2, kPlus);

                    for (List<String> zPlus : zPlusChoices) {
                        if (out.size() >= remainingBudget) break;

                        LinkedHashSet<String> zAll = new LinkedHashSet<>();
                        zAll.addAll(zFixed);
                        zAll.addAll(zQ);
                        zAll.addAll(zPlus);

                        // Enforce Z ∩ {x,y} = ∅
                        if (zAll.contains(x) || zAll.contains(y)) continue;

                        out.add(new FactSpec(x, y, zAll));
                    }
                }
            }
        }

        return out;
    }

    private void clearHighlights() {
        highlighter.removeAllHighlights();
    }

    private void applyHighlights(List<ParseProblem> problems) {
        for (ParseProblem p : problems) {
            if (p.startOffset < 0 || p.endOffset <= p.startOffset) continue;
            try {
                Highlighter.HighlightPainter painter = (p.severity == Severity.ERROR) ? errorPainter : warnPainter;
                highlighter.addHighlight(p.startOffset, p.endOffset, painter);
            } catch (BadLocationException ignored) {
            }
        }
    }

    private enum RowKind {PREVIEW, RESULT, ERROR}

    private enum TokenKind {VAR, QMARK, PLUS}

    private enum Severity {ERROR, WARNING}

    // -------------------- highlighting --------------------

    private interface FactEvaluator {
        IndependenceResult evaluate(FactSpec spec) throws Exception;

        boolean hasParams();

        String name();
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c) throws Exception;
    }

    private static final class MsepFactEvaluator implements FactEvaluator {
        private final Graph g;

        MsepFactEvaluator(Graph g) {
            if (g == null) throw new IllegalArgumentException("Graph is required for m-separation.");
            this.g = g;
        }

        // inside MsepFactEvaluator, replace isMSeparated(...) with:
        private static boolean isMSeparated(Graph g, Node x, Node y, Set<Node> z) {
            MsepTest t = new MsepTest(g);
            return t.checkIndependence(x, y, z).isIndependent();
        }

        @Override
        public IndependenceResult evaluate(FactSpec spec) {
            Node x = g.getNode(spec.xName);
            Node y = g.getNode(spec.yName);
            if (x == null || y == null) {
                throw new IllegalArgumentException("Unknown variable in graph: " + spec.xName + " or " + spec.yName);
            }

            Set<Node> z = new LinkedHashSet<>();
            for (String zn : spec.zNames) {
                Node n = g.getNode(zn);
                if (n == null) throw new IllegalArgumentException("Unknown variable in graph: " + zn);
                z.add(n);
            }

            boolean indep = isMSeparated(g, x, y, z);

            IndependenceFact f = new IndependenceFact(x, y, z);

            // p-value/score not meaningful for m-sep → NaN
            return new IndependenceResult(f, indep, Double.NaN, Double.NaN);
        }

        @Override
        public boolean hasParams() {
            return false;
        }

        @Override
        public String name() {
            return "m-sep";
        }
    }

    // -------------------- helpers --------------------

    private static final class ResultRow {
        final int index;
        final FactSpec spec;
        final RowKind kind;
        final String engine;
        final String resultText;
        final Double pValue;      // may be NaN
        final String error;

        private ResultRow(int index,
                          FactSpec spec,
                          RowKind kind,
                          String engine,
                          String resultText,
                          Double pValue,
                          String error) {
            this.index = index;
            this.spec = spec;
            this.kind = kind;
            this.engine = engine;
            this.resultText = resultText;
            this.pValue = pValue;
            this.error = error;
        }

        static ResultRow preview(int index, FactSpec spec) {
            return new ResultRow(index, spec, RowKind.PREVIEW, "", "", Double.NaN, "");
        }

        static ResultRow evaluated(int index, FactSpec spec, IndependenceResult r, String engine) {
            String res = r.isIndependent() ? "INDEPENDENT" : "dependent";
            return new ResultRow(index, spec, RowKind.RESULT, engine, res, r.getPValue(), "");
        }

        static ResultRow error(int index, FactSpec spec, String message, String engine) {
            return new ResultRow(index, spec, RowKind.ERROR, engine, "ERROR", Double.NaN, message);
        }
    }

    private static final class Token {
        final TokenKind kind;
        final String text;

        Token(TokenKind kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private static final class Template {
        final int lineIndex;
        final int baseOffset;
        final String rawLine;
        final Token x;
        final Token y;
        final List<Token> zTokens;

        Template(int lineIndex, int baseOffset, String rawLine, Token x, Token y, List<Token> zTokens) {
            this.lineIndex = lineIndex;
            this.baseOffset = baseOffset;
            this.rawLine = rawLine;
            this.x = x;
            this.y = y;
            this.zTokens = zTokens;
        }
    }

    /**
     * Name-based fact spec. We resolve to actual Node instances at evaluation time.
     */
    private static final class FactSpec {
        final String xName;
        final String yName;
        final LinkedHashSet<String> zNames; // deterministic order (display), but semantics are set

        FactSpec(String xName, String yName, Set<String> zNames) {
            this.xName = xName;
            this.yName = yName;
            this.zNames = new LinkedHashSet<>(zNames);
        }

        String toDisplayString() {
            if (zNames.isEmpty()) return xName + " _||_ " + yName;
            String z = zNames.stream().sorted().collect(Collectors.joining(", "));
            return xName + " _||_ " + yName + " | " + z;
        }

        /**
         * Canonical key for duplicate detection:
         * - order x,y lexicographically
         * - sort Z lexicographically
         */
        String canonicalKey() {
            String a = xName.compareTo(yName) <= 0 ? xName : yName;
            String b = xName.compareTo(yName) <= 0 ? yName : xName;
            List<String> z = new ArrayList<>(zNames);
            Collections.sort(z);
            return a + "||" + b + "||" + String.join(",", z);
        }
    }

    private static final class TemplateParseResult {
        final Template template;
        final List<ParseProblem> problems;

        TemplateParseResult(Template template, List<ParseProblem> problems) {
            this.template = template;
            this.problems = problems;
        }
    }

    private static final class ParseAndExpandResult {
        final List<FactSpec> expandedFacts;
        final List<ParseProblem> problems;

        ParseAndExpandResult(List<FactSpec> expandedFacts, List<ParseProblem> problems) {
            this.expandedFacts = expandedFacts;
            this.problems = problems;
        }
    }

    private static final class ParseProblem {
        final Severity severity;
        final String message;
        final int startOffset;
        final int endOffset;
        final int lineIndex; // -1 if not line-specific

        ParseProblem(Severity severity, String message, int startOffset, int endOffset, int lineIndex) {
            this.severity = severity;
            this.message = message;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.lineIndex = lineIndex;
        }
    }

    // -------------------- params panel (copied pattern from VertexCheckEditor) --------------------

    /**
     * Draws a wavy underline (approximate “red squiggle”).
     */
    private static final class WavyUnderlineHighlightPainter extends LayeredHighlighter.LayerPainter {
        private final Color color;

        WavyUnderlineHighlightPainter(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            // no-op; we paint in paintLayer
        }

        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape viewBounds,
                                JTextComponent editor, View view) {

            try {
                Shape shape = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, viewBounds);
                Rectangle r = (shape instanceof Rectangle) ? (Rectangle) shape : shape.getBounds();

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.2f));

                int y = r.y + r.height - 2;
                int x0 = r.x;
                int x1 = r.x + r.width;

                Path2D wave = new Path2D.Double();
                int amp = 2;
                int step = 4;

                wave.moveTo(x0, y);
                boolean up = true;
                for (int x = x0; x <= x1; x += step) {
                    wave.lineTo(x, y + (up ? -amp : amp));
                    up = !up;
                }
                g2.draw(wave);
                g2.dispose();

                return r;
            } catch (BadLocationException e) {
                return null;
            }
        }
    }

    private static final class Pair<A, B> {
        final A a;
        final B b;

        Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

    private final class StatisticalFactEvaluator implements FactEvaluator {
        private final Function<String, Node> resolver;
        private final TriFunction<Node, Node, Set<Node>, IndependenceResult> checker;

        StatisticalFactEvaluator(Function<String, Node> resolver,
                                 TriFunction<Node, Node, Set<Node>, IndependenceResult> checker) {
            this.resolver = resolver;
            this.checker = checker;
        }

        @Override
        public IndependenceResult evaluate(FactSpec spec) throws Exception {
            Node x = resolver.apply(spec.xName);
            Node y = resolver.apply(spec.yName);
            if (x == null || y == null) {
                throw new IllegalArgumentException("Unknown variable in test: " + spec.xName + " or " + spec.yName);
            }

            Set<Node> z = new LinkedHashSet<>();
            for (String zn : spec.zNames) {
                Node n = resolver.apply(zn);
                if (n == null) throw new IllegalArgumentException("Unknown variable in test: " + zn);
                z.add(n);
            }

            // IndependenceFact in results should be based on test nodes
            IndependenceFact f = new IndependenceFact(x, y, z);
            IndependenceResult r = checker.apply(x, y, z);

            // Some implementations return a result whose fact may differ; keep the returned result.
            // If you prefer, wrap to preserve f:
            // return new IndependenceResult(f, r.isIndependent(), r.getPValue(), r.getScore());
            return r;
        }

        @Override
        public boolean hasParams() {
            return true;
        }

        @Override
        public String name() {
            return "Statistical";
        }
    }

    private final class ResultsTableModel extends AbstractTableModel {
        private final String[] cols = new String[]{"#", "Fact", "Engine", "Result", "p-value / score", "Error"};
        private List<ResultRow> rows = List.of();

        void setRows(List<ResultRow> rows) {
            this.rows = (rows == null) ? List.of() : new ArrayList<>(rows);
            fireTableDataChanged();
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
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ResultRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.index;
                case 1 -> r.spec.toDisplayString();
                case 2 -> r.engine;
                case 3 -> r.kind == RowKind.PREVIEW ? "" : r.resultText;
                case 4 -> {
                    if (r.kind != RowKind.RESULT) yield "";
                    if (r.pValue == null || Double.isNaN(r.pValue)) yield "";
                    yield nf.format(r.pValue);
                }
                case 5 -> r.kind == RowKind.ERROR ? r.error : "";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Integer.class;
                default -> String.class;
            };
        }
    }
}