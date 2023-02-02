///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import org.junit.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestGFci {

    public void test1() {
        RandomUtil.getInstance().setSeed(1450189593459L);

        final int numNodes = 10;
        final int numLatents = 5;
        final int numEdges = 10;
        final int sampleSize = 1000;

        final double alpha = 0.01;
        final double penaltyDiscount = 2;
        final int depth = -1;
        final int maxPathLength = -1;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = RandomGraph.randomGraphUniform(vars, numLatents, numEdges, 4, 4, 4, false, 50000);

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

//        DagToPag dagToPag = new DagToPag(dag);
//        dagToPag.setCompleteRuleSetUsed(false);
//        dagToPag.setMaxPathLength(maxPathLength);
//        Graph truePag = dagToPag.convert();

        Graph truePag = SearchGraphUtils.dagToPag(dag);

        outGraph = GraphUtils.replaceNodes(outGraph, truePag.getNodes());

        int[][] counts = SearchGraphUtils.graphComparison(truePag, outGraph, null);

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

        final int numNodes = 15;
        final int numLatents = 5;
        final int numIterations = 1;

        for (int i = 0; i < numIterations; i++) {
            Graph dag = RandomGraph.randomGraph(numNodes, numLatents, numNodes,
                    10, 10, 10, false);

            GFci gfci = new GFci(new IndTestDSep(dag), new GraphScore(dag));
            gfci.setCompleteRuleSetUsed(true);
            gfci.setFaithfulnessAssumed(true);
            Graph pag1 = gfci.search();

//            DagToPag dagToPag = new DagToPag(dag);
//            dagToPag.setCompleteRuleSetUsed(false);
//            Graph pag2 = dagToPag.convert();

            Graph pag2 = SearchGraphUtils.dagToPag(dag);

            assertEquals(pag2, pag1);
        }
    }

    @Test
    public void testFromData() {
        final int numNodes = 20;
        final int numLatents = 5;
        final int numEdges = 20;
        final int sampleSize = 100;

        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            variables.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = RandomGraph.randomGraphRandomForwardEdges(variables, numLatents, numEdges, 10, 10, 10, false, false);

        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        data = DataUtils.restrictToMeasured(data);

//        System.out.println(data.getCorrelationMatrix());

        IndependenceTest test = new IndTestFisherZ(data, 0.001);
        SemBicScore score = new SemBicScore(data);
        score.setRuleType(SemBicScore.RuleType.CHICKERING);
        score.setPenaltyDiscount(2);
        GFci gFci = new GFci(test, score);
        gFci.setFaithfulnessAssumed(true);

        long start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        gFci.search();

        long stop = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        System.out.println("Elapsed " + (stop - start) + " ms");
    }

    @Test
    public void testRandomDiscreteData() {
        final int sampleSize = 1000;

        Graph g = GraphUtils.convert("X1-->X2,X1-->X3,X1-->X4,X2-->X3,X2-->X4,X3-->X4");
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

        long start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        gFci.search();

        long stop = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        System.out.println("Elapsed " + (stop - start) + " ms");
    }

    @Test
    public void testDiscreteData() throws IOException {
        final double alpha = 0.05;
        final char delimiter = '\t';
        Path dataFile = Paths.get("./src/test/resources/sim_discrete_data_20vars_100cases.txt");

        VerticalDiscreteTabularDatasetFileReader dataReader = new VerticalDiscreteTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delimiter));
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

        long start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        gFci.search();

        long stop = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        System.out.println("Elapsed " + (stop - start) + " ms");
    }
}
