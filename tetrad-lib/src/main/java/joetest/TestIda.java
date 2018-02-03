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

package joetest;

import edu.cmu.tetrad.algcomparison.algorithm.CStar;
import edu.cmu.tetrad.algcomparison.algorithm.FmbStar;
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
import edu.cmu.tetrad.util.Parameters;

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
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .5);
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

        Ida ida = new Ida(dataSet, pattern, dataSet.getVariables());

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    public void testCStar() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .5);
        parameters.set("targetName", "X50");
        parameters.set("alpha", 0.001);

        int numNodes = 50;
        int numEdges = 2 * numNodes;
        int sampleSize = 100;
        int numBootstraps = 20;

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

    public void testFmbStar() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .5);
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

        FmbStar star = new FmbStar();
        Graph graph = star.search(dataSet, parameters);

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

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 10);
        parameters.set("piThreshold", .7);
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

        RandomGraph randomForward = new RandomForward();
        LinearFisherModel fisher = new LinearFisherModel(randomForward);
        fisher.createData(parameters);
        DataSet fullData = (DataSet) fisher.getDataModel(0);
        Graph trueDag = fisher.getTrueGraph(0);

        Graph truePattern = SearchGraphUtils.patternForDag(trueDag);

        for (int i = 0; i < numIterations; i++) {

            parameters.set("targetName", "X" + (numNodes - numIterations - i));

            System.out.println("\n\n=====CSTAR====");

            long start = System.currentTimeMillis();

            CStar cstar = new CStar();
            Graph graph = cstar.search(fullData, parameters);

            long stop = System.currentTimeMillis();

            int[] ret = printResult(truePattern, parameters, graph, stop - start);
            cstarRet.add(ret);

            System.out.println("\n\n=====FmbStar====");

            start = System.currentTimeMillis();

            FmbStar fmbStar = new FmbStar();
            Graph graph2 = fmbStar.search(fullData, parameters);

            stop = System.currentTimeMillis();

            int[] ret2 = printResult(truePattern, parameters, graph2, stop - start);
            fmbStarRet.add(ret2);
        }

        System.out.println();

        System.out.println("\tCPar\tPar\tCPAnc\tFPAnc\tCDesc\tFDescl\tCSib\tFSib\tCPDesc\tFPDesc\tCOther\tFOther");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t"
                    + cstarRet.get(i)[0] + "\t" + fmbStarRet.get(i)[0] + "\t"
                    + cstarRet.get(i)[1] + "\t" + fmbStarRet.get(i)[1] + "\t"
                    + cstarRet.get(i)[2] + "\t" + fmbStarRet.get(i)[2] + "\t"
                    + cstarRet.get(i)[3] + "\t" + fmbStarRet.get(i)[3] + "\t"
                    + cstarRet.get(i)[4] + "\t" + fmbStarRet.get(i)[4] + "\t"
                    + cstarRet.get(i)[5] + "\t" + fmbStarRet.get(i)[5] + "\t"
            );
        }
    }

    private int[] printResult(Graph trueGraph, Parameters parameters, Graph graph, long elapsed) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        final Node target = trueGraph.getNode(parameters.getString("targetName"));

        System.out.println("# nodes: " + parameters.getInt("numMeasures"));
        System.out.println("Avg Degree: " + parameters.getDouble("avgDegree"));
        System.out.println("Sample size: " + parameters.getInt("sampleSize"));
        System.out.println("Target: " + parameters.getString("target"));

        Set<Node> outputNodes = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            outputNodes.add(edge.getNode1());
            outputNodes.add(edge.getNode2());
        }

        outputNodes.remove(graph.getNode(target.getName()));


        final Set<Node> possAncestors = new HashSet<>();

        for (Node node : outputNodes) {
            if (trueGraph.existsSemiDirectedPathFromTo(node, Collections.singleton(target))) {
                possAncestors.add(node);
            }
        }

        final List<Node> parents = trueGraph.getParents(target);
        final List<Node> descendants = trueGraph.getDescendants(Collections.singletonList(target));

        final List<Node> siblings = trueGraph.getAdjacentNodes(target);
        siblings.removeAll(descendants);
        siblings.removeAll(possAncestors);
        siblings.removeAll(parents);

        final Set<Node> parentsOfDescendants = new HashSet<>();

        for (Node c : descendants) {
            parentsOfDescendants.addAll(trueGraph.getParents(c));
        }

        parentsOfDescendants.remove(target);
        parentsOfDescendants.removeAll(parents);
        parentsOfDescendants.retainAll(parents);
        parentsOfDescendants.removeAll(possAncestors);
        parentsOfDescendants.removeAll(siblings);

        possAncestors.removeAll(parents);

        final List<Node> other = new ArrayList<>(outputNodes);
        other.removeAll(possAncestors);
        other.removeAll(descendants);
        other.removeAll(siblings);
        other.removeAll(parents);
        other.removeAll(parentsOfDescendants);

        parents.retainAll(outputNodes);
        descendants.retainAll(outputNodes);
        parentsOfDescendants.retainAll(outputNodes);
        possAncestors.retainAll(outputNodes);
        siblings.retainAll(outputNodes);
        other.retainAll(outputNodes);

        System.out.println("Parents: " + parents);
        System.out.println("Possible ancestors: " + possAncestors);
        System.out.println("Descendants: " + descendants);
        System.out.println("Siblings: " + siblings);
        System.out.println("Parents Of Descendants: " + parentsOfDescendants);
        System.out.println("Other: " + other);

        System.out.println("Elapsed " + elapsed / 1000.0 + " s");

        //        printIdaResult(new ArrayList<>(outputNodes), target, trueData, trueGraph);

        int[] ret = new int[6];

        ret[0] = parents.size();
        ret[1] = possAncestors.size();
        ret[2] = descendants.size();
        ret[3] = siblings.size();
        ret[4] = parentsOfDescendants.size();
        ret[5] = other.size();

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

        Ida ida = new Ida(dataSet, pattern, dataSet.getVariables());

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





