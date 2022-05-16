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

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class ContributorsAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public ContributorsAction() {
        super("Contributors");
    }

    /**
     * Closes the frontmost session of this action's desktop.
     */
    public void actionPerformed(ActionEvent e) {
        String msg = "This project has been worked on for many years under the generous " +
                "auspices of the Philosophy Department at Carnegie Mellon University, under " +
                "the direction of Clark Glymour (Philosophy, CMU), Peter Spirtes (Philosophy, " +
                "CMU) and Richard Scheines (Philosophy, CMU, now Dean of the College of Humanities " +
                "and Social Sciences at CMU). The lead developer has been Joseph Ramsey (Philosophy, " +
                "CMU). Recent work has been done under the direction of Greg Cooper (Bioinformatics, " +
                "University of Pittsburgh) in conjunction with a grant from NSF establishing the " +
                "Center for Causal Discovery, with additional advice from Kun Zhang (Philosophy, " +
                "CMU), The team under the NSF grant consisted of J Espino, Kevin Bui, Zhou Yuan, " +
                "Kong Wongchakprasitti, and Harry Hochheiser. Additional work has been done by Bryan " +
                "Andrews, Ruben Sanchez, Fattaneh Jabbari, Ricardo Silva, Dan Malinsky, Erich Kummerfeld, " +
                "Biwei Huang, Juan Miguel Ogarrio, David Danks, Kevin Kelly, Eric Strobl, Shyam Visweswaran, " +
                "Shuyan Wang, Madelyn Glymour, Frank Wimberly, Matt Easterday, and Tyler Gibson.";

//        int index = msg.indexOf("Grateful");

        JTextArea textArea = new JTextArea(msg);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(400, 250));
//        textArea.setCaretPosition(index);

        Box b = Box.createVerticalBox();
        b.add(scroll);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
                "Contributors", JOptionPane.PLAIN_MESSAGE);
    }
}



