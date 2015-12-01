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

import java.util.ArrayList;
import java.util.List;

/**
 * Handles firing of SessionSupport events to listeners.
 *
 * @author Joseph Ramsey
 */
public class SessionSupport {

    /**
     * The source of the events.
     */
    private Object source;

    /**
     * The list of session listener--for instance, GUI editors displaying this
     * session and allowing it to be edited.
     */
    private List sessionListeners = new ArrayList();

    /**
     * Constructs a new session support object for the given source object. The
     * source object will be stamped on all fired events.
     */
    public SessionSupport(Object source) {
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null.");
        }

        this.source = source;
    }

    /**
     * Adds a listener for SessionEvents.
     */
    public void addSessionListener(SessionListener l) {
        if (!(this.sessionListeners.contains(l))) {
            this.sessionListeners.add(l);
        }
    }

    /**
     * Removes a listener for SessionEvents.
     */
    public void removeSessionListener(SessionListener l) {
        if (this.sessionListeners.contains(l)) {
            this.sessionListeners.remove(l);
        }
    }

    /**
     * Fires an event indicating that a session node has been added to the
     * session.
     */
    public void fireNodeAdded(SessionNode node) {
        SessionEvent event =
                new SessionEvent(this.source, node, SessionEvent.NODE_ADDED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that a sesison node has been removed from the
     * session.
     */
    public void fireNodeRemoved(SessionNode node) {
        SessionEvent event =
                new SessionEvent(this.source, node, SessionEvent.NODE_REMOVED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that a parent has been added from the given
     * parent to the given child session node in the session.
     */
    public void fireParentAdded(SessionNode parent, SessionNode child) {
        SessionEvent event = new SessionEvent(this.source, parent, child,
                SessionEvent.PARENT_ADDED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that a parent has been removed from the given
     * parent to the given child session node in the session.
     */
    public void fireParentRemoved(SessionNode parent, SessionNode child) {
        SessionEvent event = new SessionEvent(this.source, parent, child,
                SessionEvent.PARENT_REMOVED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that a new model has been created for the given
     * session node in the session.
     */
    public void fireModelCreated(SessionNode node) {
        SessionEvent event =
                new SessionEvent(this.source, node, SessionEvent.MODEL_CREATED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that the model for the given session node in
     * the session has been destroyed.
     */
    public void fireModelDestroyed(SessionNode node) {
        SessionEvent event = new SessionEvent(this.source, node,
                SessionEvent.MODEL_DESTROYED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that the model for the given session node in
     * the session has been destroyed.
     */
    public void fireModelUnclear(SessionNode node) {
        SessionEvent event =
                new SessionEvent(this.source, node, SessionEvent.MODEL_UNCLEAR);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that the model for the given session node in
     * the session has been destroyed.
     */
    public void fireRepetitionChanged(SessionNode node) {
        SessionEvent event = new SessionEvent(this.source, node,
                SessionEvent.REPETITION_CHANGED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that an new execution of a node has begun.
     */
    public void fireExecutionStarted() {
        SessionEvent event =
                new SessionEvent(this.source, SessionEvent.EXECUTION_STARTED);

        fireSessionEvent(event);
    }

    /**
     * Fires an event indicating that an edge is about to be added.
     */
    public void fireAddingEdge() {
        SessionEvent event =
                new SessionEvent(this.source, SessionEvent.ADDING_EDGE);

        fireSessionEvent(event);
    }

    /**
     * Fires a session event. Calls the correct method on the listener for the
     * type of session event it is. All event fired with this session support
     * are stamped with the source object of this session support.
     */
    public void fireSessionEvent(SessionEvent event) {
        fireSessionEvent(event, true);
    }

    /**
     * Fires a session event. Calls the correct method on the listener for the
     * type of session event it is. Events are restamped with the source object
     * of this session support if <code>restamp</code> is true.
     *
     * @param event   the session event to fire.
     * @param restamp true iff the source of this event should be set to the
     *                source of this SessionSupport object.
     */
    public void fireSessionEvent(SessionEvent event, boolean restamp) {
        if (restamp && event.getSource() != this.source) {
            event = new SessionEvent(this.source, event);
        }

        for (int i = 0; i < sessionListeners.size(); i++) {
            SessionListener l = (SessionListener) sessionListeners.get(i);

            switch (event.getType()) {
                case SessionEvent.NODE_ADDED:
                    l.nodeAdded(event);
                    break;

                case SessionEvent.NODE_REMOVED:
                    l.nodeRemoved(event);
                    break;

                case SessionEvent.PARENT_ADDED:
                    l.parentAdded(event);
                    break;

                case SessionEvent.PARENT_REMOVED:
                    l.parentRemoved(event);
                    break;

                case SessionEvent.MODEL_CREATED:
                    l.modelCreated(event);
                    break;

                case SessionEvent.MODEL_DESTROYED:
                    l.modelDestroyed(event);
                    break;

                case SessionEvent.MODEL_UNCLEAR:
                    l.modelUnclear(event);
                    break;

                case SessionEvent.EXECUTION_STARTED:
                    l.executionStarted(event);
                    break;

                case SessionEvent.REPETITION_CHANGED:
                    l.repetitionChanged(event);
                    break;

                case SessionEvent.ADDING_EDGE:
                    l.addingEdge(event);
                    break;

                default:
                    throw new IllegalStateException(
                            "No such state: " + event.getType());
            }
        }
    }
}





