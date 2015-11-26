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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.Session;
import edu.cmu.tetradapp.editor.SaveComponentImage;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a toolbar and workbench for editing Session's.
 *
 * @author Joseph Ramsey
 * @see SessionEditorWorkbench
 * @see SessionEditorToolbar
 */
public final class SessionEditor extends JComponent
        implements SessionEditorIndirectRef {

    /**
     * The toolbar displayed on the left-hand side of the session editor.
     */
    private SessionEditorToolbar toolbar;

    /**
     * The workbench displayed on the right-hand side of the session editor.
     */
    private SessionEditorWorkbench workbench;

    /**
     * Constucts a session editor with the given name.
     */
    public SessionEditor(String name) {
        this(name, null);
    }

    /**
     * Constructs a new session editor.  A session editor consists of a session
     * workbench and a session toolbar.
     *
     * @param name The name of the session.  This is used for saving out
     */
    public SessionEditor(String name, SessionEditorWorkbench workbench) {
        setName(name);

        if (workbench == null) {
            Session session = new Session(name);
            SessionWrapper wrapper = new SessionWrapper(session);
            workbench = new SessionEditorWorkbench(wrapper);
        }

        workbench.setName(name);
        setWorkbench(workbench);
        setToolbar(new SessionEditorToolbar(workbench));
        JScrollPane workbenchScroll = new JScrollPane(workbench);

        setLayout(new BorderLayout());
        add(workbenchScroll, BorderLayout.CENTER);
        add(getToolbar(), BorderLayout.WEST);

        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                String propertyName = e.getPropertyName();

                if ("name".equals(propertyName)) {
                    firePropertyChange("name", e.getOldValue(),
                            e.getNewValue());
                }
            }
        });
    }

    /**
     * @return the session workbench.
     */
    public SessionEditorWorkbench getSessionWorkbench() {
        return getWorkbench();
    }

    /**
     * @return a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     *
     * @return the set of selected model nodes.
     */
    public List getSelectedModelComponents() {
        List selectedComponents = getWorkbench().getSelectedComponents();
        List selectedModelComponents = new ArrayList();

        for (Object comp : selectedComponents) {
            if (comp instanceof SessionEditorNode) {
                SessionEditorNode editorNode = (SessionEditorNode) comp;
                Node modelNode = editorNode.getModelNode();
                selectedModelComponents.add(modelNode);
            }
            else if (comp instanceof SessionEditorEdge) {
                SessionEditorEdge editorEdge = (SessionEditorEdge) comp;
                Edge modelEdge = getWorkbench().getModelEdge(editorEdge);
                selectedModelComponents.add(modelEdge);
            }
        }

        return selectedModelComponents;
    }

    /**
     * Pastes a consistent list of model nodes into the workbench. Note that the
     * responsivity of the toolbar to events needs to be turned off during this
     * operation.
     * @param sessionElements the list of model nodes.
     * @param point the upper left corner of the first node.
     */
    public void pasteSubsession(List sessionElements, Point point) {
        getToolbar().setRespondingToEvents(false);
        getWorkbench().pasteSubsession(sessionElements, point);
        getToolbar().setRespondingToEvents(true);
    }

    /**
     * Sets the name of the session editor.
     */
    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        super.setName(name);
    }

    private SessionEditorToolbar getToolbar() {
        return toolbar;
    }

    private void setToolbar(SessionEditorToolbar toolbar) {
        this.toolbar = toolbar;
    }

    private SessionEditorWorkbench getWorkbench() {
        return workbench;
    }

    private void setWorkbench(SessionEditorWorkbench workbench) {
        this.workbench = workbench;
    }

    public void saveSessionImage() {
        Action action = new SaveComponentImage(workbench, "Save Session Image...");
        action.actionPerformed(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Save"));
    }

    public void firePropertyChange(String s, Object o, String name) {
        super.firePropertyChange(s, o, name);
    }
}





