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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.TextTable;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class ExploreMixedComparison {


    private PrintStream out = System.out;
//    private OutputStream out =

    public void testMixedScore() {
        int k = 3; // Number of categories for discrete variables
        int v = 5;
        int e = 5;
        double penalty = 2.0;

        System.out.println("\n# nodes = " + v + " # edges = " + e +
                " penalty = " + penalty + " # categories = " + k);

        System.out.println();

        System.out.println("K = # categories");
        System.out.println("AP = Adjacency Precision");
        System.out.println("AR = Adjacency Recall");
        System.out.println("OP = Orientation (arrow head) Precision");
        System.out.println("OR = Orientation (arrow head) Recall");
        System.out.println("E = Elapsed time, in seconds");
        System.out.println();

        System.out.println("K\tAP\tAR\tOP\tOR\tE");

        for (int y = 0; y < 5; y++) {
            Graph dag = GraphUtils.randomGraphRandomForwardEdges(v, 0, e, 10, 10, 10, false);
//                        Graph dag = GraphUtils.scaleFreeGraph(v, 0, .45, .45, .1, .9);

            DataSet Dk = getMixedDataAjStyle(dag, k, 1000);

            long start = System.currentTimeMillis();

//            Graph pattern = searchSemFgs(Dk, penalty);
//            Graph pattern = searchBdeuFgs(Dk, k);
//            Graph pattern = searchMixedFgs1(Dk, penalty);
//            Graph pattern = searchMixedPc(Dk, 0.001);
//            Graph pattern = searchMixedPcs(Dk, 0.001);
//            Graph pattern = searchMixedCpc(Dk, 0.001);
//            Graph pattern = searchMGMFgs(Dk, penalty);
            Graph pattern = searchMGMPcs(Dk, 0.001);
//            Graph pattern = searchMGMCpc(Dk);

            long stop = System.currentTimeMillis();

            long elapsed = stop - start;
            long elapsedSeconds = elapsed / 1000;

            Graph truePattern = SearchGraphUtils.patternForDag(dag);

            System.out.println("\nall edges");

            GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(
                    pattern, truePattern, System.out);
            NumberFormat nf = new DecimalFormat("0.00");

            System.out.println(k +
                    "\t" + nf.format(comparison.getAdjPrec()) +
                    "\t" + nf.format(comparison.getAdjRec()) +
                    "\t" + nf.format(comparison.getAhdPrec()) +
                    "\t" + nf.format(comparison.getAhdRec()) +
                    "\t" + elapsedSeconds + " s");

            System.out.println("\ndiscrete discrete");

            comparison = SearchGraphUtils.getGraphComparison3(
                    getSubgraph(pattern, true, true, Dk),
                    getSubgraph(truePattern, true, true, Dk), System.out);

            System.out.println(k +
                    "\t" + nf.format(comparison.getAdjPrec()) +
                    "\t" + nf.format(comparison.getAdjRec()) +
                    "\t" + nf.format(comparison.getAhdPrec()) +
                    "\t" + nf.format(comparison.getAhdRec()) +
                    "\t" + getSubgraph(pattern, true, true, Dk).getNumEdges() + "\t= # edges");

            System.out.println("\ndiscrete continuous");

            comparison = SearchGraphUtils.getGraphComparison3(
                    getSubgraph(pattern, true, false, Dk),
                    getSubgraph(truePattern, true, false, Dk), System.out);

            System.out.println(k +
                    "\t" + nf.format(comparison.getAdjPrec()) +
                    "\t" + nf.format(comparison.getAdjRec()) +
                    "\t" + nf.format(comparison.getAhdPrec()) +
                    "\t" + nf.format(comparison.getAhdRec()) +
                    "\t" + getSubgraph(pattern, true, false, Dk).getNumEdges() + "\t= # edges");

            System.out.println("\ncontinuous continuous");

            comparison = SearchGraphUtils.getGraphComparison3(
                    getSubgraph(pattern, false, false, Dk),
                    getSubgraph(truePattern, false, false, Dk), System.out);

            System.out.println(k +
                    "\t" + nf.format(comparison.getAdjPrec()) +
                    "\t" + nf.format(comparison.getAdjRec()) +
                    "\t" + nf.format(comparison.getAhdPrec()) +
                    "\t" + nf.format(comparison.getAhdRec()) +
                    "\t" + getSubgraph(pattern, false, false, Dk).getNumEdges() + "\t= # edges");

        }
    }

    private Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataSet dataSet) {
        Graph newGraph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Node node1 = dataSet.getVariable(edge.getNode1().getName());
            Node node2 = dataSet.getVariable(edge.getNode2().getName());

            if (discrete1 && node1 instanceof DiscreteVariable) {
                if (discrete2 && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            } else if (!discrete1 && node1 instanceof ContinuousVariable) {
                if (!discrete2 && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            } else if ((discrete1 && !discrete2) || (!discrete1 && discrete2)) {
                if (node1 instanceof ContinuousVariable && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                } else if (node1 instanceof DiscreteVariable && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }
        }

        return newGraph;
    }

    public void testAjData() {
        double penalty = 4;

        try {

            for (int i = 0; i < 50; i++) {
                File dataPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/data/DAG_" + i + "_data.txt");
                DataReader reader = new DataReader();
                DataSet Dk = reader.parseTabular(dataPath);

                File graphPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/networks/DAG_" + i + "_graph.txt");

                Graph dag = GraphUtils.loadGraphTxt(graphPath);

                long start = System.currentTimeMillis();

//            Graph pattern = searchSemFgs(Dk);
//            Graph pattern = searchBdeuFgs(Dk, k);
                Graph pattern = searchMixedFgs1(Dk, penalty);

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;
                long elapsedSeconds = elapsed / 1000;

                Graph truePattern = SearchGraphUtils.patternForDag(dag);

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(pattern, truePattern, System.out);
                NumberFormat nf = new DecimalFormat("0.00");

                System.out.println(i +
                        "\t" + nf.format(comparison.getAdjPrec()) +
                        "\t" + nf.format(comparison.getAdjRec()) +
                        "\t" + nf.format(comparison.getAhdPrec()) +
                        "\t" + nf.format(comparison.getAhdRec()) +
                        "\t" + elapsedSeconds);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Graph searchSemFgs(DataSet Dk, double penalty) {
        Dk = DataUtils.convertNumericalDiscreteToContinuous(Dk);
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(Dk));
        score.setPenaltyDiscount(penalty);
        Fgs fgs = new Fgs(score);
        return fgs.search();
    }

    private Graph searchBdeuFgs(DataSet Dk, int k) {
        Discretizer discretizer = new Discretizer(Dk);
        List<Node> nodes = Dk.getVariables();

        for (Node node : nodes) {
            if (node instanceof ContinuousVariable) {
                discretizer.equalIntervals(node, k);
            }
        }

        Dk = discretizer.discretize();

        BDeuScore score = new BDeuScore(Dk);
        score.setSamplePrior(1.0);
        score.setStructurePrior(1.0);
        Fgs fgs = new Fgs(score);
        return fgs.search();
    }

    private Graph searchMixedFgs1(DataSet dk, double penalty) {
        MixedBicScore score = new MixedBicScore(dk);
        score.setPenaltyDiscount(penalty);
        Fgs fgs = new Fgs(score);
        return fgs.search();
    }

    private Graph searchMixedPc(DataSet dk, double alpha) {
        IndependenceTest test = new IndTestMixedLrt(dk, alpha);
//        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(dk, alpha, false);
        Pc pc = new Pc(test);
        return pc.search();
    }

    private Graph searchFgsMixed2(DataSet dk, double penaltyDiscount) {
        FgsMixed2 fgs = new FgsMixed2(dk);
        fgs.setPenaltyDiscount(2 * penaltyDiscount);
        return fgs.search();
    }

    private Graph searchMixedPcs(DataSet dk, double alpha) {
        IndependenceTest test = new IndTestMixedLrt(dk, alpha);
//        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(dk, alpha, false);
        PcStable pc = new PcStable(test);
        return pc.search();
    }

    private Graph searchMixedCpc(DataSet dk, double alpha) {
        IndependenceTest test = new IndTestMixedLrt(dk, alpha);
//        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(dk, alpha, false);
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph searchMGMFgs(DataSet ds, double penalty) {
        MGM m = new MGM(ds, new double[]{0.1, 0.1, 0.1});
        //m.setVerbose(this.verbose);
        Graph gm = m.search();
        DataSet dataSet = MixedUtils.makeContinuousData(ds);
        SemBicScore2 score = new SemBicScore2(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(penalty);
        Fgs fg = new Fgs(score);
        fg.setBoundGraph(gm);
        fg.setVerbose(false);
        return fg.search();
    }

    public synchronized Graph searchMGMPcs(DataSet ds, double alpha) {
        MGM m = new MGM(ds, new double[]{0.1, 0.1, 0.1});
        //m.setVerbose(this.verbose);
        Graph gm = m.search();
        //IndTestMultinomialLogisticRegression indTest = new IndTestMultinomialLogisticRegression(ds, searchParams[3]);
//        IndependenceTest indTest = new IndTestMultinomialLogisticRegressionWald(ds, 0.1, false);
        IndependenceTest indTest = new IndTestMixedLrt(ds, alpha);
        PcStable pcs = new PcStable(indTest);
        pcs.setDepth(-1);
        pcs.setInitialGraph(gm);
        pcs.setVerbose(false);
        return pcs.search();
    }

    public synchronized Graph searchMGMCpc(DataSet ds, double alpha) {
        MGM m = new MGM(ds, new double[]{0.1, 0.1, 0.1});
        //m.setVerbose(this.verbose);
        Graph gm = m.search();
        //IndTestMultinomialLogisticRegression indTest = new IndTestMultinomialLogisticRegression(ds, searchParams[3]);
        IndependenceTest indTest = new IndTestMultinomialLogisticRegressionWald(ds, alpha, false);
        Cpc pcs = new Cpc(indTest);
        pcs.setDepth(-1);
        pcs.setInitialGraph(gm);
        pcs.setVerbose(false);
        return pcs.search();
    }

    public DataSet getMixedDataAjStyle(Graph g, int k, int samps) {

        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = g.getNodes();

        Collections.shuffle(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() / 2) {
                nd.put(nodes.get(i).getName(), k);
            } else {
                nd.put(nodes.get(i).getName(), 0);
            }
        }

        g = MixedUtils.makeMixedGraph(g, nd);


        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
//        System.out.println(pm);

        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
//        System.out.println(im);

        DataSet ds = im.simulateDataAvoidInfinity(samps, false);
        return MixedUtils.makeMixedData(ds, nd);
    }

    public void testBestAlgorithms(int numNodes, int numEdges, int numRuns) {
        String[] algorithms = {"SemFGS", "BDeuFGS", "MixedFGS1", "MixedFGS2", "PC", "PCS", "CPC", "MGMFgs", "MGMPcs"};
        String[] statLabels = {"AP", "AR", "OP", "OR", "SUM", "McAdj", "McOr", "F1Adj", "F1Or", "E"};

        int maxCategories = 5;
        int sampleSize = 1000;
        double penaltyDiscount = 4.0;
        double ofInterestCutoff = 0.05;
        double alpha = 0.001;

        double[][][][] allAllRet = new double[maxCategories][][][];
        int latentIndex = -1;

        for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {
            latentIndex++;

            System.out.println();

            System.out.println("num categories = " + numCategories);
            System.out.println("num nodes = " + numNodes);
            System.out.println("num edges = " + numEdges);
            System.out.println("sample size = " + sampleSize);
            System.out.println("penaltyDiscount = " + penaltyDiscount);
            System.out.println("num runs = " + numRuns);

            double[][][] allRet = new double[algorithms.length][][];

            for (int t = 0; t < algorithms.length; t++) {
                allRet[t] = printStats(algorithms, t, numRuns, sampleSize, numNodes,
                        numCategories, numEdges, alpha, penaltyDiscount);
            }

            allAllRet[latentIndex] = allRet;
        }

        System.out.println();
        System.out.println("=======");
        System.out.println();
        System.out.println("Algorithms with max - " + ofInterestCutoff + " <= stat <= max.");
        System.out.println();
        System.out.println("AP = Average Adj Precision; AR = Average Adj Recall");
        System.out.println("OP = Average orientation (arrow) Precision; OR = Average orientation (arrow) recall");
        System.out.println("McAdj = Mathew's correlation for adjacencies; McOr = Mathew's correlatin for orientatons");
        System.out.println("F1Adj = F1 score for adjacencies; F1Or = F1 score for orientations");
        System.out.println("E = Averaged Elapsed Time (ms), AP/P");
        System.out.println();
        System.out.println("num categories = 2 to " + maxCategories);
        System.out.println("sample size = " + sampleSize);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("alpha = " + alpha);
        System.out.println("num runs = " + numRuns);
        System.out.println();
        System.out.println("num measures = " + numNodes);
        System.out.println("num edges = " + numEdges);

        printBestStats(allAllRet, algorithms, statLabels, maxCategories, ofInterestCutoff);
    }

    private double[][] printStats(String[] algorithms, int t, int numRuns,
                                  int sampleSize, int numMeasures, int numCategories,
                                  int numEdges, double alpha, double penalty) {
        NumberFormat nf = new DecimalFormat("0.00");

        double[] sumAdjPrecision = new double[4];
        double[] sumAdjRecall = new double[4];
        double[] sumArrowPrecision = new double[4];
        double[] sumArrowRecall = new double[4];
        double[] sumSum = new double[4];
        double[] sumMcAdj = new double[4];
        double[] sumMcOr = new double[4];
        double[] sumF1Adj = new double[4];
        double[] sumF1Or = new double[4];
        double totalElapsed = 0.0;

        int[] countAP = new int[4];
        int[] countAR = new int[4];
        int[] countOP = new int[4];
        int[] countOR = new int[4];
        int[] countSum = new int[4];
        int[] countMcAdj = new int[4];
        int[] countMcOr = new int[4];
        int[] countF1Adj = new int[4];
        int[] countF1Or = new int[4];

        for (int i = 0; i < numRuns; i++) {
            List<Node> nodes = new ArrayList<>();

            for (int r = 0; r < numMeasures; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
            }

            Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numEdges,
                    10, 10, 10, false);
            DataSet data = getMixedDataAjStyle(dag, numCategories, sampleSize);

            Graph out;

            long start = System.currentTimeMillis();

            switch (t) {
                case 0:
                    out = searchSemFgs(data, penalty);
                    break;
                case 1:
                    out = searchBdeuFgs(data, numCategories);
                    break;
                case 2:
                    out = searchMixedFgs1(data, penalty);
                    break;
                case 3:
                    out = searchFgsMixed2(data, penalty);
                    break;
                case 4:
                    out = searchMixedPc(data, alpha);
                    break;
                case 5:
                    out = searchMixedPcs(data, alpha);
                    break;
                case 6:
                    out = searchMixedCpc(data, alpha);
                    break;
                case 7:
                    out = searchMGMFgs(data, penalty);
                    break;
                case 8:
                    out = searchMGMPcs(data, alpha);
                    break;
                default:
                    throw new IllegalStateException();
            }

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            Graph[] est = new Graph[4];

            est[0] = out;
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            Graph[] truth = new Graph[4];

            truth[0] = dag;
            truth[1] = getSubgraph(dag, true, true, data);
            truth[2] = getSubgraph(dag, true, false, data);
            truth[3] = getSubgraph(dag, false, false, data);

            long stop = System.currentTimeMillis();

            long elapsed = stop - start;
            totalElapsed += elapsed / 1000.0;

            for (int u = 0; u < 4; u++) {
                int adjTp = 0;
                int adjFp = 0;
                int adjTn;
                int adjFn = 0;
                int arrowsTp = 0;
                int arrowsFp = 0;
                int arrowsTn = 0;
                int arrowsFn = 0;

                for (Edge edge : est[u].getEdges()) {
                    if (truth[u].isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                        adjTp++;
                    } else {
                        adjFp++;
                    }

                    if (edge.isDirected()) {
                        Edge _edge = truth[u].getEdge(edge.getNode1(), edge.getNode2());

                        if (edge != null && edge.equals(_edge)) {
                            arrowsTp++;
                        } else {
                            arrowsFp++;
                        }
                    }
                }

                List<Node> nodes1 = truth[u].getNodes();

                for (int w = 0; w < nodes1.size(); w++) {
                    for (int s = w + 1; w < nodes1.size(); w++) {
                        Node W = nodes1.get(w);
                        Node S = nodes1.get(s);

                        if (truth[u].isAdjacentTo(W, S)) {
                            if (!est[u].isAdjacentTo(W, S)) {
                                adjFn++;
                            }

                            Edge e1 = truth[u].getEdge(W, S);
                            Edge e2 = est[u].getEdge(W, S);

                            if (!(e2 != null && e2.equals(e1))) {
                                arrowsFn++;
                            }
                        }

                        Edge e1 = truth[u].getEdge(W, S);
                        Edge e2 = est[u].getEdge(W, S);

                        if (!(e1 != null && e2 == null) || (e1 != null && e2 != null && !e1.equals(e2))) {
                            arrowsFn++;
                        }
                    }
                }

                int allEdges = truth[u].getNumNodes() * (truth[u].getNumNodes() - 1);

                adjTn = allEdges / 2 - (adjFn + adjFp + adjTp);
                arrowsTn = allEdges - (arrowsFn + arrowsFp + arrowsTp);

                double adjPrecision = adjTp / (double) (adjTp + adjFp);
                double adjRecall = adjTp / (double) (adjTp + adjFn);

                double arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
                double arrowRecall = arrowsTp / (double) (arrowsTp + arrowsFn);

                if (!Double.isNaN(adjPrecision)) {
                    sumAdjPrecision[u] += adjPrecision;
                    countAP[u]++;
                }

                if (!Double.isNaN(adjRecall)) {
                    sumAdjRecall[u] += adjRecall;
                    countAR[u]++;
                }

                if (!Double.isNaN(arrowPrecision)) {
                    sumArrowPrecision[u] += arrowPrecision;
                    countOP[u]++;
                }

                if (!Double.isNaN(arrowRecall)) {
                    sumArrowRecall[u] += arrowRecall;
                    countOR[u]++;
                }

                double sum = adjPrecision + adjRecall + arrowPrecision + arrowRecall;
                double mcAdj = (adjTp  * adjTn - adjFp * adjFn) /
                        Math.sqrt((adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn));
                double mcOr = (arrowsTp  * arrowsTn - arrowsFp * arrowsFn) /
                        Math.sqrt((arrowsTp + arrowsFp) * (arrowsTp + arrowsFn) *
                                (arrowsTn + arrowsFp) * (arrowsTn + arrowsFn));
                double f1Adj = 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
                double f1Arrows = 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);

                if (f1Arrows < 0) {
                    System.out.println();
                }

                if (!Double.isNaN(sum)) {
                    sumSum[u] += sum;
                    countSum[u]++;
                }

                if (!Double.isNaN(mcAdj)) {
                    sumMcAdj[u] += mcAdj;
                    countMcAdj[u]++;
                }

                if (!Double.isNaN(mcOr)) {
                    sumMcOr[u] += mcOr;
                    countMcOr[u]++;
                }

                if (!Double.isNaN(f1Adj)) {
                    sumF1Adj[u] += f1Adj;
                    countF1Adj[u]++;
                }

                if (!Double.isNaN(f1Arrows)) {
                    sumF1Or[u] += f1Arrows;
                    countF1Or[u]++;
                }
            }
        }

        double[] avgAdjPrecision = new double[4];
        double[] avgAdjRecall = new double[4];
        double[] avgArrowPrecision = new double[4];
        double[] avgArrowRecall = new double[4];
        double[] avgSum = new double[4];
        double[] avgMcAdj = new double[4];
        double[] avgMcOr = new double[4];
        double[] avgF1Adj = new double[4];
        double[] avgF1Or = new double[4];
        double[] avgElapsed = new double[4];

        for (int u = 0; u < 4; u++) {
            avgAdjPrecision[u] = sumAdjPrecision[u] / (double) countAP[u];
            avgAdjRecall[u] = sumAdjRecall[u] / (double) countAR[u];
            avgArrowPrecision[u] = sumArrowPrecision[u] / (double) countOP[u];
            avgArrowRecall[u] = sumArrowRecall[u] / (double) countOR[u];
            avgSum[u] = sumSum[u] / (double) countSum[u];
            avgMcAdj[u] = sumMcAdj[u] / (double) countMcAdj[u];
            avgMcOr[u] = sumMcOr[u] / (double) countMcOr[u];
            avgF1Adj[u] = sumF1Adj[u] / (double) countF1Adj[u];
            avgF1Or[u] = sumF1Or[u] / (double) countF1Or[u];
            avgElapsed[u] = -totalElapsed / (double) numRuns;
        }

        double[][] ret = new double[][]{
                avgAdjPrecision,
                avgAdjRecall,
                avgArrowPrecision,
                avgArrowRecall,
                avgSum,
                avgMcAdj,
                avgMcOr,
                avgF1Adj,
                avgF1Or,
                avgElapsed
        };

        System.out.println();

        for (int u = 0; u < 4; u++) {
            String header = getHeader(u);

            System.out.println("\n" + header + "\n");

            System.out.println(algorithms[t] + " adj precision " + nf.format(avgAdjPrecision[u]));
            System.out.println(algorithms[t] + " adj recall " + nf.format(avgAdjRecall[u]));
            System.out.println(algorithms[t] + " arrow precision " + nf.format(avgArrowPrecision[u]));
            System.out.println(algorithms[t] + " arrow recall " + nf.format(avgArrowRecall[u]));
            System.out.println(algorithms[t] + " sum " + nf.format(avgSum[u]));
            System.out.println(algorithms[t] + " McAdj " + nf.format(avgMcAdj[u]));
            System.out.println(algorithms[t] + " McOr " + nf.format(avgMcOr[u]));
            System.out.println(algorithms[t] + " F1adj " + nf.format(avgF1Adj[u]));
            System.out.println(algorithms[t] + " F1Or " + nf.format(avgF1Or[u]));
            System.out.println(algorithms[t] + " avg elapsed " + nf.format(avgElapsed[u]));
        }


        return ret;
    }

    private String getHeader(int u) {
        String header;

        switch (u) {
            case 0:
                header = "All edges";
                break;
            case 1:
                header = "Discrete-discrete";
                break;
            case 2:
                header = "Discrete-continuous";
                break;
            case 3:
                header = "Continuous-continuous";
                break;
            default:
                throw new IllegalStateException();
        }
        return header;
    }

    private void printBestStats(double[][][][] allAllRet, String[] algorithms, String[] statLabels,
                                int maxCategories, double ofInterestCutoff) {
//        TextTable table = new TextTable(allAllRet.length + 1, allAllRet[0][0].length + 1);


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


        System.out.println();
        System.out.println("And the winners are... !");

        for (int u = 0; u < 4; u++) {
//            System.out.println("\n%%%%%%%%%%" + getHeader(u) + "%%%%%%%%%%%");

            for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {

                System.out.println();
                System.out.println("====== " + getHeader(u) + " NUM CATEGORIES = " + numCategories + " (listing high to low, top to top - 0.05)");
                System.out.println();

//                table.setToken(numCategories - 1, 0, numCategories + "");

                for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {


//                double maxStat = Double.NaN;
                    String maxAlg = "-";

                    List<Pair> algStats = new ArrayList<>();

                    for (int t = 0; t < algorithms.length; t++) {
                        double stat = allAllRet[numCategories - 2][t][statIndex][u];
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
                        double ofInterest = maxStat - ofInterestCutoff;

                        for (int i = 1; i < algStats.size(); i++) {
                            if (algStats.get(i).getStat() >= ofInterest) {
                                maxAlg += "," + algStats.get(i).getAlgorithm();
                            }
                        }
                    }

                    System.out.println(statLabels[statIndex] + ": " + maxAlg);

//                    table.setToken(numCategories - 1, statIndex + 1, maxAlg);
                }
            }

//            for (int j = 0; j < statLabels.length; j++) {
//                table.setToken(0, j + 1, statLabels[j]);
//            }

//            System.out.println();
//            System.out.println(getHeader(u));
//            System.out.println();
//
//            System.out.println(table.toString());
        }


        NumberFormat nf = new DecimalFormat("0.00");

        System.out.println();
        System.out.println("DETAILS:");
        System.out.println();
        System.out.println("AVERAGE STATISTICS");

        for (int u = 0; u < 4; u++) {
//            System.out.println();
//            System.out.println("&&&&& " + getHeader(u));
//            System.out.println();

            for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {
;

                for (int t = 0; t < algorithms.length; t++) {
                    String algorithm = algorithms[t];

                    System.out.println();
                    System.out.println(getHeader(u) + " # categories = " + numCategories + " Algorithm = " + algorithm);
                    System.out.println();
//                    System.out.println("\nAlgorithm = " + algorithm);
//                    System.out.println();

                    for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
                        String statLabel = statLabels[statIndex];
                        double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        System.out.println("\tAverage" + statLabel + " = " + nf.format(stat));
                    }
                }
            }
        }

    }

    public static void main(String...args) {
        int numNodes = Integer.parseInt(args[0]);
        int numEdges = Integer.parseInt(args[1]);
        int numRuns = Integer.parseInt(args[2]);

        new ExploreMixedComparison().testBestAlgorithms(numNodes, numEdges, numRuns);
    }
}




