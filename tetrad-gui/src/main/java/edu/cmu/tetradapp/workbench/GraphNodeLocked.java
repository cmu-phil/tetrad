///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

/**
 * Represents a locked policy variable in the workbench. Appears as a padlock.
 *
 * @author josephramsey
 * @author Willie Wheeler
 * @version $Id: $Id
 */
public class GraphNodeLocked extends DisplayNode {

    /**
     * Constructs a new node for representing latent variables in the graph workbench.
     *
     * @param modelNode a {@link edu.cmu.tetrad.graph.Node} object
     */
    public GraphNodeLocked(Node modelNode) {
        setModelNode(modelNode);
        if (modelNode.getNodeType() != NodeType.LATENT) {
            throw new IllegalArgumentException("GraphNodeLatent requires " +
                                               "a GraphNode of type NodeType.LATENT.");
        }

        setDisplayComp(new LatentDisplayComp(modelNode.getName()));
    }

    /**
     * {@inheritDoc}
     */
    public void doDoubleClickAction(Graph graph) {
        String newName;
        List<Node> nodes = graph.getNodes();
        JCheckBox latentCheckBox = new JCheckBox("Latent", true);
        JCheckBox selectionBiasCheckBox = new JCheckBox("SelectionBias", true);

        newName = chooseNewVariableName(latentCheckBox, selectionBiasCheckBox, nodes);

        boolean changed = false;

        if (this.getModelNode() != null &&
            !newName.equals(this.getModelNode().getName())) {
            this.getModelNode().setName(newName);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (!latentCheckBox.isSelected()) {
            this.getModelNode().setNodeType(NodeType.MEASURED);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (changed) {
            firePropertyChange("editingValueChanged", null, null);
        }
    }

    private String chooseNewVariableName(JCheckBox latentCheckBox, JCheckBox selectionBiasCheckBox,
                                         List<Node> nodes) {
        String newName;

        LOOP:
        while (true) {
            JTextField nameField = new JTextField(8);

            // This makes sure the name field has focus when the dialog (below)
            // is made visible, but that after this it allows other gadgets
            // to grab focus.
            nameField.addFocusListener(new FocusAdapter() {
                boolean alreadyLostFocus;

                public void focusLost(FocusEvent e) {
                    if (this.alreadyLostFocus) return;
                    JTextField field = (JTextField) e.getSource();
                    field.grabFocus();
                    this.alreadyLostFocus = true;
                }
            });

            nameField.setText(getName());
            nameField.setCaretPosition(0);
            nameField.moveCaretPosition(getName().length());

            JPanel message = new JPanel();

            message.add(new JLabel("Name:"));
            message.add(nameField);

            message.add(latentCheckBox);
            message.add(selectionBiasCheckBox);

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
                        !newName.equals(this.getModelNode().getName())) {
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

    /**
     * <p>doDoubleClickAction.</p>
     */
    public void doDoubleClickAction() {
        doDoubleClickAction(new EdgeListGraph());
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}






