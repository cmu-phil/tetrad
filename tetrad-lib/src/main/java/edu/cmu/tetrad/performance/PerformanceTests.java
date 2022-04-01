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

package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Runs some basic performance tests of various algorithm.
 *
 * @author Joseph Ramsey
 */
public class PerformanceTests {
    private PrintStream out = System.out;

    public void testPc(int numVars, double edgeFactor, int numCases, double alpha) {
        final int depth = -1;

        init(new File("long.pc." + numVars + "." + edgeFactor + "." + alpha + ".txt"), "Tests performance of the PC algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.gc();

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        Pc pc = new Pc(test);
        pc.setVerbose(false);
        pc.setDepth(depth);
//        pcStable.setOut(out);

        Graph outGraph = pc.search();

        this.out.println(outGraph);

        long time4 = System.currentTimeMillis();

        this.out.println("# Vars = " + numVars);
        this.out.println("# Edges = " + (int) (numVars * edgeFactor));
        this.out.println("# Cases = " + numCases);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        SearchGraphUtils.graphComparison(outGraph, SearchGraphUtils.cpdagForDag(graph), this.out);

        this.out.close();
    }

    public void printStuffForKlea() {

        try {
            File _data = new File("data.txt");
            File _graph = new File("graph.txt");

            PrintStream out1 = new PrintStream(new FileOutputStream(_data));
            PrintStream out2 = new PrintStream(new FileOutputStream(_graph));


            final int numVars = 50000;

            List<Node> vars = new ArrayList<>();

            for (int i = 0; i < numVars; i++) {
                vars.add(new ContinuousVariable("X" + (i + 1)));
            }

            final double edgeFactor = 1.0;
            final int numCases = 1000;

            Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                    30, 15, 15, false, true);

            out2.println(graph);

            System.out.println("Graph done");

            this.out.println("Graph done");

            System.out.println("Starting simulation");
            LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
            simulator.setOut(this.out);

            DataSet data = simulator.simulateDataFisher(numCases);

            out1.println(data);

            out1.close();
            out2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void testPcStable(int numVars, double edgeFactor, int numCases, double alpha) {
        final int depth = -1;

        init(new File("long.pcstable." + numVars + "." + edgeFactor + "." + alpha + ".txt"), "Tests performance of the PC Stable algorithm");

        long time1 = System.currentTimeMillis();

        Graph dag = makeDag(numVars, edgeFactor);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        PcStable pcStable = new PcStable(test);

        Graph estCPDAG = pcStable.search();

//        out.println(estCPDAG);

        long time4 = System.currentTimeMillis();

        this.out.println("# Cases = " + numCases);
        this.out.println("alpha = " + alpha);
        this.out.println("depth = " + depth);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        System.out.println("# edges in true CPDAG = " + trueCPDAG.getNumEdges());
        System.out.println("# edges in est CPDAG = " + estCPDAG.getNumEdges());

        SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, this.out);

        this.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");

        this.out.close();
    }

    public void testPcStableMax(int numVars, double edgeFactor, int numCases, double alpha) {
        final int depth = -1;

        init(new File("long.pcstablemax." + numVars + "." + edgeFactor + "." + alpha + ".txt"), "Tests performance of the PC Max algorithm");

        long time1 = System.currentTimeMillis();

        Graph dag = makeDag(numVars, edgeFactor);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        PcStableMax pcStable = new PcStableMax(test);

        Graph estCPDAG = pcStable.search();

//        out.println(estCPDAG);

        long time4 = System.currentTimeMillis();

        this.out.println("# Cases = " + numCases);
        this.out.println("alpha = " + alpha);
        this.out.println("depth = " + depth);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running PC-Max) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + PC-Max) " + (time4 - time2) + " ms");

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        System.out.println("# edges in true CPDAG = " + trueCPDAG.getNumEdges());
        System.out.println("# edges in est CPDAG = " + estCPDAG.getNumEdges());

        SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, this.out);

        this.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");

        this.out.close();
    }

    public void testFges(int numVars, double edgeFactor, int numCases, double penaltyDiscount) {
        init(new File("long.fges." + numVars + "." + edgeFactor + "." + penaltyDiscount + ".txt"), "Tests performance of the FGES algorithm");

        long time1 = System.currentTimeMillis();

        Graph dag = makeDag(numVars, edgeFactor);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

        SemBicScore semBicScore = new SemBicScore(cov);
        semBicScore.setPenaltyDiscount(penaltyDiscount);

        Fges pcStable = new Fges(semBicScore);

        Graph estCPDAG = pcStable.search();

//        out.println(estCPDAG);

        long time4 = System.currentTimeMillis();

        this.out.println("# Cases = " + numCases);
        this.out.println("penalty discount = " + penaltyDiscount);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running FGES) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + FGES) " + (time4 - time2) + " ms");

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        System.out.println("# edges in true CPDAG = " + trueCPDAG.getNumEdges());
        System.out.println("# edges in est CPDAG = " + estCPDAG.getNumEdges());

        SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, this.out);

        this.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");

        this.out.close();
    }


    public void testCpc(int numVars, double edgeFactor, int numCases) {
        final double alpha = 0.0001;
        final int depth = -1;

        init(new File("long.cpc." + numVars + ".txt"), "Tests performance of the CPC algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.gc();

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        Cpc cpc = new Cpc(test);
        cpc.setVerbose(false);
        cpc.setDepth(depth);
//        pcStable.setOut(out);

        Graph outGraph = cpc.search();

        this.out.println(outGraph);

        long time4 = System.currentTimeMillis();

        this.out.println("# Vars = " + numVars);
        this.out.println("# Edges = " + (int) (numVars * edgeFactor));
        this.out.println("# Cases = " + numCases);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        SearchGraphUtils.graphComparison(outGraph, SearchGraphUtils.cpdagForDag(graph), this.out);

        this.out.close();
    }

    public void testCpcStable(int numVars, double edgeFactor, int numCases, double alpha) {
        final int depth = 3;

        init(new File("long.cpcstable." + numVars + ".txt"), "Tests performance of the CPC algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = makeDag(numVars, edgeFactor);


        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        CpcStable cpcStable = new CpcStable(test);
        cpcStable.setVerbose(false);
        cpcStable.setDepth(depth);
        cpcStable.setOut(this.out);

        Graph outGraph = cpcStable.search();

//        out.println(outGraph);

        long time4 = System.currentTimeMillis();

        this.out.println("# Cases = " + numCases);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running CPC-Stable) " + (time4 - time3) + " ms");

        this.out.println("Total elapsed (cov + CPC-Stable) " + (time4 - time2) + " ms");

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(graph);

        SearchGraphUtils.graphComparison(outGraph, trueCPDAG, this.out);

        this.out.println("# ambiguous triples = " + outGraph.getAmbiguousTriples().size());

        this.out.close();
    }

    public void testFci(int numVars, double edgeFactor, int numCases) {
        final double alpha = 0.001;
        final int depth = 3;

        init(new File("long.fci." + numVars + ".txt"), "Tests performance of the FCI algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
        simulator.setOut(this.out);

        DataSet data = simulator.simulateDataFisher(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);

        Fci fci = new Fci(independenceTest);
        fci.setVerbose(false);
        fci.setDepth(depth);
        fci.setMaxPathLength(2);
//        fci.setTrueDag(truePag);
        Graph outGraph = fci.search();

        this.out.println(outGraph);

        long time4 = System.currentTimeMillis();

        this.out.println("# Vars = " + numVars);
        this.out.println("# Edges = " + (int) (numVars * edgeFactor));
        this.out.println("# Cases = " + numCases);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running FCI) " + (time4 - time3) + " ms");

        this.out.close();
    }

    public void testGfci(int numVars, double edgeFactor) {
        System.out.println("Seed = " + RandomUtil.getInstance().getSeed());

//        RandomUtil.getInstance().setSeed(1460491316813L);

        final double alpha = .1;
        final int depth = -1;
        final double penaltyDiscount = 4.0;
        final int maxPathLength = -1;
        final double coefLow = .3;
        final double coefHigh = 1.5;
        final int numLatentConfounders = 50;
        final int numCases = 1000;

        init(new File("long.gfci." + numVars + ".txt"), "Tests performance of the FCI-GES algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, numLatentConfounders, (int) (numVars * edgeFactor),
                10, 10, 10, false, false);

        System.out.println("Graph done");

        this.out.println("Graph done");

        System.out.println("Starting simulation");

        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        simulator.setCoefRange(coefLow, coefHigh);

        DataSet data = simulator.simulateDataFisher(numCases);

        data = DataUtils.restrictToMeasured(data);

        System.out.println("Finishing simulation");

        System.out.println("Num measured vars = " + data.getNumColumns());

        long time2 = System.currentTimeMillis();

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

        ICovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

        IndependenceTest independenceTest = new IndTestFisherZ(cov, alpha);
//        GFci fci = new GFci(independenceTest);

        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(penaltyDiscount);
        GFci fci = new GFci(independenceTest, score);

        fci.setVerbose(false);
        fci.setMaxPathLength(maxPathLength);
        fci.setMaxDegree(depth);
        fci.setFaithfulnessAssumed(false);
        fci.setCompleteRuleSetUsed(true);
        Graph outGraph = fci.search();

        this.out.println(outGraph);

        System.out.println(MisclassificationUtils.edgeMisclassifications(outGraph, new DagToPag(dag).convert()));

        long time4 = System.currentTimeMillis();

        this.out.println("# Vars = " + numVars);
        this.out.println("# Edges = " + (int) (numVars * edgeFactor));
        this.out.println("# Cases = " + numCases);

        this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        this.out.println("Elapsed (running FCI) " + (time4 - time3) + " ms");

        this.out.close();
    }

    public void testFgesComparisonContinuous(int numVars, double edgeFactor, int numCases, int numRuns) {
        testFges(numVars, edgeFactor, numCases, numRuns, true);
    }

    public void testFgesComparisonDiscrete(int numVars, double edgeFactor, int numCases, int numRuns) {
        testFges(numVars, edgeFactor, numCases, numRuns, false);
    }

    private void testFges(int numVars, double edgeFactor, int numCases, int numRuns, boolean continuous) {
        this.out.println(new Date());

//        RandomUtil.getInstance().setSeed(4828384343999L);
        final double penaltyDiscount = 4.0;
        final int maxIndegree = 5;
        final boolean faithfulness = true;

//        RandomUtil.getInstance().setSeed(50304050454L);


        List<int[][]> allCounts = new ArrayList<>();
        List<double[]> comparisons = new ArrayList<>();
        List<Double> degrees = new ArrayList<>();
        List<Long> elapsedTimes = new ArrayList<>();

        //noinspection IfStatementWithIdenticalBranches
        if (continuous) {
            init(new File("fges.comparison.continuous" + numVars + "." + (int) (edgeFactor * numVars) +
                    "." + numCases + "." + numRuns + ".txt"), "Num runs = " + numRuns);
            this.out.println("Num vars = " + numVars);
            this.out.println("Num edges = " + (int) (numVars * edgeFactor));
            this.out.println("Num cases = " + numCases);
            this.out.println("Penalty discount = " + penaltyDiscount);
            this.out.println("Depth = " + maxIndegree);
            this.out.println();

        } else {
            init(new File("fges.comparison.discrete" + numVars + "." + (int) (edgeFactor * numVars) +
                    "." + numCases + "." + numRuns + ".txt"), "Num runs = " + numRuns);
            this.out.println("Num vars = " + numVars);
            this.out.println("Num edges = " + (int) (numVars * edgeFactor));
            this.out.println("Num cases = " + numCases);
            this.out.println("Sample prior = " + 1);
            this.out.println("Structure prior = " + 1);
            this.out.println("Depth = " + 1);
            this.out.println();
        }

        for (int run = 0; run < numRuns; run++) {
            this.out.println("\n\n\n******************************** RUN " + (run + 1) + " ********************************\n\n");

            System.out.println("Making dag");

            this.out.println(new Date());

            Graph dag = makeDag(numVars, edgeFactor);

            System.out.println(new Date());

            System.out.println("Calculating CPDAG for DAG");

            Graph CPDAG = SearchGraphUtils.cpdagForDag(dag);

            List<Node> vars = dag.getNodes();

            int[] tiers = new int[vars.size()];

            for (int i = 0; i < vars.size(); i++) {
                tiers[i] = i;
            }

            System.out.println("Graph done");

            long time1 = System.currentTimeMillis();

            this.out.println("Graph done");

            System.out.println(new Date());

            System.out.println("Starting simulation");
            Graph estCPDAG;
            long elapsed;

            if (continuous) {
                LargeScaleSimulation simulator = new LargeScaleSimulation(dag, vars, tiers);
                simulator.setVerbose(false);
                simulator.setOut(this.out);

                DataSet data = simulator.simulateDataFisher(numCases);

                System.out.println("Finishing simulation");

                System.out.println(new Date());

                long time2 = System.currentTimeMillis();

                this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
                System.out.println(new Date());

                System.out.println("Making covariance matrix");

                long time3 = System.currentTimeMillis();

                ICovarianceMatrix cov = new CovarianceMatrix(data);

                System.out.println("Covariance matrix done");

                this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

                SemBicScore score = new SemBicScore(cov);
                score.setPenaltyDiscount(penaltyDiscount);

                System.out.println(new Date());
                System.out.println("\nStarting FGES");

                long timea = System.currentTimeMillis();

                Fges fges = new Fges(score);
//                fges.setVerbose(false);
                fges.setOut(System.out);
                fges.setFaithfulnessAssumed(faithfulness);

                long timeb = System.currentTimeMillis();

                estCPDAG = fges.search();

                long timec = System.currentTimeMillis();

                this.out.println("Time for FGES constructor " + (timeb - timea) + " ms");
                this.out.println("Time for FGES search " + (timec - timea) + " ms");
                this.out.println();
                this.out.flush();

                elapsed = timec - timea;
            } else {

                BayesPm pm = new BayesPm(dag, 3, 3);
                MlBayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

                DataSet data = im.simulateData(numCases, false, tiers);

                System.out.println("Finishing simulation");

                long time2 = System.currentTimeMillis();

                this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

                long time3 = System.currentTimeMillis();

                BDeuScore score = new BDeuScore(data);
                score.setStructurePrior(1);
                score.setSamplePrior(1);

                System.out.println(new Date());
                System.out.println("\nStarting FGES");

                long timea = System.currentTimeMillis();

                Fges fges = new Fges(score);
//                fges.setVerbose(false);
                fges.setOut(System.out);
                fges.setFaithfulnessAssumed(faithfulness);

                long timeb = System.currentTimeMillis();

                estCPDAG = fges.search();

                long timec = System.currentTimeMillis();

                this.out.println("Time consructing BDeu score " + (timea - time3) + " ms");
                this.out.println("Time for FGES constructor " + (timeb - timea) + " ms");
                this.out.println("Time for FGES search " + (timec - timea) + " ms");
                this.out.println();

                elapsed = timec - timea;
            }

            System.out.println("Done with FGES");

            System.out.println(new Date());

            double[] comparison = new double[4];

            System.out.println("Counting misclassifications.");

            estCPDAG = GraphUtils.replaceNodes(estCPDAG, CPDAG.getNodes());

            int[][] counts = GraphUtils.edgeMisclassificationCounts(CPDAG, estCPDAG, false);
            allCounts.add(counts);

            System.out.println(new Date());

            int sumRow = counts[4][0] + counts[4][3] + counts[4][5];
            int sumCol = counts[0][3] + counts[4][3] + counts[5][3] + counts[7][3];
            int trueArrow = counts[4][3];

            int sumTrueAdjacencies = 0;

            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 5; j++) {
                    sumTrueAdjacencies += counts[i][j];
                }
            }

            int falsePositiveAdjacencies = 0;

            for (int j = 0; j < 5; j++) {
                falsePositiveAdjacencies += counts[7][j];
            }

            int falseNegativeAdjacencies = 0;

            for (int i = 0; i < 5; i++) {
                falseNegativeAdjacencies += counts[i][5];
            }

            comparison[0] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falsePositiveAdjacencies);
            comparison[1] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falseNegativeAdjacencies);

            comparison[2] = trueArrow / (double) sumCol;
            comparison[3] = trueArrow / (double) sumRow;

            comparisons.add(comparison);

            this.out.println(GraphUtils.edgeMisclassifications(counts));
            this.out.println(precisionRecall(comparison));

            elapsedTimes.add(elapsed);
            this.out.println("\nElapsed: " + elapsed + " ms");
        }


        printAverageConfusion("Average", allCounts);
        printAveragePrecisionRecall(comparisons);
        printAverageStatistics(elapsedTimes, degrees);

        this.out.close();
    }

    public void testFgesMbComparisonContinuous(int numVars, double edgeFactor, int numCases, int numRuns) {
        testFgesMb(numVars, edgeFactor, numCases, numRuns, true);
    }

    public void testFgesMbComparisonDiscrete(int numVars, double edgeFactor, int numCases, int numRuns) {
        testFgesMb(numVars, edgeFactor, numCases, numRuns, false);
    }

    private void testFgesMb(int numVars, double edgeFactor, int numCases, int numRuns, boolean continuous) {

        final double penaltyDiscount = 4.0;
        final int structurePrior = 10;
        final int samplePrior = 10;
        final int maxIndegree = -1;
//        boolean faithfulness = false;

        List<int[][]> allCounts = new ArrayList<>();
        List<double[]> comparisons = new ArrayList<>();
        List<Double> degrees = new ArrayList<>();
        List<Long> elapsedTimes = new ArrayList<>();

        System.out.println("Making dag");

        Graph dag = makeDag(numVars, edgeFactor);

        System.out.println(new Date());

        System.out.println("Calculating CPDAG for DAG");

        Graph CPDAG = SearchGraphUtils.cpdagForDag(dag);

        int[] tiers = new int[dag.getNumNodes()];

        for (int i = 0; i < dag.getNumNodes(); i++) {
            tiers[i] = i;
        }

        System.out.println("Graph done");

        long time1 = System.currentTimeMillis();

        this.out.println("Graph done");

        System.out.println(new Date());

        System.out.println("Starting simulation");
        Graph estCPDAG;
        long elapsed;

        FgesMb fges;
        List<Node> vars;

        if (continuous) {
            init(new File("FgesMb.comparison.continuous" + numVars + "." + (int) (edgeFactor * numVars) +
                    "." + numCases + "." + numRuns + ".txt"), "Num runs = " + numRuns);
            this.out.println("Num vars = " + numVars);
            this.out.println("Num edges = " + (int) (numVars * edgeFactor));
            this.out.println("Num cases = " + numCases);
            this.out.println("Penalty discount = " + penaltyDiscount);
            this.out.println("Depth = " + maxIndegree);
            this.out.println();

            this.out.println(new Date());

            vars = dag.getNodes();

            LargeScaleSimulation simulator = new LargeScaleSimulation(dag, vars, tiers);
            simulator.setVerbose(false);
            simulator.setOut(this.out);

            DataSet data = simulator.simulateDataFisher(numCases);

            System.out.println("Finishing simulation");

            System.out.println(new Date());

            long time2 = System.currentTimeMillis();

            this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
            System.out.println(new Date());

            System.out.println("Making covariance matrix");

            long time3 = System.currentTimeMillis();

            ICovarianceMatrix cov = new CovarianceMatrix(data);

            System.out.println("Covariance matrix done");

            this.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

            SemBicScore score = new SemBicScore(cov);
            score.setPenaltyDiscount(penaltyDiscount);

            System.out.println(new Date());
            System.out.println("\nStarting FGES-MB");

            fges = new FgesMb(score);
            fges.setVerbose(false);
            fges.setNumCPDAGsToStore(0);
            fges.setOut(System.out);
//            fges.setHeuristicSpeedup(faithfulness);
            fges.setMaxDegree(maxIndegree);
            fges.setCycleBound(-1);
        } else {
            init(new File("FgesMb.comparison.discrete" + numVars + "." + (int) (edgeFactor * numVars) +
                    "." + numCases + "." + numRuns + ".txt"), "Num runs = " + numRuns);
            this.out.println("Num vars = " + numVars);
            this.out.println("Num edges = " + (int) (numVars * edgeFactor));
            this.out.println("Num cases = " + numCases);
            this.out.println("Sample prior = " + samplePrior);
            this.out.println("Structure prior = " + structurePrior);
            this.out.println("Depth = " + maxIndegree);
            this.out.println();

            this.out.println(new Date());

            BayesPm pm = new BayesPm(dag, 3, 3);
            MlBayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

            DataSet data = im.simulateData(numCases, false, tiers);

            vars = data.getVariables();
            CPDAG = GraphUtils.replaceNodes(CPDAG, vars);

            System.out.println("Finishing simulation");

            long time2 = System.currentTimeMillis();

            this.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

            long time3 = System.currentTimeMillis();

            BDeuScore score = new BDeuScore(data);
            score.setStructurePrior(structurePrior);
            score.setSamplePrior(samplePrior);

            System.out.println(new Date());
            System.out.println("\nStarting FGES");

            long time4 = System.currentTimeMillis();

            fges = new FgesMb(score);
            fges.setVerbose(false);
            fges.setNumCPDAGsToStore(0);
            fges.setOut(System.out);
//            fges.setHeuristicSpeedup(faithfulness);
            fges.setMaxDegree(maxIndegree);
            fges.setCycleBound(-1);

            long timeb = System.currentTimeMillis();

            this.out.println("Time consructing BDeu score " + (time4 - time3) + " ms");
            this.out.println("Time for FGES-MB constructor " + (timeb - time4) + " ms");
            this.out.println();
        }

        final int numSkipped = 0;

        for (int run = 0; run < numRuns; run++) {
            this.out.println("\n\n\n******************************** RUN " + (run + 1) + " ********************************\n\n");

            Node target = vars.get(RandomUtil.getInstance().nextInt(vars.size()));
            System.out.println("Target = " + target);
            long timea = System.currentTimeMillis();

            estCPDAG = fges.search(target);

            long timed = System.currentTimeMillis();

            elapsed = timed - timea;

            Set<Node> mb = new HashSet<>();
            mb.add(target);

            mb.addAll(CPDAG.getAdjacentNodes(target));

            for (Node child : CPDAG.getChildren(target)) {
                mb.addAll(CPDAG.getParents(child));
            }

            Graph trueMbGraph = CPDAG.subgraph(new ArrayList<>(mb));

            long timec = System.currentTimeMillis();

            this.out.println("Time for FGES-MB search " + (timec - timea) + " ms");
            this.out.println();

            System.out.println("Done with FGES");

            System.out.println(new Date());

            double[] comparison = new double[4];

            System.out.println("Counting misclassifications.");

            int[][] counts = GraphUtils.edgeMisclassificationCounts(trueMbGraph, estCPDAG, false);
            allCounts.add(counts);

            System.out.println(new Date());

            int sumRow = counts[4][0] + counts[4][3] + counts[4][5];
            int sumCol = counts[0][3] + counts[4][3] + counts[5][3] + counts[7][3];
            int trueArrow = counts[4][3];

            int sumTrueAdjacencies = 0;

            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 5; j++) {
                    sumTrueAdjacencies += counts[i][j];
                }
            }

            int falsePositiveAdjacencies = 0;

            for (int j = 0; j < 5; j++) {
                falsePositiveAdjacencies += counts[7][j];
            }

            int falseNegativeAdjacencies = 0;

            for (int i = 0; i < 5; i++) {
                falseNegativeAdjacencies += counts[i][5];
            }

            comparison[0] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falsePositiveAdjacencies);
            comparison[1] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falseNegativeAdjacencies);

            comparison[2] = trueArrow / (double) sumCol;
            comparison[3] = trueArrow / (double) sumRow;

            comparisons.add(comparison);

            this.out.println(GraphUtils.edgeMisclassifications(counts));
            this.out.println(precisionRecall(comparison));


//            printAverageConfusion("Average", allCounts);


            elapsedTimes.add(elapsed);
            this.out.println("\nElapsed: " + elapsed + " ms");
        }


        printAverageConfusion("Average", allCounts, new DecimalFormat("0.0"));

        printAveragePrecisionRecall(comparisons);

        this.out.println("Number of runs skipped because of undefined accuracies: " + numSkipped);

        printAverageStatistics(elapsedTimes, degrees);

        this.out.close();
    }

    private String precisionRecall(double[] comparison) {
        StringBuilder b = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.00");

        b.append("\n");
        b.append("APRE\tAREC\tOPRE\tOREC\n");
        b.append(nf.format(comparison[0] * 100)).append("%\t").append(nf.format(comparison[1] * 100)).append("%\t").append(nf.format(comparison[2] * 100)).append("%\t").append(nf.format(comparison[3] * 100)).append("%");
        return b.toString();
    }

    public void testGFciComparison() {
        int numVars = 1000;
        double edgeFactor = 1.0;
        int numLatents = 100;
        int numCases = 1000;
        final int numRuns = 5;
        final double alpha = 0.01;
        final double penaltyDiscount = 3.0;
        final int depth = 3;
        final int maxPathLength = 3;
        final boolean possibleDsepDone = true;
        final boolean completeRuleSetUsed = false;
        final boolean faithfulnessAssumed = true;

        init(new File("fci.algorithm.comparison" + numVars + "." + (int) (edgeFactor * numVars) +
                "." + numCases + ".txt"), "Num runs = " + numRuns);
        this.out.println("Num vars = " + numVars);
        this.out.println("Num edges = " + (int) (numVars * edgeFactor));
        this.out.println("Num cases = " + numCases);
        this.out.println("Alpha = " + alpha);
        this.out.println("Penalty discount = " + penaltyDiscount);
        this.out.println("Depth = " + depth);
        this.out.println("Maximum reachable path length for dsep search and discriminating undirectedPaths = " + maxPathLength);
        this.out.println("Num additional latent common causes = " + numLatents);
        this.out.println("Possible Dsep Done = " + possibleDsepDone);
        this.out.println("Complete Rule Set Used = " + completeRuleSetUsed);
        this.out.println();

        List<GraphUtils.GraphComparison> ffciCounts = new ArrayList<>();
        List<double[]> ffciArrowStats = new ArrayList<>();
        List<double[]> ffciTailStats = new ArrayList<>();
        List<Long> ffciElapsedTimes = new ArrayList<>();

        for (int run = 0; run < numRuns; run++) {

            this.out.println("\n\n\n******************************** RUN " + (run + 1) + " ********************************\n\n");

            System.out.println("Making list of vars");

            List<Node> vars = new ArrayList<>();

            for (int i = 0; i < numVars; i++) {
                vars.add(new ContinuousVariable("X" + (i + 1)));
            }

            System.out.println("Finishing list of vars");
            Graph dag = getLatentGraph(vars, edgeFactor, numLatents);

            System.out.println("Graph done");

            DagToPag dagToPag = new DagToPag(dag);
            dagToPag.setCompleteRuleSetUsed(false);
            dagToPag.setMaxPathLength(maxPathLength);
            Graph truePag = dagToPag.convert();

            System.out.println("True PAG_of_the_true_DAG done");

            // Data.
            System.out.println("Starting simulation");

            LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
            simulator.setCoefRange(.5, 1.5);
            simulator.setVarRange(1, 3);

            DataSet data = simulator.simulateDataFisher(numCases);

            data = DataUtils.restrictToMeasured(data);

            System.out.println("Finishing simulation");

            System.out.println("Making covariance matrix");

            ICovarianceMatrix cov = new CovarianceMatrix(data);

            System.out.println("Covariance matrix done");

            // Independence test.
            IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);
            SemBicScore score = new SemBicScore(cov);
            score.setPenaltyDiscount(penaltyDiscount);

            Graph estPag;
            long elapsed;

//            out.println("\n\n\n========================FCI run " + (run + 1));
            this.out.println("\n\n\n========================TGFCI run " + (run + 1));

            long ta1 = System.currentTimeMillis();

//            FCI fci = new FCI(independenceTest);
            GFci fci = new GFci(independenceTest, score);
            fci.setMaxDegree(depth);
            fci.setMaxPathLength(maxPathLength);
//            fci.setPossibleDsepSearchDone(possibleDsepDone);
            fci.setCompleteRuleSetUsed(completeRuleSetUsed);
            fci.setFaithfulnessAssumed(faithfulnessAssumed);
            estPag = fci.search();

            long ta2 = System.currentTimeMillis();

            estPag = GraphUtils.replaceNodes(estPag, truePag.getNodes());

            Set<Node> missingNodes = new HashSet<>();

            for (Node node : dag.getNodes()) {
                if (!estPag.containsNode(node)) {
                    missingNodes.add(node);
                }
            }

            ffciArrowStats.add(printCorrectArrows(dag, estPag, truePag));
            ffciTailStats.add(printCorrectTails(dag, estPag, truePag));

            ffciCounts.add(SearchGraphUtils.getGraphComparison2(estPag, truePag));

            elapsed = ta2 - ta1;
            ffciElapsedTimes.add(elapsed);
            this.out.println("\nElapsed: " + elapsed + " ms");

            try {
                PrintStream out2 = new PrintStream(new File("dag." + run + ".txt"));
                out2.println(dag);

                PrintStream out3 = new PrintStream(new File("estpag." + run + ".txt"));
                out3.println(estPag);

                PrintStream out4 = new PrintStream(new File("truepag." + run + ".txt"));
                out4.println(truePag);

                out2.close();
                out3.close();
                out4.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

//        printAverageConfusion("Average", ffciCounts);
        printAverageStatistics(ffciElapsedTimes, new ArrayList<>());

        this.out.close();

    }

    // Compares two different ways of calculating a PAG_of_the_true_DAG from a DAG, to see if they match up
    public void testCompareDagToCPDAG(int numLatents) {
        System.out.println("Making list of vars");

        int numVars = 20;
        double edgeFactor = 2.0;
        int numEdges = (int) (numVars * edgeFactor);

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag;
        if (false) {
            dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);
        } else {
//            dag = DataGraphUtils.randomDagRandomFowardEdges(vars, 0, numEdges);
            dag = GraphUtils.randomGraph(vars, 0, numEdges, 100, 100, 100, false);
            List<Node> ordering = dag.getCausalOrdering();
        }
        System.out.println("DAG = " + dag);

        System.out.println("Graph done");

        IndTestDSep test = new IndTestDSep(dag, true);
        Graph left = new Pc(test).search();

        System.out.println("PC graph = " + left);

        Graph top = SearchGraphUtils.cpdagForDag(dag);

        System.out.println("DAG to CPDAG graph = " + top);

        top = GraphUtils.replaceNodes(top, left.getNodes());

        System.out.println("True DAG = " + dag);
        System.out.println("FCI DAG with dsep = " + left);
        System.out.println("DAG to PAG_of_the_true_DAG = " + top);

        System.out.println("Correcting nodes");
        top = GraphUtils.replaceNodes(top, left.getNodes());

        int[][] counts = GraphUtils.edgeMisclassificationCounts(left, top, true);
        System.out.println(GraphUtils.edgeMisclassifications(counts));

        Set<Edge> leftEdges = left.getEdges();
        leftEdges.removeAll(top.getEdges());
        System.out.println("FCI but not DAGTOPAG " + leftEdges);

        Set<Edge> topEdges = top.getEdges();
        topEdges.removeAll(left.getEdges());
        System.out.println("DAGTOPAG but not FCI " + topEdges);

        System.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");
    }

    // Fas is calibrated; we need to calibrate other FAS versions to it.
    public void testComparePcVersions(int numVars, double edgeFactor, int numLatents) {
        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (vars.size() * edgeFactor),
                30, 15, 15, false, true);

        System.out.println("DAG = " + dag);

        System.out.println(dag);

        System.out.println("Graph done");

        Graph left = SearchGraphUtils.cpdagForDag(dag);//  pc1.search();

        System.out.println("First FAS graph = " + left);

        PcStable pc2 = new PcStable(new IndTestDSep(dag));
        Graph top = pc2.search();

        System.out.println("Second FAS graph = " + top);

        top = GraphUtils.replaceNodes(top, left.getNodes());
//
        top = GraphUtils.replaceNodes(top, left.getNodes());

        int[][] counts = GraphUtils.edgeMisclassificationCounts(left, top, true);
//        int[][] counts = edgeMisclassificationCounts(top, top);
        System.out.println(GraphUtils.edgeMisclassifications(counts));

        Set<Edge> leftEdges = left.getEdges();
        leftEdges.removeAll(top.getEdges());
        System.out.println("FAS1 but not Fas " + leftEdges);

        Set<Edge> topEdges = top.getEdges();
        topEdges.removeAll(left.getEdges());
        System.out.println("Fas but not FAS1 " + topEdges);

        System.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");
    }

    private void init(File file, String x) {
        boolean writeToFile = true;
        if (writeToFile) {
            try {
                File dir = new File("performance");
                dir.mkdir();
                File _file = new File(dir, file.getName());
                this.out = new PrintStream(_file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        this.out.println(x);
    }

    private Graph makeDag(int numVars, double edgeFactor) {
        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");

        //        printDegreeDistribution(dag, out);
        return GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 12, 15, false, true);
    }

    private void printDegreeDistribution(Graph dag, PrintStream out) {
        int max = 0;

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            if (degree > max) max = degree;
        }

        int[] counts = new int[max + 1];
        Map<Integer, List<Node>> names = new HashMap<>();

        for (int i = 0; i <= max; i++) {
            names.put(i, new ArrayList<>());
        }

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            counts[degree]++;
            names.get(degree).add(node);
        }

        for (int k = 0; k < counts.length; k++) {
            if (counts[k] == 0) continue;

            out.print(k + " " + counts[k]);

            for (Node node : names.get(k)) {
                out.print(" " + node.getName());
            }

            out.println();
        }
    }

    private void printIndegreeDistribution(Graph dag, PrintStream out) {
        int max = 0;

        for (Node node : dag.getNodes()) {
            int degree = dag.getIndegree(node);
            if (degree > max) max = degree;
        }

        int[] counts = new int[max + 1];
        Map<Integer, List<Node>> names = new HashMap<>();

        for (int i = 0; i <= max; i++) {
            names.put(i, new ArrayList<>());
        }

        for (Node node : dag.getNodes()) {
            int degree = dag.getIndegree(node);
            counts[degree]++;
            names.get(degree).add(node);
        }

        for (int k = 0; k < counts.length; k++) {
            out.print(k + " " + counts[k]);

            for (Node node : names.get(k)) {
                out.print(" " + node.getName());
            }

            out.println();
        }
    }

    private List<Node> getParentsInOrdering(List<Node> ordering, int i, Graph graph) {
        Node n1 = ordering.get(i);

        List<Node> parents = new ArrayList<>();

        for (Node n2 : ordering) {
            if (graph.isParentOf(n2, n1)) {
                parents.add(n2);
            }
        }

        return parents;
    }

    private void bidirectedComparison(Graph dag, Graph truePag, Graph estGraph, Set<Node> missingNodes) {
        System.out.println("Bidirected comparison");

        for (Edge edge : estGraph.getEdges()) {
            if (!estGraph.containsEdge(Edges.bidirectedEdge(edge.getNode1(), edge.getNode2()))) {
                continue;
            }
            if (!(truePag.containsEdge(Edges.partiallyOrientedEdge(edge.getNode1(), edge.getNode2()))
                    || truePag.containsEdge(Edges.partiallyOrientedEdge(edge.getNode2(), edge.getNode1())))) {
                continue;
            }

            boolean existsCommonCause = false;

            for (Node latent : missingNodes) {
                if (dag.existsDirectedPathFromTo(latent, edge.getNode1())
                        && dag.existsDirectedPathFromTo(latent, edge.getNode2())) {
                    existsCommonCause = true;
                    break;
                }
            }

            System.out.println("Edge " + edge + " common cause = " + existsCommonCause);
        }

        System.out.println();
    }

    private void printAverageStatistics(List<Long> elapsedTimes, List<Double> degrees) {
        NumberFormat nf =
                new DecimalFormat("0");
        NumberFormat nf2 = new DecimalFormat("0.00");

        this.out.println();

        if (degrees.size() > 0) {
            double sumDegrees = 0;

            for (Double degree : degrees) {
                sumDegrees += degree;
            }

            double avgDegree = sumDegrees / degrees.size();

            this.out.println();
            this.out.println("Avg Max Degree of Output CPDAG = " + nf2.format(avgDegree));
        }

        double sumElapsed = 0;

        for (Long e : elapsedTimes) {
            sumElapsed += (double) e;
        }

        this.out.println();
        this.out.println("Average Elapsed Time = " + nf.format(sumElapsed / (double) elapsedTimes.size()) + " ms");
        this.out.println();
    }

    private void printAverageConfusion(String name, List<int[][]> comparisons) {
        printAverageConfusion(name, comparisons, new DecimalFormat("0.0"));
    }

    private void printAverageConfusion(String name, List<int[][]> comparisons, NumberFormat nf) {


        int rows = comparisons.get(0).length;
        int cols = comparisons.get(0)[0].length;

        double[][] average = new double[rows][cols];
        double[][] _sum = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int sum = 0;

                for (int[][] comparison : comparisons) {
                    sum += comparison[i][j];
                }

                average[i][j] = (double) sum / comparisons.size();
                _sum[i][j] += sum;
            }
        }

        this.out.println();
        this.out.println(name);
        this.out.println(GraphUtils.edgeMisclassifications(average, nf));
    }

    private void printAveragePrecisionRecall(List<double[]> comparisons) {
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;

        for (double[] comparison : comparisons) {
            sum1 += comparison[0];
            sum2 += comparison[1];
            sum3 += comparison[2];
            sum4 += comparison[3];
        }

        double avg1 = sum1 / comparisons.size();
        double avg2 = sum2 / comparisons.size();
        double avg3 = sum3 / comparisons.size();
        double avg4 = sum4 / comparisons.size();

        StringBuilder b = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.00");

        b.append("\n");
        b.append("APRE\tAREC\tOPRE\tOREC\n");
        b.append(nf.format(avg1 * 100)).append("%\t").append(nf.format(avg2 * 100)).append("%\t").append(nf.format(avg3 * 100)).append("%\t").append(nf.format(avg4 * 100)).append("%");

        this.out.println(b);
    }

    private void printAggregatedPrecisionRecall(int[][] counts) {

        int sumRow = counts[4][0] + counts[4][3] + counts[4][5];
        int sumCol = counts[0][3] + counts[4][3] + counts[5][3] + counts[7][3];
        int trueArrow = counts[4][3];

        int sumTrueAdjacencies = 0;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 5; j++) {
                sumTrueAdjacencies += counts[i][j];
            }
        }

        int falsePositiveAdjacencies = 0;

        for (int j = 0; j < 5; j++) {
            falsePositiveAdjacencies += counts[7][j];
        }

        int falseNegativeAdjacencies = 0;

        for (int i = 0; i < 5; i++) {
            falseNegativeAdjacencies += counts[i][5];
        }

        double[] comparison = new double[4];

        comparison[0] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falsePositiveAdjacencies);
        comparison[1] = sumTrueAdjacencies / (double) (sumTrueAdjacencies + falseNegativeAdjacencies);

        comparison[2] = trueArrow / (double) sumCol;
        comparison[3] = trueArrow / (double) sumRow;
        StringBuilder b = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.00");

        b.append("\n");
        b.append("APRE\tAREC\tOPRE\tOREC\n");
        b.append(nf.format(comparison[0] * 100)).append("%\t").append(nf.format(comparison[1] * 100)).append("%\t").append(nf.format(comparison[2] * 100)).append("%\t").append(nf.format(comparison[3] * 100)).append("%");

        this.out.println(b);
    }

    private double[] printCorrectArrows(Graph dag, Graph outGraph, Graph truePag) {
        int correctArrows = 0;
        int totalEstimatedArrows = 0;
        int totalTrueArrows = 0;
        int correctNonAncestorRelationships = 0;

        double[] stats = new double[6];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.ARROW) {
                if (!dag.isAncestorOf(x, y)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }

            if (ey == Endpoint.ARROW) {
                if (!dag.isAncestorOf(y, x)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.ARROW) {
                totalTrueArrows++;
            }

            if (ey == Endpoint.ARROW) {
                totalTrueArrows++;
            }
        }

        this.out.println();
        this.out.println("# correct arrows = " + correctArrows);
        this.out.println("# total estimated arrows = " + totalEstimatedArrows);
        this.out.println("# correct arrow nonancestor relationships = " + correctNonAncestorRelationships);
        this.out.println("# total true arrows = " + totalTrueArrows);

        this.out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        double precision = correctArrows / (double) totalEstimatedArrows;
        this.out.println("Arrow precision = " + nf.format(precision));
        double recall = correctArrows / (double) totalTrueArrows;
        this.out.println("Arrow recall = " + nf.format(recall));
        double proportionCorrectNonAncestorRelationships = correctNonAncestorRelationships /
                (double) totalEstimatedArrows;
        this.out.println("Proportion correct arrow nonancestor relationships " + nf.format(proportionCorrectNonAncestorRelationships));

        stats[0] = correctArrows;
        stats[1] = totalEstimatedArrows;
        stats[2] = totalTrueArrows;
        stats[3] = precision;
        stats[4] = recall;
        stats[5] = proportionCorrectNonAncestorRelationships;

        return stats;
    }

    private double[] printCorrectTails(Graph dag, Graph outGraph, Graph truePag) {
        int correctTails = 0;
        int correctAncestorRelationships = 0;
        int totalEstimatedTails = 0;
        int totalTrueTails = 0;

        double[] stats = new double[6];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.TAIL) {
                if (dag.isAncestorOf(x, y)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }

            if (ey == Endpoint.TAIL) {
                if (dag.isAncestorOf(y, x)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.TAIL) {
                totalTrueTails++;
            }

            if (ey == Endpoint.TAIL) {
                totalTrueTails++;
            }
        }

        this.out.println();
        this.out.println("# correct tails = " + correctTails);
        this.out.println("# total estimated tails = " + totalEstimatedTails);
        this.out.println("# correct tail ancestor relationships = " + correctAncestorRelationships);
        this.out.println("# total true tails = " + totalTrueTails);

        this.out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        double precision = correctTails / (double) totalEstimatedTails;
        this.out.println("Tail precision = " + nf.format(precision));
        double recall = correctTails / (double) totalTrueTails;
        this.out.println("Tail recall = " + nf.format(recall));
        double proportionCorrectAncestorRelationships = correctAncestorRelationships /
                (double) totalEstimatedTails;
        this.out.println("Proportion correct tail ancestor relationships " + nf.format(proportionCorrectAncestorRelationships));

        stats[0] = correctTails;
        stats[1] = totalEstimatedTails;
        stats[2] = totalTrueTails;
        stats[3] = precision;
        stats[4] = recall;
        stats[5] = proportionCorrectAncestorRelationships;

        return stats;
    }

    public static String endpointMisclassification(List<Node> _nodes, Graph estGraph, Graph refGraph) {
        int[][] counts = new int[4][4];

        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = 0; j < _nodes.size(); j++) {
                if (i == j) continue;

                Endpoint endpoint1 = refGraph.getEndpoint(_nodes.get(i), _nodes.get(j));
                Endpoint endpoint2 = estGraph.getEndpoint(_nodes.get(i), _nodes.get(j));

                int index1 = PerformanceTests.getIndex(endpoint1);
                int index2 = PerformanceTests.getIndex(endpoint2);

                counts[index1][index2]++;
            }
        }

        TextTable table2 = new TextTable(5, 5);

        table2.setToken(0, 1, "-o");
        table2.setToken(0, 2, "->");
        table2.setToken(0, 3, "--");
        table2.setToken(0, 4, "NO EDGE");
        table2.setToken(1, 0, "-o");
        table2.setToken(2, 0, "->");
        table2.setToken(3, 0, "--");
        table2.setToken(4, 0, "NO EDGE");

        int sum = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) continue;
                else sum += counts[i][j];
            }
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) table2.setToken(3 + 1, j + 1, "");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();

    }


    private static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
    }

    private Graph getLatentGraph(List<Node> vars, double edgeFactor, int numLatents) {
        int numEdges = (int) (vars.size() * edgeFactor);

        return GraphUtils.randomGraph(vars,
                numLatents, numEdges, 3, 3, 3, false);
    }

    @Test
    public void printGraphDegrees() {
        final int numVars = 30000;
        final int numEdges = 60000;

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(numVars, 0, numEdges,
                30, 30, 30, false);

        TreeMap<Integer, Integer> degreeCounts = new TreeMap<>();
        List<Node> nodes = graph.getNodes();

        for (int i = 0; i < numVars; i++) {
            Node node = nodes.get(i);

            List<Node> adj = graph.getAdjacentNodes(node);

            int degree = adj.size();

            if (degreeCounts.get(degree) == null) {
                degreeCounts.put(degree, 0);
            }

            degreeCounts.put(degree, degreeCounts.get(degree) + 1);
        }

        for (int i : degreeCounts.keySet()) {
            System.out.println(i + " " + degreeCounts.get(i));
        }
    }


    public static void main(String... args) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);
        System.out.println("Start ");

        PerformanceTests performanceTests = new PerformanceTests();


        if (args.length == 3) {
            if ("GFCI".equals(args[0])) {
                int numVars = Integer.parseInt(args[1]);
                double edgeFactor = Double.parseDouble(args[2]);
//                    final int numCases = Integer.parseInt(args[3]);
                performanceTests.testGfci(numVars, edgeFactor);
            } else {
                throw new IllegalArgumentException("Not a configuration!");
            }
        }
//        else if (args.length == 4) {
//            switch (args[0]) {
//                case "GFCI": {
//                    final int numVars = Integer.parseInt(args[1]);
//                    final double edgeFactor = Double.parseDouble(args[2]);
//                    final int numCases = Integer.parseInt(args[3]);
//                    performanceTests.testGfci(numVars, edgeFactor);
//                    break;
//                }
//                default:
//                    throw new IllegalArgumentException("Not a configuration!");
//            }
//        }
        else if (args.length == 5) {
            switch (args[0]) {
                case "PC": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    double alpha = Double.parseDouble(args[4]);
                    performanceTests.testPc(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "PCSTABLE": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    double alpha = Double.parseDouble(args[4]);
                    performanceTests.testPcStable(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "CPCSTABLE": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    double alpha = Double.parseDouble(args[4]);
                    performanceTests.testCpcStable(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "TestFgesComparisonContinuous": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    int numRuns = Integer.parseInt(args[4]);

                    performanceTests.testFgesComparisonContinuous(numVars, edgeFactor, numCases, numRuns);
                    break;
                }
                case "TestFgesComparisonDiscrete": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    int numRuns = Integer.parseInt(args[4]);

                    performanceTests.testFgesComparisonDiscrete(numVars, edgeFactor, numCases, numRuns);
                    break;
                }
                case "TestFgesMbComparisonContinuous": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    int numRuns = Integer.parseInt(args[4]);

                    performanceTests.testFgesMbComparisonContinuous(numVars, edgeFactor, numCases, numRuns);
                    break;
                }
                case "TestFgesMbComparisonDiscrete": {
                    int numVars = Integer.parseInt(args[1]);
                    double edgeFactor = Double.parseDouble(args[2]);
                    int numCases = Integer.parseInt(args[3]);
                    int numRuns = Integer.parseInt(args[4]);

                    performanceTests.testFgesMbComparisonDiscrete(numVars, edgeFactor, numCases, numRuns);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Not a configuration.");
            }
        } else if (args.length == 6) {
            throw new IllegalArgumentException("Not a configuration.");
        }

        System.out.println("Finish");

        performanceTests.testPc(5000, 1, 1000, .0001);


//        performanceTests.testPcStable(20000, 1, 1000, .00001);
        performanceTests.testPcStableMax(5000, 1, 1000, .0001);

    }
}

