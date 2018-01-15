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
import edu.cmu.tetrad.algcomparison.algorithm.FmbStar;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ida;
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

    @Test
    public void test1() {
        Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        Node y = dataSet.getVariable("X10");

        Ida ida = new Ida(new CovarianceMatrixOnTheFly(dataSet));

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    @Test
    public void test2() {
        Graph trueDag = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);
        trueDag = GraphUtils.replaceNodes(trueDag, dataSet.getVariables());

        Ida ida = new Ida(new CovarianceMatrixOnTheFly(dataSet));

        final List<Node> variables = dataSet.getVariables();

        for (Node x : variables) {
            for (Node y : variables) {
                if (x == y) continue;

                double distance = ida.distance(x, y, trueDag);

                System.out.println("x = " + x + " y = " + y + " distance = " + distance);
            }
        }
    }

    @Test
    public void testCStar() {
        int numNodes = 100;
        int numEdges = 100;
        int sampleSize = 100;

        Graph trueDag = GraphUtils.randomGraph(numNodes, 0, numEdges,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(sampleSize, false);

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 100);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .7);
        parameters.set("targetName", "X14");

        long start = System.currentTimeMillis();

        CStar cstar = new CStar();
        Graph graph = cstar.search(dataSet, parameters);

        long stop = System.currentTimeMillis();

        printResult(trueDag, parameters, graph, stop - start, numNodes, numEdges, sampleSize, dataSet);
    }

    @Test
    public void testFmbStar() {
        int numNodes = 100;
        int numEdges = 100;
        int sampleSize = 100;

        Graph trueDag = GraphUtils.randomGraph(numNodes, 0, numEdges,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(sampleSize, false);

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 100);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .7);
        parameters.set("targetName", "X14");

        long start = System.currentTimeMillis();

        FmbStar cstar = new FmbStar();
        Graph graph = cstar.search(dataSet, parameters);

        long stop = System.currentTimeMillis();

        printResult(trueDag, parameters, graph, stop - start, numNodes, numEdges, sampleSize, dataSet);
    }

    @Test
    public void testBoth() {
        int numNodes = 300;
        int numEdges = 300;
        int sampleSize = 200;
        int numIterations = 1;

        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 1);
        parameters.set("numSubsamples", 10);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .5);
        parameters.set("targetName", "X70");
        parameters.set("verbose", false);

        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 0.9);
        parameters.set("covLow", 0.5);
        parameters.set("covHigh", 1.0);
        parameters.set("varLow", 0.5);
        parameters.set("varHigh", 1.1);
        parameters.set("coefSymmetric", true);
        parameters.set("covSymmetric", true);

        List<int[]> cstarRet = new ArrayList<>();
        List<int[]> fmbStarRet = new ArrayList<>();

        for (int i = 0; i < numIterations; i++) {
            Graph trueDag = GraphUtils.randomGraph(numNodes, 0, numEdges,
                    100, 100, 100, false);

//        System.out.println(trueDag);

            SemPm pm = new SemPm(trueDag);
            SemIm im = new SemIm(pm, parameters);
            DataSet dataSet = im.simulateData(sampleSize, false);

            System.out.println("\n\n=====CSTAR====");

            long start = System.currentTimeMillis();

            CStar cstar = new CStar();
            Graph graph = cstar.search(dataSet, parameters);

            long stop = System.currentTimeMillis();

            int[] ret = printResult(trueDag, parameters, graph, stop - start, numNodes, numEdges, sampleSize, dataSet);
            cstarRet.add(ret);

            System.out.println("\n\n=====FMBStar====");

            start = System.currentTimeMillis();

            FmbStar cstar2 = new FmbStar();
            Graph graph2 = cstar2.search(dataSet, parameters);

            stop = System.currentTimeMillis();

            int[] ret2 = printResult(trueDag, parameters, graph2, stop - start, numNodes, numEdges, sampleSize, dataSet);
            fmbStarRet.add(ret2);
        }

        System.out.println("\tCStarTrue\tCStarHall\tFMbStarTrue\tFmbStarHall");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t" + cstarRet.get(i)[0] + "\t" + cstarRet.get(i)[1]
                    + "\t" + fmbStarRet.get(i)[0] + "\t" + fmbStarRet.get(i)[1]);
        }
    }

    private int[] printResult(Graph trueDag, Parameters parameters, Graph graph, long elapsed, int numNodes, int numEdges, int sampleSize, DataSet dataSet) {
        graph = GraphUtils.replaceNodes(graph, trueDag.getNodes());

        final Node target = trueDag.getNode(parameters.getString("targetName"));

        System.out.println("# nodes: " + numNodes);
        System.out.println("# edges: " + numEdges);
        System.out.println("Sample size: " + sampleSize);
        System.out.println("Target: " + target);

        Set<Node> outputNodes = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            outputNodes.add(edge.getNode1());
            outputNodes.add(edge.getNode2());
        }

        outputNodes.remove(graph.getNode(target.getName()));

        System.out.println("Output nodes: " + outputNodes);

        final List<Node> mbNodes = GraphUtils.markovBlanketDag(target, trueDag).getNodes();
        mbNodes.remove(target);

        Set<Node> adjadjNodes = new HashSet<>(trueDag.getAdjacentNodes(target));
        for (Node node : new HashSet<>(adjadjNodes)) adjadjNodes.addAll(trueDag.getAdjacentNodes(node));
        adjadjNodes.remove(target);

        Set<Node> adjadjadjNodes = new HashSet<>(adjadjNodes);
        for (Node node : new HashSet<>(adjadjNodes)) adjadjadjNodes.addAll(trueDag.getAdjacentNodes(node));
        adjadjNodes.remove(target);

        Set<Node> hallucinations = new HashSet<>(outputNodes);
        hallucinations.removeAll(adjadjNodes);

        System.out.println("Hallucinations: " + hallucinations);
        System.out.println("Markov blanket: " + mbNodes);
        System.out.println("adjadjNodes = " + adjadjNodes);
        System.out.println("adjadjadjNodes = " + adjadjadjNodes);
        System.out.println("Elapsed " + elapsed / 1000.0 + " s");

        printIdaResult(new ArrayList<>(outputNodes), target, dataSet, trueDag);

        int[] ret = new int[2];

        ret[0] = outputNodes.size() - hallucinations.size();
        ret[1] = hallucinations.size();

        return ret;
    }

    private void printIdaResult(List<Node> x, Node y, DataSet dataSet, Graph trueDag) {
        List<Node> x2 = new ArrayList<>();
        for (Node node : x) x2.add(dataSet.getVariable(node.getName()));
        x = x2;
        y = dataSet.getVariable(y.getName());

        Ida ida = new Ida(new CovarianceMatrixOnTheFly(dataSet));

        for (Node _x : x) {
            double distance = ida.distance(_x, y, trueDag);
            LinkedList<Double> effects = ida.getEffects(_x, y);
//            System.out.println("Effects = " + effects);
            System.out.println(_x /*+ "->" + y*/ + " min effect = " + effects.getFirst() + " max effect = " + effects.getLast()
                + " true effect = " + ida.trueEffect(_x, y, trueDag) + " distance = " + distance);
        }

    }
}





