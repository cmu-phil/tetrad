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

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * <p>Tests the operation of the session node. The session node needs to be able
 * to:</p> </p> <ul> </p> <li>Add and remove parents or children without
 * violating the constraint that the set of models for the parents of a node at
 * any time should be a subset of the set of objects needed to constuct an
 * object of the given model class for some constructor of the model class. Note
 * that in adding parents or children, the lists of parents or children of other
 * nodes need to be adjusted and kept in sync. </p> <li>Create a new model given
 * the parents of the node, provided the models of the node's parents can be
 * mapped unambiguously onto the objects required for some constructor of the
 * model class. </p> <li>Fire events to listeners when any of the following
 * happens: (a) parents are added or removed; (b) models are created or
 * destroyed. The adding and removing of listeners must also be tested.
 *
 * @author Joseph Ramsey
 */
public class TestSessionNode extends TestCase {

    /**
     * A string field to help debug events.
     */
    private String eventId;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSessionNode(String name) {
        super(name);
    }

    /**
     * <p>Tests the <code>existsConstructor</code> method, which determines
     * whether a constructor exists in the model class that accepts objects of
     * the given classes as arguments.</p>
     */
    public void testExistsConstructor() {
        SessionNode node = new SessionNode(Type1.class);
        Class[] testSet1 = new Class[]{Type1.class};
        Class[] testSet2 = new Class[]{Type2.class};
        Class[] testSet3 = new Class[]{Type3.class};
        Class[] testSet4 = new Class[]{Type2.class, Type3.class};
        Class[] testSet5 = new Class[]{Type3.class, Type4.class};
        Class[] testSet6 = new Class[]{Type2.class, Type2.class};

        assertTrue(!node.existsConstructor(Type1.class, testSet1));
        assertTrue(node.existsConstructor(Type1.class, testSet2));
        assertTrue(node.existsConstructor(Type1.class, testSet3));
        assertTrue(node.existsConstructor(Type1.class, testSet4));
        assertTrue(!node.existsConstructor(Type1.class, testSet5));
        assertTrue(!node.existsConstructor(Type1.class, testSet6));
    }

    /**
     * Tests whether the getValueCombination method is working. This method is
     * used to generate combinations of parent model classes.
     */
    public void testGetValueCombination() {
        SessionNode node = new SessionNode(Type1.class);
        int[] numValues = new int[]{2, 3, 4};

        assertEquals(24, node.getProduct(numValues));
        assertTrue(isTheSame(node.getValueCombination(0, numValues), 0, 0, 0));
        assertTrue(isTheSame(node.getValueCombination(5, numValues), 0, 1, 1));
        assertTrue(isTheSame(node.getValueCombination(10, numValues), 0, 2, 2));
        assertTrue(isTheSame(node.getValueCombination(15, numValues), 1, 0, 3));
        assertTrue(isTheSame(node.getValueCombination(19, numValues), 1, 1, 3));
        assertTrue(isTheSame(node.getValueCombination(23, numValues), 1, 2, 3));
    }

    /**
     * Helper method for testGetValueCombination to test whether the given array
     * has the given three values in it.
     */
    private boolean isTheSame(int[] arr, int n1, int n2, int n3) {
        return (arr[0] == n1) && (arr[1] == n2) && (arr[2] == n3);
    }

    /**
     * <p>Tests the <code>assignParameters</code> method for the case where each
     * model contains exactly one model type. Must test the following:</p> </p>
     * <ul> </p> <li>The order of the classes of the returned argument array
     * must be the same as the order of the classes in the parameterTypes array.
     * </p> <li>If one of the classes in the parameterTypes array is null, null
     * should be returned. </p> <li>If an object of some type required by the
     * parameterTypes array does not exist in the object array, null should be
     * returned. </p> <li>If there are more objects in the object array than are
     * required by the parameterTypes array, null should be returned. </p>
     * </ul>
     */
    public void testAssignParameters1() {

        // Set up a dummy session node to access the assign parameters
        // method.
        SessionNode node = new SessionNode(Type1.class);

        // Set up a list of objects to get parameters from.
        Type2 object1 = new Type2();
        Type3 object2 = new Type3(object1);
        List objects = new ArrayList();

        objects.add(object1);
        objects.add(object2);

        // Try it with the correct parameter types...
        Class[] parameterTypes1 = new Class[]{Type2.class, Type3.class};
        Object[] arguments1 = node.assignParameters(parameterTypes1, objects);

        assertNotNull(arguments1);

        for (int i = 0; i < parameterTypes1.length; i++) {
            assertEquals(arguments1[i].getClass(), parameterTypes1[i]);
        }

        // Try it with the wrong set...
        Class[] parameterTypes2 = new Class[]{Type1.class, Type3.class};
        Object[] arguments2 = node.assignParameters(parameterTypes2, objects);

        assertNull(arguments2);

        // Try it with the right set but with a null inserted.
        try {
            Class[] parameterTypes3 =
                    new Class[]{Type2.class, Type3.class, null};
            node.assignParameters(parameterTypes3, objects);
            fail("Should not have been able to assign parameters with a null " +
                    "parameter in the list.");
        }
        catch (NullPointerException e) {
            // What we wanted.
        }

        // Try it with too many types...
        Class[] parameterTypes4 =
                new Class[]{Type2.class, Type3.class, Type4.class};
        Object[] arguments4 = node.assignParameters(parameterTypes4, objects);

        assertNull(arguments4);
    }

    /**
     * Tests whether model classes can identified correctly as consistent.
     */
    public void testIsConsistentModelClass() {

        // Test single model classes.
        SessionNode node1 = new SessionNode(Type1.class);
        SessionNode node2 = new SessionNode(Type2.class);
        SessionNode node3 = new SessionNode(Type3.class);
        SessionNode node4 =
                new SessionNode(new Class[]{Type1.class, Type2.class});
        //        SessionNode node5 = new SessionNode(new Class[]{Type1.class,
        //                                                        Type2.class});
        SessionNode node6 = new SessionNode(
                new Class[]{Type1.class, Type2.class, Type3.class});
        SessionNode node7 =
                new SessionNode(new Class[]{Type1.class, Type4.class});
        List parents = new ArrayList();

        // An empty set of parents should always be consistent.s
        //assertTrue(node1.isConsistentModelClass(Type1.class, parents));
        parents.add(node1);

        //assertTrue(!node1.isConsistentModelClass(Type1.class, parents));
        parents.add(node2);

        //assertTrue(!node1.isConsistentModelClass(Type1.class, parents));
        parents.add(node3);

        //assertTrue(!node1.isConsistentModelClass(Type1.class, parents));
        parents.remove(node1);

        //assertTrue(node1.isConsistentModelClass(Type1.class, parents));
        parents.remove(node2);

        //assertTrue(node1.isConsistentModelClass(Type1.class, parents));
        parents.remove(node3);

        // Type 1 requires 2 & 3; node 4 has a type 2 in it, so this
        // is consistent. We could go on to add, say, node6, which has
        // a type 3 in it, and that would be great. At that point we
        // could construct the model.
        parents.add(node4);
        assertTrue(node1.isConsistentModelClass(Type1.class, parents));
        parents.add(node6);
        assertTrue(node1.isConsistentModelClass(Type1.class, parents));

        // If we remove node6 now and add node7, which doesn't contain
        // a 3, it should fail.
        parents.remove(node6);
        parents.add(node7);
        assertTrue(!node1.isConsistentModelClass(Type1.class, parents));
    }

    /**
     * Tests whether ClassA x = y for some ClassA in a list where x is of type
     * ClassA and y is of type ClassB.
     */
    public void testGetAssignableClass() {
        SessionNode node1 = new SessionNode("???", "Node1", Type1.class);

        List list = new ArrayList();
        list.add(List.class);
        list.add(Map.class);
        list.add(String.class);

        assertNotNull(node1.getAssignableClass(list, ArrayList.class));
        assertNotNull(node1.getAssignableClass(list, SortedMap.class));
        assertNull(node1.getAssignableClass(list, Object.class));
    }

    /**
     * Tests whether parent nodes can be added and removed correctly.
     */
    public void testAddRemoveParents() {
        SessionNode node1 = new SessionNode("???", "Node1", Type1.class);
        SessionNode node2 = new SessionNode("???", "Node2", Type2.class);
        SessionNode node3 = new SessionNode("???", "Node2", Type3.class);
        SessionNode node4 = new SessionNode("???", "Node4", Type4.class);

        assertTrue(node1.addParent(node2));
        assertEquals(1, node2.getNumChildren());
        assertEquals(1, node1.getNumParents());
        assertTrue(!node1.addParent(node4));
        assertTrue(node1.addParent(node3));
        assertTrue(!node1.removeParent(node4));
        assertTrue(node1.removeParent(node2));
        assertEquals(1, node1.getNumParents());
        assertTrue(node1.removeParent(node3));
        assertEquals(0, node1.getNumParents());
    }

    /**
     * Tests whether children nodes can be added and removed correctly.
     */
    public void testAddRemoveChildren() {
        SessionNode node1 = new SessionNode("???", "Node1", Type1.class);
        SessionNode node2 = new SessionNode("???", "Node2", Type2.class);
        SessionNode node3 = new SessionNode("???", "Node3", Type3.class);
        SessionNode node4 = new SessionNode("???", "Node4", Type4.class);

        assertTrue(node2.addChild(node1));
        assertEquals(1, node2.getNumChildren());
        assertEquals(1, node1.getNumParents());
        assertTrue(!node4.addChild(node1));
        assertTrue(node3.addChild(node1));
        assertTrue(!node4.removeChild(node1));
        assertEquals(1, node2.getNumChildren());
        assertEquals(1, node3.getNumChildren());
        assertTrue(node2.removeChild(node1));
        assertTrue(node3.removeChild(node1));
        assertEquals(0, node3.getNumChildren());
    }

    /**
     * Tests whether the consistent model classes are calculated correctly. Note
     * that this method should not return anything but null unless all of the
     * parent classes have models in them.
     */
    public void testGetConsistentModelClasses() throws Exception {
        boolean simulation = true;

        SessionNode node1 = new SessionNode(new Class[]{Type1.class});
        SessionNode node2 = new SessionNode(new Class[]{Type2.class});
        SessionNode node3 = new SessionNode(new Class[]{Type3.class});

        assertTrue(node1.addParent(node2));
        assertTrue(node1.addParent(node3));
        assertTrue(node3.addParent(node2));

        Class[] classes = node1.getConsistentModelClasses();

        assertNull(classes);

        try {
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
        }
        catch (RuntimeException e) {
            fail("Model not created.");
        }

        classes = node1.getConsistentModelClasses();

        assertNotNull(classes);
        assertEquals(classes[0], Type1.class);
    }

    /**
     * Tests whether a model can be created correctly.
     */
    public void testCreateModel() {
        boolean simulation = true;

        SessionNode node1 = new SessionNode(Type1.class);
        SessionNode node2 = new SessionNode(Type2.class);
        SessionNode node3 = new SessionNode(Type3.class);

        node3.addParent(node2);
        node1.addParent(node2);
        node1.addParent(node3);

        try {
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
            node1.createModel(Type1.class, simulation);
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("Model not created.");
        }

        // TODO: When models are destroyed, models downstream should
        // be destroyed. This has to come from nodes listening to each
        // other. jdramsey 12/25/01
        node1.destroyModel();
        assertNull(node1.getModel());
    }

    /**
     * Tests to make sure events are sent and received properly.
     */
    public void testEvents() {
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

        // Create up some nodes.
        SessionNode node1 = new SessionNode("???", "Node1", Type1.class);
        SessionNode node2 = new SessionNode("???", "Node2", Type2.class);
        SessionNode node3 = new SessionNode("???", "Node3", Type3.class);

        // Listen to node1.
        node1.addSessionListener(listener);

        // Test adding and removing parents and children.
        try {
            setEventId(null);
            node1.addParent(node2);
            assertEquals("parentAdded", getEventId());
            setEventId(null);
            node1.removeParent(node2);
            assertEquals("parentRemoved", getEventId());
            setEventId(null);
            node2.addChild(node1);
            assertEquals("parentAdded", getEventId());
            setEventId(null);
            node2.removeChild(node1);
            assertEquals("parentRemoved", getEventId());

            // Test model created.
            node1.addParent(node2);
            node1.addParent(node3);
            node3.addParent(node2);
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
            setEventId(null);
            node1.createModel(Type1.class, simulation);
            assertEquals("modelCreated", getEventId());
            setEventId(null);
            node1.destroyModel();
            assertEquals("modelDestroyed", getEventId());

            // Test reassess model.
            node1.createModel(Type1.class, simulation);
            assertNotNull(node1.getModel());
            node2.destroyModel();
            assertNull(node1.getModel());
        }
        catch (Exception e) {
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
     * Tests the <code>isStructurallyIdentical</code> method.
     */
    public void testStructuralIdentity() {
        boolean simulation = true;

        SessionNode node1 = new SessionNode(Type1.class);
        SessionNode node2 = new SessionNode(Type2.class);
        SessionNode node3 = new SessionNode(Type3.class);

        try {
            node1.addParent(node2);
            node1.addParent(node3);
            node3.addParent(node2);
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
            node1.createModel(Type1.class, simulation);
            assertTrue(node1.isStructurallyIdentical(node1));
            assertTrue(!node1.isStructurallyIdentical(node2));
        }
        catch (Exception e) {
            fail(e.getMessage());
        }
    }

    /**
     * Tests whether the session can be serialized and reconstructed correctly.
     * This only tests the skeletal form of the serialization--serialization of
     * each specific model has to be tested separately in those models, since
     * the session class itself is not supposed to have any knowledge of
     * specific models. (For the test, we just make up a few classes and try
     * serializing those.)
     */
    public void testSerialization() {
        boolean simulation = true;

        SessionNode node1 = new SessionNode(Type1.class);
        SessionNode node2 = new SessionNode(Type2.class);
        SessionNode node3 = new SessionNode(Type3.class);

        node1.addParent(node2);
        node1.addParent(node3);
        node3.addParent(node2);

        try {
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
            node1.createModel(Type1.class, simulation);
        }
        catch (Exception e) {
            fail("Model not created.");
        }

        SessionNode node1Copy = null;

        try {
            node1Copy = (SessionNode) new MarshalledObject(node1).get();
        }
        catch (Exception e) {
            fail("Serialization failed.");
        }

        assertTrue(node1.isStructurallyIdentical(node1Copy));
    }

    /**
     * Tests whether parameters can be added correctly.
     */
    public void testParameterization() {
        boolean simulation = true;

        SessionNode node1 = new SessionNode(Type1.class);
        SessionNode node2 = new SessionNode(Type2.class);
        SessionNode node3 = new SessionNode(Type3.class);
        Type2 param = new Type2();

        node1.putParam(Type1.class, param);
        node1.addParent(node3);
        node3.addParent(node2);

        try {
            node2.createModel(Type2.class, simulation);
            node3.createModel(Type3.class, simulation);
            node1.createModel(Type1.class, simulation);
        }
        catch (Exception e) {
            fail("Model not created.");
        }

        node1.destroyModel();

        try {
            node1.createModel(simulation);
        }
        catch (Exception e) {
            fail("Model not created.");
        }
    }

    /**
     * Tests whether the name of the node is set correctly.
     */
    public void testSetName() {
        String name = "Test";
        SessionNode node1 = new SessionNode("???", name, Type1.class);
        assertEquals(name, node1.getDisplayName());
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSessionNode.class);
    }
}






