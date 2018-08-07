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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestMarkovBlanketSearches {
//    private NumberFormat nf2 = new DecimalFormat("     0");
//    private NumberFormat nf3 = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Simple test using d-separation.
     */
    @Test
    public void testSubgraph1() {
        Graph graph = GraphConverter.convert("T-->X,X-->Y,W-->X,W-->Y");

        IndTestDSep test = new IndTestDSep(graph);

        MbSearch search = new GrowShrink(test);
        List<Node> blanket = search.findMb("T");

        List<Node> b = new ArrayList<>();
        b.add(graph.getNode("X"));
        b.add(graph.getNode("W"));

        assertEquals(b, blanket);
    }

    /**
     * Slightly harder test using d-separation.
     */
    @Test
    public void testSubgraph2() {
        Graph graph = GraphConverter.convert("P1-->T,P2-->T,T-->C1,T-->C2," +
                "T-->C3,PC1a-->C1,PC1b-->C1,PC2a-->C2,PC2b<--C2,PC3a-->C3," +
                "PC3b-->C3,PC1b-->PC2a,PC1a<--PC3b,U,V");

        IndTestDSep test = new IndTestDSep(graph);
        MbSearch mbSearch = new GrowShrink(test);
        List<Node> blanket = mbSearch.findMb("T");

        List<Node> mbd = GraphUtils.markovBlanketDag(graph.getNode("T"), graph).getNodes();
        mbd.remove(graph.getNode("T"));

        assertEquals(new HashSet<>(mbd), new HashSet<>(blanket));

    }

    @Test
    public void testRandom() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes1, 0, 10,
                5, 5, 5, false));
        IndependenceTest test = new IndTestDSep(dag);
        Mbfs search = new Mbfs(test, -1);

        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> resultNodes = search.findMb(node.getName());
            Graph trueMb = GraphUtils.markovBlanketDag(node, dag);
            List<Node> trueNodes = trueMb.getNodes();
            trueNodes.remove(node);

            Collections.sort(trueNodes, new Comparator<Node>() {
                public int compare(Node n1, Node n2) {
                    return n1.getName().compareTo(n2.getName());
                }
            });

            Collections.sort(resultNodes, new Comparator<Node>() {
                public int compare(Node n1, Node n2) {
                    return n1.getName().compareTo(n2.getName());
                }
            });

            assertEquals(trueNodes, resultNodes);
        }
    }

//    public void overnight() {
//        try {
//            File file = new File("overnight.txt");
//            System.out.println(file);
//            PrintWriter out = new PrintWriter(file);
//
//            SimulationParams params = new SimulationParams();
//            params.setSampleSize(1000);
//            params.setDiscrete(false);
//            params.setRandomGraphEveryTime(true);
//            params.setTimeLimit(600000);
//            params.setMaxIndegree(3);
//            params.setNumTests(30);
//            params.setMinMbSize(
//                    8);
//            params.setAlternativePenalty(0.05);
//            params.setMinNumCategories(2);
//            params.setMaxNumCategories(4);
//            params.setAlgNames(Arrays.asList("PCMB", "CPCMB", "GS",
//                    "IAMB", "InterIAMBnPC", "IAMBnPC", "InterIAMB",
//                    "HITON-MB", "MMMB", "MBFS"));
//
//            params.setEdgeMultipler(1.2);
//
////            params.setNumVars(100);
////            testLoop(out, params);
//
//            params.setAlgNames(Arrays.asList(/*"GS",
//                    "IAMB", "InterIAMBnPC", "IAMBnPC", "InterIAMB",*/
//                    "HITON-MB", "MMMB", "MBFS"));
//            params.setNumVars(500);
//            testLoop(out, params);
//
//            params.setNumVars(1000);
//            testLoop(out, params);
//
//            params.setDiscrete(true);
//            params.setRandomGraphEveryTime(true);
//            params.setNumVars(100);
//            testLoop(out, params);
//
//            params.setNumVars(500);
//            testLoop(out, params);
//
//            params.setNumVars(1000);
//            testLoop(out, params);
//
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    public void large() {
//        try {
//            File file = new File("overnight.txt");
//            System.out.println(file);
//            PrintWriter out = new PrintWriter(file);
//
//            SimulationParams params = new SimulationParams();
//            params.setSampleSize(1000);
//            params.setRandomGraphEveryTime(false);
//            params.setTimeLimit(450000);
//            params.setMaxIndegree(2);
//            params.setNumTests(30);
//            params.setMinMbSize(8);
//            params.setAlternativePenalty(0.01);
//            params.setMinNumCategories(2);
//            params.setMaxNumCategories(4);
//            params.setAlgNames(Arrays.asList("HITON-MB", "MMMB", "MBFS"));
////            params.setAlgNames(Arrays.asList("MBFS"));
//
//            params.setEdgeMultipler(1.2);
//            params.setNumVars(5000);
//
//            params.setDiscrete(false);
//            testLoop(out, params);
//
//            params.setDiscrete(true);
//            testLoop(out, params);
//
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    public void tryout() {
////        LogUtils.getInstance().add(System.out, Level.FINER);
//
//        try {
//            File file = new File("tryout.txt");
//            System.out.println(file);
//            PrintWriter out = new PrintWriter(file);
//
//            SimulationParams params = new SimulationParams();
//            params.setSampleSize(1000);
//            params.setNumVars(1000);
//            params.setDiscrete(false);
//            params.setRandomGraphEveryTime(true);
//            params.setTimeLimit(600000);
//            params.setMaxIndegree(3);
//            params.setNumTests(30);
//            params.setMinMbSize(6);
//            params.setAlternativePenalty(0.05);
//            params.setMinNumCategories(2);
//            params.setMaxNumCategories(4);
////            params.setAlgNames(Arrays.asList("PCMB", "CPCMB", "GS",
////                    "IAMB", "InterIAMBnPC", "IAMBnPC", "InterIAMB",
////                    "HITON-MB", "MMMB", "MBFS"));
//            params.setAlgNames(Arrays.asList(
//                    "HITON-MB", "MMMB", "MBFS"));
//
//            testLoop(out, params);
//
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


//    public void testLoop(PrintWriter out, SimulationParams params) {
//        List<String> algNames = new LinkedList<String>(params.getAlgNames());
//
//        int numEdges = (int) (params.getEdgeMultipler() * params.getNumVars());
//
//        // For discrete.
//        int minNumCategories = params.getMinNumCategories();
//        int maxNumCategories = params.getMaxNumCategories();
//
//        println(out, "Alpha = " + params.getAlternativePenalty());
//        println(out, "# variables = " + params.getNumVars());
//        println(out, "# edges = " + numEdges);
//        println(out, "# samples = " + params.getSampleSize());
//        println(out, "Depth = " + params.getMaxIndegree());
//        println(out, params.isDiscrete() ? "Discrete" : "Continuous");
//
//        if (params.isDiscrete()) {
//            println(out, minNumCategories + " to " + maxNumCategories + " categories.");
//        }
//
//        out.println();
//        out.println();
//
//        println(out, "\t FP\t FN\t Err\t Corr\t Truth\t Time");
//
////        LogUtils.getInstance().add(System.out, Level.FINEST);
//
//        List<MbSearch> algorithm =
//                new LinkedList<MbSearch>();
//
//        List<Stats> collectedStats = new ArrayList<Stats>();
//        Dag randomGraph = null;
//        DataSet dataSet = null;
//        Set<Node> usedMbNodes = new HashSet<Node>();
//        boolean createRandomGraph = true;
//        IndependenceTest test = null;
//
//        for (int n = 0; n < params.getNumTests(); n++) {
//            System.gc();
//
//            if (params.isRandomGraphEveryTime() || createRandomGraph) {
//                List<Node> nodes = new ArrayList<Node>();
//
//                for (int i = 0; i < params.getNumVars(); i++) {
//                    nodes.add(new ContinuousVariable("X" + (i + 1)));
//                }
//
//                randomGraph = new Dag(GraphUtils.randomGraph(nodes, 0, numEdges,
//                        9, 3, 9, false));
//                createRandomGraph = false;
//
//                if (params.isDiscrete()) {
//                    dataSet = simulateDiscrete(randomGraph, dataSet, params.getSampleSize(),
//                            minNumCategories, maxNumCategories);
//                    test = new IndTestGSquare(dataSet, params.getAlternativePenalty());
//                } else {
//                    dataSet = simulateContinuous(randomGraph, params.getSampleSize(), dataSet);
//                    test = new IndTestFisherZ(dataSet, params.getAlternativePenalty());
//                }
//
//                algorithm.clear();
//
//                for (String algName : algNames) {
//                    algorithm.add(getAlgorithm(algName, test, params.getMaxIndegree(), dataSet));
//                }
//            }
//
//            Set<Integer> visited = new HashSet<Integer>();
//
//            int tried = 0;
//            Graph trueMbDag = null;
//            Node t = null;
//            int i = -1;
//
//            while (tried <= 30) {
//                i = RandomUtil.getInstance().nextInt(params.getNumVars());
//
//                if (visited.contains(i)) {
//                    tried++;
//                    continue;
//                }
//
//                t = randomGraph.getNodes().get(i);
//
//                if (usedMbNodes.contains(t)) {
//                    tried++;
//                    continue;
//                }
//
//                trueMbDag = GraphUtils.markovBlanketDag(t, randomGraph);
//
////                if (trueMbDag.getNumNodes() != minMbSize + 1) {
//                if (trueMbDag.getNumNodes() < params.getMinMbSize() + 1) {
//                    trueMbDag = null;
//                    t = null;
//                    visited.add(i);
//                    continue;
//                }
//
//                break;
//            }
//
//            if (t == null || trueMbDag == null) {
//                println(out, "new data");
//                createRandomGraph = true;
//                usedMbNodes.clear();
//                n--;
//                continue;
//            }
//
//            List<Node> nodes2 = trueMbDag.getNodes();
//            usedMbNodes.addAll(nodes2);
//            nodes2.remove(t);
//            List<String> truth = extractVarNames(nodes2, t);
//
//            println(out, "n = " + (n + 1));
//
//            for (MbSearch algorithm : new LinkedList<MbSearch>(algorithm)) {
////                System.out.println("Running " + algorithm.getAlgorithmName() + "...");
//                Stats stats = printNodeStats(algorithm, t, truth, i, out, params.getTimeLimit());
//
//                if (stats == null) {
////                    algorithm.remove(algorithm);
////
////                    for (Stats _stats : new LinkedList<Stats>(collectedStats)) {
////                        if (_stats.getAlgorithm() == algorithm) {
////                            collectedStats.remove(_stats);
////                        }
////                    }
////
////                    algNames.remove(algorithm.getAlgorithmName());
//
//                    continue;
//                }
//
////                Stats stats = printGraphStats(algorithm, t, trueMbDag, dataSet, i, nf2, out);
////                printMbfsGraphStats(t, test, depth, trueMbDag, i, nf2);
//                collectedStats.add(stats);
//            }
//
//            println(out, "");
//        }
//
//        println(out, "\\begin{tabular}{llllllll}");
//        println(out, "\\hline");
//        println(out, "#vars&Algorithm&FP&FN&Err&Corr&Truth&Time\\\\");
//        println(out, "\\hline");
//
//        println(out, "\tFP\tFN\tErr\tCorr\tTruth\tTime");
//
//        for (MbSearch algorithm : algorithm) {
//            int fpSum = 0, fnSum = 0, errorsSum = 0, truthSum = 0;
//            long timeSum = 0;
//            int n = 0;
//
//            for (Stats stats : collectedStats) {
//                if (!stats.getAlgorithm().getAlgorithmName()
//                        .equals(algorithm.getAlgorithmName())) {
//                    continue;
//                }
//
//                fpSum += stats.getAfp();
//                fnSum += stats.getAfn();
//                errorsSum += stats.getErrors();
//                truthSum += stats.getTrueDag();
//                timeSum += stats.getTime();
//                n++;
//            }
//
//            double fpAvg = fpSum / (double) n;
//            double fnAvg = fnSum / (double) n;
//            double errorsAve = errorsSum / (double) n;
//            double truthAvg = truthSum / (double) n;
//            double timeAvg = timeSum / (double) n;
//
////            println(out, "\t" + nf.format(fpAvg) + "\t" +
////                    nf.format(fnAvg) + "\t" +
////                    nf.format(errorsAve) + "\t" +
////                    nf.format(truthAvg - fnAvg) + "\t" +
////                    nf.format(truthAvg) + "\t" +
////                    nf.format(timeAvg) + "\t" +
////                    algorithm.getAlgorithmName());
//
//            // Latex table.
//            println(out, params.getNumVars() + "&" +
//                    algorithm.getAlgorithmName() + "&" +
//                    nf3.format(fpAvg) + "&" +
//                    nf3.format(fnAvg) + "&" +
//                    nf3.format(errorsAve) + "&" +
//                    nf3.format(truthAvg - fnAvg) + "&" +
//                    nf3.format(truthAvg) + "&" +
//                    nf3.format(timeAvg) + "\\\\");
//        }
//
//        println(out, "\\hline");
//        println(out, "\\end{tabular}");
//    }

//    private void println(PrintWriter out, String x) {
//        out.println(x);
//        out.flush();
//        System.out.println(x);
//    }

//    private MbSearch getAlgorithm(String name, IndependenceTest test,
//                                  int depth, DataSet dataSet) {
//        if ("PCMB".equals(name)) {
//            return new Pcmb(test, depth);
//        } else if ("GS".equals(name)) {
//            return new GrowShrink(test);
//        } else if ("IAMB".equals(name)) {
//            return new Iamb(test);
//        } else if ("IAMBnPC".equals(name)) {
//            return new IambnPc(test);
//        } else if ("InterIAMB".equals(name)) {
//            return new InterIamb(test);
//        } else if ("InterIAMBnPC".equals(name)) {
//            return new IambnPc(test);
//        } else if ("HITON-VARIANT".equals(name)) {
//            return new HitonVariant(test, depth);
//        } else if ("HITON-MB".equals(name)) {
//            return new HitonMb(test, depth, false);
//        } else if ("HITON-MB-SYM".equals(name)) {
//            return new HitonMb(test, depth, true);
//        } else if ("MMMB".equals(name)) {
//            return new Mmmb(test, depth, false);
//        } else if ("MMMB-SYM".equals(name)) {
//            return new Mmmb(test, depth, true);
//        } else if ("MBFS".equals(name)) {
//            return new Mbfs(test, depth);
//        }
//
//
//        throw new IllegalStateException("Unrecognized algorithm name: " + name);
//    }

//    private Stats printNodeStats(MbSearch algorithm, Node t,
//                                 List<String> _truth, int i, PrintWriter out, long timeLimit) {
//        final long time = System.currentTimeMillis();
//
//        class MyThread extends Thread {
//            private MbSearch algorithm;
//            private List<Node> nodes;
//            private Node t;
//            private boolean done = false;
//            private long startTime = System.currentTimeMillis();
//            private long endTime;
//
//            public MyThread(MbSearch algorithm, Node t) {
//                this.algorithm = algorithm;
//                this.t = t;
//            }
//
//            public void run() {
//                this.startTime = System.currentTimeMillis();
//                nodes = algorithm.findMb(t.getNode());
//                done = true;
//                this.endTime = System.currentTimeMillis();
//            }
//
//            public List<Node> getNodes() {
//                return nodes;
//            }
//
//            public boolean isDone() {
//                return done;
//            }
//        }
//
//        MyThread thread = new MyThread(algorithm, t);
//        thread.start();
//
//        while (!thread.isDone()) {
//            long cur = System.currentTimeMillis();
//            long diff = cur - thread.startTime;
//            if (timeLimit != -1 && diff > timeLimit) {
//                System.out.println("Took too long: " + algorithm.getAlgorithmName());
//                thread.stop();
//                return null;
//            }
//
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
////        List<Node> nodes = algorithm.findMb(t.getNode());
//        List<Node> nodes = thread.getNodes();
//        List<String> mbf = extractVarNames(nodes, t);
//
////        if (algorithm instanceof MbFanSearch) {
////            System.out.println("Estimated by MBFS: " + ((MbFanSearch)algorithm).search(t.getNode()));
////        }
//
//        // Calculate intersection(mbf, truth).
//        List<String> mbfAndTruth = new ArrayList<String>(mbf);
//        mbfAndTruth.retainAll(_truth);
//
//        // Calculate MB false positives.
//        List<String> mbfFp = new ArrayList<String>(mbf);
//        mbfFp.removeAll(mbfAndTruth);
//        int fp = mbfFp.size();
//
////        System.out.println("FP" + mbfFp);
//
//        // Calculate MB false negatives.
//        List<String> mbfFn = new ArrayList<String>(_truth);
//        mbfFn.removeAll(mbfAndTruth);
//        int fn = mbfFn.size();
//
//        // Sum up truths.
//        int truth = _truth.size();
//
//        long elapsedTime = System.currentTimeMillis() - time;
//
//        println(out, i + ".\t" +
//                nf2.format(fp) + "\t" +
//                nf2.format(fn) + "\t" +
//                nf2.format(fp + fn) + "\t" +
//                nf2.format(truth - fn) + "\t" +
//                nf2.format(truth) + "\t" +
//                elapsedTime + " ms " + algorithm.getNumIndependenceTests() + "\t" +
//                algorithm.getAlgorithmName());
//
//        return new Stats(algorithm, fp, fn, fp + fn, truth, elapsedTime);
//    }

//    private Stats printGraphStats(MbSearch algorithm, Node target,
//                                  Graph trueMbDag,
//                                  DataSet dataSet, int i,
//                                  NumberFormat nf2, PrintWriter out) {
//        final long time = System.currentTimeMillis();
//
//        Graph estimatedMbDag;
//
//        if (algorithm instanceof Mbfs) {
//            algorithm.findMb(target.getNode());
//            estimatedMbDag = ((Mbfs) algorithm).getGraph();
//        } else {
//
//            // Algorithm is run here.
//            List<Node> nodes = algorithm.findMb(target.getNode());
//
//            nodes.add(target);
//
//            List<Node> _nodes = new ArrayList<Node>();
//
//            for (Node node : nodes) {
//                _nodes.add(dataSet.getCauseNode(node.getNode()));
//            }
//
//            DataSet _dataSet = dataSet.subsetColumns(_nodes);
//
//            Ges search = new Ges(_dataSet);
//            estimatedMbDag = search.search();
//
//            MbUtils.trimToMbNodes(estimatedMbDag, estimatedMbDag.getNode(target.getNode()),
//                    false);
//        }
//
//        long elapsedTime = System.currentTimeMillis() - time;
//
//
//        int truth = trueMbDag.getNumEdges();
//
//        int fp = 0;
//        int fn = 0;
//
//        for (Edge edge : estimatedMbDag.getEdges()) {
//            Node node1 = trueMbDag.getNode(edge.getNode1().getNode());
//            Node node2 = trueMbDag.getNode(edge.getNode2().getNode());
//
//            if (node1 == null || node2 == null) {
//                fp++;
//                continue;
//            }
//
//            Edge _edge = trueMbDag.getEdge(node1, node2);
//
//            if (_edge == null) {
//                fp++;
//            }
//        }
//
//        for (Edge edge : trueMbDag.getEdges()) {
//            Node node1 = estimatedMbDag.getNode(edge.getNode1().getNode());
//            Node node2 = estimatedMbDag.getNode(edge.getNode2().getNode());
//
//            if (node1 == null || node2 == null) {
//                fn++;
//                continue;
//            }
//
//            Edge _edge = estimatedMbDag.getEdge(node1, node2);
//
//            if (_edge == null) {
//                fn++;
//            }
//        }
//
//        println(out, i + ".\t" +
//                nf2.format(fp) + "\t" +
//                nf2.format(fn) + "\t" +
//                nf2.format(fp + fn) + "\t" +
//                nf2.format(truth - fn) + "\t" +
//                nf2.format(truth) + "\t" +
//                elapsedTime + " ms " +
//                algorithm.getAlgorithmName() + "\t");
//
//        return new Stats(algorithm, fp, fn, fp + fn, truth, elapsedTime);
//    }

//    private Stats printMbfsGraphStats(Node target, IndependenceTest test,
//                                      int depth, Graph trueMbDag, int i, NumberFormat nf2) {
//        final long time = System.currentTimeMillis();
//
//        Mbfs algorithm = new Mbfs(test, depth);
//        Graph estimatedMbDag = algorithm.search(target.getNode());
//
//        long elapsedTime = System.currentTimeMillis() - time;
//
//        int truth = trueMbDag.getNumEdges();
//
//        int fp = 0;
//        int fn = 0;
//
//        for (Edge edge : estimatedMbDag.getEdges()) {
//            Node node1 = trueMbDag.getNode(edge.getNode1().getNode());
//            Node node2 = trueMbDag.getNode(edge.getNode2().getNode());
//
//            if (node1 == null || node2 == null) {
//                fp++;
//                continue;
//            }
//
//            Edge _edge = trueMbDag.getEdge(node1, node2);
//
//            if (_edge == null) {
//                fp++;
//            }
//        }
//
//        for (Edge edge : trueMbDag.getEdges()) {
//            Node node1 = estimatedMbDag.getNode(edge.getNode1().getNode());
//            Node node2 = estimatedMbDag.getNode(edge.getNode2().getNode());
//
//            if (node1 == null || node2 == null) {
//                fn++;
//                continue;
//            }
//
//            Edge _edge = estimatedMbDag.getEdge(node1, node2);
//
//            if (_edge == null) {
//                fn++;
//            }
//        }
//
//        System.out.println(i + ". (M)\t" +
//                        nf2.format(fp) + "\t" +
//                        nf2.format(fn) + "\t" +
//                        nf2.format(fp + fn) + "\t" +
//                        nf2.format(truth - fn) + "\t" +
//                        nf2.format(truth) + "\t" +
//                        elapsedTime + " ms " +
//                        algorithm.getAlgorithmName() + "\t"
//        );
//
//        return new Stats(algorithm, fp, fn, fp + fn, truth, elapsedTime);
//    }

//    private DataSet simulateDiscrete(Dag randomGraph,
//                                     DataSet dataSet,
//                                     int sampleSize,
//                                     int minNumCategories,
//                                     int maxNumCategories) {
//        BayesPm bayesPm = new BayesPm(randomGraph, minNumCategories, maxNumCategories);
//        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
//
//        if (dataSet == null) {
//            dataSet = bayesIm.simulateData(sampleSize, false);
//        } else {
//            dataSet = bayesIm.simulateData(dataSet, false);
//        }
//        return dataSet;
//    }

//    private DataSet simulateContinuous(Dag randomGraph,
//                                       int sampleSize, DataSet dataSet) {
//        LargeSemSimulator simulator = new LargeSemSimulator(randomGraph);
//        return simulator.simulateDataAcyclic(sampleSize);
//    }

//    private class Stats {
//        private MbSearch algorithm;
//        private int fp;
//        private int fn;
//        private int errors;
//        private int truth;
//        private long time;
//
//        public Stats(MbSearch algorithm, int fp, int fn, int errors,
//                     int truth, long time) {
//            this.algorithm = algorithm;
//            this.fp = fp;
//            this.fn = fn;
//            this.errors = errors;
//            this.truth = truth;
//            this.time = time;
//        }
//
//        public MbSearch getAlgorithm() {
//            return algorithm;
//        }
//
//        public int getAfp() {
//            return fp;
//        }
//
//        public int getAfn() {
//            return fn;
//        }
//
//        public int getErrors() {
//            return errors;
//        }
//
//        public int getTrueDag() {
//            return truth;
//        }
//
//        public long getTime() {
//            return time;
//        }
//    }

//    private List<String> extractVarNames(List<Node> nodes, Node target) {
//        List<String> varNames = new ArrayList<String>();
//
//        for (Node node : nodes) {
//            varNames.add(node.getNode());
//        }
//
//        varNames.remove(target.getNode());
//        Collections.sort(varNames);
//        return varNames;
//    }

//    public void findExample() {
//        List<Node> nodes1 = new ArrayList<Node>();
//
//        for (int i = 0; i < 10; i++) {
//            nodes1.add(new ContinuousVariable("X" + (i + 1)));
//        }
//
//        Dag dag = new Dag(GraphUtils.randomGraph(nodes1, 0, 10,
//                5, 5, 5, false));
//        IndependenceTest test = new IndTestDSep(dag);
//        Mbfs search = new Mbfs(test, -1);
//
//        System.out.println("INDEPENDENT Graph: " + dag);
//
//        List<Node> nodes = dag.getNodes();
//
//        for (Node node : nodes) {
//            Graph resultMb = search.search(node.getNode());
//            Graph trueMb = GraphUtils.markovBlanketDag(node, dag);
//
//            List<Node> resultNodes = resultMb.getNodes();
//            List<Node> trueNodes = trueMb.getNodes();
//
//            Set<String> resultNames = new HashSet<String>();
//
//            for (Node resultNode : resultNodes) {
//                resultNames.add(resultNode.getNode());
//            }
//
//            Set<String> trueNames = new HashSet<String>();
//
//            for (Node v : trueNodes) {
//                trueNames.add(v.getNode());
//            }
//
//            assertTrue(resultNames.equals(trueNames));
//
//            Set<Edge> resultEdges = resultMb.getEdges();
//
//            for (Edge resultEdge : resultEdges) {
//                if (Edges.isDirectedEdge(resultEdge)) {
//                    String name1 = resultEdge.getNode1().getNode();
//                    String name2 = resultEdge.getNode2().getNode();
//
//                    Node node1 = trueMb.getNode(name1);
//                    Node node2 = trueMb.getNode(name2);
//
//                    // If one of these nodes is null, probably it's because some
//                    // parent of the target could not be oriented as such, and
//                    // extra nodes and edges are being included to cover the
//                    // possibility that the node is actually a child.
//                    if (node1 == null) {
//                        System.err.println(
//                                "Node " + name1 + " is not in the true graph.");
//                        continue;
//                    }
//
//                    if (node2 == null) {
//                        System.err.println(
//                                "Node " + name2 + " is not in the true graph.");
//                        continue;
//                    }
//
//                    Edge trueEdge = trueMb.getEdge(node1, node2);
//
//                    if (trueEdge == null) {
//                        Node resultNode1 = resultMb.getNode(node1.getNode());
//                        Node resultNode2 = resultMb.getNode(node2.getNode());
//                        Node resultTarget = resultMb.getNode(node.getNode());
//
//                        Edge a = resultMb.getEdge(resultNode1, resultTarget);
//                        Edge b = resultMb.getEdge(resultNode2, resultTarget);
//
//                        if (a == null || b == null) {
//                            continue;
//                        }
//
//                        if ((Edges.isDirectedEdge(a) &&
//                                Edges.isUndirectedEdge(b)) || (
//                                Edges.isUndirectedEdge(a) &&
//                                        Edges.isDirectedEdge(b))) {
//                            continue;
//                        }
//
//                        fail("EXTRA EDGE: Edge in result MB but not true MB = " +
//                                resultEdge);
//                    }
//
//                    assertEquals(resultEdge.getEndpoint1(),
//                            trueEdge.getEndpoint1());
//                    assertEquals(resultEdge.getEndpoint2(),
//                            trueEdge.getEndpoint2());
//
//                    System.out.println("Result edge = " + resultEdge +
//                            ", true edge = " + trueEdge);
//                }
//            }
//
//            // Make sure that if adj(X, Y) in the true graph that adj(X, Y)
//            // in the result graph.
//            Set<Edge> trueEdges = trueMb.getEdges();
//
//            for (Edge trueEdge : trueEdges) {
//                Node node1 = trueEdge.getNode1();
//                Node node2 = trueEdge.getNode2();
//
//                Node resultNode1 = resultMb.getNode(node1.getNode());
//                Node resultNode2 = resultMb.getNode(node2.getNode());
//
//                assertTrue("Expected adjacency " + resultNode1 + "---" +
//                                resultNode2,
//                        resultMb.isAdjacentTo(resultNode1, resultNode2));
//            }
//        }
//    }

//    public void test2() {
//        Graph graph = new EdgeListGraph();
//
//        Node t = new GraphNode("t");
//        Node x = new GraphNode("x");
//        Node y = new GraphNode("y");
//        Node w = new GraphNode("w");
//
//        graph.addIndex(t);
//        graph.addIndex(x);
//        graph.addIndex(y);
//        graph.addIndex(w);
//
//        graph.addDirectedEdge(t, x);
//        graph.addDirectedEdge(x, y);
//        graph.addDirectedEdge(w, x);
//        graph.addDirectedEdge(w, y);
//
//        IndependenceTest test = new IndTestGraph(graph);
//
//        Hiton search = new Hiton(test, 3);
//
//        search.findMb(t.getNode());
//        List<Node> mb = search.hitonPc(t);
//
//        System.out.println(mb);
//    }

//    class SimulationParams {
//        private boolean discrete = false;
//        private int numVars = 100;
//        private double edgeMultipler = 1.0;
//        private int sampleSize = 1000;
//        private boolean randomGraphEveryTime = true;
//        private long timeLimit = 300000;
//        private int depth = 3;
//        private int numTests = 30;
//        private int minMbSize = 8;
//        private double alpha = 0.05;
//        private int minNumCategories = 2;
//        private int maxNumCategories = 4;
//        private List<String> algNames = new LinkedList<String>();
//
//        public boolean isDiscrete() {
//            return discrete;
//        }
//
//        public void setDiscrete(boolean discrete) {
//            this.discrete = discrete;
//        }
//
//        public int getNumVars() {
//            return numVars;
//        }
//
//        public void setNumVars(int numVars) {
//            this.numVars = numVars;
//        }
//
//        public boolean isRandomGraphEveryTime() {
//            return randomGraphEveryTime;
//        }
//
//        public void setRandomGraphEveryTime(boolean randomGraphEveryTime) {
//            this.randomGraphEveryTime = randomGraphEveryTime;
//        }
//
//        public long getTimeLimit() {
//            return timeLimit;
//        }
//
//        public void setTimeLimit(long timeLimit) {
//            this.timeLimit = timeLimit;
//        }
//
//        public int getMaxIndegree() {
//            return depth;
//        }
//
//        public void setMaxIndegree(int depth) {
//            this.depth = depth;
//        }
//
//        public int getNumTests() {
//            return numTests;
//        }
//
//        public void setNumTests(int numTests) {
//            this.numTests = numTests;
//        }
//
//        public int getMinMbSize() {
//            return minMbSize;
//        }
//
//        public void setMinMbSize(int minMbSize) {
//            this.minMbSize = minMbSize;
//        }
//
//        public double getAlternativePenalty() {
//            return alpha;
//        }
//
//        public void setAlternativePenalty(double alpha) {
//            this.alpha = alpha;
//        }
//
//        public int getMinNumCategories() {
//            return minNumCategories;
//        }
//
//        public void setMinNumCategories(int minNumCategories) {
//            this.minNumCategories = minNumCategories;
//        }
//
//        public int getMaxNumCategories() {
//            return maxNumCategories;
//        }
//
//        public void setMaxNumCategories(int maxNumCategories) {
//            this.maxNumCategories = maxNumCategories;
//        }
//
//        public List<String> getAlgNames() {
//            return algNames;
//        }
//
//        public void setAlgNames(List<String> algNames) {
//            this.algNames = algNames;
//        }
//
//        public int getSampleSize() {
//            return sampleSize;
//        }
//
//        public void setSampleSize(int sampleSize) {
//            this.sampleSize = sampleSize;
//        }
//
//        public double getEdgeMultipler() {
//            return edgeMultipler;
//        }
//
//        public void setEdgeMultipler(double edgeMultipler) {
//            this.edgeMultipler = edgeMultipler;
//        }
//    }
}





