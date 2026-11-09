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

import java.util.ArrayList;
import java.util.List;

/**
 * Handles firing of SessionSupport events to listeners.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SessionSupport {

    /**
     * The source of the events.
     */
    private final Object source;

    /**
     * The list of session listeners -- for instance, GUI editors displaying
     * this session and allowing it to be edited.
     */
    private final List<SessionListener> sessionListeners = new ArrayList<>();

    /**
     * Constructs a new session support object for the given source object. The
     * source object will be stamped on all fired events.
     *
     * @param source a {@link java.lang.Object} object; must not be null.
     */
    public SessionSupport(Object source) {
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null.");
        }

        this.source = source;
    }

    /**
     * Adds a listener for SessionEvents. Null listeners and duplicate
     * registrations are silently ignored.
     *
     * @param l a {@link SessionListener} object
     */
    public void addSessionListener(SessionListener l) {
        if (l == null) return;
        if (!this.sessionListeners.contains(l)) {
            this.sessionListeners.add(l);
        }
    }

    /**
     * Removes a listener for SessionEvents.
     *
     * @param l a {@link SessionListener} object
     */
    public void removeSessionListener(SessionListener l) {
        this.sessionListeners.remove(l);
    }

    /**
     * Fires an event indicating that a session node has been added to the
     * session.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireNodeAdded(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.NODE_ADDED));
    }

    /**
     * Fires an event indicating that a session node has been removed from the
     * session.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireNodeRemoved(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.NODE_REMOVED));
    }

    /**
     * Fires an event indicating that a parent has been added to the given
     * child session node in the session.
     *
     * @param parent a {@link SessionNode} object
     * @param child  a {@link SessionNode} object
     */
    public void fireParentAdded(SessionNode parent, SessionNode child) {
        fireSessionEvent(new SessionEvent(this.source, parent, child,
                SessionEvent.PARENT_ADDED));
    }

    /**
     * Fires an event indicating that a parent has been removed from the given
     * child session node in the session.
     *
     * @param parent a {@link SessionNode} object
     * @param child  a {@link SessionNode} object
     */
    public void fireParentRemoved(SessionNode parent, SessionNode child) {
        fireSessionEvent(new SessionEvent(this.source, parent, child,
                SessionEvent.PARENT_REMOVED));
    }

    /**
     * Fires an event indicating that a new model has been created for the
     * given session node.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireModelCreated(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.MODEL_CREATED));
    }

    /**
     * Fires an event indicating that the model for the given session node has
     * been destroyed.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireModelDestroyed(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.MODEL_DESTROYED));
    }

    /**
     * Fires an event indicating that the model class for the given session
     * node cannot be uniquely determined from the current parent configuration.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireModelUnclear(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.MODEL_UNCLEAR));
    }

    /**
     * Fires an event indicating that the repetition count for the given
     * session node has changed.
     *
     * @param node a {@link SessionNode} object
     */
    public void fireRepetitionChanged(SessionNode node) {
        fireSessionEvent(new SessionEvent(this.source, node,
                SessionEvent.REPETITION_CHANGED));
    }

    /**
     * Fires an event indicating that a new simulation execution has begun.
     */
    public void fireExecutionStarted() {
        fireSessionEvent(new SessionEvent(this.source,
                SessionEvent.EXECUTION_STARTED));
    }

    /**
     * Fires an event indicating that an edge is about to be added, giving
     * listeners the opportunity to veto it by calling
     * {@link SessionNode#setNextEdgeAddAllowed(boolean)}.
     */
    public void fireAddingEdge() {
        fireSessionEvent(new SessionEvent(this.source,
                SessionEvent.ADDING_EDGE));
    }

    /**
     * Fires a session event, restamping the source to this support's source
     * object. Delegates to {@link #fireSessionEvent(SessionEvent, boolean)}
     * with {@code restamp = true}.
     *
     * @param event a {@link SessionEvent} object
     */
    public void fireSessionEvent(SessionEvent event) {
        fireSessionEvent(event, true);
    }

    /**
     * Fires a session event to all currently registered listeners. Iterates
     * over a snapshot of the listener list so that listeners may safely add
     * or remove themselves in response to the event without causing a
     * {@link java.util.ConcurrentModificationException}.
     *
     * <p>If {@code restamp} is true and the event's source differs from this
     * support's source, a new event is created with this support's source
     * before dispatching.
     *
     * @param event   the session event to fire.
     * @param restamp if true, the event is restamped with this support's source.
     */
    public void fireSessionEvent(SessionEvent event, boolean restamp) {
        final SessionEvent toFire;
        if (restamp && event.getSource() != this.source) {
            toFire = new SessionEvent(this.source, event);
        } else {
            toFire = event;
        }

        // Iterate over a snapshot to allow listeners to add/remove themselves
        // during dispatch without ConcurrentModificationException.
        for (SessionListener l : new ArrayList<>(this.sessionListeners)) {
            switch (toFire.getType()) {
                case SessionEvent.NODE_ADDED:
                    l.nodeAdded(toFire);
                    break;
                case SessionEvent.NODE_REMOVED:
                    l.nodeRemoved(toFire);
                    break;
                case SessionEvent.PARENT_ADDED:
                    l.parentAdded(toFire);
                    break;
                case SessionEvent.PARENT_REMOVED:
                    l.parentRemoved(toFire);
                    break;
                case SessionEvent.MODEL_CREATED:
                    l.modelCreated(toFire);
                    break;
                case SessionEvent.MODEL_DESTROYED:
                    l.modelDestroyed(toFire);
                    break;
                case SessionEvent.MODEL_UNCLEAR:
                    l.modelUnclear(toFire);
                    break;
                case SessionEvent.EXECUTION_STARTED:
                    l.executionStarted(toFire);
                    break;
                case SessionEvent.REPETITION_CHANGED:
                    l.repetitionChanged(toFire);
                    break;
                case SessionEvent.ADDING_EDGE:
                    l.addingEdge(toFire);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unrecognized session event type: " + toFire.getType());
            }
        }
    }
}