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
import edu.cmu.tetrad.graph.NodeType;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Frank Wimberly
 */
public final class TestUpdaterJointMarginal extends TestCase {

    public TestUpdaterJointMarginal(String name) {
        super(name);
    }

    public static void testEstimate1() {

        Dag graph = new Dag();
        Node L1 = new GraphNode("L1");
        Node X1 = new GraphNode("X1");
        Node X2 = new GraphNode("X2");
        Node X3 = new GraphNode("X3");

        L1.setNodeType(NodeType.MEASURED);
        X1.setNodeType(NodeType.MEASURED);
        X2.setNodeType(NodeType.MEASURED);
        X3.setNodeType(NodeType.MEASURED);

        graph.addNode(L1);
        graph.addNode(X1);
        graph.addNode(X2);
        graph.addNode(X3);

        graph.addDirectedEdge(L1, X1);
        graph.addDirectedEdge(L1, X2);
        graph.addDirectedEdge(L1, X3);

        BayesPm bayesPm = new BayesPm(graph);
        bayesPm.setNumCategories(L1, 2);
        bayesPm.setNumCategories(X1, 2);
        bayesPm.setNumCategories(X2, 2);
        bayesPm.setNumCategories(X3, 2);

        BayesIm estimatedIm = new MlBayesIm(bayesPm);

        Node l1Node = graph.getNode("L1");
        //int l1Index = bayesImMixed.getNodeIndex(l1Node);
        int l1Index = estimatedIm.getNodeIndex(l1Node);
        Node x1Node = graph.getNode("X1");
        //int x1Index = bayesImMixed.getNodeIndex(x1Node);
        int x1Index = estimatedIm.getNodeIndex(x1Node);
        Node x2Node = graph.getNode("X2");
        //int x2Index = bayesImMixed.getNodeIndex(x2Node);
        int x2Index = estimatedIm.getNodeIndex(x2Node);
        Node x3Node = graph.getNode("X3");
        //int x3Index = bayesImMixed.getNodeIndex(x3Node);
        int x3Index = estimatedIm.getNodeIndex(x3Node);

        //bayesImMixed.setProbability(l1Index, 0, 0, 0.5);
        //bayesImMixed.setProbability(l1Index, 0, 1, 0.5);
        estimatedIm.setProbability(l1Index, 0, 0, 0.5);
        estimatedIm.setProbability(l1Index, 0, 1, 0.5);

        //bayesImMixed.setProbability(x1Index, 0, 0, 0.33333);
        //bayesImMixed.setProbability(x1Index, 0, 1, 0.66667);
        estimatedIm.setProbability(x1Index, 0, 0,
                0.33333);      //p(x1 = 0 | l1 = 0)
        estimatedIm.setProbability(x1Index, 0, 1,
                0.66667);      //p(x1 = 1 | l1 = 0)
        estimatedIm.setProbability(x1Index, 1, 0,
                0.66667);      //p(x1 = 0 | l1 = 1)
        estimatedIm.setProbability(x1Index, 1, 1,
                0.33333);      //p(x1 = 1 | l1 = 1)

        //bayesImMixed.setProbability(x2Index, 1, 0, 0.66667);
        //bayesImMixed.setProbability(x2Index, 1, 1, 0.33333);
        estimatedIm.setProbability(x2Index, 1, 0,
                0.66667);      //p(x2 = 0 | l1 = 1)
        estimatedIm.setProbability(x2Index, 1, 1,
                0.33333);      //p(x2 = 1 | l1 = 1)
        estimatedIm.setProbability(x2Index, 0, 0,
                0.33333);      //p(x2 = 0 | l1 = 0)
        estimatedIm.setProbability(x2Index, 0, 1,
                0.66667);      //p(x2 = 1 | l1 = 0)

        //bayesImMixed.setProbability(x3Index, 1, 0, 0.66667);
        //bayesImMixed.setProbability(x3Index, 1, 1, 0.33333);
        estimatedIm.setProbability(x3Index, 1, 0,
                0.66667);      //p(x3 = 0 | l1 = 1)
        estimatedIm.setProbability(x3Index, 1, 1,
                0.33333);      //p(x3 = 1 | l1 = 1)
        estimatedIm.setProbability(x3Index, 0, 0,
                0.33333);      //p(x3 = 0 | l1 = 0)
        estimatedIm.setProbability(x3Index, 0, 1,
                0.66667);      //p(x3 = 1 | l1 = 0)

        Evidence evidence = Evidence.tautology(estimatedIm);
        evidence.getProposition().setCategory(x1Index, 0);
        evidence.getProposition().setCategory(x2Index, 0);
        evidence.getProposition().setCategory(x3Index, 0);

        RowSummingExactUpdater rseu = new RowSummingExactUpdater(estimatedIm);

        rseu.setEvidence(evidence);

        int[] vars1 = {l1Index};
        int[] vals1 = {0};

        double p1 = rseu.getJointMarginal(vars1, vals1);
        assertEquals(0.1111, p1, 0.0001);

        System.out.println("p1 = " + p1);

        int[] vars2 = {l1Index, x1Index};
        //int[] vars2 = {0, 1};
        int[] vals2 = {0, 0};

        double p2 = rseu.getJointMarginal(vars2, vals2);
        assertEquals(0.1111, p2, 0.0001);

        System.out.println("p2 = " + p2);

        int[] vals3 = {1, 0};

        double p3 = rseu.getJointMarginal(vars2, vals3);
        assertEquals(0.8888, p3, 0.0001);

        System.out.println("p3 = " + p3);
    }


    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestUpdaterJointMarginal.class);
    }
}





