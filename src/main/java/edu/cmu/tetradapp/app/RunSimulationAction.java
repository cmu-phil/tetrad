///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.session.SessionNode;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Set;

/**
 * Sets up parameters for logging.
 *
 * @author Joseph Ramsey
 */
class RunSimulationAction extends AbstractAction {


    private SessionEditorNode sessionEditorNode;

    /**
     * Constructs a new action to open sessions.
     */
    public RunSimulationAction(SessionEditorNode sessionEditorNode) {
        super("Run Simulation...");
        this.sessionEditorNode = sessionEditorNode;
    }


    public void actionPerformed(ActionEvent e) {
        executeNode();
    }


    private void executeNode() {
        Set<SessionNode> children = sessionEditorNode.getChildren();
        boolean noEmptyChildren = true;

        for (SessionNode child : children) {
            if (child.getModel() == null) {
                noEmptyChildren = false;
                break;
            }
        }

        Component centeringComp = sessionEditorNode;

//        if (!noEmptyChildren) {
//            JOptionPane.showMessageDialog(centeringComp, "Nothing to run.");
//            return;
//        }

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
            executeSessionNode(sessionEditorNode.getSessionNode(), true);
        }
    }


    private SessionEditorWorkbench getWorkbench() {
        Class c = SessionEditorWorkbench.class;
        Container container = SwingUtilities.getAncestorOfClass(c, sessionEditorNode);
        return (SessionEditorWorkbench) container;
    }


    private void executeSessionNode(final SessionNode sessionNode, final boolean overwrite) {
        Window owner = (Window) sessionEditorNode.getTopLevelAncestor();

        new WatchedProcess(owner) {
            public void watch() {
                SessionEditorWorkbench workbench = getWorkbench();

//                {
////                    int ret = JOptionPane.showConfirmDialog(sessionEditorNode, "Start a new log?");
////
////                    if (ret == JOptionPane.YES_OPTION) {
//                        try {
//                            TetradLogger.getInstance().setNextOutputStream();
//                        } catch (IllegalStateException e) {
////                    TetradLogger.getInstance().removeNextOutputStream();
//                            e.printStackTrace();
//                            return;
//                        }
////                    }
//                }

                workbench.getSimulationStudy().execute(sessionNode, overwrite);

//                {
//                    // Start another log. Only stop when the log display is closed.
////                    int ret = JOptionPane.showConfirmDialog(sessionEditorNode, "Finish this log and start another one?");
////
////                    if (ret == JOptionPane.YES_OPTION) {
//                        try {
//                            TetradLogger.getInstance().setNextOutputStream();
//                        } catch (IllegalStateException e) {
////                    TetradLogger.getInstance().removeNextOutputStream();
//                            e.printStackTrace();
//                            return;
//                        }
////                    }
//                }
            }
        };
    }

}



