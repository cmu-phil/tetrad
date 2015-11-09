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

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradAlgebra;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestSemIm extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemIm(String name) {
        super(name);
    }

    /**
     * Tests whether the the correlation matrix of a simulated sample is close
     * to the implied covariance matrix.
     */
    public void rtestSampleVsImpliedCorrlelations() {
        Graph randomGraph = GraphUtils.randomGraph(5, 8, false);
        SemPm semPm1 = new SemPm(randomGraph);
        SemIm semIm1 = new SemIm(semPm1);
//        System.out.println("semIm1 = " + semIm1);

        DataSet DataSet = semIm1.simulateDataReducedForm(1000, false);
        SemEstimator semEstimator = new SemEstimator(DataSet, semPm1);
        semEstimator.estimate();
        ICovarianceMatrix covMatrix = new CovarianceMatrix(DataSet);
        System.out.println("covMatrix = " + covMatrix);
        TetradMatrix implCovarC =
                semEstimator.getEstimatedSem().getImplCovar(true);
        double[][] implCovar = implCovarC.toArray();
        System.out.println("Implied covariance matrix:");
        System.out.println(MatrixUtils.toString(implCovar));
    }

    public void rtest2() {
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);
        System.out.println(semIm);

        Node x1 = graph.getNode("X1");
        Node x2 = graph.getNode("X2");
        semIm.setEdgeCoef(x1, x2, 100.0);
        assertEquals(100.0, semIm.getEdgeCoef(x1, x2));

        semIm.setErrCovar(x1, x1, 25.0);
        assertEquals(25.0, semIm.getErrVar(x1));
    }

    public void test3() {
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

        System.out.println("Original SemIm: " + semIm);

        DataSet dataSetContColumnContinuous =
                semIm.simulateData(500, false);
        ICovarianceMatrix covMatrix =
                new CovarianceMatrix(dataSetContColumnContinuous);
        SemEstimator estimator2 = new SemEstimator(covMatrix, semPm);
        estimator2.estimate();
        SemIm semIm2 = estimator2.getEstimatedSem();

        System.out.println("\nEstimated Sem #1: " + semIm2);

        SemEstimator estimator3 = new SemEstimator(covMatrix, semPm);
        estimator3.estimate();
        SemIm semIm3 = estimator3.getEstimatedSem();

        System.out.println("\nEstimated Sem #2: " + semIm3);

        SemPm semPm4 = new SemPm(graph);
        SemEstimator estimator4 = new SemEstimator(covMatrix, semPm4);
        estimator4.estimate();
        SemIm semIm4 = estimator4.getEstimatedSem();

        System.out.println("\nEstimated Sem #3: " + semIm4);

        SemPm semPm5 = new SemPm(graph);
        SemEstimator estimator5 = new SemEstimator(covMatrix, semPm5);
        estimator5.estimate();
        SemIm semIm5 = estimator5.getEstimatedSem();

        System.out.println("\nEstimated Sem #4: " + semIm5);
    }

    public void testCovariancesOfSimulated() {

        Graph randomGraph = new Dag(GraphUtils.randomGraph(5, 8, false));
        SemPm semPm1 = new SemPm(randomGraph);
        SemIm semIm1 = new SemIm(semPm1);

        TetradMatrix implCovarC = semIm1.getImplCovar(true);
        double[][] impliedCovar = implCovarC.toArray();
        System.out.println("Implied covar of semIm = " +
                MatrixUtils.toString(impliedCovar));

        DataSet dataSet = semIm1.simulateDataRecursive(1000, false);
        ICovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        System.out.println(
                "Covariance matrix of simulated data = " + covMatrix);
    }

    public void testIntercepts() {

        Graph randomGraph = new Dag(GraphUtils.randomGraph(5, 8, false));
        SemPm semPm = new SemPm(randomGraph);
        SemIm semIm = new SemIm(semPm);

        printIntercepts(semIm);
        semIm.setIntercept(semIm.getVariableNodes().get(0), 1.0);
        printIntercepts(semIm);
        semIm.setIntercept(semIm.getVariableNodes().get(1), 3.0);
        printIntercepts(semIm);
        semIm.setIntercept(semIm.getVariableNodes().get(2), -1.0);
        printIntercepts(semIm);
        semIm.setIntercept(semIm.getVariableNodes().get(3), 6.0);
        printIntercepts(semIm);

        assertEquals(1.0, semIm.getIntercept(semIm.getVariableNodes().get(0)));
        assertEquals(3.0, semIm.getIntercept(semIm.getVariableNodes().get(1)));
        assertEquals(-1.0, semIm.getIntercept(semIm.getVariableNodes().get(2)));
        assertEquals(6.0, semIm.getIntercept(semIm.getVariableNodes().get(3)));
        assertEquals(0.0, semIm.getIntercept(semIm.getVariableNodes().get(4)));

        System.out.println(semIm);
    }

//    public void rtest9() {
//        try {
//            File file = new File("test_data/bigsemtest.txt");
//            PrintWriter out = new PrintWriter(new FileWriter(file));
//            int numVariables = 10000;
//            int numRecords = 500;
//            NumberFormat nf = new DecimalFormat("0.0000");
//
//            for (int j = 0; j < numVariables; j++) {
//                out.print("V" + j + "\t");
//            }
//
//            out.println();
//
//            for (int i = 0; i < numRecords; i++) {
//                for (int j = 0; j < numVariables; j++) {
//                    out.print(nf.format(RandomUtil.getInstance().nextDouble()) + "\t");
//                }
//
//                out.println();
//            }
//
//            out.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private void printIntercepts(SemIm semIm) {
        System.out.println();
        for (int i = 0; i < 5; i++) {
            Node node = semIm.getVariableNodes().get(i);
            System.out.println("Intercept of " + node + " = " + semIm.getIntercept(node));
        }
    }

    /**
     * The Cholesky decomposition of a symmetric, positive definite matrix
     * multiplied by the transpose of the Cholesky decomposition should be equal
     * to the original matrix itself.
     */
    public void testCholesky() {
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

        System.out.println("Original SemIm: " + semIm);

        DataSet dataSet = semIm.simulateData(500, false);

        TetradMatrix data = dataSet.getDoubleData();

        System.out.println("Data = ");
        System.out.println(data);

        double[][] a = new double[data.columns()][data.columns()];

        for (int i = 0; i < data.columns(); i++) {
            for (int j = 0; j < data.columns(); j++) {
                DoubleArrayList icol =
                        new DoubleArrayList(data.getColumn(i).toArray());
                DoubleArrayList jcol =
                        new DoubleArrayList(data.getColumn(j).toArray());
                a[i][j] = Descriptive.covariance(icol, jcol);
            }
        }

        System.out.println("A = ");
        System.out.println(MatrixUtils.toString(a));

        System.out.println("L = ");
        double[][] l = MatrixUtils.cholesky(a);
        System.out.println(MatrixUtils.toString(l));

        System.out.println("L' = ");
        double[][] lT = MatrixUtils.transpose(l);
        System.out.println(MatrixUtils.toString(lT));

        System.out.println("L L' = ");
        double[][] product = MatrixUtils.product(l, lT);
        System.out.println(MatrixUtils.toString(product));

        assertTrue(MatrixUtils.equals(a, product, 1.e-10));
    }

    public void test5() {
        Graph graph = new EdgeListGraph();

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");

        graph.addNode(x);
        graph.addNode(y);
        graph.addNode(z);

        Node lx = new GraphNode("LX");
        lx.setNodeType(NodeType.LATENT);
        Node ly = new GraphNode("LY");
        ly.setNodeType(NodeType.LATENT);
        Node lz = new GraphNode("LZ");
        lz.setNodeType(NodeType.LATENT);

        graph.addNode(lx);
        graph.addNode(ly);
        graph.addNode(lz);

        graph.addDirectedEdge(lx, x);
        graph.addDirectedEdge(ly, y);
        graph.addDirectedEdge(lz, z);

        graph.addDirectedEdge(lx, ly);
        graph.addDirectedEdge(ly, lz);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

//        DataSet data = im.simulateDataCholesky(1000, true);
        DataSet data = im.simulateDataReducedForm(1000, true);
//        DataSet data = im.simulateDataRecursive(1000, true);

        TetradMatrix cov = data.getCovarianceMatrix();

        TetradMatrix impliedCov = im.getImplCovar(true);

        System.out.println("cov = " + cov);
        System.out.println("implied cov = " + impliedCov);
    }

    public void test6() {

        // X1 = e1
        // X2 = aX1 + e2

        TetradMatrix B = new TetradMatrix(2, 2);
        B.set(0, 0, 0);
        B.set(0, 1, 0);
        B.set(1, 0, 5);
        B.set(1, 1, 0);

        System.out.println("B = " + B);

        TetradMatrix I = TetradAlgebra.identity(2);

        System.out.println("I = " + I);

//        TetradMatrix iMinusB = I.copy().assign(B, PlusMult.plusMult(-1));

        TetradMatrix iMinusB = TetradAlgebra.identity(2).minus(B);

        System.out.println("iMinusB = " + iMinusB);

        TetradMatrix reduced = iMinusB.inverse();

        System.out.println("reduced form = " + reduced);


        TetradVector e = new TetradVector(2);

        e.set(0, 0.5);
        e.set(1, -2);

        System.out.println("e = " + e);

        TetradVector x = reduced.times(e);

        System.out.println(x);

        TetradVector d1 = B.times(x);
//        d1.assign(e, PlusMult.plusMult(1));
        d1 = d1.plus(e);

        System.out.println("check x = " + x);

    }

    private Graph constructGraph1() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        x1.setNodeType(NodeType.LATENT);
        x2.setNodeType(NodeType.LATENT);

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x4, x5);

        return graph;
    }

    private Graph constructGraph2() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x4, x5);

        return graph;
    }

    public void test7() {
        Graph graph = new EdgeListGraph();

        Node z = new GraphNode("z");
        Node p = new GraphNode("P");
        Node y = new GraphNode("Y");
        Node x = new GraphNode("X");

        graph.addNode(z);
        graph.addNode(p);
        graph.addNode(y);
        graph.addNode(x);

        graph.addDirectedEdge(z, p);
        graph.addDirectedEdge(p, y);
        graph.addDirectedEdge(x, p);
        graph.addDirectedEdge(y, x);

        SemPm pm = new SemPm(graph);

        SemIm im = new SemIm(pm);

        im.setEdgeCoef(z, p, 0.5);
        im.setEdgeCoef(p, y, 0.5);
        im.setEdgeCoef(x, p, 0.5);
        im.setEdgeCoef(y, x, 0.5);

        DataSet data = im.simulateData(1000, false);

        SemEstimator est = new SemEstimator(data, pm, new SemOptimizerPowell());

        SemIm estSem = est.estimate();

        double bic = estSem.getBicScore();

        System.out.println(bic);
    }

    public void test8() {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        Graph g = new SemGraph();
        g.addNode(x1);
        g.addNode(x2);
        g.addNode(x3);
        g.addNode(x4);

        g.addDirectedEdge(x1, x4);
        g.addDirectedEdge(x2, x4);
        g.addDirectedEdge(x3, x4);
        g.addBidirectedEdge(x1, x2);

        SemPm semPm = new SemPm(g);
        SemImInitializationParams params = new SemImInitializationParams();
        SemIm semIm = new SemIm(semPm, params);

        SemIm modified = modifySemImStandardizedInterventionOnTargetParents(semIm, x4);

        DataSet data = modified.simulateData(1000, false);

    }

    public void test10() {
        int numNodes = 1000;
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = new EdgeListGraph(nodes);
//        SemPm pm = new SemPm(g);
//        SemIm im = new SemIm(pm);
//        DataSet d = im.simulateData(1000, false);

        LargeSemSimulator ls = new LargeSemSimulator(g);
        ls.simulateDataAcyclic(1000);

    }

    public static SemIm modifySemImStandardizedInterventionOnTargetParents(SemIm semIm, Node
            target) {
        SemIm modifiedSemIm = new SemIm(semIm);
        SemGraph graph = new SemGraph(modifiedSemIm.getSemPm().getGraph());

        // remove <--> arrows from a copy of the graph so we can use the getParents function to get Nodes with edges into target
        SemGraph removedDoubleArrowEdges = new SemGraph(graph);
        ArrayList<Edge> edgesToRemove = new ArrayList<Edge>();
        for(Edge e : removedDoubleArrowEdges.getEdges()) {
            if((e.getEndpoint1().equals(Endpoint.ARROW)) &&
                    (e.getEndpoint2().equals(Endpoint.ARROW))) {
                edgesToRemove.add(e);
            }
        }
        for(Edge e : edgesToRemove) {
            removedDoubleArrowEdges.removeEdge(e);
        }

        ArrayList<Node> targetParents = new
                ArrayList<Node>(removedDoubleArrowEdges.getParents(removedDoubleArrowEdges.getNode(target.getName())));

        System.out.println("ORIGINAL GRAPH");
        System.out.println(graph);

        SemEvidence semEvidence = new SemEvidence(modifiedSemIm);
        for(Node n : targetParents) {
            semEvidence.setManipulated(semEvidence.getNodeIndex(n.getName()),
                    true);
        }
        SemUpdater semUpdater = new SemUpdater(modifiedSemIm);
        semUpdater.setEvidence(semEvidence);
        SemIm modifiedAndUpdatedSemIm = new
                SemIm(semUpdater.getUpdatedSemIm());

        for(Node n : targetParents) {
            modifiedAndUpdatedSemIm.setErrVar(modifiedAndUpdatedSemIm.getVariableNode(n.getName()),
                    1.0);
            modifiedAndUpdatedSemIm.setMean(modifiedAndUpdatedSemIm.getVariableNode(n.getName()),
                    0.0);
        }

        double varianceToAddToTargetAfterEdgeRemoval = 0.0;
        for(Node n : targetParents) {
            ArrayList<Node> nodesIntoTarget = new
                    ArrayList<Node>(graph.getNodesInTo(graph.getNode(target.getName()),
                    Endpoint.ARROW));

            for(Node nodeIntoTarget : nodesIntoTarget) {
                ArrayList<Edge> edgesConnectingParentAndTarget = new
                        ArrayList<Edge>(modifiedAndUpdatedSemIm.getSemPm().getGraph().getEdges(modifiedAndUpdatedSemIm.getVariableNode(nodeIntoTarget.getName()),
                        modifiedAndUpdatedSemIm.getVariableNode(target.getName())));
                if(edgesConnectingParentAndTarget.size() > 1) {
                    for(Edge e : edgesConnectingParentAndTarget) {
                        System.out.println("Check Edge: " + e);
                        if((e.getEndpoint1().equals(Endpoint.ARROW)) &&
                                (e.getEndpoint2().equals(Endpoint.ARROW))) {
                            Edge directedEdge1 = new
                                    Edge(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                    modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()),
                                    Endpoint.TAIL, Endpoint.ARROW);
                            double directedEdgeCoef = 0.0;
                            if(edgesConnectingParentAndTarget.contains(directedEdge1))
                            {
                                directedEdgeCoef =
                                        modifiedAndUpdatedSemIm.getEdgeCoef(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                                modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()));
                            }
                            else {
                                directedEdgeCoef =
                                        modifiedAndUpdatedSemIm.getEdgeCoef(modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()),
                                                modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()));
                            }
                            varianceToAddToTargetAfterEdgeRemoval += (2 *
                                    directedEdgeCoef *
                                    modifiedAndUpdatedSemIm.getErrCovar(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                            modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName())));
                            modifiedAndUpdatedSemIm.setErrCovar(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                    modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()),
                                    0.0);
                            System.out.println("REMOVING EDGE: " + e);
                            modifiedAndUpdatedSemIm.getSemPm().getGraph().removeEdge(e);
                        }
                        else { System.out.println("DO NOTHING"); }
                    }
                }
            }
        }
        double oldTargetVariance =
                modifiedAndUpdatedSemIm.getErrVar(modifiedAndUpdatedSemIm.getVariableNode(target.getName()));
        modifiedAndUpdatedSemIm.setErrVar(modifiedAndUpdatedSemIm.getVariableNode(target.getName()),
                (oldTargetVariance + varianceToAddToTargetAfterEdgeRemoval));

        System.out.println("MODIFIED GRAPH");
        System.out.println(modifiedAndUpdatedSemIm.getSemPm().getGraph());
        return modifiedAndUpdatedSemIm;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSemIm.class);
    }
}





