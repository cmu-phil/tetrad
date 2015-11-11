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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NamingProtocol;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

/**
 * Represents a randomized policy variable in the workbench. Appears as a
 * die.
 *
 * @author Joseph Ramsey
 * @author Willie Wheeler
 */
public class GraphNodeRandomized extends DisplayNode {

    /**
     * Constructs a new node for representing latent variables in the
     * graph workbench.
     */
    public GraphNodeRandomized(Node modelNode) {
        setModelNode(modelNode);
        if (modelNode.getNodeType() != NodeType.LATENT) {
            throw new IllegalArgumentException("GraphNodeLatent requires " +
                    "a GraphNode of type NodeType.LATENT.");
        }

        setDisplayComp(new LatentDisplayComp(modelNode.getName()));
    }

    public void doDoubleClickAction(Graph graph) {
        String newName;
        List<Node> nodes = graph.getNodes();
        JCheckBox latentCheckBox = new JCheckBox("Latent", true);

        newName = chooseNewVariableName(latentCheckBox, nodes);

        boolean changed = false;

        if (super.getModelNode() != null &&
                !newName.equals(super.getModelNode().getName())) {
            super.getModelNode().setName(newName);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (!latentCheckBox.isSelected()) {
            super.getModelNode().setNodeType(NodeType.MEASURED);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (changed) {
            firePropertyChange("editingValueChanged", null, null);
        }
    }

    private String chooseNewVariableName(JCheckBox latentCheckBox,
            List<Node> nodes) {
        String newName;

        LOOP:
        while (true) {
            JTextField nameField = new JTextField(8);

            // This makes sure the name field has focus when the dialog (below)
            // is made visible, but that after this it allows other gadgets
            // to grab focus.
            nameField.addFocusListener(new FocusAdapter() {
                boolean alreadyLostFocus = false;

                public void focusLost(FocusEvent e) {
                    if (alreadyLostFocus) return;
                    JTextField field = (JTextField) e.getSource();
                    field.grabFocus();
                    alreadyLostFocus = true;
                }
            });

            nameField.setText(getName());
            nameField.setCaretPosition(0);
            nameField.moveCaretPosition(getName().length());

            JPanel message = new JPanel();

            message.add(new JLabel("Name:"));
            message.add(nameField);

            message.add(latentCheckBox);

            JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = pane.createDialog(this, "Node Properties");

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
                for (Node node : nodes) {
                    if (newName.equals(node.toString()) &&
                            !newName.equals(super.getModelNode().getName())) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(), "The name '" +
                                newName + "' is already being used." +
                                "\nPlease choose another name.");
                        continue LOOP;
                    }
                }
            }

            break;
        }
        return newName;
    }

    public void doDoubleClickAction() {
        doDoubleClickAction(null);
    }
}





