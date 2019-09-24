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

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.ui.PaddingPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Displays a simulation and lets the user create new simulations. A simulation
 * is an ordered pair of a Graph and a list of DataSets. These can be created in
 * a variety of ways, either standalone or taking graphs, IM's, or PM's as
 * parents, and using the information those objects contain. For a Simulation
 * you need a RandomGraph and you need to pick a particular style of Simulation.
 *
 * @author Joseph Ramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class SimulationEditor extends JPanel implements KnowledgeEditable, PropertyChangeListener {

    private static final long serialVersionUID = -8424284512836439370L;

    final JTabbedPane tabbedPane = new JTabbedPane();

    private final Simulation simulation;
    private final DataEditor dataEditor;
    private final SimulationGraphEditor simulationGraphEditor;
    private final ParameterTab parameterTab;

    /**
     * Constructs the data editor with an empty list of data displays.
     *
     * @param simulation
     */
    public SimulationEditor(final Simulation simulation) {
        this.simulation = simulation;
        this.dataEditor = createDataEditor(simulation);
        this.simulationGraphEditor = createSimulationGraphEditor(simulation);
        this.parameterTab = new ParameterTab(simulation);

        initComponents();
        showTab();
    }

    private void initComponents() {
        parameterTab.addPropertyChangeListener(this);

        tabbedPane.addTab("Simulation Setup", new PaddingPanel(parameterTab));
        tabbedPane.addTab("True Graph", simulationGraphEditor);
        tabbedPane.addTab("Data", dataEditor);
        tabbedPane.setPreferredSize(new Dimension(800, 600));

        setLayout(new BorderLayout());
        add(createMenuBar(), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void showTab() {
        if (simulation.getSimulation() == null) {
            tabbedPane.setEnabledAt(0, true);
            tabbedPane.setEnabledAt(1, false);
            tabbedPane.setEnabledAt(2, false);
        } else {
            if (simulation.getDataModelList().size() > 0) {
                tabbedPane.setEnabledAt(0, true);
                tabbedPane.setEnabledAt(1, true);
                tabbedPane.setEnabledAt(2, true);
                tabbedPane.setSelectedIndex(2);
            } else {
                tabbedPane.setEnabledAt(0, true);
                tabbedPane.setEnabledAt(1, false);
                tabbedPane.setEnabledAt(2, false);
            }
        }
    }

    private SimulationGraphEditor createSimulationGraphEditor(final Simulation simulation) {
        SimulationGraphEditor graphEditor = new SimulationGraphEditor(Collections.<Graph>emptyList());
        if (simulation.getSimulation() != null) {
            List<Graph> trueGraphs = new ArrayList<>();
            for (int i = 0; i < simulation.getSimulation().getNumDataModels(); i++) {
                trueGraphs.add(simulation.getSimulation().getTrueGraph(i));
            }

            graphEditor.replace(trueGraphs);
        }

        return graphEditor;
    }

    private DataEditor createDataEditor(final Simulation simulation) {
        edu.cmu.tetrad.algcomparison.simulation.Simulation sim = simulation.getSimulation();
        if (sim == null) {
            return new DataEditor(JTabbedPane.LEFT);
        } else {
            DataModelList dataModelList = new DataModelList();
            for (int i = 0; i < sim.getNumDataModels(); i++) {
                dataModelList.add(sim.getDataModel(i));
            }

            DataWrapper wrapper = new DataWrapper(new Parameters());
            wrapper.setDataModelList(dataModelList);

            return new DataEditor(wrapper, false, JTabbedPane.LEFT);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuItem saveSimulation = new JMenuItem("Save Simulation");
        saveSimulation.addActionListener(createSaveSimulationActionListener());

        JMenu file = new JMenu("File");
        file.add(saveSimulation);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);

        return menuBar;
    }

    private ActionListener createSaveSimulationActionListener() {
        return e -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int ret = chooser.showSaveDialog(JOptionUtils.centeringComp());
            if (!(ret == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null) {
                return;
            }

            new Comparison().saveToFilesSingleSimulation(selectedFile.getAbsolutePath(), simulation.getSimulation(),
                    simulation.getParams());
        };
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "modelChanged":
                List<Graph> trueGraphs = new ArrayList<>();
                for (int i = 0; i < simulation.getSimulation().getNumDataModels(); i++) {
                    trueGraphs.add(simulation.getSimulation().getTrueGraph(i));
                }
                simulationGraphEditor.replace(trueGraphs);

                DataWrapper wrapper = new DataWrapper(new Parameters());
                wrapper.setDataModelList(simulation.getDataModelList());
                tabbedPane.setComponentAt(2, new DataEditor(wrapper, false, JTabbedPane.LEFT));

                showTab();
                firePropertyChange("modelChanged", null, null);
                break;
            case "refreshParameters":
                showTab();
                break;
        }
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

}
