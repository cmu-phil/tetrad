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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.DagSepsets;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * This class represents an action to run the final FCI (Fast Causal Inference) rules on a graph in a GraphWorkbench.
 * It extends the AbstractAction class and implements the ClipboardOwner interface.
 */
public class ApplyFinalFciRules extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Runs the final FCI (Fast Causal Inference) rules on a graph in a GraphWorkbench.
     * This action is triggered by clicking a button or selecting a menu option.
     *
     * @param workbench the GraphWorkbench instance containing the graph to run final FCI rules on.
     * @throws NullPointerException if workbench is null.
     */
    public ApplyFinalFciRules(GraphWorkbench workbench) {
        super("Apply Final FCI Rules");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event that triggered the action.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();
        Graph graph = this.workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(this.workbench, "No graph to apply final FCI rules to.");
            return;
        }

        Graph __g = new EdgeListGraph(graph);
        FciOrient finalFciRules = FciOrient.defaultConfiguration(new DagSepsets(__g), new Knowledge());
        finalFciRules.doFinalOrientation(__g);
        workbench.setGraph(__g);
    }

    /**
     * Called when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost ownership
     * @param contents the contents that were lost
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}



