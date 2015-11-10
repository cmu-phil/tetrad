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
import java.util.*;
import java.util.List;

/**
 * Edits fobiddings or requirings of groups of node to other groups of nodes.
 *
 * @author Tyler Gibson
 */
class OtherGroupsEditor extends JPanel {

    /**
     * The knowledge that is being edited.
     */
    private IKnowledge knowledge;


    /**
     * The variables in the graph.
     */
    private List<String> variables;


    public OtherGroupsEditor(IKnowledge knowledge, List<String> vars) {
        if (knowledge == null) {
            throw new NullPointerException("The given knowledge must not be null");
        }
        if (vars == null) {
            throw new NullPointerException("The given list of variables must not be null");
        }
        this.knowledge = knowledge;
        this.variables = new ArrayList<String>(vars);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        this.add(buildComponent());
        //   this.add(Box.createVerticalGlue());
    }

    //===================== Private Methods ============================//


    private Box buildComponent() {
        Box vBox = Box.createVerticalBox();

        VariableDragList varList = new VariableDragList(this.variables);
        varList.setBorder(null);

        JScrollPane pane = new JScrollPane(varList);
        pane.setPreferredSize(new Dimension(500, 50));
        vBox.add(pane);

        JButton addForbidden = new JButton("Add Forbidden Group");
        addForbidden.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                knowledge.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.FORBIDDEN));
                rebuild();
            }
        });


        JButton addRequired = new JButton("Add Required Group");
        addRequired.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                knowledge.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.REQUIRED));
                rebuild();
            }
        });


        Box buttons = Box.createHorizontalBox();
        buttons.add(addForbidden);
        buttons.add(Box.createHorizontalStrut(5));
        buttons.add(addRequired);
        buttons.add(Box.createHorizontalGlue());

        vBox.add(Box.createVerticalStrut(5));
        vBox.add(buttons);
        vBox.add(Box.createVerticalStrut(5));

        Box groupBoxes = Box.createVerticalBox();
        List<KnowledgeGroup> groups = this.knowledge.getKnowledgeGroups();
        for (int i = 0; i < groups.size(); i++) {
            groupBoxes.add(buildGroupBox(i, groups.get(i)));
        }
        groupBoxes.add(Box.createVerticalGlue());
        JScrollPane pane2 = new JScrollPane(groupBoxes);
        pane2.setPreferredSize(new Dimension(500, 400));

        vBox.add(pane2);        
        vBox.add(LayoutUtils.leftAlignJLabel(new JLabel("Use shift key to select multiple items.")));
        return vBox;
    }


    /**
     * Builds a group box using the given knowledge group (if null a default instance
     * is returned).
     *
     * @param group
     * @return - A required/forbidden work area.
     */
    private Box buildGroupBox(final int index, final KnowledgeGroup group) {
        Box vBox = Box.createVerticalBox();
        vBox.setBorder(new EmptyBorder(10, 10, 10, 10));

        Box labelBox = Box.createHorizontalBox();
        String title;
        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            title = "Forbidden Group";
        } else {
            title = "Required Group";
        }
        JButton remove = new JButton("Remove");
        remove.setFont(remove.getFont().deriveFont(11f));
        remove.setMargin(new Insets(3, 4, 3, 4));
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                knowledge.removeKnowledgeGroup(index);
                rebuild();
            }
        });


        labelBox.add(new JLabel(title));
        labelBox.add(Box.createHorizontalGlue());
        labelBox.add(remove);

        vBox.add(labelBox);
        vBox.add(Box.createVerticalStrut(2));

        Box box = Box.createHorizontalBox();
        if (group != null) {
            GroupVariableDragList fromList = new GroupVariableDragList(index, true);
            GroupVariableDragList toList = new GroupVariableDragList(index, false);

            JScrollPane pane1 = new JScrollPane(fromList);
            pane1.setPreferredSize(new Dimension(180, 50));
            JScrollPane pane2 = new JScrollPane(toList);
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
     *
     * @param model
     */
    private static void sort(DefaultListModel model) {
        Object[] elements = model.toArray();
        Arrays.sort(elements);

        model.clear();

        for (Object element : elements) {
            model.addElement(element);
        }
    }


    private static Set<String> getElementsInModel(DefaultListModel model) {
        Set<String> elements = new HashSet<String>();
        for (int i = 0; i < model.getSize(); i++) {
            elements.add((String) model.getElementAt(i));
        }
        return elements;
    }


    private static boolean modelContains(Object o, DefaultListModel model) {
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

        private Color color;

        public Arrow() {
            Color b = Color.BLACK;
            color = new Color(b.getRed(), b.getGreen(), b.getBlue(), 150);
            this.setMinimumSize(new Dimension(22, 22));
        }

        public void paintComponent(Graphics g) {
            Dimension size = getSize();
            // if too small, just don't draw
            if (size.width < 21 || size.height < 21) {
                return;
            }
            int mid = size.height / 2;
            g.setColor(color);
            int end = size.width - 15;
            g.fillRect(5, mid, end - 5, 2);
            g.fillPolygon(new int[]{end, end + 10, end}, new int[]{mid + 10, mid, mid - 10}, 3);
        }
    }


    /**
     * Renderer for variables.
     */
    private static class VariableRenderer extends DefaultListCellRenderer {

        private Color fillColor = new Color(153, 204, 204);
        private Color selectedFillColor = new Color(255, 204, 102);

        public VariableRenderer() {
            this.setOpaque(true);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new CompoundBorder(
                    new MatteBorder(2, 2, 2, 2, Color.WHITE),
                    new LineBorder(Color.BLACK)));
        }


        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {

            setText(" " + value + " ");
            if (isSelected) {
                setForeground(Color.BLACK);
                setBackground(selectedFillColor);
            } else {
                setForeground(Color.BLACK);
                setBackground(fillColor);
            }

            return this;
        }
    }


    private class GroupVariableDragList extends JList implements DropTargetListener, DragSourceListener, DragGestureListener {

        /**
         * Index of the group that is being edited.
         */
        private int index;

        /**
         * States that whether this if the "from" side or the "to" side (true=from, false=to).
         */
        private final boolean from;


        public GroupVariableDragList(int index, boolean from) {
            this.index = index;
            this.from = from;
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            this.setCellRenderer(new VariableRenderer());

            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);

            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            KnowledgeGroup group = knowledge.getKnowledgeGroups().get(index);
            Set<String> vars = from ? group.getFromVariables() : group.getToVariables();
            for (Object item : vars) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public void drop(DropTargetDropEvent dtde) {
            try {
                Transferable tr = dtde.getTransferable();
                DataFlavor flavor = tr.getTransferDataFlavors()[0];
                DefaultListModel model = (DefaultListModel) getModel();
                boolean added = false;
                for (Object var : (List) tr.getTransferData(flavor)) {
                    if (!modelContains(var, model) && !opposingContains((String) var)) {
                        model.addElement(var);
                        added = true;
                    }
                }
                // nothing was added so drop is incomplete.
                if (!added) {
                    dtde.getDropTargetContext().dropComplete(false);
                    return;
                }

                sort(model);
                KnowledgeGroup group = knowledge.getKnowledgeGroups().get(index);
                KnowledgeGroup g;
                if (this.from) {
                    g = new KnowledgeGroup(group.getType(), getElementsInModel(model), group.getToVariables());
                } else {
                    g = new KnowledgeGroup(group.getType(), group.getFromVariables(), getElementsInModel(model));
                }
                try {
                    knowledge.setKnowledgeGroup(index, g);
                    dtde.getDropTargetContext().dropComplete(true);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(OtherGroupsEditor.this, ex.getMessage());
                    // rebuild so the old values are resorted.
                    rebuild();
                    dtde.getDropTargetContext().dropComplete(false);
                }


            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


        public void dragDropEnd(DragSourceDropEvent dsde) {
            if (dsde.getDropSuccess()) {
                Transferable t = dsde.getDragSourceContext().getTransferable();
                try {
                    List list = (List) t.getTransferData(ListTransferable.DATA_FLAVOR);
                    DefaultListModel model = (DefaultListModel) getModel();
                    for (Object o : list) {
                        model.removeElement(o);
                    }
                    KnowledgeGroup group = knowledge.getKnowledgeGroups().get(index);
                    KnowledgeGroup g;
                    if (this.from) {
                        g = new KnowledgeGroup(group.getType(), getElementsInModel(model), group.getToVariables());
                    } else {
                        g = new KnowledgeGroup(group.getType(), group.getFromVariables(), getElementsInModel(model));
                    }
                    try {
                        knowledge.setKnowledgeGroup(index, g);
                    } catch (IllegalArgumentException ex) {
                        JOptionPane.showMessageDialog(OtherGroupsEditor.this, ex.getMessage());
                        // rebuild so the old values are resorted.
                        rebuild();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }


        public void dragGestureRecognized(DragGestureEvent dge) {
            if (getSelectedIndex() == -1) {
                return;
            }

            List list = getSelectedValuesList();
            if (list == null) {
                getToolkit().beep();
            } else {
                ListTransferable transferable = new ListTransferable(list);
                dge.startDrag(DragSource.DefaultMoveDrop,
                        transferable, this);
            }
        }


        private boolean opposingContains(String o) {
            KnowledgeGroup group = knowledge.getKnowledgeGroups().get(index);
            Set<String> opposite = from ? group.getToVariables() : group.getFromVariables();
            return opposite.contains(o);
        }

        // ===================== Not implemented ====================//

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


    }


    /**
     * A list that allows the user to move variables from it to others. The variables are not deleted when
     * moved.
     */
    private static class VariableDragList extends JList implements DragGestureListener, DropTargetListener {

        public VariableDragList(List<String> items) {
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            this.setCellRenderer(new VariableRenderer());

            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);

            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            for (Object item : items) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public void dragGestureRecognized(DragGestureEvent dragGestureEvent) {
            if (getSelectedIndex() == -1) {
                return;
            }

            List list = getSelectedValuesList();
            if (list == null) {
                getToolkit().beep();
            } else {
                ListTransferable transferable = new ListTransferable(list);
                dragGestureEvent.startDrag(DragSource.DefaultMoveDrop,
                        transferable);
            }
        }

        public void drop(DropTargetDropEvent dtde) {
            dtde.getDropTargetContext().dropComplete(true);
        }

        public void dragEnter(DropTargetDragEvent dtde) {

        }

        public void dragOver(DropTargetDragEvent dtde) {

        }

        public void dropActionChanged(DropTargetDragEvent dtde) {

        }

        public void dragExit(DropTargetEvent dte) {

        }

    }


}




