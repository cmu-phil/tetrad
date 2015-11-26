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

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Edits knowledge by letting the user put variable names into tiers. The number
 * of tiers may be set. By default, it is assumed that the structure of the
 * tiers is Tier0 --&gt; Tier1 --&gt; Tier2 --&gt; ..., but special graphical structures
 * among the tiers may be set as well.
 *
 * @author Joseph Ramsey
 */
public class KnowledgeEditor extends JPanel {
	private static final long serialVersionUID = 3901070375247726595L;
	private List<String> varNames;
    private IKnowledge knowledge;
    private KnowledgeWorkbench edgeWorkbench;
    private JPanel tiersPanel;
    private boolean showForbiddenExplicitly = true;
    private boolean showForbiddenByTiers = true;
    private boolean showRequired = true;
    private boolean showRequiredByGroups = true;
    private boolean showForbiddenByGroups = true;
    private JTextArea textArea;
    private Graph sourceGraph;
    private int numTiers = -1;
    private JTabbedPane tabbedPane = null;

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names
     * (that is, the list of all variable names to be considered, which may vary
     * from object to object even for the same knowledge), and possible source
     * graph. The source graph is used only to arrange nodes in the edge panel.
     */
    public KnowledgeEditor(IKnowledge knowledge, List<String> varNames,
                           Graph sourceGraph) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (varNames == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
        this.varNames = varNames;
        this.sourceGraph = sourceGraph;

        setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        resetTabbedPane(tabbedPane);

        add(tabbedPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(550, 500));
    }

    private void resetTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.removeAll();
        tabbedPane.add("Tiers", tierDisplay());
        tabbedPane.add("Other Groups", new OtherGroupsEditor(this.knowledge, this.varNames));
        tabbedPane.add("Edges", edgeDisplay());
        tabbedPane.add("Text", textDisplay());

        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JTabbedPane pane = (JTabbedPane) e.getSource();
                if (pane.getSelectedIndex() == 0) {
                    setNumDisplayTiers(getNumTiers());
                } else if (pane.getSelectedIndex() == 2) {
                    resetEdgeDisplay();
                } else if (pane.getSelectedIndex() == 3) {
                    resetTextDisplay();
                }
            }
        });

        this.tabbedPane = tabbedPane;
    }

//    public KnowledgeEditor(KnowledgeWrapper wrapper) {
//        this(wrapper.getKnowledge());
//    }

    private Box tierDisplay() {
        if (numTiers < 0) {
            int numTiers = knowledge.getNumTiers();
            int _default = (int) (Math.pow(varNames.size(), 0.5) + 1);
            numTiers = numTiers < _default ? _default : numTiers;
            this.numTiers = numTiers;
        }

        Box b = Box.createVerticalBox();
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Not in tier:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("# Tiers = "));
        SpinnerNumberModel spinnerNumberModel =
                new SpinnerNumberModel(getNumTiers(), 0, 100, 1);
        spinnerNumberModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
                int numTiers = model.getNumber().intValue();
                int knowledgeNumTiers = getKnowledge().getNumTiers();

                if (numTiers >= knowledgeNumTiers) {
                    setNumDisplayTiers(numTiers);
                    setNumTiers(numTiers);
                } else {
                    model.setValue(knowledgeNumTiers);
                    setNumTiers(knowledgeNumTiers);
                }

                for (int i = getNumTiers();
                     i <= knowledge.getMaxTierForbiddenWithin(); i++) {
                    getKnowledge().setTierForbiddenWithin(i, false);
                }
            }
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
        if (numTiers < 0) {
            int knowledgeTiers = getKnowledge().getNumTiers();
            int defaultTiers = (int) (Math.pow(getVarNames().size(), 0.5) + 1);
            numTiers = knowledgeTiers <
                    defaultTiers ? defaultTiers : knowledgeTiers;
            setNumTiers(numTiers);
        } else {
            setNumTiers(numTiers);
        }

        tiersPanel.removeAll();
        tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        tiersPanel.revalidate();
        tiersPanel.repaint();
    }

    private Box getTierBoxes(int numTiers) {
        Box c = Box.createVerticalBox();

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

            JCheckBox exogenousCheckbox = new JCheckBox("Forbid Within Tier",
                    knowledge.isTierForbiddenWithin(_tier));

            exogenousCheckbox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JCheckBox checkbox = (JCheckBox) e.getSource();

                    getKnowledge().setTierForbiddenWithin(_tier,
                            checkbox.isSelected());
                }
            });

            textRow.add(Box.createHorizontalGlue());
            textRow.add(exogenousCheckbox);

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
        KnowledgeGraph graph = new KnowledgeGraph(knowledge);
        edgeWorkbench = new KnowledgeWorkbench(graph);
        resetEdgeDisplay();

        JCheckBox showForbiddenByTiersCheckbox = new JCheckBox(
                "Show Forbidden By Tiers", showForbiddenExplicitly);
        JCheckBox showForbiddenExplicitlyCheckbox = new JCheckBox(
                "Show Forbidden Explicitly", showForbiddenExplicitly);
        JCheckBox showRequiredCheckbox =
                new JCheckBox("Show Required Explicitly", showRequired);
        JCheckBox showRequiredGroupsCheckBox = new JCheckBox("Show Required by Groups", this.showRequiredByGroups);
        JCheckBox showForbiddenGroupsCheckBox = new JCheckBox("Show Forbidden by Groups", this.showForbiddenByGroups);


        showRequiredGroupsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                showRequiredByGroups = box.isSelected();
                resetEdgeDisplay();
            }
        });


        showForbiddenGroupsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                showForbiddenByGroups = box.isSelected();
                resetEdgeDisplay();
            }
        });


        showForbiddenByTiersCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                setShowForbiddenByTiers(checkBox.isSelected());
                resetEdgeDisplay();
            }
        });

        showForbiddenExplicitlyCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                setShowForbiddenExplicitly(checkBox.isSelected());
                resetEdgeDisplay();
            }
        });

        showRequiredCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                setShowRequired(checkBox.isSelected());
                resetEdgeDisplay();
            }
        });

        JPanel workbenchPanel = new JPanel();
        workbenchPanel.setLayout(new BorderLayout());
        workbenchPanel.add(new JScrollPane(edgeWorkbench), BorderLayout.CENTER);
        workbenchPanel.setBorder(
                new TitledBorder("Forbidden and Required Edges"));

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

    private void resetEdgeDisplay() {
        List<String> varNames = getVarNames();
        IKnowledge knowledge = getKnowledge();

        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        for (String varName : varNames) {
            graph.addNode(new KnowledgeModelNode(varName));
        }
        if (this.showRequiredByGroups) {
            for (Iterator<KnowledgeEdge> i = knowledge.requiredEdgesIterator(); i.hasNext();) {
                KnowledgeEdge edge = i.next();
                String from = edge.getFrom();
                String to = edge.getTo();
                if (knowledge.isRequiredByGroups(from, to)) {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(to);

                    graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.REQUIRED_BY_GROUPS));
                }
            }
        }

        if (this.showForbiddenByGroups) {
            for (Iterator<KnowledgeEdge> i = knowledge.forbiddenEdgesIterator(); i.hasNext();) {
                KnowledgeEdge edge = i.next();
                String from = edge.getFrom();
                String to = edge.getTo();
                if (knowledge.isForbiddenByGroups(from, to)) {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(to);

                    graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_BY_GROUPS));
                }
            }
        }

        if (showRequired) {
            for (Iterator<KnowledgeEdge> i =
                    knowledge.explicitlyRequiredEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();
                String from = pair.getFrom();
                String to = pair.getTo();

                KnowledgeModelNode fromNode =
                        (KnowledgeModelNode) graph.getNode(from);
                KnowledgeModelNode toNode =
                        (KnowledgeModelNode) graph.getNode(to);

                graph.addEdge(new KnowledgeModelEdge(fromNode, toNode,
                        KnowledgeModelEdge.REQUIRED));
            }
        }


        if (showForbiddenByTiers) {
            for (Iterator<KnowledgeEdge> i =
                    knowledge.forbiddenEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();
                String from = pair.getFrom();
                String to = pair.getTo();

                if (!knowledge.isForbiddenByTiers(from, to)) {
                    continue;
                }

                KnowledgeModelNode fromNode =
                        (KnowledgeModelNode) graph.getNode(from);
                KnowledgeModelNode toNode =
                        (KnowledgeModelNode) graph.getNode(to);

                if (fromNode == null) {
                    graph.addNode(new KnowledgeModelNode(from));
                    fromNode = (KnowledgeModelNode) graph.getNode(from);
                }

                if (toNode == null) {
                    graph.addNode(new KnowledgeModelNode(to));
                    toNode = (KnowledgeModelNode) graph.getNode(to);
                }


                KnowledgeModelEdge knowledgeModelEdge
                        = new KnowledgeModelEdge(fromNode, toNode,
                        KnowledgeModelEdge.FORBIDDEN_BY_TIERS);

                graph.addEdge(knowledgeModelEdge);
                // Hacked fix. Jdramsey.
//                if (!graph.containsEdge(knowledgeModelEdge)) {
//                    graph.addEdge(knowledgeModelEdge);
//                }
            }
        }


        if (showForbiddenExplicitly) {
            for (Iterator<KnowledgeEdge> i = knowledge.explicitlyForbiddenEdgesIterator(); i.hasNext();) {
                KnowledgeEdge pair = i.next();

                String from = pair.getFrom();
                String to = pair.getTo();
                KnowledgeModelNode fromNode =
                        (KnowledgeModelNode) graph.getNode(from);
                KnowledgeModelNode toNode =
                        (KnowledgeModelNode) graph.getNode(to);

                KnowledgeModelEdge edge = new KnowledgeModelEdge(fromNode, toNode,
                        KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
                if(!graph.containsEdge(edge)){
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
    }

    private Component textDisplay() {
        final JButton testButton = new JButton("Test");
        final JButton loadButton = new JButton("Load");

        testButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                try {
                    String text = getTextArea().getText();
                    DataReader reader = new DataReader();
                    reader.parseKnowledge(text.toCharArray());
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Looks good.");
                }
                catch (Exception e1) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            e1.getMessage());
                }
            }
        });

        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String text = getTextArea().getText();
                    DataReader reader = new DataReader();
                    IKnowledge knowledge = reader.parseKnowledge(text.toCharArray());
                    setKnowledge(knowledge);
                    setNumTiers(-1);
                    resetTabbedPane(KnowledgeEditor.this.tabbedPane);
//                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                            "Loaded.");
                }
                catch (Exception e1) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            e1.getMessage());
                }
            }
        });

        Box b = Box.createVerticalBox();

        textArea = new JTextArea();
        resetTextDisplay();

        b.add(getTextArea());

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(testButton);
        b2.add(loadButton);
        b.add(b2);

        return b;
    }

    private void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public void resetTextDisplay() {
        getTextArea().setFont(new Font("Monospaced", Font.PLAIN, 12));
        getTextArea().setBorder(new CompoundBorder(new LineBorder(Color.black),
                new EmptyBorder(3, 3, 3, 3)));

        try {
            IKnowledge knowledge = getKnowledge();
            CharArrayWriter out = new CharArrayWriter();
            Knowledge.saveKnowledge(knowledge, out);
            getTextArea().setText(out.toString());
        }
        catch (IOException e) {
            getTextArea().setText("Could not render knowledge.");
        }

    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    public boolean isShowForbiddenExplicitly() {
        return showForbiddenExplicitly;
    }

    public void setShowForbiddenExplicitly(boolean showForbiddenExplicitly) {
        this.showForbiddenExplicitly = showForbiddenExplicitly;
    }

    public boolean isShowRequired() {
        return showRequired;
    }

    public void setShowRequired(boolean showRequired) {
        this.showRequired = showRequired;
    }

    public boolean isShowForbiddenByTiers() {
        return showForbiddenByTiers;
    }

    public void setShowForbiddenByTiers(boolean showForbiddenByTiers) {
        this.showForbiddenByTiers = showForbiddenByTiers;
    }

    public JTextArea getTextArea() {
        return textArea;
    }

    public int getNumTiers() {
        return numTiers;
    }

    public void setNumTiers(int numTiers) {
        this.numTiers = numTiers;
    }

    public Graph getSourceGraph() {
        return sourceGraph;
    }

    public class DragDropList extends JList implements DropTargetListener,
            DragSourceListener, DragGestureListener {
        private List movedList;

        /**
         * This is the tier that this particular component is representing, or
         * -1 if it's the bin for unused variable names. It's needed so that
         * dropped gadgets can cause variable names to be put into the correct
         * tier.
         */
        private int tier;

        public DragDropList(List items, int tier) {
            if (tier < -1) {
                throw new IllegalArgumentException();
            }

            this.tier = tier;

            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            this.setCellRenderer(new ListCellRenderer() {
                public Component getListCellRendererComponent(JList list,
                                                              Object value, int index, boolean isSelected,
                                                              boolean cellHasFocus) {
                    Color fillColor = new Color(153, 204, 204);
                    Color selectedFillColor = new Color(255, 204, 102);

                    JLabel comp = new JLabel(" " + value + " ");
                    comp.setOpaque(true);

                    if (isSelected) {
                        comp.setForeground(Color.BLACK);
                        comp.setBackground(selectedFillColor);
                    } else {
                        comp.setForeground(Color.BLACK);
                        comp.setBackground(fillColor);
                    }

                    comp.setHorizontalAlignment(SwingConstants.CENTER);
                    comp.setBorder(new CompoundBorder(
                            new MatteBorder(2, 2, 2, 2, Color.WHITE),
                            new LineBorder(Color.BLACK)));

                    return comp;
                }
            });

            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);
            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            for (Object item : items) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public int getTier() {
            return tier;
        }

        public void dragGestureRecognized(DragGestureEvent dragGestureEvent) {
            if (getSelectedIndex() == -1) {
                return;
            }

            List list = getSelectedValuesList();

            if (list == null) {
                getToolkit().beep();
            } else {
                this.movedList = list;
                ListTransferable transferable = new ListTransferable(list);
                dragGestureEvent.startDrag(DragSource.DefaultMoveDrop,
                        transferable, this);
            }
        }

        public void drop(DropTargetDropEvent dropTargetDropEvent) {
            try {
                Transferable tr = dropTargetDropEvent.getTransferable();
                DataFlavor flavor = tr.getTransferDataFlavors()[0];
                List<String> list = (List<String>) tr.getTransferData(flavor);

                for (String name : list) {
                    if (getTier() >= 0) {
                        try {
                            getKnowledge().addToTier(getTier(), name);
                            DefaultListModel model =
                                    (DefaultListModel) getModel();
                            model.addElement(name);
                            sort(model);
                            dropTargetDropEvent.dropComplete(true);
                        }
                        catch (IllegalStateException e) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    e.getMessage());
                            dropTargetDropEvent.dropComplete(false);
                        }
                    } else {
                        getKnowledge().removeFromTiers(name);
                        DefaultListModel model = (DefaultListModel) getModel();
                        model.addElement(name);
                        sort(model);
                        dropTargetDropEvent.dropComplete(true);
                    }
                }
            }
            catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            if (!dsde.getDropSuccess()) {
                return;
            }

            if (this.movedList != null) {
                for (Object aMovedList : this.movedList) {
                    ((DefaultListModel) getModel()).removeElement(aMovedList);
                }

                this.movedList = null;
            }
        }

        public void dragEnter(DropTargetDragEvent dtde) {
        }

        public void dragOver(DropTargetDragEvent dtde) {
        }

        public void dropActionChanged(DropTargetDragEvent dtde) {
        }

        public void dragExit(DropTargetEvent dte) {
        }

        public void dragEnter(DragSourceDragEvent dsde) {
        }

        public void dragOver(DragSourceDragEvent dsde) {
        }

        public void dropActionChanged(DragSourceDragEvent dsde) {
        }

        public void dragExit(DragSourceEvent dse) {
        }

        private void sort(DefaultListModel model) {
            Object[] elements = model.toArray();
            Arrays.sort(elements);

            model.clear();

            for (Object element : elements) {
                model.addElement(element);
            }
        }
    }

//    public class ListSelection implements Transferable {
//
//        /**
//         * The list of graph nodes that constitutes the selection.
//         */
//        private List list;
//
//        /**
//         * Supported dataflavors--only one.
//         */
//        private DataFlavor[] dataFlavors = new DataFlavor[]{
//                new DataFlavor(ListSelection.class, "String List Selection")};
//
//        /**
//         * Constructs a new selection with the given list of graph nodes.
//         */
//        public ListSelection(List list) {
//            if (list == null) {
//                throw new NullPointerException(
//                        "List of list must " + "not be null.");
//            }
//
//            this.list = list;
//        }
//
//        /**
//         * @return an object which represents the data to be transferred.  The
//         * class of the object returned is defined by the representation class
//         * of the flavor.
//         *
//         * @param flavor the requested flavor for the data
//         * @throws IOException                if the data is no longer available
//         *                                    in the requested flavor.
//         * @throws UnsupportedFlavorException if the requested data flavor is
//         *                                    not supported.
//         * @see DataFlavor#getRepresentationClass
//         */
//        public Object getTransferData(DataFlavor flavor)
//                throws UnsupportedFlavorException, IOException {
//            if (!isDataFlavorSupported(flavor)) {
//                throw new UnsupportedFlavorException(flavor);
//            }
//
//            return list;
//        }
//
//        /**
//         * @return whether or not the specified data flavor is supported for
//         * this object.
//         *
//         * @param flavor the requested flavor for the data
//         * @return boolean indicating whether or not the data flavor is
//         *         supported
//         */
//        public boolean isDataFlavorSupported(DataFlavor flavor) {
//            return flavor.equals(getTransferDataFlavors()[0]);
//        }
//
//        /**
//         * @return an array of DataFlavor objects indicating the flavors the
//         * data can be provided in.  The array should be ordered according to
//         * preference for providing the data (from most richly descriptive to
//         * least descriptive).
//         *
//         * @return an array of data flavors in which this data can be
//         *         transferred
//         */
//        public DataFlavor[] getTransferDataFlavors() {
//            return this.dataFlavors;
//        }
//    }

    public static final class MyDragGestureRecognizer
            extends DragGestureRecognizer {
        public MyDragGestureRecognizer(DragSource ds) {
            super(ds);
        }

        protected void registerListeners() {
        }

        protected void unregisterListeners() {
        }

        protected synchronized void appendEvent(InputEvent awtie) {
            super.appendEvent(awtie);
        }
    }
}




