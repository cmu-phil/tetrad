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

package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;

public class JTextFieldWithPrompt extends JTextField {

    /**
     * Stores the prompt text.
     */
    private final String promptText;

    public JTextFieldWithPrompt(String promptText) {
        this.promptText = promptText;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("JTextField with Prompt Example");
            JTextFieldWithPrompt textField = new JTextFieldWithPrompt("Enter text here...");
            textField.setColumns(30);

            frame.add(textField);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (getText().isEmpty() && !isFocusOwner()) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setFont(getFont());
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.drawString(promptText, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top);
            g2d.dispose();
        }
    }
}
