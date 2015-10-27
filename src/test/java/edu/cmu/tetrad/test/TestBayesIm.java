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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestBayesIm extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestBayesIm(String name) {
        super(name);
    }

    public static void testConstructRandom() {
        System.out.println("\n\nTest construct random.");
        Graph graph = GraphConverter.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        System.out.println(bayesIm);
    }


    public static void testCopyConstructor() {
        System.out.println("\n\nTest copy constructor.");
        Graph graph = GraphConverter.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        //        System.out.println(bayesIm);
        BayesIm bayesIm2 = new MlBayesIm(bayesIm);
        //        System.out.println(bayesIm2);
        assertEquals(bayesIm, bayesIm2);
    }

    public static void testConstructManual() {
        System.out.println("\n\nTest construct manual.");
        Graph graph = GraphConverter.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm);
        System.out.println(bayesIm);
    }

    /**
     * Tests whether the BayesIm does the right thing in a very simple case
     * where nodes are added or removed from the graph. Start with graph a -> b,
     * parameterizing with two values for each node. Construct and fill in
     * probability tables in BayesIm. Then add edge c -> b "manually." This
     * should create a table of values for c that is unspecified, and it should
     * double up the rows from b. Then remove the node c. Now the table for b
     * should be completely unspecified.
     */
    public static void testAddRemoveParent() {
        System.out.println("\n\nTest add and remove parent.");

        Node a = new GraphNode("a");
        Node b = new GraphNode("b");

        Dag dag = new Dag();

        dag.addNode(a);
        dag.addNode(b);

        dag.addDirectedEdge(a, b);

        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        System.out.println(bayesIm);

        BayesIm bayesIm2 = new MlBayesIm(bayesPm, bayesIm, MlBayesIm.MANUAL);
        System.out.println(bayesIm2);

        assertEquals(bayesIm, bayesIm2);

        Node c = new GraphNode("c");
        dag.addNode(c);
        dag.addDirectedEdge(c, b);
        System.out.println("dag = " + dag);

        BayesPm bayesPm3 = new BayesPm(dag, bayesPm);
        BayesIm bayesIm3 = new MlBayesIm(bayesPm3, bayesIm2, MlBayesIm.MANUAL);
        System.out.println("bayesIm3 = " + bayesIm3);

        // Make sure the rows got repeated.
//        assertTrue(rowsEqual(bayesIm3, bayesIm3.getNodeIndex(b), 0, 1));
//        assertTrue(!rowsEqual(bayesIm3, bayesIm3.getNodeIndex(b), 1, 2));
//        assertTrue(rowsEqual(bayesIm3, bayesIm3.getNodeIndex(b), 2, 3));

        // Make sure the 'c' node got ?'s.
        assertTrue(rowUnspecified(bayesIm3, bayesIm3.getNodeIndex(c), 0));

        dag.removeNode(c);
        BayesPm bayesPm4 = new BayesPm(dag, bayesPm3);
        BayesIm bayesIm4 = new MlBayesIm(bayesPm4, bayesIm3, MlBayesIm.MANUAL);
        System.out.println("bayesIm4 = " + bayesIm4);

        // Make sure the 'b' node has 2 rows of '?'s'.
        assertTrue(bayesIm4.getNumRows(bayesIm4.getNodeIndex(b)) == 2);
        assertTrue(rowUnspecified(bayesIm4, bayesIm4.getNodeIndex(b), 0));
        assertTrue(rowUnspecified(bayesIm4, bayesIm4.getNodeIndex(b), 1));
    }

    /**
     * Tests whether the BayesIm does the right thing in a very simple case
     * where values of a nodes are added or removed from the BayesPm. Start with
     * graph a -> b <- c, construct and fill in probability tables in BayesIm.
     * Then add edge c -> b "manually." This should create a table of values for
     * c that is unspecified, and it should double up the rows from b. Then
     * remove the node c. Now the table for b should be completely unspecified.
     */
    public static void testAddRemoveValues() {
        System.out.println("\n\nTest retain old values.");

        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Dag dag = new Dag();

        dag.addNode(a);
        dag.addNode(b);
        dag.addNode(c);

        dag.addDirectedEdge(a, b);
        dag.addDirectedEdge(c, b);

        assertTrue(Edges.isDirectedEdge(dag.getEdge(a, b)));

        BayesPm bayesPm = new BayesPm(dag, 3, 3);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        System.out.println(bayesIm);

        bayesPm.setNumCategories(a, 4);
        bayesPm.setNumCategories(c, 4);
        BayesIm bayesIm2 = new MlBayesIm(bayesPm, bayesIm, MlBayesIm.MANUAL);
        System.out.println(bayesIm2);

        bayesPm.setNumCategories(a, 2);
        BayesIm bayesIm3 = new MlBayesIm(bayesPm, bayesIm2, MlBayesIm.MANUAL);
        System.out.println(bayesIm3);

        bayesPm.setNumCategories(b, 2);
        BayesIm bayesIm4 = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        // At this point, a has 2 categories, b has 2 categories, and c has 4 categories.

        for (int node = 0; node < bayesIm4.getNumNodes(); node++) {
            for (int row = 0; row < bayesIm4.getNumRows(node); row++) {
                for (int col = 0; col < bayesIm4.getNumColumns(node); col++) {
                    bayesIm4.setProbability(node, row, col, Double.NaN);
                }
            }
        }

        System.out.println(bayesIm4);

        double[][] aTable = {
                {.2, .8}
        };

        double[][] bTable = {
                {.1, .9},
                {.7, .3},
                {.3, .7},
                {.5, .5},
                {.09, .91},
                {.6, .4},
                {.2, .8},
                {.8, .2}
        };

        double[][] cTable = {
                {.1, .2, .3, .4},
        };

        int _a = bayesIm.getNodeIndex(a);

        for (int row = 0; row < bayesIm4.getNumRows(_a); row++) {
            for (int col = 0; col < bayesIm4.getNumColumns(_a); col++) {
                bayesIm4.setProbability(_a, row, col, aTable[row][col]);
            }
        }

        int _b = bayesIm.getNodeIndex(b);

        for (int row = 0; row < bayesIm4.getNumRows(_b); row++) {
            for (int col = 0; col < bayesIm4.getNumColumns(_b); col++) {
                bayesIm4.setProbability(_b, row, col, bTable[row][col]);
            }
        }

        int _c = bayesIm.getNodeIndex(c);

        for (int row = 0; row < bayesIm4.getNumRows(_c); row++) {
            for (int col = 0; col < bayesIm4.getNumColumns(_c); col++) {
                bayesIm4.setProbability(_c, row, col, cTable[row][col]);
            }
        }

        System.out.println(bayesIm4);

    }

    private static boolean rowsEqual(BayesIm bayesIm, int node, int row1,
                                     int row2) {
        for (int col = 0; col < bayesIm.getNumColumns(node); col++) {
            double prob1 = bayesIm.getProbability(node, row1, col);
            double prob2 = bayesIm.getProbability(node, row2, col);
            if (prob1 != prob2) {
                return false;
            }
        }

        return true;
    }

    private static boolean rowUnspecified(BayesIm bayesIm, int node, int row) {
        for (int col = 0; col < bayesIm.getNumColumns(node); col++) {
            double prob = bayesIm.getProbability(node, row, col);
            if (!Double.isNaN(prob)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBayesIm.class);
    }
}




