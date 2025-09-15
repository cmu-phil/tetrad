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

package edu.cmu.tetradapp.test;

import edu.cmu.tetradapp.session.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the functionality of SessionSupport. SessionSupport has to be able to add and remove listeners, construct
 * SessionEvents, and fire the SessionEvents correctly.
 *
 * @author josephramsey
 */
public class TestSessionSupport {

    /**
     * The session support object being tested.
     */
    private SessionSupport sessionSupport;

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
        this.node1 = new SessionNode(Type1.class);
        this.node2 = new SessionNode(Type2.class);
        /**
         * A Session object to facilitate testing.
         */
        Session session = new Session("Test");
        this.sessionSupport = new SessionSupport(session);
    }

    /**
     * Tests whether listeners are correctly added and removed.
     */
    @Test
    public void testSingleListener() {
        setUp();

        SessionListener l1 = new SessionListener() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {

                assertEquals(SessionEvent.NODE_ADDED, event.getType());
                assertSame(getNode1(), event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is removed.
             */
            public void nodeRemoved(SessionEvent event) {

                assertEquals(SessionEvent.NODE_REMOVED, event.getType());
                assertSame(getNode1(), event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when an edge is added.
             */
            public void parentAdded(SessionEvent event) {

                assertEquals(SessionEvent.PARENT_ADDED, event.getType());
                assertSame(getNode1(), event.getParent());
                assertSame(getNode2(), event.getChild());
                setEvent1Received(true);
            }

            /**
             * This method is called when an edge is removed.
             */
            public void parentRemoved(SessionEvent event) {

                assertEquals(SessionEvent.PARENT_REMOVED, event.getType());
                assertSame(getNode1(), event.getParent());
                assertSame(getNode2(), event.getChild());
                setEvent1Received(true);
            }

            /**
             * This method is called when a model is created for a node.
             */
            public void modelCreated(SessionEvent event) {

                assertEquals(SessionEvent.MODEL_CREATED, event.getType());
                assertSame(getNode1(), event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a model is destroyed for a node.
             */
            public void modelDestroyed(SessionEvent event) {

                assertEquals(SessionEvent.MODEL_DESTROYED, event.getType());
                assertSame(getNode1(), event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when createModel() is called but the model
             * type is ambiguous.
             */
            public void modelUnclear(SessionEvent event) {

                assertEquals(SessionEvent.MODEL_UNCLEAR, event.getType());
                assertSame(getNode1(), event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void executionStarted(SessionEvent event) {
                assertEquals(SessionEvent.EXECUTION_STARTED, event.getType());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void repetitionChanged(SessionEvent event) {
                assertEquals(SessionEvent.REPETITION_CHANGED, event.getType());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void addingEdge(SessionEvent event) {
                assertEquals(SessionEvent.ADDING_EDGE, event.getType());
                assertNull(event.getParent());
                setEvent1Received(true);
            }
        };

        // Add the listener and make sure all the events get through.
        this.sessionSupport.addSessionListener(l1);

        // Test node added event.
        setEvent1Received(false);
        this.sessionSupport.fireNodeAdded(this.node1);
        assertTrue(isEvent1Received());

        // Test node removed event.
        setEvent1Received(false);
        this.sessionSupport.fireNodeRemoved(this.node1);
        assertTrue(isEvent1Received());

        // Test parent added event.
        setEvent1Received(false);
        this.sessionSupport.fireParentAdded(this.node1, this.node2);
        assertTrue(isEvent1Received());

        // Test parent removed event.
        setEvent1Received(false);
        this.sessionSupport.fireParentRemoved(this.node1, this.node2);
        assertTrue(isEvent1Received());

        // Test model created event.
        setEvent1Received(false);
        this.sessionSupport.fireModelCreated(this.node1);
        assertTrue(isEvent1Received());

        // Test model destroyed event.
        setEvent1Received(false);
        this.sessionSupport.fireModelDestroyed(this.node1);
        assertTrue(isEvent1Received());

        // Remove the listener and make sure it's removed.
        this.sessionSupport.removeSessionListener(l1);
        setEvent1Received(false);
        this.sessionSupport.fireNodeAdded(this.node1);
        assertFalse(isEvent1Received());
    }

    /**
     * Tests whether multiple listeners will all receive events that are sent.
     */
    @Test
    public void testMultipleListeners() {
        setUp();

        SessionListener l1 = new SessionAdapter() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                setEvent1Received(true);
            }
        };
        SessionListener l2 = new SessionAdapter() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                setEvent2Received(true);
            }
        };

        //Add both listeners.
        this.sessionSupport.addSessionListener(l1);
        this.sessionSupport.addSessionListener(l2);
        setEvent1Received(false);
        setEvent2Received(false);
        this.sessionSupport.fireNodeAdded(this.node1);
        assertTrue(isEvent1Received());
        assertTrue(isEvent2Received());

        // Remove one of the listeners.
        //Add both listeners.
        this.sessionSupport.removeSessionListener(l1);
        setEvent1Received(false);
        setEvent2Received(false);
        this.sessionSupport.fireNodeAdded(this.node1);
        assertFalse(isEvent1Received());
        assertTrue(isEvent2Received());
    }

    /**
     * Checks if event 1 has been received.
     *
     * @return true if event 1 has been received, false otherwise
     */
    public boolean isEvent1Received() {
        return this.event1Received;
    }

    /**
     * Sets the flag indicating whether event 1 has been received.
     *
     * @param event1Received the flag value to set
     */
    public void setEvent1Received(boolean event1Received) {
        this.event1Received = event1Received;
    }

    /**
     * Returns whether event 2 has been received.
     *
     * @return {@code true} if event 2 has been received; {@code false} otherwise
     */
    public boolean isEvent2Received() {
        return this.event2Received;
    }

    /**
     * Sets the flag indicating whether event 2 has been received.
     *
     * @param event2Received the flag value to set
     */
    public void setEvent2Received(boolean event2Received) {
        this.event2Received = event2Received;
    }

    /**
     * Retrieves the node1 object.
     *
     * @return The node1 object.
     */
    public SessionNode getNode1() {
        return this.node1;
    }

    /**
     * Retrieves the node2 object.
     *
     * @return The node2 object.
     */
    public SessionNode getNode2() {
        return this.node2;
    }
}






