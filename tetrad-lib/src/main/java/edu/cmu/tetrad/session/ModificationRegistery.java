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

package edu.cmu.tetrad.session;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allows editors launched for session nodes to register that relevant changes
 * have been made that will require models downstream to be reconstructed.
 *
 * @author William Taysom
 * @author Joseph Ramsey
 */
public final class ModificationRegistery {
    private static final Set<Object> EDITED_MODELS = new HashSet<Object>();
    private static final Map<JComponent, SessionNode> EDITORS_TO_SESSION_NODES = new HashMap<JComponent, SessionNode>();
    private static final Map<JComponent, PropertyChangeListener> EDITORS_TO_LISTENERS = new HashMap<JComponent, PropertyChangeListener>();

    /**
     * Registers an editor which could modify model.
     */
    public static void registerEditor(SessionNode sessionNode,
                                      JComponent editor) {
        if (editor == null) {
            throw new NullPointerException();
        }

        if (sessionNode == null) {
            throw new NullPointerException();
        }

        if (editor instanceof DelegatesEditing) {
            editor = ((DelegatesEditing) editor).getEditDelegate();
        }

        EDITORS_TO_SESSION_NODES.put(editor, sessionNode);

        PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    JComponent editor = (JComponent) evt.getSource();
                    Object sessionNode = EDITORS_TO_SESSION_NODES.get(editor);
                    if (sessionNode != null) {
                        EDITED_MODELS.add(sessionNode);
                    }
                }
            }
        };

        editor.addPropertyChangeListener(listener);
        EDITORS_TO_LISTENERS.put(editor, listener);
    }

    /**
     * @return true if an editor has registered that model has changed.
     */
    public static boolean modelHasChanged(SessionNode sessionNode) {
        return EDITED_MODELS.contains(sessionNode);
    }

    public static void unregisterEditor(JComponent editor) {
        if (editor instanceof DelegatesEditing) {
            editor = ((DelegatesEditing) editor).getEditDelegate();
        }

        EDITORS_TO_SESSION_NODES.remove(editor);
        PropertyChangeListener listener =
                EDITORS_TO_LISTENERS.get(editor);
        editor.removePropertyChangeListener(listener);
        EDITORS_TO_LISTENERS.remove(editor);
    }

    /**
     * Removes the given session node from the list of sessions nodes for which
     * changes have been made.
     */
    public static void unregisterSessionNode(SessionNode sessionNode) {
        EDITED_MODELS.remove(sessionNode);
    }
}




