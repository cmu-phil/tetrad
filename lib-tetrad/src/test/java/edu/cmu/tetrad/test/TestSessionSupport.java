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
 * Tests the functionality of SessionSupport. SessionSupport has to be able to
 * add and remove listeners, construct SessionEvents, and fire the SessionEvents
 * correctly.
 *
 * @author Joseph Ramsey
 */
public class TestSessionSupport extends TestCase {

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
     * Standard constructor for JUnit test cases.
     */
    public TestSessionSupport(String name) {
        super(name);
    }

    /**
     * Sets up the session support object to be tested.
     */
    public void setUp() {
        this.node1 = new SessionNode(Type1.class);
        this.node2 = new SessionNode(Type2.class);
        this.session = new Session("Test");
        this.sessionSupport = new SessionSupport(session);
    }

    /**
     * Tests whether listeners are correctly added and removed.
     */
    public void testSingleListener() {

        SessionListener l1 = new SessionListener() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.NODE_ADDED);
                assertTrue(getNode1() == event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is removed.
             */
            public void nodeRemoved(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.NODE_REMOVED);
                assertTrue(getNode1() == event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when an edge is added.
             */
            public void parentAdded(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.PARENT_ADDED);
                assertTrue(getNode1() == event.getParent());
                assertTrue(getNode2() == event.getChild());
                setEvent1Received(true);
            }

            /**
             * This method is called when an edge is removed.
             */
            public void parentRemoved(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.PARENT_REMOVED);
                assertTrue(getNode1() == event.getParent());
                assertTrue(getNode2() == event.getChild());
                setEvent1Received(true);
            }

            /**
             * This method is called when a model is created for a node.
             */
            public void modelCreated(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_CREATED);
                assertTrue(getNode1() == event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a model is destroyed for a node.
             */
            public void modelDestroyed(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_DESTROYED);
                assertTrue(getNode1() == event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when createModel() is called but the model
             * type is ambiguous.
             */
            public void modelUnclear(SessionEvent event) {

                assertTrue(event.getType() == SessionEvent.MODEL_UNCLEAR);
                assertTrue(getNode1() == event.getNode());
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void executionStarted(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.EXECUTION_STARTED);
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void repetitionChanged(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.REPETITION_CHANGED);
                assertNull(event.getParent());
                setEvent1Received(true);
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void addingEdge(SessionEvent event) {
                assertTrue(event.getType() == SessionEvent.ADDING_EDGE);
                assertNull(event.getParent());
                setEvent1Received(true);
            }
        };

        // Add the listener and make sure all the events get through.
        this.sessionSupport.addSessionListener(l1);

        // Test node added event.
        setEvent1Received(false);
        this.sessionSupport.fireNodeAdded(node1);
        assertTrue(isEvent1Received());

        // Test node removed event.
        setEvent1Received(false);
        this.sessionSupport.fireNodeRemoved(node1);
        assertTrue(isEvent1Received());

        // Test parent added event.
        setEvent1Received(false);
        this.sessionSupport.fireParentAdded(node1, node2);
        assertTrue(isEvent1Received());

        // Test parent removed event.
        setEvent1Received(false);
        this.sessionSupport.fireParentRemoved(node1, node2);
        assertTrue(isEvent1Received());

        // Test model created event.
        setEvent1Received(false);
        this.sessionSupport.fireModelCreated(node1);
        assertTrue(isEvent1Received());

        // Test model destroyed event.
        setEvent1Received(false);
        this.sessionSupport.fireModelDestroyed(node1);
        assertTrue(isEvent1Received());

        // Remove the listener and make sure it's removed.
        this.sessionSupport.removeSessionListener(l1);
        setEvent1Received(false);
        this.sessionSupport.fireNodeAdded(node1);
        assertTrue(!isEvent1Received());

        // TODO: The modelUnclear method is not tested yet.
    }

    /**
     * Tests whether multiple listeners will all receive events that are sent.
     */
    public void testMultipleListeners() {

        SessionListener l1 = new SessionAdapter() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                System.out.println("HERE");
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
        this.sessionSupport.fireNodeAdded(node1);
        assertTrue(isEvent1Received());
        assertTrue(isEvent2Received());

        // Remove one of the listeners.
        //Add both listeners.
        this.sessionSupport.removeSessionListener(l1);
        setEvent1Received(false);
        setEvent2Received(false);
        this.sessionSupport.fireNodeAdded(node1);
        assertTrue(!isEvent1Received());
        assertTrue(isEvent2Received());
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
        return this.node1;
    }

    /**
     * Returns the test node, node2.
     */
    public SessionNode getNode2() {
        return this.node2;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSessionSupport.class);
    }
}





