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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.CStaS;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.*;

/**
 * Tests IDA.
 *
 * @author Joseph Ramsey
 */
public class TestIda {

    public void testIda() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", 0.5);
        parameters.set("maxQ", 100);
        parameters.set("targetName", "X50");
        parameters.set("alpha", 0.001);

        Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        Node y = dataSet.getVariable("X10");

        SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);

        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.PRIORITY);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
        Graph pattern = pc.search();

        Ida ida = new Ida(dataSet, pattern);

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    //    @Test
    public void testBoth() {
        int numNodes = 5000;
        int avgDegree = 8;
        int sampleSize = 100;
        int numIterations = 10;
        int numSubsamples = 50;
        int minNumAncestors = 15;
        int maxEr = 10;

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1.5);
        parameters.set("numSubsamples", numSubsamples);
        parameters.set("maxQ", 200);
        parameters.set("maxEr", maxEr);
        parameters.set("depth", 3);

        parameters.set("numMeasures", numNodes);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("verbose", false);

        parameters.set("coefLow", 0.3);
        parameters.set("coefHigh", 1.0);
        parameters.set("includeNegativeCoefs", true);
        parameters.set("sampleSize", sampleSize);
        parameters.set("intervalBetweenShocks", 40);
        parameters.set("intervalBetweenRecordings", 40);

        parameters.set("sampleSize", sampleSize);

        parameters.set("parallelism", 40);

        List<int[]> cstasRet = new ArrayList<>();

        RandomGraph randomForward = new RandomForward();
        LinearFisherModel fisher = new LinearFisherModel(randomForward);
        fisher.createData(parameters);
        DataSet fullData = (DataSet) fisher.getDataModel(0);

        Graph trueDag = fisher.getTrueGraph(0);

        int m = trueDag.getNumNodes() + 1;

        for (int i = 0; i < numIterations; i++) {
            m--;

            final Node t = trueDag.getNodes().get(m - 1);
            Set<Node> p = new HashSet<>(trueDag.getParents(t));

            for (Node q : new HashSet<>(p)) {
                p.addAll(trueDag.getParents(q));
            }

            if (p.size() < minNumAncestors) {
                i--;
                continue;
            }

            parameters.set("targetName", "X" + m);

            CStaS cstas = new CStaS(new SemBicTest());
            cstas.setTrueDag(trueDag);
            Graph graph = cstas.search(fullData, parameters);

            int[] ret = getResult(trueDag, graph);
            cstasRet.add(ret);
        }

        System.out.println();

        System.out.println("\tTreks\tAncestors\tNnn-Treks");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t"
                    + cstasRet.get(i)[0] + "\t"
                    + cstasRet.get(i)[1] + "\t"
                    + cstasRet.get(i)[2]
            );
        }
    }

    //    @Test
    public void testCombinations() {
        int avgDegree = 6;

        Parameters parameters = new Parameters();
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("verbose", false);

        parameters.set("coefLow", 0.3);
        parameters.set("coefHigh", 1.0);
        parameters.set("intervalBetweenShocks", 40);
        parameters.set("intervalBetweenRecordings", 40);

        parameters.set("parallelism", 40);

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 50);
        parameters.set("percentSubsampleSize", 0.5);
        parameters.set("maxQ", 200);
        parameters.set("maxEr", 5);
        parameters.set("depth", 3);

        int numIterations = 5;

        for (int numNodes : new int[]{400, 600}) {//, 100, 200, 400, 600}) {
            for (int sampleSize : new int[]{1000}) {//50, 100, 200, 400, 600, 1000}) {
                parameters.set("numMeasures", numNodes);
                parameters.set("sampleSize", sampleSize);


                List<int[]> cstasRet = new ArrayList<>();

                RandomGraph randomForward = new RandomForward();
                LinearFisherModel fisher = new LinearFisherModel(randomForward);
                fisher.createData(parameters);
                DataSet fullData = (DataSet) fisher.getDataModel(0);

                Graph trueDag = fisher.getTrueGraph(0);
                Graph truePattern = SearchGraphUtils.patternForDag(trueDag);

                int m = trueDag.getNumNodes() + 1;

                for (int i = 0; i < numIterations; i++) {
                    m--;

                    final Node t = trueDag.getNodes().get(m - 1);
                    Set<Node> p = new HashSet<>(trueDag.getParents(t));

                    for (Node q : new HashSet<>(p)) {
                        p.addAll(trueDag.getParents(q));
                    }

                    if (p.size() < 15) {
                        i--;
                        continue;
                    }

                    parameters.set("targetName", "X" + m);

                    CStaS cstas = new CStaS(new SemBicTest());
                    cstas.setTrueDag(trueDag);
                    Graph graph = cstas.search(fullData, parameters);

                    int[] ret = getResult(truePattern, graph);
                    cstasRet.add(ret);
                }

                int allFp = 0;

                for (int i = 0; i < numIterations; i++) {
                    allFp += cstasRet.get(i)[1];
                }

                double avgFp = allFp / (double) numIterations;

                System.out.println("# nodes = " + numNodes + " sample size = " + sampleSize + " avg FP = " + avgFp);
            }
        }

    }

    @Test
    public void testConditionalGaussian() {

        Parameters parameters = new Parameters();
        parameters.set("numMeasures", 50);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 3);

        parameters.set("numCategories", 2);
        parameters.set("percentDiscrete", 50);
        parameters.set("numRuns", 10);
        parameters.set("differentGraphs", true);
        parameters.set("sampleSize", 1000);

        parameters.set("penaltyDiscount", 1);
        parameters.set("numSubsamples", 30);
        parameters.set("maxEr", 5);
        parameters.set("targetName", "X50");

        RandomGraph graph = new RandomForward();

        LeeHastieSimulation simulation = new LeeHastieSimulation(graph);
        simulation.createData(parameters);

        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            edu.cmu.tetrad.search.CStaS cStaS = new edu.cmu.tetrad.search.CStaS();
            cStaS.setTrueDag(simulation.getTrueGraph(i));
            final DataSet dataSet = (DataSet) simulation.getDataModel(i);
            final ConditionalGaussianScore score = new ConditionalGaussianScore(dataSet, 1, false);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            final IndependenceTest test = new IndTestScore(score);
            List<edu.cmu.tetrad.search.CStaS.Record> records = cStaS.getRecords(dataSet,
                    simulation.getTrueGraph(i).getNode(parameters.getString("targetName")),
                    test);
            System.out.println(cStaS.makeTable(records));
        }
    }

    private int[] getResult(Graph trueDag, Graph graph) {
        graph = GraphUtils.replaceNodes(graph, trueDag.getNodes());

        if (graph == null) throw new NullPointerException("Graph null");

        Set<Edge> allTreks = new HashSet<>();
        Set<Edge> allAncestors = new HashSet<>();
        Set<Edge> allOther = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            boolean ancestor = trueDag.isAncestorOf(x, y);

            List<List<Node>> treks = GraphUtils.treks(trueDag, x, y, 10);
            boolean trekToTarget = !treks.isEmpty();

            if (ancestor) {
                allAncestors.add(edge);
            }

            if (trekToTarget) {
                allTreks.add(edge);
            } else {
                allOther.add(edge);
            }
        }

        int[] ret = new int[3];

        ret[0] = allTreks.size();
        ret[1] = allAncestors.size();
        ret[2] = allOther.size();

        return ret;
    }

    public static void main(String[] args) {
        new TestIda().testBoth();
    }
}




