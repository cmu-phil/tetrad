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

package edu.cmu.tetradapp.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Shows Peter's algorithm flowcharts.
 *
 * @author josephramsey
 */
final class AlgorithmFlowchartAction extends AbstractAction {

    private final Component parent;

    /**
     * Creates a new close session action for the given desktop.
     */
    public AlgorithmFlowchartAction(Component parent) {
        super("Algorithm Flowchart");
        this.parent = parent;
    }

    /**
     * This method handles the action event triggered by a user interaction.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        showFlowchartDialog(parent);
    }

    public static void showFlowchartDialog(Component parent) {
        // Load images from resources
        ImageIcon slide1 = new ImageIcon(AlgorithmFlowchartAction.class.getResource("/docs/manual/images/flowchart/Slide1.png"));
        ImageIcon slide2 = new ImageIcon(AlgorithmFlowchartAction.class.getResource("/docs/manual/images/flowchart/Slide2.png"));

        // Stack them vertically in a panel
        JPanel imagePanel = new JPanel();
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.Y_AXIS));
        imagePanel.setBackground(Color.WHITE);
        imagePanel.add(new JLabel(slide1));
        imagePanel.add(Box.createVerticalStrut(10));
        imagePanel.add(new JLabel(slide2));

        // Wrap in a scroll pane
        JScrollPane scrollPane = new JScrollPane(imagePanel);
        scrollPane.setPreferredSize(new Dimension(750, 700));

        // Show in a dialog
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                "Tetrad Flowchart", false); // false = non-modal
        dialog.setContentPane(scrollPane);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}




