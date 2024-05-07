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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetradapp.util.GraphUtils;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * CheckGraphForMpdagAction is an action class that checks if a given graph is a legal PAG (Mixed Ancesgral Graph) and
 * displays a message to indicate the result.
 */
public class CheckGraphForPagAction extends AbstractAction {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Highlights all latent variables in the given display graph.
     *
     * @param workbench the given workbench.
     */
    public CheckGraphForPagAction(GraphWorkbench workbench) {
        super("Check to see if Graph is a PAG");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    private volatile GraphSearchUtils.LegalPagRet legalPag = null;

    /**
     * This method is used to perform an action when an event is triggered, specifically when the user clicks on a
     * button or menu item associated with it. It checks if a graph is a legal DAG (Partial Ancestral Graph).
     *
     * @param e The ActionEvent object that represents the event generated by the user action.
     */
    public void actionPerformed(ActionEvent e) {
        Graph graph = workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(GraphUtils.getContainingScrollPane(workbench), "No graph to check for PAGness.");
            return;
        }

        class MyWatchedProcess extends WatchedProcess {
            @Override
            public void watch() {
                Graph _graph = new EdgeListGraph(workbench.getGraph());
                legalPag = GraphSearchUtils.isLegalPag(_graph);
            }
        }

        new MyWatchedProcess();

        while (legalPag == null) {
            try {
                Thread.sleep(100); // Sleep a bit to prevent tight loop
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
            }
        }

        String reason = GraphUtils.breakDown(legalPag.getReason(), 60);

        if (!legalPag.isLegalPag()) {
            JOptionPane.showMessageDialog(GraphUtils.getContainingScrollPane(workbench),
                    "This is not a legal PAG--one reason is as follows:" +
                    "\n\n" + reason + ".",
                    "Legal PAG check",
                    JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(GraphUtils.getContainingScrollPane(workbench), reason);
        }
    }

}



