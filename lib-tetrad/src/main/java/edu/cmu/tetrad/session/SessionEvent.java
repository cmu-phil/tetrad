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

import java.util.EventObject;

/**
 * Notifies a listener that some change has occurred in the session--a node has
 * been added or removed, an edge has been added or removed, a model has been
 * created or destroyed.
 *
 * @author Joseph Ramsey
 */
public class SessionEvent extends EventObject {
    public static final int NODE_ADDED = 0;
    public static final int NODE_REMOVED = 1;
    public static final int PARENT_ADDED = 2;
    public static final int PARENT_REMOVED = 3;
    public static final int MODEL_CREATED = 4;
    public static final int MODEL_DESTROYED = 5;
    public static final int MODEL_UNCLEAR = 6;
    public static final int EXECUTION_STARTED = 7;
    public static final int REPETITION_CHANGED = 8;
    public static final int ADDING_EDGE = 9;
    private SessionNode node;
    private SessionNode parent;
    private SessionNode child;
    private int type = -1;

    /**
     * Constructs an event where one session node is involved--session node
     * added or removed, model created or destroyed.
     */
    public SessionEvent(Object source, int type) {
        super(source);

        switch (type) {
            case EXECUTION_STARTED:
                this.type = type;
                break;

            case ADDING_EDGE:
                this.type = type;
                break;

            default :
                throw new IllegalArgumentException(
                        "Not the type of event that " +
                                "requires zero session nodes " +
                                "as arguments.");
        }
    }

    /**
     * Constructs an event where one session node is involved--session node
     * added or removed, model created or destroyed.
     */
    public SessionEvent(Object source, SessionNode node, int type) {
        super(source);

        if (node != null) {
            this.node = node;
        }
        else {
            throw new NullPointerException();
        }

        switch (type) {
            case NODE_ADDED:

                // Falls through!
            case NODE_REMOVED:

                // Falls through!
            case MODEL_CREATED:

                // Falls through!
            case MODEL_DESTROYED:

                // Falls through!
            case MODEL_UNCLEAR:

                // Falls through!
            case REPETITION_CHANGED:
                this.type = type;
                break;
                                                                           
            default :
                throw new IllegalArgumentException(
                        "Not the type of event that " +
                                "requires one session node " + "as argument.");
        }
    }

    /**
     * Constructs an event where two session nodes are involved--parent added or
     * removed.
     */
    public SessionEvent(Object source, SessionNode parent, SessionNode child,
            int type) {

        super(source);

        this.parent = parent;
        this.child = child;

        switch (type) {
            case PARENT_ADDED:

                // Falls through!
            case PARENT_REMOVED:
                this.type = type;
                break;

            default :
                throw new IllegalArgumentException(
                        "Not the type of event that " +
                                "requires two session nodes " +
                                "as arguments.");
        }
    }

    /**
     * Creates a new SessionEvent with the same information as the given event
     * but with a new source.
     */
    public SessionEvent(Object source, SessionEvent event) {

        super(source);

        this.node = event.getNode();
        this.parent = event.getParent();
        this.child = event.getChild();
        this.type = event.getType();
    }

    /**
     * @return the session node set, if this event was constructed using one
     * session node.
     */
    public SessionNode getNode() {
        return this.node;
    }

    /**
     * @return the parent session node set, if this is an event constructed
     * using two session nodes.
     */
    public SessionNode getParent() {
        return this.parent;
    }

    /**
     * @return the child session node set, if this is an event constructed using
     * two session nodes
     */
    public SessionNode getChild() {
        return this.child;
    }

    /**
     * @return the type of this event--one of NODE_ADDED, NODE_REMOVED,
     * PARENT_ADDED, PARENT_REMOVED, MODEL_CREATED, MODEL_DESTROYED,
     * MODEL_UNCLEAR.
     */
    public int getType() {
        return this.type;
    }
}





