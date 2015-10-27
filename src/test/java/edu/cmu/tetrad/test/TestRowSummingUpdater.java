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

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the BayesUpdqater
 *
 * @author Joseph Ramsey
 */
public final class TestRowSummingUpdater extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestRowSummingUpdater(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown(){
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    /**
     * Richard's 2-variable example worked by hand.
     */
    public void testUpdate0() {
        BayesIm bayesIm = sampleBayesIm0();

        System.out.println(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        int zIndex = evidence.getNodeIndex("z");
//        int valueIndex = evidence.getCategoryIndex("z", "0");

        evidence.getProposition().setCategory(zIndex, 1);

        System.out.println(evidence);

        ManipulatingBayesUpdater updater = new RowSummingExactUpdater(bayesIm);

        updater.setEvidence(evidence);
        BayesIm updatedIm = updater.getUpdatedBayesIm();

        // Print before and after
        System.out.println(bayesIm.getBayesPm());
        System.out.println(bayesIm);
        System.out.println(updatedIm);

        // Check results.
        assertEquals(0, updatedIm.getProbability(0, 0, 0), 0.001);
        assertEquals(1, updatedIm.getProbability(0, 0, 1), 0.001);
    }

    /**
     * Richard's 2-variable example worked by hand.
     */
    public void testUpdate1() {
        BayesIm bayesIm = sampleBayesIm1();
        ManipulatingBayesUpdater updater = new RowSummingExactUpdater(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        int xIndex = evidence.getNodeIndex("x");
        int zIndex = evidence.getNodeIndex("z");
        int valueIndex = evidence.getCategoryIndex("z", "1");

        evidence.getProposition().setCategory(zIndex, valueIndex);

        System.out.println(evidence);

        updater.setEvidence(evidence);
        BayesIm updatedIm = updater.getUpdatedBayesIm();

        // Print before and after
        System.out.println(bayesIm.getBayesPm());
        System.out.println(bayesIm);
        System.out.println(updatedIm);

        // Check results.
        assertEquals(0.1250, updatedIm.getProbability(0, 0, 0), 0.001);
        assertEquals(0.8750, updatedIm.getProbability(0, 0, 1), 0.001);

        assertEquals(0.0000, updatedIm.getProbability(1, 0, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(1, 0, 1), 0.001);

        assertEquals(0.0000, updatedIm.getProbability(1, 1, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(1, 1, 1), 0.001);

        System.out.println(updater.getMarginal(xIndex, 0));

        ManipulatingBayesUpdater updater2 = new CptInvariantUpdater(bayesIm);
        Evidence evidence2 = new Evidence(evidence, bayesIm);
        updater2.setEvidence(evidence2);

        System.out.println(updater2.getMarginal(xIndex, 0));
    }

    /**
     * Bill's 3-variable example, with c=value2.
     */
    public static void testUpdate2() {
        BayesIm bayesIm = sampleBayesIm2();
        ManipulatingBayesUpdater updater = new RowSummingExactUpdater(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        int nodeIndex = evidence.getNodeIndex("c");
        int valueIndex = evidence.getCategoryIndex("c", "1");

        evidence.getProposition().setCategory(nodeIndex, valueIndex);

        updater.setEvidence(evidence);
        BayesIm updatedIm = updater.getUpdatedBayesIm();

        // Print before and after
        System.out.println(bayesIm.getBayesPm());
        System.out.println(bayesIm);
        System.out.println(updatedIm);

        // Check results.
        assertEquals(0.2750, updatedIm.getProbability(0, 0, 0), 0.001);
        assertEquals(0.7250, updatedIm.getProbability(0, 0, 1), 0.001);

        assertEquals(0.0556, updatedIm.getProbability(1, 0, 0), 0.001);
        assertEquals(0.6667, updatedIm.getProbability(1, 0, 1), 0.001);
        assertEquals(0.2778, updatedIm.getProbability(1, 0, 2), 0.001);

        assertEquals(0.7869, updatedIm.getProbability(1, 1, 0), 0.001);
        assertEquals(0.0656, updatedIm.getProbability(1, 1, 1), 0.001);
        assertEquals(0.1475, updatedIm.getProbability(1, 1, 2), 0.001);

        assertEquals(0.0000, updatedIm.getProbability(2, 0, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 0, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(2, 1, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 1, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(2, 2, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 2, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(2, 3, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 3, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(2, 4, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 4, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(2, 5, 0), 0.001);
        assertEquals(1.0000, updatedIm.getProbability(2, 5, 1), 0.001);
    }

    /**
     * Bill's 3-variable example, with b=value1.
     */
    public static void testUpdate3() {
        BayesIm bayesIm = sampleBayesIm2();
        ManipulatingBayesUpdater updater = new RowSummingExactUpdater(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        int nodeIndex = evidence.getNodeIndex("b");
        int valueIndex = evidence.getCategoryIndex("b", "0");

        evidence.getProposition().setCategory(nodeIndex, valueIndex);

        System.out.println(evidence);

        updater.setEvidence(evidence);
        BayesIm updatedIm = updater.getUpdatedBayesIm();

        // Print before and after
        System.out.println(bayesIm.getBayesPm());
        System.out.println(bayesIm);
        System.out.println(updatedIm);

        // Check results.
        assertEquals(0.1765, updatedIm.getProbability(0, 0, 0), 0.001);
        assertEquals(0.8235, updatedIm.getProbability(0, 0, 1), 0.001);

        assertEquals(1.0000, updatedIm.getProbability(1, 0, 0), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(1, 0, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(1, 0, 2), 0.001);

        assertEquals(1.0000, updatedIm.getProbability(1, 1, 0), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(1, 1, 1), 0.001);
        assertEquals(0.0000, updatedIm.getProbability(1, 1, 2), 0.001);

        assertEquals(0.9000, updatedIm.getProbability(2, 0, 0), 0.001);
        assertEquals(0.1000, updatedIm.getProbability(2, 0, 1), 0.001);
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 1, 0)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 1, 1)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 2, 0)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 2, 1)));
        assertEquals(0.2000, updatedIm.getProbability(2, 3, 0), 0.001);
        assertEquals(0.8000, updatedIm.getProbability(2, 3, 1), 0.001);
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 4, 0)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 4, 1)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 5, 0)));
        assertTrue(Double.isNaN(updatedIm.getProbability(2, 5, 1)));
    }

    public static void testUpdate4() {
        Node x0Node = new GraphNode("X0");
        Node x1Node = new GraphNode("X1");
        Node x2Node = new GraphNode("X2");
        Node x3Node = new GraphNode("X3");

        Dag graph = new Dag();
        graph.addNode(x0Node);
        graph.addNode(x1Node);
        graph.addNode(x2Node);
        graph.addNode(x3Node);

        graph.addDirectedEdge(x0Node, x1Node);
        graph.addDirectedEdge(x0Node, x2Node);
        graph.addDirectedEdge(x1Node, x3Node);
        graph.addDirectedEdge(x2Node, x3Node);

        System.out.println(graph);

        BayesPm bayesPm = new BayesPm(graph);
        MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        //        int x0 = bayesIm.getNodeIndex(x0Node);
        //        int x1 = bayesIm.getNodeIndex(x1Node);
        int x2 = bayesIm.getNodeIndex(x2Node);
        int x3 = bayesIm.getNodeIndex(x3Node);

        System.out.println(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        evidence.getProposition().setCategory(x2, 0);

        System.out.println(evidence);

        BayesUpdater updater1 = new CptInvariantUpdater(bayesIm);
        updater1.setEvidence(evidence);

        BayesUpdater updater2 = new RowSummingExactUpdater(bayesIm);
        updater2.setEvidence(evidence);

        double marginal1 = updater1.getMarginal(x3, 0);
        double marginal2 = updater2.getMarginal(x3, 0);

        System.out.println("Marginal from CPT Inv = " + marginal1);
        System.out.println("Marginal from Row Summer = " + marginal2);

        assertEquals(marginal1, marginal2, 0.000001);
    }

    public static void testUpdate5() {
        Node x0Node = new GraphNode("X0");
        Node x1Node = new GraphNode("X1");
        Node x2Node = new GraphNode("X2");
        Node x3Node = new GraphNode("X3");
        Node x4Node = new GraphNode("X4");

        Dag graph = new Dag();
        graph.addNode(x0Node);
        graph.addNode(x1Node);
        graph.addNode(x2Node);
        graph.addNode(x3Node);
        graph.addNode(x4Node);

        graph.addDirectedEdge(x0Node, x1Node);
        graph.addDirectedEdge(x0Node, x2Node);
        graph.addDirectedEdge(x1Node, x3Node);
        graph.addDirectedEdge(x2Node, x3Node);
        graph.addDirectedEdge(x4Node, x0Node);
        graph.addDirectedEdge(x4Node, x2Node);

        System.out.println(graph);

        BayesPm bayesPm = new BayesPm(graph);
        MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        int x1 = bayesIm.getNodeIndex(x1Node);
        int x2 = bayesIm.getNodeIndex(x2Node);
        int x3 = bayesIm.getNodeIndex(x3Node);

        System.out.println(bayesIm);

        Evidence evidence = Evidence.tautology(bayesIm);
        evidence.getProposition().setCategory(x1, 1);
        evidence.getProposition().setCategory(x2, 0);

        evidence.getNodeIndex("X1");

        System.out.println(evidence);

        BayesUpdater updater1 = new CptInvariantUpdater(bayesIm);
        updater1.setEvidence(evidence);

        BayesUpdater updater2 = new RowSummingExactUpdater(bayesIm);
        updater2.setEvidence(evidence);

        double marginal1 = updater1.getMarginal(x3, 0);
        double marginal2 = updater2.getMarginal(x3, 0);

        System.out.println("Marginal from CPT Inv = " + marginal1);
        System.out.println("Marginal from Row Summer = " + marginal2);

        assertEquals(marginal1, marginal2, 0.000001);
    }

    private static BayesIm sampleBayesIm0() {
        Node z = new GraphNode("z");

        Dag graph = new Dag();

        graph.addNode(z);

        System.out.println(graph);

        BayesPm bayesPm = new BayesPm(graph);

        BayesIm bayesIm1 = new MlBayesIm(bayesPm);
        bayesIm1.setProbability(0, 0, 0, .3);
        bayesIm1.setProbability(0, 0, 1, .7);

        return bayesIm1;
    }

    private BayesIm sampleBayesIm1() {
        Node x = new GraphNode("x");
        Node z = new GraphNode("z");

        Dag graph = new Dag();

        graph.addNode(x);
        graph.addNode(z);

        graph.addDirectedEdge(x, z);

        System.out.println(graph);

        BayesPm bayesPm = new BayesPm(graph);

        BayesIm bayesIm1 = new MlBayesIm(bayesPm);
        bayesIm1.setProbability(0, 0, 0, .3);
        bayesIm1.setProbability(0, 0, 1, .7);

        bayesIm1.setProbability(1, 0, 0, .8);
        bayesIm1.setProbability(1, 0, 1, .2);

        bayesIm1.setProbability(1, 1, 0, .4);
        bayesIm1.setProbability(1, 1, 1, .6);

        return bayesIm1;
    }

    private static BayesIm sampleBayesIm2() {
        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Dag graph;

        graph = new Dag();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(a, c);
        graph.addDirectedEdge(b, c);

        System.out.println(graph);

        BayesPm bayesPm = new BayesPm(graph);
        bayesPm.setNumCategories(b, 3);

        BayesIm bayesIm1 = new MlBayesIm(bayesPm);
        bayesIm1.setProbability(0, 0, 0, .3);
        bayesIm1.setProbability(0, 0, 1, .7);

        bayesIm1.setProbability(1, 0, 0, .3);
        bayesIm1.setProbability(1, 0, 1, .4);
        bayesIm1.setProbability(1, 0, 2, .3);

        bayesIm1.setProbability(1, 1, 0, .6);
        bayesIm1.setProbability(1, 1, 1, .1);
        bayesIm1.setProbability(1, 1, 2, .3);

        bayesIm1.setProbability(2, 0, 0, .9);
        bayesIm1.setProbability(2, 0, 1, .1);

        bayesIm1.setProbability(2, 1, 0, .1);
        bayesIm1.setProbability(2, 1, 1, .9);

        bayesIm1.setProbability(2, 2, 0, .5);
        bayesIm1.setProbability(2, 2, 1, .5);

        bayesIm1.setProbability(2, 3, 0, .2);
        bayesIm1.setProbability(2, 3, 1, .8);

        bayesIm1.setProbability(2, 4, 0, .6);
        bayesIm1.setProbability(2, 4, 1, .4);

        bayesIm1.setProbability(2, 5, 0, .7);
        bayesIm1.setProbability(2, 5, 1, .3);
        return bayesIm1;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestRowSummingUpdater.class);
    }
}





