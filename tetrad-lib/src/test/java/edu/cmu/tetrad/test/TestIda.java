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
    public void testIda() {
        Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        Node y = dataSet.getVariable("X10");

        Ida ida = new Ida(dataSet);

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    @Test
    public void testCStar() {
        int numNodes = 10;
        int numEdges = 10;
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
        parameters.set("targetName", "X5");

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
        int numNodes = 100;
        int numEdges = 200;
        int sampleSize = 300;
        int numIterations = 20;

        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 1);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .5);
        parameters.set("targetName", "X30");
        parameters.set("verbose", false);

        parameters.set("coefLow", 0.3);
        parameters.set("coefHigh", 0.9);
        parameters.set("covLow", 0.5);
        parameters.set("covHigh", 1.5);
        parameters.set("varLow", 0.5);
        parameters.set("varLow", 1.5);

        parameters.set("coefSymmetric", true);
        parameters.set("covSymmetric", true);

        List<int[]> cstarRet = new ArrayList<>();
        List<int[]> fmbStarRet = new ArrayList<>();

        for (int i = 0; i < numIterations; i++) {

            Graph trueDag = GraphUtils.randomGraph(numNodes, 0, numEdges,
                    100, 100, 100, false);

            SemPm pm = new SemPm(trueDag);
            SemIm im = new SemIm(pm, parameters);
            DataSet fullData = im.simulateData(sampleSize, false);

            System.out.println("\n\n=====CSTAR====");

            long start = System.currentTimeMillis();

            CStar cstar = new CStar();
            Graph graph = cstar.search(fullData, parameters);

            long stop = System.currentTimeMillis();

            int[] ret = printResult(trueDag, parameters, graph, stop - start, numNodes, numEdges, sampleSize, fullData);
            cstarRet.add(ret);

            System.out.println("\n\n=====FmbStar====");

            start = System.currentTimeMillis();

            FmbStar cstar2 = new FmbStar();
            Graph graph2 = cstar2.search(fullData, parameters);

            stop = System.currentTimeMillis();

            int[] ret2 = printResult(trueDag, parameters, graph2, stop - start, numNodes, numEdges, sampleSize, fullData);
            fmbStarRet.add(ret2);
        }

        System.out.println("\tC\tF\tCPred\tFPred\t~CMB\t~FMB\t~CSAA\t~FAA\t~CAAA\t~FAAA");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t"
                    + cstarRet.get(i)[0] + "\t" + fmbStarRet.get(i)[0] + "\t"
                    + cstarRet.get(i)[4] + "\t" + fmbStarRet.get(i)[4] + "\t"
                    + cstarRet.get(i)[1] + "\t" + fmbStarRet.get(i)[1] + "\t"
                    + cstarRet.get(i)[2] + "\t" + fmbStarRet.get(i)[2] + "\t"
                    + cstarRet.get(i)[3] + "\t" + fmbStarRet.get(i)[3] + "\t"
            );
        }
    }

    private int[] printResult(Graph trueDag, Parameters parameters, Graph graph, long elapsed, int numNodes,
                              int numEdges, int sampleSize, DataSet trueData) {
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

        final List<Node> mbNodes = GraphUtils.markovBlanketDag(target, trueDag).getNodes();
        mbNodes.remove(target);

        Set<Node> adjadjNodes = new HashSet<>(trueDag.getAdjacentNodes(target));
        for (Node node : new HashSet<>(adjadjNodes)) adjadjNodes.addAll(trueDag.getAdjacentNodes(node));
        adjadjNodes.remove(target);

        Set<Node> adjadjadjNodes = new HashSet<>(adjadjNodes);
        for (Node node : new HashSet<>(adjadjNodes)) adjadjadjNodes.addAll(trueDag.getAdjacentNodes(node));
        adjadjNodes.remove(target);

        Set<Node> notInMb = new HashSet<>(outputNodes);
        notInMb.removeAll(mbNodes);

        Set<Node> notInAdjAdj = new HashSet<>(outputNodes);
        notInAdjAdj.removeAll(adjadjNodes);

        Set<Node> notInAdjAdjAdj = new HashSet<>(outputNodes);
        notInAdjAdjAdj.removeAll(adjadjadjNodes);

        System.out.println("Output: " + outputNodes);
        System.out.println("Not In MB: " + notInMb);
        System.out.println("Not In AdjAdj: " + notInAdjAdj);
        System.out.println("Not In AdjAdjAdj: " + notInAdjAdjAdj);

        System.out.println("Elapsed " + elapsed / 1000.0 + " s");

        int count = printIdaResult(new ArrayList<>(outputNodes), target, trueData, trueDag);

        int[] ret = new int[5];

        ret[0] = outputNodes.size();
        ret[1] = notInMb.size();
        ret[2] = notInAdjAdj.size();
        ret[3] = notInAdjAdjAdj.size();
        ret[4] = count;

        return ret;
    }

    private int printIdaResult(List<Node> x, Node y, DataSet dataSet, Graph trueDag) {
        trueDag = GraphUtils.replaceNodes(trueDag, dataSet.getVariables());

        List<Node> x2 = new ArrayList<>();
        for (Node node : x) x2.add(dataSet.getVariable(node.getName()));
        x = x2;
        y = dataSet.getVariable(y.getName());

        Ida ida = new Ida(dataSet, x);

        int count = 0;

        for (Node _x : x) {
            LinkedList<Double> effects = ida.getEffects(_x, y);
            double trueEffect = ida.trueEffect(_x, y, trueDag);
            double distance = ida.distance(effects, trueEffect);
            System.out.println(_x + ": min effect = " + effects.getFirst() + " max effect = " + effects.getLast()
                    + " true effect = " + trueEffect + " distance = " + distance);

            count++;
        }

        return count;
    }
}





