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
import edu.cmu.tetrad.bayes.EmBayesEstimator;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Frank Wimberly
 */
public final class TestEmBayesEstimator extends TestCase {

    public TestEmBayesEstimator(String name) {
        super(name);
    }


    public void setUp() {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }


    public static void rtestEstimate1() {

        Dag graph = new Dag();
        Node L1 = new GraphNode("L1");
        Node X1 = new GraphNode("X1");
        Node X2 = new GraphNode("X2");
//        Node X3 = new GraphNode("X3");

        L1.setNodeType(NodeType.LATENT);
        X1.setNodeType(NodeType.MEASURED);
        X2.setNodeType(NodeType.MEASURED);
//        X3.setNodeType(NodeType.MEASURED);

        graph.addNode(L1);
        graph.addNode(X1);
        graph.addNode(X2);
//        graph.addIndex(X3);

        graph.addDirectedEdge(L1, X1);
        graph.addDirectedEdge(L1, X2);
//        graph.addDirectedEdge(X1, X3);
//        graph.addDirectedEdge(X2, X3);

        BayesPm pm = new BayesPm(graph);
        pm.setNumCategories(L1, 2);
        pm.setNumCategories(X1, 2);
        pm.setNumCategories(X2, 2);
//        pm.setNumCategories(X3, 2);

        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        DataSet dataSet = im.simulateData(1000, false);

        EmBayesEstimator emb = new EmBayesEstimator(im, dataSet);
        emb.expectationOnly();
        BayesIm estimatedIm = emb.getEstimatedIm();

        System.out.println(im);
        System.out.println(estimatedIm);
    }

    public void rtest2() {
        Dag graph = new Dag();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");

        x2.setNodeType(NodeType.LATENT);

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.addDirectedEdge(x2, x1);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x3);

        BayesPm pm = new BayesPm(graph);
        MlBayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        System.out.println(im);

        DataSet data = im.simulateData(1000, false);

        EmBayesEstimator estimator = new EmBayesEstimator(pm, data);
        estimator.expectationOnly();
        estimator.maximization(0.0001);

        System.out.println(estimator.getEstimatedIm());

    }

    public void testTemp() {
        //blank.
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.                                                                       7
        return new TestSuite(TestEmBayesEstimator.class);
    }
}





