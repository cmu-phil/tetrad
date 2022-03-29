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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestSemIm {

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(49489384L);
        final Graph graph = constructGraph1();
        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);

        final Node x1 = graph.getNode("X1");
        final Node x2 = graph.getNode("X2");
        semIm.setEdgeCoef(x1, x2, 100.0);
        assertEquals(100.0, semIm.getEdgeCoef(x1, x2), 0.1);

        semIm.setErrCovar(x1, x1, 25.0);
        assertEquals(1.35, semIm.getErrVar(x1), 0.1);
    }

    @Test
    public void test3() {
        final Graph graph = constructGraph1();
        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);

        final DataSet dataSetContColumnContinuous =
                semIm.simulateData(500, false);
        final ICovarianceMatrix covMatrix =
                new CovarianceMatrix(dataSetContColumnContinuous);
        final SemEstimator estimator2 = new SemEstimator(covMatrix, semPm);
        estimator2.estimate();
        estimator2.getEstimatedSem();

        final SemEstimator estimator3 = new SemEstimator(covMatrix, semPm);
        estimator3.estimate();
        estimator3.getEstimatedSem();

        final SemPm semPm4 = new SemPm(graph);
        final SemEstimator estimator4 = new SemEstimator(covMatrix, semPm4);
        estimator4.estimate();
        estimator4.getEstimatedSem();

        final SemPm semPm5 = new SemPm(graph);
        final SemEstimator estimator5 = new SemEstimator(covMatrix, semPm5);
        estimator5.estimate();
        estimator5.getEstimatedSem();
    }

    @Test
    public void testCovariancesOfSimulated() {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Graph randomGraph = new Dag(GraphUtils.randomGraph(nodes, 0, 8, 30, 15, 15, false));
        final SemPm semPm1 = new SemPm(randomGraph);
        final SemIm semIm1 = new SemIm(semPm1);

        final Matrix implCovarC = semIm1.getImplCovar(true);
        implCovarC.toArray();

        final DataSet dataSet = semIm1.simulateDataRecursive(1000, false);
        new CovarianceMatrix(dataSet);
    }

    @Test
    public void testIntercepts() {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Graph randomGraph = new Dag(GraphUtils.randomGraph(nodes, 0, 8, 30, 15, 15, false));
        final SemPm semPm = new SemPm(randomGraph);
        final SemIm semIm = new SemIm(semPm);

        semIm.setIntercept(semIm.getVariableNodes().get(0), 1.0);
        semIm.setIntercept(semIm.getVariableNodes().get(1), 3.0);
        semIm.setIntercept(semIm.getVariableNodes().get(2), -1.0);
        semIm.setIntercept(semIm.getVariableNodes().get(3), 6.0);

        assertEquals(1.0, semIm.getIntercept(semIm.getVariableNodes().get(0)), 0.1);
        assertEquals(3.0, semIm.getIntercept(semIm.getVariableNodes().get(1)), 0.1);
        assertEquals(-1.0, semIm.getIntercept(semIm.getVariableNodes().get(2)), 0.1);
        assertEquals(6.0, semIm.getIntercept(semIm.getVariableNodes().get(3)), 0.1);
        assertEquals(0.0, semIm.getIntercept(semIm.getVariableNodes().get(4)), 0.1);
    }

    /**
     * The Cholesky decomposition of a symmetric, positive definite matrix
     * multiplied by the transpose of the Cholesky decomposition should be equal
     * to the original matrix itself.
     */
    @Test
    public void testCholesky() {
        final Graph graph = constructGraph2();
        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);

        final DataSet dataSet = semIm.simulateData(500, false);

        final Matrix data = dataSet.getDoubleData();
        final ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
        final double[][] a = cov.getMatrix().toArray();

        final double[][] l = MatrixUtils.cholesky(new Matrix(a)).toArray();
        final double[][] lT = MatrixUtils.transpose(l);
        final double[][] product = MatrixUtils.product(l, lT);

        assertTrue(MatrixUtils.equals(a, product, 1.e-10));
    }

    @Test
    public void test5() {
        final Graph graph = new EdgeListGraph();

        final Node x = new GraphNode("X");
        final Node y = new GraphNode("Y");
        final Node z = new GraphNode("Z");

        graph.addNode(x);
        graph.addNode(y);
        graph.addNode(z);

        final Node lx = new GraphNode("LX");
        lx.setNodeType(NodeType.LATENT);
        final Node ly = new GraphNode("LY");
        ly.setNodeType(NodeType.LATENT);
        final Node lz = new GraphNode("LZ");
        lz.setNodeType(NodeType.LATENT);

        graph.addNode(lx);
        graph.addNode(ly);
        graph.addNode(lz);

        graph.addDirectedEdge(lx, x);
        graph.addDirectedEdge(ly, y);
        graph.addDirectedEdge(lz, z);

        graph.addDirectedEdge(lx, ly);
        graph.addDirectedEdge(ly, lz);

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);

//        DataSet data = im.simulateDataCholesky(1000, true);
        final DataSet data = im.simulateDataReducedForm(1000, true);
//        DataSet data = im.simulateDataRecursive(1000, true);

        data.getCovarianceMatrix();

        im.getImplCovar(true);
    }

    @Test
    public void test6() {

        // X1 = e1
        // X2 = aX1 + e2

        final Matrix B = new Matrix(2, 2);
        B.set(0, 0, 0);
        B.set(0, 1, 0);
        B.set(1, 0, 5);
        B.set(1, 1, 0);

        final Matrix I = TetradAlgebra.identity(2);
        final Matrix iMinusB = TetradAlgebra.identity(2).minus(B);
        final Matrix reduced = iMinusB.inverse();
        final Vector e = new Vector(2);

        e.set(0, 0.5);
        e.set(1, -2);

        final Vector x = reduced.times(e);
        Vector d1 = B.times(x);
//        d1.assign(e, PlusMult.plusMult(1));
        d1 = d1.plus(e);
    }

    private Graph constructGraph1() {
        final Graph graph = new EdgeListGraph();

        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");
        final Node x5 = new GraphNode("X5");

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
        final Graph graph = new EdgeListGraph();

        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");
        final Node x5 = new GraphNode("X5");

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

    @Test
    public void test7() {
        final Graph graph = new EdgeListGraph();

        final Node z = new GraphNode("z");
        final Node p = new GraphNode("P");
        final Node y = new GraphNode("Y");
        final Node x = new GraphNode("X");

        graph.addNode(z);
        graph.addNode(p);
        graph.addNode(y);
        graph.addNode(x);

        graph.addDirectedEdge(z, p);
        graph.addDirectedEdge(p, y);
        graph.addDirectedEdge(x, p);
        graph.addDirectedEdge(y, x);

        final SemPm pm = new SemPm(graph);

        final SemIm im = new SemIm(pm);

        im.setEdgeCoef(z, p, 0.5);
        im.setEdgeCoef(p, y, 0.5);
        im.setEdgeCoef(x, p, 0.5);
        im.setEdgeCoef(y, x, 0.5);

        final DataSet data = im.simulateData(1000, false);

        final SemEstimator est = new SemEstimator(data, pm, new SemOptimizerPowell());

        final SemIm estSem = est.estimate();

        estSem.getBicScore();
    }

    @Test
    public void test8() {
        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");

        final Graph g = new SemGraph();
        g.addNode(x1);
        g.addNode(x2);
        g.addNode(x3);
        g.addNode(x4);

        g.addDirectedEdge(x1, x4);
        g.addDirectedEdge(x2, x4);
        g.addDirectedEdge(x3, x4);
        g.addBidirectedEdge(x1, x2);

        final SemPm semPm = new SemPm(g);
        final Parameters params = new Parameters();
        final SemIm semIm = new SemIm(semPm, params);

        final SemIm modified = TestSemIm.modifySemImStandardizedInterventionOnTargetParents(semIm, x4);

        modified.simulateData(1000, false);

    }

    public static SemIm modifySemImStandardizedInterventionOnTargetParents(final SemIm semIm, final Node
            target) {
        final SemIm modifiedSemIm = new SemIm(semIm);
        final SemGraph graph = new SemGraph(modifiedSemIm.getSemPm().getGraph());

        // remove <--> arrows from a copy of the graph so we can use the getParents function to get Nodes with edges into target
        final SemGraph removedDoubleArrowEdges = new SemGraph(graph);
        final ArrayList<Edge> edgesToRemove = new ArrayList<>();
        for (final Edge e : removedDoubleArrowEdges.getEdges()) {
            if ((e.getEndpoint1().equals(Endpoint.ARROW)) &&
                    (e.getEndpoint2().equals(Endpoint.ARROW))) {
                edgesToRemove.add(e);
            }
        }
        for (final Edge e : edgesToRemove) {
            removedDoubleArrowEdges.removeEdge(e);
        }

        final ArrayList<Node> targetParents = new
                ArrayList<>(removedDoubleArrowEdges.getParents(removedDoubleArrowEdges.getNode(target.getName())));

        final SemEvidence semEvidence = new SemEvidence(modifiedSemIm);
        for (final Node n : targetParents) {
            semEvidence.setManipulated(semEvidence.getNodeIndex(n.getName()),
                    true);
        }
        final SemUpdater semUpdater = new SemUpdater(modifiedSemIm);
        semUpdater.setEvidence(semEvidence);
        final SemIm modifiedAndUpdatedSemIm = new
                SemIm(semUpdater.getUpdatedSemIm());

        for (final Node n : targetParents) {
            modifiedAndUpdatedSemIm.setErrVar(modifiedAndUpdatedSemIm.getVariableNode(n.getName()),
                    1.0);
            modifiedAndUpdatedSemIm.setMean(modifiedAndUpdatedSemIm.getVariableNode(n.getName()),
                    0.0);
        }

        double varianceToAddToTargetAfterEdgeRemoval = 0.0;
        for (final Node n : targetParents) {
            final ArrayList<Node> nodesIntoTarget = new
                    ArrayList<>(graph.getNodesInTo(graph.getNode(target.getName()),
                    Endpoint.ARROW));

            for (final Node nodeIntoTarget : nodesIntoTarget) {
                final ArrayList<Edge> edgesConnectingParentAndTarget = new
                        ArrayList<>(modifiedAndUpdatedSemIm.getSemPm().getGraph().getEdges(modifiedAndUpdatedSemIm.getVariableNode(nodeIntoTarget.getName()),
                        modifiedAndUpdatedSemIm.getVariableNode(target.getName())));
                if (edgesConnectingParentAndTarget.size() > 1) {
                    for (final Edge e : edgesConnectingParentAndTarget) {
                        if ((e.getEndpoint1().equals(Endpoint.ARROW)) &&
                                (e.getEndpoint2().equals(Endpoint.ARROW))) {
                            final Edge directedEdge1 = new
                                    Edge(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                    modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()),
                                    Endpoint.TAIL, Endpoint.ARROW);
                            double directedEdgeCoef = 0.0;
                            if (edgesConnectingParentAndTarget.contains(directedEdge1)) {
                                directedEdgeCoef =
                                        modifiedAndUpdatedSemIm.getEdgeCoef(modifiedAndUpdatedSemIm.getVariableNode(e.getNode1().getName()),
                                                modifiedAndUpdatedSemIm.getVariableNode(e.getNode2().getName()));
                            } else {
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
                        }
                    }
                }
            }
        }
        final double oldTargetVariance =
                modifiedAndUpdatedSemIm.getErrVar(modifiedAndUpdatedSemIm.getVariableNode(target.getName()));
        modifiedAndUpdatedSemIm.setErrVar(modifiedAndUpdatedSemIm.getVariableNode(target.getName()),
                (oldTargetVariance + varianceToAddToTargetAfterEdgeRemoval));

        return modifiedAndUpdatedSemIm;
    }
}





