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

package edu.cmu.tetradapp.session;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allows editors launched for session nodes to register that relevant changes have been made that will require models
 * downstream to be reconstructed.
 *
 * @author William Taysom
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ModificationRegistery {
    private static final Set<Object> EDITED_MODELS = new HashSet<>();
    private static final Map<JComponent, SessionNode> EDITORS_TO_SESSION_NODES = new HashMap<>();
    private static final Map<JComponent, PropertyChangeListener> EDITORS_TO_LISTENERS = new HashMap<>();

    /**
     * Registers an editor which could modify model.
     *
     * @param sessionNode a {@link SessionNode} object
     * @param editor      a {@link javax.swing.JComponent} object
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

        ModificationRegistery.EDITORS_TO_SESSION_NODES.put(editor, sessionNode);

        PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    JComponent editor = (JComponent) evt.getSource();
                    Object sessionNode = ModificationRegistery.EDITORS_TO_SESSION_NODES.get(editor);
                    if (sessionNode != null) {
                        ModificationRegistery.EDITED_MODELS.add(sessionNode);
                    }
                }
            }
        };

        editor.addPropertyChangeListener(listener);
        ModificationRegistery.EDITORS_TO_LISTENERS.put(editor, listener);
    }

    /**
     * <p>modelHasChanged.</p>
     *
     * @param sessionNode a {@link SessionNode} object
     * @return true if an editor has registered that model has changed.
     */
    public static boolean modelHasChanged(SessionNode sessionNode) {
        return ModificationRegistery.EDITED_MODELS.contains(sessionNode);
    }

    /**
     * <p>unregisterEditor.</p>
     *
     * @param editor a {@link javax.swing.JComponent} object
     */
    public static void unregisterEditor(JComponent editor) {
        if (editor instanceof DelegatesEditing) {
            editor = ((DelegatesEditing) editor).getEditDelegate();
        }

        ModificationRegistery.EDITORS_TO_SESSION_NODES.remove(editor);
        PropertyChangeListener listener =
                ModificationRegistery.EDITORS_TO_LISTENERS.get(editor);
        editor.removePropertyChangeListener(listener);
        ModificationRegistery.EDITORS_TO_LISTENERS.remove(editor);
    }

    /**
     * Removes the given session node from the list of sessions nodes for which changes have been made.
     *
     * @param sessionNode a {@link SessionNode} object
     */
    public static void unregisterSessionNode(SessionNode sessionNode) {
        ModificationRegistery.EDITED_MODELS.remove(sessionNode);
    }
}





