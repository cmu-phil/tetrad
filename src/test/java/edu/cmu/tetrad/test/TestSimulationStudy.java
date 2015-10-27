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

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the basic functionality of the SimulationStudy.
 *
 * @author Joseph Ramsey
 */
public class TestSimulationStudy extends TestCase {

    /* Variables to help count the model creations. */
    private List nodes;
    private int[] creations;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSimulationStudy(String name) {
        super(name);
    }

    public void testPlaceholder() {
        // This test is here so that there will be at least one test in this
        // class until the testExecutionPath method is fixed.
    }

    /**
     * Sets up a tree of objects, sets repetitions on some of them, and makes
     * sure that the correct number of models is created for various nodes.
     */
    public void rtestExecutionPath() {

        // TODO: With the thread in SimulationStudy.run(Node), this
        // test no longer works, since it doesn't wait around for the
        // thread to finish before reporting counts. Maybe this means
        // the thread in run is a bad idea, or maybe it means this
        // test needs to be fixed, I don't know yet. jdramsey 4/29/02

        Session session = new Session("Test");
        SessionNode node1 = new SessionNode(null, "Node1", Type11.class);
        SessionNode node2 = new SessionNode(null, "Node2", Type12.class);
        SessionNode node3 = new SessionNode(null, "Node3", Type12.class);
        SessionNode node4 = new SessionNode(null, "Node4", Type12.class);
        SessionNode node5 = new SessionNode(null, "Node5", Type12.class);
        SessionNode node6 = new SessionNode(null, "Node6", Type12.class);
        SessionNode node7 = new SessionNode(null, "Node7", Type12.class);
        SessionNode node8 = new SessionNode(null, "Node8", Type12.class);

        session.addNode(node1);
        session.addNode(node2);
        session.addNode(node3);
        session.addNode(node4);
        session.addNode(node5);
        session.addNode(node6);
        session.addNode(node7);
        session.addNode(node8);
        node1.addChild(node2);
        node2.addChild(node3);
        node2.addChild(node4);
        node3.addChild(node5);
        node3.addChild(node6);
        node4.addChild(node7);
        node4.addChild(node8);

        this.nodes = new ArrayList();

        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        nodes.add(node4);
        nodes.add(node5);
        nodes.add(node6);
        nodes.add(node7);
        nodes.add(node8);
        initCreations(nodes);

        SimulationStudy simulationStudy = new SimulationStudy(session);

        session.addSessionListener(new SessionAdapter() {
            public void modelCreated(SessionEvent e) {
                SessionNode node = e.getNode();
                incrementCreationCount(node);
            }
        });

        simulationStudy.setRepetition(node2, 2);
        simulationStudy.setRepetition(node4, 3);
        simulationStudy.setRepetition(node5, 7);

        boolean overwrite = true;
        simulationStudy.execute(node1, overwrite);
        assertEquals(2, getCreationCount(node2));
        assertEquals(6, getCreationCount(node4));
        assertEquals(14, getCreationCount(node5));
        assertEquals(6, getCreationCount(node8));
        assertEquals(2, getCreationCount(node3));
        assertEquals(1, getCreationCount(node1));
    }

    public synchronized void incrementCreationCount(SessionNode node) {
        System.out.println("Incrementing: " + node);
        this.creations[nodes.indexOf(node)]++;
    }

    public synchronized int getCreationCount(SessionNode node) {
        return this.creations[nodes.indexOf(node)];
    }

    public void initCreations(List nodes) {
        this.nodes = new ArrayList(nodes);
        this.creations = new int[nodes.size()];
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSimulationStudy.class);
    }
}






