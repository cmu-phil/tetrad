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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.session.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the basic function of the SessionEvent class--that is, whether events
 * can be constructed properly and whether the information can be extracted from
 * them properly.
 *
 * @author Joseph Ramsey
 */
public class TestSessionEvent extends TestCase {

    /**
     * The session for the events being tested.
     */
    private Session session;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSessionEvent(String name) {
        super(name);
    }

    public void setUp() {
        this.session = new Session("Test");
    }

    /**
     * Tests whether an ADD_NODE event can be constructed properly.
     */
    public void testAddNodeEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(session, node, SessionEvent.NODE_ADDED);

        assertTrue(node == event.getNode());
        assertEquals(SessionEvent.NODE_ADDED, event.getType());
    }

    /**
     * Tests whether an NODE_REMOVED event can be constructed properly.
     */
    public void testRemoveNodeEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(session, node, SessionEvent.NODE_REMOVED);

        assertTrue(node == event.getNode());
        assertEquals(SessionEvent.NODE_REMOVED, event.getType());
    }

    /**
     * Tests whether an MODEL_CREATED event can be constructed properly.
     */
    public void testModelCreatedEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(session, node, SessionEvent.MODEL_CREATED);

        assertTrue(node == event.getNode());
        assertEquals(SessionEvent.MODEL_CREATED, event.getType());
    }

    /**
     * Tests whether an MODEL_DESTROYED event can be constructed properly.
     */
    public void testModelDestroyedEvent() {
        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(session, node, SessionEvent.MODEL_DESTROYED);

        assertTrue(node == event.getNode());
        assertEquals(SessionEvent.MODEL_DESTROYED, event.getType());
    }

    /**
     * Tests whether an MODEL_UNCLEAR event can be constructed properly.
     */
    public void testModelUnclearEvent() {
        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(session, node, SessionEvent.MODEL_UNCLEAR);

        assertTrue(node == event.getNode());
        assertEquals(SessionEvent.MODEL_UNCLEAR, event.getType());
    }

    /**
     * Tests whether a PARENT_ADDED event can be constructed properly.
     */
    public void testParentAddedEvent() {
        SessionNode child = new SessionNode(Type1.class);
        SessionNode parent = new SessionNode(Type2.class);
        SessionEvent event = new SessionEvent(session, parent, child,
                SessionEvent.PARENT_ADDED);

        assertTrue(child == event.getChild());
        assertTrue(parent == event.getParent());
        assertEquals(SessionEvent.PARENT_ADDED, event.getType());
    }

    /**
     * Tests whether a PARENT_REMOVED event can be constructed properly.
     */
    public void testParentRemovedEvent() {
        SessionNode child = new SessionNode(Type1.class);
        SessionNode parent = new SessionNode(Type2.class);
        SessionEvent event = new SessionEvent(session, parent, child,
                SessionEvent.PARENT_REMOVED);

        assertTrue(child == event.getChild());
        assertTrue(parent == event.getParent());
        assertEquals(SessionEvent.PARENT_REMOVED, event.getType());
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSessionEvent.class);
    }
}





