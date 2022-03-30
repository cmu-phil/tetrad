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

import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.data.simulation.LoadContinuousDataAndGraphs;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ParameterComponents;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * May 23, 2019 3:59:42 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ParameterTab extends JPanel {

    private static final long serialVersionUID = 7074205549192562786L;

    private static final String[] GRAPH_ITEMS = {
            GraphTypes.RANDOM_FOWARD_DAG,
            GraphTypes.ERDOS_RENYI_DAG,
            GraphTypes.SCALE_FREE_DAG,
            GraphTypes.CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS,
            GraphTypes.RANDOM_ONE_FACTOR_MIM,
            GraphTypes.RANDOM_TWO_FACTOR_MIM
    };

    private static final String[] SOURCE_GRAPH_ITEMS = {
            SimulationTypes.BAYS_NET,
            SimulationTypes.STRUCTURAL_EQUATION_MODEL,
//            SimulationTypes.LINEAR_FISHER_MODEL,
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

    private Component getPanel() {
        return null;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(createSimulationOptionBox(), BorderLayout.NORTH);
        add(createParameterPanel(), BorderLayout.CENTER);
        add(createSimulationButtonBox(), BorderLayout.SOUTH);
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
            this.simulation.getParams().set("graphsDropdownPreference", graphItem);

            switch (graphItem) {
                case GraphTypes.RANDOM_FOWARD_DAG:
                    randomGraph = new RandomForward();
                    break;
                case GraphTypes.ERDOS_RENYI_DAG:
                    randomGraph = new ErdosRenyi();
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
        return randomGraph;
    }

    private void newSimulation(RandomGraph randomGraph) {
        if (!this.simulation.isFixedSimulation()) {
            String simulationItem = this.simulationsDropdown.getItemAt(this.simulationsDropdown.getSelectedIndex());
            this.simulation.getParams().set("simulationsDropdownPreference", simulationItem);
            this.simulation.setFixedGraph(randomGraph instanceof SingleGraph);

            if (this.simulation.getSourceGraph() == null) {
                switch (simulationItem) {
                    case SimulationTypes.BAYS_NET:
                        this.simulation.setSimulation(new BayesNetSimulation(randomGraph), this.simulation.getParams());
                        break;
                    case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                        this.simulation.setSimulation(new SemSimulation(randomGraph), this.simulation.getParams());
                        break;
//                    case SimulationTypes.LINEaAR_FISHER_MODEL:
//                        simulation.setSimulation(new LinearFisherModel(randomGraph, simulation.getInputDataModelList()), simulation.getParams());
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
                    case SimulationTypes.LINEAR_FISHER_MODEL:
                        this.simulation.setSimulation(new LinearFisherModel(randomGraph), this.simulation.getParams());
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
                this.parameterBox.add(ParameterTab.NO_PARAM_LBL, BorderLayout.NORTH);
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
            Arrays.stream(ParameterTab.GRAPH_ITEMS).forEach(this.graphsDropdown::addItem);
            this.graphsDropdown.setMaximumSize(this.graphsDropdown.getPreferredSize());
            this.graphsDropdown.setSelectedItem(this.simulation.getParams().getString("graphsDropdownPreference", ParameterTab.GRAPH_ITEMS[0]));
            this.graphsDropdown.addActionListener(e -> refreshParameters());

            simOptBox.add(createLabeledComponent("Type of Graph: ", this.graphsDropdown));
            simOptBox.add(Box.createVerticalStrut(10));
        }

        String[] simulationItems = getSimulationItems(this.simulation);
        Arrays.stream(simulationItems).forEach(this.simulationsDropdown::addItem);
        this.simulationsDropdown.setMaximumSize(this.simulationsDropdown.getPreferredSize());
        this.simulationsDropdown.setSelectedItem(
                this.simulation.getParams().getString("simulationsDropdownPreference", simulationItems[0]));
        this.simulationsDropdown.addActionListener(e -> refreshParameters());

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
        } else {
            JOptionPane.showMessageDialog(getPanel(), "Created new datasets(s); check the Data tab");
        }

        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                try {
                    RandomGraph randomGraph = newRandomGraph();
                    newSimulation(randomGraph);
                    ParameterTab.this.simulation.getSimulation().createData(ParameterTab.this.simulation.getParams(), false);

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
        String[] items;

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
            items = ParameterTab.SOURCE_GRAPH_ITEMS;
        }

        return items;
    }

}
