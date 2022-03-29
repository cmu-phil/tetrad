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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.KnowledgeGroup;
import edu.cmu.tetradapp.workbench.LayoutUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

/**
 * Edits fobiddings or requirings of groups of node to other groups of nodes.
 *
 * @author Tyler Gibson
 */
class OtherGroupsEditor extends JPanel {

    /**
     * The knowledge that is being edited.
     */
    private final IKnowledge knowledge;

    /**
     * The variables in the graph.
     */
    private final List<String> variables;

    /**
     * All the interventional variable pairs
     */
    private List<Map> interventionalVarPairs;

    public OtherGroupsEditor(final IKnowledge knowledge, final List<String> vars) {
        if (knowledge == null) {
            throw new NullPointerException("The given knowledge must not be null");
        }
        if (vars == null) {
            throw new NullPointerException("The given list of variables must not be null");
        }
        this.knowledge = knowledge;
        this.variables = new ArrayList<>(vars);

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        this.add(buildComponent());
        //   this.add(Box.createVerticalGlue());
    }

    //===================== Private Methods ============================//
    private Box buildComponent() {
        final Box vBox = Box.createVerticalBox();

        final VariableDragList varList = new VariableDragList(this.variables);
        varList.setBorder(null);

        final JScrollPane pane = new JScrollPane(varList);
        pane.setPreferredSize(new Dimension(500, 50));
        vBox.add(pane);

        final JButton addForbidden = new JButton("Add New Forbidden Group");
        addForbidden.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final KnowledgeGroup targetKnowledgeGroup = new KnowledgeGroup(KnowledgeGroup.FORBIDDEN);
                OtherGroupsEditor.this.knowledge.addKnowledgeGroup(targetKnowledgeGroup);
                rebuild();
            }
        });

        final JButton addRequired = new JButton("Add New Required Group");
        addRequired.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final KnowledgeGroup targetKnowledgeGroup = new KnowledgeGroup(KnowledgeGroup.REQUIRED);
                OtherGroupsEditor.this.knowledge.addKnowledgeGroup(targetKnowledgeGroup);
                rebuild();
            }
        });

        final Box buttons = Box.createHorizontalBox();
        buttons.add(addForbidden);
        buttons.add(Box.createHorizontalStrut(5));
        buttons.add(addRequired);
        buttons.add(Box.createHorizontalGlue());

        vBox.add(Box.createVerticalStrut(5));
        vBox.add(buttons);
        vBox.add(Box.createVerticalStrut(5));

        final Box groupBoxes = Box.createVerticalBox();
        final List<KnowledgeGroup> groups = this.knowledge.getKnowledgeGroups();
        for (int i = 0; i < groups.size(); i++) {
            groupBoxes.add(buildGroupBox(i, groups.get(i)));
        }
        groupBoxes.add(Box.createVerticalGlue());
        final JScrollPane pane2 = new JScrollPane(groupBoxes);
        pane2.setPreferredSize(new Dimension(500, 400));

        vBox.add(pane2);
        vBox.add(LayoutUtils.leftAlignJLabel(new JLabel("Use shift key to select multiple items.")));
        return vBox;
    }

    /**
     * Builds a group box using the given knowledge group (if null a default
     * instance is returned).
     *
     * @return - A required/forbidden work area.
     */
    private Box buildGroupBox(final int index, final KnowledgeGroup group) {
        final Box vBox = Box.createVerticalBox();
        vBox.setBorder(new EmptyBorder(10, 10, 10, 10));

        final Box labelBox = Box.createHorizontalBox();

        final String title;

        // Only add this forbidden checkbox for required group - Zhou
        final JButton forbiddenButton = new JButton("Generate forbidden group");
        forbiddenButton.setFont(forbiddenButton.getFont().deriveFont(11f));
        forbiddenButton.setMargin(new Insets(3, 4, 3, 4));

        // Enable/disable the button
        final Set<String> fromGroup = group.getFromVariables();
        final Set<String> toGroup = group.getToVariables();

        // Don't allow to create forbidden group from this required group if 
        // no variables in the from or to boxes - Zhou
        if (fromGroup.isEmpty() || toGroup.isEmpty()) {
            forbiddenButton.setEnabled(false);
        } else {
            forbiddenButton.setEnabled(true);
        }

        // Add skinny hand
        forbiddenButton.addActionListener((e) -> {
            final Set<String> toForbiddenGroup = new HashSet<>();

            this.variables.forEach(var -> {
                if (!fromGroup.contains(var) && !toGroup.contains(var)) {
                    toForbiddenGroup.add(var);
                }
            });

            final KnowledgeGroup targetKnowledgeGroup = new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, fromGroup, toForbiddenGroup);

            this.knowledge.addKnowledgeGroup(targetKnowledgeGroup);

            rebuild();
        });

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            title = "Forbidden Group";
        } else {
            title = "Required Group";
        }

        final JButton remove = new JButton("Remove");
        remove.setFont(remove.getFont().deriveFont(11f));
        remove.setMargin(new Insets(3, 4, 3, 4));
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                OtherGroupsEditor.this.knowledge.removeKnowledgeGroup(index);

                rebuild();
            }
        });

        labelBox.add(new JLabel(title));

        // Only add this forbidden button for required group - Zhou
        if (group.getType() == KnowledgeGroup.REQUIRED) {
            labelBox.add(Box.createHorizontalGlue());
            labelBox.add(forbiddenButton);
        }

        labelBox.add(Box.createHorizontalGlue());
        labelBox.add(remove);

        vBox.add(labelBox);
        vBox.add(Box.createVerticalStrut(2));

        final Box box = Box.createHorizontalBox();
        if (group != null) {
            final GroupVariableDragList fromList = new GroupVariableDragList(index, true);
            final GroupVariableDragList toList = new GroupVariableDragList(index, false);

            final JScrollPane pane1 = new JScrollPane(fromList);
            pane1.setPreferredSize(new Dimension(180, 50));
            final JScrollPane pane2 = new JScrollPane(toList);
            pane2.setPreferredSize(new Dimension(180, 50));

            box.add(pane1);
            box.add(new Arrow());
            box.add(pane2);
        }
        vBox.add(box);

        return vBox;
    }

    /**
     * Rebuilds the components
     */
    private void rebuild() {
        this.removeAll();
        this.add(buildComponent());
        revalidate();
        repaint();
    }

    /**
     * Sorts the elemenets of a default list model
     */
    private static void sort(final DefaultListModel model) {
        final Object[] elements = model.toArray();
        Arrays.sort(elements);

        model.clear();

        for (final Object element : elements) {
            model.addElement(element);
        }
    }

    private static Set<String> getElementsInModel(final DefaultListModel model) {
        final Set<String> elements = new HashSet<>();
        for (int i = 0; i < model.getSize(); i++) {
            elements.add((String) model.getElementAt(i));
        }
        return elements;
    }

    private static boolean modelContains(final Object o, final DefaultListModel model) {
        for (int i = 0; i < model.getSize(); i++) {
            if (o.equals(model.getElementAt(i))) {
                return true;
            }
        }
        return false;
    }

    //========================== Inner classes =====================================//

    /**
     * Renders an arrow from the left to right.
     */
    private static class Arrow extends JPanel {

        private final Color color;

        public Arrow() {
            final Color b = Color.BLACK;
            this.color = new Color(b.getRed(), b.getGreen(), b.getBlue(), 150);
            this.setMinimumSize(new Dimension(22, 22));
        }

        public void paintComponent(final Graphics g) {
            final Dimension size = getSize();
            // if too small, just don't draw
            if (size.width < 21 || size.height < 21) {
                return;
            }
            final int mid = size.height / 2;
            g.setColor(this.color);
            final int end = size.width - 15;
            g.fillRect(5, mid, end - 5, 2);
            g.fillPolygon(new int[]{end, end + 10, end}, new int[]{mid + 10, mid, mid - 10}, 3);
        }
    }

    /**
     * Renderer for variables.
     */
    private static class VariableRenderer extends DefaultListCellRenderer {

        private final Color fillColor = new Color(153, 204, 204);
        private final Color selectedFillColor = new Color(255, 204, 102);

        public VariableRenderer() {
            this.setOpaque(true);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new CompoundBorder(
                    new MatteBorder(2, 2, 2, 2, Color.WHITE),
                    new LineBorder(Color.BLACK)));
        }

        public Component getListCellRendererComponent(final JList list, final Object value, final int index,
                                                      final boolean isSelected, final boolean cellHasFocus) {

            setText(" " + value + " ");
            if (isSelected) {
                setForeground(Color.BLACK);
                setBackground(this.selectedFillColor);
            } else {
                setForeground(Color.BLACK);
                setBackground(this.fillColor);
            }

            return this;
        }
    }

    private class GroupVariableDragList extends JList implements DropTargetListener, DragSourceListener, DragGestureListener {

        /**
         * Index of the group that is being edited.
         */
        private final int index;

        /**
         * States that whether this if the "from" side or the "to" side
         * (true=from, false=to).
         */
        private final boolean from;

        public GroupVariableDragList(final int index, final boolean from) {
            this.index = index;
            this.from = from;
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            this.setCellRenderer(new VariableRenderer());

            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);

            final DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            final KnowledgeGroup group = OtherGroupsEditor.this.knowledge.getKnowledgeGroups().get(index);
            final Set<String> vars = from ? group.getFromVariables() : group.getToVariables();
            for (final Object item : vars) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public void drop(final DropTargetDropEvent dtde) {
            try {
                final Transferable tr = dtde.getTransferable();
                final DataFlavor flavor = tr.getTransferDataFlavors()[0];
                final DefaultListModel model = (DefaultListModel) getModel();
                boolean added = false;
                for (final Object var : (List) tr.getTransferData(flavor)) {
                    if (!OtherGroupsEditor.modelContains(var, model) && !opposingContains((String) var)) {
                        model.addElement(var);
                        added = true;
                    }
                }
                // nothing was added so drop is incomplete.
                if (!added) {
                    dtde.getDropTargetContext().dropComplete(false);
                    return;
                }

                OtherGroupsEditor.sort(model);
                final KnowledgeGroup group = OtherGroupsEditor.this.knowledge.getKnowledgeGroups().get(this.index);
                final KnowledgeGroup g;
                if (this.from) {
                    g = new KnowledgeGroup(group.getType(), OtherGroupsEditor.getElementsInModel(model), group.getToVariables());
                } else {
                    g = new KnowledgeGroup(group.getType(), group.getFromVariables(), OtherGroupsEditor.getElementsInModel(model));
                }
                try {
                    OtherGroupsEditor.this.knowledge.setKnowledgeGroup(this.index, g);
                    dtde.getDropTargetContext().dropComplete(true);

                    rebuild(); // Zhou added this to reflect the update
                } catch (final IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(OtherGroupsEditor.this, ex.getMessage());
                    // rebuild so the old values are resorted.
                    rebuild();
                    dtde.getDropTargetContext().dropComplete(false);
                }

            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }

        public void dragDropEnd(final DragSourceDropEvent dsde) {
            if (dsde.getDropSuccess()) {
                final Transferable t = dsde.getDragSourceContext().getTransferable();
                try {
                    final List list = (List) t.getTransferData(ListTransferable.DATA_FLAVOR);
                    final DefaultListModel model = (DefaultListModel) getModel();
                    for (final Object o : list) {
                        model.removeElement(o);
                    }
                    final KnowledgeGroup group = OtherGroupsEditor.this.knowledge.getKnowledgeGroups().get(this.index);
                    final KnowledgeGroup g;
                    if (this.from) {
                        g = new KnowledgeGroup(group.getType(), OtherGroupsEditor.getElementsInModel(model), group.getToVariables());
                    } else {
                        g = new KnowledgeGroup(group.getType(), group.getFromVariables(), OtherGroupsEditor.getElementsInModel(model));
                    }
                    try {
                        OtherGroupsEditor.this.knowledge.setKnowledgeGroup(this.index, g);

                        rebuild(); // Zhou added this to reflect the update
                    } catch (final IllegalArgumentException ex) {
                        JOptionPane.showMessageDialog(OtherGroupsEditor.this, ex.getMessage());
                        // rebuild so the old values are resorted.
                        rebuild();
                    }
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        public void dragGestureRecognized(final DragGestureEvent dge) {
            if (getSelectedIndex() == -1) {
                return;
            }

            final List list = getSelectedValuesList();
            if (list == null) {
                getToolkit().beep();
            } else {
                final ListTransferable transferable = new ListTransferable(list);
                dge.startDrag(DragSource.DefaultMoveDrop,
                        transferable, this);
            }
        }

        private boolean opposingContains(final String o) {
            final KnowledgeGroup group = OtherGroupsEditor.this.knowledge.getKnowledgeGroups().get(this.index);
            final Set<String> opposite = this.from ? group.getToVariables() : group.getFromVariables();
            return opposite.contains(o);
        }

        // ===================== Not implemented ====================//
        public void dragEnter(final DropTargetDragEvent dtde) {

        }

        public void dragOver(final DropTargetDragEvent dtde) {

        }

        public void dropActionChanged(final DropTargetDragEvent dtde) {

        }

        public void dragExit(final DropTargetEvent dte) {

        }

        public void dragEnter(final DragSourceDragEvent dsde) {

        }

        public void dragOver(final DragSourceDragEvent dsde) {

        }

        public void dropActionChanged(final DragSourceDragEvent dsde) {

        }

        public void dragExit(final DragSourceEvent dse) {

        }

    }

    /**
     * A list that allows the user to move variables from it to others. The
     * variables are not deleted when moved.
     */
    private static class VariableDragList extends JList implements DragGestureListener, DropTargetListener {

        public VariableDragList(final List<String> items) {
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            this.setCellRenderer(new VariableRenderer());

            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);

            final DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            for (final Object item : items) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public void dragGestureRecognized(final DragGestureEvent dragGestureEvent) {
            if (getSelectedIndex() == -1) {
                return;
            }

            final List list = getSelectedValuesList();
            if (list == null) {
                getToolkit().beep();
            } else {
                final ListTransferable transferable = new ListTransferable(list);
                dragGestureEvent.startDrag(DragSource.DefaultMoveDrop,
                        transferable);
            }
        }

        public void drop(final DropTargetDropEvent dtde) {
            dtde.getDropTargetContext().dropComplete(true);
        }

        public void dragEnter(final DropTargetDragEvent dtde) {

        }

        public void dragOver(final DropTargetDragEvent dtde) {

        }

        public void dropActionChanged(final DropTargetDragEvent dtde) {

        }

        public void dragExit(final DropTargetEvent dte) {

        }

    }

}
