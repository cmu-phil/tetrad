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
 * Represents a selection variable in the workbench. Appears as an oval with the variable name in it. Clicking on the oval
 * pops up a dialog that lets the user modify the name of the variable and whether the variable is selection.
 *
 * @author josephramsey
 * @author Willie Wheeler
 * @version $Id: $Id
 */
public class GraphNodeSelection extends DisplayNode {

    /**
     * Constructs a new node for representing selection variables in the graph workbench.
     *
     * @param modelNode a {@link edu.cmu.tetrad.graph.Node} object
     */
    public GraphNodeSelection(Node modelNode) {
        setModelNode(modelNode);
        if (modelNode.getNodeType() != NodeType.SELECTION) {
            throw new IllegalArgumentException("GraphNodeSelection requires " +
                                               "a GraphNode of type NodeType.SELECTION.");
        }

        setDisplayComp(new SelectionDisplayComp(modelNode.getName()));
    }

    /**
     * {@inheritDoc}
     */
    public void doDoubleClickAction(Graph graph) {
        String newName;
        List<Node> nodes = graph.getNodes();
        JComboBox<String> typeBox = new JComboBox<>();
        typeBox.addItem("Measured");
        typeBox.addItem("Latent");
        typeBox.addItem("Selection");

        typeBox.setSelectedItem("Selection");

        newName = chooseNewVariableName(typeBox, nodes);

        boolean changed = false;

        if (this.getModelNode() != null &&
            !newName.equals(this.getModelNode().getName())) {
            this.getModelNode().setName(newName);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (typeBox.getSelectedItem().equals("Measured")) {
            this.getModelNode().setNodeType(NodeType.MEASURED);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

        if (typeBox.getSelectedItem().equals("Latent")) {
            this.getModelNode().setNodeType(NodeType.LATENT);
            firePropertyChange("resetGraph", null, null);
            changed = true;
        }

//        if (typeBox.getSelectedItem().equals("Selection")) {
//            this.getModelNode().setNodeType(NodeType.SELECTION);
//            firePropertyChange("resetGraph", null, null);
//            changed = true;
//        }

        if (changed) {
            firePropertyChange("editingValueChanged", null, null);
        }
    }

    private String chooseNewVariableName(JComboBox<String> typeBox,
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

            message.add(typeBox);

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






