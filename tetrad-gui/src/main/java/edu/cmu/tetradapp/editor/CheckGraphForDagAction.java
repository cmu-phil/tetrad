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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * This class represents an action that checks if a graph is a Directed Acyclic Graph (DAG).
 * It extends the AbstractAction class.
 */
public class CheckGraphForDagAction extends AbstractAction {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Highlights all latent variables in the given display graph.
     *
     * @param workbench the given workbench.
     */
    public CheckGraphForDagAction(GraphWorkbench workbench) {
        super("Check to see if Graph is a DAG");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * This method checks if the graph is a Directed Acyclic Graph (DAG).
     *
     * @param e the action event that triggered the method
     */
    public void actionPerformed(ActionEvent e) {
        Graph graph = workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(workbench, "No graph to check for DAGness.");
            return;
        }

        if (graph.paths().isLegalDag()) {
            JOptionPane.showMessageDialog(workbench, "Graph is a legal DAG.");
        } else {
            JOptionPane.showMessageDialog(workbench, "Graph is not a legal DAG.");
        }
    }
}



