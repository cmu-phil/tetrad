package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.ui.PaddingPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Displays a simulation and lets the user create new simulations. A simulation is an ordered pair of a Graph and a list
 * of DataSets. These can be created in a variety of ways, either standalone or taking graphs, IM's, or PM's as parents,
 * and using the information those objects contain. For a Simulation you need a RandomGraph and you need to pick a
 * particular style of Simulation.
 *
 * @author josephramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class SimulationEditor extends JPanel implements KnowledgeEditable, PropertyChangeListener,
        ISelectedModel {

    @Serial
    private static final long serialVersionUID = -8424284512836439370L;

    /**
     * The tabbed pane containing the simulation editor's tabs.
     */
    final JTabbedPane tabbedPane = new JTabbedPane();

    /**
     * The simulation being edited.
     */
    private final Simulation simulation;

    /**
     * The data editor.
     */
    private final DataEditor dataEditor;

    /**
     * The simulation graph editor.
     */
    private final SimulationGraphEditor simulationGraphEditor;

    /**
     * The parameter tab.
     */
    private final ParameterTab parameterTab;

    /**
     * Constructs the data editor with an empty list of data displays.
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public SimulationEditor(Simulation simulation) {
        this.simulation = simulation;
        this.dataEditor = createDataEditor(simulation);
        this.simulationGraphEditor = createSimulationGraphEditor(simulation);
        this.parameterTab = new ParameterTab(simulation);

        initComponents();
        showTab();
    }

    private void initComponents() {
        this.parameterTab.addPropertyChangeListener(this);

        setLayout(new BorderLayout());

        add(createMenuBar(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);

        setPreferredSize(new Dimension(900, 700));
    }

    private JComponent createMainContent() {
        this.tabbedPane.removeAll();

        // Do NOT scroll the whole ParameterTab, or its bottom button panel
        // becomes part of the scrollable content and can disappear.
        this.tabbedPane.addTab("Simulation Setup", wrapTabNoScroll(new PaddingPanel(this.parameterTab)));

        // These benefit from scrolling.
        this.tabbedPane.addTab("True Graph", wrapTabWithScroll(this.simulationGraphEditor));

        this.tabbedPane.addTab("Data", wrapTabWithScroll(this.dataEditor));

        this.tabbedPane.setFocusable(false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(this.tabbedPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Wraps content in a simple host panel so it fills the tab cleanly.
     */
    private JComponent wrapTabNoScroll(JComponent content) {
        JPanel host = new JPanel(new BorderLayout());
        host.add(content, BorderLayout.CENTER);
        return host;
    }

    /**
     * Wraps content in a scroll pane, then in a host panel so it fills the tab area.
     */
    private JComponent wrapTabWithScroll(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(content);
//        scrollPane.setBorder(BorderFactory.createEmptyBorder());

//        JPanel host = new JPanel(new BorderLayout());
//        host.add(scrollPane, BorderLayout.CENTER);
        return scrollPane;
    }

    private void showTab() {
        if (this.simulation.getSimulation() == null) {
            this.tabbedPane.setEnabledAt(0, true);
            this.tabbedPane.setEnabledAt(1, false);
            this.tabbedPane.setEnabledAt(2, false);
        } else {
            if (!this.simulation.getDataModelList().isEmpty()) {
                this.tabbedPane.setEnabledAt(0, true);
                this.tabbedPane.setEnabledAt(1, true);
                this.tabbedPane.setEnabledAt(2, true);

                if (!this.parameterTab.isInitial()) {
                    this.tabbedPane.setSelectedIndex(1);
                }
            } else {
                this.tabbedPane.setEnabledAt(0, true);
                this.tabbedPane.setEnabledAt(1, false);
                this.tabbedPane.setEnabledAt(2, false);
            }
        }
    }

    private SimulationGraphEditor createSimulationGraphEditor(Simulation simulation) {
        SimulationGraphEditor graphEditor = new SimulationGraphEditor(Collections.emptyList());

        if (simulation.getSimulation() != null) {
            List<Graph> trueGraphs = new ArrayList<>();
            for (int i = 0; i < simulation.getSimulation().getNumDataModels(); i++) {
                trueGraphs.add(simulation.getSimulation().getTrueGraph(i));
            }
            graphEditor.replace(trueGraphs);
        }

        return graphEditor;
    }

    private DataEditor createDataEditor(Simulation simulation) {
        edu.cmu.tetrad.algcomparison.simulation.Simulation sim = simulation.getSimulation();

        if (sim == null) {
            return new DataEditor(SwingConstants.LEFT, false);
        } else {
            DataModelList dataModelList = new DataModelList();
            for (int i = 0; i < sim.getNumDataModels(); i++) {
                dataModelList.add(sim.getDataModel(i));
            }

            DataWrapper wrapper = new DataWrapper(new Parameters());
            wrapper.setDataModelList(dataModelList);

            return new DataEditor(wrapper, false, SwingConstants.LEFT);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuItem saveSimulation = new JMenuItem("Save Simulation");
        saveSimulation.addActionListener(createSaveSimulationActionListener());

        JMenu file = new JMenu("File");
        file.add(saveSimulation);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);

        JMenu data = new JMenu("Data");

        data.add(new DataAuditAction(this));
        data.add(new NonlinearityChecksAction(this));
        data.add(new DescriptiveStatsAction(this));
        data.add(new PlotMatrixAction(this));
        data.add(new QQPlotAction(this));
        data.add(new CheckIndependenceFacts(this));
        menuBar.add(data);

        JMenu graph = new JMenu("Graph");
        graph.add(new CheckMSeparationFacts(this.simulationGraphEditor));
        menuBar.add(graph);

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

            File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null) {
                return;
            }

            new Comparison().saveToFilesSingleSimulation(
                    selectedFile.getAbsolutePath(),
                    this.simulation.getSimulation(),
                    this.simulation.getParams()
            );
        };
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "modelChanged": {
                List<Graph> trueGraphs = new ArrayList<>();
                if (this.simulation.getSimulation() != null) {
                    for (int i = 0; i < this.simulation.getSimulation().getNumDataModels(); i++) {
                        trueGraphs.add(this.simulation.getSimulation().getTrueGraph(i));
                    }
                }
                this.simulationGraphEditor.replace(trueGraphs);

                DataModelList list = this.simulation.getDataModelList();

                if (list.getSelectedModel() == null && !list.isEmpty()) {
                    list.setSelectedModel(list.get(0));
                }

                this.dataEditor.replace(list);

                showTab();
                firePropertyChange("modelChanged", null, null);
                break;
            }

            case "refreshParameters":
                showTab();
                break;
        }
    }

    @Override
    public Knowledge getKnowledge() {
        return null;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
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
    public DataModel getSelectedDataModel() {
        return this.dataEditor.getSelectedDataModel();
    }

    public Graph getSelectedGraph() {
        return this.simulationGraphEditor.getSelectedGraph();
    }
}