///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.ForbiddenGraphModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.RemoveNonSkeletonEdgesModel;
import org.apache.commons.math3.util.FastMath;

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
import java.io.Serial;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Edits knowledge of forbidden and required edges.
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public class KnowledgeBoxEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 959706288096545158L;

    /**
     * Edge limit for displaying edges in the edge panel
     */
    private static final long EDGE_LIMIT = 100;

    /**
     * The background color for unselected labels.
     */
    private final Color UNSELECTED_BG = new Color(153, 204, 204);

    /**
     * The background color for selected labels.
     */
    private final Color SELECTED_BG = new Color(255, 204, 102);

    /**
     * Map from variable names to labels.
     */
    private final Map<String, JLabel> labelMap = new HashMap<>();

    /**
     * The variables in the knowledge.
     */
    private final List<Node> vars;

    /**
     * The variables in the first tier.
     */
    private final List<String> firstTierVars = new LinkedList<>();

    /**
     * The variables in the second tier.
     */
    private final List<String> secondTierVars = new LinkedList<>();

    /**
     * The knowledge box model.
     */
    private final KnowledgeBoxModel knowledgeBoxModel;

    /**
     * The tabbed pane.
     */
    private final JTabbedPane tabbedPane;

    /**
     * The knowledge.
     */
    private Knowledge knowledge;

    /**
     * The edge workbench.
     */
    private KnowledgeWorkbench edgeWorkbench;

    /**
     * The number of tiers to display.
     */
    private JPanel tiersPanel;

    /**
     * True if edges explicitly forbidden should be shown.
     */
    private boolean showForbiddenExplicitly;

    /**
     * True if edges required by groups should be shown.
     */
    private boolean showForbiddenByTiers;

    /**
     * True if edges required by groups should be shown.
     */
    private boolean showRequired;

    /**
     * True if edges required by groups should be shown.
     */
    private boolean showRequiredByGroups;

    /**
     * True if edges forbidden by groups should be shown.
     */
    private boolean showForbiddenByGroups;

    /**
     * The number of tiers to display.
     */
    private int numTiers = 3;

    /**
     * <p>Constructor for KnowledgeBoxEditor.</p>
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.ForbiddenGraphModel} object
     */
    public KnowledgeBoxEditor(ForbiddenGraphModel knowledgeBoxModel) {
        this((KnowledgeBoxModel) knowledgeBoxModel);
    }

    /**
     * <p>Constructor for KnowledgeBoxEditor.</p>
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.RemoveNonSkeletonEdgesModel} object
     */
    public KnowledgeBoxEditor(RemoveNonSkeletonEdgesModel knowledgeBoxModel) {
        this((KnowledgeBoxModel) knowledgeBoxModel);
    }

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names (that is, the list of all variable names to
     * be considered, which may vary from object to object even for the same knowledge), and possible source graph. The
     * source graph is used only to arrange nodes in the edge panel.
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public KnowledgeBoxEditor(KnowledgeBoxModel knowledgeBoxModel) {
        this.vars = knowledgeBoxModel.getVariables();
        this.knowledge = knowledgeBoxModel.getKnowledge();
        this.knowledgeBoxModel = knowledgeBoxModel;

        setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.LEFT);
        this.tabbedPane = tabbedPane;
        resetTabbedPane();

        add(tabbedPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(640, 500));

        add(menuBar(), BorderLayout.NORTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                TetradLogger.getInstance().forceLogMessage("Edited Knowledge:");
                String message = KnowledgeBoxEditor.this.knowledge.toString();
                TetradLogger.getInstance().forceLogMessage(message);
            }
        });

        initComponents();

        setNumDisplayTiers(this.knowledge.getNumTiers());
    }

    private void initComponents() {
        getKnowledge().getVariables().forEach(e -> this.labelMap.put(e, createJLabel(e)));
        getKnowledge().getVariablesNotInTiers().forEach(e -> this.labelMap.put(e, createJLabel(e)));
    }

    private JLabel createJLabel(String name) {
        JLabel label = new JLabel(String.format("  %s  ", name));
        label.setOpaque(true);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new CompoundBorder(new MatteBorder(2, 2, 2, 2, Color.WHITE), new LineBorder(Color.BLACK)));
        label.setForeground(Color.BLACK);
        label.setBackground(this.UNSELECTED_BG);

        return label;
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);

        JMenuItem loadKnowledge = new JMenuItem("Load Knowledge...");
        JMenuItem saveKnowledge = new JMenuItem("Save Knowledge...");

        file.add(loadKnowledge);
        file.add(saveKnowledge);

        loadKnowledge.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                Knowledge knowledge = SimpleDataLoader.loadKnowledge(selectedFile, DelimiterType.WHITESPACE, "//");
                setKnowledge(knowledge);
                resetTabbedPane();
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
                e1.printStackTrace();
            }
        });

        saveKnowledge.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showSaveDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                DataWriter.saveKnowledge(this.knowledge, new FileWriter(selectedFile));
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        return menuBar;
    }

    /**
     * <p>resetTabbedPane.</p>
     */
    public void resetTabbedPane() {
        this.tabbedPane.removeAll();
        this.tabbedPane.add("Tiers", tierDisplay());
        this.tabbedPane.add("Other Groups", new OtherGroupsEditor(this.knowledge, this.knowledge.getVariables()));
        this.tabbedPane.add("Edges", edgeDisplay());

        this.tabbedPane.addChangeListener((e) -> {
            JTabbedPane pane = (JTabbedPane) e.getSource();
            if (pane.getSelectedIndex() == 0) {
                setNumDisplayTiers(FastMath.max(getNumTiers(), this.knowledge.getNumTiers()));
            } else if (pane.getSelectedIndex() == 2) {
                resetEdgeDisplay(null);
            }
        });
    }

    private Box tierDisplay() {
        if (getNumTiers() < 0) {
            int numTiers = getKnowledge().getNumTiers();
            int _default = (int) (FastMath.pow(this.vars.size(), 0.5) + 1);
            numTiers = FastMath.max(numTiers, _default);
            setNumDisplayTiers(numTiers);
        }

        Box b = Box.createVerticalBox();
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Not in tier:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("# Tiers = "));
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(
                getNumTiers(), 2, 100, 1);
        spinnerNumberModel.addChangeListener((e) -> {
            SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            int numTiers = model.getNumber().intValue();

            setNumDisplayTiers(numTiers);
            setNumTiers(numTiers);
            model.setValue(numTiers);

            for (int i = getNumTiers(); i <= getKnowledge()
                    .getMaxTierForbiddenWithin(); i++) {
                getKnowledge().setTierForbiddenWithin(i, false);
            }

            notifyKnowledge();
        });

        JSpinner spinner = new JSpinner(spinnerNumberModel);
        spinner.setMaximumSize(spinner.getPreferredSize());
        b1.add(spinner);
        b.add(b1);

        this.tiersPanel = new JPanel();
        this.tiersPanel.setLayout(new BorderLayout());
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);

        b.add(this.tiersPanel);

        Box c = Box.createHorizontalBox();
        c.add(new JLabel("Use shift key to select multiple items."));
        c.add(Box.createGlue());
        b.add(c);

        return b;
    }

    private void setNumDisplayTiers(int numTiers) {
        if (numTiers < 2) {
            int knowledgeTiers = getKnowledge().getNumTiers();
            int defaultTiers = (int) (FastMath.pow(getVarNames().size(), 0.5) + 1);
            numTiers = FastMath.max(knowledgeTiers, defaultTiers);
        }

        setNumTiers(numTiers);

        for (int i = numTiers; i < getKnowledge().getNumTiers(); i++) {
            List<String> vars = getKnowledge().getTier(i);

            for (String var : vars) {
                getKnowledge().removeFromTiers(var);
            }
        }

        this.tiersPanel.removeAll();
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        this.tiersPanel.revalidate();
        this.tiersPanel.repaint();
    }

    /**
     * If the knowledge box sees interventional variables it automatically places those variables in the first tier and
     * the rest of domain variables in second tier - Zhou
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

    private Box getTierBoxes(int numTiers) {
        // Handling interventional variables
        checkInterventionalVariables();

        // Only for dataset with interventional variables and the first time
        // we open the knowledge box - Zhou
        if (getKnowledge().isEmpty() && !this.firstTierVars.isEmpty()) {
            // Display interventional variables in first tier and the rest in second tier
            getKnowledge().setTier(0, this.firstTierVars);
            getKnowledge().setTier(1, this.secondTierVars);
        }

        for (Node var : this.vars) {
            getKnowledge().addVariable(var.getName());
        }

        // Overall container
        Box container = Box.createVerticalBox();

        // Vars not in tier. Reinitialize in case the variables in knowledge have changed.
        initComponents();
        List<String> varsNotInTiers = getKnowledge().getVariablesNotInTiers();
        JList<String> varsNotInTiersList = new DragDropList(varsNotInTiers, -1);
        varsNotInTiersList.setBorder(null);

        Box varsNotInTiersBox = Box.createHorizontalBox();
        JScrollPane jScrollPane1 = new JScrollPane(varsNotInTiersList);
        jScrollPane1.setPreferredSize(new Dimension(640, 50));
        varsNotInTiersBox.add(jScrollPane1);

        Box tiersBox = Box.createVerticalBox();

        // Use this list so we can set the first tier forbidden within tier with interventional variables handling - Zhou
        List<JCheckBox> forbiddenCheckboxes = new LinkedList<>();

        for (int tier = 0; tier < numTiers; tier++) {
            Box textRow = Box.createHorizontalBox();
            textRow.add(new JLabel("Tier " + (tier)));
            int _tier = tier;

            textRow.add(Box.createHorizontalGlue());

            JButton regexAdd = new JButton("Find");

            JCheckBox forbiddenCheckbox = new JCheckBox("Forbid Within Tier", getKnowledge().isTierForbiddenWithin(_tier));

            JCheckBox causesOnlyNextTierCheckbox = new JCheckBox("Can Cause Only Next Tier", getKnowledge().isOnlyCanCauseNextTier(_tier));

            JComponent upReference = this;

            forbiddenCheckbox.addActionListener((e) -> {
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setTierForbiddenWithin(_tier, checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            forbiddenCheckboxes.add(forbiddenCheckbox);

            textRow.add(regexAdd);

            regexAdd.addActionListener((e) -> {
                String regex = JOptionPane.showInputDialog("Search Cpdag");
                try {
                    getKnowledge().removeFromTiers(regex);
                    getKnowledge().addToTier(_tier, regex);
                } catch (IllegalArgumentException iae) {
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
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setOnlyCanCauseNextTier(_tier, checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            if (tier + 2 < numTiers) textRow.add(causesOnlyNextTierCheckbox);

            tiersBox.add(textRow);

            List<String> tierNames = getKnowledge().getTier(tier);

            JList<String> tierList = new DragDropList(tierNames, tier);

            Box tierBox = Box.createHorizontalBox();
            JScrollPane jScrollPane = new JScrollPane(tierList);
            jScrollPane.setPreferredSize(new Dimension(600, 50));
            tierBox.add(jScrollPane);

            tiersBox.add(tierBox);
        }

        // Add all tiers to a scroll pane
        JScrollPane tiersScrollPane = new JScrollPane(tiersBox);
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
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        graph.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                notifyKnowledge();
            }
        });

        this.edgeWorkbench = new KnowledgeWorkbench(graph);
        resetEdgeDisplay(null);

        JCheckBox showForbiddenByTiersCheckbox = new JCheckBox("Show Forbidden By Tiers", this.showForbiddenByTiers);
        JCheckBox showForbiddenGroupsCheckBox = new JCheckBox("Show Forbidden by Groups", this.showForbiddenByGroups);
        JCheckBox showForbiddenExplicitlyCheckbox = new JCheckBox("Show Forbidden Explicitly", this.showForbiddenExplicitly);


        JCheckBox showRequiredGroupsCheckBox = new JCheckBox("Show Required by Groups", this.showRequiredByGroups);
        JCheckBox showRequiredExplicitlyCheckbox = new JCheckBox("Show Required Explicitly", this.showRequired);


        showRequiredGroupsCheckBox.addActionListener((e) -> {
            JCheckBox box = (JCheckBox) e.getSource();
            this.showRequiredByGroups = box.isSelected();
            resetEdgeDisplay(showRequiredGroupsCheckBox);
        });

        showForbiddenGroupsCheckBox.addActionListener((e) -> {
            JCheckBox box = (JCheckBox) e.getSource();
            this.showForbiddenByGroups = box.isSelected();
            resetEdgeDisplay(showForbiddenGroupsCheckBox);
        });

        showForbiddenByTiersCheckbox.addActionListener((e) -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowForbiddenByTiers(checkBox.isSelected());
            resetEdgeDisplay(showForbiddenByTiersCheckbox);
        });

        showForbiddenExplicitlyCheckbox.addActionListener((e) -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowForbiddenExplicitly(checkBox.isSelected());
            resetEdgeDisplay(showForbiddenExplicitlyCheckbox);
        });

        showRequiredExplicitlyCheckbox.addActionListener((e) -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowRequired(checkBox.isSelected());
            resetEdgeDisplay(showRequiredExplicitlyCheckbox);
        });

        JPanel workbenchPanel = new JPanel();
        workbenchPanel.setLayout(new BorderLayout());
        workbenchPanel.add(new JScrollPane(this.edgeWorkbench), BorderLayout.CENTER);
        workbenchPanel.setBorder(new TitledBorder("Forbidden and Required Edges"));

        JPanel display = new JPanel();
        display.setPreferredSize(new Dimension(640, 450));
        display.setLayout(new BorderLayout());

        JPanel b2 = new KnowledgeEditorToolbar(this.edgeWorkbench, this.edgeWorkbench.getSourceGraph());
        display.add(b2, BorderLayout.WEST);
        display.add(workbenchPanel, BorderLayout.CENTER);

        Box showOptionsBox = Box.createVerticalBox();

        Box forbiddenOptionsBox = Box.createHorizontalBox();
        forbiddenOptionsBox.add(showForbiddenByTiersCheckbox);
        forbiddenOptionsBox.add(showForbiddenGroupsCheckBox);
        forbiddenOptionsBox.add(showForbiddenExplicitlyCheckbox);
        forbiddenOptionsBox.add(Box.createHorizontalGlue());

        Box requiredOptionsBox = Box.createHorizontalBox();
        requiredOptionsBox.add(showRequiredGroupsCheckBox);
        requiredOptionsBox.add(showRequiredExplicitlyCheckbox);
        requiredOptionsBox.add(Box.createHorizontalGlue());

        showOptionsBox.add(forbiddenOptionsBox);
        showOptionsBox.add(requiredOptionsBox);

        display.add(showOptionsBox, BorderLayout.SOUTH);

        return display;
    }

    private void resetEdgeDisplay(JCheckBox checkBox) {
        Knowledge knowledge = getKnowledge();
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());
        getVarNames().forEach(e -> {
            knowledge.addVariable(e);
            graph.addNode(new KnowledgeModelNode(e));
        });

        if (this.showRequiredByGroups) {
            List<KnowledgeEdge> list = knowledge.getListOfRequiredEdges();
            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showRequiredByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", KnowledgeBoxEditor.EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
                    if (knowledge.isRequiredByGroups(from, to)) {
                        KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                                .getNode(from);
                        KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                                .getNode(to);

                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.REQUIRED_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showForbiddenByGroups) {
            List<KnowledgeEdge> list = knowledge.getListOfForbiddenEdges();
            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", KnowledgeBoxEditor.EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
                    if (knowledge.isForbiddenByGroups(from, to)) {
                        KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                                .getNode(from);
                        KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                                .getNode(to);

                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.FORBIDDEN_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showRequired) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyRequiredEdges();
            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showRequired = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", KnowledgeBoxEditor.EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    if (!(fromNode == null || toNode == null)) {
                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                                KnowledgeModelEdge.REQUIRED));
                    }
                });
            }
        }

        if (this.showForbiddenByTiers) {
            List<KnowledgeEdge> list = knowledge.getListOfForbiddenEdges();
            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenByTiers = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", KnowledgeBoxEditor.EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
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

                        KnowledgeModelEdge knowledgeModelEdge = new KnowledgeModelEdge(
                                fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_BY_TIERS);

                        graph.addEdge(knowledgeModelEdge);
                    }
                });
            }
        }

        if (this.showForbiddenExplicitly) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyForbiddenEdges();
            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenExplicitly = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", KnowledgeBoxEditor.EDGE_LIMIT);
                JOptionPane.showMessageDialog(this, errMsg, "Unable To Display Edges", JOptionPane.ERROR_MESSAGE);
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    KnowledgeModelEdge edge = new KnowledgeModelEdge(fromNode,
                            toNode, KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
                    if (!graph.containsEdge(edge)) {
                        graph.addEdge(edge);
                    }
                });
            }
        }

        boolean arrangedAll = LayoutUtil.arrangeBySourceGraph(graph,
                this.edgeWorkbench.getGraph());

        if (!arrangedAll) {
            LayoutUtil.defaultLayout(graph);
        }

        this.edgeWorkbench.setGraph(graph);
        notifyKnowledge();
    }

    private void notifyKnowledge() {
        firePropertyChange("modelChanged", null, null);
    }

    private Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
        this.knowledgeBoxModel.setKnowledge(knowledge);
    }

    private List<String> getVarNames() {
        return this.knowledge.getVariables();
    }

    private void setShowForbiddenExplicitly(boolean showForbiddenExplicitly) {
        this.showForbiddenExplicitly = showForbiddenExplicitly;
    }

    private void setShowRequired(boolean showRequired) {
        this.showRequired = showRequired;
    }

    private void setShowForbiddenByTiers(boolean showForbiddenByTiers) {
        this.showForbiddenByTiers = showForbiddenByTiers;
    }

    private int getNumTiers() {
        return this.numTiers;
    }

    private void setNumTiers(int numTiers) {
        this.numTiers = numTiers;
    }

    private class DragDropList extends JList<String> {

        private static final long serialVersionUID = 7240458207688841986L;

        private final List<String> items;
        private final int tier;

        public DragDropList(List<String> items, int tier) {
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
                public boolean canImport(TransferSupport info) {
                    return info.isDataFlavorSupported(ListTransferable.DATA_FLAVOR);
                }

                @Override
                protected Transferable createTransferable(JComponent c) {
                    JList source = (JList) c;

                    List list = source.getSelectedValuesList();
                    if (list == null) {
                        getToolkit().beep();
                        list = Collections.EMPTY_LIST;
                    }

                    return new ListTransferable(list);
                }

                @Override
                public int getSourceActions(JComponent c) {
                    return TransferHandler.COPY_OR_MOVE;
                }

                @Override
                public boolean importData(TransferSupport info) {
                    if (!info.isDrop()) {
                        return false;
                    }

                    JList<String> source = (JList<String>) info.getComponent();
                    DefaultListModel listModel = (DefaultListModel) source.getModel();
                    Knowledge knowledge = getKnowledge();

                    Transferable transferable = info.getTransferable();
                    try {
                        List<String> list = (List<String>) transferable.getTransferData(ListTransferable.DATA_FLAVOR);
                        list.forEach(name -> {
                            if (DragDropList.this.tier >= 0) {
                                try {
                                    knowledge.removeFromTiers(name);
                                    knowledge.addToTier(DragDropList.this.tier, name);

                                    notifyKnowledge();

                                    listModel.addElement(name);
                                    sort(listModel);
                                } catch (IllegalStateException e) {
                                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
                                }
                            } else {
                                knowledge.removeFromTiers(name);

                                notifyKnowledge();
                                listModel.addElement(name);
                                sort(listModel);
                            }
                        });
                    } catch (IOException | UnsupportedFlavorException exception) {
                        exception.printStackTrace(System.err);
                        return false;
                    }

                    return true;
                }

                @Override
                protected void exportDone(JComponent c, Transferable data, int action) {
                    if (action == TransferHandler.MOVE) {
                        JList<String> source = (JList<String>) c;
                        DefaultListModel<String> listModel = (DefaultListModel<String>) source.getModel();
                        try {
                            List<String> list = (List<String>) data.getTransferData(ListTransferable.DATA_FLAVOR);
                            list.forEach(listModel::removeElement);
                        } catch (IOException | UnsupportedFlavorException ignored) {
                        }
                    }
                }

            });

            DefaultListModel<String> listModel = new DefaultListModel<>();
            this.items.forEach(listModel::addElement);
            setModel(listModel);
        }

        private void sort(DefaultListModel<String> listModel) {
            Object[] elements = listModel.toArray();
            String[] values = new String[elements.length];
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

                int i1 = tokens1[1].compareTo(tokens2[1]);
                int i0 = tokens1[0].compareTo(tokens2[0]);

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
