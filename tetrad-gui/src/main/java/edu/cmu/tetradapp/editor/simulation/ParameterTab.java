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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor.simulation;

import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.utils.ParameterSettingsText;
import edu.cmu.tetradapp.editor.AlgorithmParameterPanel;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetradapp.model.BooleanGlassSimulation;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ParameterComponents;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.Serial;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * May 23, 2019 3:59:42 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ParameterTab extends JPanel {

    /**
     * The graph type items.
     */
    public static final String[] GRAPH_TYPE_ITEMS = {
            GraphTypes.RANDOM_FOWARD_DAG,
            GraphTypes.ERDOS_RENYI_DAG,
            GraphTypes.SCALE_FREE_DAG,
            GraphTypes.CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS,
            GraphTypes.RANDOM_MIM,
            GraphTypes.RANDOM_MIMIC
//            GraphTypes.RANDOM_ONE_FACTOR_MIM,
//            GraphTypes.RANDOM_TWO_FACTOR_MIM
    };
    /**
     * The model type items.
     */
    public static final String[] MODEL_TYPE_ITEMS = {
            SimulationTypes.BAYS_NET,
            SimulationTypes.STRUCTURAL_EQUATION_MODEL,
            SimulationTypes.DAO_SIMULATION,
//            SimulationTypes.LINEAR_FISHER_MODEL,
//            SimulationTypes.GAUSSIAN_PROCESS_STRUCTURAL_EQUATION_MODEL,
            SimulationTypes.GENERAL_ADDITIVE_MODEL,
            SimulationTypes.GENERAL_NOISE_SEM,
            SimulationTypes.ADDITIVE_NOISE_SEM,
            SimulationTypes.DESIGNED_EXPERIMENT,
            SimulationTypes.OBSERVATIONAL_STUDY,
//            SimulationTypes.POST_NONLINEAR_MODEL,
            SimulationTypes.LEE_AND_HASTIE,
            SimulationTypes.CONDITIONAL_GAUSSIAN,
            SimulationTypes.TIME_SERIES,
            SimulationTypes.TRAINED_DAG_SIMULATION
    };

    public static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    @Serial
    private static final long serialVersionUID = 7074205549192562786L;
    /**
     * The graph type dropdown.
     */
    private final JComboBox<String> graphsDropdown = new JComboBox<>();

    /**
     * The simulation type dropdown.
     */
    private final JComboBox<String> simulationsDropdown = new JComboBox<>();

    /**
     * The parameter box.
     */
    private final Box parameterBox = Box.createVerticalBox();

    /**
     * The simulation.
     */
    private final Simulation simulation;

    /**
     * The initial flag.
     */
    private boolean initial = true;

    private static final String GRAPH_PREF_PARAM = "graphsDropdownPreference";
    private static final String SIM_PREF_PARAM   = "simulationsDropdownPreference";

    private static final String PREF_GRAPH_TYPE = "simulation.lastGraphType";
    private static final String PREF_SIM_TYPE   = "simulation.lastSimulationType";

    /**
     * <p>Constructor for ParameterTab.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public ParameterTab(Simulation simulation) {
        this.simulation = simulation;

        // set default simulation
        if (simulation.getSimulation() == null) {
            simulation.setSimulation(new BayesNetSimulation(new RandomForward()), simulation.getParams());
            simulation.setFixedSimulation(false);
        } else {
            if (simulation.getSimulation() instanceof BooleanGlassSimulation) {
                simulation.setFixedGraph(true);
            }
        }

        initComponents();

        Object graphSel = this.graphsDropdown.getSelectedItem();
        if (graphSel instanceof String s) {
            persistGraphSelection(s);
        }

        Object simSel = this.simulationsDropdown.getSelectedItem();
        if (simSel instanceof String s) {
            persistSimulationSelection(s);
        }

        if (simulation.getDataModelList().isEmpty()) {
            refreshParameters();
        } else {
            showParameters();
        }
    }

    /**
     * Returns an array of strings representing the available simulation items.
     *
     * @return an array of strings representing the available simulation items
     */
    public static String[] getSimulationItems() {
        return ParameterTab.MODEL_TYPE_ITEMS;
    }

    private Component getPanel() {
        return null;
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel north = new JPanel(new BorderLayout());
        north.add(createSettingsTextRow(), BorderLayout.NORTH);
        north.add(createSimulationOptionBox(), BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(createParameterPanel(), BorderLayout.CENTER);
        add(createSimulationButtonBox(), BorderLayout.SOUTH);
    }

    /**
     * Creates the top-left row with the "Settings as Text..." and "Paste Settings..." buttons,
     * mirroring the search editor's parameter panel.
     *
     * @return the row.
     */
    private JPanel createSettingsTextRow() {
        JButton settingsButton = new JButton("Settings as Text...");
        settingsButton.setToolTipText("Show the settings on this panel as plain text that can "
                + "be selected and copied.");
        settingsButton.addActionListener(e -> showSettingsTextDialog());

        JButton pasteButton = new JButton("Paste Settings...");
        pasteButton.setToolTipText("Paste settings text (as produced by \"Settings as Text...\") "
                + "to restore the simulation and graph selections and parameter values.");
        pasteButton.addActionListener(e -> showPasteSettingsDialog());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(settingsButton);
        row.add(Box.createHorizontalStrut(5));
        row.add(pasteButton);
        return row;
    }

    private void refreshParameters() {
        RandomGraph randomGraph = newRandomGraph();
        newSimulation(randomGraph);

        showParameters();

        firePropertyChange("refreshParameters", null, null);
    }

    @NotNull
    private RandomGraph newRandomGraph() {
        RandomGraph randomGraph = (this.simulation.getSourceGraph() == null)
                ? new SingleGraph(new EdgeListGraph())
                : new SingleGraph(this.simulation.getSourceGraph());

        if (!this.simulation.isFixedGraph()) {
            String graphItem = this.graphsDropdown.getItemAt(this.graphsDropdown.getSelectedIndex());
            persistGraphSelection(graphItem);

            randomGraph = switch (graphItem) {
                case GraphTypes.RANDOM_FOWARD_DAG -> new RandomForward();
                case GraphTypes.ERDOS_RENYI_DAG -> new ErdosRenyi();
                case GraphTypes.SCALE_FREE_DAG -> new ScaleFree();
                case GraphTypes.CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS -> new Cyclic();
                case GraphTypes.RANDOM_MIM -> new RandomMim();
                case GraphTypes.RANDOM_MIMIC -> new RandomMimic();
                case GraphTypes.RANDOM_ONE_FACTOR_MIM -> new RandomSingleFactorMim();
                case GraphTypes.RANDOM_TWO_FACTOR_MIM -> new RandomTwoFactorMim();
                default -> throw new IllegalArgumentException("Unrecognized simulation type: " + graphItem);
            };
        }

        return randomGraph;
    }

    private void newSimulation(RandomGraph randomGraph) {
        if (!this.simulation.isFixedSimulation()) {
            String simulationItem = this.simulationsDropdown.getItemAt(this.simulationsDropdown.getSelectedIndex());
            persistSimulationSelection(simulationItem);
            this.simulation.setFixedGraph(randomGraph instanceof SingleGraph);

            if (this.simulation.getSourceGraph() == null) {
                switch (simulationItem) {
                    case SimulationTypes.BAYS_NET:
                        this.simulation.setSimulation(new BayesNetSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                        this.simulation.setSimulation(new SemSimulation(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.GAUSSIAN_PROCESS_STRUCTURAL_EQUATION_MODEL:
//                        this.simulation.setSimulation(new GpSemSimulation(randomGraph), this.simulation.getParams());
//                        break;
//                    case SimulationTypes.POST_NONLINEAR_MODEL:
//                        this.simulation.setSimulation(new PostnonlinearSem(randomGraph), this.simulation.getParams());
//                        break;
                    case SimulationTypes.GENERAL_NOISE_SEM:
                        this.simulation.setSimulation(new GeneralNoiseSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.ADDITIVE_NOISE_SEM:
                        this.simulation.setSimulation(new AdditiveNoiseSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.DESIGNED_EXPERIMENT:
                        this.simulation.setSimulation(new DesignedExperimentSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.OBSERVATIONAL_STUDY:
                        this.simulation.setSimulation(new ObservationalStudySimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.GENERAL_ADDITIVE_MODEL:
                        this.simulation.setSimulation(new GeneralAdditiveModel(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.LG_MNAR_SIMULATION:
                        this.simulation.setSimulation(new LgMnarSimulation(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.LINEAR_FISHER_MODEL:
//                        this.simulation.setSimulation(new LinearFisherModel(randomGraph), this.simulation.getParams());
//                        break;
                    case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL:
                        this.simulation.setSimulation(new GeneralSemSimulationSpecial1(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.LEE_AND_HASTIE:
                        this.simulation.setSimulation(new LeeHastieSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.CONDITIONAL_GAUSSIAN:
                        this.simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.TIME_SERIES:
                        this.simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.BOOLEAN_GLASS_SIMULATION:
                        this.simulation.setSimulation(new BooleanGlassSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.TRAINED_DAG_SIMULATION:
                        try {
                            this.simulation.setSimulation(new TrainedDagModel(new SingleGraph(simulation.getInputGraph()),
                                    simulation.getInputData()), this.simulation.getParams());
                        } catch (Exception e) {
                            JOptionPane.showMessageDialog(getPanel(), "The trained DAG model simulation " +
                                    "requires a dataset and a graph whose variables all exist in the dataset.");
                        }
                        break;
                    case SimulationTypes.DAO_SIMULATION:
                        this.simulation.setSimulation(new DaoSimulation(randomGraph), this.simulation.getParams());
                        break;
                    default:
                        throw new IllegalArgumentException("Unrecognized simulation type: " + simulationItem);
                }
            } else {
                switch (simulationItem) {
                    case SimulationTypes.BAYS_NET:
                        this.simulation.setSimulation(new BayesNetSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                        this.simulation.setSimulation(new SemSimulation(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.GAUSSIAN_PROCESS_STRUCTURAL_EQUATION_MODEL:
//                        this.simulation.setSimulation(new GpSemSimulation(randomGraph), this.simulation.getParams());
//                        break;
                    case SimulationTypes.GENERAL_ADDITIVE_MODEL:
                        this.simulation.setSimulation(new GeneralAdditiveModel(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.POST_NONLINEAR_MODEL:
//                        this.simulation.setSimulation(new PostnonlinearSem(randomGraph), this.simulation.getParams());
//                        break;
                    case SimulationTypes.GENERAL_NOISE_SEM:
                        this.simulation.setSimulation(new GeneralNoiseSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.ADDITIVE_NOISE_SEM:
                        this.simulation.setSimulation(new AdditiveNoiseSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.DESIGNED_EXPERIMENT:
                        this.simulation.setSimulation(new DesignedExperimentSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.OBSERVATIONAL_STUDY:
                        this.simulation.setSimulation(new ObservationalStudySimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.LG_MNAR_SIMULATION:
                        this.simulation.setSimulation(new LgMnarSimulation(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.LINEAR_FISHER_MODEL:
//                        this.simulation.setSimulation(new LinearFisherModel(randomGraph), this.simulation.getParams());
//                        break;
                    case SimulationTypes.LEE_AND_HASTIE:
                        this.simulation.setSimulation(new LeeHastieSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.CONDITIONAL_GAUSSIAN:
                        this.simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.TIME_SERIES:
                        this.simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph, simulation.getKnowledge()), this.simulation.getParams());
                        break;
                    case SimulationTypes.TRAINED_DAG_SIMULATION:
                        this.simulation.setSimulation(new TrainedDagModel(new SingleGraph(simulation.getInputGraph()),
                                simulation.getInputData()), this.simulation.getParams());

                }
            }
        }
    }

    private void showParameters() {
        boolean fixedGraph = this.simulation.isFixedGraph();
        this.graphsDropdown.setEnabled(!fixedGraph);
        this.simulationsDropdown.setEnabled(!this.simulation.isFixedSimulation());

        this.parameterBox.removeAll();
        if (this.simulation.getSimulation() != null) {
            Set<String> params = new LinkedHashSet<>(this.simulation.getSimulation().getParameters());

            if (params.isEmpty()) {
                JLabel noParamLbl = ParameterTab.NO_PARAM_LBL;
                noParamLbl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                this.parameterBox.add(noParamLbl, BorderLayout.NORTH);
            } else {
                Box parameters = Box.createVerticalBox();
                Box[] paramBoxes = ParameterComponents.toArray(
                        ParameterComponents.createParameterComponents(params, this.simulation.getParams()));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                this.parameterBox.add(new PaddingPanel(parameters), BorderLayout.CENTER);
            }
        }
        this.parameterBox.validate();
        this.parameterBox.repaint();
    }

    private Box createSimulationButtonBox() {
        Dimension buttonSize = new Dimension(268, 25);

        JButton button = new JButton("Simulate");

        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setPreferredSize(buttonSize);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> simulate());

        Box box = Box.createVerticalBox();
        box.add(Box.createVerticalStrut(20));
        box.add(button);

        return box;
    }

    /**
     * Pops up a dialog into which settings text (as produced by "Settings as Text...") can be
     * pasted. If the first title line matches a simulation type (and, in parentheses, a graph
     * type) in the dropdowns, those selections are restored first; the parameter values are
     * then applied and the panel refreshed. Unknown or unparseable lines are reported.
     */
    private void showPasteSettingsDialog() {
        JTextArea area = new JTextArea(20, 60);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);

        Object[] options = {"Apply", "Cancel"};
        int choice = JOptionPane.showOptionDialog(getPanel(), scroll, "Paste Settings",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options,
                options[0]);

        if (choice != 0) {
            return;
        }

        String text = area.getText();

        // First pass: read the titles only, to restore the dropdown selections before applying
        // values (selection changes rebuild the simulation but retain the Parameters object).
        ParameterSettingsText.ApplyResult peek =
                ParameterSettingsText.applySettingsText(text, new edu.cmu.tetrad.util.Parameters());

        if (!peek.titles.isEmpty()) {
            String title = peek.titles.get(0);
            String simName = title;
            String graphName = null;

            int at = title.lastIndexOf(" (graph: ");
            if (at >= 0 && title.endsWith(")")) {
                simName = title.substring(0, at);
                graphName = title.substring(at + " (graph: ".length(), title.length() - 1);
            }

            if (this.simulationsDropdown.isEnabled()) {
                selectIfPresent(this.simulationsDropdown, simName);
            }
            if (graphName != null && this.graphsDropdown.isEnabled()) {
                selectIfPresent(this.graphsDropdown, graphName);
            }
        }

        ParameterSettingsText.ApplyResult result =
                ParameterSettingsText.applySettingsText(text, this.simulation.getParams());

        showParameters();
        firePropertyChange("refreshParameters", null, null);

        AlgorithmParameterPanel.showApplyResultMessage(getPanel(), result);
    }

    /**
     * Selects the given item in the dropdown if it is present (exact string match).
     *
     * @param dropdown the dropdown.
     * @param item     the item to select.
     */
    private void selectIfPresent(JComboBox<String> dropdown, String item) {
        for (int i = 0; i < dropdown.getItemCount(); i++) {
            if (dropdown.getItemAt(i).equals(item)) {
                if (!item.equals(dropdown.getSelectedItem())) {
                    dropdown.setSelectedItem(item);
                }
                return;
            }
        }
    }

    /**
     * Pops up a dialog containing the effective simulation settings as selectable text, with a
     * copy-to-clipboard option, mirroring the search editor's "Settings as Text..." button.
     */
    private void showSettingsTextDialog() {
        if (this.simulation.getSimulation() == null) {
            return;
        }

        String simulationItem = String.valueOf(this.simulationsDropdown.getSelectedItem());
        String title = this.graphsDropdown.isEnabled()
                ? simulationItem + " (graph: " + this.graphsDropdown.getSelectedItem() + ")"
                : simulationItem;

        Set<String> params = new LinkedHashSet<>(this.simulation.getSimulation().getParameters());
        String text = ParameterSettingsText.render(title, params, this.simulation.getParams());

        JTextArea area = new JTextArea(text, 25, 60);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);

        Object[] options = {"Copy to Clipboard", "Close"};
        int choice = JOptionPane.showOptionDialog(getPanel(), scroll, "Parameter Settings",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options,
                options[1]);

        if (choice == 0) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        }
    }

    private JPanel createParameterPanel() {
        JPanel paramPanel = new JPanel(new BorderLayout(0, 5));
        paramPanel.add(new JLabel("Parameters for your simulation are listed below. Please adjust the parameter values."), BorderLayout.NORTH);
        paramPanel.add(new JScrollPane(this.parameterBox), BorderLayout.CENTER);

        return paramPanel;
    }

    private Box createSimulationOptionBox() {
        Box simOptBox = Box.createVerticalBox();

        // type of graph options
        if (!this.simulation.isFixedGraph()) {
            Arrays.stream(ParameterTab.GRAPH_TYPE_ITEMS).forEach(this.graphsDropdown::addItem);
            this.graphsDropdown.setMaximumSize(this.graphsDropdown.getPreferredSize());
            this.graphsDropdown.setSelectedItem(resolveInitialGraphSelection());
            this.graphsDropdown.addActionListener(e -> {
                Object selected = this.graphsDropdown.getSelectedItem();
                if (selected instanceof String s) {
                    persistGraphSelection(s);
                }
                refreshParameters();
            });

            simOptBox.add(createLabeledComponent("Type of Graph: ", this.graphsDropdown));
            simOptBox.add(Box.createVerticalStrut(10));
        }

        if (simulation.getSimulation() instanceof TrainedDagModel) {
            this.simulationsDropdown.addItem(SimulationTypes.TRAINED_DAG_SIMULATION);
            this.simulationsDropdown.setSelectedItem(SimulationTypes.TRAINED_DAG_SIMULATION);
        } else {
            String[] simulationItems = getSimulationItems();
            Arrays.stream(simulationItems).forEach(this.simulationsDropdown::addItem);
            this.simulationsDropdown.setSelectedItem(resolveInitialSimulationSelection());
        }

        this.simulationsDropdown.setMaximumSize(this.simulationsDropdown.getPreferredSize());
        this.simulationsDropdown.addActionListener(e -> {
            Object selected = this.simulationsDropdown.getSelectedItem();
            if (selected instanceof String s) {
                persistSimulationSelection(s);
            }
            refreshParameters();
        });

        simOptBox.add(createLabeledComponent("Type of Simulation: ", this.simulationsDropdown));
        simOptBox.add(Box.createVerticalStrut(20));

        return simOptBox;
    }

    private void simulate() {
        int ret = JOptionPane.showConfirmDialog(getPanel(), "Simulate new dataset(s)?",
                "Confirm", JOptionPane.OK_CANCEL_OPTION);

        if (ret == JOptionPane.CANCEL_OPTION) {
            JOptionPane.showMessageDialog(getPanel(), "Keeping existing datasets(s)");
            return;
        }

        class MyWatchedProcess extends WatchedProcess {
            private volatile Throwable error;

            @Override
            public void watch() {
                try {
                    RandomGraph randomGraph = newRandomGraph();
                    newSimulation(randomGraph);
                    ParameterTab.this.simulation.getSimulation()
                            .createData(ParameterTab.this.simulation.getParams(), false);

                    initial = false;
                    firePropertyChange("modelChanged", null, null);

                } catch (Throwable t) {
                    t.printStackTrace(System.err);
                    error = (t.getCause() != null) ? t.getCause() : t;
                } finally {
                    if (error != null) {
                        final String msg =
                                (error.getMessage() == null || error.getMessage().trim().isEmpty())
                                        ? "Exception in creating data. Check model setup or parameter settings."
                                        : error.getMessage();

                        String _msg = msg;

                        if (error instanceof ParseException) {
                            _msg += " (Parse error)";
                        }

                        String finalMsg = _msg;

                        // Let WatchedProcess finish & close its dialog, then show the error.
                        SwingUtilities.invokeLater(() ->
                                SwingUtilities.invokeLater(() ->
                                        JOptionPane.showMessageDialog(
                                                getPanel(), finalMsg, "Error", JOptionPane.ERROR_MESSAGE)));
                    }
                }
            }
        }

        new MyWatchedProcess();
    }

    private Box createLabeledComponent(String text, Component comp) {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel(text));
        box.add(Box.createGlue());

        if (comp != null) {
            box.add(comp);
        }

        return box;
    }

    /**
     * <p>isInitial.</p>
     *
     * @return a boolean
     */
    public boolean isInitial() {
        return initial;
    }

    private Preferences prefs() {
        return Preferences.userRoot().node("edu.cmu.tetradapp.editor.simulation");
    }

    private String resolveInitialGraphSelection() {
        String[] allowed = ParameterTab.GRAPH_TYPE_ITEMS;

        String fromParams = this.simulation.getParams().getString(GRAPH_PREF_PARAM, null);
        if (isAllowed(fromParams, allowed)) {
            return fromParams;
        }

        String fromPrefs = prefs().get(PREF_GRAPH_TYPE, null);
        if (isAllowed(fromPrefs, allowed)) {
            return fromPrefs;
        }

        return allowed[0];
    }

    private String resolveInitialSimulationSelection() {
        String[] allowed = getSimulationItems();

        String fromParams = this.simulation.getParams().getString(SIM_PREF_PARAM, null);
        if (isAllowed(fromParams, allowed)) {
            return fromParams;
        }

        String fromPrefs = prefs().get(PREF_SIM_TYPE, null);
        if (isAllowed(fromPrefs, allowed)) {
            return fromPrefs;
        }

        return allowed[0];
    }

    private boolean isAllowed(String value, String[] allowed) {
        if (value == null) {
            return false;
        }

        for (String s : allowed) {
            if (s.equals(value)) {
                return true;
            }
        }

        return false;
    }

    private void persistGraphSelection(String graphItem) {
        if (graphItem == null) {
            return;
        }

        this.simulation.getParams().set(GRAPH_PREF_PARAM, graphItem);
        prefs().put(PREF_GRAPH_TYPE, graphItem);
    }

    private void persistSimulationSelection(String simulationItem) {
        if (simulationItem == null) {
            return;
        }

        this.simulation.getParams().set(SIM_PREF_PARAM, simulationItem);
        prefs().put(PREF_SIM_TYPE, simulationItem);
    }
}

