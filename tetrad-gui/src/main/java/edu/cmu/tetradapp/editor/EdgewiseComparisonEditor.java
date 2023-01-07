///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.EdgewiseComparisonModel;
import edu.cmu.tetradapp.model.GraphWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * Provides a little display/editor for notes in the session workbench. This may
 * be elaborated in the future to allow marked up text.
 *
 * @author Joseph Ramsey
 */
public class EdgewiseComparisonEditor extends JPanel {

    private static final long serialVersionUID = 7921819261142670181L;

    /**
     * The model for the note.
     */
    private final EdgewiseComparisonModel comparison;

    /**
     * Constructs the editor given the model
     */
    public EdgewiseComparisonEditor(EdgewiseComparisonModel comparison) {
        this.comparison = comparison;
        setup();
    }

    //============================ Private Methods =========================//
    private void setup() {
        setLayout(new BorderLayout());

        JPanel pane = new JPanel();

        String compareString = this.comparison.getComparisonString();

        Font font = new Font("Monospaced", Font.PLAIN, 14);
        JTextArea textPane = new JTextArea();
        textPane.setText(compareString);

        textPane.setFont(font);

        JScrollPane scrollTextPane = new JScrollPane(textPane);
        scrollTextPane.setPreferredSize(new Dimension(500, 600));

        pane.add(scrollTextPane, new BorderLayout());

        add(pane);
    }

}
