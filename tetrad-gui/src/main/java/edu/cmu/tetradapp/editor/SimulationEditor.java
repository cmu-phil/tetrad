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
import edu.cmu.tetrad.algcomparison.graph.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;

/**
 * Displays a simulation and lets the user create new simulations. A simulation
 * is an ordered pair of a Graph and a list of DataSets. These can be created in
 * a variety of ways, either standalone or taking graphs, IM's, or PM's as
 * parents, and using the information those objects contain. For a Simulation
 * you need a RandomGraph and you need to pick a particular style of Simulation.
 *
 * @author Joseph Ramsey
 */
public final class SimulationEditor extends JPanel implements KnowledgeEditable,
        PropertyChangeListener {

    private final JButton simulateButton = new JButton("Simulate");
    private final JComboBox<String> graphsDropdown = new JComboBox<>();
    private final JComboBox<String> simulationsDropdown = new JComboBox<>();

    //==========================CONSTUCTORS===============================//

    /**
     * Constructs the data editor with an empty list of data displays.
     */
    public SimulationEditor(final Simulation simulation) {
        final GraphSelectionEditor graphEditor;
        DataEditor dataEditor;

        if (simulation.getSimulation() != null) {
            List<Graph> trueGraphs = new ArrayList<>();
            DataModelList dataModelList = new DataModelList();

            int numDataSets = simulation.getSimulation().getNumDataModels();

            for (int i = 0; i < numDataSets; i++) {
                trueGraphs.add(simulation.getSimulation().getTrueGraph(i));
                dataModelList.add(simulation.getSimulation().getDataModel(i));
            }

            graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(trueGraphs, new Parameters()));
            DataWrapper wrapper = new DataWrapper(new Parameters());
            wrapper.setDataModelList(dataModelList);
            dataEditor = new DataEditor(wrapper, false, JTabbedPane.LEFT);

            if (simulation.getSimulation() instanceof BooleanGlassSimulation) {
                simulation.setFixedGraph(true);
            }
        } else {
            graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(Collections.<Graph>emptyList(), new Parameters()));
            dataEditor = new DataEditor(JTabbedPane.LEFT);
            simulation.setSimulation(new BayesNetSimulation(new RandomForward()), simulation.getParams());
            simulation.setFixedSimulation(false);
        }

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Simulation Setup", getParametersPane(simulation, simulation.getSimulation(),
                simulation.getParams()));
        tabbedPane.addTab("True Graph", graphEditor);
        tabbedPane.addTab("Data", dataEditor);
        tabbedPane.setPreferredSize(new Dimension(900, 600));

        final String[] graphItems = new String[]{
                "Random Foward DAG",
                "Scale Free DAG",
                "Cyclic, constructed from small loops",
                "Random One Factor MIM",
                "Random Two Factor MIM"
        };

        for (String item : graphItems) {
            graphsDropdown.addItem(item);
        }

        graphsDropdown.setSelectedItem(simulation.getParams().getString("graphsDropdownPreference",
                graphItems[0]));

        final String[] simulationItems = getSimulationItems(simulation);

        for (String item : simulationItems) {
            simulationsDropdown.addItem(item);
        }

        simulationsDropdown.setSelectedItem(simulation.getParams().getString("simulationsDropdownPreference",
                simulationItems[0]));

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
                        try {
                            _simulation.createData(simulation.getParams());
                        } catch (Exception e) {
                            e.printStackTrace();
                            Throwable cause = e;
                            if (e.getCause() != null) {
                                cause = e.getCause();
                            }

                            if (cause.getMessage() == null || cause.getMessage().trim().isEmpty()) {
                                throw new IllegalArgumentException(
                                        "Exception in creating data. Check model setup or parameter settings.");
                            } else {
                                throw new IllegalArgumentException(cause.getMessage());
                            }
                        }

                        firePropertyChange("modelChanged", null, null);

                        List<Graph> graphs = new ArrayList<>();
                        for (int i = 0; i < _simulation.getNumDataModels(); i++) {
                            graphs.add(_simulation.getTrueGraph(i));
                        }

                        graphEditor.replace(graphs);
                        DataWrapper wrapper = new DataWrapper(new Parameters());
                        wrapper.setDataModelList(simulation.getDataModelList());
                        tabbedPane.setComponentAt(2, new DataEditor(wrapper, false, JTabbedPane.LEFT));
                        tabbedPane.setSelectedIndex(2);
                    }
                };
            }
        });

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");

        JMenuItem loadSimulation = new JMenuItem("Load Simulation");
        JMenuItem saveSimulation = new JMenuItem("Save Simulation");

        loadSimulation.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
                chooser.setCurrentDirectory(new File(sessionSaveLocation));
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());
                if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                    return;
                }

                File file = chooser.getSelectedFile();

                if (file == null) {
                    return;
                }

                // Check to make sure the directory has the right structure.
                File[] files = file.listFiles();

                if (files == null) {
                    JOptionPane.showMessageDialog((SimulationEditor.this),
                            "That wasn't a directory");
                    return;
                }

                boolean correctStructure = isCorrectStructure(files);

                if (!correctStructure) {
                    int count = 0;
                    File thisOne = null;

                    for (File _file : files) {
                        File[] _files = _file.listFiles();

                        if (_files == null) {
                            continue;
                        }

                        if (isCorrectStructure(_files)) {
                            count++;
                            thisOne = _file;
                        }
                    }

                    if (thisOne == null) {
                        JOptionPane.showMessageDialog((SimulationEditor.this),
                                "That file was not a simulation, and none of its subdirectories was either. "
                                        + "\nNeed a directory with a 'data' subdirectory, a 'graph' subdirectory, "
                                        + "\nand a 'parameters.txt' file.");
                        return;
                    }

                    if (count > 1) {
                        JOptionPane.showMessageDialog((SimulationEditor.this),
                                "More than one subdirectory of that directory was a simulation; please select "
                                        + "\none of the subdirectories.");
                        return;
                    }

                    file = thisOne;
                }

                edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation
                        = new LoadContinuousDataAndGraphs(file.getPath());
                _simulation.createData(simulation.getParams());

                Graph trueGraph = _simulation.getTrueGraph(0);
                edu.cmu.tetrad.graph.GraphUtils.circleLayout(trueGraph, 225, 200, 150);
                List<Graph> graphs = new ArrayList<>();
                for (int i = 0; i < _simulation.getNumDataModels(); i++) {
                    graphs.add(_simulation.getTrueGraph(i));
                }

                graphEditor.replace(graphs);
                DataWrapper wrapper = new DataWrapper(new Parameters());

                DataModelList list = new DataModelList();

                for (int i = 0; i < _simulation.getNumDataModels(); i++) {
                    list.add(_simulation.getDataModel(i));
                }

                wrapper.setDataModelList(list);
                tabbedPane.setComponentAt(2, new DataEditor(wrapper, false, JTabbedPane.LEFT));

                simulation.setSimulation(_simulation, simulation.getParams());

                resetPanel(simulation, graphItems, simulationItems, tabbedPane);
            }

            private boolean isCorrectStructure(File[] files) {
                boolean hasDataDir = false;
                boolean hasGraphDir = false;
                boolean hasParametersFile = false;

                for (File _file : files) {
                    if (_file.isDirectory() && _file.getName().equals("data")) {
                        hasDataDir = true;
                    }
                    if (_file.isDirectory() && _file.getName().equals("graph")) {
                        hasGraphDir = true;
                    }
                    if (_file.isFile() && _file.getName().equals("parameters.txt")) {
                        hasParametersFile = true;
                    }
                }

                return hasDataDir && hasGraphDir && hasParametersFile;
            }
        });

        saveSimulation.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
                chooser.setCurrentDirectory(new File(sessionSaveLocation));
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int ret1 = chooser.showSaveDialog(JOptionUtils.centeringComp());
                if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                    return;
                }

                final File file = chooser.getSelectedFile();

                if (file == null) {
                    return;
                }

//                if (file.listFiles().length != 0) {
//                    JOptionPane.showMessageDialog((SimulationEditor.this),
//                            "That wasn't a a new or empty directory; try typing a name for the directory\n" +
//                                    "or creating an empty directory.");
//                    return;
//                }
                new Comparison().saveToFiles(file.getAbsolutePath(), simulation.getSimulation(),
                        simulation.getParams());
            }
        });

        file.addSeparator();

        file.add(loadSimulation);
        file.add(saveSimulation);

        menuBar.add(file);

        add(menuBar, BorderLayout.NORTH);

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        List<Graph> graphs = new ArrayList<>();
        for (int i = 0; i < _simulation.getNumDataModels(); i++) {
            graphs.add(_simulation.getTrueGraph(i));
        }

        graphEditor.replace(graphs);
        DataWrapper wrapper = new DataWrapper(new Parameters());
        wrapper.setDataModelList(simulation.getDataModelList());
        tabbedPane.setComponentAt(2, new DataEditor(wrapper, false, JTabbedPane.LEFT));

        if (simulation.getDataModelList().size() > 0) {
            tabbedPane.setSelectedIndex(2);
        }
    }

    private void resetPanel(Simulation simulation, String[] graphItems, String[] simulationItems, JTabbedPane tabbedPane) {
        RandomGraph randomGraph;

        if (simulation.getSourceGraph() != null) {
            randomGraph = new SingleGraph(simulation.getSourceGraph());
        } else {
            randomGraph = new SingleGraph(new EdgeListGraph());
        }

        if (!simulation.isFixedGraph()) {
            String graphItem = (String) graphsDropdown.getSelectedItem();
            simulation.getParams().set("graphsDropdownPreference", graphItem);

            if (graphItem.equals(graphItems[0])) {
                randomGraph = new RandomForward();
            } else if (graphItem.equals(graphItems[1])) {
                randomGraph = new ScaleFree();
            } else if (graphItem.equals(graphItems[2])) {
                randomGraph = new Cyclic();
            } else if (graphItem.equals(graphItems[3])) {
                randomGraph = new RandomSingleFactorMim();
            } else if (graphItem.equals(graphItems[4])) {
                randomGraph = new RandomTwoFactorMim();
            } else {
                throw new IllegalArgumentException("Unrecognized simulation type: " + graphItem);
            }
        }

        if (!simulation.isFixedSimulation()) {
            if (simulation.getSourceGraph() != null) {
                String simulationItem = (String) simulationsDropdown.getSelectedItem();
                simulation.getParams().set("simulationsDropdownPreference", simulationItem);
                simulation.setFixedGraph(false);

                if (randomGraph instanceof SingleGraph) {
                    simulation.setFixedGraph(true);
                }

                if (simulationItem.equals(simulationItems[0])) {
                    simulation.setSimulation(new BayesNetSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[1])) {
                    simulation.setSimulation(new SemSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[2])) {
                    simulation.setSimulation(new LinearFisherModel(randomGraph), simulation.getParams());
//                } else if (simulationItem.equals(simulationItems[3])) {
//                    simulation.setSimulation(new GeneralSemSimulationSpecial1(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[3])) {
                    simulation.setSimulation(new LeeHastieSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[4])) {
                    simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[5])) {
                    simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph), simulation.getParams());
                } else {
                    throw new IllegalArgumentException("Unrecognized simulation type: " + simulationItem);
                }

            } else {
                String simulationItem = (String) simulationsDropdown.getSelectedItem();
                simulation.getParams().set("simulationsDropdownPreference", simulationItem);
                simulation.setFixedGraph(false);

                if (randomGraph instanceof SingleGraph) {
                    simulation.setFixedGraph(true);
                }

                if (simulationItem.equals(simulationItems[0])) {
                    simulation.setSimulation(new BayesNetSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[1])) {
                    simulation.setSimulation(new SemSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[2])) {
                    simulation.setSimulation(new LinearFisherModel(randomGraph, simulation.getInputDataModelList()),
                            simulation.getParams());
//                } else if (simulationItem.equals(simulationItems[3])) {
//                    simulation.setSimulation(new GeneralSemSimulationSpecial1(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[3])) {
                    simulation.setSimulation(new LeeHastieSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[4])) {
                    simulation.setSimulation(new ConditionalGaussianSimulation(randomGraph), simulation.getParams());
                } else if (simulationItem.equals(simulationItems[5])) {
                    simulation.setSimulation(new TimeSeriesSemSimulation(randomGraph), simulation.getParams());
//                } else if (simulationItem.equals(simulationItems[6])) {
//                    simulation.setSimulation(new BooleanGlassSimulation(randomGraph), simulation.getParams());
                    simulation.setFixedGraph(true);
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
                        "Bayes net",};
            } else if (simulation.getSimulation() instanceof SemSimulation) {
                simulationItems = new String[]{
                        "Structural Equation Model"
                };
            } else if (simulation.getSimulation() instanceof LinearFisherModel) {
                simulationItems = new String[]{
                        "Linear Fisher Model"
                };
            } else if (simulation.getSimulation() instanceof StandardizedSemSimulation) {
                simulationItems = new String[]{
                        "Standardized Structural Equation Model"
                };
            } else if (simulation.getSimulation() instanceof GeneralSemSimulation) {
                simulationItems = new String[]{
                        "General Structural Equation Model",};
            } else if (simulation.getSimulation() instanceof LoadContinuousDataAndGraphs) {
                simulationItems = new String[]{
                        "Loaded From Files",};
            } else {
                throw new IllegalStateException("Not expecting that model type: "
                        + simulation.getSimulation().getClass());
            }
        } else {
//            if (simulation.getSimulation() instanceof TakesData) {
//                simulationItems = new String[]{
//                        "Linear Fisher Model",
//                };
//            } else
            if (simulation.getSourceGraph() != null) {
                simulationItems = new String[]{
                        "Bayes net",
                        "Structural Equation Model",
                        "Linear Fisher Model",
                        //                        "General Structural Equation Model Special",
                        "Lee & Hastie",
                        "Conditional Gaussian",
                        "Time Series"
                };
            } else {
                simulationItems = new String[]{
                        "Bayes net",
                        "Structural Equation Model",
                        "Linear Fisher Model",
                        //                        "General Structural Equation Model Special",
                        "Lee & Hastie",
                        "Conditional Gaussian",
                        "Time Series", //                        "Boolean Glass"
                };
            }
        }

        return simulationItems;
    }

    private Box getParametersPane(Simulation _simulation,
                                  edu.cmu.tetrad.algcomparison.simulation.Simulation simulation,
                                  Parameters parameters) {
        JScrollPane scroll;

        if (simulation != null) {
            List<String> _params = simulation.getParameters();
            ParameterPanel comp = new ParameterPanel(_params, parameters);
            scroll = new JScrollPane(comp);
        } else {
            scroll = new JScrollPane();
        }

        boolean fixedGraph = _simulation.isFixedGraph();
        graphsDropdown.setEnabled(!fixedGraph);
        simulationsDropdown.setEnabled(!_simulation.isFixedSimulation());

        scroll.setPreferredSize(scroll.getMaximumSize());

        Box c = Box.createVerticalBox();

        if (!fixedGraph) {
            Box f = Box.createHorizontalBox();
            JLabel lf = new JLabel("Type of Graph: ");
            lf.setFont(new Font("Dialog", Font.BOLD, 13));
            f.add(lf);
            f.add(Box.createGlue());
            graphsDropdown.setMaximumSize(graphsDropdown.getPreferredSize());
            f.add(graphsDropdown);
            c.add(f);
        }

        Box g = Box.createHorizontalBox();
        JLabel lg = new JLabel("Type of Simulation Model: ");
        lg.setFont(new Font("Dialog", Font.BOLD, 13));
        g.add(lg);
        g.add(Box.createGlue());
        simulationsDropdown.setMaximumSize(simulationsDropdown.getPreferredSize());
        g.add(simulationsDropdown);
        c.add(g);

        c.add(Box.createVerticalStrut(15));

        Box d0 = Box.createHorizontalBox();
        JLabel label0 = new JLabel("Parameters for your simulation are listed below. Please adjust the parameter values.");
        label0.setFont(new Font("Dialog", Font.BOLD, 13));
        d0.add(label0);
        d0.add(Box.createHorizontalGlue());
        c.add(d0);
        c.add(Box.createVerticalStrut(10));

        Box e = Box.createHorizontalBox();
        e.add(Box.createHorizontalGlue());
        e.add(c);
        e.add(Box.createHorizontalGlue());

        c.add(scroll);

        Box d6 = Box.createHorizontalBox();
        d6.add(Box.createHorizontalGlue());
        d6.add(simulateButton);
        d6.add(Box.createHorizontalGlue());
        c.add(d6);

        Box b = Box.createHorizontalBox();
        b.add(c);
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
