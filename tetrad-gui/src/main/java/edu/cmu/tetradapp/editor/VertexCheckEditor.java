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
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.*;

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

import static edu.cmu.tetradapp.util.ParameterComponents.toArray;

/**
 * Swing editor for the Vertex Checker (per-vertex local Markov check).
 * <p>
 * Layout:
 * - Top controls: Independence Test, Conditioning Set Type, Alpha, Run
 * - Left: overview table (one row per vertex)
 * - Right: details for selected vertex (CS(X)/MB(X) list, tested facts table, histogram)
 */
public class VertexCheckEditor extends JPanel {

    private static final String PREF_KEY_TEST = "markovCheckerIndependenceTest";
    private static final String PREF_KEY_SET_TYPE = "markovCheckerConditioningSetType";

    private final VertexCheckIndTestModel model;

    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    private final JComboBox<IndependenceTestModel> indTestCombo = new JComboBox<>();
    private final JComboBox<String> conditioningCombo = new JComboBox<>();
    private final JCheckBox verbose = new JCheckBox("Verbose");
    private JTable overviewTable;
    private JTable factsTable;
    private AbstractTableModel overviewModel;
    private AbstractTableModel factsModel;
    private JPanel histogramPanel = new JPanel(new BorderLayout());

    private JButton runAll = new JButton("Run All");

    private IndependenceWrapper independenceWrapper;

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(VertexCheckEditor.class);

    private boolean initializing;

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

        initializing = false;
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
                "Click a row in the table to compute and view results for that vertex."
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
                model.runAllVertices(true);
                SwingUtilities.invokeLater(() -> {
                    overviewModel.fireTableDataChanged();
                    selectFirstRowIfAny();
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

        ocm.getColumn(0).setPreferredWidth(80);   // Vertex
        ocm.getColumn(1).setPreferredWidth(45);   // CS

        ocm.getColumn(2).setPreferredWidth(45);   // #t
        ocm.getColumn(3).setPreferredWidth(45);   // #p

        for (int c : new int[]{4,5,6,7,8,9,10}) {
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
        TableColumn colIndex  = cm.getColumn(0); // #
        TableColumn colFact   = cm.getColumn(1); // Fact
        TableColumn colResult = cm.getColumn(2); // Result
        TableColumn colPval   = cm.getColumn(3); // p-value

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
        factsScroll.setBorder(BorderFactory.createTitledBorder("Tests implied for selected vertex"));

        histogramPanel.setBorder(BorderFactory.createTitledBorder("P-value Histogram"));

        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.add(factsScroll, BorderLayout.CENTER);
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

                    // Only refresh details for whichever vertex is currently "active"
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

        edu.cmu.tetradapp.editor.HistogramPanel view = new edu.cmu.tetradapp.editor.HistogramPanel(histogram, true);
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

    private record SelectedRows(List<Integer> modelRows, List<String> vertices) {}

    private SelectedRows getSelectedVertices() {
        int[] viewRows = overviewTable.getSelectedRows();
        if (viewRows == null || viewRows.length == 0) {
            return new SelectedRows(List.of(), List.of());
        }

        List<Integer> modelRows = new ArrayList<>(viewRows.length);
        List<String> vertices  = new ArrayList<>(viewRows.length);

        for (int vr : viewRows) {
            int mr = overviewTable.convertRowIndexToModel(vr);
            modelRows.add(mr);
            vertices.add(model.getVertexNames().get(mr));
        }

        return new SelectedRows(modelRows, vertices);
    }

    /** Pick which selected vertex drives the right-side details panel. */
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
}