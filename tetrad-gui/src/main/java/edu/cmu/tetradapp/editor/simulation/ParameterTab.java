/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.editor.simulation;

import edu.cmu.tetrad.algcomparison.graph.Cyclic;
import edu.cmu.tetrad.algcomparison.graph.GraphTypes;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.RandomSingleFactorMim;
import edu.cmu.tetrad.algcomparison.graph.RandomTwoFactorMim;
import edu.cmu.tetrad.algcomparison.graph.ScaleFree;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.BooleanGlassSimulation;
import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.algcomparison.simulation.GeneralSemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.GeneralSemSimulationSpecial1;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.SimulationTypes;
import edu.cmu.tetrad.algcomparison.simulation.StandardizedSemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.TimeSeriesSemSimulation;
import edu.cmu.tetrad.data.simulation.LoadContinuousDataAndGraphs;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ParameterComponents;
import edu.cmu.tetradapp.util.WatchedProcess;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 *
 * May 23, 2019 3:59:42 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ParameterTab extends JPanel {

    private static final long serialVersionUID = 7074205549192562786L;

    private static final String[] GRAPH_ITEMS = new String[]{
        GraphTypes.RANDOM_FOWARD_DAG,
        GraphTypes.SCALE_FREE_DAG,
        GraphTypes.CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS,
        GraphTypes.RANDOM_ONE_FACTOR_MIM,
        GraphTypes.RANDOM_TWO_FACTOR_MIM
    };

    private static final String[] SOURCE_GRAPH_ITEMS = {
        SimulationTypes.BAYS_NET,
        SimulationTypes.STRUCTURAL_EQUATION_MODEL,
        SimulationTypes.LINEAR_FISHER_MODEL,
        SimulationTypes.LEE_AND_HASTIE,
        SimulationTypes.CONDITIONAL_GAUSSIAN,
        SimulationTypes.TIME_SERIES
    };

    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");

    private final JComboBox<String> graphsDropdown = new JComboBox<>();
    private final JComboBox<String> simulationsDropdown = new JComboBox<>();

    private final Box parameterBox = Box.createVerticalBox();

    private final Simulation simulation;

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

        if (simulation.getDataModelList().isEmpty()) {
            refreshParameters();
        } else {
            showParameters();
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(createSimulationOptionBox(), BorderLayout.NORTH);
        add(createParameterPanel(), BorderLayout.CENTER);
        add(createSimulationButtonBox(), BorderLayout.SOUTH);
    }

    private void refreshParameters() {
        RandomGraph randomGraph = (simulation.getSourceGraph() == null)
                ? new SingleGraph(new EdgeListGraph())
                : new SingleGraph(simulation.getSourceGraph());

        if (!simulation.isFixedGraph()) {
            String graphItem = graphsDropdown.getItemAt(graphsDropdown.getSelectedIndex());
            simulation.getParams().set("graphsDropdownPreference", graphItem);

            switch (graphItem) {
                case GraphTypes.RANDOM_FOWARD_DAG:
                    randomGraph = new RandomForward();
                    break;
                case GraphTypes.SCALE_FREE_DAG:
                    randomGraph = new ScaleFree();
                    break;
                case GraphTypes.CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS:
                    randomGraph = new Cyclic();
                    break;
                case GraphTypes.RANDOM_ONE_FACTOR_MIM:
                    randomGraph = new RandomSingleFactorMim();
                    break;
                case GraphTypes.RANDOM_TWO_FACTOR_MIM:
                    randomGraph = new RandomTwoFactorMim();
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognized simulation type: " + graphItem);
            }
        }

        if (!simulation.isFixedSimulation()) {
            String simulationItem = simulationsDropdown.getItemAt(simulationsDropdown.getSelectedIndex());
            simulation.getParams().set("simulationsDropdownPreference", simulationItem);
            simulation.setFixedGraph(randomGraph instanceof SingleGraph);

            if (simulation.getSourceGraph() == null) {
                switch (simulationItem) {
                    case SimulationTypes.BAYS_NET:
                        simulation.setSimulation(new BayesNetSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                        simulation.setSimulation(new SemSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.LINEAR_FISHER_MODEL:
                        simulation.setSimulation(new LinearFisherModel(randomGraph, simulation.getInputDataModelList()), simulation.getParams());
                        break;
                    case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL:
                        simulation.setSimulation(new GeneralSemSimulationSpecial1(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.LEE_AND_HASTIE:
                        simulation.setSimulation(new LeeHastieSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.CONDITIONAL_GAUSSIAN:
                        simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.TIME_SERIES:
                        simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.BOOLEAN_GLASS_SIMULATION:
                        simulation.setSimulation(new BooleanGlassSimulation(randomGraph), simulation.getParams());
                        break;
                    default:
                        throw new IllegalArgumentException("Unrecognized simulation type: " + simulationItem);
                }
            } else {
                switch (simulationItem) {
                    case SimulationTypes.BAYS_NET:
                        simulation.setSimulation(new BayesNetSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                        simulation.setSimulation(new SemSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.LINEAR_FISHER_MODEL:
                        simulation.setSimulation(new LinearFisherModel(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.LEE_AND_HASTIE:
                        simulation.setSimulation(new LeeHastieSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.CONDITIONAL_GAUSSIAN:
                        simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), simulation.getParams());
                        break;
                    case SimulationTypes.TIME_SERIES:
                        simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph), simulation.getParams());
                        break;
                }
            }
        }

        showParameters();

        firePropertyChange("refreshParameters", null, null);
    }

    private void showParameters() {
        boolean fixedGraph = simulation.isFixedGraph();
        graphsDropdown.setEnabled(!fixedGraph);
        simulationsDropdown.setEnabled(!simulation.isFixedSimulation());

        parameterBox.removeAll();
        if (simulation.getSimulation() != null) {
            Set<String> params = new LinkedHashSet<>(simulation.getSimulation().getParameters());
            if (params.isEmpty()) {
                parameterBox.add(NO_PARAM_LBL, BorderLayout.NORTH);
            } else {
                Box parameters = Box.createVerticalBox();
                Box[] paramBoxes = ParameterComponents.toArray(
                        ParameterComponents.createParameterComponents(params, simulation.getParams()));
                int lastIndex = paramBoxes.length - 1;
                for (int i = 0; i < lastIndex; i++) {
                    parameters.add(paramBoxes[i]);
                    parameters.add(Box.createVerticalStrut(10));
                }
                parameters.add(paramBoxes[lastIndex]);

                parameterBox.add(new PaddingPanel(parameters), BorderLayout.CENTER);
            }
        }
        parameterBox.validate();
        parameterBox.repaint();
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

    private JPanel createParameterPanel() {
        JPanel paramPanel = new JPanel(new BorderLayout(0, 5));
        paramPanel.add(new JLabel("Parameters for your simulation are listed below. Please adjust the parameter values."), BorderLayout.NORTH);
        paramPanel.add(new JScrollPane(parameterBox), BorderLayout.CENTER);

        return paramPanel;
    }

    private Box createSimulationOptionBox() {
        Box simOptBox = Box.createVerticalBox();

        // type of graph options
        if (!simulation.isFixedGraph()) {
            Arrays.stream(GRAPH_ITEMS).forEach(graphsDropdown::addItem);
            graphsDropdown.setMaximumSize(graphsDropdown.getPreferredSize());
            graphsDropdown.setSelectedItem(simulation.getParams().getString("graphsDropdownPreference", GRAPH_ITEMS[0]));
            graphsDropdown.addActionListener(e -> refreshParameters());

            simOptBox.add(createLabeledComponent("Type of Graph: ", graphsDropdown));
            simOptBox.add(Box.createVerticalStrut(10));
        }

        String[] simulationItems = getSimulationItems(simulation);
        Arrays.stream(simulationItems).forEach(simulationsDropdown::addItem);
        simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());
        simulationsDropdown.setSelectedItem(
                simulation.getParams().getString("simulationsDropdownPreference", simulationItems[0]));
        simulationsDropdown.addActionListener(e -> refreshParameters());

        simOptBox.add(createLabeledComponent("Type of Simulation Model: ", simulationsDropdown));
        simOptBox.add(Box.createVerticalStrut(20));

        return simOptBox;
    }

    private void simulate() {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                try {
                    simulation.getSimulation().createData(simulation.getParams());

                    firePropertyChange("modelChanged", null, null);
                } catch (Exception exception) {
                    exception.printStackTrace(System.err);
                    Throwable cause = exception;
                    if (exception.getCause() != null) {
                        cause = exception.getCause();
                    }

                    if (cause.getMessage() == null || cause.getMessage().trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "Exception in creating data. Check model setup or parameter settings.");
                    } else {
                        throw new IllegalArgumentException(cause.getMessage());
                    }
                }
            }
        };
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

    private String[] getSimulationItems(Simulation simulation) {
        final String[] items;

        if (simulation.isFixedSimulation()) {
            if (simulation.getSimulation() instanceof BayesNetSimulation) {
                items = new String[]{
                    SimulationTypes.BAYS_NET
                };
            } else if (simulation.getSimulation() instanceof SemSimulation) {
                items = new String[]{
                    SimulationTypes.STRUCTURAL_EQUATION_MODEL
                };
            } else if (simulation.getSimulation() instanceof LinearFisherModel) {
                items = new String[]{
                    SimulationTypes.LINEAR_FISHER_MODEL
                };
            } else if (simulation.getSimulation() instanceof StandardizedSemSimulation) {
                items = new String[]{
                    SimulationTypes.STANDARDIZED_STRUCTURAL_EQUATION_MODEL
                };
            } else if (simulation.getSimulation() instanceof GeneralSemSimulation) {
                items = new String[]{
                    SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL
                };
            } else if (simulation.getSimulation() instanceof LoadContinuousDataAndGraphs) {
                items = new String[]{
                    SimulationTypes.LOADED_FROM_FILES
                };
            } else {
                throw new IllegalStateException("Not expecting that model type: "
                        + simulation.getSimulation().getClass());
            }
        } else {
            items = SOURCE_GRAPH_ITEMS;
        }

        return items;
    }

}
