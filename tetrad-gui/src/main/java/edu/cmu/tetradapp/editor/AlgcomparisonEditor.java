package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc;
import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.model.AlgcomparisonModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.ui.model.ScoreModel;
import edu.cmu.tetradapp.ui.model.ScoreModels;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

/**
 * The AlgcomparisonEditor class is a JPanel that allows users to compare and analyze different algorithms and
 * simulation methods. It provides a graphical user interface to select and configure various simulation methods,
 * algorithms, and statistics.
 * <p>
 * The class contains the following fields: - simulationChoiceTextArea: JTextArea to display the selected simulation
 * method. - algorithmChoiceTextArea: JTextArea to display the selected algorithm. - statisticsChoiceTextArea: JTextArea
 * to display the selected statistics. - resultsTextArea: JTextArea to display the comparison results. -
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
 * setResultsText: A method to update the resultsTextArea with the comparison results. - setHelpText: A method to update
 * the helpChoiceTextArea with help information.
 * <p>
 * The AlgcomparisonEditor class extends javax.swing.JPanel.
 */
public class AlgcomparisonEditor extends JPanel {

    private final JTextArea simulationChoiceTextArea;
    private final JTextArea algorithChoiceTextArea;
    private final JTextArea statisticsChoiceTextArea;
    private final JTextArea resultsTextArea;
    private final JTextArea helpChoiceTextArea;
    private final JButton addSimulation;
    private final JButton addAlgorithm;
    /**
     * The AlgcomparisonModel class represents a model used in an algorithm comparison application. It contains methods
     * and properties related to the comparison of algorithms.
     */
    AlgcomparisonModel model;
    private JComboBox<Object> independenceTestDropdown;

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
        tabbedPane.setTabPlacement(JTabbedPane.TOP);

        simulationChoiceTextArea = new JTextArea();
        simulationChoiceTextArea.setLineWrap(true);
        simulationChoiceTextArea.setWrapStyleWord(true);
        simulationChoiceTextArea.setEditable(false);

        setSimulationText();

        JPanel simulationChoice = new JPanel();
        simulationChoice.setLayout(new BorderLayout());
        simulationChoice.add(new JScrollPane(simulationChoiceTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        Box simulationSelectionBox = Box.createHorizontalBox();
        simulationSelectionBox.add(Box.createHorizontalGlue());

        addSimulation = new JButton("Add Simulation");
        addAddSimulationListener(model.getParameters());

        JButton removeLastSimulation = new JButton("Remove Last Simulation");
        removeLastSimulation.addActionListener(e -> {
            model.removeLastSimulation();
            setSimulationText();
        });

        simulationSelectionBox.add(addSimulation);
        simulationSelectionBox.add(removeLastSimulation);
        simulationSelectionBox.add(new JButton("Edit Parameters"));
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
        });

        JButton editAlgorithmParameters = new JButton("Edit Parameters");

        algorithSelectionBox.add(addAlgorithm);
        algorithSelectionBox.add(removeLastAlgorithm);
        algorithSelectionBox.add(editAlgorithmParameters);
        algorithSelectionBox.add(Box.createHorizontalGlue());

        JPanel algorithmChoice = new JPanel();
        algorithmChoice.setLayout(new BorderLayout());
        algorithmChoice.add(new JScrollPane(algorithChoiceTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), BorderLayout.CENTER);
        algorithmChoice.add(algorithSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Algorithms", algorithmChoice);

        statisticsChoiceTextArea = new JTextArea();
        statisticsChoiceTextArea.setLineWrap(true);
        statisticsChoiceTextArea.setWrapStyleWord(true);
        statisticsChoiceTextArea.setEditable(false);
        setStatisticsText();

        Box statisticsSelectionBox = Box.createHorizontalBox();
        statisticsSelectionBox.add(Box.createHorizontalGlue());
        statisticsSelectionBox.add(new JButton("Add Statistic(s)"));
        statisticsSelectionBox.add(new JButton("Remove Last Statistic"));
        statisticsSelectionBox.add(Box.createHorizontalGlue());

        JPanel statisticsChoice = new JPanel();
        statisticsChoice.setLayout(new BorderLayout());
        statisticsChoice.add(statisticsChoiceTextArea, BorderLayout.CENTER);
        statisticsChoice.add(statisticsSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Statistics", statisticsChoice);

        resultsTextArea = new JTextArea();
        resultsTextArea.setLineWrap(false);
        resultsTextArea.setWrapStyleWord(false);
        resultsTextArea.setEditable(false);

        setResultsText();

        Box resultsSelectionBox = Box.createHorizontalBox();
        resultsSelectionBox.add(Box.createHorizontalGlue());
        resultsSelectionBox.add(new JButton("Run Comparison"));
        resultsSelectionBox.add(Box.createHorizontalGlue());

        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BorderLayout());
        resultsPanel.add(new JScrollPane(resultsTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        resultsPanel.add(resultsSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Results", resultsPanel);

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
        helpSelectionBox.add(new JCheckBox("Show Algcomparison XML"));
        helpSelectionBox.add(Box.createHorizontalGlue());

        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BorderLayout());
        helpPanel.add(new JScrollPane(helpChoiceTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        helpPanel.add(helpSelectionBox, BorderLayout.SOUTH);

        tabbedPane.addTab("Help", helpPanel);

        tabbedPane.setPreferredSize(new Dimension(800, 400));

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    @NotNull
    private static edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation(Class<? extends edu.cmu.tetrad.algcomparison.graph.RandomGraph> graphClazz,
                                                                                    Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> simulationClazz)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        RandomGraph randomGraph = graphClazz.getConstructor().newInstance();
        return simulationClazz.getConstructor(RandomGraph.class).newInstance(randomGraph);
    }

    private static void addTestAndScoreDropdowns(Box vert1) {
        IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
        List<IndependenceTestModel> models = independenceTestModels.getModels();

        JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();

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

        JComboBox<ScoreModel> scoreModelComboBox = new JComboBox<>();

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

        for (String name : paramNames) {
            ParamDescription description = paramDescriptions.get(name);
            paramText.append("\n\n- ").append(name).append(" = ").append(parameters.get(name));
            paramText.append("\n").append(description.getShortDescription());
            paramText.append("--that is, ").append(description.getLongDescription());
        }

        return paramText.toString();
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

//            String[] simulationItems = getSimulationItems();

            Arrays.stream(ParameterTab.MODEL_TYPE_ITEMS).forEach(simulationsDropdown::addItem);
            simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());
//            simulationsDropdown.setSelectedItem(
//                    simulation.getParams().getString("simulationsDropdownPreference", simulationItems[0]));
//            simulationsDropdown.addActionListener(e -> refreshParameters());

            horiz3.add(simulationsDropdown);
            vert1.add(horiz3);

//            addTestAndScoreDropdowns(vert1);

            panel.add(vert1, BorderLayout.NORTH);

            // Create the JDialog. Use the parent frame to make it modal.
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Simulation", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Your custom JPanel
            // Optionally set the preferred size of yourPanel here
            // yourPanel.setPreferredSize(new Dimension(200, 100));

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
            horiz3.add(new JLabel("Choose an algorithm:"));
            horiz3.add(Box.createHorizontalGlue());

            JComboBox<String> simulationsDropdown = new JComboBox<>();

            Arrays.stream(ParameterTab.MODEL_TYPE_ITEMS).forEach(simulationsDropdown::addItem);
            simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());

            horiz3.add(simulationsDropdown);
            vert1.add(horiz3);

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
        Set<String> paramNamesSet = new HashSet<>();

        for (Simulation simulation : simulations) {
            paramNamesSet.addAll(simulation.getParameters());
        }

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

        List<Algorithm> simulations = model.getSelectedAlgorithms().getAlgorithms();
        Set<String> paramNamesSet = new HashSet<>();

        for (Algorithm simulation : simulations) {
            paramNamesSet.addAll(simulation.getParameters());
        }

        StringBuilder paramText;

        if (simulations.size() == 1) {
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
            Class<? extends Algorithm> algorithmClass = algorithm.getClass();
            algorithChoiceTextArea.append("Selected algorithm = " + algorithmClass.getSimpleName() + "\n");
        } else {
            algorithChoiceTextArea.setText("""
                    The following simulations have been selected. These simulations will be run with the selected algorithms.
                    """);
            for (int i = 0; i < algorithms.size(); i++) {
                algorithChoiceTextArea.append("\n");
                algorithChoiceTextArea.append("Algorithm #" + (i + 1) + ":\n");
                algorithChoiceTextArea.append("Selected graph type = " + algorithms.get(i).getClass().getSimpleName() + "\n");
                algorithChoiceTextArea.append("Selected simulation type = " + algorithms.get(i).getClass().getSimpleName() + "\n");
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
                statisticsChoiceTextArea.append("\n" + (i + 1) + ". " + statistic1.getAbbreviation() + " (" + statistic1.getDescription() + ")");

            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        statisticsChoiceTextArea.append(getAlgorithmParameterText());
        statisticsChoiceTextArea.setCaretPosition(0);
    }

    private void setResultsText() {
        resultsTextArea.setText("""
                We have some massive and impressive results for you!
                                
                The results are as follows:
                                
                (Comparison output goes here)
                """);
    }

    private void setHelpText() {
        helpChoiceTextArea.setText("""
                This is some information about how to use the application.
                                
                To run a comparison, select a simulation, one or more algorithms, and one or more statistics.
                Then click the "Run Comparison" button.
                                
                The results will be displayed in the "Results" tab.
                                
                Here is some information on the graph type you have selected:
                                
                RandomForward:
                - This graph type generates a random graph with a forward edge.
                                
                Here is some information on the simulation type you have selected:
                                
                BayesNetSimulation:
                - This simulation type generates a random Bayes net.
                                
                Here is some information on the algorithms you have selected:
                                
                PC:
                - PC is a constraint-based algorithm that searches for the best fitting graph structure.
                                
                FCI
                - FCI is a constraint-based algorithm that searches for the best fitting graph structure.
                              
                """);
    }

    public static class BufferedListeningByteArrayOutputStream extends ByteArrayOutputStream {
        private final StringBuilder buffer = new StringBuilder();

        public static void main(String[] args) {
            BufferedListeningByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);

            // Example usage
            ps.println("Hello, world!");
            ps.printf("Pi is approximately %.2f%n", Math.PI);

            ps.close();
        }

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
