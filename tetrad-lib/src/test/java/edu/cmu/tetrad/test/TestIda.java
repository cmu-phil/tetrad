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

import edu.cmu.tetrad.algcomparison.algorithm.CStar;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;

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

        Graph pattern = CStar.getPattern(dataSet, parameters);

        Ida ida = new Ida(dataSet, pattern);

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    @Test
    public void testCStar() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("maxQ", 5);
        parameters.set("targetName", "X50");
        parameters.set("alpha", 0.001);

        int numNodes = 50;
        int numEdges = 2 * numNodes;
        int sampleSize = 100;

        Graph trueDag = GraphUtils.randomGraph(numNodes, 0, numEdges,
                100, 100, 100, false);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(sampleSize, false);

        long start = System.currentTimeMillis();

        CStar cstar = new CStar();
        Graph graph = cstar.search(dataSet, parameters);

        long stop = System.currentTimeMillis();

        printResult(trueDag, parameters, graph, stop - start);
    }

    public void testBoth(int numNodes, double avgDegree, int sampleSize, int numIterations) {
        Parameters parameters = new Parameters();
        parameters.set("numMeasures", numNodes);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("verbose", false);

        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 1.8);
//        parameters.set("varLow");
//        parameters.set("varHigh");
//        parameters.set("verbose");
//        parameters.set("includePositiveCoefs");
        parameters.set("includeNegativeCoefs", true);
//        parameters.set("errorsNormal", true);
//        parameters.set("betaLeftValue", 2);
//        parameters.set("betaRightValue", 5);
//        parameters.set("numRuns");
//        parameters.set("percentDiscrete");
//        parameters.set("numCategories");
//        parameters.set("differentGraphs");
        parameters.set("sampleSize", sampleSize);
        parameters.set("intervalBetweenShocks", 40);
        parameters.set("intervalBetweenRecordings", 40);
//        parameters.set("selfLoopCoef");
//        parameters.set("fisherEpsilon");
//        parameters.set("randomizeColumns");
//        parameters.set("measurementVariance");

        parameters.set("sampleSize", sampleSize);

        parameters.set("parallelism", 40);
        parameters.set("CStarAlg", 2); // 1 = FGES, 2 = PC-Stable

        parameters.set("penaltyDiscount", 1.5);
        parameters.set("numSubsamples", 50);
        parameters.set("bootstrapSelectionSize", 0.5);
        parameters.set("maxQ", 1000);
        parameters.set("maxEr", 25);

        List<int[]> cstarRet = new ArrayList<>();

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

            if (p.size() < 20) {
                i--;
                continue;
            }

            parameters.set("targetName", "X" + m);

            System.out.println("\n\n=====CSTAR====");

            long start = System.currentTimeMillis();

            DataSet selectedData = selectVariables(fullData, parameters);

            final List<Node> ancestors = new ArrayList<>();

            for (Node node : trueDag.getNodes()) {
                if (truePattern.existsSemiDirectedPathFromTo(node, Collections.singleton(t))) {
                    ancestors.add(node);
                }
            }

            CStar cstar = new CStar();
            Graph graph = cstar.search(selectedData, parameters);

            long stop = System.currentTimeMillis();

            int[] ret = printResult(truePattern, parameters, graph, stop - start);
            cstarRet.add(ret);

            System.out.println("\n\n=====FmbStar====");
        }

        System.out.println();

        System.out.println("\tCPar\tCPAnc\tCDesc\tCSib\tCPDesc\tCOther");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t"
                    + cstarRet.get(i)[0] + "\t"
                    + cstarRet.get(i)[1] + "\t"
                    + cstarRet.get(i)[2] + "\t"
                    + cstarRet.get(i)[3] + "\t"
                    + cstarRet.get(i)[4] + "\t"
                    + cstarRet.get(i)[5] + "\t"
            );
        }
    }

    private DataSet selectVariables(DataSet fullData, Parameters parameters) {
        Node y = fullData.getVariable(parameters.getString("targetName"));

        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(fullData));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);

        List<Node> selection = new ArrayList<>();

        final List<Node> variables = fullData.getVariables();

        {
            class Task implements Callable<Boolean> {
                private int from;
                private int to;
                private Node y;

                private Task(int from, int to, Node y) {
                    this.from = from;
                    this.to = to;
                    this.y = y;
                }

                @Override
                public Boolean call() {
                    for (int n = from; n < to; n++) {
                        final Node node = variables.get(n);
                        if (node != y) {
                            if (!test.isIndependent(node, y)) {
                                if (!selection.contains(node)) {
                                    selection.add(node);
                                }
                            }
                        }
                    }

                    return true;
                }
            }

            final int chunk = 50;
            List<Callable<Boolean>> tasks;

            {
                tasks = new ArrayList<>();

                for (int from = 0; from < variables.size(); from += chunk) {
                    final int to = Math.min(variables.size(), from + chunk);
                    tasks.add(new Task(from, to, y));
                }

                ConcurrencyUtils.runCallables(tasks, parameters.getInt("parallelism"));
            }

            {
                tasks = new ArrayList<>();

                for (Node s : new ArrayList<>(selection)) {
                    for (int from = 0; from < variables.size(); from += chunk) {
                        final int to = Math.min(variables.size(), from + chunk);
                        tasks.add(new Task(from, to, s));
                    }
                }
            }

            ConcurrencyUtils.runCallables(tasks, parameters.getInt("parallelism"));
        }

        final DataSet dataSet = fullData.subsetColumns(selection);

        System.out.println("# selected variables = " + dataSet.getVariables().size());

        return dataSet;

    }

    private int[] printResult(Graph trueGraph, Parameters parameters, Graph graph, long elapsed) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        Set<Edge> allParents = new HashSet<>();
        Set<Edge> allPossAncestors = new HashSet<>();
        Set<Edge> allDescendants = new HashSet<>();
        Set<Edge> allSiblings = new HashSet<>();
        Set<Edge> allParentsOfDescendants = new HashSet<>();
        Set<Edge> allOther = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            final boolean parent = trueGraph.getParents(x).contains(y);

            if (parent) allParents.add(edge);

            final boolean possibleAncestor = trueGraph.existsSemiDirectedPathFromTo(x, Collections.singleton(y));

            if (possibleAncestor && !parent)
                allPossAncestors.add(edge);

            final boolean descendant = trueGraph.existsSemiDirectedPathFromTo(y, Collections.singleton(x));

            if (descendant) allDescendants.add(edge);

            if (!parent && !descendant && !possibleAncestor) {
                allOther.add(edge);
            }
        }

        int[] ret = new int[6];

        ret[0] = allParents.size();
        ret[1] = allPossAncestors.size();
        ret[2] = allDescendants.size();
        ret[3] = allSiblings.size();
        ret[4] = allParentsOfDescendants.size();
        ret[5] = allOther.size();

        return ret;
    }

    private void printIdaResult(List<Node> x, Node y, DataSet dataSet, Graph trueGraph, Parameters parameters) {
        trueGraph = GraphUtils.replaceNodes(trueGraph, dataSet.getVariables());
        ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(dataSet);

        List<Node> x2 = new ArrayList<>();
        for (Node node : x) x2.add(dataSet.getVariable(node.getName()));
        x = x2;
        y = dataSet.getVariable(y.getName());

        Graph pattern = CStar.getPattern(dataSet, parameters);

        Ida ida = new Ida(dataSet, pattern);

        for (Node _x : x) {
            LinkedList<Double> effects = ida.getEffects(_x, y);
            double trueEffect = ida.trueEffect(_x, y, trueGraph);
            double distance = ida.distance(effects, trueEffect);
            System.out.println(_x + ": min effect = " + effects.getFirst() + " max effect = " + effects.getLast()
                    + " true effect = " + trueEffect + " distance = " + distance);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            int numNodes = 50;
            double avgDegree = 2;
            int sampleSize = 100;
            int numIterations = 5;
            new TestIda().testBoth(numNodes, avgDegree, sampleSize, numIterations);
        } else {
            int numNodes = Integer.parseInt(args[0]);
            double avgDegree = Double.parseDouble(args[1]);
            int sampleSize = Integer.parseInt(args[2]);
            int numIterations = Integer.parseInt(args[3]);

            new TestIda().testBoth(numNodes, avgDegree, sampleSize, numIterations);
        }
    }
}





