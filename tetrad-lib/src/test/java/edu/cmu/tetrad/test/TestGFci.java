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
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDataReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

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

        double alpha = 0.01;
        double penaltyDiscount = 2;
        int depth = -1;
        int maxPathLength = -1;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = GraphUtils.randomGraphUniform(vars, numLatents, numEdges, 4, 4, 4, false);
//        Graph dag = GraphUtils.randomGraphRandomForwardEdges1(vars, numLatents, numEdges);
//        Graph dag = DataGraphUtils.scaleFreeGraph(vars, numLatents, .05, .05, .05, 3);

        DataSet data;

        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        simulator.setCoefRange(.5, 1.5);
        simulator.setVarRange(1, 3);
        data = simulator.simulateDataFisher(sampleSize);
        data = DataUtils.restrictToMeasured(data);

        ICovarianceMatrix cov = new CovarianceMatrix(data);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(penaltyDiscount);

        independenceTest.setAlpha(alpha);

        GFci gFci = new GFci(independenceTest, score);
        gFci.setVerbose(false);
        gFci.setMaxDegree(depth);
        gFci.setMaxPathLength(maxPathLength);
        gFci.setCompleteRuleSetUsed(false);
        gFci.setFaithfulnessAssumed(true);
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
            {0, 0, 0, 0, 0, 0},};

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

        GFci gfci = new GFci(new IndTestDSep(g1), new GraphScore(g1));

        Graph pag = gfci.search();

        Graph truePag = new EdgeListGraph();

        truePag.addNode(x1);
        truePag.addNode(x2);
        truePag.addNode(x3);
        truePag.addNode(x4);

        truePag.addPartiallyOrientedEdge(x1, x2);
        truePag.addBidirectedEdge(x2, x3);
        truePag.addPartiallyOrientedEdge(x4, x3);

        assertEquals(pag, truePag);
    }

    @Test
    public void testFromGraph() {
//        RandomUtil.getInstance().setSeed(new Date().getTime());
        RandomUtil.getInstance().setSeed(19444322L);

        int numNodes = 15;
        int numLatents = 5;
        int numIterations = 10;

        for (int i = 0; i < numIterations; i++) {
            Graph dag = GraphUtils.randomGraph(numNodes, numLatents, numNodes,
                    10, 10, 10, false);

            GFci gfci = new GFci(new IndTestDSep(dag), new GraphScore(dag));
            gfci.setCompleteRuleSetUsed(false);
            gfci.setFaithfulnessAssumed(true);
            Graph pag1 = gfci.search();

            DagToPag dagToPag = new DagToPag(dag);
            dagToPag.setCompleteRuleSetUsed(false);
            Graph pag2 = dagToPag.convert();

            assertEquals(pag2, pag1);
        }
    }

    @Test
    public void testFromData() {
        int numNodes = 20;
        int numLatents = 5;
        int numEdges = 20;
        int sampleSize = 50;

        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            variables.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = GraphUtils.randomGraphRandomForwardEdges(variables, numLatents, numEdges, 10, 10, 10, false, false);

        LargeScaleSimulation semSimulator = new LargeScaleSimulation(g);

        DataSet data = semSimulator.simulateDataFisher(sampleSize);

        data = DataUtils.restrictToMeasured(data);

        IndependenceTest test = new IndTestFisherZ(new CovarianceMatrixOnTheFly(data), 0.001);
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
        score.setPenaltyDiscount(4);
        GFci gFci = new GFci(test, score);
        gFci.setFaithfulnessAssumed(true);

        long start = System.currentTimeMillis();

        gFci.search();

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");

        DagToPag dagToPag = new DagToPag(g);
        dagToPag.setVerbose(false);
    }

    @Test
    public void testRandomDiscreteData() {
        int sampleSize = 1000;

        Graph g = GraphConverter.convert("X1-->X2,X1-->X3,X1-->X4,X2-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(g);
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        DataSet data = bayesIm.simulateData(sampleSize, false);

        IndependenceTest test = new IndTestChiSquare(data, 0.05);
        BDeuScore bDeuScore = new BDeuScore(data);
        bDeuScore.setSamplePrior(1.0);
        bDeuScore.setStructurePrior(1.0);

        GFci gFci = new GFci(test, bDeuScore);
        gFci.setFaithfulnessAssumed(true);

        long start = System.currentTimeMillis();

        gFci.search();

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");

        DagToPag dagToPag = new DagToPag(g);
        dagToPag.setVerbose(false);
    }

    @Test
    public void testDiscreteData() throws IOException {
        double alpha = 0.05;
        char delimiter = '\t';
        Path dataFile = Paths.get("./src/test/resources/sim_discrete_data_20vars_100cases.txt");

        TabularDataReader dataReader = new VerticalDiscreteTabularDataReader(dataFile.toFile(), DelimiterUtils.toDelimiter(delimiter));
        DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());

        IndependenceTest indTest = new IndTestChiSquare(dataSet, alpha);

        BDeuScore score = new BDeuScore(dataSet);
        score.setStructurePrior(1.0);
        score.setSamplePrior(1.0);

        GFci gFci = new GFci(indTest, score);
        gFci.setFaithfulnessAssumed(true);
        gFci.setMaxDegree(-1);
        gFci.setMaxPathLength(-1);
        gFci.setCompleteRuleSetUsed(false);
        gFci.setVerbose(true);

        long start = System.currentTimeMillis();

        gFci.search();

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");
    }
}
