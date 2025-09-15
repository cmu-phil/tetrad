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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Set;

/**
 * Sets up parameters for logging.
 *
 * @author josephramsey
 */
class RunSimulationAction extends AbstractAction {


    private final SessionEditorNode sessionEditorNode;

    /**
     * Constructs a new action to open sessions.
     *
     * @param sessionEditorNode a {@link edu.cmu.tetradapp.app.SessionEditorNode} object
     */
    public RunSimulationAction(SessionEditorNode sessionEditorNode) {
        super("Run Simulation...");
        this.sessionEditorNode = sessionEditorNode;
    }


    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        executeNode();
    }


    private void executeNode() {
        Set<SessionNode> children = this.sessionEditorNode.getChildren();

        for (SessionNode child : children) {
            if (child.getModel() == null) {
                break;
            }
        }

        Component centeringComp = this.sessionEditorNode;

        Object[] options = {"Simulate", "Cancel"};

//        int selection = 0;

        int selection = JOptionPane.showOptionDialog(
                centeringComp,
                "Executing this node will erase any model for this node, " +
                "\nerase any models for any descendant nodes, and create " +
                "\nnew models with new values using the default" +
                "\nparameters for each node, which you may edit, and " +
                "\nthe repetition numbers for each node, which you " +
                "\nmay also edit. Continue?",
                "Warning", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]);

        if (selection == 0) {
            int ret = JOptionPane.showConfirmDialog(centeringComp,
                    "Please confirm once more.",
                    "Confirm",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (ret != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (selection == 0) {
            executeSessionNode(this.sessionEditorNode.getSessionNode());
        }
    }


    private SessionEditorWorkbench getWorkbench() {
        final Class<?> c = SessionEditorWorkbench.class;
        Container container = SwingUtilities.getAncestorOfClass(c, this.sessionEditorNode);
        return (SessionEditorWorkbench) container;
    }


    private void executeSessionNode(SessionNode sessionNode) {
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                SessionEditorWorkbench workbench = getWorkbench();

                workbench.getSimulationStudy().execute(sessionNode, true);

            }
        }

        new MyWatchedProcess();
    }

}




