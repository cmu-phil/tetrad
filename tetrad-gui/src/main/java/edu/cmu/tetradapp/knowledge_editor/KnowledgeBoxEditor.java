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
package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.ForbiddenGraphModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Edits knowledge of forbidden and required edges.
 *
 * @author kaalpurush
 */
public class KnowledgeBoxEditor extends JPanel {

    private static final long serialVersionUID = 959706288096545158L;

    private static final long EDGE_LIMIT = 100;

    private final Color UNSELECTED_BG = new Color(153, 204, 204);
    private final Color SELECTED_BG = new Color(255, 204, 102);

    private final Map<String, JLabel> labelMap = new HashMap<>();

    private final List<Node> vars;
    private final List<String> firstTierVars = new LinkedList<>();
    private final List<String> secondTierVars = new LinkedList<>();
    private final KnowledgeBoxModel knowledgeBoxModel;
    private IKnowledge knowledge;
    private KnowledgeWorkbench edgeWorkbench;
    private JPanel tiersPanel;
    private boolean showForbiddenExplicitly = false;
    private boolean showForbiddenByTiers = false;
    private boolean showRequired = false;
    private boolean showRequiredByGroups = false;
    private boolean showForbiddenByGroups = false;
    private final JTabbedPane tabbedPane;
    private int numTiers = 3;

    public KnowledgeBoxEditor(final ForbiddenGraphModel knowledgeBoxModel) {
        this((KnowledgeBoxModel) knowledgeBoxModel);
    }

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names
     * (that is, the list of all variable names to be considered, which may vary
     * from object to object even for the same knowledge), and possible source
     * graph. The source graph is used only to arrange nodes in the edge panel.
     */
    public KnowledgeBoxEditor(final KnowledgeBoxModel knowledgeBoxModel) {
        this.vars = knowledgeBoxModel.getVariables();
        this.knowledge = knowledgeBoxModel.getKnowledge();
        this.knowledgeBoxModel = knowledgeBoxModel;

        setLayout(new BorderLayout());
        final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        this.tabbedPane = tabbedPane;
        resetTabbedPane();

        add(tabbedPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(640, 500));

        add(menuBar(), BorderLayout.NORTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(final ComponentEvent e) {
                TetradLogger.getInstance().log("knowledge", "Edited Knowledge:");
                TetradLogger.getInstance().log("knowledge", KnowledgeBoxEditor.this.knowledge.toString());
            }
        });

        initComponents();
    }

    private void initComponents() {
        getKnowledge().getVariables().forEach(e -> this.labelMap.put(e, createJLabel(e)));
        getKnowledge().getVariablesNotInTiers().forEach(e -> this.labelMap.put(e, createJLabel(e)));
    }

    private JLabel createJLabel(final String name) {
        final JLabel label = new JLabel(String.format("  %s  ", name));
        label.setOpaque(true);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new CompoundBorder(new MatteBorder(2, 2, 2, 2, Color.WHITE), new LineBorder(Color.BLACK)));
        label.setForeground(Color.BLACK);
        label.setBackground(this.UNSELECTED_BG);

        return label;
    }

    private JMenuBar menuBar() {
        final JMenuBar menuBar = new JMenuBar();
        final JMenu file = new JMenu("File");
        menuBar.add(file);

        final JMenuItem loadKnowledge = new JMenuItem("Load Knowledge...");
        final JMenuItem saveKnowledge = new JMenuItem("Save Knowledge...");

        file.add(loadKnowledge);
        file.add(saveKnowledge);

        loadKnowledge.addActionListener((e) -> {
            final JFileChooser chooser = new JFileChooser();
            final String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            final int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                final IKnowledge knowledge = DataUtils.loadKnowledge(selectedFile, DelimiterType.WHITESPACE, "//");
                setKnowledge(knowledge);
                resetTabbedPane();
            } catch (final Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
                e1.printStackTrace();
            }
        });

        saveKnowledge.addActionListener((e) -> {
            final JFileChooser chooser = new JFileChooser();
            final String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            final int ret1 = chooser.showSaveDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                DataWriter.saveKnowledge(this.knowledge, new FileWriter(selectedFile));
            } catch (final Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        return menuBar;
    }

    public void resetTabbedPane() {
        this.tabbedPane.removeAll();
        this.tabbedPane.add("Tiers", tierDisplay());
        this.tabbedPane.add("Other Groups", new OtherGroupsEditor(this.knowledge, this.knowledge.getVariables()));
        this.tabbedPane.add("Edges", edgeDisplay());

        this.tabbedPane.addChangeListener((e) -> {
            final JTabbedPane pane = (JTabbedPane) e.getSource();
            if (pane.getSelectedIndex() == 0) {
                setNumDisplayTiers(Math.max(getNumTiers(), this.knowledge.getNumTiers()));
            } else if (pane.getSelectedIndex() == 2) {
                resetEdgeDisplay(null);
            }
        });
    }

    private Box tierDisplay() {
        if (getNumTiers() < 0) {
            int numTiers = getKnowledge().getNumTiers();
            final int _default = (int) (Math.pow(this.vars.size(), 0.5) + 1);
            numTiers = Math.max(numTiers, _default);
            setNumDisplayTiers(numTiers);
        }

        final Box b = Box.createVerticalBox();
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        final Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Not in tier:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("# Tiers = "));
        final SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(
                getNumTiers(), 2, 100, 1);
        spinnerNumberModel.addChangeListener((e) -> {
            final SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            final int numTiers = model.getNumber().intValue();

            setNumDisplayTiers(numTiers);
            setNumTiers(numTiers);
            model.setValue(numTiers);

            for (int i = getNumTiers(); i <= getKnowledge()
                    .getMaxTierForbiddenWithin(); i++) {
                getKnowledge().setTierForbiddenWithin(i, false);
            }

            notifyKnowledge();
        });

        final JSpinner spinner = new JSpinner(spinnerNumberModel);
        spinner.setMaximumSize(spinner.getPreferredSize());
        b1.add(spinner);
        b.add(b1);

        this.tiersPanel = new JPanel();
        this.tiersPanel.setLayout(new BorderLayout());
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);

        b.add(this.tiersPanel);

        final Box c = Box.createHorizontalBox();
        c.add(new JLabel("Use shift key to select multiple items."));
        c.add(Box.createGlue());
        b.add(c);

        return b;
    }

    private void setNumDisplayTiers(int numTiers) {
        if (numTiers < 2) {
            final int knowledgeTiers = getKnowledge().getNumTiers();
            final int defaultTiers = (int) (Math.pow(getVarNames().size(), 0.5) + 1);
            numTiers = Math.max(knowledgeTiers, defaultTiers);
        }

        setNumTiers(numTiers);

        for (int i = numTiers; i < getKnowledge().getNumTiers(); i++) {
            final List<String> vars = getKnowledge().getTier(i);

            for (final String var : vars) {
                getKnowledge().removeFromTiers(var);
            }
        }

        this.tiersPanel.removeAll();
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        this.tiersPanel.revalidate();
        this.tiersPanel.repaint();
    }

    /**
     * If the knowledge box sees interventional variables
     * it automatically places those variables in the first tier
     * and the rest of domain variables in second tier - Zhou
     */
    private void checkInterventionalVariables() {
        this.vars.forEach(e -> {
            if ((e.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS) || (e.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE)) {
                this.firstTierVars.add(e.getName());
            } else {
                if (e.getAttribute("fullyDeterminisedDomainVar") != null) {
                    // Also put domain variables that have the "fullyDeterminisedDomainVar" set as true into the first tier
                    if ((boolean) e.getAttribute("fullyDeterminisedDomainVar")) {
                        this.firstTierVars.add(e.getName());
                    }
                } else {
                    this.secondTierVars.add(e.getName());
                }
            }
        });
    }

    private Box getTierBoxes(final int numTiers) {
        // Handling interventional variables
        checkInterventionalVariables();

        // Only for dataset with interventional variables and the first time
        // we open the knowledge box - Zhou
        if (getKnowledge().isEmpty() && !this.firstTierVars.isEmpty()) {
            // Display interventional variables in first tier and the rest in second tier
            getKnowledge().setTier(0, this.firstTierVars);
            getKnowledge().setTier(1, this.secondTierVars);
        }

        for (final Node var : this.vars) {
            getKnowledge().addVariable(var.getName());
        }

        // Overall container
        final Box container = Box.createVerticalBox();

        // Vars not in tier. Reinitialize in case the variables in knowledge have changed.
        initComponents();
        final List<String> varsNotInTiers = getKnowledge().getVariablesNotInTiers();
        final JList<String> varsNotInTiersList = new DragDropList(varsNotInTiers, -1);
        varsNotInTiersList.setBorder(null);

        final Box varsNotInTiersBox = Box.createHorizontalBox();
        final JScrollPane jScrollPane1 = new JScrollPane(varsNotInTiersList);
        jScrollPane1.setPreferredSize(new Dimension(640, 50));
        varsNotInTiersBox.add(jScrollPane1);

        final Box tiersBox = Box.createVerticalBox();

        // Use this list so we can set the first tier forbidden within tier with interventional variables handling - Zhou
        final List<JCheckBox> forbiddenCheckboxes = new LinkedList<>();

        for (int tier = 0; tier < numTiers; tier++) {
            final Box textRow = Box.createHorizontalBox();
            textRow.add(new JLabel("Tier " + (tier + 1)));
            final int _tier = tier;

            textRow.add(Box.createHorizontalGlue());

            final JButton regexAdd = new JButton("Find");

            final JCheckBox forbiddenCheckbox = new JCheckBox("Forbid Within Tier", getKnowledge().isTierForbiddenWithin(_tier));

            final JCheckBox causesOnlyNextTierCheckbox = new JCheckBox("Can Cause Only Next Tier", getKnowledge().isOnlyCanCauseNextTier(_tier));

            final JComponent upReference = this;

            forbiddenCheckbox.addActionListener((e) -> {
                final JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setTierForbiddenWithin(_tier, checkbox.isSelected());
                } catch (final Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            forbiddenCheckboxes.add(forbiddenCheckbox);

            textRow.add(regexAdd);

            regexAdd.addActionListener((e) -> {
                final String regex = JOptionPane.showInputDialog("Search Cpdag");
                try {
                    getKnowledge().removeFromTiers(regex);
                    getKnowledge().addToTier(_tier, regex);
                } catch (final IllegalArgumentException iae) {
                    JOptionPane.showMessageDialog(upReference, iae.getMessage());
                }

                notifyKnowledge();

                this.tiersPanel.removeAll();
                this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
                this.tiersPanel.revalidate();
                this.tiersPanel.repaint();
            });

            textRow.add(forbiddenCheckbox);

            causesOnlyNextTierCheckbox.addActionListener((e) -> {
                final JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setOnlyCanCauseNextTier(_tier, checkbox.isSelected());
                } catch (final Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            if (tier + 2 < numTiers) textRow.add(causesOnlyNextTierCheckbox);

            tiersBox.add(textRow);

            final List<String> tierNames = getKnowledge().getTier(tier);

            final JList<String> tierList = new DragDropList(tierNames, tier);

            final Box tierBox = Box.createHorizontalBox();
            final JScrollPane jScrollPane = new JScrollPane(tierList);
            jScrollPane.setPreferredSize(new Dimension(600, 50));
            tierBox.add(jScrollPane);

            tiersBox.add(tierBox);
        }

        // Add all tiers to a scroll pane
        final JScrollPane tiersScrollPane = new JScrollPane(tiersBox);
        tiersScrollPane.setPreferredSize(new Dimension(640, 400));

        // Also check "Forbin Within Tier" for the first tier variables
        if (!this.firstTierVars.isEmpty()) {
            forbiddenCheckboxes.get(0).setSelected(true);
            getKnowledge().setTierForbiddenWithin(0, true);
        }

        // Finally add to container
        container.add(varsNotInTiersBox);
        container.add(Box.createVerticalStrut(5));
        container.add(tiersScrollPane);

        return container;
    }

    private JPanel edgeDisplay() {
        final KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        graph.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                notifyKnowledge();
            }
        });

        this.edgeWorkbench = new KnowledgeWorkbench(graph);
        resetEdgeDisplay(null);

        final JCheckBox showForbiddenByTiersCheckbox = new JCheckBox("Show Forbidden By Tiers", this.showForbiddenByTiers);
        final JCheckBox showForbiddenGroupsCheckBox = new JCheckBox("Show Forbidden by Groups", this.showForbiddenByGroups);
        final JCheckBox showForbiddenExplicitlyCheckbox = new JCheckBox("Show Forbidden Explicitly", this.showForbiddenExplicitly);


        final JCheckBox showRequiredGroupsCheckBox = new JCheckBox("Show Required by Groups", this.showRequiredByGroups);
        final JCheckBox showRequiredExplicitlyCheckbox = new JCheckBox("Show Required Explicitly", this.showRequired);


        showRequiredGroupsCheckBox.addActionListener((e) -> {
            final JCheckBox box = (JCheckBox) e.getSource();
            this.showRequiredByGroups = box.isSelected();
            resetEdgeDisplay(showRequiredGroupsCheckBox);
        });

        showForbiddenGroupsCheckBox.addActionListener((e) -> {
            final JCheckBox box = (JCheckBox) e.getSource();
            this.showForbiddenByGroups = box.isSelected();
            resetEdgeDisplay(showForbiddenGroupsCheckBox);
        });

        showForbiddenByTiersCheckbox.addActionListener((e) -> {
            final JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowForbiddenByTiers(checkBox.isSelected());
            resetEdgeDisplay(showForbiddenByTiersCheckbox);
        });

        showForbiddenExplicitlyCheckbox.addActionListener((e) -> {
            final JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowForbiddenExplicitly(checkBox.isSelected());
            resetEdgeDisplay(showForbiddenExplicitlyCheckbox);
        });

        showRequiredExplicitlyCheckbox.addActionListener((e) -> {
            final JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowRequired(checkBox.isSelected());
            resetEdgeDisplay(showRequiredExplicitlyCheckbox);
        });

        final JPanel workbenchPanel = new JPanel();
        workbenchPanel.setLayout(new BorderLayout());
        workbenchPanel.add(new JScrollPane(this.edgeWorkbench), BorderLayout.CENTER);
        workbenchPanel.setBorder(new TitledBorder("Forbidden and Required Edges"));

        final JPanel display = new JPanel();
        display.setPreferredSize(new Dimension(640, 450));
        display.setLayout(new BorderLayout());

        final JPanel b2 = new KnowledgeEditorToolbar(this.edgeWorkbench, this.edgeWorkbench.getSourceGraph());
        display.add(b2, BorderLayout.WEST);
        display.add(workbenchPanel, BorderLayout.CENTER);

        final Box showOptionsBox = Box.createVerticalBox();

        final Box forbiddenOptionsBox = Box.createHorizontalBox();
        forbiddenOptionsBox.add(showForbiddenByTiersCheckbox);
        forbiddenOptionsBox.add(showForbiddenGroupsCheckBox);
        forbiddenOptionsBox.add(showForbiddenExplicitlyCheckbox);
        forbiddenOptionsBox.add(Box.createHorizontalGlue());

        final Box requiredOptionsBox = Box.createHorizontalBox();
        requiredOptionsBox.add(showRequiredGroupsCheckBox);
        requiredOptionsBox.add(showRequiredExplicitlyCheckbox);
        requiredOptionsBox.add(Box.createHorizontalGlue());

        showOptionsBox.add(forbiddenOptionsBox);
        showOptionsBox.add(requiredOptionsBox);

        display.add(showOptionsBox, BorderLayout.SOUTH);

        return display;
    }

    private void resetEdgeDisplay(final JCheckBox checkBox) {
        final IKnowledge knowledge = getKnowledge();
        final KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());
        getVarNames().forEach(e -> {
            knowledge.addVariable(e);
            graph.addNode(new KnowledgeModelNode(e));
        });

        if (this.showRequiredByGroups) {
            final List<KnowledgeEdge> list = knowledge.getListOfRequiredEdges();
            if (list.size() > EDGE_LIMIT) {
                this.showRequiredByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                final String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    final String from = e.getFrom();
                    final String to = e.getTo();
                    if (knowledge.isRequiredByGroups(from, to)) {
                        final KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                                .getNode(from);
                        final KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                                .getNode(to);

                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.REQUIRED_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showForbiddenByGroups) {
            final List<KnowledgeEdge> list = knowledge.getListOfForbiddenEdges();
            if (list.size() > EDGE_LIMIT) {
                this.showForbiddenByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                final String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    final String from = e.getFrom();
                    final String to = e.getTo();
                    if (knowledge.isForbiddenByGroups(from, to)) {
                        final KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                                .getNode(from);
                        final KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                                .getNode(to);

                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.FORBIDDEN_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showRequired) {
            final List<KnowledgeEdge> list = knowledge.getListOfExplicitlyRequiredEdges();
            if (list.size() > EDGE_LIMIT) {
                this.showRequired = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                final String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    final String from = e.getFrom();
                    final String to = e.getTo();
                    final KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    final KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    if (!(fromNode == null || toNode == null)) {
                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.REQUIRED));
                    }
                });
            }
        }

        if (this.showForbiddenByTiers) {
            final List<KnowledgeEdge> list = knowledge.getListOfForbiddenEdges();
            if (list.size() > EDGE_LIMIT) {
                this.showForbiddenByTiers = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                final String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    final String from = e.getFrom();
                    final String to = e.getTo();
                    if (knowledge.isForbiddenByTiers(from, to)) {
                        KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                                .getNode(from);
                        KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                                .getNode(to);

                        if (fromNode == null) {
                            graph.addNode(new KnowledgeModelNode(from));
                            fromNode = (KnowledgeModelNode) graph.getNode(from);
                        }

                        if (toNode == null) {
                            graph.addNode(new KnowledgeModelNode(to));
                            toNode = (KnowledgeModelNode) graph.getNode(to);
                        }

                        final KnowledgeModelEdge knowledgeModelEdge = new KnowledgeModelEdge(
                                fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_BY_TIERS);

                        graph.addEdge(knowledgeModelEdge);
                    }
                });
            }
        }

        if (this.showForbiddenExplicitly) {
            final List<KnowledgeEdge> list = knowledge.getListOfExplicitlyForbiddenEdges();
            if (list.size() > EDGE_LIMIT) {
                this.showForbiddenExplicitly = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                final String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    final String from = e.getFrom();
                    final String to = e.getTo();
                    final KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    final KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    final KnowledgeModelEdge edge = new KnowledgeModelEdge(fromNode,
                            toNode, KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
                    if (!graph.containsEdge(edge)) {
                        graph.addEdge(edge);
                    }
                });
            }
        }

        final boolean arrangedAll = GraphUtils.arrangeBySourceGraph(graph,
                this.edgeWorkbench.getGraph());

        if (!arrangedAll) {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        this.edgeWorkbench.setGraph(graph);
        notifyKnowledge();
    }

    private void notifyKnowledge() {
        firePropertyChange("modelChanged", null, null);
    }

    private IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
        this.knowledgeBoxModel.setKnowledge(knowledge);
    }

    private List<String> getVarNames() {
        return this.knowledge.getVariables();
    }

    private void setShowForbiddenExplicitly(final boolean showForbiddenExplicitly) {
        this.showForbiddenExplicitly = showForbiddenExplicitly;
    }

    private void setShowRequired(final boolean showRequired) {
        this.showRequired = showRequired;
    }

    private void setShowForbiddenByTiers(final boolean showForbiddenByTiers) {
        this.showForbiddenByTiers = showForbiddenByTiers;
    }

    private int getNumTiers() {
        return this.numTiers;
    }

    private void setNumTiers(final int numTiers) {
        this.numTiers = numTiers;
    }

    private class DragDropList extends JList<String> {

        private static final long serialVersionUID = 7240458207688841986L;

        private final List<String> items;
        private final int tier;

        public DragDropList(final List<String> items, final int tier) {
            this.items = items;
            this.tier = tier;

            initComponents();
        }

        private void initComponents() {
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            setDropMode(DropMode.ON_OR_INSERT);
            setDragEnabled(true);
            setCellRenderer((JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) -> {
                JLabel label = KnowledgeBoxEditor.this.labelMap.get(value);
                if (label == null) {
                    label = new JLabel();
                }

                label.setBackground(isSelected ? KnowledgeBoxEditor.this.SELECTED_BG : KnowledgeBoxEditor.this.UNSELECTED_BG);

                return label;
            });
            setTransferHandler(new TransferHandler() {

                private static final long serialVersionUID = 3109256773218160485L;

                @Override
                public boolean canImport(final TransferHandler.TransferSupport info) {
                    return info.isDataFlavorSupported(ListTransferable.DATA_FLAVOR);
                }

                @Override
                protected Transferable createTransferable(final JComponent c) {
                    final JList source = (JList) c;

                    List list = source.getSelectedValuesList();
                    if (list == null) {
                        getToolkit().beep();
                        list = Collections.EMPTY_LIST;
                    }

                    return new ListTransferable(list);
                }

                @Override
                public int getSourceActions(final JComponent c) {
                    return TransferHandler.COPY_OR_MOVE;
                }

                @Override
                public boolean importData(final TransferHandler.TransferSupport info) {
                    if (!info.isDrop()) {
                        return false;
                    }

                    final JList<String> source = (JList<String>) info.getComponent();
                    final DefaultListModel listModel = (DefaultListModel) source.getModel();
                    final IKnowledge knowledge = getKnowledge();

                    final Transferable transferable = info.getTransferable();
                    try {
                        final List<String> list = (List<String>) transferable.getTransferData(ListTransferable.DATA_FLAVOR);
                        list.forEach(name -> {
                            if (DragDropList.this.tier >= 0) {
                                try {
                                    knowledge.removeFromTiers(name);
                                    knowledge.addToTier(DragDropList.this.tier, name);

                                    notifyKnowledge();

                                    listModel.addElement(name);
                                    sort(listModel);
                                } catch (final IllegalStateException e) {
                                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
                                }
                            } else {
                                knowledge.removeFromTiers(name);

                                notifyKnowledge();
                                listModel.addElement(name);
                                sort(listModel);
                            }
                        });
                    } catch (final IOException | UnsupportedFlavorException exception) {
                        exception.printStackTrace(System.err);
                        return false;
                    }

                    return true;
                }

                @Override
                protected void exportDone(final JComponent c, final Transferable data, final int action) {
                    if (action == TransferHandler.MOVE) {
                        final JList<String> source = (JList<String>) c;
                        final DefaultListModel<String> listModel = (DefaultListModel<String>) source.getModel();
                        try {
                            final List<String> list = (List<String>) data.getTransferData(ListTransferable.DATA_FLAVOR);
                            list.forEach(listModel::removeElement);
                        } catch (final IOException | UnsupportedFlavorException ignored) {
                        }
                    }
                }

            });

            final DefaultListModel<String> listModel = new DefaultListModel<>();
            this.items.forEach(listModel::addElement);
            setModel(listModel);
        }

        private void sort(final DefaultListModel<String> listModel) {
            final Object[] elements = listModel.toArray();
            final String[] values = new String[elements.length];
            for (int i = 0; i < elements.length; i++) {
                values[i] = (String) elements[i];
            }

            Arrays.sort(values, (o1, o2) -> {
                String[] tokens1 = o1.split(":");
                String[] tokens2 = o2.split(":");

                if (tokens1.length == 1) {
                    tokens1 = new String[]{tokens1[0], "0"};
                }

                if (tokens2.length == 1) {
                    tokens2 = new String[]{tokens2[0], "0"};
                }

                final int i1 = tokens1[1].compareTo(tokens2[1]);
                final int i0 = tokens1[0].compareTo(tokens2[0]);

                if (i1 == 0) {
                    return i0;
                } else {
                    return i1;
                }
            });

            listModel.clear();

            Arrays.stream(values).forEach(listModel::addElement);
        }
    }
}
