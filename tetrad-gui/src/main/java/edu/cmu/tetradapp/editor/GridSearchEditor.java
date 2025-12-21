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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.model.GridSearchModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.*;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
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
import java.awt.event.ActionEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.cmu.tetradapp.model.GridSearchModel.getAllSimulationParameters;

/**
 * The AlgcomparisonEditor class represents a JPanel that contains different tabs for simulation, algorithm, table
 * columns, comparison, and help. It is used for editing a GridSearchModel.
 * <p>
 * The reference is here:
 * <p>
 * Ramsey, J. D., Malinsky, D., &amp; Bui, K. V. (2020). Algcomparison: Comparing the performance of graphical structure
 * learning algorithms with tetrad. Journal of Machine Learning Research, 21(238), 1-6.
 *
 * @author josephramsey
 */
public class GridSearchEditor extends JPanel {
    /**
     * JLabel representing a message indicating that there are no parameters to edit.
     */
//    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    private static final String NO_PARAM_TEXT = "No parameters to edit";
    /**
     * A JComboBox that holds instances of IndependenceTestModel.
     */
    private static JComboBox<IndependenceTestModel> indTestComboBox;
    /**
     * ComboBox for selecting a ScoreModel.
     */
    private static JComboBox<ScoreModel> scoreModelComboBox;
    /**
     * The GridSearchModel class represents a model used in an algorithm comparison application. It contains methods and
     * properties related to the comparison of algorithms.
     */
    private final GridSearchModel model;
    /**
     * JTextArea used for displaying comparison results.
     */
    private transient final JTextArea comparisonTextArea;
    /**
     * JTextArea used for displaying verbose output.
     */
    private transient JTextArea verboseOutputTextArea;
    /**
     * JTextArea used for displaying simulation choice information.
     */
    private transient JTextArea simulationChoiceTextArea;
    /**
     * The TextArea component used for displaying algorithm choices.
     */
    private transient JTextArea algorithmChoiceTextArea;
    /**
     * JTextArea used for displaying table column choices.
     */
    private transient JTextArea tableColumnsChoiceTextArea;
    /**
     * JTextArea used for displaying help choice information.
     */
    private transient JTextArea helpChoiceTextArea;
    /**
     * Button used to add a simulation.
     */
    private transient JButton selectSimulation;
    /**
     * Button used to add an algorithm.
     */
//    private transient JButton addAlgorithm;
    // class fields
    private JButton addAlgorithm;
    private JButton removeLastAlgorithm;
    private JButton editAlgorithmParameters;

    /**
     * Button used to add table columns.
     */
    private transient JButton addTableColumns;
    /**
     * Represents a drop-down menu for selecting an algorithm.
     */
    private transient JComboBox<Object> algorithmDropdown;
    /**
     * Private variable representing a JScrollPane used for comparing variables.
     */
    private transient JScrollPane comparisonScroll;
    /**
     * The comparisonTabbedPane represents a tabbed pane component in the user interface for displaying comparison
     * related data and functionality.
     * <p>
     * It is a private instance variable of type JTabbedPane.
     */
    private transient JTabbedPane comparisonTabbedPane;

    /**
     * Initializes an instance of AlgcomparisonEditor, which is a JPanel containing a JTabbedPane that displays
     * different tabs for simulation, algorithm, table columns, comparison and help.
     *
     * @param model the GridSearchModel to use for the editor
     */
    public GridSearchEditor(GridSearchModel model) {
        this.model = model;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
        tabbedPane.setPreferredSize(new Dimension(800, 400));

        comparisonTextArea = new JTextArea();

        addSimulationTab(tabbedPane);
        addAlgorithmTab(tabbedPane);
        addTableColumnsTab(tabbedPane);
        addComparisonTab(tabbedPane);
//        addXmlTab(tabbedPane); // todo work on this later.
        addHelpTab(tabbedPane);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        model.getParameters().set("algcomparisonSaveData", model.getParameters().getBoolean("algcomparisonSaveData", true));
        model.getParameters().set("algcomparisonSaveGraphs", model.getParameters().getBoolean("algcomparisonSaveGraphs", true));
        model.getParameters().set("algcomparisonSaveCPDAGs", model.getParameters().getBoolean("algcomparisonSaveCPDAGs", false));
        model.getParameters().set("algcomparisonSavePAGs", model.getParameters().getBoolean("algcomparisonSavePAGs", false));
        model.getParameters().set("algcomparisonSortByUtility", model.getParameters().getBoolean("algcomparisonSortByUtility", false));
        model.getParameters().set("algcomparisonShowUtilities", model.getParameters().getBoolean("algcomparisonShowUtilities", false));
        model.getParameters().set("algcomparisonSetAlgorithmKnowledge", model.getParameters().getBoolean("algcomparisonSetAlgorithmKnowledge", true));
        model.getParameters().set("algcomparisonParallelism", model.getParameters().getInt("algcomparisonParallelism", Runtime.getRuntime().availableProcessors()));
        model.getParameters().set("algcomparisonGraphType", model.getParameters().getString("algcomparisonGraphType", "DAG"));
        // Markov checker fact-set selection (for independence implications).
        // Canonical key used by Grid Search evaluation.
        model.getParameters().set(
                "markovCheckerFactMode",
                model.getParameters().getString("markovCheckerFactMode", "ORDERED_LOCAL_MARKOV")
        );
        comparisonTextArea.setText(model.getLastComparisonText());
        verboseOutputTextArea.setText(model.getLastVerboseOutputText());

        SwingUtilities.invokeLater(() -> {
            try {
                scrollToWord(comparisonTextArea, comparisonScroll, "AVERAGE VALUE");
            } catch (BadLocationException ex) {
                System.out.println("Scrolling operation failed.");
            }
        });

        addAddAlgorithmListener();
        addRemoveLastAlgorithmListener();
        addEditAlgorithmParametersListener();
    }

    /**
     * Creates a map of parameter components for the given set of parameters and a Parameters object.
     *
     * @param params     the set of parameter names
     * @param parameters the Parameters object containing the parameter values
     * @return a map of parameter names to corresponding Box components
     */
    public static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters, boolean listOptionAllowed, boolean bothOptionAllowed) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream().collect(Collectors.toMap(Function.identity(), e -> createParameterComponent(e, parameters, paramDescriptions.get(e), listOptionAllowed, bothOptionAllowed), (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s.", u));
        }, TreeMap::new));
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

        return Stream.concat(otherComps.stream(), boolComps.stream()).toArray(Box[]::new);
    }

    /**
     * Creates a component for a specific parameter based on its type and default value.
     *
     * @param parameter  the name of the parameter
     * @param parameters the Parameters object containing the parameter values
     * @param paramDesc  the ParamDescription object containing information about the parameter
     * @return a Box component representing the parameter
     */
    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc, boolean listOptionAllowed, boolean bothOptionAllowed) {
        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();

        Object[] defaultValues = parameters.getValues(parameter);

        switch (defaultValue) {
            case Double v -> {
                double lowerBoundDouble = paramDesc.getLowerBoundDouble();
                double upperBoundDouble = paramDesc.getUpperBoundDouble();
                Double[] defValues = new Double[defaultValues.length];
                for (int i = 0; i < defaultValues.length; i++) {
                    if (defaultValues[i] instanceof Number) {
                        defValues[i] = ((Number) defaultValues[i]).doubleValue();
                    } else {
                        throw new IllegalArgumentException("Unexpected type: " + defaultValues[i].getClass());
                    }
                }

                if (listOptionAllowed) {
                    component = getListDoubleTextField(parameter, parameters, defValues, lowerBoundDouble, upperBoundDouble);
                } else {
                    component = getDoubleTextField(parameter, parameters, v, lowerBoundDouble, upperBoundDouble);
                }
            }
            case Integer integer -> {
                int lowerBoundInt = paramDesc.getLowerBoundInt();
                int upperBoundInt = paramDesc.getUpperBoundInt();
                Integer[] defValues = new Integer[defaultValues.length];
                for (int i = 0; i < defaultValues.length; i++) {
                    try {
                        defValues[i] = (int) defaultValues[i];
                    } catch (Exception e) {
                        throw new RuntimeException("Parameter " + parameter + " has a default value that is not an integer: " + defaultValues[i]);
                    }
                }

                if (listOptionAllowed) {
                    component = getListIntTextField(parameter, parameters, defValues, lowerBoundInt, upperBoundInt);
                } else {
                    component = getIntTextField(parameter, parameters, integer, lowerBoundInt, upperBoundInt);
                }
            }
            case Long l -> {
                long lowerBoundLong = paramDesc.getLowerBoundLong();
                long upperBoundLong = paramDesc.getUpperBoundLong();
                Long[] defValues = new Long[defaultValues.length];
                for (int i = 0; i < defaultValues.length; i++) {
                    try {
                        defValues[i] = (Long) defaultValues[i];
                    } catch (Exception e) {
                        throw new RuntimeException("Parameter " + parameter + " has a default value that is not a long: " + defaultValues[i]);
                    }
                }
                if (listOptionAllowed) {
                    component = getListLongTextField(parameter, parameters, defValues, lowerBoundLong, upperBoundLong);
                } else {
                    component = getLongTextField(parameter, parameters, l, lowerBoundLong, upperBoundLong);
                }
            }
            case Boolean ignored -> component = getBooleanSelectionBox(parameter, parameters, bothOptionAllowed);
            case String s -> component = createStringField(parameter, parameters, s);
            default -> throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
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
     * Returns a customized DoubleTextField with specified parameters.
     *
     * @param parameter    the name of the parameter to be set in the Parameters object
     * @param parameters   the Parameters object to store the parameter values
     * @param defaultValue the default value to set in the DoubleTextField
     * @param lowerBound   the lowerbound limit for valid input values in the DoubleTextField
     * @param upperBound   the upperbound limit for valid input values in the DoubleTextField
     * @return a DoubleTextField with the specified parameters
     */
    public static DoubleTextField getDoubleTextField(String parameter, Parameters parameters, double defaultValue, double lowerBound, double upperBound) {
        DoubleTextField field = new DoubleTextField(defaultValue, 8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((value, oldValues) -> {
            if (Double.isNaN(value)) {
                return oldValues;
            }

            if (value < lowerBound) {
                return oldValues;
            }

            if (value > upperBound) {
                return oldValues;
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
     * Creates a ListDoubleTextField component with the given parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListDoubleTextField component with the specified parameters
     */
    public static ListDoubleTextField getListDoubleTextField(String parameter, Parameters parameters, Double[] defaultValues, double lowerBound, double upperBound) {
        ListDoubleTextField field = new ListDoubleTextField(defaultValues, 8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

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
    public static ListIntTextField getListIntTextField(String parameter, Parameters parameters, Integer[] defaultValues, double lowerBound, double upperBound) {
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

    /**
     * Returns a LongTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter to set in the Parameters object.
     * @param parameters   The Parameters object to set the parameter in.
     * @param defaultValue The default value to use for the LongTextField.
     * @param lowerBound   The lower bound for the LongTextField value.
     * @param upperBound   The upper bound for the LongTextField value.
     * @return A LongTextField object with the specified parameters.
     */
    public static LongTextField getLongTextField(String parameter, Parameters parameters, long defaultValue, long lowerBound, long upperBound) {
        LongTextField field = new LongTextField(defaultValue, 8);

        field.setFilter((value, oldValue) -> {
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

    public static ListLongTextField getListLongTextField(String parameter, Parameters parameters, Long[] defaultValues, long lowerBound, long upperBound) {
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

    /**
     * Returns a Box component representing a boolean selection box.
     *
     * @param parameter         the name of the parameter
     * @param parameters        the Parameters object containing the parameter values
     * @param bothOptionAllowed whether the option allows one to select both true and false
     * @return a Box component representing the boolean selection box
     */
    public static Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean bothOptionAllowed) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        JRadioButton bothButton = null;

        if (bothOptionAllowed) {
            bothButton = new JRadioButton("Both");
        }

        // Button group to ensure only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);

        if (bothOptionAllowed) {
            selectionBtnGrp.add(bothButton);
        }

        Object[] values = parameters.getValues(parameter);
        Boolean[] booleans = new Boolean[values.length];

        try {
            for (int i = 0; i < values.length; i++) {
                try {
                    booleans[i] = (Boolean) values[i];
                } catch (Exception e) {
                    throw new RuntimeException("Parameter " + parameter + " has a value that is not a boolean: " + values[i]);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Set default selection
        if (booleans.length == 1 && booleans[0]) {
            yesButton.setSelected(true);
        } else if (booleans.length == 1) {
            noButton.setSelected(true);
        } else if (booleans.length == 2 && bothOptionAllowed) {
            bothButton.setSelected(true);
        }

        // Add to the containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        if (bothOptionAllowed) {
            selectionBox.add(bothButton);
        }

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                Object[] objects = new Object[1];
                objects[0] = Boolean.TRUE;
                parameters.set(parameter, objects);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                Object[] objects = new Object[1];
                objects[0] = Boolean.FALSE;
                parameters.set(parameter, objects);
            }
        });

        if (bothOptionAllowed) {
            bothButton.addActionListener((e) -> {
                JRadioButton button = (JRadioButton) e.getSource();
                if (button.isSelected()) {
                    Object[] objects = new Object[2];
                    objects[0] = Boolean.TRUE;
                    objects[1] = Boolean.FALSE;
                    parameters.set(parameter, objects);
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
        NumberFormat nf = NumberFormat.getInstance();

        for (String name : paramNames) {
            ParamDescription description = paramDescriptions.get(name);
            Object[] values = parameters.getValues(name);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                switch (values[i]) {
                    case Double ignored -> sb.append(nf.format((double) values[i]));
                    case Integer ignored -> sb.append((int) values[i]);
                    case Long ignored -> sb.append((long) values[i]);
                    case null, default -> sb.append(values[i]);
                }
            }

            paramText.append("\n\n- ").append(name).append(" = ").append(sb);
            paramText.append("\n").append(description.getShortDescription());
            paramText.append(". ").append(description.getLongDescription());
        }

        return paramText.toString();
    }

    public static void scrollToWord(JTextArea textArea, JScrollPane scrollPane, String word) throws BadLocationException {
        String text = textArea.getText();
        int pos = text.indexOf(word);
        if (pos >= 0) {
            Rectangle viewRect = textArea.modelToView2D(pos).getBounds();
            if (viewRect != null) {
                JViewport viewport = scrollPane.getViewport();
                viewRect = new Rectangle(viewRect.x, viewRect.y, viewport.getWidth() - 10, viewport.getHeight() - 10);
                textArea.scrollRectToVisible(viewRect);
            }
        }
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
        switch (defaultValue) {
            case Double v -> {
                double lowerBoundDouble = paramDesc.getLowerBoundDouble();
                double upperBoundDouble = paramDesc.getUpperBoundDouble();
                component = getDoubleField(parameter, parameters, v, lowerBoundDouble, upperBoundDouble);
            }
            case Integer i -> {
                int lowerBoundInt = paramDesc.getLowerBoundInt();
                int upperBoundInt = paramDesc.getUpperBoundInt();
                component = getIntTextField(parameter, parameters, i, lowerBoundInt, upperBoundInt);
            }
            case Long l -> {
                long lowerBoundLong = paramDesc.getLowerBoundLong();
                long upperBoundLong = paramDesc.getUpperBoundLong();
                component = createLongTextField(parameter, parameters, l, lowerBoundLong, upperBoundLong);
            }
            case Boolean b -> component = createBooleanSelectionBox(parameter, parameters, b);
            case String s -> component = getStringField(parameter, parameters, s);
            default -> throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
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
                                                int defaultValue, int lowerBound, int upperBound) {
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
    private static LongTextField createLongTextField(String parameter, Parameters parameters,
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
    private static Box createBooleanSelectionBox(String parameter, Parameters parameters, boolean defaultValue) {
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

        // Add to the containing box
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
    private static StringTextField createStringField(String parameter, Parameters parameters, String defaultValue) {
        return PathsAction.getStringField(parameter, parameters, defaultValue);
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static @NotNull List<Integer> getIntegers(File resultsDir, int selectedAlgorithm, int simulation) {
        File dir = new File(resultsDir, simulation + "." + selectedAlgorithm);

        List<Integer> indices = new ArrayList<>();

        if (dir.exists()) {
            File[] graphs = dir.listFiles();

            if (graphs != null) {
                for (File graph : graphs) {
                    String name = graph.getName();

                    if (!name.startsWith("graph.")) {
                        continue;
                    }

                    String[] parts = name.split("\\.");

                    int graphIndex;

                    try {
                        graphIndex = Integer.parseInt(parts[1]);

                        if (!indices.contains(graphIndex)) {
                            indices.add(graphIndex);
                        }
                    } catch (NumberFormatException e) {
                        // These aren't directories/files written out by the tool.
                    }
                }
            }
        }
        return indices;
    }

    private static @NotNull List<Integer> getIntegers(File resultsDir) {
        List<Integer> simulationIndices = new ArrayList<>();

        if (resultsDir.exists()) {
            File[] dirs = resultsDir.listFiles();

            if (dirs != null) {

                // The dirs array should contain directories for each simulation/algorithm combination. These
                // are formatted as, e.g., "5.2" for simulation5 and algorithm 2. We need to iterate through
                // all of these directories and find the highest simulation number and the highest
                // algorithm number. The number of graphs will be determined once we have the simulation and
                // algorithm numbers. These are listed as "graph1.txt", "graph2.txt", etc., in each of these
                // directories.
                for (File dir : dirs) {
                    String name = dir.getName();
                    String[] parts = name.split("\\.");

                    int simulation;

                    try {
                        simulation = Integer.parseInt(parts[0]);

                        if (!simulationIndices.contains(simulation)) {
                            simulationIndices.add(simulation);
                        }
                    } catch (NumberFormatException e) {
                        // These aren't directories/files written out by the tool.
                    }
                }
            }
        }
        return simulationIndices;
    }

    private static String safe(String s) {
        return s == null ? "(no description)" : s;
    }

    private static String describeTest(GridSearchModel.AlgorithmSpec spec) {
        return describeAnnotated(spec.getTest(), "(no test)");
    }

    private static String describeScore(GridSearchModel.AlgorithmSpec spec) {
        return describeAnnotated(spec.getScore(), "(no score)");
    }

    private static String describeAnnotated(edu.cmu.tetrad.annotation.AnnotatedClass<?> ac, String noneLabel) {
        if (ac == null) return noneLabel;

        // Try to use annotation.name() if present (common in Tetrad annotations).
        Object ann = ac.annotation();
        if (ann != null) {
            String name = reflectStringNoThrow(ann, "name");
            if (name != null && !name.isBlank()) return name;

            // Some annotations might use "value" instead of "name"
            String value = reflectStringNoThrow(ann, "value");
            if (value != null && !value.isBlank()) return value;
        }

        // Fallback: class simple name
        Class<?> c = ac.clazz();
        return (c != null) ? c.getSimpleName() : "(unknown)";
    }

    private static String reflectStringNoThrow(Object target, String methodName) {
        try {
            var m = target.getClass().getMethod(methodName);
            Object r = m.invoke(target);
            return (r instanceof String) ? (String) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Updates the indices in the graph index combo box based on the selected simulation and algorithm.
     *
     * @param simulationComboBox The combo box that contains the available simulation options.
     * @param algorithmComboBox  The combo box that contains the available algorithm options.
     * @param graphIndexComboBox The combo box to update with the graph indices.
     * @param resultsDir         The directory where the graph results are stored.
     */
    private void updateAlgorithmBoxIndices(JComboBox<Integer> simulationComboBox,
                                           JComboBox<Integer> algorithmComboBox,
                                           JComboBox<Integer> graphIndexComboBox,
                                           File resultsDir) {

        Integer savedAlgorithm = model.getSelectedAlgorithm();//  (Integer) algorithmComboBox.getSelectedItem(); // <— use combo
        Integer savedGraphIndex = model.getSelectedGraphIndex();
        Object selectedSimulation =  simulationComboBox.getSelectedItem();


        if (selectedSimulation == null) {
            algorithmComboBox.removeAllItems();
            graphIndexComboBox.removeAllItems();
            return;
        }

        List<Integer> algorithmIndices = new ArrayList<>();

        if (resultsDir.exists()) {
            File[] dirs = resultsDir.listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    String name = dir.getName();
                    String[] parts = name.split("\\.");
                    try {
                        int simulation = Integer.parseInt(parts[0]);
                        int algorithm = Integer.parseInt(parts[1]);

                        if (simulation == (int) selectedSimulation && !algorithmIndices.contains(algorithm)) {
                            algorithmIndices.add(algorithm);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        algorithmComboBox.removeAllItems();
        Collections.sort(algorithmIndices);
        for (int i : algorithmIndices) algorithmComboBox.addItem(i);

        // restore if still available, else default to first
        if (savedAlgorithm != null && algorithmIndices.contains(savedAlgorithm)) {
            algorithmComboBox.setSelectedItem(savedAlgorithm);
            graphIndexComboBox.setSelectedItem(savedGraphIndex);
        } else if (!algorithmIndices.isEmpty()) {
            algorithmComboBox.setSelectedIndex(0);
            graphIndexComboBox.setSelectedIndex(0);
        }

        updateGraphBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
    }

//    private JComponent buildAlgorithmSelectionControls() {
//        Box box = Box.createHorizontalBox();
//        box.add(Box.createHorizontalGlue());
//
//        addAlgorithm = new JButton("Add Algorithm");
//        addAddAlgorithmListener();
//
//        removeLastAlgorithm = new JButton("Remove Last Algorithm");
//
//        removeLastAlgorithm.addActionListener(e -> {
//            model.removeLastAlgorithm();
//            onSelectedAlgorithmsChanged();
//        });
//
//        editAlgorithmParameters = new JButton("Edit Parameters");
//        editAlgorithmParameters.addActionListener(e -> openEditAlgorithmParametersDialog());
//
//        box.add(addAlgorithm);
//        box.add(removeLastAlgorithm);
//        box.add(editAlgorithmParameters);
//        box.add(Box.createHorizontalGlue());
//        return box;
//    }

    private void openEditAlgorithmParametersDialog() {
        List<GridSearchModel.AlgorithmSpec> selected = model.getSelectedAlgorithms();

        if (selected == null || selected.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No algorithms have been selected.\n\nUse 'Add Algorithm' first.",
                    "Edit Algorithm Parameters",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        Set<String> algParams = GridSearchModel.getAllAlgorithmParameters(selected);
        Set<String> testParams = GridSearchModel.getAllTestParameters(selected);
        Set<String> scoreParams = GridSearchModel.getAllScoreParameters(selected);
        Set<String> bootParams = GridSearchModel.getAllBootstrapParameters(selected);

        Set<String> all = new LinkedHashSet<>();
        all.addAll(algParams);
        all.addAll(testParams);
        all.addAll(scoreParams);
        all.addAll(bootParams);

        JComponent center;
        if (all.isEmpty()) {
            JLabel lbl = new JLabel(NO_PARAM_TEXT);
            lbl.setBorder(new EmptyBorder(10, 10, 10, 10));
            center = new PaddingPanel(lbl);
        } else {
            Box parameterBox = getParameterBox(all, true, true);
            center = new PaddingPanel(parameterBox);
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Edit Algorithm Parameters",
                Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("Set parameter values (comma-separated lists will be grid-searched where applicable).");
        label.setBorder(new EmptyBorder(10, 10, 10, 10));

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton done = new JButton("Done");
        done.addActionListener(ev -> {
            dialog.dispose();
            onSelectedAlgorithmsChanged();
        });
        buttonPanel.add(done);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

//    /**
//     * Adds a tab if params is non-empty.
//     *
//     * @return the tab index added, or -1 if not added.
//     */
//    private int addParamTabIfAny(JTabbedPane tabs, String title, Set<String> params,
//                                 boolean listOptionAllowed, boolean bothOptionAllowed) {
//        if (params == null || params.isEmpty()) return -1;
//        int index = tabs.getTabCount();
//        tabs.addTab(title, new PaddingPanel(getParameterBox(params, listOptionAllowed, bothOptionAllowed)));
//        return index;
//    }

    private void onSelectedAlgorithmsChanged() {
        setAlgorithmText();
        setComparisonText();
    }

    /**
     * Updates the indices in the graph index combo box based on the selected simulation and algorithm.
     *
     * @param simulationComboBox The combo box that contains the available simulation options.
     * @param algorithmComboBox  The combo box that contains the available algorithm options.
     * @param graphIndexComboBox The combo box to update with the graph indices.
     * @param resultsDir         The directory where the graph results are stored.
     */
    private void updateGraphBoxIndices(JComboBox<Integer> simulationComboBox, JComboBox<Integer> algorithmComboBox, JComboBox<Integer> graphIndexComboBox, File resultsDir) {
        int savedGraphIndex = model.getSelectedGraphIndex();

        Object selectedSimulation = simulationComboBox.getSelectedItem();
        Object selectedAlgorithm = algorithmComboBox.getSelectedItem();

        if (selectedSimulation == null || selectedAlgorithm == null) {
            graphIndexComboBox.removeAllItems();
            return;
        }

        int simulation = (int) selectedSimulation;
        List<Integer> indices = getIntegers(resultsDir, (int) selectedAlgorithm, simulation);

        graphIndexComboBox.removeAllItems();
        Collections.sort(indices);

        for (int i : indices) {
            graphIndexComboBox.addItem(i);
        }

        if (savedGraphIndex > 0) {
            graphIndexComboBox.setSelectedItem(savedGraphIndex);
        }
    }

    private void updateSelectedGraph(JComboBox<Integer> simulationComboBox, JComboBox<Integer> algorithmComboBox, JComboBox<Integer> graphIndexComboBox, File resultsDir, GraphWorkbench workbench) {
        Object selectedSimulation = simulationComboBox.getSelectedItem();
        Object selectedAlgorithm = algorithmComboBox.getSelectedItem();
        Object selectedGraphIndex = graphIndexComboBox.getSelectedItem();

        if (selectedSimulation == null || selectedAlgorithm == null || selectedGraphIndex == null) {
            return;
        }

        File dir = new File(resultsDir, selectedSimulation + "." + selectedAlgorithm);
        File graphFile = new File(dir, "graph." + selectedGraphIndex + ".txt");

        if (graphFile.exists()) {
            Graph graph = GraphSaveLoadUtils.loadGraphTxt(graphFile);
            LayoutUtil.defaultLayout(graph);
            workbench.setGraph(graph);
            model.setSelectedGraph(graph);

            model.setSelectedSimulation((int) selectedSimulation);
            model.setSelectedAlgorithm((int) selectedAlgorithm);
            model.setSelectedGraphIndex((int) selectedGraphIndex);

            firePropertyChange("modelChanged", null, null);
        }
    }

    private void refreshGraphSelectionContent(JTabbedPane tabbedPane) {
        Box tab = (Box) tabbedPane.getComponentAt(4);
        tab.removeAll();
        tab.add(getGraphSelectorBox());
        tab.revalidate();
        tab.repaint();
    }

    @NotNull
    private Class<? extends RandomGraph> getGraphClazz(String graphString) {
        List<String> graphTypeStrings = new ArrayList<>(Arrays.asList(ParameterTab.GRAPH_TYPE_ITEMS));

        if (model.getSuppliedGraph() != null) {
            graphTypeStrings.add("User Supplied Graph");
        }

        return switch (graphTypeStrings.indexOf(graphString)) {
            case 0 -> RandomForward.class;
            case 1 -> ErdosRenyi.class;
            case 2 -> ScaleFree.class;
            case 3 -> Cyclic.class;
            case 4 -> RandomSingleFactorMim.class;
            case 5 -> RandomTwoFactorMim.class;
            case 6 -> SingleGraph.class;
            default -> throw new IllegalArgumentException("Unexpected value: " + graphString);
        };
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

        selectSimulation = new JButton("Select Simulation");
        addSelectSimulationListener();

        JButton editSimulationParameters = getJButton();

        simulationSelectionBox.add(selectSimulation);
        simulationSelectionBox.add(editSimulationParameters);
        simulationSelectionBox.add(Box.createHorizontalGlue());

        if (model.getSuppliedData() == null) {
            simulationChoice.add(simulationSelectionBox, BorderLayout.SOUTH);
        }

        tabbedPane.addTab(model.getSuppliedData() != null ? "Data" : "Simulations", simulationChoice);
    }

    private @NotNull JButton getJButton() {
        JButton editSimulationParameters = new JButton("Edit Parameters");

        editSimulationParameters.addActionListener(e -> {
            List<Simulation> simulations = model.getSelectedSimulations().getSimulations();
            Set<String> params = getAllSimulationParameters(simulations);

            Box parameterBox = getParameterBox(params, true, false);
            new PaddingPanel(parameterBox);

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Simulation Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = betButtonPanel(dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
        return editSimulationParameters;
    }

    @NotNull
    private JPanel betButtonPanel(JDialog dialog) {
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
        return buttonPanel;
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
        onSelectedAlgorithmsChanged();

        Box algorithSelectionBox = Box.createHorizontalBox();
        algorithSelectionBox.add(Box.createHorizontalGlue());

        addAlgorithm = new JButton("Add Algorithm");
        addAddAlgorithmListener();

        removeLastAlgorithm = new JButton("Remove Last Algorithm");
        removeLastAlgorithm.addActionListener(e -> {
            model.removeLastAlgorithm();
            onSelectedAlgorithmsChanged();
        });

        editAlgorithmParameters = new JButton("Edit Parameters");

        editAlgorithmParameters.addActionListener(e -> openEditAlgorithmParametersDialog());

        algorithSelectionBox.add(addAlgorithm);
        algorithSelectionBox.add(removeLastAlgorithm);
        algorithSelectionBox.add(editAlgorithmParameters);
        algorithSelectionBox.add(Box.createHorizontalGlue());

        JPanel algorithmChoice = new JPanel();
        algorithmChoice.setLayout(new BorderLayout());
        algorithmChoice.add(new JScrollPane(algorithmChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), BorderLayout.CENTER);
        algorithmChoice.add(algorithSelectionBox, BorderLayout.SOUTH);

        // inside addAlgorithmTab(...)
        addAlgorithm = new JButton("Add Algorithm");
        removeLastAlgorithm = new JButton("Remove Last Algorithm");
        editAlgorithmParameters = new JButton("Edit Parameters");

        tabbedPane.addTab("Algorithms", algorithmChoice);
    }

    @NotNull
    private Box getParameterBox(Set<String> params, boolean listOptionAllowed, boolean bothOptionAllowed) {
        Box parameterBox = Box.createVerticalBox();
        parameterBox.removeAll();

        if (params.isEmpty()) {
            JLabel noParamLbl = new JLabel(NO_PARAM_TEXT);
            noParamLbl.setBorder(new EmptyBorder(10, 10, 10, 10));
            parameterBox.add(noParamLbl, BorderLayout.NORTH);
        } else {
            Box parameters = Box.createVerticalBox();
            Box[] paramBoxes = ParameterComponents.toArray(createParameterComponents(params, model.getParameters(), listOptionAllowed, bothOptionAllowed));
            int lastIndex = paramBoxes.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                parameters.add(paramBoxes[i]);
                parameters.add(Box.createVerticalStrut(10));
            }
            parameters.add(paramBoxes[lastIndex]);

            Box horiz = Box.createHorizontalBox();

            if (listOptionAllowed) {
                horiz.add(new JLabel("Please type comma-separated lists of values, thus: 10, 100, 1000"));
            } else {
                horiz.add(new JLabel("Please type a single value."));
            }

            horiz.add(Box.createHorizontalGlue());
            horiz.setBorder(new EmptyBorder(0, 0, 10, 0));
            parameterBox.add(horiz, BorderLayout.NORTH);
            parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
            parameterBox.setBorder(new EmptyBorder(10, 10, 10, 10));
            parameterBox.setPreferredSize(new Dimension(800, 400));
        }
        return parameterBox;
    }

    /**
     * Adds a table columns tab to the provided JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to add the table columns tab to
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
        addAddTableColumnsListener(tabbedPane);

        JButton removeLastTableColumn = new JButton("Remove Last Column");
        removeLastTableColumn.addActionListener(e -> {
            model.removeLastTableColumn();
            setTableColumnsText();
            setComparisonText();
        });

        tableColumnsSelectionBox.add(addTableColumns);
        tableColumnsSelectionBox.add(removeLastTableColumn);
//        tableColumnsSelectionBox.add(createEditutilitiesButton());
        tableColumnsSelectionBox.add(Box.createHorizontalGlue());

        JPanel tableColumnsChoice = new JPanel();
        tableColumnsChoice.setLayout(new BorderLayout());
        tableColumnsChoice.add(new JScrollPane(tableColumnsChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        tableColumnsChoice.add(tableColumnsSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Table Columns", tableColumnsChoice);
    }

    private @NotNull JButton createEditutilitiesButton() {
        JButton editUtilities = new JButton("Edit Utilities");
        editUtilities.addActionListener(e -> {
            List<GridSearchModel.MyTableColumn> columns = model.getSelectedTableColumns();
            Set<String> params = new HashSet<>();
            for (GridSearchModel.MyTableColumn column : columns) {
                params.add("algcomparison." + column.getColumnName());
                Double weight = model.getParameters().getDouble("algcomparison." + column.getColumnName());

                Parameters.serializableInstance().remove("algcomparison." + column.getColumnName());

                ParamDescriptions.getInstance().put("algcomparison." + column.getColumnName(), new ParamDescription("algcomparison." + column.getColumnName(), "Utility for " + column.getColumnName() + " in [0, 1]", "Utility for " + column.getColumnName(), weight, 0.0, 1.0));
            }

            Box parameterBox = getParameterBox(params, false, false);
            new PaddingPanel(parameterBox);

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(GridSearchEditor.this), "Edit Utilities", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            JLabel label = new JLabel("To sort comparison tables by utility please adjust parameters in Comparison.");
            label.setBorder(new EmptyBorder(10, 10, 10, 10));

            dialog.add(label, BorderLayout.NORTH);

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = betButtonPanel(dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(GridSearchEditor.this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
        return editUtilities;
    }

    /**
     * Adds a comparison tab to the given JTabbedPane.
     *
     * @param tabbedPane the JTabbedPane to add the comparison tab to
     */
    private void addComparisonTab(JTabbedPane tabbedPane) {
        comparisonTextArea.setLineWrap(false);
        comparisonTextArea.setWrapStyleWord(false);
        comparisonTextArea.setEditable(false);
        comparisonTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        verboseOutputTextArea = new JTextArea();
        verboseOutputTextArea.setLineWrap(true);
        verboseOutputTextArea.setWrapStyleWord(true);
        verboseOutputTextArea.setEditable(false);
        verboseOutputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        setComparisonText();

        JButton runComparison = runComparisonButton();

        JButton setComparisonParameters = new JButton("Edit Parameters");

        setComparisonParameters.addActionListener(e -> {
            Box parameterBox = Box.createVerticalBox();

            Box horiz1 = Box.createHorizontalBox();
            horiz1.add(new JLabel("Save Data:"));
            horiz1.add(Box.createHorizontalGlue());
            horiz1.add(getBooleanSelectionBox("algcomparisonSaveData", model.getParameters(), false));

            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Save Graphs:"));
            horiz2.add(Box.createHorizontalGlue());
            horiz2.add(getBooleanSelectionBox("algcomparisonSaveGraphs", model.getParameters(), false));

            Box horiz2b = Box.createHorizontalBox();
            horiz2b.add(new JLabel("Save CPDAGs:"));
            horiz2b.add(Box.createHorizontalGlue());
            horiz2b.add(getBooleanSelectionBox("algcomparisonSaveCPDAGs", model.getParameters(), false));

            Box horiz2c = Box.createHorizontalBox();
            horiz2c.add(new JLabel("Save PAGs:"));
            horiz2c.add(Box.createHorizontalGlue());
            horiz2c.add(getBooleanSelectionBox("algcomparisonSavePAGs", model.getParameters(), false));

            Box horiz4a = Box.createHorizontalBox();
            horiz4a.add(new JLabel("Sort by Utility:"));
            horiz4a.add(Box.createHorizontalGlue());
            horiz4a.add(getBooleanSelectionBox("algcomparisonSortByUtility", model.getParameters(), false));

            Box horiz4b = Box.createHorizontalBox();
            horiz4b.add(new JLabel("Show Utilities:"));
            horiz4b.add(Box.createHorizontalGlue());
            horiz4b.add(getBooleanSelectionBox("algcomparisonShowUtilities", model.getParameters(), false));

            Box horiz4c = Box.createHorizontalBox();
            horiz4c.add(new JLabel("Set Knowledge on Algorithm If Available:"));
            horiz4c.add(Box.createHorizontalGlue());
            horiz4c.add(getBooleanSelectionBox("algcomparisonSetAlgorithmKnowledge", model.getParameters(), false));

            Box horiz5 = Box.createHorizontalBox();
            horiz5.add(new JLabel("Parallelism:"));
            horiz5.add(Box.createHorizontalGlue());
            horiz5.add(getIntTextField("algcomparisonParallelism", model.getParameters(), model.getParameters().getInt("algcomparisonParallelism", Runtime.getRuntime().availableProcessors()), 1, 1000));

            Box horiz6 = Box.createHorizontalBox();
            horiz6.add(new JLabel("Comparison Graph Type:"));
            horiz6.add(Box.createHorizontalGlue());
            JComboBox<String> comparisonGraphTypeComboBox = new JComboBox<>();

            Box horiz7 = Box.createHorizontalBox();
            horiz7.add(new JLabel("Markov Checker:"));
            horiz7.add(Box.createHorizontalGlue());
            JButton chooseTest = getButton();

            horiz7.add(chooseTest);

            Box horiz8 = Box.createHorizontalBox();
            horiz8.add(new JLabel("Markov Checker:"));
            horiz8.add(Box.createHorizontalGlue());
            JButton configureMarkovChecker = new JButton("Params");

            configureMarkovChecker.addActionListener(e3 -> {
                JPanel independenceWrapperParamsPanel = createIndependenceWrapperParamsPanel(model.getParameters());
                JOptionPane dialog = new JOptionPane(independenceWrapperParamsPanel, JOptionPane.PLAIN_MESSAGE);
                dialog.createDialog("Set Parameters").setVisible(true);
            });


            horiz7.add(configureMarkovChecker);

            for (GridSearchModel.ComparisonGraphType comparisonGraphType : GridSearchModel.ComparisonGraphType.values()) {
                comparisonGraphTypeComboBox.addItem(comparisonGraphType.toString());
            }

            comparisonGraphTypeComboBox.setSelectedItem(model.getParameters().getString("algcomparisonGraphType"));

            comparisonGraphTypeComboBox.addActionListener(e1 -> {
                String selectedItem = (String) comparisonGraphTypeComboBox.getSelectedItem();
                model.getParameters().set("algcomparisonGraphType", selectedItem);
            });

            horiz6.add(comparisonGraphTypeComboBox);

            parameterBox.add(horiz1);
            parameterBox.add(horiz2);
            parameterBox.add(horiz2b);
            parameterBox.add(horiz2c);
            parameterBox.add(horiz4a);
            parameterBox.add(horiz4b);
            parameterBox.add(horiz4c);
            parameterBox.add(horiz5);
            parameterBox.add(horiz6);
            parameterBox.add(horiz7);

            parameterBox.setBorder(new EmptyBorder(10, 10, 10, 10));

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Comparison Parameters", Dialog.ModalityType.APPLICATION_MODAL);

            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton("Done");

            doneButton.addActionListener(e1 -> {
                SwingUtilities.invokeLater(dialog::dispose);
                setComparisonText();
            });

            buttonPanel.add(doneButton);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });

        Box comparisonSelectionBox = Box.createHorizontalBox();
        comparisonSelectionBox.add(Box.createHorizontalGlue());
        comparisonSelectionBox.add(runComparison);
        comparisonSelectionBox.add(createEditutilitiesButton());
        comparisonSelectionBox.add(setComparisonParameters);
        comparisonSelectionBox.add(Box.createHorizontalGlue());

        comparisonTabbedPane = new JTabbedPane();
        comparisonScroll = new JScrollPane(comparisonTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        comparisonTabbedPane.addTab("Comparison", comparisonScroll);
        comparisonTabbedPane.addTab("Verbose Output", new JScrollPane(verboseOutputTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));

        JPanel comparisonPanel = new JPanel();
        comparisonPanel.setLayout(new BorderLayout());

        comparisonPanel.add(comparisonTabbedPane, BorderLayout.CENTER);
        comparisonPanel.add(comparisonSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Comparison", comparisonPanel);

        tabbedPane.addTab("View Graphs", getGraphSelectorBox());

        tabbedPane.addChangeListener(e -> {
            JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
            refreshGraphSelectionContent(sourceTabbedPane);
        });
    }

    private @NotNull JButton getButton() {
        JButton chooseTest = new JButton("Choose Test");

        chooseTest.addActionListener(e2 -> {
            JComboBox<IndependenceTestModel> comboBox = new JComboBox<>();
            populateTestTypes(comboBox); // now: populate + select, but does NOT auto-commit

            int ret = JOptionPane.showConfirmDialog(
                    this,
                    comboBox,
                    "Choose Markov Checker Test",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (ret == JOptionPane.OK_OPTION) {
                IndependenceTestModel selected = (IndependenceTestModel) comboBox.getSelectedItem();
                applySelectedMarkovCheckerTest(selected);
            }
        });
        return chooseTest;
    }

    /**
     * Returns a Box component with selectors for simulation, algorithm, and graph index.
     *
     * @return a Box component with selectors for simulation, algorithm, and graph index
     */
    private @NotNull Box getGraphSelectorBox() {
        String resultsPath = model.getResultsPath();

        File resultsDir = new File(resultsPath, "results");

        List<Integer> simulationIndices = getIntegers(resultsDir);

        Collections.sort(simulationIndices);

        Box graphSelectorBox = Box.createVerticalBox();
        Box instructions = Box.createHorizontalBox();
        instructions.add(new JLabel("Select the simulation, algorithm, and graph index to view, from the comparison table:"));
        instructions.add(Box.createHorizontalGlue());
        graphSelectorBox.add(Box.createVerticalStrut(4));
        graphSelectorBox.add(instructions);
        graphSelectorBox.add(Box.createVerticalStrut(4));
        Box selectors = Box.createHorizontalBox();
        JComboBox<Integer> simulationComboBox = new JComboBox<>();
        JComboBox<Integer> algorithmComboBox = new JComboBox<>();
        JComboBox<Integer> graphIndexComboBox = new JComboBox<>();

        for (int i : simulationIndices) {
            simulationComboBox.addItem(i);
        }

        if (model.getSelectedSimulation() != null) {
            simulationComboBox.setSelectedItem(model.getSelectedSimulation());
        }

        updateAlgorithmBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
        updateGraphBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);

        if (model.getSelectedGraphIndex() > 0) {
            graphIndexComboBox.setSelectedItem(model.getSelectedGraphIndex());
        }

//        selectors.add(new JLabel("Simulation:"));
//        selectors.add(simulationComboBox);

        selectors.add(new JLabel("Algorithm:"));
        selectors.add(algorithmComboBox);

        selectors.add(new JLabel("Graph Index:"));
        selectors.add(graphIndexComboBox);

        graphSelectorBox.add(selectors);
        graphSelectorBox.add(Box.createVerticalStrut(4));

        GraphWorkbench workbench = new GraphWorkbench();
        workbench.setGraph(new EdgeListGraph());

        graphSelectorBox.add(new JScrollPane(workbench));

        // Add listeners to the algorithm and simulation combo boxes to update the graph index combo box
        // when the algorithm or simulation is changed.
        simulationComboBox.addActionListener(e -> {
            updateAlgorithmBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
            updateGraphBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
            updateSelectedGraph(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir, workbench);
        });

        algorithmComboBox.addActionListener(e -> {
            updateGraphBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
            updateSelectedGraph(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir, workbench);
        });

        graphIndexComboBox.addActionListener((ActionEvent e) -> updateSelectedGraph(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir, workbench));

        updateAlgorithmBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
        updateGraphBoxIndices(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir);
        updateSelectedGraph(simulationComboBox, algorithmComboBox, graphIndexComboBox, resultsDir, workbench);

        return graphSelectorBox;
    }

    @NotNull
    private JButton runComparisonButton() {
        JButton runComparison = new JButton("Run Comparison");

        runComparison.addActionListener(e -> {

            class MyWatchedProcess extends WatchedProcess {

                public void watch() {
                    SwingUtilities.invokeLater(() -> comparisonTabbedPane.setSelectedIndex(1));

                    ByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
                    java.io.PrintStream ps1 = new java.io.PrintStream(baos);

                    verboseOutputTextArea.setText("");

                    TextAreaOutputStream baos2 = new TextAreaOutputStream(verboseOutputTextArea);
                    PrintStream ps2 = new PrintStream(baos2);

                    TetradLogger.getInstance().addOutputStream(baos2);

                    try {
                        model.runComparison(ps1, ps2);

                        String resultsPath = model.getResultsPath();

                        if (resultsPath != null && simulationChoiceTextArea != null) {
                            // Write contents of the simulation text area to a file at resultsPath + "/simulation.txt"
                            try (PrintWriter writer = new PrintWriter(resultsPath + "/simulation.txt")) {
                                writer.println(simulationChoiceTextArea.getText());
                            } catch (FileNotFoundException ex) {
                                throw new RuntimeException(ex);
                            }

                            // Write contents of the algorithm text area to a file at resultsPath + "/algorithm.txt"
                            try (PrintWriter writer = new PrintWriter(resultsPath + "/algorithm.txt")) {
                                writer.println(algorithmChoiceTextArea.getText());
                            } catch (FileNotFoundException ex) {
                                throw new RuntimeException(ex);
                            }

                            // Write contents of table columns text area to a file at resultsPath + "/tableColumns.txt"
                            try (PrintWriter writer = new PrintWriter(resultsPath + "/tableColumns.txt")) {
                                writer.println(tableColumnsChoiceTextArea.getText());
                            } catch (FileNotFoundException ex) {
                                throw new RuntimeException(ex);
                            }

                            // Write contents of the verbose output text area to a file at resultsPath + "/verboseOutput.txt"
                            try (PrintWriter writer = new PrintWriter(resultsPath + "/verboseOutput.txt")) {
                                writer.println(verboseOutputTextArea.getText());
                            } catch (FileNotFoundException ex) {
                                throw new RuntimeException(ex);
                            }


                        }

                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    ps1.flush();
                    comparisonTextArea.setText(baos.toString());

                    TetradLogger.getInstance().removeOutputStream(baos2);

                    SwingUtilities.invokeLater(() -> {
                        try {
                            scrollToWord(comparisonTextArea, comparisonScroll, "AVERAGE VALUE");
                        } catch (BadLocationException ex) {
                            System.out.println("Scrolling operation failed.");
                        }
                    });

                    SwingUtilities.invokeLater(() -> comparisonTabbedPane.setSelectedIndex(0));

                    if (comparisonTextArea != null) {
                        model.setLastComparisonText(comparisonTextArea.getText());
                    }

                    if (verboseOutputTextArea != null) {
                        model.setLastVerboseOutputText(verboseOutputTextArea.getText());
                    }
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
    private void addSelectSimulationListener() {
        selectSimulation.addActionListener(e -> {
            if (model.getSuppliedData() != null) {
                throw new IllegalArgumentException("A data set has been supplied, so we cannot select a simulation.");
            }

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            Box vert1 = Box.createVerticalBox();
            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Choose a graph type:"));
            horiz2.add(Box.createHorizontalGlue());
            JComboBox<String> graphsDropdown = getGraphsDropdown();

            Arrays.stream(ParameterTab.GRAPH_TYPE_ITEMS).forEach(graphsDropdown::addItem);

            if (model.getSuppliedGraph() != null) {
                graphsDropdown.addItem("User Supplied Graph");
            }

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
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Select Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = getButtonPanel(graphsDropdown, simulationsDropdown, dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
    }

    @NotNull
    private JComboBox<String> getGraphsDropdown() {
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

        return graphsDropdown;
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
                case 0 -> BayesNetSimulation.class;
                case 1 -> SemSimulation.class;
                case 2 -> LinearFisherModel.class;
                case 3 -> AdditiveAnmSimulator.class;
                case 4 -> GeneralNoiseSimulation.class;
                case 5 -> AdditiveNoiseSimulation.class;
                case 6 -> PostnonlinearSem.class;
                case 7 -> LeeHastieSimulation.class;
                case 8 -> ConditionalGaussianSimulation.class;
                case 9 -> TimeSeriesSemSimulation.class;
                default -> throw new IllegalArgumentException("Unexpected value: " + simulationString);
            };

            GridSearchModel.SimulationSpec spec = new GridSearchModel.SimulationSpec("name", graphClazz, simulationClass);

            // Single-simulation policy: replace the current selection rather than accumulating.
            // (We keep the existing model API by clearing the current list before adding.)
            try {
                Simulations sims = model.getSelectedSimulations();
                if (sims != null && sims.getSimulations() != null) {
                    sims.getSimulations().clear();
                }
            } catch (Exception ignore) {
                // If the model changes later, failing to clear here is non-fatal; addSimulationSpec will still set something.
            }

//            model.removeLastSimulation();
//            model.addSimulationSpec(spec);
            model.setSelectedSimulation(spec);
            setComparisonText();
            setSimulationText();

            onSelectedAlgorithmsChanged();

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
            List<AlgorithmModel> algorithmModels1 = algorithmModels.getModels(getDataTypeForGridSearch(), false);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            algorithmDropdown = new JComboBox<>();

            for (AlgorithmModel model : algorithmModels1) {
                algorithmDropdown.addItem(model);
            }

            String lastAlgorithmChoice = model.getLastAlgorithmChoice();

            for (int i = 0; i < algorithmDropdown.getItemCount(); i++) {
                AlgorithmModel itemAt = (AlgorithmModel) algorithmDropdown.getItemAt(i);
                if (itemAt.getName().equals(lastAlgorithmChoice)) {
                    algorithmDropdown.setSelectedIndex(i);
                    break;
                }
            }

            algorithmDropdown.addActionListener(e1 -> {
                setupAlgorithmDropdown();
            });

            Box vert1 = Box.createVerticalBox();
            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Choose an algorithm:"));
            horiz2.add(Box.createHorizontalGlue());
            horiz2.add(algorithmDropdown);
            vert1.add(horiz2);

            indTestComboBox = new JComboBox<>();
            scoreModelComboBox = new JComboBox<>();
            setupAlgorithmDropdown();

            Box horiz4 = Box.createHorizontalBox();
            horiz4.add(new JLabel("Choose an independence test:"));
            horiz4.add(Box.createHorizontalGlue());
            horiz4.add(indTestComboBox);
            vert1.add(horiz4);

            Box horiz5 = Box.createHorizontalBox();
            horiz5.add(new JLabel("Choose a score:"));
            horiz5.add(Box.createHorizontalGlue());
            horiz5.add(scoreModelComboBox);
            vert1.add(horiz5);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = getAddButton(dialog);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
    }

    private void setupAlgorithmDropdown() {
        AlgorithmModel selectedItem = (AlgorithmModel) algorithmDropdown.getSelectedItem();

        if (selectedItem != null) {
            model.setLastAlgorithmChoice(selectedItem.getName());
        }

        DataType datatype = getDataTypeForGridSearch();

        indTestComboBox.removeAllItems();

        if (selectedItem != null && selectedItem.isRequiredTest()) {
            List<IndependenceTestModel> indTestModels = switch (datatype) {
                case DataType.Continuous -> IndependenceTestModels.getInstance().getModels(DataType.Continuous);
                case DataType.Discrete -> IndependenceTestModels.getInstance().getModels(DataType.Discrete);
                case DataType.Mixed -> IndependenceTestModels.getInstance().getModels(DataType.Mixed);
                default -> new ArrayList<>();
            };

            for (IndependenceTestModel model1 : indTestModels) {
                indTestComboBox.addItem(model1);
            }
        }

        scoreModelComboBox.removeAllItems();

        if (selectedItem != null && selectedItem.isRequiredScore()) {
            List<ScoreModel> scoreModelsList = switch (datatype) {
                case DataType.Continuous -> ScoreModels.getInstance().getModels(DataType.Continuous);
                case DataType.Discrete -> ScoreModels.getInstance().getModels(DataType.Discrete);
                case DataType.Mixed -> ScoreModels.getInstance().getModels(DataType.Mixed);
                default -> new ArrayList<>();
            };

            for (ScoreModel model1 : scoreModelsList) {
                scoreModelComboBox.addItem(model1);
            }
        }

        revalidate();
        repaint();
    }

    private void addRemoveLastAlgorithmListener() {
        removeLastAlgorithm.addActionListener(e -> {
            List<GridSearchModel.AlgorithmSpec> algs = model.getSelectedAlgorithms();
            if (algs == null || algs.isEmpty()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            // Prefer model API if present; fallback to mutating the live list
            model.removeLastAlgorithm();          // if you have it

            onSelectedAlgorithmsChanged();
        });
    }

    private void addEditAlgorithmParametersListener() {
        editAlgorithmParameters.addActionListener(e -> openEditAlgorithmParametersDialog());
    }

    private DataType getDataTypeForGridSearch() {
        // Best: infer from the actual dataset being analyzed.
        // Placeholder: return model.getDataType() if you already have it.
        // Fallback: Continuous.

        if (model.getSelectedAlgorithms() == null) {
            return inferSuppliedDataType();
        } else if (!model.getSelectedAlgorithms().isEmpty()) {
            if (model.getSuppliedData() != null) {
                return inferSuppliedDataType();
            } else {
                return model.getSelectedSimulation().getSimulationImpl().getDataType();
            }
        } else {
            return DataType.Continuous;
        }
    }

    @NotNull
    private JPanel getAddButton(JDialog dialog) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        // Add action listeners for the buttons
        addButton.addActionListener(e1 -> {
            AlgorithmModel algorithmModel = (AlgorithmModel) algorithmDropdown.getSelectedItem();

            if (algorithmModel == null) {
                return;
            }

            Class<?> algorithm = algorithmModel.getAlgorithm().clazz();

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

                if (algorithmImpl instanceof TakesScoreWrapper && scoreWrapper != null) {
                    ((TakesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
                }

//                model.addAlgorithm(new GridSearchModel.AlgorithmSpec("name", algorithmModel, test, score));

                String displayName = algorithmModel.getName(); // or algorithmModel.toString()
                model.addAlgorithm(new GridSearchModel.AlgorithmSpec(displayName, algorithmModel, test, score));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }

            onSelectedAlgorithmsChanged();
            dialog.dispose();
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
     * Adds an action listener to the addTableColumns button. This action listener creates a table model, adds a JTable
     * to the panel, and sets a row sorter for the table based on user input. It also creates a text field with a
     * document listener to filter the table based on user input.
     */
    private void addAddTableColumnsListener(JTabbedPane tabbedPane) {
        addTableColumns.addActionListener(e -> {
            java.util.Set<GridSearchModel.MyTableColumn> selectedColumns = new HashSet<>();
            List<GridSearchModel.MyTableColumn> allTableColumns = model.getAllTableColumns();

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
                    GridSearchModel.MyTableColumn myTableColumn = columnSelectionTableModel.getMyTableColumn(i);

                    if (myTableColumn.getType() == GridSearchModel.MyTableColumn.ColumnType.PARAMETER && myTableColumn.isSetByUser()) {
                        columnSelectionTableModel.selectRow(i);
                    }
                }
            });

            selectLastStatisticsUsed.addActionListener(e1 -> {
                for (int i = 0; i < table.getRowCount(); i++) {
                    GridSearchModel.MyTableColumn myTableColumn = columnSelectionTableModel.getMyTableColumn(i);
                    List<String> lastStatisticsUsed = model.getLastStatisticsUsed();

                    if (myTableColumn.getType() == GridSearchModel.MyTableColumn.ColumnType.STATISTIC && lastStatisticsUsed.contains(myTableColumn.getColumnName())) {
                        columnSelectionTableModel.selectRow(i);
                    }
                }
            });

            panel.add(vert1, BorderLayout.CENTER);


            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Table Column", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(panel, BorderLayout.CENTER);
            dialog.setPreferredSize(new Dimension(500, 500));

            // Create a panel for the buttons
            JPanel buttonPanel = getButtonPanel(columnSelectionTableModel, dialog, tabbedPane);

            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            // Set the dialog size, position, and visibility
            dialog.pack(); // Adjust the dialog size to fit its contents
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
    }

    @NotNull
    private JPanel getButtonPanel(TableColumnSelectionModel columnSelectionTableModel, JDialog dialog, JTabbedPane tabbedPane) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        // Add action listeners for the buttons
        addButton.addActionListener(e1 -> {
            columnSelectionTableModel.setTableRef(null);
            SwingUtilities.invokeLater(dialog::dispose);

            List<GridSearchModel.MyTableColumn> selectedTableColumns = new ArrayList<>(columnSelectionTableModel.getSelectedTableColumns());

            for (GridSearchModel.MyTableColumn column : selectedTableColumns) {
                model.addTableColumn(column);
            }

            setLayout(new BorderLayout());
            add(tabbedPane, BorderLayout.CENTER);

            setTableColumnsText();
            setComparisonText();
        });

        cancelButton.addActionListener(e12 -> {
            columnSelectionTableModel.setTableRef(null);

            setLayout(new BorderLayout());
            add(tabbedPane, BorderLayout.CENTER);

            dialog.dispose();
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
     * parameter text obtained from the getSimulationParameterText () method.
     */
    private void setSimulationText() {
        simulationChoiceTextArea.setText("");

        Simulations selectedSimulations = model.getSelectedSimulations();
        List<Simulation> simulations = selectedSimulations.getSimulations();

        DataSet dataSet = model.getSuppliedData();

        if (dataSet != null) {
            simulationChoiceTextArea.append("A data set has been supplied with " + dataSet.getNumColumns() + " variables and " + dataSet.getNumRows() + " rows.");
            simulationChoiceTextArea.append("\n\nThe variables for the data are as follow: " + dataSet.getVariableNames() + "\n\n");
        }

        Graph graph = model.getSuppliedGraph();

        if (graph != null) {
            simulationChoiceTextArea.append("A graph has been supplied with " + graph.getNumNodes() + " nodes and " + graph.getNumEdges() + " edges.");
            simulationChoiceTextArea.append("\n\nThe nodes for the graph are as follow: " + graph.getNodeNames() + "\n\n");
        }

        Knowledge knowledge = model.getKnowledge();

        if (knowledge != null) {
            simulationChoiceTextArea.append("Knowledge has been set, as follows:");
            simulationChoiceTextArea.append("\n\n" + knowledge + "\n\n");
        }

        if (simulations.isEmpty()) {
            simulationChoiceTextArea.append("""
                     ** No simulations have been selected. Please select at least one simulation using the Select Simulation button below. **
                    """);
            return;
        } else if (simulations.size() == 1) {
            simulationChoiceTextArea.append("""
                    The following simulation has been selected. This simulations will be run with the selected algorithms.
                    
                    """);

            Simulation simulation = simulations.getFirst();
            Class<? extends RandomGraph> randomGraphClass = simulation.getRandomGraphClass();
            Class<? extends Simulation> simulationClass = simulation.getSimulationClass();
            simulationChoiceTextArea.append("Selected graph type = " + (randomGraphClass == null ? "None" : randomGraphClass.getSimpleName() + "\n"));
            simulationChoiceTextArea.append("Selected simulation type = " + simulationClass.getSimpleName() + "\n");
        } else {
            simulationChoiceTextArea.append("""
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

        List<GridSearchModel.AlgorithmSpec> selectedAlgorithms = model.getSelectedAlgorithms();

        if (selectedAlgorithms == null || selectedAlgorithms.isEmpty()) {
            algorithmChoiceTextArea.append(
                    "** No algorithms have been selected. Please select at least one algorithm using the Add Algorithm button below. **\n"
            );
            algorithmChoiceTextArea.setCaretPosition(0);
            return;
        }

        if (selectedAlgorithms.size() == 1) {
            algorithmChoiceTextArea.append("""
                    The following algorithm has been selected. This algorithm will be run with the selected simulations.
                    
                    """);
        } else {
            algorithmChoiceTextArea.append("""
                    The following algorithms have been selected. These algorithms will be run with the selected simulations.
                    """);
            algorithmChoiceTextArea.append("\n");
        }

        // Build a stable, non-side-effecting summary of what’s selected.
        for (int i = 0; i < selectedAlgorithms.size(); i++) {
            GridSearchModel.AlgorithmSpec spec = selectedAlgorithms.get(i);

            // Instantiate exactly once (per spec) for the algorithm description.
            // (If this is still too expensive, we can add a cachedDescription field to AlgorithmSpec later.)
            Algorithm algImpl;
            try {
                algImpl = spec.getAlgorithmImpl();
            } catch (Exception ex) {
                algorithmChoiceTextArea.append("\nAlgorithm #" + (i + 1) + ". " + spec.getName() + "\n");
                algorithmChoiceTextArea.append("  (Error constructing algorithm instance for description: " + ex.getMessage() + ")\n");
                continue;
            }

            if (selectedAlgorithms.size() == 1) {
                algorithmChoiceTextArea.append("Selected algorithm: " + safe(algImpl.getDescription()) + "\n");
            } else {
                algorithmChoiceTextArea.append("\nAlgorithm #" + (i + 1) + ". " + safe(algImpl.getDescription()) + "\n");
            }

            // Test + Score are chosen in AlgorithmSpec, not on AlgorithmSpec itself.
            // So report from spec.getTest()/getScore() (and/or the wrappers those map to).
            if (spec.getTest() != null) {
                // Prefer IndependenceWrapper.getDescription() if available.
                String testDesc = describeTest(spec);
                algorithmChoiceTextArea.append("Selected independence test = " + testDesc + "\n");
            }

            if (spec.getScore() != null) {
                String scoreDesc = describeScore(spec);
                algorithmChoiceTextArea.append("Selected score = " + scoreDesc + "\n");
            }
        }

        // Parameter summary you already have
        algorithmChoiceTextArea.append(getAlgorithmParameterText());

        // --------- Descriptions section (dedup by NAME) ---------
        Set<String> algorithmNamesSeen = new HashSet<>();
        if (!selectedAlgorithms.isEmpty()) {
            algorithmChoiceTextArea.append("\n\nAlgorithm Descriptions:");
        }

        for (GridSearchModel.AlgorithmSpec spec : selectedAlgorithms) {
            String name = spec.getName();
            if (name == null) name = "(Unnamed Algorithm)";
            if (!algorithmNamesSeen.add(name)) continue;

            algorithmChoiceTextArea.append("\n\n" + name);

            // Use model description rather than constructing algorithm again
            AlgorithmModel modelAlg = spec.getAlgorithm();
            if (modelAlg != null && modelAlg.getDescription() != null) {
                algorithmChoiceTextArea.append("\n\n" + modelAlg.getDescription().replace("\n", "\n\n"));
            } else {
                algorithmChoiceTextArea.append("\n\n(No description available.)");
            }
        }

        // --------- Independence Test Descriptions (dedup) ---------
        Set<Class<?>> testClasses = new HashSet<>();
        for (GridSearchModel.AlgorithmSpec spec : selectedAlgorithms) {
            if (spec.getTest() != null && spec.getTest().clazz() != null) {
                testClasses.add(spec.getTest().clazz());
            }
        }

        if (!testClasses.isEmpty()) {
            algorithmChoiceTextArea.append("\n\nIndependence Test Descriptions:");
            IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
            for (IndependenceTestModel m : independenceTestModels.getModels()) {
                Class<?> c = (m.getIndependenceTest() == null) ? null : m.getIndependenceTest().clazz();
                if (c != null && testClasses.contains(c)) {
                    algorithmChoiceTextArea.append("\n\n" + m.getName());
                    algorithmChoiceTextArea.append("\n\n" + safe(m.getDescription()).replace("\n", "\n\n"));
                }
            }
        }

        // --------- Score Descriptions (dedup) ---------
        Set<Class<?>> scoreClasses = new HashSet<>();
        for (GridSearchModel.AlgorithmSpec spec : selectedAlgorithms) {
            if (spec.getScore() != null && spec.getScore().clazz() != null) {
                scoreClasses.add(spec.getScore().clazz());
            }
        }

        if (!scoreClasses.isEmpty()) {
            algorithmChoiceTextArea.append("\n\nScore Descriptions:");
            ScoreModels scoreModels = ScoreModels.getInstance();
            for (ScoreModel m : scoreModels.getModels()) {
                Class<?> c = (m.getScore() == null) ? null : m.getScore().clazz();
                if (c != null && scoreClasses.contains(c)) {
                    algorithmChoiceTextArea.append("\n\n" + m.getName());
                    algorithmChoiceTextArea.append("\n\n" + safe(m.getDescription()).replace("\n", "\n\n"));
                }
            }
        }

        algorithmChoiceTextArea.setCaretPosition(0);
    }

    /**
     * Sets the text in the tableColumnsChoiceTextArea based on the selected table columns.
     */
    private void setTableColumnsText() {
        tableColumnsChoiceTextArea.setText("");

        List<GridSearchModel.MyTableColumn> selectedTableColumns = model.getSelectedTableColumns();

        if (selectedTableColumns.isEmpty()) {
            tableColumnsChoiceTextArea.append("""
                     ** No columns have been selected. Please select at least one column using the Add Table Column button below. **
                    """);
            return;
        } else if (selectedTableColumns.size() == 1) {
            tableColumnsChoiceTextArea.setText("""
                    The following table column has been selected. The comparison table will include these columns in the table.
                    """);
        } else {
            tableColumnsChoiceTextArea.setText("The comparison table will include these columns in the table.");
        }

        for (int i = 0; i < selectedTableColumns.size(); i++) {
            GridSearchModel.MyTableColumn tableColumn = selectedTableColumns.get(i);
            tableColumnsChoiceTextArea.append("\n\n" + (i + 1) + ". " + tableColumn.getColumnName() + " (" + tableColumn.getDescription() + ")");
        }

        tableColumnsChoiceTextArea.setCaretPosition(0);
    }

    /**
     * Sets the text of the comparisonTextArea based on the selected simulations, algorithms, and table columns. If any
     * of the simulations, algorithms, or table columns are empty, it sets a message indicating that the selection is
     * empty. Otherwise, it sets a message indicating that a comparison has not been run for the selection.
     */
    private void setComparisonText() {
        if (model.getSelectedSimulations().getSimulations().isEmpty() || model.getSelectedAlgorithms().isEmpty() || model.getSelectedTableColumns().isEmpty()) {
            comparisonTextArea.setText("""
                    ** You have made an empty selection; look back at the Simulation, Algorithm, and Table Columns tabs **
                    """);
        } else if (comparisonTextArea.getText().isBlank()) {
            comparisonTextArea.setText("""
                    ** Your selection is non-empty, but you have not yet run a comparison for it **
                    """);
        }
    }

    /**
     * Sets the help text for the tool.
     */
    private void setHelpText() {
        helpChoiceTextArea.setText("""
                This tool may be used to do a comparison of multiple algorithms (in Tetrad for now) for a range of simulations types, algorithms, table columns, and parameter settings.
                
                To run a Grid Search comparison, select one or more simulations, one or more algorithms, and one or more table columns (statistics or parameter columns). Then in the Comparison tab, click the "Run Comparison" button.
                
                The comparison will be displayed in the "comparison" tab.
                
                Some combinations you may select could take a very long time to run; you may need to experiment. One problem is that you may select too many combinations of parameters, and the tool will try every combination of these parameters that is sensible, and perhaps this may take a very long time to do. Or you may, for instance, opt for graphs that have too many variables or are too dense. Or, some of the algorithms may simply take a very long time to run, even for small graphs. We will run your request in a thread with a stop button so you can gracefully exit and try a smaller problem. In fact, it may not make sense to run larger comparisons in this interface at all; you may wish to use the command line tool or Python to do it.
                
                If you think the problem is that you need more memory, you can increase the memory available to the JVM by starting Tetrad from the command line changing the -Xmx option in at startup. That is, you can start Tetrad with a command like this:
                
                    java -Xmx4g -jar [tetrad.jar]
                
                Here, "[tetrad.jar]" should be replaced by the name of the Tetrad jar you have downloaded. This would set the maximum memory available to the JVM to 4 gigabytes. You can increase this number to increase the memory available to the JVM up to the limit of what you have available on your machine. The default is 1 gigabyte.
                
                In the Simulation tab, simulations may be added by clicking the Add Simulation button. The last one in the list may be removed by clicking the Remove Last Simulation button.
                
                A simulation selection requires one to select a graph type and a simulation type.
                
                This selection implies a list of parameters for all of the simulations. These parameters may be edited by clicking the Edit Parameters button. Note that parameters may be given a list of comma-separated values; each combination of parameters will be explored in the comparison.
                
                The Algorithm tab and TableColumns tab work similarly. An algorithm selection requires one to select an algorithm type and then an independence test and/or a score depending on the requirements of the algorithm.
                
                For the Algorithm tab, once one has selected all algorithms, one may edit the parameters for these algorithms.
                
                For the TableColumns tab, one may select the columns to be included in the comparison table. These columns may be selected by clicking the Add Table Column button. The last column in the list may be removed by clicking the Remove Last Table Column button. For parameter columns, parameters that have been set by the user may be selected by clicking the Select Parameters Used button. For statistic columns, the last statistics used may be selected by clicking the Select Last Statistics Used button. This will select all statistics that were used in the last comparison.
                
                In the Comparison tab, there is a button to run the comparison and display the results.
                
                Full results are saved to the user's hard drive. The location of these files is displayed in the comparison tab, at the top of the page. This includes all the output from the comparison, including the true dataset and graphs for all simulations, the estimated graph, elapsed times for all algorithm runs, and the results displayed in the Comparison tab for the comparison. These datasets and graphs may be used for analayis by other tools, such as in R or Python.
                
                The reference is here:
                
                Ramsey, J. D., Malinsky, D., &amp; Bui, K. V. (2020). Algcomparison: Comparing the performance of graphical structure learning algorithms with tetrad. Journal of Machine Learning Research, 21(238), 1-6.
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
        List<GridSearchModel.AlgorithmSpec> algorithm = model.getSelectedAlgorithms();
        Set<String> allAlgorithmParameters = GridSearchModel.getAllAlgorithmParameters(algorithm);
        Set<String> allTestParameters = GridSearchModel.getAllTestParameters(algorithm);
        Set<String> allScoreParameters = GridSearchModel.getAllScoreParameters(algorithm);
        Set<String> allBootstrappingParameters = GridSearchModel.getAllBootstrapParameters(algorithm);
        StringBuilder paramText = new StringBuilder();

        if (algorithm.size() == 1) {
            if (!allAlgorithmParameters.isEmpty()) {
                paramText.append("\nParameter choices for this algorithm:");
                paramText.append(getParameterText(allAlgorithmParameters, model.getParameters()));
            }

            if (!allTestParameters.isEmpty()) {
                paramText.append("\n\nParameter choices for this test:");
                paramText.append(getParameterText(allTestParameters, model.getParameters()));
            }

            if (!allScoreParameters.isEmpty()) {
                paramText.append("\n\nParameter choices for this score:");
                paramText.append(getParameterText(allScoreParameters, model.getParameters()));
            }

        } else {
            if (!allAlgorithmParameters.isEmpty()) {
                paramText.append("\nParameter choices for all algorithms:");
                paramText.append(getParameterText(allAlgorithmParameters, model.getParameters()));
            }

            if (!allTestParameters.isEmpty()) {
                paramText.append("\n\nParameter choices for all tests:");
                paramText.append(getParameterText(allTestParameters, model.getParameters()));
            }

            if (!allScoreParameters.isEmpty()) {
                paramText.append("\n\nParameter choices for all scores:");
                paramText.append(getParameterText(allScoreParameters, model.getParameters()));
            }

        }
        if (!allBootstrappingParameters.isEmpty()) {
            paramText.append("\n\nParameter choices for bootstrapping:");
            paramText.append(getParameterText(allBootstrappingParameters, model.getParameters()));
        }

        return paramText.toString();
    }

    /**
     * Creates a parameters panel for the given independence wrapper and parameters.
     *
     * @param params The parameters for the independence test.
     * @return The JPanel containing the parameters panel.
     */
    private JPanel createIndependenceWrapperParamsPanel(Parameters params) {
        Set<String> testParameters = new HashSet<>(model.getMarkovCheckerIndependenceWrapper().getParameters());
        return createParamsPanel(testParameters, params);
    }

    /**
     * Refreshes the test list in the GUI. Retrieves the data type of the data set. Removes all items from the test
     * combo box. Retrieves the independence test models for the given data type. Adds the independence test models to
     * the test combo box. Disables the test combo box if there are no items. Selects the default model for the data
     * type.
     */
    private void populateTestTypes(JComboBox<IndependenceTestModel> indTestJComboBox) {
        indTestJComboBox.removeAllItems();

        // Pull *all* models (as before)
        List<IndependenceTestModel> models = new ArrayList<>();
        models.addAll(IndependenceTestModels.getInstance().getModels(DataType.Continuous));
        models.addAll(IndependenceTestModels.getInstance().getModels(DataType.Discrete));
        models.addAll(IndependenceTestModels.getInstance().getModels(DataType.Mixed));

        // De-dupe (defensive), keep stable order
        LinkedHashMap<String, IndependenceTestModel> uniq = new LinkedHashMap<>();
        for (IndependenceTestModel m : models) {
            if (m == null) continue;
            // Using name as a key is usually what the UI expects; change to something stronger if you have it.
            uniq.putIfAbsent(m.getName(), m);
        }
        models = new ArrayList<>(uniq.values());

        for (IndependenceTestModel m : models) {
            indTestJComboBox.addItem(m);
        }

        indTestJComboBox.setEnabled(!models.isEmpty());
        if (models.isEmpty()) return;

        // Choose selection (in priority order):
        //   1) explicit model state
        //   2) preference (last used)
        //   3) preferred default (BF blocks if available; else type-based; else Fisher Z; else first)
        IndependenceTestModel chosen = null;

        IndependenceTestModel remembered = this.model.getSelectedIndependenceTestModel();
        if (remembered != null && models.contains(remembered)) {
            chosen = remembered;
        }

        if (chosen == null) {
            String lastName = safeTrim(this.model.getLastIndependenceTest());
            if (!lastName.isEmpty()) {
                for (IndependenceTestModel m : models) {
                    if (m != null && lastName.equals(m.getName())) {
                        chosen = m;
                        break;
                    }
                }
            }
        }

        if (chosen == null) {
            DataType dt = inferSuppliedDataType();
            chosen = choosePreferredDefaultTest(models, dt);
        }

        if (chosen == null) chosen = models.getFirst();

        indTestJComboBox.setSelectedItem(chosen);
    }

    /**
     * Commit the selected Markov-checker test into the model (and Preferences) and rebuild the corresponding
     * IndependenceWrapper instance.
     */
    private void applySelectedMarkovCheckerTest(IndependenceTestModel selected) {
        if (selected == null) return;

        this.model.setSelectedIndependenceTestModel(selected);
        this.model.setLastIndependenceTest(selected.getName());

        @SuppressWarnings("unchecked")
        Class<IndependenceWrapper> clazz =
                (Class<IndependenceWrapper>) selected.getIndependenceTest().clazz();

        if (clazz == null) return;

        try {
            IndependenceWrapper independenceWrapper =
                    clazz.getDeclaredConstructor(new Class[0]).newInstance();
            model.setMarkovCheckerIndependenceWrapper(independenceWrapper);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException e1) {
            TetradLogger.getInstance().log("Error: " + e1.getMessage());
            throw new RuntimeException(e1);
        }
    }

    /**
     * Prefer BF blocks for Markov checking when available; otherwise fall back to a sensible choice based on data type;
     * otherwise Fisher Z; otherwise first.
     */
    private IndependenceTestModel choosePreferredDefaultTest(List<IndependenceTestModel> models, DataType dt) {
        // 1) Strong preference: Basis-function blocks (your intended default strategy)
        for (IndependenceTestModel m : models) {
            if (m == null) continue;
            String name = m.getName();
            if (name != null && name.toLowerCase().contains("basis")) {
                return m;
            }
        }

        // 2) If mixed data, Conditional Gaussian variants are often the “least wrong” fallback
        if (dt == DataType.Mixed) {
            for (IndependenceTestModel m : models) {
                if (m == null) continue;
                String name = m.getName();
                if (name != null && name.toLowerCase().contains("conditional gaussian")) {
                    return m;
                }
            }
        }

        // 3) Generic fallback many users expect
        for (IndependenceTestModel m : models) {
            if (m == null) continue;
            String name = m.getName();
            if (name != null && name.toLowerCase().contains("fisher")) {
                return m;
            }
        }

        return models.isEmpty() ? null : models.getFirst();
    }

    private DataType inferSuppliedDataType() {
        // GridSearchModel has suppliedData, but GridSearchEditor typically has access only through `model`.
        // If you have a direct accessor, use it; otherwise default conservatively.
        try {
            DataSet ds = model.getSuppliedData();
            if (ds == null) return DataType.Continuous;

            boolean hasContinuous = false;
            boolean hasDiscrete = false;
            for (Node v : ds.getVariables()) {
                if (v instanceof ContinuousVariable) hasContinuous = true;
                if (v instanceof DiscreteVariable) hasDiscrete = true;
            }
            if (hasContinuous && hasDiscrete) return DataType.Mixed;
            if (hasDiscrete) return DataType.Discrete;
            return DataType.Continuous;
        } catch (Exception e) {
            return DataType.Continuous;
        }
    }

    /**
     * This class extends ByteArrayOutputStream and adds buffering and listening functionality. It overrides the write
     * methods to capture the data being written and processes it when a newline character is encountered.
     */
    public static class BufferedListeningByteArrayOutputStream extends ByteArrayOutputStream {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void write(int b) {
            // Convert byte to string and add to buffer
            String s = new String(new byte[]{(byte) b}, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if a newline character is found
            if (s.contains("\n")) {
                super.write(buffer.toString().getBytes(StandardCharsets.UTF_8), 0, buffer.length());
                buffer.setLength(0); // Clear the buffer for next data
            }
        }

        @Override
        public void write(byte @NotNull [] b, int off, int len) {
            // Convert the byte array to string and add to buffer
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if a newline character is found
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
         * effect, where if the true total effect falls between the minimum and maximum total effect, zero is reported.
         * If the true model is not given, the last two columns are not included.
         */
        private final String[] columnNames = {"Index", "Column Name", "Description", "Selected"};
        /**
         * The data for the table.
         */
        private final Object[][] data;
        private final List<GridSearchModel.MyTableColumn> allTableColumns;
        private final Set<GridSearchModel.MyTableColumn> selectedTableColumns;
        private JTable tableRef;

        /**
         * Constructs a new table estModel for the results of the IDA check.
         */
        public TableColumnSelectionModel(List<GridSearchModel.MyTableColumn> allTableColumns, Set<GridSearchModel.MyTableColumn> selectedTableColumns) {
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
                GridSearchModel.MyTableColumn tableColumn = allTableColumns.get(i);
                this.data[i][0] = i + 1; // 1-based index (not 0-based index)
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
            if (col == 3) {
                if (tableRef == null || tableRef.getSelectionModel() == null) {
                    return "";
                }

                int index = tableRef.convertRowIndexToView(row); // Convert the row index to the model index (in case the table is sorted)

                if (index < 0 || index >= allTableColumns.size()) {
                    return "";
                }

                boolean rowSelected = tableRef.getSelectionModel().isSelectedIndex(index);

                if (rowSelected) {
                    selectedTableColumns.add(allTableColumns.get(((Integer) data[row][0]) - 1));
                } else {
                    selectedTableColumns.remove(allTableColumns.get(((Integer) data[row][0]) - 1));
                }

                return rowSelected ? "Selected" : "";
            } else {
                return this.data[row][col];
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

        public Set<GridSearchModel.MyTableColumn> getSelectedTableColumns() {
            return selectedTableColumns;
        }

        public void setTableRef(JTable tableRef) {
            this.tableRef = tableRef;
        }

        public GridSearchModel.MyTableColumn getMyTableColumn(int row) {
            return allTableColumns.get(row);
        }

        public void selectRow(int row) {
            tableRef.getSelectionModel().addSelectionInterval(row, row);
        }
    }

    public static class TextAreaOutputStream extends OutputStream {
        private final JTextArea textArea;
        private final StringBuilder sb = new StringBuilder();

        public TextAreaOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\r') return; // Ignore carriage return on Windows

            if (b == '\n') {
                final String text = sb + "\n";
                SwingUtilities.invokeLater(() -> textArea.append(text));
                sb.setLength(0); // Reset StringBuilder
            } else {
                sb.append((char) b);
            }
        }
    }
}

