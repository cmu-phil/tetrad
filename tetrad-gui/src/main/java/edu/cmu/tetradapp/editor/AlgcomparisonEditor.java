package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataType;
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
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The AlgcomparisonEditor class is a JPanel that allows users to compare and analyze different algorithms and
 * simulation methods. It provides a graphical user interface to select and configure various simulation methods,
 * algorithms, and statistics.
 * <p>
 * The class contains the following fields: - simulationChoiceTextArea: JTextArea to display the selected simulation
 * method. - algorithmChoiceTextArea: JTextArea to display the selected algorithm. - statisticsChoiceTextArea: JTextArea
 * to display the selected statistics. - comparisonTextArea: JTextArea to display the comparison comparison. -
 * helpChoiceTextArea: JTextArea to display help information. - addSimulation: Button to add a new simulation. - model:
 * An AlgcomparisonModel object that holds the data and handles the comparison logic. - independenceTestDropdown:
 * Dropdown to select the independence test method.
 * <p>
 * The AlgcomparisonEditor class provides the following methods: - AlgcomparisonEditor: The constructor for the
 * AlgcomparisonEditor class. It creates a new Comparison object, populates the lists of simulation methods, algorithms,
 * and statistics , and initializes the GUI components. It sets up an ActionListener for the "Run Comparison" button to
 * trigger the comparison. - getSimulation: A utility method to retrieve a specific simulation object using the given
 * graph and simulation class names. - addTestAndScoreDropdowns: A utility method to add the independence test and score
 * dropdowns to the provided Box container. - getParameterText: A utility method to retrieve the parameter text to be
 * displayed for a given set of parameter names and their values. - addAddSimulationListener: A method to add an
 * ActionListener to handle adding a new simulation with the given parameters. - setSimulationText: A method to update
 * the simulationChoiceTextArea with the selected simulation method. - getSimulationParameterText: A method to retrieve
 * the simulation parameters information as text. - getAlgorithmParameterText: A method to retrieve the algorithm
 * parameters information as text. - setAlgorithmText: A method to update the algorithmChoiceTextArea with the selected
 * algorithm. - setStatisticsText: A method to update the statisticsChoiceTextArea with the selected statistics. -
 * setcomparisonText: A method to update the comparisonTextArea with the comparison comparison. - setHelpText: A method
 * to update the helpChoiceTextArea with help information.
 * <p>
 * The AlgcomparisonEditor class extends javax.swing.JPanel.
 */
public class AlgcomparisonEditor extends JPanel {

    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    private static JComboBox<IndependenceTestModel> indTestComboBox;
    private static JComboBox<ScoreModel> scoreModelComboBox;
    private final JTextArea simulationChoiceTextArea;
    private final JTextArea algorithChoiceTextArea;
    private final JTextArea statisticsChoiceTextArea;
    private final JTextArea comparisonTextArea;
    private final JTextArea helpChoiceTextArea;
    private final JButton addSimulation;
    private final JButton addAlgorithm;
    private final JButton addStatistics;
    /**
     * The AlgcomparisonModel class represents a model used in an algorithm comparison application. It contains methods
     * and properties related to the comparison of algorithms.
     */
    private final AlgcomparisonModel model;
    private final Box parameterBox = Box.createVerticalBox();


    /**
     * The constructor for the AlgcomparisonEditor class. The constructor will create a new Comparison object and pass
     * in the PrintStream "localOut" to the constructor. The constructor will then use the Reflection API to populate
     * the lists of simulation methods, algorithms, and statistics, and pass these to the Comparison class through its
     * constructor. The constructor will then create a new JFrame and add the JLists, ParameterPanel, and JTextArea to
     * the JFrame. The constructor will then add an ActionListener to the "Run Comparison" button that will call the
     * runComparison() method in the Comparison class. The constructor will then set the JFrame to be visible.
     */
    public AlgcomparisonEditor(AlgcomparisonModel model) {
        this.model = model;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.BOTTOM);

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
        addAddSimulationListener(model.getParameters());

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
                        createParameterComponents(params, model.getParameters()));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                this.parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
            }

            this.parameterBox.validate();
            this.parameterBox.repaint();

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Simulation Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(new PaddingPanel(this.parameterBox), BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton("Done");

            doneButton.addActionListener(e1 -> {
//                Parameters parameters1 = ParameterComponents.toParameters(paramBoxes);
//                model.setParameters(parameters1);
                setSimulationText();
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

        simulationSelectionBox.add(addSimulation);
        simulationSelectionBox.add(removeLastSimulation);
        simulationSelectionBox.add(editSimulationParameters);
        simulationSelectionBox.add(Box.createHorizontalGlue());
        simulationChoice.add(simulationSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Simulations", simulationChoice);

        algorithChoiceTextArea = new JTextArea();
        algorithChoiceTextArea.setLineWrap(true);
        algorithChoiceTextArea.setWrapStyleWord(true);
        algorithChoiceTextArea.setEditable(false);
        setAlgorithmText();

        Box algorithSelectionBox = Box.createHorizontalBox();
        algorithSelectionBox.add(Box.createHorizontalGlue());

        addAlgorithm = new JButton("Add Algorithm");
        addAddAlgorithmListener(model.getParameters());

        JButton removeLastAlgorithm = new JButton("Remove Last Algorithm");
        removeLastAlgorithm.addActionListener(e -> {
            model.removeLastAlgorithm();
            setAlgorithmText();
            setComparisonText();
        });

        JButton editAlgorithmParameters = new JButton("Edit Parameters");

        editAlgorithmParameters.addActionListener(e -> {
            List<Algorithm> algorithm = model.getSelectedAlgorithms().getAlgorithms();
            Set<String> params = getAllAlgorithmParameters(algorithm);

            this.parameterBox.removeAll();

            if (params.isEmpty()) {
                this.parameterBox.add(NO_PARAM_LBL, BorderLayout.NORTH);
            } else {
                Box parameters = Box.createVerticalBox();
                Box[] paramBoxes = ParameterComponents.toArray(
                        createParameterComponents(params, model.getParameters()));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                this.parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
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
//                Parameters parameters1 = ParameterComponents.toParameters(paramBoxes);
//                model.setParameters(parameters1);
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
        algorithmChoice.add(new JScrollPane(algorithChoiceTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), BorderLayout.CENTER);
        algorithmChoice.add(algorithSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Algorithms", algorithmChoice);

        statisticsChoiceTextArea = new JTextArea();
        statisticsChoiceTextArea.setLineWrap(true);
        statisticsChoiceTextArea.setWrapStyleWord(true);
        statisticsChoiceTextArea.setEditable(false);
        setStatisticsText();

        Box statisticsSelectionBox = Box.createHorizontalBox();
        statisticsSelectionBox.add(Box.createHorizontalGlue());

        addStatistics = new JButton("Add Statistic(s)");
        addAddStatisticsListener();

        JButton removeLastStatistic = new JButton("Remove Last Statistic");
        removeLastStatistic.addActionListener(e -> {
            model.removeLastStatistic();
            setStatisticsText();
            setComparisonText();
        });

        statisticsSelectionBox.add(addStatistics);
        statisticsSelectionBox.add(removeLastStatistic);
        statisticsSelectionBox.add(Box.createHorizontalGlue());

        JPanel statisticsChoice = new JPanel();
        statisticsChoice.setLayout(new BorderLayout());
        statisticsChoice.add(statisticsChoiceTextArea, BorderLayout.CENTER);
        statisticsChoice.add(statisticsSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Statistics", statisticsChoice);

        comparisonTextArea = new JTextArea();
        comparisonTextArea.setLineWrap(false);
        comparisonTextArea.setWrapStyleWord(false);
        comparisonTextArea.setEditable(false);
        comparisonTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        setComparisonText();

        JButton runComparison = new JButton("Run Comparison");

        runComparison.addActionListener(e -> {

            class MyWatchedProcess extends WatchedProcess {

                public void watch() throws InterruptedException {
                    ByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
                    java.io.PrintStream ps = new java.io.PrintStream(baos);
                    model.runComparison(ps);
                    ps.flush();
                    comparisonTextArea.setText(baos.toString());
                }
            }

            new MyWatchedProcess();
        });

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

        JPanel xmlPanel = new JPanel();
        xmlPanel.setLayout(new BorderLayout());
        JTextArea xmlTextArea = new JTextArea();
        xmlTextArea.setLineWrap(false);
        xmlTextArea.setWrapStyleWord(false);
        xmlTextArea.setEditable(false);
        xmlTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        xmlTextArea.setText(getXmlText());
        xmlPanel.add(new JScrollPane(xmlTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        JButton loadXml = new JButton("Load XML");
        JButton saveXml = new JButton("Save XML");

        loadXml.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "This will load and XML file and parse it to set the" + " configuration of this tool.");
            setSimulationText();
            setAlgorithmText();
            setStatisticsText();
        });

        saveXml.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "This will save the XML file shown in this panel.");
            setSimulationText();
            setAlgorithmText();
            setStatisticsText();
        });

        Box xmlSelectionBox = Box.createHorizontalBox();
        xmlSelectionBox.add(Box.createHorizontalGlue());
        xmlSelectionBox.add(loadXml);
        xmlSelectionBox.add(saveXml);
        xmlSelectionBox.add(Box.createHorizontalGlue());

        xmlPanel.add(xmlSelectionBox, BorderLayout.SOUTH);
//        tabbedPane.addTab("XML", xmlPanel);


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

        tabbedPane.setPreferredSize(new Dimension(800, 400));

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    public static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescs.get(e)),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    public static Box[] toArray(Map<String, Box> parameterComponents) {
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();

        List<Box> boolComps = new LinkedList<>();
        List<Box> otherComps = new LinkedList<>();
        parameterComponents.forEach((k, v) -> {
            if (paramDescs.get(k).getDefaultValue() instanceof Boolean) {
                boolComps.add(v);
            } else {
                otherComps.add(v);
            }
        });

        return Stream.concat(otherComps.stream(), boolComps.stream())
                .toArray(Box[]::new);
    }

    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc) {
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

    public static ListDoubleTextField getListDoubleTextField(String parameter, Parameters parameters,
                                                             Double[] defaultValues, double lowerBound, double upperBound) {
        ListDoubleTextField field = new ListDoubleTextField(defaultValues,
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);


        field.setFilter((values, oldValues) -> {
            for (int i = 0; i < values.length; i++) {
                if (values[i] < lowerBound) {
                    return oldValues;
                }

                if (values[i] > upperBound) {
                    return oldValues;
                }
            }

            // Check if the values have changed
            if (Arrays.equals(values, field.getValues())) {
                return oldValues;
            }

            try {
                parameters.set(parameter, (Object[]) values);
            } catch (Exception e) {
                // Ignore.
            }

            return values;
        });

        return field;
    }

    public static ListIntTextField getListIntTextField(String parameter, Parameters parameters,
                                                       Integer[] defaultValues, double lowerBound, double upperBound) {
        ListIntTextField field = new ListIntTextField(defaultValues, 8);

        field.setFilter((values, oldValues) -> {
            for (int i = 0; i < values.length; i++) {
                if (values[i] < lowerBound) {
                    return oldValues;
                }

                if (values[i] > upperBound) {
                    return oldValues;
                }
            }

            // Check if the values have changed
            if (Arrays.equals(values, field.getValues())) {
                return oldValues;
            }

//            if (value < lowerBound) {
//                return oldValue;
//            }
//
//            if (value > upperBound) {
//                return oldValue;
//            }

            try {
                parameters.set(parameter, (Object[]) values);
            } catch (Exception e) {
                // Ignore.
            }

            return values;
        });

        return field;
    }

    public static ListLongTextField getListLongTextField(String parameter, Parameters parameters,
                                                         Long[] defaultValues, double lowerBound, double upperBound) {
        ListLongTextField field = new ListLongTextField(defaultValues, 8);


        field.setFilter((values, oldValues) -> {
            for (int i = 0; i < values.length; i++) {
                if (values[i] < lowerBound) {
                    return oldValues;
                }

                if (values[i] > upperBound) {
                    return oldValues;
                }
            }

            // Check if the values have changed
            if (Arrays.equals(values, field.getValues())) {
                return oldValues;
            }

//            if (value < lowerBound) {
//                return oldValue;
//            }
//
//            if (value > upperBound) {
//                return oldValue;
//            }

            try {
                parameters.set(parameter, (Object[]) values);
            } catch (Exception e) {
                // Ignore.
            }

            return values;
        });

        return field;
    }


    /**
     * <p>getDoubleField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a double
     * @param lowerBound   a double
     * @param upperBound   a double
     * @return a {@link edu.cmu.tetradapp.util.DoubleTextField} object
     */
    public static DoubleTextField getDoubleField(String parameter, Parameters parameters,
                                                 double defaultValue, double lowerBound, double upperBound) {
        DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

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
     * <p>getIntTextField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a int
     * @param lowerBound   a double
     * @param upperBound   a double
     * @return a {@link edu.cmu.tetradapp.util.IntTextField} object
     */
    public static IntTextField getIntTextField(String parameter, Parameters parameters,
                                               int defaultValue, double lowerBound, double upperBound) {
        IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 8);

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
     * <p>getLongTextField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a long
     * @param lowerBound   a double
     * @param upperBound   a double
     * @return a {@link edu.cmu.tetradapp.util.LongTextField} object
     */
    public static LongTextField getLongTextField(String parameter, Parameters parameters,
                                                 long defaultValue, double lowerBound, double upperBound) {
        LongTextField field = new LongTextField(parameters.getLong(parameter, defaultValue), 10);

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
     * <p>getBooleanSelectionBox.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a boolean
     * @return a {@link javax.swing.Box} object
     */
    public static Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean defaultValue) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        // Button group to ensure only only one option can be selected
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
     * <p>getStringField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetradapp.util.StringTextField} object
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


    @NotNull
    private static String getXmlText() {
        return """
                ** This is placeholder text **
                                
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <comparison>
                    <compareBy>
                        <search>
                            <simulations>
                                <simulation source="directory">
                                    <path>src/test/resources/data/simulation</path>
                                </simulation>
                                <simulation source="generate">
                                    <graphtype>RandomForward</graphtype>
                                    <modeltype>SemSimulation</modeltype>
                                </simulation>
                            </simulations>
                            <algorithms>
                                <algorithm name="gfci">
                                    <test>fisher-z-test</test>
                                    <score>sem-bic-score</score>
                                </algorithm>
                                <algorithm name="fges">
                                    <score>sem-bic-score</score>
                                </algorithm>
                            </algorithms>
                            <parameters>
                                <parameter name="numRuns">1</parameter>
                                <parameter name="numMeasures">4,6</parameter>
                                <parameter name="avgDegree">4</parameter>
                            </parameters>
                        </search>
                    </compareBy>
                    <statistics>
                        <statistic>adjacencyPrecision</statistic>
                        <statistic>arrowheadRecall</statistic>
                        <statistic>adjacencyRecall</statistic>
                    </statistics>
                    <properties>
                        <property name="showAlgorithmIndices">true</property>
                        <property name="showSimulationIndices">true</property>
                        <property name="sortByUtility">true</property>
                        <property name="showUtilities">true</property>
                        <property name="saveSearchGraphs">true</property>
                        <property name="tabDelimitedTables">true</property>
                    </properties>
                </comparison>""";
    }

    @NotNull
    private static edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation(Class<? extends edu.cmu.tetrad.algcomparison.graph.RandomGraph> graphClazz, Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> simulationClazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        RandomGraph randomGraph = graphClazz.getConstructor().newInstance();
        return simulationClazz.getConstructor(RandomGraph.class).newInstance(randomGraph);
    }

    private static void addTestAndScoreDropdowns(Box vert1) {
        IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
        List<IndependenceTestModel> models = independenceTestModels.getModels();

        indTestComboBox = new JComboBox<>();

        for (IndependenceTestModel model : models) {
            indTestComboBox.addItem(model);
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

        Box horiz5 = Box.createHorizontalBox();
        horiz5.add(new JLabel("Choose a score:"));
        horiz5.add(Box.createHorizontalGlue());
        horiz5.add(scoreModelComboBox);
        vert1.add(horiz5);
    }

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

//                sb.append(nf.format((double) values[i]));
            }

            paramText.append("\n\n- ").append(name).append(" = ").append(sb);
            paramText.append("\n").append(description.getShortDescription());
            paramText.append(". ").append(description.getLongDescription());
        }

        return paramText.toString();
    }

    @NotNull
    private static Set<String> getAllSimulationParameters(List<Simulation> simulations) {
        Set<String> paramNamesSet = new HashSet<>();

        for (Simulation simulation : simulations) {
            paramNamesSet.addAll(simulation.getParameters());
        }

        return paramNamesSet;
    }

    @NotNull
    private static Set<String> getAllAlgorithmParameters(List<Algorithm> algorithm) {
        Set<String> paramNamesSet = new HashSet<>();

        for (Algorithm simulation : algorithm) {
            paramNamesSet.addAll(simulation.getParameters());
        }

        return paramNamesSet;
    }

    private void addAddSimulationListener(Parameters parameters) {
        addSimulation.addActionListener(e -> {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            Box vert1 = Box.createVerticalBox();
            Box horiz2 = Box.createHorizontalBox();
            horiz2.add(new JLabel("Choose a graph type:"));
            horiz2.add(Box.createHorizontalGlue());
            JComboBox<String> graphsDropdown = new JComboBox<>();

            Arrays.stream(ParameterTab.GRAPH_TYPE_ITEMS).forEach(graphsDropdown::addItem);
            graphsDropdown.setMaximumSize(graphsDropdown.getPreferredSize());
            graphsDropdown.setSelectedItem(parameters.getString("graphsDropdownPreference", ParameterTab.GRAPH_TYPE_ITEMS[0]));
//            graphsDropdown.addActionListener(e -> refreshParameters());

            horiz2.add(graphsDropdown);
            vert1.add(horiz2);
            Box horiz3 = Box.createHorizontalBox();
            horiz3.add(new JLabel("Choose a simulation type:"));
            horiz3.add(Box.createHorizontalGlue());

            JComboBox<String> simulationsDropdown = new JComboBox<>();

            Arrays.stream(ParameterTab.MODEL_TYPE_ITEMS).forEach(simulationsDropdown::addItem);
            simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());

            horiz3.add(simulationsDropdown);
            vert1.add(horiz3);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton addButton = new JButton("Add");
            JButton cancelButton = new JButton("Cancel");

            // Add action listeners for the buttons
            addButton.addActionListener(e1 -> {
                String graphString = (String) graphsDropdown.getSelectedItem();
                String simulationString = (String) simulationsDropdown.getSelectedItem();

                List<String> graphTypeStrings = Arrays.asList(ParameterTab.GRAPH_TYPE_ITEMS);

                Class<? extends RandomGraph> graphClazz = switch (graphTypeStrings.indexOf(graphString)) {
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

    private void addAddAlgorithmListener(Parameters parameters) {
        addAlgorithm.addActionListener(e -> {
            DefaultListModel<AlgorithmModel> algoModels = new DefaultListModel<>();
            AlgorithmModels algorithmModels = AlgorithmModels.getInstance();
            List<AlgorithmModel> algorithmModels1 = algorithmModels.getModels(DataType.Continuous, false);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            JComboBox<AlgorithmModel> algorithmDropdown = new JComboBox<>();

            for (AlgorithmModel model : algorithmModels1) {
                algorithmDropdown.addItem(model);
            }

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

                IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
                List<IndependenceTestModel> models = independenceTestModels.getModels();

                IndependenceTestModel testModel = (IndependenceTestModel) indTestComboBox.getSelectedItem();
                AnnotatedClass<TestOfIndependence> test = testModel.getIndependenceTest();

                ScoreModel scoreModel = (ScoreModel) scoreModelComboBox.getSelectedItem();
                AnnotatedClass<Score> score = scoreModel.getScore();

                IndependenceWrapper independenceWrapper;
                ScoreWrapper scoreWrapper;

                try {
                    independenceWrapper = (IndependenceWrapper) test.clazz().getConstructor().newInstance();
                    scoreWrapper = (ScoreWrapper) score.clazz().getConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    throw new RuntimeException(ex);
                }

                try {
                    Algorithm algorithmImpl = (Algorithm) algorithm.getConstructor().newInstance();

                    if (algorithmImpl instanceof TakesIndependenceWrapper) {
                        ((TakesIndependenceWrapper) algorithmImpl).setIndependenceWrapper(independenceWrapper);
                    }

                    if (algorithmImpl instanceof UsesScoreWrapper) {
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

    private void addAddStatisticsListener() {
        addStatistics.addActionListener(e -> {

            List<Class<? extends Statistic>> statisticClasses = model.getStatisticsClasses();

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            List<String> statisticsStrings = new ArrayList<>();
            Map<String, Statistic> statisticMap = new HashMap<>();

            for (Class<? extends Statistic> statisticClass : statisticClasses) {
                try {
                    Statistic statistic = statisticClass.getConstructor().newInstance();
                    statisticsStrings.add(statistic.getAbbreviation());
                    statisticMap.put(statistic.getAbbreviation(), statistic);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    ex.printStackTrace();
                }
            }

            Collections.sort(statisticsStrings);
            String[] _statistics = statisticsStrings.toArray(new String[0]);
            JList<String> statisticsList = new JList<>(_statistics);
            statisticsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            JScrollPane scrollPane = new JScrollPane(statisticsList);
            scrollPane.setPreferredSize(new Dimension(100, 200));

            System.out.println(statisticsStrings);

            JButton selectDefault = new JButton("Select Defaults");

            List<String> defaults = Arrays.asList("AP", "AR", "AHP", "AHR", "AHPC", "AHRC");

            selectDefault.addActionListener(e13 -> {
                for (int i = 0; i < _statistics.length; i++) {
                    if (defaults.contains(_statistics[i])) {
                        statisticsList.addSelectionInterval(i, i);
                    }
                }
            });

            Box vert1 = Box.createVerticalBox();
            vert1.add(new JLabel("Choose an statistic:"));
            vert1.add(Box.createVerticalStrut(5));
            vert1.add(scrollPane);
            vert1.add(Box.createVerticalStrut(10));
            vert1.add(selectDefault);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Statistic", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(panel, BorderLayout.CENTER);

            // Create a panel for the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton addButton = new JButton("Add");
            JButton cancelButton = new JButton("Cancel");

            // Add action listeners for the buttons
            addButton.addActionListener(e1 -> {
                List<String> valuesList = statisticsList.getSelectedValuesList();

                for (String value : valuesList) {
                    model.addStatistic(statisticMap.get(value));
                }

                setStatisticsText();
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

    @NotNull
    private String getSimulationParameterText() {
        Parameters parameters = model.getParameters();

        List<Simulation> simulations = model.getSelectedSimulations().getSimulations();
        Set<String> paramNamesSet = getAllSimulationParameters(simulations);
        StringBuilder paramText;

        if (simulations.size() == 1) {
            paramText = new StringBuilder("\nParameter choices for this simulation:");
        } else {
            paramText = new StringBuilder("\nParameter choices for all simulations:");
        }

        paramText.append(getParameterText(paramNamesSet, parameters));
        return paramText.toString();
    }

    private String getAlgorithmParameterText() {
        Parameters parameters = model.getParameters();
        List<Algorithm> algorithm = model.getSelectedAlgorithms().getAlgorithms();
        Set<String> paramNamesSet = getAllAlgorithmParameters(algorithm);
        StringBuilder paramText;

        if (algorithm.size() == 1) {
            paramText = new StringBuilder("\nParameter choices for this algorithm:");
        } else {
            paramText = new StringBuilder("\nParameter choices for all algorithms:");
        }

        paramText.append(getParameterText(paramNamesSet, parameters));
        return paramText.toString();
    }

    private void setAlgorithmText() {
        algorithChoiceTextArea.setText("");

        Algorithms selectedAlgorithms = model.getSelectedAlgorithms();
        List<Algorithm> algorithms = selectedAlgorithms.getAlgorithms();

        if (algorithms.isEmpty()) {
            algorithChoiceTextArea.append("""
                     ** No algorithm have been selected. Please select at least one algorithm using the Add Algorithm button below. **
                    """);
            return;
        } else if (algorithms.size() == 1) {
            algorithChoiceTextArea.setText("""
                    The following algorithm has been selected. This algorithm will be run with the selected simulations.
                                        
                    """);

            Algorithm algorithm = algorithms.get(0);
            algorithChoiceTextArea.append("Selected algorithm: " + algorithm.getDescription() + "\n");

            if (algorithm instanceof TakesIndependenceWrapper) {
                algorithChoiceTextArea.append("Selected independence test = " + ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getDescription() + "\n");
            }

            if (algorithm instanceof UsesScoreWrapper) {
                algorithChoiceTextArea.append("Selected score = " + ((UsesScoreWrapper) algorithm).getScoreWrapper().getDescription() + "\n");
            }

        } else {
            algorithChoiceTextArea.setText("""
                    The following algorithms have been selected. These algorithms will be run with the selected simulations.
                    """);
            for (int i = 0; i < algorithms.size(); i++) {
                Algorithm algorithm = algorithms.get(i);
                algorithChoiceTextArea.append("\nAlgorithm #" + (i + 1) + ". " + algorithm.getDescription() + "\n");

                if (algorithm instanceof TakesIndependenceWrapper) {
                    algorithChoiceTextArea.append("Selected independence test = " + ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getDescription() + "\n");
                }

                if (algorithm instanceof UsesScoreWrapper) {
                    algorithChoiceTextArea.append("Selected score = " + ((UsesScoreWrapper) algorithm).getScoreWrapper().getDescription() + "\n");
                }
            }
        }

        algorithChoiceTextArea.append(getAlgorithmParameterText());
        algorithChoiceTextArea.setCaretPosition(0);
    }

    private void setStatisticsText() {
        statisticsChoiceTextArea.setText("");

        Statistics selectedStatistics = model.getSelectedStatistics();
        List<Statistic> statistics = selectedStatistics.getStatistics();

        if (statistics.isEmpty()) {
            statisticsChoiceTextArea.append("""
                     ** No statistics have been selected. Please select at least one statistic using the Add Statistic(s) button below. **
                    """);
            return;
        } else if (statistics.size() == 1) {
            statisticsChoiceTextArea.setText("""
                    The following statistics has been selected. The comparison table will include these statistics as columns in the table.
                    """);
        } else {
            statisticsChoiceTextArea.setText("""
                    The following simulations have been selected. The comparison table will include these statistics as columns in the table.
                    """);
        }

        for (int i = 0; i < statistics.size(); i++) {
            Statistic statistic = statistics.get(i);
            Class<? extends Statistic> statisticClass = statistic.getClass();

            try {
                Statistic statistic1 = statisticClass.getConstructor().newInstance();
                statisticsChoiceTextArea.append("\n\n" + (i + 1) + ". " + statistic1.getAbbreviation() + " (" + statistic1.getDescription() + ")");

            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        statisticsChoiceTextArea.setCaretPosition(0);
    }

    private void setComparisonText() {
        if (model.getSelectedSimulations().getSimulations().isEmpty() || model.getSelectedAlgorithms().getAlgorithms().isEmpty()
            || model.getSelectedStatistics().getStatistics().isEmpty()) {
            comparisonTextArea.setText(
                    """
                            ** You have made an empty selection; look back at the Simulation, Algorithm, and Statistics tabs **
                            """);
        } else {
            comparisonTextArea.setText
                    ("""
                            ** Your selection is non-empty, but you have not yet run a comparison for it **
                            """);
        }
    }

    private void setHelpText() {
        helpChoiceTextArea.setText("""
                This tool may be used to do a comparison of multiple algorithms (in Tetrad for now) for a range of simulations types, statistics, and parameter settings.

                To run a comparison, select one or more simulations, one or more algorithms, and one or more statistics. Then in the Comparison tab, click the "Run Comparison" button.

                The comparison will be displayed in the "comparison" tab.

                Not all combinations you can select in this tool are stellar ideas; you may need to experiment. One problem is that you may select too many combinations of parameters. We will run your request in a thread with a stop button so you can gracefully exit and try a smaller number of combinations. Also, some of the algorithms may be slow. In fact, it may not make sense to run larger comparisons in this interface at all; you may with to use the command line tool or Python to do it.

                In the Simulation tab, simulations may be added by clicking the Add Simulation button. The last one in the list may be removed by clicking the Remove Last Simulation button.

                A simulation selection requires one to select a graph type and a simulation type.

                This selection implies a list of parameters for all of the simulations. These parameters may be edited by clicking the Edit Parameters button. Note that parameters may be given a list of comma-separated values; each combination of parameters will be explored in the comparison.

                The Algorithm tab and Statistics tab work similarly. An algorithm selection requires one to select an algorithm type and then an independence test and/or a score depending on the requirements of the algorithm.

                For the Algorithm tab, once one has selected all algorithms, one may edit the parameters for these algorithms.

                In the Comparison tab, there are some properties of the comparison itself; these may be edited by clicking the Edit Comparison Parameters button.

                The XML tab allows one to save and load XML specifications of the information in this tool, suitable for use with the command-line Algcomparison tool. These may also be used to save the setup for this tool to a hard drive and load it in later for further exploration. This XML file will be saved along with full results to the user's hard drive.

                In further work, we plan to allow the Simulation and Search boxes in the Tetrad UI to be made parents of this algorithm comparison box. Information in these boxes, such as simulation selections and parameters and algorithm selections and parameters, may be used to set up a comparison, though it will still be possible to edit the comparison after this information has been gleaned.

                Also, in future work, we may include a method to insert results from other tools into the tables generated here.
                                """);
    }

    public static class BufferedListeningByteArrayOutputStream extends ByteArrayOutputStream {
        private final StringBuilder buffer = new StringBuilder();

//        public static void main(String[] args) {
//            BufferedListeningByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
//            PrintStream ps = new PrintStream(baos);
//
//            // Example usage
//            ps.println("Hello, world!");
//            ps.printf("Pi is approximately %.2f%n", Math.PI);
//
//            ps.close();
//        }

        @Override
        public void write(int b) {
            super.write(b);
            // Convert single byte to character and add to buffer
            char c = (char) b;
            buffer.append(c);
            if (c == '\n') {
                processBuffer();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            // Convert bytes to string and add to buffer
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if newline character is found
            if (s.contains("\n")) {
                processBuffer();
            }
        }

        private void processBuffer() {
            // Process the buffered data (print it in this case)
            System.out.print("Buffered data: " + buffer);
            buffer.setLength(0); // Clear the buffer for next data
        }
    }

}
