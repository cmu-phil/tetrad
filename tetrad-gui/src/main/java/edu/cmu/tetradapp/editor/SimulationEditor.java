///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Displays a simulation and lets the user create new simulations. A simulation is an ordered
 * pair of a Graph and a list of DataSets. These can be created in a variety of ways, either
 * standalone or taking graphs, IM's, or PM's as parents, and using the information those
 * objects contain. For a Simulation you need a RandomGraph and you need to pick a particular
 * style of Simulation.
 *
 * @author Joseph Ramsey
 */
public final class SimulationEditor extends JPanel implements KnowledgeEditable,
        PropertyChangeListener {

    private final JButton simulateButton = new JButton("Simulate");
    private JComboBox<String> graphsDropdown = new JComboBox<>();
    private JComboBox<String> simulationsDropdown = new JComboBox<>();

    //==========================CONSTUCTORS===============================//

    /**
     * Constructs the data editor with an empty list of data displays.
     */
    public SimulationEditor(final Simulation simulation) {
        final GraphWorkbench graphWorkbench;
        DataEditor dataEditor;

        if (simulation.getSimulation() != null) {
            simulation.createSimulation();
            graphWorkbench = new GraphWorkbench(simulation.getSimulation().getTrueGraph());
            DataWrapper wrapper = new DataWrapper();
            wrapper.setDataModelList(simulation.getDataModelList());
            dataEditor = new DataEditor(wrapper, false);
        } else {
            graphWorkbench = new GraphWorkbench();
            dataEditor = new DataEditor();
            simulation.setSimulation(new BayesNetSimulation(new RandomForward()));
        }

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Simulation Setup", getParametersPane(simulation, simulation.getSimulation(), simulation.getParams()));
        tabbedPane.addTab("True Graph", graphWorkbench);
        tabbedPane.addTab("Data", dataEditor);
        tabbedPane.setPreferredSize(new Dimension(800, 600));

        final String[] graphItems = new String[]{
                "Random Foward",
                "Cyclic",
                "Scale Free"
        };

        for (String item : graphItems) {
            graphsDropdown.addItem(item);
        }

        final String[] simulationItems = getSimulationItems(simulation);

        for (String item : simulationItems) {
            simulationsDropdown.addItem(item);
        }

        graphsDropdown.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                resetPanel(simulation, graphItems, simulationItems, tabbedPane);
            }
        });

        simulationsDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetPanel(simulation, graphItems, simulationItems, tabbedPane);
            }
        });

        simulateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new WatchedProcess((Window) getTopLevelAncestor()) {
                    @Override
                    public void watch() {
                        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();
                        _simulation.createData(simulation.getParams());
                        graphWorkbench.setGraph(_simulation.getTrueGraph());
                        DataWrapper wrapper = new DataWrapper();
                        wrapper.setDataModelList(simulation.getDataModelList());
                        tabbedPane.setComponentAt(2, new DataEditor(wrapper));
                    }
                };
            }
        });

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void resetPanel(Simulation simulation, String[] graphItems, String[] simulationItems, JTabbedPane tabbedPane) {
        if (!simulation.isFixedSimulation()) {
            RandomGraph randomGraph;

            String graphItem = (String) graphsDropdown.getSelectedItem();

            if (graphItem.equals(graphItems[0])) {
                randomGraph = new RandomForward();
            } else if (graphItem.equals(graphItems[1])) {
                randomGraph = new Cyclic();
            } else if (graphItem.equals(graphItems[2])) {
                randomGraph = new ScaleFree();
            } else {
                randomGraph = new SingleGraph(simulation.getSimulation().getTrueGraph());
            }

            if (simulationItems.length > 1) {
                String simulationItem = (String) simulationsDropdown.getSelectedItem();

                if (simulationItem.equals(simulationItems[0])) {
                    simulation.setSimulation(new BayesNetSimulation(randomGraph));
                } else if (simulationItem.equals(simulationItems[1])) {
                    simulation.setSimulation(new SemSimulation(randomGraph));
                } else if (simulationItem.equals(simulationItems[2])) {
                    simulation.setSimulation(new SemThenDiscretize(randomGraph));
//                    } else if (graphItem.equals(simulationItems[3])) {
//                        simulation.setSimulation(new GeneralSemSimulation(randomGraph));
                } else if (simulationItem.equals(simulationItems[3])) {
                    simulation.setSimulation(new GeneralSemSimulationSpecial1(randomGraph));
                } else if (simulationItem.equals(simulationItems[4])) {
                    simulation.setSimulation(new LeeHastieSimulation(randomGraph));
                } else if (simulationItem.equals(simulationItems[5])) {
                    simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph));
                } else {
                    throw new IllegalArgumentException("Unrecognized simulation type: " + simulationItem);
                }
            }
        }

        tabbedPane.setComponentAt(0, getParametersPane(simulation, simulation.getSimulation(),
                simulation.getParams()));
    }

    private String[] getSimulationItems(Simulation simulation) {
        final String[] simulationItems;

        if (simulation.isFixedSimulation()) {
            if (simulation.getSimulation() instanceof BayesNetSimulation) {
                simulationItems = new String[]{
                        "Bayes net",
                };
            } else if (simulation.getSimulation() instanceof SemSimulation) {
                simulationItems = new String[]{
                        "Structural Equation Model"
                };
            } else if (simulation.getSimulation() instanceof GeneralSemSimulation) {
                simulationItems = new String[]{
                        "General Structural Equation Model",
                };
            } else {
                throw new IllegalStateException("Not expecting that model type: "
                        + simulation.getSimulation().getClass());
            }
        } else {
            simulationItems = new String[]{
                    "Bayes net",
                    "Structural Equation Model",
                    "Structural Equation Model, discretizing some variables",
//                    "General Structural Equation Model",
                    "General Structural Equation Model Special",
                    "Lee & Hastie",
                    "Time Series"
            };
        }
        return simulationItems;
    }

    private Box getParametersPane(Simulation _simulation,
                                  edu.cmu.tetrad.algcomparison.simulation.Simulation simulation,
                                  Parameters parameters) {
        JScrollPane scroll;

        if (simulation != null) {
            ParameterPanel comp = new ParameterPanel(simulation.getParameters(), parameters);
            scroll = new JScrollPane(comp);
        } else {
            scroll = new JScrollPane();
        }

        scroll.setPreferredSize(scroll.getMaximumSize());

        Box c = Box.createVerticalBox();

        if (!(_simulation.isFixedSimulation())) {
            c.add(graphsDropdown);
        }

        c.add(simulationsDropdown);
        c.add(scroll);
        c.add(simulateButton);

        Box b = Box.createHorizontalBox();
        b.add(c);
        b.add(Box.createHorizontalGlue());
        return b;
    }

    @Override
    public IKnowledge getKnowledge() {
        return null;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {

    }

    @Override
    public Graph getSourceGraph() {
        return null;
    }

    @Override
    public List<String> getVarNames() {
        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }
}




