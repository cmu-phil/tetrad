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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.TextAreaOutputStream;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;

/**
 * The area used to display log output.
 *
 * @author Tyler Gibson
 */
class TetradLogArea extends JPanel {


    /**
     * The output stream that is used to log to.
     */
    private TextAreaOutputStream stream;

    /**
     * Constructs the log area.
     */
    public TetradLogArea(TetradDesktop tetradDesktop) {
        super(new BorderLayout());
        if (tetradDesktop == null) {
            throw new NullPointerException("The given desktop must not be null");
        }

        // build the text area.
        JTextArea textArea = new JTextArea();
        if (TetradLogger.getInstance().isDisplayLogEnabled()) {
            this.stream = new TextAreaOutputStream(textArea);
            TetradLogger.getInstance().addOutputStream(this.stream);
        }
        JScrollPane pane = new JScrollPane(textArea);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        // finally add the components to the panel.
        add(createHeader(), BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
    }

    //================================= Public methods =============================//


    /**
     * @return the output stream that is being used to log messages to the log area.
     */
    public OutputStream getOutputStream() {
        return this.stream;
    }

    //============================== Private Methods ============================//

    /**
     * Creates the header of the log display.
     */
    private JComponent createHeader() {
        JPanel panel = new JPanel();
        panel.setBackground(DisplayNodeUtils.getNodeFillColor());
        panel.setLayout(new BorderLayout());

        String path = TetradLogger.getInstance().getLatestFilePath();

        JLabel label = new JLabel(path == null ? "Logging to console (select Setup... from Logging menu)" : "  Logging to " + path);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBackground(DisplayNodeUtils.getNodeFillColor());
        label.setForeground(Color.WHITE);
        label.setOpaque(false);
        label.setBorder(new EmptyBorder(1, 2, 1, 2));

        Box b = Box.createHorizontalBox();

        b.add(label);
        b.add(Box.createHorizontalGlue());
        panel.add(b, BorderLayout.CENTER);

        return panel;
    }

    //========================= Private Methods =================================//


}



