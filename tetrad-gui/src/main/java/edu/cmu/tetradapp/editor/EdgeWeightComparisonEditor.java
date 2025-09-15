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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.EdgeWeightComparison;

import javax.swing.*;
import java.awt.*;

/**
 * <p>EdgeWeightComparisonEditor class.</p>
 *
 * @author Michael Freenor
 * @version $Id: $Id
 */
public class EdgeWeightComparisonEditor extends JPanel {
    /**
     * The model for the note.
     */
    private final EdgeWeightComparison comparison;


    /**
     * Constructs the editor given the model
     *
     * @param comparison a {@link edu.cmu.tetradapp.model.EdgeWeightComparison} object
     */
    public EdgeWeightComparisonEditor(EdgeWeightComparison comparison) {
        this.comparison = comparison;
        setup();
    }

    //============================ Private Methods =========================//


    private void setup() {
        String compareString = this.comparison.getDisplayString();

        Font font = new Font("Monospaced", Font.PLAIN, 14);
        JTextArea textPane = new JTextArea();
        textPane.setText(compareString);

        textPane.setFont(font);
//        textPane.setCaretPosition(textPane.getStyledDocument().getLength());

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setPreferredSize(new Dimension(400, 400));

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.add(Box.createVerticalStrut(10));

        Box box = Box.createHorizontalBox();
        this.add(box);
        this.add(Box.createVerticalStrut(10));

        Box box1 = Box.createHorizontalBox();
        box1.add(new JLabel("Graph Comparison: "));
        box1.add(Box.createHorizontalGlue());

        add(box1);
        setLayout(new BorderLayout());
        add(scroll);
    }
}





