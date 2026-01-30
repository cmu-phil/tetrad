/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static edu.cmu.tetradapp.editor.VertexRepairPanel.factKey;
import static edu.cmu.tetradapp.util.ParameterComponents.toArray;

/**
 * Swing editor for the Vertex Checker: an interactive tool for diagnosing and
 * exploring violations of the local Markov property on a per-vertex basis.
 *
 * <p>
 * Given a graph, data set, and independence test, this editor evaluates
 * conditional independencies implied by the chosen conditioning rule
 * (e.g., parents, parents-and-neighbors, Markov blanket, or ordered local Markov
 * for MAGs) and compares them to the results of statistical independence tests.
 * </p>
 *
 * <p>
 * The editor presents results at multiple levels:
 * <ul>
 *   <li><b>Overview table (left)</b>: one row per vertex, summarizing
 *       the number of implied tests, p-values used, and multiple distributional
 *       diagnostics (e.g., KS, Anderson–Darling, Fisher, binomial, min/median p);</li>
 *   <li><b>Details panel (right)</b>: for the currently selected vertex,
 *       a table of individual implied conditional independence tests and their
 *       outcomes, along with a histogram of the corresponding p-values.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The editor supports incremental, selection-driven computation:
 * results are computed lazily for selected vertices and reused where possible.
 * Users may run checks for individual vertices or explicitly recompute all
 * vertices using the {@code Run All} action.
 * </p>
 *
 * <p>
 * Integrated with this editor is the {@link VertexRepairPanel}, which allows
 * users to explore local graph modifications when Markov violations are present.
 * Accepted repairs update the underlying graph, trigger recomputation of affected
 * results, and preserve table selections where possible.
 * </p>
 *
 * <p>
 * Additional features include:
 * <ul>
 *   <li>Selection-stable updates when the graph changes;</li>
 *   <li>Undo and graph-display actions for navigating graph space;</li>
 *   <li>Support for multiple independence tests and parameter settings;</li>
 *   <li>Visualization of p-value distributions to diagnose systematic deviations
 *       from uniformity.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The Vertex Checker is intended as a diagnostic and exploratory aid.
 * It does not assume score equivalence, nor does it restrict the user to
 * equivalence classes of graphs. Instead, it provides detailed local feedback
 * to support informed navigation of graph space.
 * </p>
 *
 * <p><b>Layout:</b></p>
 * <ul>
 *   <li><b>Top controls:</b> Independence test selection, conditioning set type,
 *       parameter access, verbosity, and run controls;</li>
 *   <li><b>Left:</b> Overview table with one row per vertex;</li>
 *   <li><b>Right:</b> Detailed results for the active vertex, including test results
 *       and a p-value histogram.</li>
 * </ul>
 */
public class VertexCheckEditor extends JPanel {

    private static final String PREF_KEY_TEST = "markovCheckerIndependenceTest";
    private static final String PREF_KEY_SET_TYPE = "markovCheckerConditioningSetType";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(VertexCheckEditor.class);
    private final VertexCheckIndTestModel model;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JComboBox<IndependenceTestModel> indTestCombo = new JComboBox<>();
    private final JComboBox<String> conditioningCombo = new JComboBox<>();
    private final JCheckBox verbose = new JCheckBox("Verbose");
    private final JButton showIndepsForRow = new JButton("Independencies");
    private final JButton repairNodeButton = new JButton("Repair Node");
    private JTable overviewTable;
    private JTable factsTable;
    private AbstractTableModel overviewModel;
    private AbstractTableModel factsModel;
    private JPanel histogramPanel = new JPanel(new BorderLayout());
    private JButton runAll = new JButton("Run All");
    private IndependenceWrapper independenceWrapper;
    private boolean initializing;

    // --- Graph UX ---
    private final JButton undoGraphButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Graph");
    private final Deque<Graph> graphHistory = new ArrayDeque<>();

    private boolean applyingGraphProgrammatically = false; // prevents history double-push

    public VertexCheckEditor(VertexCheckIndTestModel model) {
        if (model == null) throw new NullPointerException("Expecting a model.");
        this.model = model;

        setPreferredSize(new Dimension(1100, 650));
        setLayout(new BorderLayout(10, 10));

        setBorder(new EmptyBorder(8, 8, 8, 8));

        initializing = true;

        buildControls();
        buildMainPanels();

        refreshTestList();      // this will select the saved item
        applySavedSetType();

        setTestFromCombo();

        repairNodeButton.setEnabled(true);

        repairNodeButton.addActionListener(e -> showRepairNodeDialog());

        undoGraphButton.addActionListener(e -> undoGraph());
        showGraphButton.addActionListener(e -> showGraphDialog());
        updateUndoButtonEnabled();

        initializing = false;

        model.addPropertyChangeListener(evt -> {
            if (!VertexCheckIndTestModel.PROP_GRAPH.equals(evt.getPropertyName())) return;

            if (!applyingGraphProgrammatically) {
                Object oldV = evt.getOldValue();
                if (oldV instanceof Graph oldG) {
                    graphHistory.push(safeCopy(oldG));
                    SwingUtilities.invokeLater(this::updateUndoButtonEnabled);
                }
            }

            SwingUtilities.invokeLater(this::onModelGraphChanged);
        });
    }

    private static DataType guessDataType(DataModel dm) {
        // This is how a lot of editors do it: inspect DataSet variable types where possible.
        if (dm instanceof DataSet ds) {
            boolean hasContinuous = ds.getVariables().stream().anyMatch(v -> v instanceof ContinuousVariable);
            boolean hasDiscrete = ds.getVariables().stream().anyMatch(v -> v instanceof DiscreteVariable);

            if (hasContinuous && hasDiscrete) return DataType.Mixed;
            if (hasDiscrete) return DataType.Discrete;
            return DataType.Continuous;
        }
        // Fall back; most independence tests will at least show.
        return DataType.Continuous;
    }

    private static String factString(IndependenceFact fact) {
        List<Node> z = new ArrayList<>(fact.getZ());
        String zStr = z.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
        if (z.isEmpty()) return "Ind(" + fact.getX().getName() + ", " + fact.getY().getName() + ")";
        return "Ind(" + fact.getX().getName() + ", " + fact.getY().getName() + " | " + zStr + ")";
    }

    /**
     * Creates a parameters panel for the given independence wrapper and parameters.
     *
     * @param independenceWrapper The independence wrapper implementation.
     * @param params              The parameters for the independence test.
     * @return The JPanel containing the parameters panel.
     */
    private static JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());
        return createParamsPanel(testParameters, params);
    }

    /**
     * Creates a parameters panel for the given set of parameters and Parameters object.
     *
     * @param params     The set of parameter names.
     * @param parameters The Parameters object containing the parameter values.
     * @return The JPanel containing the parameters panel.
     */
    public static JPanel createParamsPanel(Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        Box paramsBox = Box.createVerticalBox();

        Box[] boxes = toArray(createParameterComponents(params, parameters));
        int lastIndex = boxes.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            paramsBox.add(boxes[i]);
            paramsBox.add(Box.createVerticalStrut(10));
        }
        paramsBox.add(boxes[lastIndex]);

        panel.add(new PaddingPanel(paramsBox), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates a map of parameter components for the given set of parameters and Parameters object.
     *
     * @param params     The set of parameter names.
     * @param parameters The Parameters object containing the parameter values.
     * @return A map of parameter names to Box components.
     */
    private static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescriptions.get(e)),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    /**
     * Creates a parameter component based on the given parameter, Parameters, and ParamDescription.
     *
     * @param parameter  The name of the parameter.
     * @param parameters The Parameters object containing the parameter values.
     * @param paramDesc  The ParamDescription object with information about the parameter.
     * @return A Box component representing the parameter component.
     * @throws IllegalArgumentException If the default value type is unexpected.
     */
    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc) {
        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();
        if (defaultValue instanceof Double) {
            double lowerBoundDouble = paramDesc.getLowerBoundDouble();
            double upperBoundDouble = paramDesc.getUpperBoundDouble();
            component = getDoubleField(parameter, parameters, (Double) defaultValue, lowerBoundDouble, upperBoundDouble);
        } else if (defaultValue instanceof Integer) {
            int lowerBoundInt = paramDesc.getLowerBoundInt();
            int upperBoundInt = paramDesc.getUpperBoundInt();
            component = getIntTextField(parameter, parameters, (Integer) defaultValue, lowerBoundInt, upperBoundInt);
        } else if (defaultValue instanceof Long) {
            long lowerBoundLong = paramDesc.getLowerBoundLong();
            long upperBoundLong = paramDesc.getUpperBoundLong();
            component = getLongTextField(parameter, parameters, (Long) defaultValue, lowerBoundLong, upperBoundLong);
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
        } else if (defaultValue instanceof String) {
            component = getStringField(parameter, parameters, (String) defaultValue);
        } else {
            throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
        }

        Box paramRow = Box.createHorizontalBox();

        JLabel paramLabel = new JLabel(paramDesc.getShortDescription());
        String longDescription = paramDesc.getLongDescription();
        if (longDescription != null) {
            paramLabel.setToolTipText(longDescription);
        }
        paramRow.add(paramLabel);
        paramRow.add(Box.createHorizontalGlue());
        paramRow.add(component);

        return paramRow;
    }

    /**
     * Returns a DoubleTextField with specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the DoubleTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return A DoubleTextField with the specified parameters.
     */
    private static DoubleTextField getDoubleField(String parameter, Parameters parameters,
                                                  double defaultValue, double lowerBound, double upperBound) {
        return ParameterComponents.getDoubleField(parameter, parameters, defaultValue, lowerBound, upperBound);
    }

    /**
     * Returns an IntTextField with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the IntTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return An IntTextField with the specified parameters.
     */
    private static IntTextField getIntTextField(String parameter, Parameters parameters,
                                                int defaultValue, double lowerBound, double upperBound) {
        return ParameterComponents.getIntTextField(parameter, parameters, defaultValue, lowerBound, upperBound);
    }

    /**
     * Returns a LongTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the LongTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return A LongTextField object with the specified parameters.
     */
    private static LongTextField getLongTextField(String parameter, Parameters parameters,
                                                  long defaultValue, long lowerBound, long upperBound) {
        LongTextField field = new LongTextField(parameters.getLong(parameter, defaultValue), 8);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    /**
     * Creates a boolean selection box with Yes and No radio buttons.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the boolean parameter
     */
    private static Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean defaultValue) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        // Button group to ensure only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);

        boolean aBoolean = parameters.getBoolean(parameter, defaultValue);

        // Set default selection
        if (aBoolean) {
            yesButton.setSelected(true);
        } else {
            noButton.setSelected(true);
        }

        // Add to containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, true);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, false);
            }
        });

        return selectionBox;
    }

    /**
     * Returns a StringTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the StringTextField.
     * @return A StringTextField object with the specified parameters.
     */
    private static StringTextField getStringField(String parameter, Parameters parameters, String defaultValue) {
        return PathsAction.getStringField(parameter, parameters, defaultValue);
    }

    private void buildControls() {
        Box layout = Box.createVerticalBox();

        Box controls = Box.createHorizontalBox();
        controls.add(new JLabel("Independence Test:"));

        indTestCombo.setPreferredSize(new Dimension(280, 24));
        controls.add(indTestCombo);

        JButton paramsButton = new JButton("Params");
        controls.add(paramsButton);

        controls.add(new JLabel("Conditioning Sets:"));

        conditioningCombo.addItem("MarkovBlanket(X)");
        conditioningCombo.addItem("Parents(X)");
        conditioningCombo.addItem("Parents(X) and Neighbors(X)");
        conditioningCombo.addItem("Ordered Local Markov (MAG)");
        conditioningCombo.setPreferredSize(new Dimension(220, 24));
        controls.add(conditioningCombo);

        controls.add(verbose);
        controls.add(runAll);

        controls.add(Box.createHorizontalGlue());

        Box controlBox = Box.createHorizontalBox();
        controlBox.add(Box.createHorizontalGlue());
        controlBox.add(controls);
        controlBox.add(Box.createHorizontalGlue());

        layout.add(controlBox);
        Box labelBox = Box.createHorizontalBox();

        JLabel instructionLabel = new JLabel(
                "Click a row in the table to compute and view results for that node."
        );
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.ITALIC));
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        labelBox.add(Box.createHorizontalGlue());
        labelBox.add(instructionLabel);
        labelBox.add(Box.createHorizontalGlue());
        layout.add(labelBox);
        add(layout, BorderLayout.NORTH);

        // Wiring
        indTestCombo.addActionListener(e -> {
            if (initializing) return;
            setTestFromCombo();
            resetResultsUI();
        });

        conditioningCombo.addActionListener(e -> {
            if (initializing) return;

            String s = (String) conditioningCombo.getSelectedItem();
            ConditioningSetType t = toSetType(s);
            model.setConditioningSetType(t);

            PREFS.put(PREF_KEY_SET_TYPE, s == null ? "" : s);

            resetResultsUI();
        });

        verbose.addActionListener(e -> model.setVerbose(verbose.isSelected()));

        paramsButton.addActionListener(e -> {
            // Reuse the same convention as other editors: open a params UI for the independence wrapper.
            // If you’d rather keep it minimal, delete this button.
            if (independenceWrapper == null) {
                JOptionPane.showMessageDialog(this, "Choose an independence test first.");
                return;
            }
            JOptionPane pane = new JOptionPane(
                    createParamsPanel(independenceWrapper, model.getParameters()),
                    JOptionPane.PLAIN_MESSAGE
            );
            pane.createDialog(this, "Set Parameters").setVisible(true);

            // Ensure alpha box matches parameter state
            setTestFromCombo();
            resetResultsUI();
        });

        runAll.addActionListener(e -> new WatchedProcess() {
            @Override
            public void watch() {
                String selectedVertex = getSelectedVertexName();
                model.runAllVertices(true);
                SwingUtilities.invokeLater(() -> {
                    overviewModel.fireTableDataChanged();
                    restoreOverviewSelection(selectedVertex);
//                    selectFirstRowIfAny();
                });

            }
        });
    }

    private void buildMainPanels() {
        // Overview table
        overviewModel = new AbstractTableModel() {
            private final String[] cols = new String[]{
                    "Vertex", "CS", "#t", "#p", "KS", "AD", "Fish", "Bin", "frac≤q", "min", "med"
            };

            @Override
            public int getRowCount() {
                return model.getVertexNames().size();
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
                String v = model.getVertexNames().get(rowIndex);
                VertexCheckIndTestModel.VertexSummary s = model.getSummary(v);

                return switch (columnIndex) {
                    case 0 -> v;

                    case 1 -> { // CS size range
                        int min = model.getMinConditioningSetSizeFast(v);
                        int max = model.getMaxConditioningSetSizeFast(v);
                        if (min < 0 || max < 0) yield "";
                        if (min == max) yield String.valueOf(min);
                        yield min + "-" + max;
                    }

                    case 2 -> (s == null ? "" : s.numFactsTotal());
                    case 3 -> (s == null ? "" : s.numPValuesUsed());
                    case 4 -> (s == null ? "" : fmt(s.ksPValue()));
                    case 5 -> (s == null ? "" : fmt(s.asP()));      // your AD p
                    case 6 -> (s == null ? "" : fmt(s.fishP()));
                    case 7 -> (s == null ? "" : fmt(s.binP()));
                    case 8 -> (s == null ? "" : fmt(s.fractionReject())); // rename in UI to frac≤q
                    case 9 -> (s == null ? "" : fmt(s.minP()));
                    case 10 -> (s == null ? "" : fmt(s.medianP()));
                    default -> "";
                };
            }
        };

        overviewTable = new JTable(overviewModel);
        overviewTable.setTransferHandler(new DefaultTableTransferHandler(0));

        TableColumnModel ocm = overviewTable.getColumnModel();

        ocm.getColumn(0).setPreferredWidth(80);   // Node
        ocm.getColumn(1).setPreferredWidth(45);   // CS

        ocm.getColumn(2).setPreferredWidth(45);   // #t
        ocm.getColumn(3).setPreferredWidth(45);   // #p

        for (int c : new int[]{4, 5, 6, 7, 8, 9, 10}) {
            ocm.getColumn(c).setPreferredWidth(70);  // KS, AD, Fish, frac≤q, min, med
        }

        overviewTable.setRowSorter(new TableRowSorter<>(overviewModel));
        overviewTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        overviewTable.getSelectionModel().addListSelectionListener(this::overviewSelectionChanged);

        JScrollPane overviewScroll = new JScrollPane(overviewTable);
        overviewScroll.setPreferredSize(new Dimension(520, 500));

        factsModel = new AbstractTableModel() {
            private final String[] cols = new String[]{"#", "Fact", "Result", "p-value"};

            @Override
            public int getRowCount() {
                String v = getSelectedVertexName();
                if (v == null) return 0;
                return model.getResultsForVertex(v).size();
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
                String v = getSelectedVertexName();
                if (v == null) return "";
                List<IndependenceResult> rs = model.getResultsForVertex(v);
                if (rowIndex < 0 || rowIndex >= rs.size()) return "";
                IndependenceResult r = rs.get(rowIndex);

                return switch (columnIndex) {
                    case 0 -> (rowIndex + 1);
                    case 1 -> factString(r.getFact());
                    case 2 -> r.isIndependent() ? "INDEPENDENT" : "dependent";
                    case 3 -> fmt(r.getPValue());
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
        };

        factsTable = new JTable(factsModel);
        factsTable.setTransferHandler(new DefaultTableTransferHandler(0));

        TableColumnModel cm = factsTable.getColumnModel();

        // Column indices assumed; adjust if needed
        TableColumn colIndex = cm.getColumn(0); // #
        TableColumn colFact = cm.getColumn(1); // Fact
        TableColumn colResult = cm.getColumn(2); // Result
        TableColumn colPval = cm.getColumn(3); // p-value

        // # column: very skinny
        colIndex.setPreferredWidth(40);
        colIndex.setMaxWidth(40);

        // Result column: "INDEPENDENT"
        colResult.setPreferredWidth(100);
        colResult.setMaxWidth(100);

        // p-value column: ~6–8 chars
        colPval.setMinWidth(70);
        colPval.setPreferredWidth(70);

        // Fact column: stretch
        colFact.setMinWidth(300);
        // no max width → absorbs remaining space

        factsTable.setRowSorter(new TableRowSorter<>(factsModel));
        JScrollPane factsScroll = new JScrollPane(factsTable);

        // "Show Independencies..." button is only enabled when exactly one row is selected.
        showIndepsForRow.setEnabled(false);
        factsTable.getSelectionModel().addListSelectionListener(evt -> {
            if (evt.getValueIsAdjusting()) return;
            showIndepsForRow.setEnabled(factsTable.getSelectedRowCount() == 1);
        });
        showIndepsForRow.addActionListener(e -> showIndependenciesForSelectedRow());

        JPanel factsPane = new JPanel(new BorderLayout(6, 6));
        factsPane.setBorder(BorderFactory.createTitledBorder("Tests implied for selected node"));
        factsPane.add(factsScroll, BorderLayout.CENTER);

        JPanel factsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        factsButtons.add(showIndepsForRow);
        factsButtons.add(repairNodeButton);
        factsButtons.add(undoGraphButton);
        factsButtons.add(showGraphButton);

        factsPane.add(factsButtons, BorderLayout.SOUTH);

        histogramPanel.setBorder(BorderFactory.createTitledBorder("P-value Histogram"));

        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.add(factsPane, BorderLayout.CENTER);
        rightTop.add(histogramPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, overviewScroll, rightTop);
        split.setResizeWeight(0.45);

        add(split, BorderLayout.CENTER);
    }

    private void overviewSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        SelectedRows sel = getSelectedVertices();
        if (sel.vertices().isEmpty()) return;

        // Decide which vertex is driving details (right panel)
        final String active = getActiveSelectedVertexName();
        if (active == null) return;

        // Compute all selected vertices that aren't computed yet
        List<String> toCompute = sel.vertices().stream()
                .filter(v -> !model.isVertexComputed(v))
                .collect(Collectors.toList());

        // If nothing new to compute, just refresh details for active selection.
        if (toCompute.isEmpty()) {
            refreshDetails(active);
            return;
        }

        // Run all computations in one watched process.
        new WatchedProcess() {
            @Override
            public void watch() {
                for (String v : toCompute) {
                    model.ensureVertexComputed(v);
                }

                SwingUtilities.invokeLater(() -> {
                    // Update all rows that were selected (cheap + keeps table accurate)
                    for (int mr : sel.modelRows()) {
                        overviewModel.fireTableRowsUpdated(mr, mr);
                    }

                    // Only refresh details for whichever node is currently "active"
                    String stillActive = getActiveSelectedVertexName();
                    if (stillActive != null) {
                        refreshDetails(stillActive);
                    }
                });
            }
        };
    }

    private void refreshDetails(String v) {
        factsModel.fireTableDataChanged();
        updateHistogram(v);

        // Keep "Repair Node" in sync with what's shown in the Result column.
        updateRepairButtonEnabled();
    }

    private void updateHistogram(String vertexName) {
        histogramPanel.removeAll();

        List<IndependenceResult> rs = model.getResultsForVertex(vertexName);
        List<Double> pvals = rs.stream()
                .map(IndependenceResult::getPValue)
                .filter(p -> !Double.isNaN(p) && p >= 0.0 && p <= 1.0)
                .collect(Collectors.toList());

        if (pvals.isEmpty()) {
            histogramPanel.add(new JLabel("(No valid p-values)"), BorderLayout.CENTER);
        } else {
            histogramPanel.add(buildHistogramPanel(pvals), BorderLayout.CENTER);
        }

        histogramPanel.revalidate();
        histogramPanel.repaint();
    }

    private JPanel buildHistogramPanel(List<Double> pvals) {
        // Build a 1-column dataset and reuse Tetrad’s histogram view classes.
        DataSet dataSet = new BoxDataSet(
                new VerticalDoubleDataBox(pvals.size(), 1),
                Collections.singletonList(new ContinuousVariable("p"))
        );

        for (int i = 0; i < pvals.size(); i++) {
            dataSet.setDouble(i, 0, pvals.get(i));
        }

        Histogram histogram = new Histogram(dataSet, "p", false);
        histogram.setNumBins(10);

        HistogramPanel view = new HistogramPanel(histogram, true);
        view.setXAxisBounds(0.0, 1.0, true);  // <-- force [0,1], ignore outside

//        Histogram histogram = new Histogram(dataSet, "p", false);
//        histogram.setNumBins(10);
//
//        edu.cmu.tetradapp.editor.HistogramPanel view = new edu.cmu.tetradapp.editor.HistogramPanel(histogram, true);
        view.setMinimumSize(new Dimension(420, 180));
        view.setPreferredSize(new Dimension(420, 180));

        JPanel p = new JPanel(new BorderLayout());
        p.add(view, BorderLayout.CENTER);
        return p;
    }

    private void refreshTestList() {
        DataType dt = guessDataType(model.getDataModel());

        indTestCombo.removeAllItems();
        List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(dt);
        for (IndependenceTestModel m : models) indTestCombo.addItem(m);

        indTestCombo.setEnabled(indTestCombo.getItemCount() > 0);

        String savedClassName = PREFS.get(PREF_KEY_TEST, null);

        IndependenceTestModel toSelect = null;

        if (savedClassName != null) {
            for (int i = 0; i < indTestCombo.getItemCount(); i++) {
                IndependenceTestModel m = indTestCombo.getItemAt(i);
                String wrapperName = m.getIndependenceTest().clazz().getName();
                if (wrapperName.equals(savedClassName)) {
                    toSelect = m;
                    break;
                }
            }
        }

        if (toSelect == null && !models.isEmpty()) {
            toSelect = IndependenceTestModels.getInstance().getDefaultModel(dt);
        }

        if (toSelect != null) {
            indTestCombo.setSelectedItem(toSelect);
        }
    }

    private void setTestFromCombo() {
        IndependenceTestModel selectedItem =
                (IndependenceTestModel) indTestCombo.getSelectedItem();

        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null
                : (Class<IndependenceWrapper>) selectedItem.getIndependenceTest().clazz();
        IndependenceTest independenceTest;

        if (clazz != null) {
            try {
                independenceWrapper =
                        clazz.getDeclaredConstructor(new Class[0]).newInstance();
                independenceTest =
                        independenceWrapper.getTest(model.getDataModel(),
                                model.getParameters());
                model.setIndependenceTest(independenceTest);

                PREFS.put(PREF_KEY_TEST, clazz.getName());

                System.out.println("setTestFromCombo: " + clazz.getName());

                invalidate();
                repaint();
            } catch (InstantiationException | IllegalAccessException
                     | InvocationTargetException | NoSuchMethodException e1) {
                TetradLogger.getInstance().log("Error: " + e1.getMessage());
                throw new RuntimeException(e1);
            }
        }
    }

    private void applySavedSetType() {
        String saved = PREFS.get(PREF_KEY_SET_TYPE, "MarkovBlanket(X)");

        conditioningCombo.setSelectedItem(saved);

        // Optional safety: if saved wasn’t found, fall back to current selection
        String actual = (String) conditioningCombo.getSelectedItem();
        model.setConditioningSetType(toSetType(actual));
    }

    private ConditioningSetType toSetType(String s) {
        if (s == null) return ConditioningSetType.MARKOV_BLANKET;
        return switch (s) {
            case "Parents(X)" -> ConditioningSetType.LOCAL_MARKOV;
            case "Parents(X) and Neighbors(X)" -> ConditioningSetType.PARENTS_AND_NEIGHBORS;
            case "MarkovBlanket(X)" -> ConditioningSetType.MARKOV_BLANKET;
            case "Ordered Local Markov (MAG)" -> ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG;
            default -> ConditioningSetType.MARKOV_BLANKET;
        };
    }

    private String getSelectedVertexName() {
        return getActiveSelectedVertexName();
    }

    public VertexCheckIndTestModel getIndTestModel() {
        return model;
    }

    private SelectedRows getSelectedVertices() {
        int[] viewRows = overviewTable.getSelectedRows();
        if (viewRows == null || viewRows.length == 0) {
            return new SelectedRows(List.of(), List.of());
        }

        List<Integer> modelRows = new ArrayList<>(viewRows.length);
        List<String> vertices = new ArrayList<>(viewRows.length);

        for (int vr : viewRows) {
            int mr = overviewTable.convertRowIndexToModel(vr);
            modelRows.add(mr);
            vertices.add(model.getVertexNames().get(mr));
        }

        return new SelectedRows(modelRows, vertices);
    }

    /**
     * Pick which selected vertex drives the right-side details panel.
     */
    private String getActiveSelectedVertexName() {
        // Lead selection if present; otherwise first selected.
        int leadView = overviewTable.getSelectionModel().getLeadSelectionIndex();
        if (leadView >= 0) {
            int leadModel = overviewTable.convertRowIndexToModel(leadView);
            if (leadModel >= 0 && leadModel < model.getVertexNames().size()) {
                return model.getVertexNames().get(leadModel);
            }
        }
        SelectedRows sel = getSelectedVertices();
        return sel.vertices().isEmpty() ? null : sel.vertices().get(0);
    }

    private void selectFirstRowIfAny() {
        if (overviewTable.getRowCount() > 0) {
            overviewTable.setRowSelectionInterval(0, 0);
        } else {
            histogramPanel.removeAll();
            histogramPanel.add(new JLabel("(No results)"), BorderLayout.CENTER);
            factsModel.fireTableDataChanged();
        }
    }

    private void showIndependenciesForSelectedRow() {
        String vName = getActiveSelectedVertexName();
        if (vName == null) return;

        int viewRow = factsTable.getSelectedRow();
        if (viewRow < 0) return;

        int modelRow = factsTable.convertRowIndexToModel(viewRow);
        List<IndependenceResult> rs = model.getResultsForVertex(vName);
        if (rs == null || modelRow < 0 || modelRow >= rs.size()) return;

        IndependenceFact fact = rs.get(modelRow).getFact();
        if (fact == null) return;

        // Map to the IndependenceTest's node instances (avoid graph-vs-data node identity issues).
        Node x = nodeInTestByName(fact.getX().getName());
        Node y = nodeInTestByName(fact.getY().getName());
        if (x == null || y == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        ShowIndepsDialog dialog = new ShowIndepsDialog(owner, x, y);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }


    // ---------------------------------------------------------------------
    // "Show Independencies For Row" feature
    // ---------------------------------------------------------------------

    private Node nodeInTestByName(String name) {
        IndependenceTest test = model.getIndependenceTest();
        if (test == null) return null;

        for (Node n : test.getVariables()) {
            if (n != null && Objects.equals(n.getName(), name)) return n;
        }
        return null;
    }

    private List<IndependenceResult>    findIndependencies(Node x, Node y, PoolChoice poolChoice, int maxSetSize) {
        IndependenceTest test = model.getIndependenceTest();
        if (test == null) return Collections.emptyList();

        Graph g = model.getGraph();
        if (g == null) return Collections.emptyList();

        // Candidate pool
        Set<Node> pool = new LinkedHashSet<>();
        switch (poolChoice) {
            case MB_UNION -> {
                pool.addAll(GraphUtils.markovBlanket(x, g));
                pool.addAll(GraphUtils.markovBlanket(y, g));
            }
            case PARENTS_UNION -> {
                pool.addAll(g.getParents(x));
                pool.addAll(g.getParents(y));
            }
            case PARENTS_AND_NEIGHBORS_UNION -> {
                pool.addAll(parentsAndNeighbors(x, g));
                pool.addAll(parentsAndNeighbors(y, g));
            }
            case ALL_NODES -> pool.addAll(g.getNodes());
        }

        pool.remove(x);
        pool.remove(y);

        // Hard cap to avoid accidental combinatorial explosions from huge pools.
        final int HARD_CAP = 30;
        List<Node> poolList = new ArrayList<>(pool);
        poolList.sort(Comparator.comparing(Node::getName));
        if (poolList.size() > HARD_CAP) {
            poolList = poolList.subList(0, HARD_CAP);
        }

        // Enumerate subsets up to maxSetSize
        List<IndependenceResult> found = new ArrayList<>();
        int limit = 200; // UI safety: don't flood the table.
        for (int k = 0; k <= maxSetSize; k++) {
            if (found.size() >= limit) break;
            enumerateSubsets(poolList, k, subset -> {
                if (found.size() >= limit) return false;

                Set<Node> z = new LinkedHashSet<>(subset);
                IndependenceResult r = null;
                try {
                    r = test.checkIndependence(x, y, z);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (r != null && r.isIndependent()) {
                    found.add(r);
                }
                return true;
            });
        }

        // Sort: highest p-value first (most comfortable independencies).
        found.sort((a, b) -> Double.compare(b.getPValue(), a.getPValue()));
        return found;
    }

    private Set<Node> parentsAndNeighbors(Node x, Graph g) {
        Set<Node> z = new LinkedHashSet<>();
        for (Node w : g.getAdjacentNodes(x)) {
            Edge e = g.getEdge(w, x);
            if (e != null && Edges.isUndirectedEdge(e)) z.add(w);
            if (g.isParentOf(w, x)) z.add(w);
        }
        return z;
    }

    /**
     * Enumerate all subsets of size k from pool, calling `accept` with each subset (as a List<Node>).
     * If accept returns false, enumeration halts early.
     */
    private void enumerateSubsets(List<Node> pool, int k, Function<List<Node>, Boolean> accept) {
        if (k == 0) {
            accept.apply(Collections.emptyList());
            return;
        }
        if (pool.isEmpty() || k > pool.size()) return;

        Node[] a = pool.toArray(new Node[0]);
        int n = a.length;

        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;

        while (true) {
            List<Node> subset = new ArrayList<>(k);
            for (int i = 0; i < k; i++) subset.add(a[idx[i]]);
            Boolean cont = accept.apply(subset);
            if (cont != null && !cont) return;

            // next combination
            int i = k - 1;
            while (i >= 0 && idx[i] == n - k + i) i--;
            if (i < 0) break;
            idx[i]++;
            for (int j = i + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
        }
    }

    private String fmt(double x) {
        if (Double.isNaN(x)) return "";
        return nf.format(x);
    }

    private void resetResultsUI() {
        model.clearResults();

        histogramPanel.removeAll();
        histogramPanel.add(new JLabel("(Select a vertex to compute results)"), BorderLayout.CENTER);

        factsModel.fireTableDataChanged();
        overviewModel.fireTableDataChanged();

        // Clear selection (prevents auto-run on stale selection)
        overviewTable.clearSelection();

        histogramPanel.revalidate();
        histogramPanel.repaint();
    }

    private void updateRepairButtonEnabled() {
        // Enable "Repair Node" iff the selected vertex has at least one DEPENDENT judgment
        // in the facts table (i.e., at least one IndependenceResult with isIndependent()==false).
        String v = getSelectedVertexName();

        boolean enable = false;

        if (v != null && model != null) {
            List<IndependenceResult> rs = model.getResultsForVertex(v);
            enable = rs != null && !rs.isEmpty() && rs.stream().anyMatch(r -> r != null && !r.isIndependent());
        }

        repairNodeButton.setEnabled(true);
    }

    private void showRepairNodeDialog() {
        Node x = getSelectedVertex();
        if (x == null) return;

        VertexRepairPanel panel = new VertexRepairPanel(this, x);

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Repair Vertex: " + x.getName(),
                Dialog.ModalityType.APPLICATION_MODAL
        );

        // explicit accept/cancel behavior
        final boolean[] accepted = { false };

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");

        ok.addActionListener(e -> { accepted[0] = true; dialog.dispose(); });
        cancel.addActionListener(e -> { accepted[0] = false; dialog.dispose(); });

        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                // red close button = cancel
                accepted[0] = false;
                dialog.dispose();
            }
        });

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonBar.add(ok);
        buttonBar.add(cancel);

        JPanel content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(buttonBar, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true); // blocks

        if (!accepted[0]) return;                 // <-- cancel path: no change

        Graph g2 = panel.getGraph();
        if (g2 == null) return;

        // push current graph onto history, then apply
        graphHistory.push(safeCopy(model.getGraph()));
        updateUndoButtonEnabled();

        applyingGraphProgrammatically = true;
        try {
            model.setGraph(g2);
        } finally {
            applyingGraphProgrammatically = false;
        }
    }

    private Graph rebindGraphToModelNodesByName(Graph repaired) {
        if (repaired == null) return null;

        Graph base = (model != null ? model.getGraph() : null);
        if (base == null) return repaired;

        Map<String, Node> nameToNode = new HashMap<>();
        for (Node n : base.getNodes()) nameToNode.put(n.getName(), n);

        Graph out = new EdgeListGraph();
        // Use the model's node instances
        for (Node n : base.getNodes()) out.addNode(n);

        // Recreate edges using the model's node instances, preserving endpoints
        for (Edge e : repaired.getEdges()) {
            Node a = nameToNode.get(e.getNode1().getName());
            Node b = nameToNode.get(e.getNode2().getName());
            if (a == null || b == null) continue;

            Edge e2 = new Edge(a, b, e.getEndpoint1(), e.getEndpoint2());
            out.addEdge(e2);
        }

        return out;
    }

    public Node getSelectedVertex() {
        List<Node> nodes = model.getGraph().getNodes();
        String selectedName = getSelectedVertexName();

        for (Node node : nodes) {
            if (node.getName().equals(selectedName)) {
                return node;
            }
        }

        throw new IllegalStateException("Selected vertex not found in graph");
    }

    private void restoreOverviewSelection(String vertexName) {
        if (vertexName == null) return;

        JTable table = overviewTable;
        if (table == null) return;

        for (int row = 0; row < table.getRowCount(); row++) {
            Object value = table.getValueAt(row, 0); // assuming vertex name is column 0
            if (vertexName.equals(value)) {
                table.getSelectionModel().setSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private enum PoolChoice {
        MB_UNION("MB(x) U MB(y)"),
        PARENTS_UNION("Parents(x) U Parents(y)"),
        PARENTS_AND_NEIGHBORS_UNION("Parents-and-Neighbors(x) U Parents-and-Neighbors(y)"),
        ALL_NODES("All nodes \\ {x, y}");

        private final String label;

        PoolChoice(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record SelectedRows(List<Integer> modelRows, List<String> vertices) {
    }

    private final class ShowIndepsDialog extends JDialog {
        private final Node x;
        private final Node y;

        private final JComboBox<PoolChoice> poolBox = new JComboBox<>(PoolChoice.values());
        private final JSpinner depthSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
        private final JButton showButton = new JButton("Show independencies");

        private final JPanel resultsPanel = new JPanel(new BorderLayout(6, 6));
        private final JLabel emptyLabel = new JLabel("No independencies found under those constraints.");

        private final IndepTableModel tableModel = new IndepTableModel();
        private final JTable table = new JTable(tableModel);

        ShowIndepsDialog(Window owner, Node x, Node y) {
            super(owner, "Independencies for <" + x.getName() + ", " + y.getName() + ">", Dialog.ModalityType.APPLICATION_MODAL);
            this.x = x;
            this.y = y;

            table.setTransferHandler(new DefaultTableTransferHandler(0));

            TableColumnModel cm = table.getColumnModel();

            // Column indices assumed; adjust if needed
            TableColumn colIndex = cm.getColumn(0); // #
            TableColumn colFact = cm.getColumn(1); // Fact
            TableColumn colResult = cm.getColumn(2); // Result
            TableColumn colPval = cm.getColumn(3); // p-value

            // # column: very skinny
            colIndex.setPreferredWidth(40);
            colIndex.setMaxWidth(40);

            // Result column: "INDEPENDENT"
            colResult.setPreferredWidth(100);
            colResult.setMaxWidth(100);

            // p-value column: ~6–8 chars
            colPval.setMinWidth(70);
            colPval.setPreferredWidth(70);

            // Fact column: stretch
            colFact.setMinWidth(350);
            // no max width → absorbs remaining space

            table.setRowSorter(new TableRowSorter<>(tableModel));

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel controls = new JPanel(new GridBagLayout());
            controls.setBorder(new EmptyBorder(10, 10, 6, 10));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(3, 3, 3, 3);
            gc.anchor = GridBagConstraints.WEST;

            gc.gridx = 0;
            gc.gridy = 0;
            controls.add(new JLabel("Candidate pool:"), gc);
            gc.gridx = 1;
            gc.gridy = 0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;
            controls.add(poolBox, gc);

            gc.gridx = 0;
            gc.gridy = 1;
            gc.fill = GridBagConstraints.NONE;
            gc.weightx = 0;
            controls.add(new JLabel("Depth:"), gc);
            gc.gridx = 1;
            gc.gridy = 1;
            controls.add(depthSpinner, gc);

            gc.gridx = 0;
            gc.gridy = 2;
            gc.gridwidth = 2;
            gc.fill = GridBagConstraints.NONE;
            gc.weightx = 0;
            controls.add(showButton, gc);

            resultsPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
            resultsPanel.add(new JLabel("(Click 'Show independencies' to run)"), BorderLayout.CENTER);

            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);

            showButton.addActionListener(e -> runSearch());

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(controls, BorderLayout.NORTH);
            getContentPane().add(resultsPanel, BorderLayout.CENTER);

            setSize(780, 520);
        }

        private void runSearch() {
            showButton.setEnabled(false);
            resultsPanel.removeAll();
            resultsPanel.add(new JLabel("Searching..."), BorderLayout.CENTER);
            resultsPanel.revalidate();
            resultsPanel.repaint();

            final PoolChoice choice = (PoolChoice) poolBox.getSelectedItem();
            final int k = (Integer) depthSpinner.getValue();

            SwingWorker<List<IndependenceResult>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<IndependenceResult> doInBackground() {
                    return findIndependencies(x, y, choice, k);
                }

                @Override
                protected void done() {
                    try {
                        List<IndependenceResult> found = get();
                        tableModel.setResults(found);

                        resultsPanel.removeAll();
                        if (found.isEmpty()) {
                            resultsPanel.add(emptyLabel, BorderLayout.CENTER);
                        } else {
                            resultsPanel.add(new JScrollPane(table), BorderLayout.CENTER);
                        }
                    } catch (Exception ex) {
                        resultsPanel.removeAll();
                        resultsPanel.add(new JLabel("Error: " + ex.getMessage()), BorderLayout.CENTER);
                    } finally {
                        showButton.setEnabled(true);
                        resultsPanel.revalidate();
                        resultsPanel.repaint();
                    }
                }
            };

            worker.execute();
        }
    }

    private final class IndepTableModel extends AbstractTableModel {
        private List<IndependenceResult> results = Collections.emptyList();

        void setResults(List<IndependenceResult> rs) {
            this.results = (rs == null) ? Collections.emptyList() : new ArrayList<>(rs);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "#";
                case 1 -> "Fact";
                case 2 -> "Result";
                case 3 -> "P-value";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IndependenceResult r = results.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> factString(r.getFact());
                case 2 -> "INDEPENDENT";
                case 3 -> fmt(r.getPValue());
                default -> "";
            };
        }
    }

    private void onModelGraphChanged() {
        var selectedVertexNames = getSelectedOverviewVertexNames();
        var selectedFactKeys    = getSelectedFactsKeys();

        // Graph changed => cached per-vertex results are stale.
        model.clearResults();

        // Recompute only what’s visible/selected (or compute nothing and let selection triggers do it)
        for (String v : selectedVertexNames) {
            model.ensureVertexComputed(v);
        }

        overviewModel.fireTableDataChanged();
        factsModel.fireTableDataChanged();

        reselectOverviewVerticesByName(selectedVertexNames);
        reselectFactsByKey(selectedFactKeys);

        // Update right-side details for the active vertex (histogram + repair button)
        String active = getActiveSelectedVertexName();
        if (active != null) refreshDetails(active);
    }

    private Set<String> getSelectedOverviewVertexNames() {
        Set<String> names = new HashSet<>();
        int[] rows = overviewTable.getSelectedRows();
        for (int r : rows) {
            int m = overviewTable.convertRowIndexToModel(r);
            Node v = model.getGraph().getNode(model.getVertexNames().get(m));
            if (v != null) {
                names.add(v.getName());
            }
        }
        return names;
    }

    private Set<String> getSelectedFactsKeys() {
        Set<String> keys = new HashSet<>();
        for (int vr : factsTable.getSelectedRows()) {
            IndependenceFact f = getIndependenceFactFromFactsRow(vr);
            if (f != null) keys.add(factKey(f));
        }
        return keys;
    }

    private IndependenceFact getIndependenceFact(int r) {
        int m = factsTable.convertRowIndexToModel(r);
        String name = model.getVertexNames().get(m);     // <-- WRONG LIST
        List<IndependenceResult> rs = model.getResultsForVertex(name);
        return rs.get(m).getFact();
    }

    private void recomputeOverviewForSelectedRows() {
        overviewModel.fireTableRowsUpdated(0, overviewTable.getRowCount() - 1);
    }

    private void recomputeFactsForSelectedRows() {
        factsModel.fireTableDataChanged();
    }

//    private void reselectOverviewVerticesByName(Set<String> names) {
//        ListSelectionModel sel = overviewTable.getSelectionModel();
//        sel.clearSelection();
//
//        for (int i = 0; i < overviewModel.getRowCount(); i++) {
//            Node v = model.getGraph().getNode(model.getVertexNames().get(i));
//            if (v != null && names.contains(v.getName())) {
//                int viewRow = overviewTable.convertRowIndexToView(i);
//                sel.addSelectionInterval(viewRow, viewRow);
//            }
//        }
//    }

//    private void reselectFactsByKey(Set<String> keys) {
//        ListSelectionModel sel = factsTable.getSelectionModel();
//        sel.clearSelection();
//
//        for (int i = 0; i < factsModel.getRowCount(); i++) {
//            IndependenceFact f = getIndependenceFact(i);
//            if (f != null && keys.contains(factKey(f))) {
//                int viewRow = factsTable.convertRowIndexToView(i);
//                sel.addSelectionInterval(viewRow, viewRow);
//            }
//        }
//    }

//    private Set<String> getSelectedOverviewVertexNames() {
//        Set<String> names = new HashSet<>();
//        int[] viewRows = overviewTable.getSelectedRows();
//        for (int vr : viewRows) {
//            int mr = overviewTable.convertRowIndexToModel(vr);
//            if (mr >= 0 && mr < model.getVertexNames().size()) {
//                names.add(model.getVertexNames().get(mr));
//            }
//        }
//        return names;
//    }

//    private Set<String> getSelectedFactsKeys() {
//        Set<String> keys = new HashSet<>();
//        String v = getActiveSelectedVertexName();
//        if (v == null) return keys;
//
//        int[] viewRows = factsTable.getSelectedRows();
//        for (int vr : viewRows) {
//            IndependenceFact f = getFactAtFactsViewRow(v, vr);
//            if (f != null) keys.add(factKey(f));
//        }
//        return keys;
//    }

    private IndependenceFact getFactAtFactsViewRow(String vertexName, int factsViewRow) {
        int mr = factsTable.convertRowIndexToModel(factsViewRow);
        List<IndependenceResult> rs = model.getResultsForVertex(vertexName);
        if (rs == null) return null;
        if (mr < 0 || mr >= rs.size()) return null;
        IndependenceResult r = rs.get(mr);
        return (r == null) ? null : r.getFact();
    }

    private IndependenceFact getFactAtFactsModelRow(String vertexName, int factsModelRow) {
        List<IndependenceResult> rs = model.getResultsForVertex(vertexName);
        if (rs == null) return null;
        if (factsModelRow < 0 || factsModelRow >= rs.size()) return null;
        IndependenceResult r = rs.get(factsModelRow);
        return (r == null) ? null : r.getFact();
    }

    private void reselectOverviewVerticesByName(Set<String> names) {
        ListSelectionModel sel = overviewTable.getSelectionModel();
        sel.clearSelection();

        for (int mr = 0; mr < model.getVertexNames().size(); mr++) {
            String name = model.getVertexNames().get(mr);
            if (names.contains(name)) {
                int vr = overviewTable.convertRowIndexToView(mr);
                sel.addSelectionInterval(vr, vr);
            }
        }
    }

    private void reselectFactsByKey(Set<String> keys) {
        ListSelectionModel sel = factsTable.getSelectionModel();
        sel.clearSelection();

        String v = getActiveSelectedVertexName();
        if (v == null) return;

        List<IndependenceResult> rs = model.getResultsForVertex(v);
        if (rs == null) return;

        for (int modelRow = 0; modelRow < rs.size(); modelRow++) {
            IndependenceFact f = rs.get(modelRow).getFact();
            if (f != null && keys.contains(factKey(f))) {
                int viewRow = factsTable.convertRowIndexToView(modelRow);
                sel.addSelectionInterval(viewRow, viewRow);
            }
        }
    }

    private IndependenceFact getIndependenceFactFromFactsRow(int factsViewRow) {
        String v = getActiveSelectedVertexName();
        if (v == null) return null;

        int factsModelRow = factsTable.convertRowIndexToModel(factsViewRow);

        List<IndependenceResult> rs = model.getResultsForVertex(v);
        if (rs == null || factsModelRow < 0 || factsModelRow >= rs.size()) return null;

        IndependenceResult r = rs.get(factsModelRow);
        return (r == null ? null : r.getFact());
    }

    private void showGraphDialog() {
        Graph graph = model.getGraph();

        // --- Tab 1: Text ---
        JTextArea ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(ta);
        textScroll.setPreferredSize(new Dimension(820, 520));

        // --- Tab 2: Render ---
        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.setEnableEditing(false);
        JScrollPane renderScroll = new JScrollPane(workbench);
        renderScroll.setPreferredSize(new Dimension(820, 520));

        // --- Tabs ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Graph", renderScroll);
        tabs.addTab("Text", textScroll);

        JOptionPane.showMessageDialog(
                this,
                tabs,
                "Current Graph",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void updateUndoButtonEnabled() {
        undoGraphButton.setEnabled(!graphHistory.isEmpty());
    }

    private Graph safeCopy(Graph g) {
        if (g == null) return null;
        try { return g.copy(); }
        catch (Throwable t) { return new EdgeListGraph(g); }
    }

    private void undoGraph() {
        if (graphHistory.isEmpty()) return;

        Graph prev = graphHistory.pop();
        applyingGraphProgrammatically = true;
        try {
            model.setGraph(prev);
        } finally {
            applyingGraphProgrammatically = false;
            updateUndoButtonEnabled();
        }
    }
}