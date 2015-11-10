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
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.Proposition;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the BayesUpdqater
 *
 * @author William Taysom
 */
public final class TestProposition extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestProposition(String name) {
        super(name);
    }

    /**
     * Richard's 2-variable example worked by hand.
     */
    public static void testUpdate1() {
        BayesIm bayesIm = sampleBayesIm2();

        Proposition prop1 = Proposition.tautology(bayesIm);

        System.out.println(prop1);

        prop1.removeCategory(0, 1);

        System.out.println(prop1);

        prop1.setVariable(1, false);

        System.out.println(prop1);

        Proposition prop2 = new Proposition(bayesIm, prop1);

        System.out.println("Copied prop:");
        System.out.println(prop2);

        assertEquals(prop1, prop2);

        BayesIm bayesIm2 = new MlBayesIm(bayesIm);

        Proposition prop3 = new Proposition(bayesIm2, prop1);

        assertTrue(!prop3.equals(prop1));
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
        return new TestSuite(TestProposition.class);
    }
}





