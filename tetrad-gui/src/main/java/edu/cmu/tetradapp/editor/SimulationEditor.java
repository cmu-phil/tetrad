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

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Displays data objects and allows users to edit these objects as well as load
 * and save them.
 *
 * @author Joseph Ramsey
 */
public final class SimulationEditor extends JPanel implements KnowledgeEditable,
        PropertyChangeListener {

    private final JButton simulateButton = new JButton("Simulate");
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
            dataEditor = new DataEditor(wrapper);
        } else {
            graphWorkbench = new GraphWorkbench();
            dataEditor = new DataEditor();
        }

        final String[] items = new String[]{
                "Bayes net",
                "Structural Equation Model",
                "General Structural Equation Model",
                "General Structural Equation Model Special",
                "Lee & Hastie",
                "Time Series"
        };

        for (String item : items) {
            simulationsDropdown.addItem(item);
        }

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Simulation Setup", getParametersPane(simulation.getSimulation(), simulation.getParams()));
        tabbedPane.addTab("True Graph", graphWorkbench);
        tabbedPane.addTab("Data", dataEditor);
        tabbedPane.setPreferredSize(new Dimension(800, 600));

        simulationsDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();

                String item = (String) box.getSelectedItem();

                edu.cmu.tetrad.algcomparison.simulation.Simulation dummySimulation;

                if (item.equals(items[0])) {
                    dummySimulation = new BayesNetSimulation(new RandomForward());
                } else if (item.endsWith(items[1])) {
                    dummySimulation = new SemSimulation(new RandomForward());
                } else if (item.endsWith(items[2])) {
                    dummySimulation = new GeneralSemSimulation(new RandomForward());
                } else if (item.endsWith(items[3])) {
                    dummySimulation = new GeneralSemSimulationSpecial1(new RandomForward());
                } else if (item.endsWith(items[4])) {
                    dummySimulation = new LeeHastieSimulation(new RandomForward());
                } else if (item.endsWith(items[5])) {
                    dummySimulation = new TimeSeriesSemSimulation();
                } else {
                    throw new IllegalArgumentException("Unrecognized simulation type: " + item);
                }

                tabbedPane.setComponentAt(0, getParametersPane(dummySimulation, simulation.getParams()));
            }
        });

        simulateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = simulationsDropdown;
                String item = (String) box.getSelectedItem();

                if (item.equals(items[0])) {
                    simulation.setSimulation(new BayesNetSimulation(new RandomForward()));
                } else if (item.endsWith(items[1])) {
                    simulation.setSimulation(new SemSimulation(new RandomForward()));
                } else if (item.endsWith(items[2])) {
                    simulation.setSimulation(new GeneralSemSimulation(new RandomForward()));
                } else if (item.endsWith(items[3])) {
                    simulation.setSimulation(new GeneralSemSimulationSpecial1(new RandomForward()));
                } else if (item.endsWith(items[4])) {
                    simulation.setSimulation(new LeeHastieSimulation(new RandomForward()));
                } else if (item.endsWith(items[5])) {
                    simulation.setSimulation(new TimeSeriesSemSimulation());
                } else {
                    throw new IllegalArgumentException("Unrecognized simulation type: " + item);
                }

                simulation.createSimulation();
                graphWorkbench.setGraph(simulation.getSimulation().getTrueGraph());
                DataWrapper wrapper = new DataWrapper();
                wrapper.setDataModelList(simulation.getDataModelList());
                DataEditor dataEditor = new DataEditor(wrapper);
                tabbedPane.setComponentAt(2, dataEditor);
                tabbedPane.setComponentAt(0, getParametersPane(simulation.getSimulation(), simulation.getParams()));
            }
        });

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private Box getParametersPane(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation, Parameters parameters) {
        JScrollPane scroll;

        if (simulation != null) {
            ParameterPanel comp = new ParameterPanel(simulation.getParameters(), parameters);
            scroll = new JScrollPane(comp);
        } else {
            scroll = new JScrollPane();
        }

        scroll.setPreferredSize(scroll.getMaximumSize());

        Box c = Box.createVerticalBox();
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




