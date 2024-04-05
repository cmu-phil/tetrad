package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.model.AlgcomparisonModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.*;
import edu.cmu.tetradapp.util.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.cmu.tetradapp.model.AlgcomparisonModel.getAllSimulationParameters;

/**
 * AlgcomparisonEditor is a JPanel that displays a JTabbedPane with tabs for simulation, algorithm, statistics,
 * comparison, XML, and help. It provides methods for adding tabs, listeners, and setting text based on the selected
 * simulations, algorithms, and statistics.
 *
 * @author josephramsey
 */
public class AlgcomparisonEditor extends JPanel {
    /**
     * JLabel representing a message indicating that there are no parameters to edit.
     */
    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    /**
     * A JComboBox that holds instances of IndependenceTestModel.
     */
    private static JComboBox<IndependenceTestModel> indTestComboBox;
    /**
     * ComboBox for selecting a ScoreModel.
     */
    private static JComboBox<ScoreModel> scoreModelComboBox;
    /**
     * The AlgcomparisonModel class represents a model used in an algorithm comparison application. It contains methods
     * and properties related to the comparison of algorithms.
     */
    private final AlgcomparisonModel model;
    /**
     * Represents a box for holding parameter components.
     */
    private final Box parameterBox = Box.createVerticalBox();
    /**
     * JTextArea used for displaying simulation choice information.
     */
    private JTextArea simulationChoiceTextArea;
    /**
     * The TextArea component used for displaying algorithm choices.
     */
    private JTextArea algorithmChoiceTextArea;
    /**
     * JTextArea used for displaying statistics choices.
     */
    private JTextArea tableColumnsChoiceTextArea;
    /**
     * JTextArea used for displaying comparison results.
     */
    private JTextArea comparisonTextArea;
    /**
     * JTextArea used for displaying help choice information.
     */
    private JTextArea helpChoiceTextArea;
    /**
     * Button used to add a simulation.
     */
    private JButton addSimulation;
    /**
     * Button used to add an algorithm.
     */
    private JButton addAlgorithm;
    /**
     * Button used to add table columns.
     */
    private JButton addTableColumns;

    /**
     * Initializes an instance of AlgcomparisonEditor which is a JPanel containing a JTabbedPane that displays different
     * tabs for simulation, algorithm, table columns, comparison and help.
     *
     * @param model the AlgcomparisonModel to use for the editor
     */
    public AlgcomparisonEditor(AlgcomparisonModel model) {
        this.model = model;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
        tabbedPane.setPreferredSize(new Dimension(800, 400));

        addSimulationTab(tabbedPane);
        addAlgorithmTab(tabbedPane);
        addTableColumnsTab(tabbedPane);
        addComparisonTab(tabbedPane);
//        addXmlTab(tabbedPane); // todo work on this later.
        addHelpTab(tabbedPane);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }


    /**
     * Creates a map of parameter components for the given set of parameters and a Parameters object.
     *
     * @param params     the set of parameter names
     * @param parameters the Parameters object containing the parameter values
     * @return a map of parameter names to corresponding Box components
     */
    public static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters, boolean bothOptionAllowed) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescriptions.get(e), bothOptionAllowed),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    /**
     * Converts a map of parameter components to an array of Box objects.
     *
     * @param parameterComponents a map of parameter names to corresponding Box components
     * @return an array of Box objects containing the parameter components
     */
    public static Box[] toArray(Map<String, Box> parameterComponents) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();

        List<Box> boolComps = new LinkedList<>();
        List<Box> otherComps = new LinkedList<>();
        parameterComponents.forEach((k, v) -> {
            if (paramDescriptions.get(k).getDefaultValue() instanceof Boolean) {
                boolComps.add(v);
            } else {
                otherComps.add(v);
            }
        });

        return Stream.concat(otherComps.stream(), boolComps.stream())
                .toArray(Box[]::new);
    }

    /**
     * Creates a component for a specific parameter based on its type and default value.
     *
     * @param parameter  the name of the parameter
     * @param parameters the Parameters object containing the parameter values
     * @param paramDesc  the ParamDescription object containing information about the parameter
     * @return a Box component representing the parameter
     */
    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc,
                                                boolean bothOptionAllowed) {
        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();

        Object[] defaultValues = parameters.getValues(parameter);

        if (defaultValue instanceof Double) {
            double lowerBoundDouble = paramDesc.getLowerBoundDouble();
            double upperBoundDouble = paramDesc.getUpperBoundDouble();
            Double[] defValues = new Double[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Double) defaultValues[i];
            }
            component = getListDoubleTextField(parameter, parameters, defValues, lowerBoundDouble, upperBoundDouble);
        } else if (defaultValue instanceof Integer) {
            int lowerBoundInt = paramDesc.getLowerBoundInt();
            int upperBoundInt = paramDesc.getUpperBoundInt();
            Integer[] defValues = new Integer[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Integer) defaultValues[i];
            }
            component = getListIntTextField(parameter, parameters, defValues, lowerBoundInt, upperBoundInt);
        } else if (defaultValue instanceof Long) {
            long lowerBoundLong = paramDesc.getLowerBoundLong();
            long upperBoundLong = paramDesc.getUpperBoundLong();
            Long[] defValues = new Long[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Long) defaultValues[i];
            }
            component = getListLongTextField(parameter, parameters, defValues, lowerBoundLong, upperBoundLong);
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, bothOptionAllowed);
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
     * Creates a ListDoubleTextField component with the given parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListDoubleTextField component with the specified parameters
     */
    public static ListDoubleTextField getListDoubleTextField(String parameter, Parameters parameters,
                                                             Double[] defaultValues, double lowerBound, double upperBound) {
        ListDoubleTextField field = new ListDoubleTextField(defaultValues,
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Double> valuesList = new ArrayList<>();

            for (Double value : values) {
                if (Double.isNaN(value)) {
                    continue;
                }

                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Double[] newValues = valuesList.toArray(new Double[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

    /**
     * Returns a ListIntTextField component with the specified parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListIntTextField component with the specified parameters
     */
    public static ListIntTextField getListIntTextField(String parameter, Parameters parameters,
                                                       Integer[] defaultValues, double lowerBound, double upperBound) {
        ListIntTextField field = new ListIntTextField(defaultValues, 8);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Integer> valuesList = new ArrayList<>();

            for (Integer value : values) {
                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Integer[] newValues = valuesList.toArray(new Integer[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

    public static ListLongTextField getListLongTextField(String parameter, Parameters parameters,
                                                         Long[] defaultValues, long lowerBound, long upperBound) {
        ListLongTextField field = new ListLongTextField(defaultValues, 8);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Long> valuesList = new ArrayList<>();

            for (Long value : values) {
                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Long[] newValues = valuesList.toArray(new Long[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

//    /**
//     * Returns the XML text used for the XML tab in the AlgcomparisonEditor.
//     *
//     * @return the XML text
//     */
//    @NotNull
//    private static String getXmlText() {
//        return """
//                ** This is placeholder text **
//
//                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
//                <comparison>
//                    <compareBy>
//                        <search>
//                            <simulations>
//                                <simulation source="directory">
//                                    <path>src/test/resources/data/simulation</path>
//                                </simulation>
//                                <simulation source="generate">
//                                    <graphtype>RandomForward</graphtype>
//                                    <modeltype>SemSimulation</modeltype>
//                                </simulation>
//                            </simulations>
//                            <algorithms>
//                                <algorithm name="gfci">
//                                    <test>fisher-z-test</test>
//                                    <score>sem-bic-score</score>
//                                </algorithm>
//                                <algorithm name="fges">
//                                    <score>sem-bic-score</score>
//                                </algorithm>
//                            </algorithms>
//                            <parameters>
//                                <parameter name="numRuns">1</parameter>
//                                <parameter name="numMeasures">4,6</parameter>
//                                <parameter name="avgDegree">4</parameter>
//                            </parameters>
//                        </search>
//                    </compareBy>
//                    <statistics>
//                        <statistic>adjacencyPrecision</statistic>
//                        <statistic>arrowheadRecall</statistic>
//                        <statistic>adjacencyRecall</statistic>
//                    </statistics>
//                    <properties>
//                        <property name="showAlgorithmIndices">true</property>
//                        <property name="showSimulationIndices">true</property>
//                        <property name="sortByUtility">true</property>
//                        <property name="showUtilities">true</property>
//                        <property name="saveSearchGraphs">true</property>
//                        <property name="tabDelimitedTables">true</property>
//                    </properties>
//                </comparison>""";
//    }

    /**
     * Returns a Box component representing a boolean selection box.
     *
     * @param parameter         the name of the parameter
     * @param parameters        the Parameters object containing the parameter values
     * @param bothOptionAllowed whether the option is allows to select both true and false
     * @return a Box component representing the boolean selection box
     */
    public static Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean bothOptionAllowed) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");
        JRadioButton bothButton = new JRadioButton("Both");

        // Button group to ensure only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);
        selectionBtnGrp.add(bothButton);

        Object[] values = parameters.getValues(parameter);

        Boolean[] booleans = new Boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            booleans[i] = (Boolean) values[i];
        }

        // Set default selection
        if (booleans.length == 1 && booleans[0]) {
            yesButton.setSelected(true);
        } else if (booleans.length == 1) {
            noButton.setSelected(true);
        } else if (booleans.length == 2 && bothOptionAllowed) {
            bothButton.setSelected(true);
        }

        // Add to containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        if (bothOptionAllowed) {
            selectionBox.add(bothButton);
        }

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, (Object) new Boolean[]{true});
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, (Object[]) new Boolean[]{false});
            }
        });

        if (bothOptionAllowed) {
            bothButton.addActionListener((e) -> {
                JRadioButton button = (JRadioButton) e.getSource();
                if (button.isSelected()) {
                    parameters.set(parameter, (Object[]) new Boolean[]{true, false});
                }
            });
        }

        return selectionBox;
    }

    /**
     * Creates a StringTextField component with the specified parameters.
     *
     * @param parameter    the name of the parameter
     * @param parameters   the Parameters object containing the parameter values
     * @param defaultValue the default value for the component
     * @return a StringTextField component with the specified parameters
     */
    public static StringTextField getStringField(String parameter, Parameters parameters, String defaultValue) {
        StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

        field.setFilter((value, oldValue) -> {
            if (value.equals(field.getValue().trim())) {
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
     * Retrieves a simulation object based on the provided graph and simulation classes.
     *
     * @param graphClazz      The class of the random graph object.
     * @param simulationClazz The class of the simulation object.
     * @return The simulation object.
     * @throws NoSuchMethodException     If the constructor for the graph or simulation class cannot be found.
     * @throws InvocationTargetException If an error occurs while invoking the graph or simulation constructor.
     * @throws InstantiationException    If the graph or simulation class cannot be instantiated.
     * @throws IllegalAccessException    If the graph or simulation constructor or class is inaccessible.
     */
    @NotNull
    private static edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation(Class<? extends edu.cmu.tetrad.algcomparison.graph.RandomGraph> graphClazz, Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> simulationClazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        RandomGraph randomGraph = graphClazz.getConstructor().newInstance();
        return simulationClazz.getConstructor(RandomGraph.class).newInstance(randomGraph);
    }

    /**
     * Retrieves the parameter text for the given set of parameter names and parameters.
     *
     * @param paramNamesSet the set of parameter names
     * @param parameters    the Parameters object containing the parameter values
     * @return the parameter text
     */
    @NotNull
    private static String getParameterText(Set<String> paramNamesSet, Parameters parameters) {
        List<String> paramNames = new ArrayList<>(paramNamesSet);
        Collections.sort(paramNames);

        StringBuilder paramText = new StringBuilder();

        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        NumberFormat nf = new DecimalFormat("0.####");

        for (String name : paramNames) {
            ParamDescription description = paramDescriptions.get(name);
            Object[] values = parameters.getValues(name);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                if (values[i] instanceof Double)
                    sb.append(nf.format((double) values[i]));
                else if (values[i] instanceof Integer)
                    sb.append((int) values[i]);
                else if (values[i] instanceof Long)
                    sb.append((long) values[i]);
                else
                    sb.append(values[i]);
            }

            paramText.append("\n\n- ").append(name).append(" = ").append(sb);
            paramText.append("\n").append(description.getShortDescription());
            paramText.append(". ").append(description.getLongDescription());
        }

        return paramText.toString();
    }

    public static void scrollToWord(JTextArea textArea, String word) throws BadLocationException {
        String text = textArea.getText();
        int pos = text.indexOf(word);
        if (pos >= 0) {
            Rectangle viewRect = textArea.modelToView2D(pos).getBounds();
            if (viewRect != null) {
                viewRect = new Rectangle(viewRect.x, viewRect.y, textArea.getWidth(), textArea.getHeight());
                textArea.scrollRectToVisible(viewRect);
            }
        }
    }

    @NotNull
    private static Class<? extends RandomGraph> getGraphClazz(String graphString) {
        List<String> graphTypeStrings = Arrays.asList(ParameterTab.GRAPH_TYPE_ITEMS);

        return switch (graphTypeStrings.indexOf(graphString)) {
            case 0:
                yield RandomForward.class;
            case 1:
                yield ErdosRenyi.class;
            case 2:
                yield ScaleFree.class;
            case 4:
                yield Cyclic.class;
            case 5:
                yield RandomSingleFactorMim.class;
            case 6:
                yield RandomTwoFactorMim.class;
            default:
                throw new IllegalArgumentException("Unexpected value: " + graphString);
        };
    }

    /**
     * Adds the dropdowns for selecting an independence test and a score to the given Box.
     *
     * @param vert1 the Box to which the dropdowns will be added
     */
    private void addTestAndScoreDropdowns(Box vert1) {
        IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
        List<IndependenceTestModel> models = independenceTestModels.getModels();

        indTestComboBox = new JComboBox<>();

        for (IndependenceTestModel model : models) {
            indTestComboBox.addItem(model);
        }

        String lastIndependenceTest = model.getLastIndependenceTest();

        for (int i = 0; i < indTestComboBox.getItemCount(); i++) {
            IndependenceTestModel independenceTestModel = indTestComboBox.getItemAt(i);
            if (independenceTestModel.getName().equals(lastIndependenceTest)) {
                indTestComboBox.setSelectedIndex(i);
                break;
            }
        }

        Box horiz4 = Box.createHorizontalBox();
        horiz4.add(new JLabel("Choose an independence test:"));
        horiz4.add(Box.createHorizontalGlue());
        horiz4.add(indTestComboBox);
        vert1.add(horiz4);

        ScoreModels scoreModels = ScoreModels.getInstance();
        List<ScoreModel> scoreModelsList = scoreModels.getModels();

        scoreModelComboBox = new JComboBox<>();

        for (ScoreModel model : scoreModelsList) {
            scoreModelComboBox.addItem(model);
        }

        String lastScore = model.getLastScore();

        for (int i = 0; i < scoreModelComboBox.getItemCount(); i++) {
            ScoreModel scoreModel = scoreModelComboBox.getItemAt(i);
            if (scoreModel.getName().equals(lastScore)) {
                scoreModelComboBox.setSelectedIndex(i);
                break;
            }
        }

        Box horiz5 = Box.createHorizontalBox();
        horiz5.add(new JLabel("Choose a score:"));
        horiz5.add(Box.createHorizontalGlue());
        horiz5.add(scoreModelComboBox);
        vert1.add(horiz5);
    }

    /**
     * Adds a simulation tab to the provided JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to which the simulation tab is added
     */
    private void addSimulationTab(JTabbedPane tabbedPane) {
        simulationChoiceTextArea = new JTextArea();
        simulationChoiceTextArea.setLineWrap(true);
        simulationChoiceTextArea.setWrapStyleWord(true);
        simulationChoiceTextArea.setEditable(false);

        setSimulationText();

        JPanel simulationChoice = new JPanel();
        simulationChoice.setLayout(new BorderLayout());
        simulationChoice.add(new JScrollPane(simulationChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        Box simulationSelectionBox = Box.createHorizontalBox();
        simulationSelectionBox.add(Box.createHorizontalGlue());

        addSimulation = new JButton("Add Simulation");
        addAddSimulationListener();

        JButton removeLastSimulation = new JButton("Remove Last Simulation");
        removeLastSimulation.addActionListener(e -> {
            model.removeLastSimulation();
            setSimulationText();
            setComparisonText();
        });

        JButton editSimulationParameters = new JButton("Edit Parameters");

        editSimulationParameters.addActionListener(e -> {
            List<Simulation> simulations = model.getSelectedSimulations().getSimulations();
            Set<String> params = getAllSimulationParameters(simulations);

            this.parameterBox.removeAll();

            if (params.isEmpty()) {
                this.parameterBox.add(NO_PARAM_LBL, BorderLayout.NORTH);
            } else {
                Box parameters = Box.createVerticalBox();
                Box[] paramBoxes = toArray(
                        createParameterComponents(params, model.getParameters(), false));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                Box horiz = Box.createHorizontalBox();
                horiz.add(new JLabel("Please type comma-separated lists of values, thus: 10, 100, 1000"));
                horiz.add(Box.createHorizontalGlue());
                horiz.setBorder(new EmptyBorder(0, 0, 10, 0));
                this.parameterBox.add(horiz, BorderLayout.NORTH);
                this.parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
                this.parameterBox.setBorder(new EmptyBorder(10, 10, 10, 10));
                this.parameterBox.setPreferredSize(new Dimension(700, 400));

            }

            this.parameterBox.validate();
            this.parameterBox.repaint();

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Simulation Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton("Done");

            doneButton.addActionListener(e1 -> {
                setSimulationText();
                setComparisonText();
                dialog.dispose();

                SwingUtilities.invokeLater(() -> {
                    revalidate();
                    repaint();
                });
            });

            buttonPanel.add(doneButton);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });

        simulationSelectionBox.add(addSimulation);
        simulationSelectionBox.add(removeLastSimulation);
        simulationSelectionBox.add(editSimulationParameters);
        simulationSelectionBox.add(Box.createHorizontalGlue());
        simulationChoice.add(simulationSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Simulations", simulationChoice);
    }

    /**
     * Adds an algorithm tab to the given JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to add the algorithm tab to
     */
    private void addAlgorithmTab(JTabbedPane tabbedPane) {
        algorithmChoiceTextArea = new JTextArea();
        algorithmChoiceTextArea.setLineWrap(true);
        algorithmChoiceTextArea.setWrapStyleWord(true);
        algorithmChoiceTextArea.setEditable(false);
        setAlgorithmText();

        Box algorithSelectionBox = Box.createHorizontalBox();
        algorithSelectionBox.add(Box.createHorizontalGlue());

        addAlgorithm = new JButton("Add Algorithm");
        addAddAlgorithmListener();

        JButton removeLastAlgorithm = new JButton("Remove Last Algorithm");
        removeLastAlgorithm.addActionListener(e -> {
            model.removeLastAlgorithm();
            setAlgorithmText();
            setComparisonText();
        });

        JButton editAlgorithmParameters = new JButton("Edit Parameters");

        editAlgorithmParameters.addActionListener(e -> {
            List<Algorithm> algorithm = model.getSelectedAlgorithms().getAlgorithms();
            Set<String> params = AlgcomparisonModel.getAllAlgorithmParameters(algorithm);

            this.parameterBox.removeAll();

            if (params.isEmpty()) {
                this.parameterBox.add(NO_PARAM_LBL, BorderLayout.NORTH);
            } else {
                Box parameters = Box.createVerticalBox();
                Box[] paramBoxes = ParameterComponents.toArray(
                        createParameterComponents(params, model.getParameters(), true));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                Box horiz = Box.createHorizontalBox();
                horiz.add(new JLabel("Please type comma-separated lists of values, thus: 10, 100, 1000"));
                horiz.add(Box.createHorizontalGlue());
                horiz.setBorder(new EmptyBorder(0, 0, 10, 0));
                this.parameterBox.add(horiz, BorderLayout.NORTH);
                this.parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
                this.parameterBox.setBorder(new EmptyBorder(10, 10, 10, 10));
                this.parameterBox.setPreferredSize(new Dimension(700, 400));
            }

            this.parameterBox.validate();
            this.parameterBox.repaint();

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Algorithm Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(new PaddingPanel(this.parameterBox), BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton("Done");

            doneButton.addActionListener(e1 -> {
                setAlgorithmText();
                setComparisonText();
                dialog.dispose();
            });

            buttonPanel.add(doneButton);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });

        algorithSelectionBox.add(addAlgorithm);
        algorithSelectionBox.add(removeLastAlgorithm);
        algorithSelectionBox.add(editAlgorithmParameters);
        algorithSelectionBox.add(Box.createHorizontalGlue());

        JPanel algorithmChoice = new JPanel();
        algorithmChoice.setLayout(new BorderLayout());
        algorithmChoice.add(new JScrollPane(algorithmChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), BorderLayout.CENTER);
        algorithmChoice.add(algorithSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Algorithms", algorithmChoice);
    }

//    /**
//     * Adds an XML tab to the provided JTabbedPane.
//     *
//     * @param tabbedPane the JTabbedPane to which the XML tab is added
//     */
//    private void addXmlTab(JTabbedPane tabbedPane) {
//        JPanel xmlPanel = new JPanel();
//        xmlPanel.setLayout(new BorderLayout());
//        JTextArea xmlTextArea = new JTextArea();
//        xmlTextArea.setLineWrap(false);
//        xmlTextArea.setWrapStyleWord(false);
//        xmlTextArea.setEditable(false);
//        xmlTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
//        xmlTextArea.setText(getXmlText());
//        xmlPanel.add(new JScrollPane(xmlTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
//
//        JButton loadXml = new JButton("Load XML");
//        JButton saveXml = new JButton("Save XML");
//
//        loadXml.addActionListener(e -> {
//            JOptionPane.showMessageDialog(this, "This will load and XML file and parse it to set the" + " configuration of this tool.");
//            setSimulationText();
//            setAlgorithmText();
//            setTableColumnsText();
//        });
//
//        saveXml.addActionListener(e -> {
//            JOptionPane.showMessageDialog(this, "This will save the XML file shown in this panel.");
//            setSimulationText();
//            setAlgorithmText();
//            setTableColumnsText();
//        });
//
//        Box xmlSelectionBox = Box.createHorizontalBox();
//        xmlSelectionBox.add(Box.createHorizontalGlue());
//        xmlSelectionBox.add(loadXml);
//        xmlSelectionBox.add(saveXml);
//        xmlSelectionBox.add(Box.createHorizontalGlue());
//
//        xmlPanel.add(xmlSelectionBox, BorderLayout.SOUTH);
//        tabbedPane.addTab("XML", xmlPanel);
//    }

    /**
     * Adds a table columns tab to the provided JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to add the statistics tab to
     */
    private void addTableColumnsTab(JTabbedPane tabbedPane) {
        tableColumnsChoiceTextArea = new JTextArea();
        tableColumnsChoiceTextArea.setLineWrap(true);
        tableColumnsChoiceTextArea.setWrapStyleWord(true);
        tableColumnsChoiceTextArea.setEditable(false);
        setTableColumnsText();

        Box tableColumnsSelectionBox = Box.createHorizontalBox();
        tableColumnsSelectionBox.add(Box.createHorizontalGlue());

        addTableColumns = new JButton("Add Table Column(s)");
        addAddTableColumnsListener();

        JButton removeLastTableColumn = new JButton("Remove Last Column");
        removeLastTableColumn.addActionListener(e -> {
            model.removeLastTableColumn();
            setTableColumnsText();
            setComparisonText();
        });

        tableColumnsSelectionBox.add(addTableColumns);
        tableColumnsSelectionBox.add(removeLastTableColumn);
        tableColumnsSelectionBox.add(Box.createHorizontalGlue());

        JPanel tableColumnsChoice = new JPanel();
        tableColumnsChoice.setLayout(new BorderLayout());
        tableColumnsChoice.add(new JScrollPane(tableColumnsChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        tableColumnsChoice.add(tableColumnsSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Table Columns", tableColumnsChoice);
    }

    /**
     * Adds a comparison tab to the given JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to add the comparison tab to
     */
    private void addComparisonTab(JTabbedPane tabbedPane) {
        comparisonTextArea = new JTextArea();
        comparisonTextArea.setLineWrap(false);
        comparisonTextArea.setWrapStyleWord(false);
        comparisonTextArea.setEditable(false);
        comparisonTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        setComparisonText();

        JButton runComparison = runComparisonButton();

        // todo work on this later.
//        JButton setComparisonParameters = new JButton("Edit Comparison Parameters");

//        setComparisonParameters.addActionListener(e -> JOptionPane.showMessageDialog(this, "This will allow you to set the parameters for " + "the comparison."));

        Box comparisonSelectionBox = Box.createHorizontalBox();
        comparisonSelectionBox.add(Box.createHorizontalGlue());
//        comparisonSelectionBox.add(setComparisonParameters);
        comparisonSelectionBox.add(runComparison);
        comparisonSelectionBox.add(Box.createHorizontalGlue());

        JPanel comparisonPanel = new JPanel();
        comparisonPanel.setLayout(new BorderLayout());
        comparisonPanel.add(new JScrollPane(comparisonTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        comparisonPanel.add(comparisonSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Comparison", comparisonPanel);
    }

    @NotNull
    private JButton runComparisonButton() {
        JButton runComparison = new JButton("Run Comparison");

        runComparison.addActionListener(e -> {

            class MyWatchedProcess extends WatchedProcess {

                public void watch() {
                    ByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
                    java.io.PrintStream ps = new java.io.PrintStream(baos);
                    model.runComparison(ps);
                    ps.flush();
                    comparisonTextArea.setText(baos.toString());

                    SwingUtilities.invokeLater(() -> {
                        try {
                            scrollToWord(comparisonTextArea, "AVERAGE VALUE");
                        } catch (BadLocationException ex) {
                            System.out.println("Scrolling operation failed.");
                        }
                    });
                }
            }

            new MyWatchedProcess();
        });
        return runComparison;
    }

    /**
     * Adds a help tab to the provided JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to which the help tab is added
     */
    private void addHelpTab(JTabbedPane tabbedPane) {
        JPanel helpChoice = new JPanel();
        helpChoice.setLayout(new BorderLayout());
        helpChoiceTextArea = new JTextArea();
        helpChoiceTextArea.setLineWrap(true);
        helpChoiceTextArea.setWrapStyleWord(true);
        helpChoiceTextArea.setEditable(false);

        setHelpText();
        helpChoice.add(helpChoiceTextArea, BorderLayout.CENTER);

        Box helpSelectionBox = Box.createHorizontalBox();
        helpSelectionBox.add(Box.createHorizontalGlue());
        helpSelectionBox.add(Box.createHorizontalGlue());

        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BorderLayout());
        helpPanel.add(new JScrollPane(helpChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        helpPanel.add(helpSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Help", helpPanel);
    }

    /**
     * Adds a listener to the "Add Simulation" button. When the button is clicked, a dialog is displayed to choose the
     * graph type and simulation type. If the "Add" button in the dialog is clicked, the selected graph and simulation
     * types are used to create a simulation and add it to the model.
     */
    private void addAddSimulationListener() {
        addSimulation.addActionListener(e -> {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            Box vert1 = Box.createVerticalBox();
            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Choose a graph type:"));
            horiz2.add(Box.createHorizontalGlue());
            JComboBox<String> graphsDropdown = new JComboBox<>();

            String lastGraphChoice = model.getLastGraphChoice();

            if (lastGraphChoice != null) {
                graphsDropdown.addItem(lastGraphChoice);
            }

            graphsDropdown.addActionListener(e1 -> {
                String selectedItem = (String) graphsDropdown.getSelectedItem();

                if (selectedItem != null) {
                    model.setLastGraphChoice(selectedItem);
                }
            });

            Arrays.stream(ParameterTab.GRAPH_TYPE_ITEMS).forEach(graphsDropdown::addItem);
            graphsDropdown.setMaximumSize(graphsDropdown.getPreferredSize());
            graphsDropdown.setSelectedItem(model.getLastGraphChoice());

            horiz2.add(graphsDropdown);
            vert1.add(horiz2);
            Box horiz3 = Box.createHorizontalBox();
            horiz3.add(new JLabel("Choose a simulation type:"));
            horiz3.add(Box.createHorizontalGlue());

            JComboBox<String> simulationsDropdown = new JComboBox<>();

            Arrays.stream(ParameterTab.MODEL_TYPE_ITEMS).forEach(simulationsDropdown::addItem);
            simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());

            simulationsDropdown.setSelectedItem(model.getLastSimulationChoice());

            simulationsDropdown.addActionListener(e1 -> {
                String selectedItem = (String) simulationsDropdown.getSelectedItem();

                if (selectedItem != null) {
                    model.setLastSimulationChoice(selectedItem);
                }
            });

            horiz3.add(simulationsDropdown);
            vert1.add(horiz3);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = getButtonPanel(graphsDropdown, simulationsDropdown, dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
    }

    @NotNull
    private JPanel getButtonPanel(JComboBox<String> graphsDropdown, JComboBox<String> simulationsDropdown, JDialog dialog) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        // Add action listeners for the buttons
        addButton.addActionListener(e1 -> {
            String graphString = (String) graphsDropdown.getSelectedItem();
            String simulationString = (String) simulationsDropdown.getSelectedItem();

            Class<? extends RandomGraph> graphClazz = getGraphClazz(graphString);

            List<String> simulationTypeStrings = Arrays.asList(ParameterTab.MODEL_TYPE_ITEMS);

            Class<? extends Simulation> simulationClass = switch (simulationTypeStrings.indexOf(simulationString)) {
                case 0:
                    yield edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation.class;
                case 1:
                    yield edu.cmu.tetrad.algcomparison.simulation.SemSimulation.class;
                case 2:
                    yield edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel.class;
                case 3:
                    yield edu.cmu.tetrad.algcomparison.simulation.NLSemSimulation.class;
                case 4:
                    yield edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation.class;
                case 5:
                    yield edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation.class;
                case 6:
                    yield edu.cmu.tetrad.algcomparison.simulation.TimeSeriesSemSimulation.class;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + simulationString);
            };

            try {
                model.addSimulation(getSimulation(graphClazz, simulationClass));
                setComparisonText();
                setSimulationText();
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }

            dialog.dispose(); // Close the dialog
        });

        cancelButton.addActionListener(e12 -> {
            dialog.dispose(); // Close the dialog
        });

        // Add the buttons to the button panel
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

    /**
     * Adds an ActionListener to the addAlgorithm button
     */
    private void addAddAlgorithmListener() {
        addAlgorithm.addActionListener(e -> {
            AlgorithmModels algorithmModels = AlgorithmModels.getInstance();
            List<AlgorithmModel> algorithmModels1 = algorithmModels.getModels(DataType.Continuous, false);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            JComboBox<AlgorithmModel> algorithmDropdown = new JComboBox<>();

            for (AlgorithmModel model : algorithmModels1) {
                algorithmDropdown.addItem(model);
            }

            String lastAlgorithmChoice = model.getLastAlgorithmChoice();

            for (int i = 0; i < algorithmDropdown.getItemCount(); i++) {
                if (algorithmDropdown.getItemAt(i).getName().equals(lastAlgorithmChoice)) {
                    algorithmDropdown.setSelectedIndex(i);
                    break;
                }
            }

            algorithmDropdown.addActionListener(e1 -> {
                AlgorithmModel selectedItem = (AlgorithmModel) algorithmDropdown.getSelectedItem();

                if (selectedItem != null) {
                    model.setLastAlgorithmChoice(selectedItem.getName());
                }
            });

            Box vert1 = Box.createVerticalBox();
            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Choose an algorithm:"));
            horiz2.add(Box.createHorizontalGlue());
            horiz2.add(algorithmDropdown);
            vert1.add(horiz2);

            addTestAndScoreDropdowns(vert1);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton addButton = new JButton("Add");
            JButton cancelButton = new JButton("Cancel");

            // Add action listeners for the buttons
            addButton.addActionListener(e1 -> {
                AlgorithmModel selectedItem = (AlgorithmModel) algorithmDropdown.getSelectedItem();

                if (selectedItem == null) {
                    return;
                }

                Class<?> algorithm = selectedItem.getAlgorithm().clazz();

                IndependenceTestModel testModel = (IndependenceTestModel) indTestComboBox.getSelectedItem();

                AnnotatedClass<TestOfIndependence> test = null;

                if (testModel != null) {
                    test = testModel.getIndependenceTest();
                }

                ScoreModel scoreModel = (ScoreModel) scoreModelComboBox.getSelectedItem();
                AnnotatedClass<Score> score = null;

                if (scoreModel != null) {
                    score = scoreModel.getScore();
                }

                IndependenceWrapper independenceWrapper = null;
                ScoreWrapper scoreWrapper = null;

                try {
                    if (test != null) {
                        independenceWrapper = (IndependenceWrapper) test.clazz().getConstructor().newInstance();
                    }

                    if (score != null) {
                        scoreWrapper = (ScoreWrapper) score.clazz().getConstructor().newInstance();
                    }

                    if (testModel != null) {
                        model.setLastIndependenceTest(testModel.getName());
                    }

                    if (scoreModel != null) {
                        model.setLastScore(scoreModel.getName());
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    throw new RuntimeException(ex);
                }

                try {
                    Algorithm algorithmImpl = (Algorithm) algorithm.getConstructor().newInstance();

                    if (algorithmImpl instanceof TakesIndependenceWrapper && independenceWrapper != null) {
                        ((TakesIndependenceWrapper) algorithmImpl).setIndependenceWrapper(independenceWrapper);
                    }

                    if (algorithmImpl instanceof UsesScoreWrapper && scoreWrapper != null) {
                        ((UsesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
                    }

                    model.addAlgorithm(algorithmImpl);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    throw new RuntimeException(ex);
                }

                setAlgorithmText();
                setComparisonText();
                dialog.dispose();
            });

            cancelButton.addActionListener(e12 -> {
                // Handle the Cancel button click event
                System.out.println("Cancel button clicked");
                dialog.dispose(); // Close the dialog
            });

            // Add the buttons to the button panel
            buttonPanel.add(addButton);
            buttonPanel.add(cancelButton);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
    }

    /**
     * Adds an action listener to the addTableColumns button. This action listener creates a table model, adds a JTable
     * to the panel, and sets a row sorter for the table based on user input. It also creates a text field with a
     * document listener to filter the table based on user input.
     */
    private void addAddTableColumnsListener() {
        addTableColumns.addActionListener(e -> {
            java.util.Set<AlgcomparisonModel.MyTableColumn> selectedColumns = new HashSet<>();
            List<AlgcomparisonModel.MyTableColumn> allTableColumns = model.getAllTableColumns();

            // Create a table idaCheckEst for the results of the IDA check
            TableColumnSelectionModel columnSelectionTableModel = new TableColumnSelectionModel(allTableColumns, selectedColumns);
            this.setLayout(new BorderLayout());

            // Add the table to the left
            JTable table = new JTable(columnSelectionTableModel);
            NumberFormat numberFormat = NumberFormatUtil.getInstance().getNumberFormat();
            IdaEditor.NumberFormatRenderer numberRenderer = new IdaEditor.NumberFormatRenderer(numberFormat);
            table.setDefaultRenderer(Double.class, numberRenderer);
            table.setAutoCreateRowSorter(true);
            table.setFillsViewportHeight(true);
            ((TableColumnSelectionModel) table.getModel()).setTableRef(table); // Set the table reference

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);

            TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(0).setCellRenderer(centerRenderer);

            this.add(new JScrollPane(table));

            // Create a TableRowSorter and set it to the JTable
            TableRowSorter<TableColumnSelectionModel> sorter = new TableRowSorter<>(columnSelectionTableModel);
            table.setRowSorter(sorter);

//            sorter.addRowSorterListener(e2 -> {
//
//                if (e2.getType() == RowSorterEvent.Type.SORTED) {
//                    List<AlgcomparisonModel.MyTableColumn> visiblePairs = new ArrayList<>();
//                    int rowCount = table.getRowCount();
//
//                    for (int i = 0; i < rowCount; i++) {
//                        int modelIndex = table.convertRowIndexToModel(i);
//                        visiblePairs.add(allTableColumns.get(modelIndex));
//                    }
//                }
//            });

            // Create the text field
            JLabel label = new JLabel("Regexes (semicolon separated):");
            JTextField filterText = new JTextField(15);
            filterText.setMaximumSize(new Dimension(500, 20));
            label.setLabelFor(filterText);

            // Create a listener for the text field that will update the table's row sort
            filterText.getDocument().addDocumentListener(new DocumentListener() {

                /**
                 * Filters the table based on the text in the text field.
                 */
                private void filter() {
                    String text = filterText.getText();
                    if (text.trim().isEmpty()) {
                        sorter.setRowFilter(null);
                    } else {
                        String[] textParts = text.split(";");
                        List<RowFilter<Object, Object>> filters = new ArrayList<>(textParts.length);
                        for (String part : textParts) {
                            try {
                                String trim = part.trim();
                                filters.add(RowFilter.regexFilter(trim));
                            } catch (PatternSyntaxException e) {
                                // ignore
                            }
                        }

                        sorter.setRowFilter(RowFilter.orFilter(filters));
                    }
                }


                /**
                 * Inserts text into the text field.
                 *
                 * @param e the document event.
                 */
                @Override
                public void insertUpdate(DocumentEvent e) {
                    filter();
                }

                /**
                 * Removes text from the text field.
                 *
                 * @param e the document event.
                 */
                @Override
                public void removeUpdate(DocumentEvent e) {
                    filter();
                }

                /**
                 * Changes text in the text field.
                 *
                 * @param e the document event.
                 */
                @Override
                public void changedUpdate(DocumentEvent e) {
                    // this method won't be called for plain text fields
                }
            });

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            Box vert1 = Box.createVerticalBox();
            vert1.add(new JLabel("Choose new columns for your tables:"));

            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(label);
            horiz2.add(filterText);
            vert1.add(horiz2);
            vert1.add(Box.createVerticalStrut(5));

            vert1.add(new JScrollPane(table));
            vert1.add(Box.createVerticalStrut(10));

            Box horiz3 = Box.createHorizontalBox();
            horiz3.add(Box.createHorizontalGlue());

            JButton selectUsedParameters = new JButton("Select Parameters Used");
            horiz3.add(selectUsedParameters);

            JButton selectLastStatisticsUsed = new JButton("Select Last Statistics Used");
            horiz3.add(selectLastStatisticsUsed);

            horiz3.add(Box.createHorizontalGlue());

            vert1.add(horiz3);

            selectUsedParameters.addActionListener(e1 -> {
                for (int i = 0; i < table.getRowCount(); i++) {
                    AlgcomparisonModel.MyTableColumn myTableColumn = columnSelectionTableModel.getMyTableColumn(i);

                    if (myTableColumn.getType() == AlgcomparisonModel.MyTableColumn.ColumnType.PARAMETER
                        && myTableColumn.isSetByUser()) {
                        columnSelectionTableModel.selectRow(i);
                    }
                }
            });

            selectLastStatisticsUsed.addActionListener(e1 -> {
                for (int i = 0; i < table.getRowCount(); i++) {
                    AlgcomparisonModel.MyTableColumn myTableColumn = columnSelectionTableModel.getMyTableColumn(i);
                    List<String> lastStatisticsUsed = model.getLastStatisticsUsed();

                    if (myTableColumn.getType() == AlgcomparisonModel.MyTableColumn.ColumnType.STATISTIC
                        && lastStatisticsUsed.contains(myTableColumn.getColumnName())) {
                        columnSelectionTableModel.selectRow(i);
                    }
                }
            });

            panel.add(vert1, BorderLayout.CENTER);
            panel.setPreferredSize(new Dimension(500, 500));

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Statistic", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = getButtonPanel(columnSelectionTableModel, dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });


    }

    @NotNull
    private JPanel getButtonPanel(TableColumnSelectionModel columnSelectionTableModel, JDialog dialog) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        // Add action listeners for the buttons
        addButton.addActionListener(e1 -> {
            List<AlgcomparisonModel.MyTableColumn> selectedTableColumns = new ArrayList<>(
                    columnSelectionTableModel.getSelectedTableColumns());

            for (AlgcomparisonModel.MyTableColumn column : selectedTableColumns) {
                model.addTableColumn(column);
            }

            setTableColumnsText();
            setComparisonText();
            dialog.dispose();
        });

        cancelButton.addActionListener(e12 -> {
            // Handle the Cancel button click event
            System.out.println("Cancel button clicked");
            dialog.dispose(); // Close the dialog
        });

        // Add the buttons to the button panel
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

    /**
     * Sets the text in the simulationChoiceTextArea based on the selected simulations in the model. If no simulations
     * are selected, it displays a message indicating that at least one simulation needs to be selected. If only one
     * simulation is selected, it displays information about the selected simulation. If multiple simulations are
     * selected, it displays information about each of the selected simulations. It also appends the simulation
     * parameter text obtained from getSimulationParameterText() method.
     */
    private void setSimulationText() {
        simulationChoiceTextArea.setText("");

        Simulations selectedSimulations = model.getSelectedSimulations();
        List<Simulation> simulations = selectedSimulations.getSimulations();

        if (simulations.isEmpty()) {
            simulationChoiceTextArea.append("""
                     ** No simulations have been selected. Please select at least one simulation using the Add Simulation button below. **
                    """);
            return;
        } else if (simulations.size() == 1) {
            simulationChoiceTextArea.setText("""
                    The following simulation has been selected. This simulations will be run with the selected algorithms.
                                        
                    """);

            Simulation simulation = simulations.get(0);
            Class<? extends RandomGraph> randomGraphClass = simulation.getRandomGraphClass();
            Class<? extends Simulation> simulationClass = simulation.getSimulationClass();
            simulationChoiceTextArea.append("Selected graph type = " + (randomGraphClass == null ? "None" : randomGraphClass.getSimpleName() + "\n"));
            simulationChoiceTextArea.append("Selected simulation type = " + simulationClass.getSimpleName() + "\n");
        } else {
            simulationChoiceTextArea.setText("""
                    The following simulations have been selected. These simulations will be run with the selected algorithms.
                    """);
            for (int i = 0; i < simulations.size(); i++) {
                simulationChoiceTextArea.append("\n");
                simulationChoiceTextArea.append("Simulation #" + (i + 1) + ":\n");
                simulationChoiceTextArea.append("Selected graph type = " + simulations.get(i).getRandomGraphClass().getSimpleName() + "\n");
                simulationChoiceTextArea.append("Selected simulation type = " + simulations.get(i).getSimulationClass().getSimpleName() + "\n");
            }
        }

        simulationChoiceTextArea.append(getSimulationParameterText());
        simulationChoiceTextArea.setCaretPosition(0);
    }

    private void setAlgorithmText() {
        algorithmChoiceTextArea.setText("");

        Algorithms selectedAlgorithms = model.getSelectedAlgorithms();
        List<Algorithm> algorithms = selectedAlgorithms.getAlgorithms();

        if (algorithms.isEmpty()) {
            algorithmChoiceTextArea.append("""
                     ** No algorithm have been selected. Please select at least one algorithm using the Add Algorithm button below. **
                    """);
            return;
        } else if (algorithms.size() == 1) {
            algorithmChoiceTextArea.setText("""
                    The following algorithm has been selected. This algorithm will be run with the selected simulations.
                                        
                    """);

            Algorithm algorithm = algorithms.get(0);
            algorithmChoiceTextArea.append("Selected algorithm: " + algorithm.getDescription() + "\n");

            if (algorithm instanceof TakesIndependenceWrapper) {
                algorithmChoiceTextArea.append("Selected independence test = " + ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getDescription() + "\n");
            }

            if (algorithm instanceof UsesScoreWrapper) {
                algorithmChoiceTextArea.append("Selected score = " + ((UsesScoreWrapper) algorithm).getScoreWrapper().getDescription() + "\n");
            }

        } else {
            algorithmChoiceTextArea.setText("""
                    The following algorithms have been selected. These algorithms will be run with the selected simulations.
                    """);
            for (int i = 0; i < algorithms.size(); i++) {
                Algorithm algorithm = algorithms.get(i);
                algorithmChoiceTextArea.append("\nAlgorithm #" + (i + 1) + ". " + algorithm.getDescription() + "\n");

                if (algorithm instanceof TakesIndependenceWrapper) {
                    algorithmChoiceTextArea.append("Selected independence test = " + ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getDescription() + "\n");
                }

                if (algorithm instanceof UsesScoreWrapper) {
                    algorithmChoiceTextArea.append("Selected score = " + ((UsesScoreWrapper) algorithm).getScoreWrapper().getDescription() + "\n");
                }
            }
        }

        algorithmChoiceTextArea.append(getAlgorithmParameterText());
        algorithmChoiceTextArea.setCaretPosition(0);
    }

    /**
     * Sets the text in the statisticsChoiceTextArea based on the selected statistics.
     */
    private void setTableColumnsText() {
        tableColumnsChoiceTextArea.setText("");

        List<AlgcomparisonModel.MyTableColumn> selectedTableColumns = model.getSelectedTableColumns();

        if (selectedTableColumns.isEmpty()) {
            tableColumnsChoiceTextArea.append("""
                     ** No statistics have been selected. Please select at least one statistic using the Add Statistic(s) button below. **
                    """);
            return;
        } else if (selectedTableColumns.size() == 1) {
            tableColumnsChoiceTextArea.setText("""
                    The following statistics has been selected. The comparison table will include these statistics as columns in the table.
                    """);
        } else {
            tableColumnsChoiceTextArea.setText("""
                    The following simulations have been selected. The comparison table will include these statistics as columns in the table.
                    """);
        }

        for (int i = 0; i < selectedTableColumns.size(); i++) {
            AlgcomparisonModel.MyTableColumn statistic = selectedTableColumns.get(i);
            tableColumnsChoiceTextArea.append("\n\n" + (i + 1) + ". " + statistic.getColumnName() + " (" + statistic.getDescription() + ")");
        }

        tableColumnsChoiceTextArea.setCaretPosition(0);
    }

    /**
     * Sets the text of the comparisonTextArea based on the selected simulations, algorithms, and statistics. If any of
     * the simulations, algorithms, or statistics are empty, it sets a message indicating that the selection is empty.
     * Otherwise, it sets a message indicating that a comparison has not been run for the selection.
     */
    private void setComparisonText() {
        if (model.getSelectedSimulations().getSimulations().isEmpty() || model.getSelectedAlgorithms().getAlgorithms().isEmpty()
            || model.getSelectedTableColumns().isEmpty()) {
            comparisonTextArea.setText(
                    """
                            ** You have made an empty selection; look back at the Simulation, Algorithm, and TableColumns tabs **
                            """);
        } else {
            comparisonTextArea.setText
                    ("""
                            ** Your selection is non-empty, but you have not yet run a comparison for it **
                            """);
        }
    }

    /**
     * Sets the help text for the tool.
     */
    private void setHelpText() {
        helpChoiceTextArea.setText("""
                This tool may be used to do a comparison of multiple algorithms (in Tetrad for now) for a range of simulations types, statistics, and parameter settings.

                To run a comparison, select one or more simulations, one or more algorithms, and one or more statistics. Then in the Comparison tab, click the "Run Comparison" button.

                The comparison will be displayed in the "comparison" tab.

                Not all combinations you can select in this tool may be stellar ideas; you may need to experiment. One problem is that you may select too many combinations of parameters, and the tool will try every combination of these parameters that is sensible, and perhaps this may take a very long time to do. Or you may, for instance, opt for graphs that have too many variables or are too dense. Or, some of the algorithms may simply take a very long time to run, even for small graphs. We will run your request in a thread with a stop button so you can gracefully exit and try a smaller problem. In fact, it may not make sense to run larger comparisons in this interface at all; you may wish to use the command line tool or Python to do it.

                If you think the problem is that you need more memory, you can increase the memory available to the JVM by starting Tetrad from the command line changing the -Xmx option in at startup. That is, you can start Tetrad with a command like this:
                                
                    java -Xmx4g -jar [tetrad.jar]
                    
                Here, "[tetrad.jar]" should be replaced by the name of the Tetrad jar you have downloaded. This would set the maximum memory available to the JVM to 4 gigabytes. You can increase this number to increase the memory available to the JVM up to the limit of what you have available on your machine. The default is 1 gigabyte.

                In the Simulation tab, simulations may be added by clicking the Add Simulation button. The last one in the list may be removed by clicking the Remove Last Simulation button.

                A simulation selection requires one to select a graph type and a simulation type.

                This selection implies a list of parameters for all of the simulations. These parameters may be edited by clicking the Edit Parameters button. Note that parameters may be given a list of comma-separated values; each combination of parameters will be explored in the comparison.

                The Algorithm tab and TableColumns tab work similarly. An algorithm selection requires one to select an algorithm type and then an independence test and/or a score depending on the requirements of the algorithm.

                For the Algorithm tab, once one has selected all algorithms, one may edit the parameters for these algorithms.

                In the Comparison tab, there is a button to run the comparison and display the results.
                """);
    }

    /**
     * Retrieves the simulation parameter text for displaying the parameter choices
     *
     * @return a string containing the simulation parameter text
     */
    @NotNull
    private String getSimulationParameterText() {
        List<Simulation> simulations = model.getSelectedSimulations().getSimulations();
        Set<String> paramNamesSet = getAllSimulationParameters(simulations);
        StringBuilder paramText;

        if (simulations.size() == 1) {
            paramText = new StringBuilder("\nParameter choices for this simulation:");
        } else {
            paramText = new StringBuilder("\nParameter choices for all simulations:");
        }

        paramText.append(getParameterText(paramNamesSet, model.getParameters()));
        return paramText.toString();
    }

    /**
     * Retrieves the algorithm parameter choices as text.
     *
     * @return The algorithm parameter choices as text.
     */
    private String getAlgorithmParameterText() {
        List<Algorithm> algorithm = model.getSelectedAlgorithms().getAlgorithms();
        Set<String> paramNamesSet = AlgcomparisonModel.getAllAlgorithmParameters(algorithm);
        StringBuilder paramText;

        if (algorithm.size() == 1) {
            paramText = new StringBuilder("\nParameter choices for this algorithm:");
        } else {
            paramText = new StringBuilder("\nParameter choices for all algorithms:");
        }

        paramText.append(getParameterText(paramNamesSet, model.getParameters()));
        return paramText.toString();
    }

    /**
     * This class extends ByteArrayOutputStream and adds buffering and listening functionality. It overrides the write
     * methods to capture the data being written and process it when a newline character is encountered.
     */
    public static class BufferedListeningByteArrayOutputStream extends ByteArrayOutputStream {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void write(int b) {
            // Convert byte to string and add to buffer
            String s = new String(new byte[]{(byte) b}, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if newline character is found
            if (s.contains("\n")) {
                super.write(buffer.toString().getBytes(StandardCharsets.UTF_8), 0, buffer.length());
                buffer.setLength(0); // Clear the buffer for next data
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // Convert byte array to string and add to buffer
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if newline character is found
            if (s.contains("\n")) {
                super.write(buffer.toString().getBytes(StandardCharsets.UTF_8), 0, buffer.length());
                buffer.setLength(0); // Clear the buffer for next data
            }

        }

    }

    /**
     * A table model for the results of the IDA check. This table can be sorted by clicking on the column headers, up or
     * down. The table can be copied and pasted into a text file or into Excel.
     */
    private static class TableColumnSelectionModel extends AbstractTableModel {

        /**
         * The column names for the table. The first column is the pair of nodes, the second column is the minimum total
         * effect, the third column is the maximum total effect, the fourth column is the minimum absolute total effect,
         * the fifth column is the true total effect, and the sixth column is the squared distance from the true total
         * effect, where if the true total effect falls between the minimum and maximum total effect zero is reported.
         * If the true model is not given, the last two columns are not included.
         */
        private final String[] columnNames = {"Index", "Column Name", "Description", "Selected"};
        /**
         * The data for the table.
         */
        private final Object[][] data;
        private final List<AlgcomparisonModel.MyTableColumn> allTableColumns;
        private final Set<AlgcomparisonModel.MyTableColumn> selectedTableColumns;
        private JTable tableRef;

        /**
         * Constructs a new table estModel for the results of the IDA check.
         */
        public TableColumnSelectionModel(List<AlgcomparisonModel.MyTableColumn> allTableColumns, Set<AlgcomparisonModel.MyTableColumn> selectedTableColumns) {
            if (allTableColumns == null) {
                throw new IllegalArgumentException("allTableColumns is null");
            }

            if (selectedTableColumns == null) {
                throw new IllegalArgumentException("selectedTableColumns is null");
            }

            if (!new HashSet<>(allTableColumns).containsAll(selectedTableColumns)) {
                throw new IllegalArgumentException("selectedTableColumns contains elements not in allTableColumns");
            }

            // Create the data for the table
            this.data = new Object[allTableColumns.size()][3];
            this.allTableColumns = allTableColumns;
            this.selectedTableColumns = new HashSet<>(selectedTableColumns);

            for (int i = 0; i < allTableColumns.size(); i++) {
                AlgcomparisonModel.MyTableColumn tableColumn = allTableColumns.get(i);
                this.data[i][0] = Integer.toString(i + 1); // 1-based index (not 0-based index)
                this.data[i][1] = tableColumn.getColumnName();
                this.data[i][2] = tableColumn.getDescription();
            }
        }

        /**
         * Returns the number of rows in the table.
         *
         * @return the number of rows in the table.
         */
        @Override
        public int getRowCount() {
            return allTableColumns.size();
        }

        /**
         * Returns the number of columns in the table.
         *
         * @return the number of columns in the table.
         */
        @Override
        public int getColumnCount() {
            return 4;
        }

        /**
         * Returns the name of the column at the given index.
         *
         * @param col the index of the column.
         * @return the name of the column at the given index.
         */
        @Override
        public String getColumnName(int col) {
            return this.columnNames[col];
        }

        /**
         * Returns the value at the given row and column.
         *
         * @param row the row.
         * @param col the column.
         * @return the value at the given row and column.
         */
        @Override
        public Object getValueAt(int row, int col) {
            int index = tableRef == null ? row : tableRef.convertRowIndexToView(row);

            if (index == -1) {
                index = row;
            }

            if (col == 3) {
                if (tableRef == null || tableRef.getSelectionModel() == null) {
                    return "";
                }

                boolean rowSelected = tableRef.getSelectionModel().isSelectedIndex(index);

                if (rowSelected) {
                    selectedTableColumns.add(allTableColumns.get(index));
                } else {
                    selectedTableColumns.remove(allTableColumns.get(index));
                }

                return rowSelected ? "Selected" : "";
            } else {
                return this.data[index][col];
            }
        }

        /**
         * Returns the class of the column at the given index.
         *
         * @param c the index of the column.
         * @return the class of the column at the given index.
         */
        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        public Set<AlgcomparisonModel.MyTableColumn> getSelectedTableColumns() {
            return selectedTableColumns;
        }

        public void setTableRef(JTable tableRef) {
            this.tableRef = tableRef;
        }

        public AlgcomparisonModel.MyTableColumn getMyTableColumn(int row) {
            return allTableColumns.get(row);
        }

        public void selectRow(int row) {
            tableRef.getSelectionModel().addSelectionInterval(row, row);
        }
    }
}
