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

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.EigenvalueDecomposition;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.GraphWithParameters;
import edu.cmu.tetrad.search.Ling;
import edu.cmu.tetrad.search.Lingam;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.dist.GaussianPower;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class TestLing extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestLing(String name) {
        super(name);
    }

    // test from gustavo's essay

    public void rtest() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
        g.addNode(new GraphNode("X5"));

        gWP.addEdge("X1", "X2", 1.2);
        gWP.addEdge("X2", "X3", 2);
        gWP.addEdge("X3", "X4", -1);
        gWP.addEdge("X4", "X2", -0.3);
        gWP.addEdge("X2", "X5", 3);

        System.out.println("Input Graph");
        System.out.println(gWP);

        Ling t = new Ling(gWP, 15000);
        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
            System.out.println("Data = " + gs.getData(i));
        }
    }

    public void test1_6() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
        g.addNode(new GraphNode("X5"));
//        g.addIndex(new GraphNode("T"));

        gWP.addEdge("X1", "X2", -1);
        gWP.addEdge("X2", "X3", .5);
        gWP.addEdge("X3", "X1", 1);

        gWP.addEdge("X1", "X3", .5);

        System.out.println("Input Graph");
        System.out.println(gWP);

        TetradMatrix b = gWP.getGraphMatrix().getDoubleData();
        boolean smaller = allEigenvaluesAreSmallerThanOneInModulus(b);
        System.out.println("All eigenvalues smaller than one in modulus: " + smaller);
    }

    public void test1_7() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
//        g.addIndex(new GraphNode("X5"));
//        g.addIndex(new GraphNode(""));

        gWP.addEdge("X1", "X2", 1);

        gWP.addEdge("X2", "X3", 1);
        gWP.addEdge("X3", "X1", .4999);

        gWP.addEdge("X2", "X4", 1);
        gWP.addEdge("X4", "X1", .4999);

        System.out.println("Input Graph");
        System.out.println(gWP);

        TetradMatrix b = gWP.getGraphMatrix().getDoubleData();
        boolean smaller = allEigenvaluesAreSmallerThanOneInModulus(b);
        System.out.println("All eidgenvalues smaller than one in modulus: " + smaller);
    }

    public void test1_8() {

        // This one confuses me.
        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
//        g.addIndex(new GraphNode("X5"));
//        g.addIndex(new GraphNode("X6"));

        gWP.addEdge("X2", "X1", 1);
        gWP.addEdge("X3", "X2", 1);

        gWP.addEdge("X1", "X4", 1);

        gWP.addEdge("X4", "X3", .25);
        gWP.addEdge("X4", "X2", 1.75);

        gWP.addEdge("X3", "X1", -9.9);

//        gWP.addEdge("X5", "X6", 1);
//        gWP.addEdge("X6", "X2", 1);

        System.out.println("Input Graph");
        System.out.println(gWP);

        TetradMatrix b = gWP.getGraphMatrix().getDoubleData();
        boolean smaller = allEigenvaluesAreSmallerThanOneInModulus(b);
        System.out.println("All eigenvalues smaller than one in modulus: " + smaller);
    }

    public void test1_9() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
        g.addNode(new GraphNode("X5"));
        g.addNode(new GraphNode("X6"));

        gWP.addEdge("X2", "X1", 1);
        gWP.addEdge("X2", "X3", 1);
        gWP.addEdge("X3", "X1", 1);

        gWP.addEdge("X4", "X5", 1);
        gWP.addEdge("X5", "X6", 1);
        gWP.addEdge("X6", "X4", .9);

        gWP.addEdge("X1", "X4", 1);

        System.out.println("Input Graph");
        System.out.println(gWP);

        TetradMatrix b = gWP.getGraphMatrix().getDoubleData();

        boolean smaller = allEigenvaluesAreSmallerThanOneInModulus(b);
        System.out.println("All eigenvalues smaller than one in modulus: " + smaller);


        for (int i = 0; i < 1000; i++) {
            for (Edge edge : gWP.getGraph().getEdges()) {
                gWP.getWeightHash().put(edge, 6 * RandomUtil.getInstance().nextDouble() - 3);
            }

            TetradMatrix b1 = gWP.getGraphMatrix().getDoubleData();
            boolean smaller1 = allEigenvaluesAreSmallerThanOneInModulus(b1);
            System.out.println("All eidgenvalues smaller than one in modulus: " + smaller1);
        }

    }

    // randomly generated cyclic graph

    public void rtest2() {
        Dag dag = new Dag(GraphUtils.randomGraph(6, 0, 7, 3,
                3, 4, false));
        Graph graph = GraphUtils.addCycles(dag, 3, 2);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        for (Node node : im.getSemPm().getGraph().getNodes()) {
            im.setDistribution(node, new GaussianPower(2));
        }

        DataSet data = im.simulateDataReducedForm(15000, false);

        System.out.println("Input Graph");
        System.out.println(graph);

//        Ling t = new Ling(data);
        Ling t = new Ling(new GraphWithParameters(im, graph), 15000);
        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
        }
    }

    public void rtest2_1() {
        Dag dag = new Dag(GraphUtils.randomGraph(8, 0, 8, 3,
                3, 4, false));
        Graph graph = GraphUtils.addCycles(dag, 1, 3);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        GraphWithParameters gWP = new GraphWithParameters(im, pm.getGraph());

        Ling t = new Ling(gWP, 15000);

        System.out.println(graph);

        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
        }
    }

    // simple loop test

    public void rtest3() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));

        gWP.addEdge("X1", "X2", 1);
        gWP.addEdge("X2", "X3", 2);
        gWP.addEdge("X3", "X4", -.5);
        gWP.addEdge("X4", "X1", 1);

        System.out.println("Input Graph");
        System.out.println(gWP);

        Ling t = new Ling(gWP, 15000);

//        t.setThreshold(.05);
        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
        }

    }

    // test for peter

    public void rtest4() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
        g.addNode(new GraphNode("X5"));
        g.addNode(new GraphNode("X6"));

        gWP.addEdge("X1", "X2", .5);
        gWP.addEdge("X3", "X4", -2);
        gWP.addEdge("X2", "X4", 1.4);
        gWP.addEdge("X4", "X2", -2);
        gWP.addEdge("X2", "X6", -1);
        gWP.addEdge("X4", "X5", -.7);
        gWP.addEdge("X5", "X6", .8);
        gWP.addEdge("X6", "X5", 1);


        System.out.println("Input Graph");
        System.out.println(gWP);

        Ling t = new Ling(gWP, 15000);
        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
        }

    }

    // test for peter

    public void rtest5() {

        Graph g = new EdgeListGraph();

        GraphWithParameters gWP = new GraphWithParameters(g);
        g.addNode(new GraphNode("X1"));
        g.addNode(new GraphNode("X2"));
        g.addNode(new GraphNode("X3"));
        g.addNode(new GraphNode("X4"));
        g.addNode(new GraphNode("X5"));
        g.addNode(new GraphNode("X6"));

        gWP.addEdge("X1", "X2", 1.5);
        gWP.addEdge("X2", "X4", .4);
        gWP.addEdge("X4", "X2", -1.1);
        gWP.addEdge("X2", "X6", -1);
        gWP.addEdge("X5", "X6", .8);
        gWP.addEdge("X6", "X5", 1);


        System.out.println("Input Graph");
        System.out.println(gWP);

        Ling t = new Ling(gWP, 15000);

//        t.setThreshold(.05);
        Ling.StoredGraphs gs = t.search();

        int zeroless = gs.getNumGraphs();

        System.out.println("There are " + zeroless + " zeroless diagonal permutations.");

        for (int i = 0; i < zeroless; i++) {
            System.out.println("Shrinking = " + gs.isStable(i));
            System.out.println("Graph = " + gs.getGraph(i));
        }

    }

    // benchmark1 of ling to lingam

    public void rtest6() {

        double avg = 0.0;
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {

            Dag dag = new Dag(GraphUtils.randomGraph(5, 0, 5, 3,
                    3, 4, false));
            Graph graph = GraphUtils.addCycles(dag, 1, 10);
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);

            Ling t = new Ling(new GraphWithParameters(im, graph), 15000);

            DataSet data = t.getData();

            long sTime, eTime, dif1, dif2;

            sTime = (new Date()).getTime();
            t.search();
            eTime = (new Date()).getTime();
            dif1 = eTime - sTime;

            Lingam t2 = new Lingam();
            sTime = (new Date()).getTime();
            t2.search(data);
            eTime = (new Date()).getTime();
            dif2 = eTime - sTime;

            avg += (double) dif1 / (double) dif2;
        }

        System.out.println("Average of the difference ratio is: " + (avg / (double) iterations));

    }

    public void rtest7() {
        try {
            DataReader reader = new DataReader();
            DataSet data = reader.parseTabular(new File("src/test/resources/roidata.txt"));
            Ling t = new Ling(data);
            t.setThreshold(0.5);
            t.search();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean allEigenvaluesAreSmallerThanOneInModulus(TetradMatrix b) {
        EigenvalueDecomposition dec = new EigenvalueDecomposition(new DenseDoubleMatrix2D(b.toArray()));
        DoubleMatrix1D realEigenvalues = dec.getRealEigenvalues();
        DoubleMatrix1D imagEigenvalues = dec.getImagEigenvalues();

        boolean allEigenvaluesSmallerThanOneInModulus = true;
        for (int i = 0; i < realEigenvalues.size(); i++) {
            double realEigenvalue = realEigenvalues.get(i);
            double imagEigenvalue = imagEigenvalues.get(i);
            double modulus = Math.sqrt(Math.pow(realEigenvalue, 2) + Math.pow(imagEigenvalue, 2));

            if (modulus >= 1) {
                allEigenvaluesSmallerThanOneInModulus = false;
            }
        }

        return allEigenvaluesSmallerThanOneInModulus;
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestLing.class);
    }

}


