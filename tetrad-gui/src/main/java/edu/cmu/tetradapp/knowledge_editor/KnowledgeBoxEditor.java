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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.ForbiddenGraphModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;

import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

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

    private List<String> varNames;
    private KnowledgeWorkbench edgeWorkbench;
    private JPanel tiersPanel;
    private boolean showForbiddenExplicitly = false;
    private boolean showForbiddenByTiers = false;
    private boolean showRequired = false;
    private boolean showRequiredByGroups = false;
    private boolean showForbiddenByGroups = false;
    private JTextArea textArea;


    private final KnowledgeBoxModel knowledgeBoxModel;
    private JTabbedPane tabbedPane = null;
    private Graph sourceGraph;

    public KnowledgeBoxEditor(final KnowledgeBoxModel knowledgeBoxModel) {
        this(knowledgeBoxModel, knowledgeBoxModel.getVarNames());
    }

    public KnowledgeBoxEditor(final ForbiddenGraphModel knowledgeBoxModel) {
        this(knowledgeBoxModel, knowledgeBoxModel.getVarNames());
    }

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names
     * (that is, the list of all variable names to be considered, which may vary
     * from object to object even for the same knowledge), and possible source
     * graph. The source graph is used only to arrange nodes in the edge panel.
     */
    private KnowledgeBoxEditor(final KnowledgeBoxModel knowledgeBoxModel, List<String> varNames) {
        if (knowledgeBoxModel == null) {
            throw new NullPointerException();
        }

        if (varNames == null) {
            varNames = new ArrayList<>();
        }

        this.varNames = varNames;
        this.knowledgeBoxModel = knowledgeBoxModel;

        setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        resetTabbedPane(tabbedPane);

        add(tabbedPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(550, 500));

        add(menuBar(), BorderLayout.NORTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                TetradLogger.getInstance().log("knowledge", "Edited Knowledge:");
                TetradLogger.getInstance().log("knowledge", knowledgeBoxModel.getKnowledge().toString());
            }
        });

        initComponents();
    }

    private void initComponents() {
        getKnowledge().getVariables().forEach(e -> labelMap.put(e, createJLabel(e)));
        getKnowledge().getVariablesNotInTiers().forEach(e -> labelMap.put(e, createJLabel(e)));
    }

    private JLabel createJLabel(String name) {
        JLabel label = new JLabel(String.format("  %s  ", name));
        label.setOpaque(true);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new CompoundBorder(new MatteBorder(2, 2, 2, 2, Color.WHITE), new LineBorder(Color.BLACK)));
        label.setForeground(Color.BLACK);
        label.setBackground(UNSELECTED_BG);

        return label;
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);

        JMenuItem load = new JMenuItem("Load");
        JMenuItem save = new JMenuItem("Save");

        file.add(load);
        file.add(save);

        load.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                IKnowledge knowledge = new DataReader().parseKnowledge(selectedFile);

                if (knowledge == null) {
                    throw new NullPointerException("No knowledge found in this file. Perhaps the formatting is off");
                }

                setKnowledge(knowledge);
                resetTabbedPane(KnowledgeBoxEditor.this.tabbedPane);
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
                e1.printStackTrace();
            }
        });

        save.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showSaveDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                DataWriter.saveKnowledge(knowledgeBoxModel.getKnowledge(), new FileWriter(selectedFile));
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });


        return menuBar;
    }

    private void resetTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.removeAll();
        tabbedPane.add("Tiers", tierDisplay());
        tabbedPane.add("Other Groups", new OtherGroupsEditor(knowledgeBoxModel.getKnowledge(), this.varNames));
        tabbedPane.add("Edges", edgeDisplay());

        tabbedPane.addChangeListener((e) -> {
            JTabbedPane pane = (JTabbedPane) e.getSource();
            if (pane.getSelectedIndex() == 0) {
                setNumDisplayTiers(getNumTiers());
            } else if (pane.getSelectedIndex() == 2) {
                resetEdgeDisplay(null);
            }
        });

        this.tabbedPane = tabbedPane;
    }

    private Box tierDisplay() {
        if (getNumTiers() < 0) {
            int numTiers = getKnowledge().getNumTiers();
            int _default = (int) (Math.pow(varNames.size(), 0.5) + 1);
            numTiers = numTiers < _default ? _default : numTiers;
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

        tiersPanel = new JPanel();
        tiersPanel.setLayout(new BorderLayout());
        tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);

        b.add(tiersPanel);

        Box c = Box.createHorizontalBox();
        c.add(new JLabel("Use shift key to select multiple items."));
        c.add(Box.createGlue());
        b.add(c);

        return b;
    }

    private void setNumDisplayTiers(int numTiers) {
        if (numTiers < 2) {
            int knowledgeTiers = getKnowledge().getNumTiers();
            int defaultTiers = (int) (Math.pow(getVarNames().size(), 0.5) + 1);
            numTiers = knowledgeTiers < defaultTiers ? defaultTiers
                    : knowledgeTiers;
            setNumTiers(numTiers);
        } else {
            setNumTiers(numTiers);
        }

        for (int i = numTiers; i < getKnowledge().getNumTiers(); i++) {
            List<String> vars = getKnowledge().getTier(i);

            for (String var : vars) {
                getKnowledge().removeFromTiers(var);
            }
        }

        tiersPanel.removeAll();
        tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        tiersPanel.revalidate();
        tiersPanel.repaint();
    }

    private Box getTierBoxes(int numTiers) {
        Box c = Box.createVerticalBox();

        for (String var : varNames) {
            getKnowledge().addVariable(var);
        }

        List varsNotInTiers = getKnowledge().getVariablesNotInTiers();
        JList l1 = new DragDropList(varsNotInTiers, -1);
        l1.setBorder(null);

        Box b2 = Box.createHorizontalBox();
        JScrollPane jScrollPane1 = new JScrollPane(l1);
        jScrollPane1.setPreferredSize(new Dimension(500, 50));
        b2.add(jScrollPane1);
        c.add(b2);

        c.add(Box.createVerticalStrut(5));

        Box d = Box.createVerticalBox();

        for (int tier = 0; tier < numTiers; tier++) {
            Box textRow = Box.createHorizontalBox();
            textRow.add(new JLabel("Tier " + (tier + 1)));
            final int _tier = tier;

            textRow.add(Box.createHorizontalGlue());

            JButton regexAdd = new JButton("Find");

            JCheckBox forbiddenCheckbox = new JCheckBox("Forbid Within Tier",
                    getKnowledge().isTierForbiddenWithin(_tier));

            JCheckBox causesOnlyNextTierCheckbox =  new JCheckBox("Can Cause Only Next Tier",
                                getKnowledge().isOnlyCanCauseNextTier(_tier));

            final JComponent upReference = this;

            forbiddenCheckbox.addActionListener((e) -> {
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setTierForbiddenWithin(_tier,
                            checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            textRow.add(regexAdd);

            regexAdd.addActionListener((e) -> {
                String regex = JOptionPane.showInputDialog("Search Pattern");
                try {

                    getKnowledge().removeFromTiers(regex);
                    getKnowledge().addToTier(_tier, regex);
                } catch (IllegalArgumentException iae) {
                    JOptionPane.showMessageDialog(upReference, iae.getMessage());
                }

                notifyKnowledge();

                tiersPanel.removeAll();
                tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
                tiersPanel.revalidate();
                tiersPanel.repaint();


            });

            textRow.add(forbiddenCheckbox);

            causesOnlyNextTierCheckbox.addActionListener((e) -> {
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setOnlyCanCauseNextTier(_tier,
                            checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            if (tier + 2 < numTiers) textRow.add(causesOnlyNextTierCheckbox);

            d.add(textRow);

            List tierNames = getKnowledge().getTier(tier);

            JList tierList = new DragDropList(tierNames, tier);

            Box tierRow = Box.createHorizontalBox();
            JScrollPane jScrollPane = new JScrollPane(tierList);
            jScrollPane.setPreferredSize(new Dimension(500, 50));
            tierRow.add(jScrollPane);
            d.add(tierRow);
        }

        JScrollPane scroll = new JScrollPane(d);
        scroll.setPreferredSize(new Dimension(550, 400));
        c.add(scroll);
        return c;
    }

    private JPanel edgeDisplay() {
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        graph.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                notifyKnowledge();
            }
        });

        edgeWorkbench = new KnowledgeWorkbench(graph);
        resetEdgeDisplay(null);

        JCheckBox showForbiddenByTiersCheckbox = new JCheckBox(
                "Show Forbidden By Tiers", showForbiddenByTiers);
        JCheckBox showForbiddenExplicitlyCheckbox = new JCheckBox(
                "Show Forbidden Explicitly", showForbiddenExplicitly);
        JCheckBox showRequiredCheckbox = new JCheckBox(
                "Show Required Explicitly", showRequired);
        JCheckBox showRequiredGroupsCheckBox = new JCheckBox(
                "Show Required by Groups", this.showRequiredByGroups);
        JCheckBox showForbiddenGroupsCheckBox = new JCheckBox(
                "Show Forbidden by Groups", this.showForbiddenByGroups);

        showRequiredGroupsCheckBox.addActionListener((e) -> {
            JCheckBox box = (JCheckBox) e.getSource();
            showRequiredByGroups = box.isSelected();
            resetEdgeDisplay(showRequiredGroupsCheckBox);
        });

        showForbiddenGroupsCheckBox.addActionListener((e) -> {
            JCheckBox box = (JCheckBox) e.getSource();
            showForbiddenByGroups = box.isSelected();
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

        showRequiredCheckbox.addActionListener((e) -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            setShowRequired(checkBox.isSelected());
            resetEdgeDisplay(showRequiredCheckbox);
        });

        JPanel workbenchPanel = new JPanel();
        workbenchPanel.setLayout(new BorderLayout());
        workbenchPanel.add(new JScrollPane(edgeWorkbench), BorderLayout.CENTER);
        workbenchPanel.setBorder(new TitledBorder(
                "Forbidden and Required Edges"));

        JPanel display = new JPanel();
        display.setPreferredSize(new Dimension(550, 450));
        display.setLayout(new BorderLayout());

        JPanel b2 = new KnowledgeEditorToolbar(edgeWorkbench, getSourceGraph());
        display.add(b2, BorderLayout.WEST);
        display.add(workbenchPanel, BorderLayout.CENTER);

        Box vBox = Box.createVerticalBox();

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalGlue());
        b4.add(showForbiddenByTiersCheckbox);
        b4.add(showForbiddenExplicitlyCheckbox);
        b4.add(showRequiredCheckbox);

        Box hBox = Box.createHorizontalBox();
        hBox.add(Box.createHorizontalGlue());
        hBox.add(showRequiredGroupsCheckBox);
        hBox.add(showForbiddenGroupsCheckBox);

        vBox.add(b4);
        vBox.add(hBox);

        display.add(vBox, BorderLayout.SOUTH);

        return display;
    }

    private void resetEdgeDisplay(JCheckBox checkBox) {
        IKnowledge knowledge = getKnowledge();
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());
        getVarNames().forEach(e -> {
            knowledge.addVariable(e);
            graph.addNode(new KnowledgeModelNode(e));
        });

        if (this.showRequiredByGroups) {
            List<KnowledgeEdge> list = knowledge.getListOfRequiredEdges();
            if (list.size() > EDGE_LIMIT) {
                showRequiredByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
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
            if (list.size() > EDGE_LIMIT) {
                showForbiddenByGroups = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
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

        if (showRequired) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyRequiredEdges();
            if (list.size() > EDGE_LIMIT) {
                showRequired = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
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

        if (showForbiddenByTiers) {
            List<KnowledgeEdge> list = knowledge.getListOfForbiddenEdges();
            if (list.size() > EDGE_LIMIT) {
                showForbiddenByTiers = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
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

        if (showForbiddenExplicitly) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyForbiddenEdges();
            if (list.size() > EDGE_LIMIT) {
                showForbiddenExplicitly = false;
                if (checkBox != null) {
                    checkBox.setSelected(false);
                }
                String errMsg = String.format("The number of edges to show exceeds the limit %d.", EDGE_LIMIT);
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

        boolean arrangedAll = GraphUtils.arrangeBySourceGraph(graph,
                edgeWorkbench.getGraph());

        if (!arrangedAll) {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        edgeWorkbench.setGraph(graph);
        if (knowledgeBoxModel != null) {
            notifyKnowledge();
        }

    }

    /**
     * This is an old method that needs to be removed when cleaning up code.
     */
    private void resetEdgeDisplayOld() {
        List<String> varNames = getVarNames();
        IKnowledge knowledge = getKnowledge();

        for (String name : varNames) {
            knowledge.addVariable(name);
        }

        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        for (String varName : varNames) {
            graph.addNode(new KnowledgeModelNode(varName));
        }

        if (this.showRequiredByGroups) {
            for (Iterator<KnowledgeEdge> i = knowledge.requiredEdgesIterator(); i
                    .hasNext();) {
                KnowledgeEdge edge = i.next();
                String from = edge.getFrom();
                String to = edge.getTo();
                if (knowledge.isRequiredByGroups(from, to)) {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                            KnowledgeModelEdge.REQUIRED_BY_GROUPS));
                }
            }
        }

        if (this.showForbiddenByGroups) {
            for (Iterator<KnowledgeEdge> i = knowledge.forbiddenEdgesIterator(); i
                    .hasNext();) {
                KnowledgeEdge edge = i.next();
                String from = edge.getFrom();
                String to = edge.getTo();
                if (knowledge.isForbiddenByGroups(from, to)) {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                            .getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                            .getNode(to);

                    graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                            KnowledgeModelEdge.FORBIDDEN_BY_GROUPS));
                }
            }
        }

        if (showRequired) {
            for (Iterator<KnowledgeEdge> i = knowledge
                    .explicitlyRequiredEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();
                String from = pair.getFrom();
                String to = pair.getTo();

                KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                        .getNode(from);
                KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                        .getNode(to);

                if (fromNode == null || toNode == null) {
                    continue;
                }

                graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                        KnowledgeModelEdge.REQUIRED));
            }
        }

        if (showForbiddenByTiers) {
            for (Iterator<KnowledgeEdge> i = knowledge.forbiddenEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();
                String from = pair.getFrom();
                String to = pair.getTo();

                if (!knowledge.isForbiddenByTiers(from, to)) {
                    continue;
                }

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
                // Hacked fix. Jdramsey.
                // if (!graph.containsEdge(knowledgeModelEdge)) {
                // graph.addEdge(knowledgeModelEdge);
                // }
            }
        }

        if (showForbiddenExplicitly) {
            for (Iterator<KnowledgeEdge> i = knowledge
                    .explicitlyForbiddenEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();

                String from = pair.getFrom();
                String to = pair.getTo();
                KnowledgeModelNode fromNode = (KnowledgeModelNode) graph
                        .getNode(from);
                KnowledgeModelNode toNode = (KnowledgeModelNode) graph
                        .getNode(to);

                KnowledgeModelEdge edge = new KnowledgeModelEdge(fromNode,
                        toNode, KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
                if (!graph.containsEdge(edge)) {
                    graph.addEdge(edge);
                }
            }
        }

        boolean arrangedAll = GraphUtils.arrangeBySourceGraph(graph,
                edgeWorkbench.getGraph());

        if (!arrangedAll) {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        edgeWorkbench.setGraph(graph);
        if (knowledgeBoxModel != null) {
            notifyKnowledge();
        }
    }

    private Component textDisplay() {
        final JButton testButton = new JButton("Test");
        final JButton loadFromTextPaneButton = new JButton("Load from Text Pane");
        final JButton loadFromFileButton = new JButton("Load from File");

        testButton.addActionListener((e) -> {
            try {
                String text = getTextArea().getText();
                DataReader reader = new DataReader();
                reader.parseKnowledge(text.toCharArray());
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Looks good.");
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        loadFromTextPaneButton.addActionListener((e) -> {
            try {
                String text = getTextArea().getText();
                DataReader reader = new DataReader();
                IKnowledge knowledge = reader.parseKnowledge(text.toCharArray());
                setKnowledge(knowledge);
                resetTabbedPane(KnowledgeBoxEditor.this.tabbedPane);
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        loadFromFileButton.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation
                    = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
                return;
            }

            final File file = chooser.getSelectedFile();

            if (file == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", file.getParent());

            try {
                IKnowledge knowledge = new DataReader().parseKnowledge(file);
                setKnowledge(knowledge);
                resetTabbedPane(KnowledgeBoxEditor.this.tabbedPane);
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        Box b = Box.createVerticalBox();

        textArea = new JTextArea();
        resetTextDisplay();

        b.add(getTextArea());

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(testButton);
        b2.add(loadFromTextPaneButton);
        b2.add(loadFromFileButton);
        b.add(b2);

        return b;
    }

    private void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        knowledgeBoxModel.setKnowledge(knowledge);
    }

    private void notifyKnowledge() {
        firePropertyChange("modelChanged", null, null);
    }

    private void resetTextDisplay() {
        getTextArea().setFont(new Font("Monospaced", Font.PLAIN, 12));
        getTextArea().setBorder(
                new CompoundBorder(new LineBorder(Color.black),
                        new EmptyBorder(3, 3, 3, 3)));

        try {
            IKnowledge knowledge = getKnowledge();
            CharArrayWriter out = new CharArrayWriter();
            DataWriter.saveKnowledge(knowledge, out);

            getTextArea().setText(out.toString());
        } catch (IOException e) {
            getTextArea().setText("Could not render knowledge.");
        }

    }

    private void loadKnowledge(String fileName) {
        if (fileName == null) {
            throw new IllegalStateException("No data file was specified.");
        }

        try {
            File knowledgeFile = new File(fileName);

            CharArrayWriter writer = new CharArrayWriter();

            FileReader fr = new FileReader(knowledgeFile);
            int i;

            while ((i = fr.read()) != -1) {
                writer.append((char) i);
            }

            DataReader reader = new DataReader();
            char[] chars = writer.toCharArray();

            System.out.println(new String(chars));

            knowledgeBoxModel.setKnowledge(reader.parseKnowledge(chars));
        } catch (Exception e) {
            throw new RuntimeException("Couldn't read knowledge.");
        }
    }

    private IKnowledge getKnowledge() {
        return knowledgeBoxModel.getKnowledge();
    }

    private List<String> getVarNames() {
        return varNames;
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

    private JTextArea getTextArea() {
        return textArea;
    }

    private int getNumTiers() {
        return knowledgeBoxModel.getNumTiers();
    }

    private void setNumTiers(int numTiers) {
        knowledgeBoxModel.setNumTiers(numTiers);
    }

    private Graph getSourceGraph() {
        return sourceGraph;
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
                JLabel label = labelMap.get(value);
                if (label == null) {
                    label = new JLabel();
                }

                label.setBackground(isSelected ? SELECTED_BG : UNSELECTED_BG);

                return label;
            });
            setTransferHandler(new TransferHandler() {

                private static final long serialVersionUID = 3109256773218160485L;

                @Override
                public boolean canImport(TransferHandler.TransferSupport info) {
                    return info.isDataFlavorSupported(ListTransferable.DATA_FLAVOR);
                }

                @Override
                protected Transferable createTransferable(JComponent c) {
                    JList<String> source = (JList<String>) c;

                    List<String> list = source.getSelectedValuesList();
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
                public boolean importData(TransferHandler.TransferSupport info) {
                    if (!info.isDrop()) {
                        return false;
                    }

                    JList<String> source = (JList<String>) info.getComponent();
                    DefaultListModel listModel = (DefaultListModel) source.getModel();
                    IKnowledge knowledge = getKnowledge();

                    Transferable transferable = info.getTransferable();
                    try {
                        List<String> list = (List<String>) transferable.getTransferData(ListTransferable.DATA_FLAVOR);
                        list.forEach(name -> {
                            if (tier >= 0) {
                                try {
                                    knowledge.removeFromTiers(name);
                                    knowledge.addToTier(tier, name);

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
                        } catch (IOException | UnsupportedFlavorException exception) {
                        }
                    }
                }

            });

            DefaultListModel<String> listModel = new DefaultListModel<>();
            items.forEach(listModel::addElement);
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
