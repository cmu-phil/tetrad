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
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestPc {

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch2() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch3() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch4() {
        IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---B,B-->C,D", /*"A---B,B-->C,A-->D,C-->D", */
                knowledge);
    }

    @Test
    public void testCites() {
        String citesString = "164\n" +
                "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                "1.0\n" +
                ".62\t1.0\n" +
                ".25\t.09\t1.0\n" +
                ".16\t.28\t.07\t1.0\n" +
                "-.10\t.00\t.03\t.10\t1.0\n" +
                ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        char[] citesChars = citesString.toCharArray();
        DataReader reader = new DataReader();
        ICovarianceMatrix dataSet = reader.parseCovariance(citesChars);

        IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.11));
        pc.setKnowledge(knowledge);

        Graph pattern = pc.search();

        Graph _true = new EdgeListGraph(pattern.getNodes());

        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("GPQ"));
        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("PREPROD"));
        _true.addDirectedEdge(pattern.getNode("GPQ"), pattern.getNode("QFJ"));
        _true.addDirectedEdge(pattern.getNode("PREPROD"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("PREPROD"), pattern.getNode("PUBS"));
        _true.addDirectedEdge(pattern.getNode("PUBS"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("QFJ"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("QFJ"), pattern.getNode("PUBS"));
        _true.addDirectedEdge(pattern.getNode("SEX"), pattern.getNode("PUBS"));

        System.out.println(pattern);

        assertEquals(pattern, _true);
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        Pc pc = new Pc(independence);

        // Run search
//        Graph resultGraph = pc.search();
        Graph resultGraph = pc.search(new Fas(independence), independence.getVariables());

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
//        System.out.println("\nInput graph:");
//        System.out.println(graph);
//        System.out.println("\nResult graph:");
//        System.out.println(resultGraph);
//        System.out.println("\nTrue graph:");
//        System.out.println(trueGraph);

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String outputGraph,
                                    IKnowledge knowledge) {
        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        Pc pc = new Pc(independence);

        // Set up search.
        pc.setKnowledge(knowledge);
//        pc.setVerbose(false);

        // Run search
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

//        System.out.println("Knowledge = " + knowledge);
//        System.out.println("True graph = " + graph);
//        System.out.println("Result graph = " + resultGraph);

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    @Test
    public void checkPattern() {
        for (int i = 0; i < 2; i++) {
            Graph graph = GraphUtils.randomGraph(100, 0, 100, 100,
                    100, 100, false);
            IndTestDSep test = new IndTestDSep(graph);
            Pc pc = new Pc(test);
            Graph pattern = pc.search();
            Graph pattern2 = SearchGraphUtils.patternFromDag(graph);
            assertEquals(pattern, pattern2);
        }
    }

    @Test
    public void testPcStable2() {
        RandomUtil.getInstance().setSeed(1450030184196L);
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = GraphUtils.randomGraph(nodes, 0, 10, 30, 15, 15, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(200, false);

        TetradLogger.getInstance().setForceLog(false);
        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        PcStableMax pc = new PcStableMax(test);
        pc.setVerbose(false);
        Graph pattern = pc.search();

        for (int i = 0; i < 1; i++) {
            DataSet data2 = DataUtils.reorderColumns(data);
            IndependenceTest test2 = new IndTestFisherZ(data2, 0.05);
            PcStableMax pc2 = new PcStableMax(test2);
            pc2.setVerbose(false);
            Graph pattern2 = pc2.search();
            assertTrue(pattern.equals(pattern2));
        }
    }

//    @Test
    public void testPcFci() {

        String[] algorithms = {"PC", "CPC", "FGES", "FCI", "GFCI", "RFCI", "CFCI"};
        String[] statLabels = {"AP", "TP", "BP", "NA", "NT", "NB", "E"/*, "AP/E"*/};

        int numMeasures = 200;
        double edgeFactor = 1.0;

        int numRuns = 5;
        int maxLatents = numMeasures;
        int jumpLatents = maxLatents / 5;
        double alpha = 0.01;
        double penaltyDiscount = 2.0;
        double ofInterestCutoff = 0.1;

        if (maxLatents % jumpLatents != 0) throw new IllegalStateException();
        int numLatentGroups = maxLatents / jumpLatents + 1;

        double[][][] allAllRet = new double[numLatentGroups][][];
        int latentIndex = -1;

        for (int numLatents = 0; numLatents <= maxLatents; numLatents += jumpLatents) {
            latentIndex++;

            System.out.println();

            System.out.println("num latents = " + numLatents);
            System.out.println("num measures = " + numMeasures);
            System.out.println("edge factor = " + edgeFactor);
            System.out.println("alpha = " + alpha);
            System.out.println("penaltyDiscount = " + penaltyDiscount);
            System.out.println("num runs = " + numRuns);

            double[][] allRet = new double[algorithms.length][];

            for (int t = 0; t < algorithms.length; t++) {
                allRet[t] = printStats(algorithms, t, true, numRuns, alpha, penaltyDiscount, maxLatents,
                        numLatents, edgeFactor);
            }

            allAllRet[latentIndex] = allRet;
        }

        System.out.println();
        System.out.println("=======");
        System.out.println();
        System.out.println("Algorithms with max = " + ofInterestCutoff + "*(max - min) < stat <= max.");
        System.out.println();
        System.out.println("AP = Average Arrow Precision; TP = Average Tail Precision");
        System.out.println("BP = Average Bidirected Precision; NA = Average Number of Arrows");
        System.out.println("NT = Average Number of Tails; NB = Average Number of Bidirected");
        System.out.println("E = Averaged Elapsed Time (ms), AP/P");
        System.out.println();
        System.out.println("num latents = 0 to " + maxLatents);
        System.out.println("alpha = " + alpha);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("num runs = " + numRuns);
        System.out.println();
        System.out.println("num measures = " + numMeasures);
        System.out.println("edge factor = " + edgeFactor);


        printBestStats(allAllRet, algorithms, statLabels, maxLatents, jumpLatents, ofInterestCutoff);
    }

    private double[] printStats(String[] algorithms, int t, boolean directed, int numRuns,
                                double alpha, double penaltyDiscount,
                                int numMeasures, int numLatents,
                                double edgeFactor) {
        NumberFormat nf = new DecimalFormat("0.00");

        double sumArrowPrecision = 0.0;
        double sumTailPrecision = 0.0;
        double sumBidirectedPrecision = 0.0;
        int numArrows = 0;
        int numTails = 0;
        int numBidirected = 0;
        int count = 0;
        int totalElapsed = 0;

        int countAP = 0;
        int countTP = 0;
        int countBP = 0;

        for (int i = 0; i < numRuns; i++) {
            int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            List<Node> nodes = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
                names.add(name);
            }

            Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(1000, false);

            IndTestFisherZ test = new IndTestFisherZ(data, alpha);

            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
            score.setPenaltyDiscount(penaltyDiscount);
            GraphSearch search;

            switch (t) {
                case 0:
                    search = new Pc(test);
                    break;
                case 1:
                    search = new Cpc(test);
                    break;
                case 2:
                    search = new Fges(score);
                    break;
                case 3:
                    search = new Fci(test);
                    break;
                case 4:
                    search = new GFci(test, score);
                    break;
                case 5:
                    search = new Rfci(test);
                    break;
                case 6:
                    search = new Cfci(test);
                    break;
                default:
                    throw new IllegalStateException();
            }

            long start = System.currentTimeMillis();

            Graph out = search.search();

            long stop = System.currentTimeMillis();

            long elapsed = stop - start;
            totalElapsed += elapsed;

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            int arrowsTp = 0;
            int arrowsFp = 0;
            int tailsTp = 0;
            int tailsFp = 0;
            int bidirectedTp = 0;
            int bidirectedFp = 0;

//            out = outClosure(out);

            for (Edge edge : out.getEdges()) {
                if (directed && !(edge.isDirected() || Edges.isBidirectedEdge(edge))) {
                    continue;
                }

                if (edge.getEndpoint1() == Endpoint.ARROW) {
                    if (!dag.isAncestorOf(edge.getNode1(), edge.getNode2()) &&
                            dag.existsTrek(edge.getNode1(), edge.getNode2())) {
                        arrowsTp++;
                    } else {
                        arrowsFp++;
                    }

                    numArrows++;
                }

                if (edge.getEndpoint2() == Endpoint.ARROW) {
                    if (!dag.isAncestorOf(edge.getNode2(), edge.getNode1()) &&
                            dag.existsTrek(edge.getNode1(), edge.getNode2())) {
                        arrowsTp++;
                    } else {
                        arrowsFp++;
                    }

                    numArrows++;
                }

                if (edge.getEndpoint1() == Endpoint.TAIL) {
                    if (dag.existsDirectedPathFromTo(edge.getNode1(), edge.getNode2())) {
                        tailsTp++;
                    } else {
                        tailsFp++;
                    }

                    numTails++;
                }

                if (edge.getEndpoint2() == Endpoint.TAIL) {
                    if (dag.existsDirectedPathFromTo(edge.getNode2(), edge.getNode1())) {
                        tailsTp++;
                    } else {
                        tailsFp++;
                    }

                    numTails++;
                }

                if (Edges.isBidirectedEdge(edge)) {
                    if (!dag.isAncestorOf(edge.getNode1(), edge.getNode2())
                            && !dag.isAncestorOf(edge.getNode2(), edge.getNode1())
                            && dag.existsTrek(edge.getNode1(), edge.getNode2())) {
                        bidirectedTp++;
                    } else {
                        bidirectedFp++;
                    }

                    numBidirected++;
                }
            }

            double arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
            double tailPrecision = tailsTp / (double) (tailsTp + tailsFp);
            double bidirectedPrecision = bidirectedTp / (double) (bidirectedTp + bidirectedFp);

            if (!Double.isNaN(arrowPrecision)) {
                sumArrowPrecision += arrowPrecision;
                countAP++;
            }

            if (!Double.isNaN(tailPrecision)) {
                sumTailPrecision += tailPrecision;
                countTP++;
            }

            if (!Double.isNaN(bidirectedPrecision)) {
                sumBidirectedPrecision += bidirectedPrecision;
                countBP++;
            }

            count++;
        }

        double avgArrowPrecision = sumArrowPrecision / (double) countAP;
        double avgTailPrecision = sumTailPrecision / (double) countTP;
        double avgBidirectedPrecision = sumBidirectedPrecision / (double) countBP;
        double avgNumArrows = numArrows / (double) count;
        double avgNumTails = numTails / (double) count;
        double avgNumBidirected = numBidirected / (double) count;
        double avgElapsed = totalElapsed / (double) numRuns;
//        double avgRatioPrecisionToElapsed = avgArrowPrecision / avgElapsed;

        double[] ret = new double[]{
                avgArrowPrecision,
                avgTailPrecision,
                avgBidirectedPrecision,
                avgNumArrows,
                avgNumTails,
                avgNumBidirected,
                -avgElapsed, // minimize
//                avgRatioPrecisionToElapsed
        };

        System.out.println();

        NumberFormat nf2 = new DecimalFormat("0.0000");

        System.out.println(algorithms[t] + " arrow precision " + nf.format(avgArrowPrecision));
        System.out.println(algorithms[t] + " tail precision " + nf.format(avgTailPrecision));
        System.out.println(algorithms[t] + " bidirected precision " + nf.format(avgBidirectedPrecision));
        System.out.println(algorithms[t] + " avg num arrow " + nf.format(avgNumArrows));
        System.out.println(algorithms[t] + " avg num tails " + nf.format(avgNumTails));
        System.out.println(algorithms[t] + " avg num bidirected " + nf.format(avgNumBidirected));
        System.out.println(algorithms[t] + " avg elapsed " + nf.format(avgElapsed));
//        System.out.println(algorithm[t] + " avg precision / elapsed " + nf2.format(avgRatioPrecisionToElapsed));

        return ret;
    }

    private Graph outClosure(Graph out) {
        Graph revised = new EdgeListGraph(out);

        for (Node n : out.getNodes()) {
            for (Node m : out.getNodesOutTo(n, Endpoint.ARROW)) {
                Edge e = out.getEdge(n, m);
                Endpoint proximalEndpoint = e.getProximalEndpoint(n);
                if (proximalEndpoint == Endpoint.CIRCLE || proximalEndpoint == Endpoint.TAIL) {
                    List<Node> descendants = out.getDescendants(Collections.singletonList(m));

                    for (Node o : descendants) {
                        if (!revised.isAdjacentTo(m, o)) {
                            revised.addEdge(new Edge(n, o, proximalEndpoint, Endpoint.ARROW));
                        }
                    }
                }
            }
        }

        return revised;
    }


    private void printBestStats(double[][][] allAllRet, String[] algorithms, String[] statLabels,
                                int maxLatents, int jumpLatents, double ofInterestCutoff) {
        TextTable table = new TextTable(allAllRet.length + 1, allAllRet[0][0].length + 1);

        int latentIndex = -1;

        class Pair {
            private String algorithm;
            private double stat;

            public Pair(String algorithm, double stat) {
                this.algorithm = algorithm;
                this.stat = stat;
            }

            public String getAlgorithm() {
                return algorithm;
            }

            public double getStat() {
                return stat;
            }
        }

        for (int numLatents = 0; numLatents <= maxLatents; numLatents += jumpLatents) {
            latentIndex++;

            table.setToken(latentIndex + 1, 0, numLatents + "");

            for (int statIndex = 0; statIndex < allAllRet[latentIndex][0].length; statIndex++) {
//                double maxStat = Double.NaN;
                String maxAlg = "-";

                List<Pair> algStats = new ArrayList<>();

                for (int t = 0; t < algorithms.length; t++) {
                    double stat = allAllRet[latentIndex][t][statIndex];
                    if (!Double.isNaN(stat)) {
                        algStats.add(new Pair(algorithms[t], stat));
                    }
                }

                if (algStats.isEmpty()) {
                    maxAlg = "-";
                } else {
                    Collections.sort(algStats, new Comparator<Pair>() {

                        @Override
                        public int compare(Pair o1, Pair o2) {
                            return -Double.compare(o1.getStat(), o2.getStat());
                        }
                    });

                    double maxStat = algStats.get(0).getStat();
                    maxAlg = algStats.get(0).getAlgorithm();

                    double minStat = algStats.get(algStats.size() - 1).getStat();

                    double diff = maxStat - minStat;
                    double ofInterest = maxStat - ofInterestCutoff * (diff);

                    for (int i = 1; i < algStats.size(); i++) {
                        if (algStats.get(i).getStat() >= ofInterest) {
                            maxAlg += "," + algStats.get(i).getAlgorithm();
                        }
                    }
                }

                table.setToken(latentIndex + 1, statIndex + 1, maxAlg);
            }
        }

        for (int j = 0; j < statLabels.length; j++) {
            table.setToken(0, j + 1, statLabels[j]);
        }

        System.out.println();

        System.out.println(table.toString());
    }

//    @Test
    public void testPcRegression() {

        String[] algorithms = {"PC", "CPC", "FGES", "FCI", "GFCI", "RFCI", "CFCI", "Regression"};
        String[] statLabels = {"AP", "AR"};

        int numMeasures = 10;
        double edgeFactor = 2.0;

        int numRuns = 5;
        int maxLatents = numMeasures;
        int jumpLatents = maxLatents / 5;
        double alpha = 0.1;
        double penaltyDiscount = 4.0;
        double ofInterestCutoff = 0.01;
        int sampleSize = 10000;

        if (maxLatents % jumpLatents != 0) throw new IllegalStateException();
        int numLatentGroups = maxLatents / jumpLatents + 1;

        double[][][] allAllRet = new double[numLatentGroups][][];
        int latentIndex = -1;

        for (int numLatents = 0; numLatents <= maxLatents; numLatents += jumpLatents) {
            latentIndex++;

            System.out.println();

            System.out.println("num latents = " + numLatents);
            System.out.println("num measures = " + numMeasures);
            System.out.println("edge factor = " + edgeFactor);
            System.out.println("alpha = " + alpha);
            System.out.println("penaltyDiscount = " + penaltyDiscount);
            System.out.println("num runs = " + numRuns);
            System.out.println("sample size = " + sampleSize);

            double[][] allRet = new double[algorithms.length][];

            for (int t = 0; t < algorithms.length; t++) {
                allRet[t] = printStatsPcRegression(algorithms, t, true, numRuns, alpha, penaltyDiscount, maxLatents,
                        numLatents, edgeFactor, sampleSize);
            }

            allAllRet[latentIndex] = allRet;
        }

        System.out.println();
        System.out.println("=======");
        System.out.println();
        System.out.println("Algorithms with max = " + ofInterestCutoff + "*(max - min) <= stat <= max.");
        System.out.println();
        System.out.println("AP = Average Adjacency Precision; AR = Average Adjacency Recall");
        System.out.println();
        System.out.println("num latents = 0 to " + maxLatents);
        System.out.println("alpha = " + alpha);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("num runs = " + numRuns);
        System.out.println();
        System.out.println("num measures = " + numMeasures);
        System.out.println("edge factor = " + edgeFactor);


        printBestStats(allAllRet, algorithms, statLabels, maxLatents, jumpLatents, ofInterestCutoff);
    }

    private double[] printStatsPcRegression(String[] algorithms, int t, boolean directed, int numRuns,
                                            double alpha, double penaltyDiscount,
                                            int numMeasures, int numLatents,
                                            double edgeFactor, int sampleSize) {
        NumberFormat nf = new DecimalFormat("0.00");

        double sumAdjPrecision = 0.0;
        double sumAdjRecall = 0.0;
        int count = 0;

        for (int i = 0; i < numRuns; i++) {
            int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            List<Node> nodes = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
                names.add(name);
            }

            Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(sampleSize, false);

//            Graph comparison = dag;
            Graph comparison = new DagToPag(dag).convert();
//            Graph comparison = new Pc(new IndTestDSep(dag)).search();

            IndTestFisherZ test = new IndTestFisherZ(data, alpha);


            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
            score.setPenaltyDiscount(penaltyDiscount);
            GraphSearch search;
            Graph out;

            Node target = null;

            for (Node node : nodes) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    target = node;
                    break;
                }
            }

            switch (t) {
                case 0:
                    search = new Pc(test);
                    out = search.search();
                    break;
                case 1:
                    search = new Cpc(test);
                    out = search.search();
                    break;
                case 2:
                    search = new Fges(score);
                    out = search.search();
                    break;
                case 3:
                    search = new Fci(test);
                    out = search.search();
                    break;
                case 4:
                    search = new GFci(test, score);
                    out = search.search();
                    break;
                case 5:
                    search = new Rfci(test);
                    out = search.search();
                    break;
                case 6:
                    search = new Cfci(test);
                    out = search.search();
                    break;
                case 7:
                    out = getRegressionGraph(data, target);
                    break;
                default:
                    throw new IllegalStateException();
            }

            target = out.getNode(target.getName());

            out = trim(out, target);

            long start = System.currentTimeMillis();

            long stop = System.currentTimeMillis();

            long elapsed = stop - start;

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            for (Node node : dag.getNodes()) {
                if (!out.containsNode(node)) {
                    out.addNode(node);
                }
            }

            int adjTp = 0;
            int adjFp = 0;
            int adjFn = 0;

            for (Node node : out.getAdjacentNodes(target)) {
                if (comparison.isAdjacentTo(target, node)) {
                    adjTp++;
                } else {
                    adjFp++;
                }
            }

            for (Node node : dag.getAdjacentNodes(target)) {
                if (!out.isAdjacentTo(target, node)) {
                    adjFn++;
                }
            }

            double adjPrecision = adjTp / (double) (adjTp + adjFp);
            double adjRecall = adjTp / (double) (adjTp + adjFn);

            if (!Double.isNaN(adjPrecision)) {
                sumAdjPrecision += adjPrecision;
            }

            if (!Double.isNaN(adjRecall)) {
                sumAdjRecall += adjRecall;
            }

            count++;
        }

        double avgAdjPrecision = sumAdjPrecision / (double) count;
        double avgAdjRecall = sumAdjRecall / (double) count;

        double[] ret = new double[]{
                avgAdjPrecision,
                avgAdjRecall,
        };

        System.out.println();

        System.out.println(algorithms[t] + " adj precision " + nf.format(avgAdjPrecision));
        System.out.println(algorithms[t] + " adj recall " + nf.format(avgAdjRecall));

        return ret;
    }

    private Graph trim(Graph out, Node target) {
        Graph trimmed = new EdgeListGraph(out.getNodes());

        if (!out.containsNode(target)) throw new IllegalArgumentException();

        for (Node node : out.getAdjacentNodes(target)) {
            trimmed.addUndirectedEdge(target, node);
        }

        return trimmed;
    }

    private Graph getRegressionGraph(DataSet data, Node target) {
        List<Node> rest = new ArrayList<>(data.getVariables());
        rest.remove(target);
        RegressionDataset regressionDataset = new RegressionDataset(data);
        regressionDataset.regress(target, rest);
        Graph graph = regressionDataset.getGraph();
        graph = GraphUtils.replaceNodes(graph, data.getVariables());
        return graph;
    }
}





