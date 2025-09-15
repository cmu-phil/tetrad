/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.CheckKnowledgeModel;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * Provides a little display/editor for notes in the session workbench. This may be elaborated in the future to allow
 * marked up text.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CheckKnowledgeEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 7921819261142670181L;

    /**
     * The model for the note.
     */
    private final CheckKnowledgeModel comparison;

    /**
     * The parameters for the comparison.
     */
    private final Parameters params;

    /**
     * The text area for the note.
     */
    private JTextArea area;

    /**
     * Constructs the editor given the model
     *
     * @param comparison a {@link edu.cmu.tetradapp.model.CheckKnowledgeModel} object
     */
    public CheckKnowledgeEditor(CheckKnowledgeModel comparison) {
        this.comparison = comparison;
        this.params = comparison.getParams();
        setup();
    }

    //============================ Private Methods =========================//
    private void setup() {
        setLayout(new BorderLayout());

        JPanel pane = new JPanel();

        Font font = new Font("Monospaced", Font.PLAIN, 14);
        area = new JTextArea();
        area.setText(tableTextWithHeader());

        area.setFont(font);

        JScrollPane scrollTextPane = new JScrollPane(area);
        scrollTextPane.setPreferredSize(new Dimension(500, 600));

        pane.add(scrollTextPane, new BorderLayout());

        add(pane);

        this.area.setText(comparison.getComparisonString());
        this.area.moveCaretPosition(0);
        this.area.setSelectionStart(0);
        this.area.setSelectionEnd(0);

        this.area.repaint();
    }

    private String tableTextWithHeader() {
        return this.comparison.getComparisonString();
    }
}
