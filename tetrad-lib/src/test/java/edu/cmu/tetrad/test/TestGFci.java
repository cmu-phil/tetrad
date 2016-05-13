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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestGFci {

    public void test1() {
        RandomUtil.getInstance().setSeed(1450189593459L);

        int numNodes = 10;
        int numLatents = 5;
        int numEdges = 10;
        int sampleSize = 1000;

//        int numNodes = 3000;
//        int numLatents = 150;
//        int numEdges = 4500;
//        int sampleSize = 1000;

        double alpha = 0.01;
        double penaltyDiscount = 2;
        int depth = -1;
        int maxPathLength = -1;
        boolean possibleDsepDone = true;
        boolean completeRuleSetUsed = false;
        boolean faithfulnessAssumed = true;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = GraphUtils.randomGraphUniform(vars, numLatents, numEdges, 4, 4, 4, false);
//        Graph dag = GraphUtils.randomGraphRandomForwardEdges1(vars, numLatents, numEdges);
//        Graph dag = DataGraphUtils.scaleFreeGraph(vars, numLatents, .05, .05, .05, 3);

        DataSet data;

        LargeSemSimulator simulator = new LargeSemSimulator(dag);
        simulator.setCoefRange(.5, 1.5);
        simulator.setVarRange(1, 3);
        data = simulator.simulateDataAcyclic(sampleSize);
        data = DataUtils.restrictToMeasured(data);

        ICovarianceMatrix cov = new CovarianceMatrix(data);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);

        independenceTest.setAlpha(alpha);

        GFci gFci = new GFci(independenceTest);
        gFci.setVerbose(false);
        gFci.setPenaltyDiscount(penaltyDiscount);
        gFci.setMaxIndegree(depth);
        gFci.setMaxPathLength(maxPathLength);
//        gFci.setPossibleDsepSearchDone(possibleDsepDone);
        gFci.setCompleteRuleSetUsed(completeRuleSetUsed);
        gFci.setFaithfulnessAssumed(faithfulnessAssumed);
        Graph outGraph = gFci.search();

        final DagToPag dagToPag = new DagToPag(dag);
        dagToPag.setCompleteRuleSetUsed(false);
        dagToPag.setMaxPathLength(maxPathLength);
        Graph truePag = dagToPag.convert();

        outGraph = GraphUtils.replaceNodes(outGraph, truePag.getNodes());

        int[][] counts = SearchGraphUtils.graphComparison(outGraph, truePag, null);

        int[][] expectedCounts = {
                {0, 0, 0, 0, 0, 0},
                {0, 4, 0, 0, 0, 1},
                {0, 0, 3, 0, 0, 1},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }

//        System.out.println(MatrixUtils.toString(counts));
//        System.out.println(MatrixUtils.toString(expectedCounts));
    }

    @Test
    public void test2() {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node L = new GraphNode("L");
        L.setNodeType(NodeType.LATENT);

        Graph g1 = new EdgeListGraph();
        g1.addNode(x1);
        g1.addNode(x2);
        g1.addNode(x3);
        g1.addNode(x4);
        g1.addNode(L);

        g1.addDirectedEdge(x1, x2);
        g1.addDirectedEdge(x4, x3);
        g1.addDirectedEdge(L, x2);
        g1.addDirectedEdge(L, x3);

        GFci gfci = new GFci(new IndTestDSep(g1));

        Graph pag = gfci.search();

        Graph truePag = new EdgeListGraph();

        truePag.addNode(x1);
        truePag.addNode(x2);
        truePag.addNode(x3);
        truePag.addNode(x4);

        truePag.addPartiallyOrientedEdge(x1, x2);
        truePag.addBidirectedEdge(x2, x3);
        truePag.addPartiallyOrientedEdge(x4, x3);

//        System.out.println(pag);

        assertEquals(pag, truePag);
    }

    @Test
    public void testFromGraph() {
        RandomUtil.getInstance().setSeed(new Date().getTime());

        int numNodes = 20;
        int numLatents = 5;
        int numIterations = 20;

        boolean completeRuleSetUsed = false;
        boolean faithfulnessAssumed = true;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            Graph dag = GraphUtils.randomGraph(numNodes, numLatents, numNodes,
                    10, 10, 10, false);

            GFci gfci = new GFci(new GraphScore(dag));
            gfci.setCompleteRuleSetUsed(completeRuleSetUsed);
//            GFci gfci = new GFci(new IndTestDSep(dag));
            gfci.setFaithfulnessAssumed(faithfulnessAssumed);
            Graph pattern1 = gfci.search();
            DagToPag2 dagToPag2 = new DagToPag2(dag);
            dagToPag2.setCompleteRuleSetUsed(completeRuleSetUsed);
            Graph pattern2 = dagToPag2.convert();

//            System.out.println(pattern1);
//            System.out.println(pattern2);
//
//            System.out.println(MisclassificationUtils.edgeMisclassifications(pattern1, pattern2));
            assertEquals(pattern2, pattern1);
        }
    }

    @Test
    public void testFromData() {
        int numNodes = 100;
        int numLatents = 50;
        int numEdges = 100;
        int sampleSize = 1000;

//        System.out.println(RandomUtil.getInstance().getSeed());
//
//        RandomUtil.getInstance().setSeed(1461186701390L);


        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            variables.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = GraphUtils.randomGraphRandomForwardEdges(variables, numLatents, numEdges, 10, 10, 10, false, false);

        LargeSemSimulator semSimulator = new LargeSemSimulator(g);

        DataSet data = semSimulator.simulateDataAcyclic(sampleSize);

        data = DataUtils.restrictToMeasured(data);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
        score.setPenaltyDiscount(4);
        GFci gFci = new GFci(score);
        gFci.setFaithfulnessAssumed(true);

        long start = System.currentTimeMillis();

        Graph graph = gFci.search();

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");

        DagToPag2 dagToPag2 = new DagToPag2(g);
        dagToPag2.setVerbose(true);
        System.out.println(MisclassificationUtils.edgeMisclassifications(graph, dagToPag2.convert()));

    }
}





