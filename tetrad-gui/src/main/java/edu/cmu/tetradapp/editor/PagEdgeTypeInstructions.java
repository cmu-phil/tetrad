/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.editor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.net.URL;

/**
 * Represents an action to display PAG Edge Type Instructions in a GraphWorkbench. This class extends AbstractAction and
 * implements ClipboardOwner.
 */
public class PagEdgeTypeInstructions extends AbstractAction implements ClipboardOwner {

    /**
     * Represents an action to display PAG Edge Type Instructions in a GraphWorkbench.
     */
    public PagEdgeTypeInstructions() {
        super("PAG Edge Type Instructions");
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event that triggered the action.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        final String helpPage = "/docs/javahelp/manual/graph_edge_types.html";

        try {
            URL url = getClass().getResource(helpPage);
            if (url == null) {
                throw new IllegalArgumentException("Help page not found: " + helpPage);
            }

            JEditorPane editorPane = new JEditorPane();
            editorPane.setEditable(false);
            editorPane.setContentType("text/html");
            editorPane.setPage(url);
            editorPane.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(editorPane);
            scrollPane.setPreferredSize(new Dimension(900, 650));

            Object source = e.getSource();
            java.awt.Component sourceComponent =
                    (source instanceof java.awt.Component) ? (java.awt.Component) source : null;
            java.awt.Window owner =
                    sourceComponent != null ? SwingUtilities.getWindowAncestor(sourceComponent) : null;

            JDialog dialog = new JDialog(owner, "PAG Edge Types", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.getContentPane().setLayout(new BorderLayout());
            dialog.getContentPane().add(scrollPane, BorderLayout.CENTER);

            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(ev -> dialog.dispose());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(closeButton);
            dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load help page:\n" + ex.getMessage(),
                    "Help Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Called when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost ownership
     * @param contents  the contents that were lost
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




