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

import java.rmi.MarshalledObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the basic functionality of the Session.
 *
 * @author Joseph Ramsey
 */
public class TestSession {

    /**
     * The session being tested.
     */
    private Session session;

    /**
     * A string field to help debug events.
     */
    private String eventId;

    public void setUp() {
        this.session = new Session("Test");
    }

    /**
     * Tests whether session nodes can be added and removed from the session
     * correctly.
     */
    @Test
    public void testAddRemoveSessionNodes() {
        setUp();

        // Test adding/removing independent nodes.
        this.session.clearNodes();

        SessionNode node1 = new SessionNode(Type6.class);
        SessionNode node2 = new SessionNode(Type7.class);
        SessionNode node3 = new SessionNode(Type8.class);

        this.session.addNode(node1);
        this.session.addNode(node2);
        this.session.addNode(node3);
        assertEquals(3, this.session.getNodes().size());
        assertTrue(this.session.getNodes().contains(node1));
        assertTrue(this.session.getNodes().contains(node2));
        assertTrue(this.session.getNodes().contains(node3));
        this.session.removeNode(node2);
        assertEquals(2, this.session.getNodes().size());
        assertTrue(!this.session.getNodes().contains(node2));
        this.session.clearNodes();
        assertEquals(0, this.session.getNodes().size());
    }

    /**
     * Tests to make sure events are sent and received properly.
     */
    @Test
    public void testEvents() {
        setUp();
        boolean simulation = true;

        SessionListener listener = new SessionListener() {

            /**
             * This method is called when a node is added.
             */
            public void nodeAdded(SessionEvent event) {
                setEventId("nodeAdded");
            }

            /**
             * This method is called when a node is removed.
             */
            public void nodeRemoved(SessionEvent event) {
                setEventId("nodeRemoved");
            }

            /**
             * This method is called when a parent is added.
             */
            public void parentAdded(SessionEvent event) {
                setEventId("parentAdded");
            }

            /**
             * This method is called when a parent is removed.
             */
            public void parentRemoved(SessionEvent event) {
                setEventId("parentRemoved");
            }

            /**
             * This method is called when a model is created for a node.
             */
            public void modelCreated(SessionEvent event) {
                setEventId("modelCreated");
            }

            /**
             * This method is called when a model is destroyed for a node.
             */
            public void modelDestroyed(SessionEvent event) {
                setEventId("modelDestroyed");
            }

            /**
             * This method is called when a model is destroyed for a node.
             */
            public void modelUnclear(SessionEvent event) {
                setEventId("modelUnclear");
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void executionStarted(SessionEvent event) {
                setEventId("executionBegun");
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void repetitionChanged(SessionEvent event) {
                setEventId("repetitionChanged");
            }

            /**
             * This method is called when a node is executed manually.
             */
            public void addingEdge(SessionEvent event) {
                setEventId("addingEdge");
            }
        };

        // List to the session.
        this.session.addSessionListener(listener);

        // Test nodeAdded, nodeRemoved.
        try {
            setEventId(null);

            SessionNode node1 = new SessionNode(Type6.class);

            this.session.addNode(node1);
            assertEquals("nodeAdded", getEventId());

            SessionNode node2 = new SessionNode(Type7.class);
            SessionNode node3 = new SessionNode(Type8.class);

            this.session.addNode(node2);
            this.session.addNode(node3);
            setEventId(null);
            node1.addParent(node2);
            assertEquals("parentAdded", getEventId());
            setEventId(null);
            node2.createModel(Type7.class, simulation);
            assertEquals("modelCreated", getEventId());
            setEventId(null);
            node2.destroyModel();
            assertEquals("modelDestroyed", getEventId());
            setEventId(null);
            node1.removeParent(node2);
            assertEquals("parentRemoved", getEventId());
            setEventId(null);
            this.session.removeNode(node2);
            assertEquals("nodeRemoved", getEventId());
        }
        catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private void setEventId(String eventId) {
        this.eventId = eventId;
    }

    private String getEventId() {
        return this.eventId;
    }

    /**
     * Tests whether the session can be serialized and reconstructed correctly.
     * This only tests the skeletal form of the serialization--serialization of
     * each specific model has to be tested separately in those models, since
     * the session class itself is not supposed to have any knowledge of
     * specific models. (For the test, we just make up a few classes and try
     * serializing those.)
     */
    @Test
    public void rtestSerialization() {
        setUp();
        boolean simulation = true;

        this.session.clearNodes();

        SessionNode node1 = new SessionNode(Type6.class);
        SessionNode node2 = new SessionNode(Type7.class);
        SessionNode node3 = new SessionNode(Type8.class);

        this.session.addNode(node1);
        this.session.addNode(node2);
        this.session.addNode(node3);
        node1.addParent(node2);
        node1.addParent(node3);
        node3.addParent(node2);

        try {
            node2.createModel(Type7.class, simulation);
            node3.createModel(Type8.class, simulation);
            node1.createModel(Type6.class, simulation);
        }
        catch (Exception e) {
            fail("Model not created.");
        }

        try {
            new MarshalledObject(this.session).get();
        }
        catch (Exception e) {
            fail("Serialization failed.");
        }
    }
}






