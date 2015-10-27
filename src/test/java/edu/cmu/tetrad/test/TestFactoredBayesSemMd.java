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
import edu.cmu.tetrad.bayes.FactoredBayesStructuralEM;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;

/**
 * Test of the iterate method of the FactoredBayesStructuralEM class.
 *
 * @author Frank Wimberly </p> NOTE:  The string "SEM" here does not mean
 *         "Structural Equation Model" but "Structural Expectation
 *         Maximization".
 */
public final class TestFactoredBayesSemMd extends TestCase {

    public TestFactoredBayesSemMd(String name) {
        super(name);
    }

    //So that it will have a test method when the real test is "r-ed" out.
    public static void testNothing() {
        // Blank.
    }

    public static void rtestFbSemOrig() {

        try {
            String fileName = "src/test/resources/l1x1x2x3nolatent.dat";
            File file = new File(fileName);

            DataReader reader = new DataReader();
            reader.setMissingValueMarker("-99");
            DataSet ds = reader.parseTabular(file);

            Node l1 = new GraphNode("L1");
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");

            l1.setNodeType(NodeType.MEASURED);
            x1.setNodeType(NodeType.MEASURED);
            x2.setNodeType(NodeType.MEASURED);
            x3.setNodeType(NodeType.MEASURED);

            Dag graph = new Dag();

            // Add and remove some nodes.
            graph.addNode(l1);
            graph.addNode(x1);
            graph.addNode(x2);
            graph.addNode(x3);

            BayesPm bayesPm = new BayesPm(graph);
            bayesPm.setNumCategories(l1, 2);
            bayesPm.setNumCategories(x1, 2);
            bayesPm.setNumCategories(x2, 2);
            bayesPm.setNumCategories(x3, 2);

            FactoredBayesStructuralEM fbsem =
                    new FactoredBayesStructuralEM(ds, bayesPm);

            //The iterate method, called below, uses the BdeMetric class in an implementation of the
            //Procedure Factored-Bayesian-SEM in the paper "The Bayesian Structural EM
            //Algorithm" by Nir Friedman.  The iterate1 method uses the BdeMetricCache
            //class instead.  The latter class factors each of the models it searches over
            //exploiting the fact that different models may share factors whose score only
            //has to be computed and stored once.  Hence, the iterate and iterate1 methods
            //should return the same model with the same score but iterate1 should be more
            //efficient.

            BayesIm bestIm = fbsem.iterate();
            BayesPm bestPm = bestIm.getBayesPm();

            System.out.println(
                    "Graph of best model (output of FactoredBayesStructuralEM):  ");
            Graph bestGraph = bestPm.getDag();
            System.out.println(bestGraph);
            System.out.println(bestPm);

            //Graph gen is the graph of the generating model.  That is, if the
            //search algorithm is correct it will infer gen from the data.
            Graph genGraph = new Dag();

            genGraph.clear();

            // Add and remove some nodes.
            genGraph.addNode(l1);
            genGraph.addNode(x1);
            genGraph.addNode(x2);
            genGraph.addNode(x3);

            genGraph.addDirectedEdge(x1, l1);
            genGraph.addDirectedEdge(x2, x1);
            genGraph.addDirectedEdge(l1, x3);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void rtestFbSemMdw() {
        try {
            String fileName = "src/test/resources/structEM_MDW4000.dat";
            File file = new File(fileName);

            DataReader reader = new DataReader();
            reader.setMissingValueMarker("-99");

            DataSet dds = reader.parseTabular(file);

            double[] probs = {0.05, 0.05, 0.05, 0.05, 0.05};
            dds = DataUtils.addMissingData(dds, probs);

            Node A = new GraphNode("A");
            Node B = new GraphNode("B");
            Node C = new GraphNode("C");
            Node X = new GraphNode("X");
            Node Y = new GraphNode("Y");

            //L1.setNodeType(NodeType.LATENT);
            A.setNodeType(NodeType.MEASURED);
            B.setNodeType(NodeType.MEASURED);
            C.setNodeType(NodeType.MEASURED);
            X.setNodeType(NodeType.MEASURED);
            Y.setNodeType(NodeType.MEASURED);

            Dag graph = new Dag();

            // Add and remove some nodes.
            graph.addNode(A);
            graph.addNode(B);
            graph.addNode(C);
            graph.addNode(X);
            graph.addNode(Y);

            BayesPm bayesPm = new BayesPm(graph);
            bayesPm.setNumCategories(A, 2);
            bayesPm.setNumCategories(B, 2);
            bayesPm.setNumCategories(C, 2);
            bayesPm.setNumCategories(X, 2);
            bayesPm.setNumCategories(Y, 2);

            FactoredBayesStructuralEM fbsem =
                    new FactoredBayesStructuralEM(dds, bayesPm);

            //The iterate method, called below, uses the BdeMetric class in an implementation of the
            //Procedure Factored-Bayesian-SEM in the paper "The Bayesian Structural EM
            //Algorithm" by Nir Friedman.  The iterate1 method uses the BdeMetricCache
            //class instead.  The latter class factors each of the models it searches over
            //exploiting the fact that different models may share factors whose score only
            //has to be computed and stored once.  Hence, the iterate and iterate1 methods
            //should return the same model with the same score but iterate1 should be more
            //efficient.
            BayesIm bestIm = fbsem.maximization(0.0001);
            BayesPm bestPm = bestIm.getBayesPm();

            System.out.println(
                    "Graph of best model (output of FactoredBayesStructuralEM):  ");
            Graph bestGraph = bestPm.getDag();
            System.out.println(bestGraph);
            System.out.println(bestPm);

            //Graph genGraph is not quite the graph of the generating model.  It is,
            //however the best (i.e. highest scoring) model that the Structual Bayes EM
            //search produces for this dataset.
            Graph genGraph = new Dag();

            genGraph.clear();

            Node Ac = new GraphNode("A");
            Node Bc = new GraphNode("B");
            Node Cc = new GraphNode("C");
            Node Xc = new GraphNode("X");
            Node Yc = new GraphNode("Y");

            // Add and remove some nodes.

            genGraph.addNode(Ac);
            genGraph.addNode(Bc);
            genGraph.addNode(Cc);
            genGraph.addNode(Xc);
            genGraph.addNode(Yc);

            genGraph.addDirectedEdge(Ac, Bc);
            genGraph.addDirectedEdge(Ac, Xc);
            genGraph.addDirectedEdge(Bc, Xc);
            genGraph.addDirectedEdge(Bc, Cc);
            genGraph.addDirectedEdge(Yc, Bc);
            genGraph.addDirectedEdge(Yc, Cc);
            genGraph.addDirectedEdge(Yc, Xc);


            assertEquals(genGraph, bestGraph);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void rtestFbSemMdab() {
        try {
            String fileName = "src/test/resources/structEM_ABMD.dat";
            File file = new File(fileName);

            DataReader reader = new DataReader();
            reader.setMissingValueMarker("-99");
            DataSet ds = reader.parseTabular(file);

            Node A = new GraphNode("A");
            Node B = new GraphNode("B");

            //L1.setNodeType(NodeType.LATENT);
            A.setNodeType(NodeType.MEASURED);
            B.setNodeType(NodeType.MEASURED);

            Dag graph = new Dag();

            graph.clear();

            // Add and remove some nodes.
            graph.addNode(A);
            graph.addNode(B);

            //graph.addIndex(X4);
            //graph.addIndex(X5);

            //graph.addDirectedEdge(X1, X2);
            //graph.addDirectedEdge(X2, X3);
            //graph.addDirectedEdge(X3, X4);
            //graph.addDirectedEdge(X1, X3);
            //graph.addDirectedEdge(X2, X3);

            BayesPm bayesPm = new BayesPm(graph);

            bayesPm.setNumCategories(A, 2);
            bayesPm.setNumCategories(B, 2);

            FactoredBayesStructuralEM fbsem =
                    new FactoredBayesStructuralEM(ds, bayesPm);

            //The iterate method, called below, uses the BdeMetric class in an implementation of the
            //Procedure Factored-Bayesian-SEM in the paper "The Bayesian Structural EM
            //Algorithm" by Nir Friedman.  The iterate1 method uses the BdeMetricCache
            //class instead.  The latter class factors each of the models it searches over
            //exploiting the fact that different models may share factors whose score only
            //has to be computed and stored once.  Hence, the iterate and iterate1 methods
            //should return the same model with the same score but iterate1 should be more
            //efficient.

            //System.exit(0);    //Temporary until implementation finished.

            //BayesPm  bestPm = fbsem.iterate();
            //BayesPm bestPm = fbsem.iterate1();
            BayesIm bestIm = fbsem.iterate();
            BayesPm bestPm = bestIm.getBayesPm();

            System.out.println(
                    "Graph of best model (output of FactoredBayesStructuralEM):  ");
            Graph bestGraph = bestPm.getDag();
            System.out.println(bestGraph);
            System.out.println(bestPm);

            //Graph gen is the graph of the generating model.  That is, if the
            //search algorithm is correct it will infer gen from the data.
            Graph genGraph = new Dag();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static void rtestFbSemMdy() {
        try {
            String fileName = "src/test/resources/structEM_MDY.dat";
            File file = new File(fileName);

            DataReader reader = new DataReader();
            reader.setMissingValueMarker("-99");
            DataSet ds = reader.parseTabular(file);

            Node X = new GraphNode("X");
            Node Y = new GraphNode("Y");
            Node Z = new GraphNode("Z");
            Node W = new GraphNode("W");

            X.setNodeType(NodeType.MEASURED);
            Y.setNodeType(NodeType.MEASURED);
            Z.setNodeType(NodeType.MEASURED);
            W.setNodeType(NodeType.MEASURED);

            Dag graph = new Dag();

            graph.clear();

            // Add and remove some nodes.
            graph.addNode(X);
            graph.addNode(Y);
            graph.addNode(Z);
            graph.addNode(W);

            BayesPm bayesPm = new BayesPm(graph);

            bayesPm.setNumCategories(X, 2);
            bayesPm.setNumCategories(Y, 2);
            bayesPm.setNumCategories(Z, 2);
            bayesPm.setNumCategories(W, 2);

            FactoredBayesStructuralEM fbsem =
                    new FactoredBayesStructuralEM(ds, bayesPm);

            //The iterate method, called below, uses the BdeMetric class in an implementation of the
            //Procedure Factored-Bayesian-SEM in the paper "The Bayesian Structural EM
            //Algorithm" by Nir Friedman.  The iterate1 method uses the BdeMetricCache
            //class instead.  The latter class factors each of the models it searches over
            //exploiting the fact that different models may share factors whose score only
            //has to be computed and stored once.  Hence, the iterate and iterate1 methods
            //should return the same model with the same score but iterate1 should be more
            //efficient.
            BayesIm bestIm = fbsem.iterate();
            BayesPm bestPm = bestIm.getBayesPm();

            System.out.println(
                    "Graph of best model (output of FactoredBayesStructuralEM):  ");
            Graph bestGraph = bestPm.getDag();
            System.out.println(bestGraph);
            System.out.println(bestPm);

            //Graph gen is the graph of the generating model.  That is, if the
            //search algorithm is correct it will infer gen from the data.
            Graph genGraph = new Dag();
            genGraph.clear();

            // Add and remove some nodes.
            genGraph.addNode(X);
            genGraph.addNode(Y);
            genGraph.addNode(Z);
            genGraph.addNode(W);

            genGraph.addDirectedEdge(W, Z);
            assertEquals(genGraph, bestGraph);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestFactoredBayesSemMd.class);
    }
}





