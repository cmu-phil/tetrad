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
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the functionality of SessionSupport. SessionSupport has to be able to
 * add and remove listeners, construct SessionEvents, and fire the SessionEvents
 * correctly.
 *
 * @author Joseph Ramsey
 */
public class TestSessionSupport {

    /**
     * The session support object being tested.
     */
    private SessionSupport sessionSupport;

    /**
     * A Session object to facilitate testing.
     */
    private Session session;

    /**
     * A session node to facilitate testing.
     */
    private SessionNode node1;

    /**
     * A session node to facilitate testing.
     */
    private SessionNode node2;

    /**
     * A flag to help test whether events are correctly received.
     */
    private boolean event1Received;

    /**
     * A flag to help test whether events are correctly received.
     */
    private boolean event2Received;

    /**
     * Sets up the session support object to be tested.
     */
    public void setUp() {
        node1 = new SessionNode(Type1.class);
        node2 = new SessionNode(Type2.class);
        session = new Session("Test");
        sessionSupport = new SessionSupport(session);
    }

    /**
     * Tests whether listeners are correctly added and removed.
     */
    @Test
    public void testSingleListener() {
        this.setUp();

        SessionListener l1 = new SessionListener() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.NODE_ADDED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getNode());
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a node is removed.
             */
            public void nodeRemoved(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.NODE_REMOVED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getNode());
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when an edge is added.
             */
            public void parentAdded(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.PARENT_ADDED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getParent());
                assertTrue(TestSessionSupport.this.getNode2() == event.getChild());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when an edge is removed.
             */
            public void parentRemoved(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.PARENT_REMOVED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getParent());
                assertTrue(TestSessionSupport.this.getNode2() == event.getChild());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a model is created for a node.
             */
            public void modelCreated(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_CREATED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getNode());
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a model is destroyed for a node.
             */
            public void modelDestroyed(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_DESTROYED);
                assertTrue(TestSessionSupport.this.getNode1() == event.getNode());
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when createModel() is called but the model
             * type is ambiguous.
             */
            public void modelUnclear(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_UNCLEAR);
                assertTrue(TestSessionSupport.this.getNode1() == event.getNode());
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void executionStarted(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.EXECUTION_STARTED);
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void repetitionChanged(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.REPETITION_CHANGED);
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void addingEdge(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.ADDING_EDGE);
                assertNull(event.getParent());
                TestSessionSupport.this.setEvent1Received(true);
            }
        };

        // Add the listener and make sure all the events get through.
        sessionSupport.addSessionListener(l1);

        // Test node added event.
        this.setEvent1Received(false);
        sessionSupport.fireNodeAdded(node1);
        assertTrue(this.isEvent1Received());

        // Test node removed event.
        this.setEvent1Received(false);
        sessionSupport.fireNodeRemoved(node1);
        assertTrue(this.isEvent1Received());

        // Test parent added event.
        this.setEvent1Received(false);
        sessionSupport.fireParentAdded(node1, node2);
        assertTrue(this.isEvent1Received());

        // Test parent removed event.
        this.setEvent1Received(false);
        sessionSupport.fireParentRemoved(node1, node2);
        assertTrue(this.isEvent1Received());

        // Test model created event.
        this.setEvent1Received(false);
        sessionSupport.fireModelCreated(node1);
        assertTrue(this.isEvent1Received());

        // Test model destroyed event.
        this.setEvent1Received(false);
        sessionSupport.fireModelDestroyed(node1);
        assertTrue(this.isEvent1Received());

        // Remove the listener and make sure it's removed.
        sessionSupport.removeSessionListener(l1);
        this.setEvent1Received(false);
        sessionSupport.fireNodeAdded(node1);
        assertTrue(!this.isEvent1Received());
    }

    /**
     * Tests whether multiple listeners will all receive events that are sent.
     */
    @Test
    public void testMultipleListeners() {
        this.setUp();

        SessionListener l1 = new SessionAdapter() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                TestSessionSupport.this.setEvent1Received(true);
            }
        };
        SessionListener l2 = new SessionAdapter() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                TestSessionSupport.this.setEvent2Received(true);
            }
        };

        //Add both listeners.
        sessionSupport.addSessionListener(l1);
        sessionSupport.addSessionListener(l2);
        this.setEvent1Received(false);
        this.setEvent2Received(false);
        sessionSupport.fireNodeAdded(node1);
        assertTrue(this.isEvent1Received());
        assertTrue(this.isEvent2Received());

        // Remove one of the listeners.
        //Add both listeners.
        sessionSupport.removeSessionListener(l1);
        this.setEvent1Received(false);
        this.setEvent2Received(false);
        sessionSupport.fireNodeAdded(node1);
        assertTrue(!this.isEvent1Received());
        assertTrue(this.isEvent2Received());
    }

    /**
     * Helps to determine whether an event was received.
     */
    public boolean isEvent1Received() {
        return event1Received;
    }

    /**
     * Set in the test adapater to help determine whether an event was
     * received.
     */
    public void setEvent1Received(boolean event1Received) {
        this.event1Received = event1Received;
    }

    /**
     * Helps to determine whether an event was received.
     */
    public boolean isEvent2Received() {
        return event2Received;
    }

    /**
     * Set in the test adapater to help determine whether an event was
     * received.
     */
    public void setEvent2Received(boolean event2Received) {
        this.event2Received = event2Received;
    }

    /**
     * Returns the test node, node1.
     */
    public SessionNode getNode1() {
        return node1;
    }

    /**
     * Returns the test node, node2.
     */
    public SessionNode getNode2() {
        return node2;
    }
}





