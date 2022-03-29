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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * This class represents a knowledge variable on screen.
 *
 * @author Joseph Ramsey
 */
public class KnowledgeDisplayNode extends DisplayNode {

    /**
     * Constructs a new measured workbench node.
     */
    public KnowledgeDisplayNode(final Node modelNode) {
        setModelNode(modelNode);
        setBackground(DisplayNodeUtils.getNodeFillColor());
        setFont(DisplayNodeUtils.getFont());
    }

    /**
     * Determines whether the given coordinate lie inside the component.
     */
    public boolean contains(final int x, final int y) {
        return getShape().contains(x, y);
    }

    /**
     * Calculates the size of the component based on its name.
     */
    public Dimension getPreferredSize() {
        final FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        final int width = fm.stringWidth(getName()) + 11;
        final int height = fm.getMaxAscent() + 6;
        return new Dimension(width, height);
    }

    /**
     * @return the shape of the component.
     */
    private Shape getShape() {
        return new Rectangle2D.Double(0, 0,
                getPreferredSize().width - 1,
                getPreferredSize().height - 1);
    }

    /**
     * Launches an editor for this node.
     *
     * @param graph Strings which are invalid names for this node.
     */
    public void doDoubleClickAction(final Graph graph) {
        String newName;
        final List<Node> nodes = graph.getNodes();

        final JCheckBox latentCheckBox = new JCheckBox("Latent", false);

        loop:
        while (true) {
            final JTextField nameField = new JTextField(8);

            nameField.setText(getName());
            nameField.setCaretPosition(0);
            nameField.moveCaretPosition(getName().length());

            final JPanel message = new JPanel();

            message.add(new JLabel("Name:"));
            message.add(nameField);

            message.add(latentCheckBox);

            final JOptionPane pane = new JOptionPane(message,
                    JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            final JDialog dialog = pane.createDialog(this, "Node Properties");

            dialog.pack();
            dialog.setVisible(true);

            newName = nameField.getText();

            // Tests that newName is a well formed variable
            if (!NamingProtocol.isLegalName(newName)) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        NamingProtocol.getProtocolDescription());
                continue;
            }
            // Tests that newName is not in the nodes list.
            else if (nodes != null) {
                for (final Node node : nodes) {
                    if (newName.equals(node.toString()) &&
                            !newName.equals(super.getModelNode().getName())) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(), "The name '" +
                                        newName + "' is already being used." +
                                        "\nPlease choose another name.");
                        continue loop;
                    }
                }
            }

            break;
        }

        boolean changed = false;

        if (super.getModelNode() != null /*&&
                !newName.equals(super.getModelNode().getNode())*/) {
            super.getModelNode().setName(newName);
            changed = true;
        }

        if (latentCheckBox.isSelected()) {
            super.getModelNode().setNodeType(NodeType.LATENT);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (changed) {
            firePropertyChange("editingValueChanged", null, null);
        }
    }

    /**
     * Launches an editor for this node.
     */
    public void doDoubleClickAction() {
        doDoubleClickAction(null);
    }

    /**
     * Paints the component.
     *
     * @param g the graphics context.
     */
    public void paint(final Graphics g) {
        final Graphics2D g2 = (Graphics2D) g;
        final FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        final int stringX = 6;
        final int stringY = fm.getMaxAscent() + 1;

        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedFillColor() : DisplayNodeUtils.getNodeFillColor());
        g2.fill(getShape());
        g2.setColor(Color.BLACK);
        g2.draw(getShape());
        g2.setColor(Color.BLACK);
        g2.drawString(getName(), stringX, stringY);
    }
}





