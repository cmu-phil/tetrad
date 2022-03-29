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
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
        final IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---B,B-->C,D",
                knowledge);
    }

    @Test
    public void testCites() {
        final String citesString = "164\n" +
                "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                "1.0\n" +
                ".62\t1.0\n" +
                ".25\t.09\t1.0\n" +
                ".16\t.28\t.07\t1.0\n" +
                "-.10\t.00\t.03\t.10\t1.0\n" +
                ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        final char[] citesChars = citesString.toCharArray();
        final ICovarianceMatrix dataSet = DataUtils.parseCovariance(citesChars, "//", DelimiterType.WHITESPACE, '\"', "*");

        final IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        final Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.05));
        pc.setKnowledge(knowledge);

        Graph CPDAG = pc.search();

        final String trueString = "Graph Nodes:\n" +
                "ABILITY;GPQ;PREPROD;QFJ;SEX;CITES;PUBS\n" +
                "\n" +
                "Graph Edges:\n" +
                "1. ABILITY --> CITES\n" +
                "2. ABILITY --> GPQ\n" +
                "3. ABILITY --> PREPROD\n" +
                "4. GPQ --> QFJ\n" +
                "5. PREPROD --> CITES\n" +
                "6. PUBS --> CITES\n" +
                "7. QFJ --> CITES\n" +
                "8. QFJ --> PUBS\n" +
                "9. SEX --> PUBS";

        Graph trueGraph = null;


        try {
            trueGraph = GraphUtils.readerToGraphTxt(trueString);
            CPDAG = GraphUtils.replaceNodes(CPDAG, trueGraph.getNodes());
            assertEquals(trueGraph, CPDAG);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(final String inputGraph, final String outputGraph) {

        // Set up graph and node objects.
        final Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        final IndependenceTest independence = new IndTestDSep(graph);
        final Pc pc = new Pc(independence);

        // Run search
//        Graph resultGraph = pc.search();
        Graph resultGraph = pc.search(new Fas(independence), independence.getVariables());

        // Build comparison graph.
        final Graph trueGraph = GraphConverter.convert(outputGraph);

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
    private void checkWithKnowledge(final String inputGraph, final String outputGraph,
                                    final IKnowledge knowledge) {
        // Set up graph and node objects.
        final Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        final IndependenceTest independence = new IndTestDSep(graph);
        final Pc pc = new Pc(independence);

        // Set up search.
        pc.setKnowledge(knowledge);
//        pc.setVerbose(false);

        // Run search
        final Graph resultGraph = pc.search();

        // Build comparison graph.
        final Graph trueGraph = GraphConverter.convert(outputGraph);

//        System.out.println("Knowledge = " + knowledge);
        System.out.println("True graph = " + graph);
        System.out.println("Result graph = " + resultGraph);

        // Do test.
        assertEquals(trueGraph, resultGraph);
    }

    @Test
    public void checknumCPDAGsToStore() {
        for (int i = 0; i < 2; i++) {
            final Graph graph = GraphUtils.randomGraph(100, 0, 100, 100,
                    100, 100, false);
            final IndTestDSep test = new IndTestDSep(graph);
            final Pc pc = new Pc(test);
            final Graph CPDAG = pc.search();
            final Graph CPDAG2 = SearchGraphUtils.cpdagFromDag(graph);
            assertEquals(CPDAG, CPDAG2);
        }
    }

    //    @Test
    public void testPcFci() {

        final String[] algorithms = {"PC", "CPC", "FGES", "FCI", "GFCI", "RFCI", "CFCI"};
        final String[] statLabels = {"AP", "TP", "BP", "NA", "NT", "NB", "E"/*, "AP/E"*/};

        final int numMeasures = 200;
        final double edgeFactor = 1.0;

        final int numRuns = 5;
        final int maxLatents = numMeasures;
        final int jumpLatents = maxLatents / 5;
        final double alpha = 0.01;
        final double penaltyDiscount = 2.0;
        final double ofInterestCutoff = 0.1;

        if (maxLatents % jumpLatents != 0) throw new IllegalStateException();
        final int numLatentGroups = maxLatents / jumpLatents + 1;

        final double[][][] allAllRet = new double[numLatentGroups][][];
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

            final double[][] allRet = new double[algorithms.length][];

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

    private double[] printStats(final String[] algorithms, final int t, final boolean directed, final int numRuns,
                                final double alpha, final double penaltyDiscount,
                                final int numMeasures, final int numLatents,
                                final double edgeFactor) {
        final NumberFormat nf = new DecimalFormat("0.00");

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
            final int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            final List<Node> nodes = new ArrayList<>();
            final List<String> names = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                final String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
                names.add(name);
            }

            final Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false);
            final SemPm pm = new SemPm(dag);
            final SemIm im = new SemIm(pm);
            final DataSet data = im.simulateData(1000, false);

            final IndTestFisherZ test = new IndTestFisherZ(data, alpha);

            final SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
            score.setPenaltyDiscount(penaltyDiscount);
            final GraphSearch search;

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

            final long start = System.currentTimeMillis();

            Graph out = search.search();

            final long stop = System.currentTimeMillis();

            final long elapsed = stop - start;
            totalElapsed += elapsed;

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            int arrowsTp = 0;
            int arrowsFp = 0;
            int tailsTp = 0;
            int tailsFp = 0;
            int bidirectedTp = 0;
            int bidirectedFp = 0;

//            out = outClosure(out);

            for (final Edge edge : out.getEdges()) {
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

            final double arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
            final double tailPrecision = tailsTp / (double) (tailsTp + tailsFp);
            final double bidirectedPrecision = bidirectedTp / (double) (bidirectedTp + bidirectedFp);

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

        final double avgArrowPrecision = sumArrowPrecision / (double) countAP;
        final double avgTailPrecision = sumTailPrecision / (double) countTP;
        final double avgBidirectedPrecision = sumBidirectedPrecision / (double) countBP;
        final double avgNumArrows = numArrows / (double) count;
        final double avgNumTails = numTails / (double) count;
        final double avgNumBidirected = numBidirected / (double) count;
        final double avgElapsed = totalElapsed / (double) numRuns;
//        double avgRatioPrecisionToElapsed = avgArrowPrecision / avgElapsed;

        final double[] ret = {
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

        final NumberFormat nf2 = new DecimalFormat("0.0000");

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

    private Graph outClosure(final Graph out) {
        final Graph revised = new EdgeListGraph(out);

        for (final Node n : out.getNodes()) {
            for (final Node m : out.getNodesOutTo(n, Endpoint.ARROW)) {
                final Edge e = out.getEdge(n, m);
                final Endpoint proximalEndpoint = e.getProximalEndpoint(n);
                if (proximalEndpoint == Endpoint.CIRCLE || proximalEndpoint == Endpoint.TAIL) {
                    final List<Node> descendants = out.getDescendants(Collections.singletonList(m));

                    for (final Node o : descendants) {
                        if (!revised.isAdjacentTo(m, o)) {
                            revised.addEdge(new Edge(n, o, proximalEndpoint, Endpoint.ARROW));
                        }
                    }
                }
            }
        }

        return revised;
    }


    private void printBestStats(final double[][][] allAllRet, final String[] algorithms, final String[] statLabels,
                                final int maxLatents, final int jumpLatents, final double ofInterestCutoff) {
        final TextTable table = new TextTable(allAllRet.length + 1, allAllRet[0][0].length + 1);

        int latentIndex = -1;

        class Pair {
            private final String algorithm;
            private final double stat;

            public Pair(final String algorithm, final double stat) {
                this.algorithm = algorithm;
                this.stat = stat;
            }

            public String getAlgorithm() {
                return this.algorithm;
            }

            public double getStat() {
                return this.stat;
            }
        }

        for (int numLatents = 0; numLatents <= maxLatents; numLatents += jumpLatents) {
            latentIndex++;

            table.setToken(latentIndex + 1, 0, numLatents + "");

            for (int statIndex = 0; statIndex < allAllRet[latentIndex][0].length; statIndex++) {
//                double maxStat = Double.NaN;
                String maxAlg = "-";

                final List<Pair> algStats = new ArrayList<>();

                for (int t = 0; t < algorithms.length; t++) {
                    final double stat = allAllRet[latentIndex][t][statIndex];
                    if (!Double.isNaN(stat)) {
                        algStats.add(new Pair(algorithms[t], stat));
                    }
                }

                if (algStats.isEmpty()) {
                    maxAlg = "-";
                } else {
                    Collections.sort(algStats, new Comparator<Pair>() {

                        @Override
                        public int compare(final Pair o1, final Pair o2) {
                            return -Double.compare(o1.getStat(), o2.getStat());
                        }
                    });

                    final double maxStat = algStats.get(0).getStat();
                    maxAlg = algStats.get(0).getAlgorithm();

                    final double minStat = algStats.get(algStats.size() - 1).getStat();

                    final double diff = maxStat - minStat;
                    final double ofInterest = maxStat - ofInterestCutoff * (diff);

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

        System.out.println(table);
    }

    //    @Test
    public void testPcRegression() {

        final String[] algorithms = {"PC", "CPC", "FGES", "FCI", "GFCI", "RFCI", "CFCI", "Regression"};
        final String[] statLabels = {"AP", "AR"};

        final int numMeasures = 10;
        final double edgeFactor = 2.0;

        final int numRuns = 5;
        final int maxLatents = numMeasures;
        final int jumpLatents = maxLatents / 5;
        final double alpha = 0.1;
        final double penaltyDiscount = 4.0;
        final double ofInterestCutoff = 0.01;
        final int sampleSize = 10000;

        if (maxLatents % jumpLatents != 0) throw new IllegalStateException();
        final int numLatentGroups = maxLatents / jumpLatents + 1;

        final double[][][] allAllRet = new double[numLatentGroups][][];
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

            final double[][] allRet = new double[algorithms.length][];

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

    private double[] printStatsPcRegression(final String[] algorithms, final int t, final boolean directed, final int numRuns,
                                            final double alpha, final double penaltyDiscount,
                                            final int numMeasures, final int numLatents,
                                            final double edgeFactor, final int sampleSize) {
        final NumberFormat nf = new DecimalFormat("0.00");

        double sumAdjPrecision = 0.0;
        double sumAdjRecall = 0.0;
        int count = 0;

        for (int i = 0; i < numRuns; i++) {
            final int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            final List<Node> nodes = new ArrayList<>();
            final List<String> names = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                final String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
                names.add(name);
            }

            final Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false);
            final SemPm pm = new SemPm(dag);
            final SemIm im = new SemIm(pm);
            final DataSet data = im.simulateData(sampleSize, false);

//            Graph comparison = dag;
            final Graph comparison = new DagToPag2(dag).convert();
//            Graph comparison = new Pc(new IndTestDSep(dag)).search();

            final IndTestFisherZ test = new IndTestFisherZ(data, alpha);


            final SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
            score.setPenaltyDiscount(penaltyDiscount);
            final GraphSearch search;
            Graph out;

            Node target = null;

            for (final Node node : nodes) {
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

            final long start = System.currentTimeMillis();

            final long stop = System.currentTimeMillis();

            final long elapsed = stop - start;

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            for (final Node node : dag.getNodes()) {
                if (!out.containsNode(node)) {
                    out.addNode(node);
                }
            }

            int adjTp = 0;
            int adjFp = 0;
            int adjFn = 0;

            for (final Node node : out.getAdjacentNodes(target)) {
                if (comparison.isAdjacentTo(target, node)) {
                    adjTp++;
                } else {
                    adjFp++;
                }
            }

            for (final Node node : dag.getAdjacentNodes(target)) {
                if (!out.isAdjacentTo(target, node)) {
                    adjFn++;
                }
            }

            final double adjPrecision = adjTp / (double) (adjTp + adjFp);
            final double adjRecall = adjTp / (double) (adjTp + adjFn);

            if (!Double.isNaN(adjPrecision)) {
                sumAdjPrecision += adjPrecision;
            }

            if (!Double.isNaN(adjRecall)) {
                sumAdjRecall += adjRecall;
            }

            count++;
        }

        final double avgAdjPrecision = sumAdjPrecision / (double) count;
        final double avgAdjRecall = sumAdjRecall / (double) count;

        final double[] ret = {
                avgAdjPrecision,
                avgAdjRecall,
        };

        System.out.println();

        System.out.println(algorithms[t] + " adj precision " + nf.format(avgAdjPrecision));
        System.out.println(algorithms[t] + " adj recall " + nf.format(avgAdjRecall));

        return ret;
    }

    private Graph trim(final Graph out, final Node target) {
        final Graph trimmed = new EdgeListGraph(out.getNodes());

        if (!out.containsNode(target)) throw new IllegalArgumentException();

        for (final Node node : out.getAdjacentNodes(target)) {
            trimmed.addUndirectedEdge(target, node);
        }

        return trimmed;
    }

    private Graph getRegressionGraph(final DataSet data, final Node target) {
        final List<Node> rest = new ArrayList<>(data.getVariables());
        rest.remove(target);
        final RegressionDataset regressionDataset = new RegressionDataset(data);
        regressionDataset.regress(target, rest);
        Graph graph = regressionDataset.getGraph();
        graph = GraphUtils.replaceNodes(graph, data.getVariables());
        return graph;
    }
}





