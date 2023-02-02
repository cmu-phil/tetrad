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
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;

/**
 * Checks to see if a graph is a legal PAG.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class PagColorer extends JCheckBoxMenuItem {

    /**
     * Creates a new copy subsession action for the given desktop and
     * clipboard.
     */
    public PagColorer(GraphWorkbench workbench) {
        super("Add PAG Coloring if a Legal PAG");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        final GraphWorkbench _workbench = workbench;

        Graph graph = workbench.getGraph();

        setSelected(workbench.isDoPagColoring());

        addItemListener(e -> {
            workbench.setDoPagColoring(!workbench.isDoPagColoring());

            if (!workbench.isDoPagColoring()) {
                JOptionPane.showMessageDialog(workbench, "Legal PAG coloring is turned off.");
                workbench.setDoPagColoring(false);
            } else {
                SearchGraphUtils.LegalPagRet legalPag = SearchGraphUtils.isLegalPag(graph);

                String reason = legalPag.getReason() + ".";
                reason = breakDown(reason, 60);

                if (!legalPag.isLegalPag()) {
                    int ret = JOptionPane.showConfirmDialog(workbench, "This is not a legal PAG--one reason is as follows:" +
                                    "\n\n" + reason +
                                    "\n\nProceed anyway?",
                            "Legal PAG check", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ret == JOptionPane.YES_NO_OPTION) {
                        _workbench.setDoPagColoring(true);
                    }
                } else {
                    JOptionPane.showMessageDialog(workbench, reason);
//                    _workbench.setDoPagColoring(false);
                }
            }
        });

    }

    private String breakDown(String reason, int maxColumns) {
        StringBuilder buf1 = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();

        String[] tokens = reason.split(" ");

        for (String token : tokens) {
            if (buf1.length() + token.length() > maxColumns) {
                buf2.append(buf1);
                buf2.append("\n");
                buf1 = new StringBuilder();
                buf1.append(token);
            } else {
                buf1.append(" ").append(token);
            }
        }

        if (buf1.length() > 0) {
            buf2.append(buf1);
        }

        return buf2.toString();
    }
}



