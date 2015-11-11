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
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the BayesUpdqater
 *
 * @author William Taysom
 */
public final class TestUpdatedBayesIm extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestUpdatedBayesIm(String name) {
        super(name);
    }

    public static void testCompound() {
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

        UpdatedBayesIm updatedIm1 = new UpdatedBayesIm(bayesIm);
        assertEquals(bayesIm, updatedIm1);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                System.out.println("*** i = " + i + ", j = " + j);

                Evidence evidence1 = updatedIm1.getEvidence();
                evidence1.getProposition().disallowComplement(i, 0);
                UpdatedBayesIm updatedIm2 =
                        new UpdatedBayesIm(updatedIm1, evidence1);

                Evidence evidence2 = updatedIm2.getEvidence();
                evidence2.getProposition().setToTautology();
                evidence2.getProposition().disallowComplement(j, 0);

                CptInvariantMarginalCalculator marginals1 =
                        new CptInvariantMarginalCalculator(updatedIm2,
                                evidence2);
                double marginal1 = marginals1.getMarginal(0, 0);

                Evidence evidence3 = updatedIm1.getEvidence();
                evidence3.getProposition().disallowComplement(i, 0);
                evidence3.getProposition().disallowComplement(j, 0);

                CptInvariantMarginalCalculator marginals2 =
                        new CptInvariantMarginalCalculator(updatedIm1,
                                evidence3);

                double marginal2 = marginals2.getMarginal(0, 0);

                assertEquals(marginal1, marginal2, 1.0e-2);
            }
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestUpdatedBayesIm.class);
    }
}





