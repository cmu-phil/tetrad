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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;

import javax.swing.*;
import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.round;

/**
 * Runs some basic performance tests of various algorithms.
 *
 * @author Joseph Ramsey
 */
public class PerformanceTests {
    private PrintStream out = System.out;
    private boolean writeToFile = true;

    public void testPc(int numVars, double edgesPerNode, int numCases, double alpha) {
        int depth = -1;

        init(new File("long.pc." + numVars + ".txt"), "Tests performance of the FGS algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode),
                30, 15, 15, false);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorreqlationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.gc();

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        PcGes pc = new PcGes(test);
        pc.setVerbose(true);
        pc.setDepth(depth);
//        pcStable.setOut(out);

        Graph outGraph = pc.search();

        out.println(outGraph);

        long time4 = System.currentTimeMillis();

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        SearchGraphUtils.graphComparison(outGraph, SearchGraphUtils.patternForDag(graph), out);

        out.close();
    }

    public void printStuffForKlea() {

        try {
            File _data = new File("data.txt");
            File _graph = new File("graph.txt");

            PrintStream out1 = new PrintStream(new FileOutputStream(_data));
            PrintStream out2 = new PrintStream(new FileOutputStream(_graph));


            int numVars = 50000;

            List<Node> vars = new ArrayList<>();

            for (int i = 0; i < numVars; i++) {
                vars.add(new ContinuousVariable("X" + (i + 1)));
            }

            double edgesPerNode = 1.0;
            int numCases = 1000;

            Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode),
                    30, 15, 15, false);

            out2.println(graph);

            System.out.println("Graph done");

            out.println("Graph done");

            System.out.println("Starting simulation");
            LargeSemSimulator simulator = new LargeSemSimulator(graph);
            simulator.setOut(out);

            DataSet data = simulator.simulateDataAcyclic(numCases);

            out1.println(data);

            out1.close();
            out2.close();
        }
        catch (Exception e) {

        }


    }

    public void testPcStable(int numVars, double edgesPerNode, int numCases, double alpha) {
        int depth = -1;

        init(new File("long.pcstable." + numVars + ".txt"), "Tests performance of the PC Stable algorithm");

        long time1 = System.currentTimeMillis();

        Graph dag = makeDag(numVars, edgesPerNode);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(dag);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorrelationMatrix(new CovarianceMatrix(data));
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        Pc pcStable = new Pc(test);
//        pcStable.setVerbose(true);
        pcStable.setDepth(depth);
//        pcStable.setOut(out);

        Graph estPattern = pcStable.search(new FasStableConcurrent(test), test.getVariables());

//        out.println(estPattern);

        long time4 = System.currentTimeMillis();

//        out.println("# Vars = " + numVars);
//        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);
        out.println("alpha = " + alpha);
        out.println("depth = " + depth);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        System.out.println("# edges in true pattern = " + truePattern.getNumEdges());
        System.out.println("# edges in est pattern = " + estPattern.getNumEdges());

        SearchGraphUtils.graphComparison(estPattern, truePattern, out);

        out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");

        out.close();
    }

    public void testCpc(int numVars, double edgesPerNode, int numCases) {
        double alpha = 0.0001;
        int depth = -1;

        init(new File("long.cpc." + numVars + ".txt"), "Tests performance of the CPC algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode),
                30, 15, 15, false);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorreqlationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.gc();

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        Cpc cpc = new Cpc(test);
        cpc.setVerbose(true);
        cpc.setDepth(depth);
//        pcStable.setOut(out);

        Graph outGraph = cpc.search();

        out.println(outGraph);

        long time4 = System.currentTimeMillis();

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        SearchGraphUtils.graphComparison(outGraph, SearchGraphUtils.patternForDag(graph), out);

        out.close();
    }

    public void testCpcStable(int numVars, double edgesPerNode, int numCases, double alpha) {
        int depth = 3;

        init(new File("long.cpcstable." + numVars + ".txt"), "Tests performance of the CPC algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

//        Graph graph = DataGraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode));
//        Graph graph = DataGraphUtils.randomGraphUniform(vars, 0, (int) (numVars * edgesPerNode), 5, 5, 5, false);
        Graph graph = makeDag(numVars, edgesPerNode);


        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorrelationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.out.println("Covariance matrix done");

        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        CpcStable cpcStable = new CpcStable(test);
        cpcStable.setVerbose(true);
        cpcStable.setDepth(depth);
        cpcStable.setOut(out);

        Graph outGraph = cpcStable.search();

//        out.println(outGraph);

        long time4 = System.currentTimeMillis();

//        out.println("# Vars = " + numVars);
//        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running CPC-Stable) " + (time4 - time3) + " ms");

        out.println("Total elapsed (cov + CPC-Stable) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(graph);

        SearchGraphUtils.graphComparison(outGraph, truePattern, out);

        out.println("# ambiguous triples = " + outGraph.getAmbiguousTriples().size());

        out.close();
    }

    public void testFci(int numVars, double edgesPerNode, int numCases) {
        double alpha = 0.001;
        int depth = 3;

        init(new File("long.fci." + numVars + ".txt"), "Tests performance of the FCI algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode),
                30, 15, 15, false);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorreqlationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);

        Fci fci = new Fci(independenceTest);
        fci.setVerbose(true);
        fci.setDepth(depth);
        fci.setMaxPathLength(2);
//        fci.setTrueDag(truePag);
        Graph outGraph = fci.search();

        out.println(outGraph);

        long time4 = System.currentTimeMillis();

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running FCI) " + (time4 - time3) + " ms");

        out.close();
    }

    public void testGfci(int numVars, double edgesPerNode, int numCases) {
        double alpha = 0.001;
        int depth = 3;

        init(new File("long.gfci." + numVars + ".txt"), "Tests performance of the FCI-GES algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag = getLatentGraph(vars, edgesPerNode, 20);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");

        LargeSemSimulator simulator = new LargeSemSimulator(dag);
//            LargeSemSimulator simulator = new LargeSemSimulator(dag);
        simulator.setCoefRange(.5, 1.5);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        data = DataUtils.restrictToMeasured(data);

//            DataSet discreteData = DataUtils.discretize(data, 4, true);
//
//            try {
//                data = DataUtils.convertNumericalDiscreteToContinuous(discreteData);
//            } catch (NumberFormatException e) {
//                throw new RuntimeException("There were some non-numeric values in that dataset.");
//            }

        System.out.println("Finishing simulation");

        System.out.println("Num measured vars = " + data.getNumColumns());

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorreqlationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        out.println(cov);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);
        double penaltyDiscount = 2.0;
        int maxPathLength = 6;
        GFci fci = new GFci(independenceTest);
        fci.setVerbose(false);
        fci.setPenaltyDiscount(penaltyDiscount);
        fci.setMaxPathLength(maxPathLength);
        fci.setPossibleDsepSearchDone(true);
        fci.setDepth(depth);
        fci.setFaithfulnessAssumed(true);
        fci.setCompleteRuleSetUsed(false);
        Graph outGraph = fci.search();

        out.println(outGraph);

        long time4 = System.currentTimeMillis();

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgesPerNode));
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running FCI) " + (time4 - time3) + " ms");

        out.close();
    }

    public void testFgs(int numVars, double edgesPerNode, int numCases, double penaltyDiscount) {
        final int numEdges = (int) (numVars * edgesPerNode);

        init(new File("long.FGS." + numVars + "." + numEdges + "." + penaltyDiscount + ".txt"), "Tests performance of the FGS algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");
//        Graph dag;
//        dag = DataGraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges);
        Graph dag = makeDag(numVars, edgesPerNode);

        List<Node> vars = dag.getNodes();

        int[] causalOrdering = new int[vars.size()];

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");

        LargeSemSimulator simulator = new LargeSemSimulator(dag, vars, causalOrdering);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorrelationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = DataUtils.covarianceNonparanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

//        List<String> names = cov.getVariableNames();
//        Collections.shuffle(names);
//        cov = cov.getSubmatrix(names);

        SemBicScore score = new SemBicScore(cov, penaltyDiscount);

        Fgs fgs = new Fgs(score);
        fgs.setVerbose(false);
        fgs.setNumPatternsToStore(0);
        fgs.setPenaltyDiscount(penaltyDiscount);
        fgs.setOut(System.out);
        fgs.setFaithfulnessAssumed(true);
        fgs.setDepth(-1);
        fgs.setCycleBound(5);

//        IKnowledge knowledge = new Knowledge2();
//        knowledge.setForbidden("X1", "X2");

        System.out.println("\nStarting FGS");

        Graph estPattern = fgs.search();

        System.out.println("Done with FGS");

//        fgs.setKnowledge(knowledge);

        printDegreeDistribution(estPattern, System.out);

//        out.println(estPattern);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + numEdges);
        out.println("# Cases = " + numCases);
        out.println("Penalty discount = " + penaltyDiscount);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running FGS) " + (time4 - time3) + " ms");
        out.println("Elapsed (cov + FGS) " + (time4 - time2) + " ms");

//        graphComparison(estPattern, SearchGraphUtils.patternForDag(dag));

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        SearchGraphUtils.graphComparison(estPattern, truePattern, out);

//        printDegreeDistribution(estPattern, System.out);

        out.close();
    }

    public void testFgsDiscrete(int numVars, double edgeFactor, int numCases,
                                double structurePrior, double samplePrior) {
        init(new File("long.FGSDiscrete." + numVars + ".txt"), "Tests performance of the FGS algorithm");

        long time1 = System.currentTimeMillis();

        Graph dag = makeDag(numVars, edgeFactor);
        printDegreeDistribution(dag, System.out);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");

        BayesPm pm = new BayesPm(dag, 3, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        DataSet data = im.simulateData(numCases, false);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        BDeuScore score = new BDeuScore(data);
        score.setSamplePrior(samplePrior);
        score.setStructurePrior(structurePrior);

        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

        Fgs fgs = new Fgs(score);
        fgs.setVerbose(false);
        fgs.setNumPatternsToStore(0);
        fgs.setOut(out);
        fgs.setFaithfulnessAssumed(true);
        fgs.setDepth(3);

        System.out.println("\nStarting FGS");

        Graph estPattern = fgs.search();

        System.out.println("Done with FGS");

        out.println(estPattern);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgeFactor));
        out.println("# Cases = " + numCases);
        out.println("Structure prior = " + structurePrior);
        out.println("Sample prior = " + samplePrior);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (running FGS) " + (time4 - time3) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        SearchGraphUtils.graphComparison(estPattern, truePattern, out);
        printDegreeDistribution(estPattern, System.out);

        out.close();

        System.out.println("See output file.");
    }

    public void testFgsDiscrete(String path, double structurePrior, double samplePrior) {
//        numVars = 2000;
//        edgeFactor = 1.0;
//        numCases = 1000;
//        double structurePrior = .01;
//        double samplePrior = 10;

        if (writeToFile) {
            try {
                File _path = new File(path);
                final File file = new File("long.FGSDiscrete." + _path.getName() + ".txt");
                out = new PrintStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        try {
//            String path = "/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/tgen_10kvars.txt";
//            String path = "tgen_1kvars.txt";

            DataSet dataSet = BigDataSetUtility.readInContinuousData(new File(path), '\t');

            out.println("data set # vars = " + dataSet.getNumColumns() + " # rows = " + dataSet.getNumRows());

            long time1 = System.currentTimeMillis();

//            DataSet discreteData = DataUtils.discretize(dataSet, 4, false);

//            System.out.println(discreteData);

            long time2 = System.currentTimeMillis();

            Fgs fgs = new Fgs(dataSet);
            fgs.setVerbose(false);
            fgs.setNumPatternsToStore(0);
            fgs.setPenaltyDiscount(4);
            fgs.setOut(out);
            fgs.setFaithfulnessAssumed(true);
            fgs.setDepth(2);
            fgs.setStructurePrior(structurePrior);
            fgs.setSamplePrior(samplePrior);

            Graph graph = fgs.search();

            long time3 = System.currentTimeMillis();

            out.println(graph);

            out.println("Num edges = " + graph.getNumEdges());
            out.println("Discretize elapsed " + (time2 - time1) + "ms");
            out.println("FGS elapsed " + (time3 - time2) + "ms");
            out.println("Total elapsed " + (time3 - time1) + "ms");

            printDegreeDistribution(graph, out);

            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        out.close();
    }

    public void testSaveLoadDataText(int numVars, int numCases) {
        out.println("Tests saving and loading of data sets.");
        String dir = "/Users/josephramsey/Documents/proj/tetrad7/test_data";

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numVars, 30, 15, 15, false);

        try {
            File graphFile = new File(dir, "graph.txt");
            PrintWriter graphOut = new PrintWriter(new FileWriter(graphFile));
            graphOut.print(graph);
            graphOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        out.println("Graph generated.");
//        Graph graph = new EndpointMatrixGraph(DataGraphUtils.randomDagQuick(vars, 0, numVars));

    }

    public void testSaveLoadDataSerial(int numVars, int numCases) {
        out.println("Tests saving and loading of data sets.");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numVars, 30, 15, 15, false);;

        out.println("Graph generated.");
//        Graph graph = new EndpointMatrixGraph(DataGraphUtils.randomDagQuick(vars, 0, numVars));

        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        simulator.setOut(out);

        DataSet data = simulator.simulateDataAcyclic(numCases);

//        out.println(data);

        String dir = "/Users/josephramsey/Documents/proj/tetrad7/test_data";
        File file = new File(dir, "test.txt");

        out.println("Data generated.");

        try {
            long time1 = System.currentTimeMillis();

            FileOutputStream _out = new FileOutputStream(file);
            ObjectOutputStream objOut = new ObjectOutputStream(_out);

            objOut.writeObject(data);
            _out.close();

            long time2 = System.currentTimeMillis();
            out.println("Elapsed (saving the data): " + (time2 - time1) + " ms");

            FileInputStream in = new FileInputStream(file);
            ObjectInputStream objIn = new ObjectInputStream(in);
            objIn.readObject();

            long time3 = System.currentTimeMillis();
            out.println("Elapsed (loading the data): " + (time3 - time2) + " ms");
        } catch (Exception e2) {
            e2.printStackTrace();
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "An error occurred while attempting to save the session as " +
                            file.getAbsolutePath() + ".");
        }
    }

    public void testGFciComparison(int numVars, double edgesPerNode, int numCases, int numLatents) {
        numVars = 1000;
        edgesPerNode = 1.0;
        numLatents = 100;
        numCases = 1000;
        int numRuns = 5;
        double alpha = 0.01;
        double penaltyDiscount = 3.0;
        int depth = 3;
        int maxPathLength = 3;
        boolean possibleDsepDone = true;
        boolean completeRuleSetUsed = false;
        boolean faithfulnessAssumed = true;

        init(new File("fci.algorithms.comparison" + numVars + "." + (int) (edgesPerNode * numVars) +
                "." + numCases + ".txt"), "Num runs = " + numRuns);
        out.println("Num vars = " + numVars);
        out.println("Num edges = " + (int) (numVars * edgesPerNode));
        out.println("Num cases = " + numCases);
        out.println("Alpha = " + alpha);
        out.println("Penalty discount = " + penaltyDiscount);
        out.println("Depth = " + depth);
        out.println("Maximum reachable path length for dsep search and discriminating undirectedPaths = " + maxPathLength);
        out.println("Num additional latent common causes = " + numLatents);
        out.println("Possible Dsep Done = " + possibleDsepDone);
        out.println("Complete Rule Set Used = " + completeRuleSetUsed);
        out.println();

        List<int[][]> ffciCounts = new ArrayList<int[][]>();
        List<double[]> ffciArrowStats = new ArrayList<double[]>();
        List<double[]> ffciTailStats = new ArrayList<double[]>();
        List<Long> ffciElapsedTimes = new ArrayList<Long>();

        for (int run = 0; run < numRuns; run++) {

            out.println("\n\n\n******************************** RUN " + (run + 1) + " ********************************\n\n");

            System.out.println("Making list of vars");

            List<Node> vars = new ArrayList<Node>();

            for (int i = 0; i < numVars; i++) {
                vars.add(new ContinuousVariable("X" + (i + 1)));
            }

            System.out.println("Finishing list of vars");
            Graph dag = getLatentGraph(vars, edgesPerNode, numLatents);

            System.out.println("Graph done");

            final DagToPag dagToPag = new DagToPag(dag);
            dagToPag.setCompleteRuleSetUsed(false);
            dagToPag.setMaxPathLength(maxPathLength);
            Graph truePag = dagToPag.convert();

            System.out.println("True PAG done");

            // Data.
            System.out.println("Starting simulation");

            LargeSemSimulator simulator = new LargeSemSimulator(dag);
            simulator.setCoefRange(.5, 1.5);
            simulator.setVarRange(1, 3);

            DataSet data = simulator.simulateDataAcyclic(numCases);

            data = DataUtils.restrictToMeasured(data);

            System.out.println("Finishing simulation");

            System.out.println("Making covariance matrix");

            ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);

            System.out.println("Covariance matrix done");

            // Independence test.
            final IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);

            Graph estPag;
            long elapsed;

//            out.println("\n\n\n========================FCI run " + (run + 1));
            out.println("\n\n\n========================TGFCI run " + (run + 1));

            long ta1 = System.currentTimeMillis();

//            Fci fci = new Fci(independenceTest);
            GFci fci = new GFci(independenceTest);
//            TFci fci = new TFci(independenceTest);
//            fci.setVerbose(false);
            fci.setPenaltyDiscount(penaltyDiscount);
            fci.setDepth(depth);
            fci.setMaxPathLength(maxPathLength);
            fci.setPossibleDsepSearchDone(possibleDsepDone);
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

            ffciCounts.add(SearchGraphUtils.graphComparison(estPag, truePag, out));

            elapsed = ta2 - ta1;
            ffciElapsedTimes.add(elapsed);
            out.println("\nElapsed: " + elapsed + " ms");

            directedComparison(dag, truePag, estPag);
            bidirectedComparison(dag, truePag, estPag, missingNodes);

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

        printAverageConfusion("Average", ffciCounts);
        printAverageStatistics("Average", ffciArrowStats, ffciTailStats, ffciElapsedTimes);

        out.close();

    }

    // Compares two different ways of calculating a PAG from a DAG, to see if they match up
    public void testCompareDagToPattern(int numVars, double edgesPerNode, int numLatents) {
        System.out.println("Making list of vars");

        numVars = 20;
        edgesPerNode = 2.0;
        int numEdges = (int) (numVars * edgesPerNode);

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag;
        if (false) {
            dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false);
        } else {
//            dag = DataGraphUtils.randomDagRandomFowardEdges(vars, 0, numEdges);
            dag = GraphUtils.randomGraph(vars, 0, numEdges, 100, 100, 100, false);
//            dag = DataGraphUtils.randomDagUniform(vars, numEdges, false);
//            dag = DataGraphUtils.randomDagUniform(vars, 0, numEdges, 100, 100, 100, false);
            List<Node> ordering = dag.getCausalOrdering();
        }
        System.out.println("DAG = " + dag);

        System.out.println("Graph done");

        IndTestDSep test = new IndTestDSep(dag);
        Graph left = new Pc(test).search();

        System.out.println("PC graph = " + left);

        Graph top = SearchGraphUtils.patternForDag(dag);

        System.out.println("DAG to Pattern graph = " + top);

        top = GraphUtils.replaceNodes(top, left.getNodes());

        System.out.println("True DAG = " + dag);
        System.out.println("FCI DAG with dsep = " + left);
        System.out.println("DAG to PAG = " + top);

        int[][] counts = GraphUtils.edgeMisclassificationCounts(left, top, true);
        System.out.println(GraphUtils.edgeMisclassifications(counts));

        Set<Edge> leftEdges = left.getEdges();
        leftEdges.removeAll(top.getEdges());
        System.out.println("FCI but not DAGTOPAG " + leftEdges);

        Set<Edge> topEdges = top.getEdges();
        topEdges.removeAll(left.getEdges());
        System.out.println("DAGTOPAG but not FCI " + topEdges);

//        final List<Node> path = DataGraphUtils.getInducingPath(dag.getNode("X14"), dag.getNode("X11"), dag);
//        System.out.println(DataGraphUtils.pathString(path, dag));

        System.out.println("seed = " + RandomUtil.getInstance().getSeed() + "L");
    }

    // Fas is calibrated; we need to calibrate other FAS versions to it.
    public void testCompareFasVersions(int numVars, double edgesPerNode, int numLatents) {
        System.out.println("Making list of vars");

//        RandomUtil.getInstance().setSeed(1429287088750L);
//        RandomUtil.getInstance().setSeed(1429287454751L);
//        RandomUtil.getInstance().setSeed(1429309942146L);

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (vars.size() * edgesPerNode),
                30, 15, 15, false);

        System.out.println("DAG = " + dag);

        System.out.println(dag);

        System.out.println("Graph done");

        Fas fas = new Fas(new IndTestDSep(dag));

        Graph left = fas.search();

        System.out.println("First FAS graph = " + left);

        final FasStableConcurrent fas2 = new FasStableConcurrent(new IndTestDSep(dag));
        Graph top = fas2.search();

        System.out.println("Second FAS graph = " + top);

        top = GraphUtils.replaceNodes(top, left.getNodes());
//
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

    // Fas is calibrated; we need to calibrate other FAS versions to it.
    public void testComparePcVersions(int numVars, double edgesPerNode, int numLatents) {
        System.out.println("Making list of vars");

//        RandomUtil.getInstance().setSeed(1429287088750L);
//        RandomUtil.getInstance().setSeed(1429287454751L);
//        RandomUtil.getInstance().setSeed(1429309942146L);

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (vars.size() * edgesPerNode),
                30, 15, 15, false);

        System.out.println("DAG = " + dag);

        System.out.println(dag);

        System.out.println("Graph done");

        Graph left = SearchGraphUtils.patternForDag(dag);//  pc1.search();

        System.out.println("First FAS graph = " + left);

        final PcStable pc2 = new PcStable(new IndTestDSep(dag));
        Graph top = pc2.search();

        System.out.println("Second FAS graph = " + top);

        top = GraphUtils.replaceNodes(top, left.getNodes());
//
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

    public void testDagToPagOnly(int numVars, double edgesPerNode, int numLatents) {
        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        System.out.println("Finishing list of vars");

//        Graph dag = DataGraphUtils.randomDagQuick2(vars, 0, (int) (vars.size() * edgesPerNode));
        Dag dag = new Dag(getLatentGraph(vars, edgesPerNode, numLatents));

        System.out.println(dag);

        System.out.println("Graph done");

        long time2a = System.currentTimeMillis();

        final DagToPag dagToPag = new DagToPag(dag);
        dagToPag.setCompleteRuleSetUsed(true);
        Graph top = dagToPag.convert();

        long time2b = System.currentTimeMillis();

//        top = DataGraphUtils.replaceNodes(top, left.getNodes());

//        int[][] counts = edgeMisclassificationCounts(left, top);
        int[][] counts = GraphUtils.edgeMisclassificationCounts(top, top, true);
        System.out.println(GraphUtils.edgeMisclassifications(counts));

//        System.out.println("Elapsed fci = " + (time1b - time1a) + " ms");
        System.out.println("Elapsed dagtopag = " + (time2b - time2a) + " ms");
    }

//    public void testIcaOutputForDan() {
//        int numRuns = 100;
//
//        for (int run = 0; run < numRuns; run++) {
//            double alphaGFci = 0.01;
//            double alphaPc = 0.01;
//            int penaltyDiscount = 1;
//            int depth = 3;
//            int maxPathLength = 3;
//
//            final int numVars = 15;
//            final double edgesPerNode = 1.0;
//            final int numCases = 1000;
//            final int numLatents = RandomUtil.getInstance().nextInt(3) + 1;
//
////        writeToFile = false;
//
//            PrintStream out1 = System.out;
//            PrintStream out2 = System.out;
//            PrintStream out3 = System.out;
//            PrintStream out4 = System.out;
//            PrintStream out5 = System.out;
//            PrintStream out6 = System.out;
//            PrintStream out7 = System.out;
//            PrintStream out8 = System.out;
//            PrintStream out9 = System.out;
//            PrintStream out10 = System.out;
//            PrintStream out11 = System.out;
//            PrintStream out12 = System.out;
//
//            if (writeToFile) {
//                File dir0 = new File("fci.output");
//                dir0.mkdirs();
//
//                File dir = new File(dir0, "" + (run + 1));
//                dir.mkdir();
//
//                try {
//                    out1 = new PrintStream(new File(dir, "hyperparameters.txt"));
//                    out2 = new PrintStream(new File(dir, "variables.txt"));
//                    out3 = new PrintStream(new File(dir, "dag.long.txt"));
//                    out4 = new PrintStream(new File(dir, "dag.matrix.txt"));
//                    out5 = new PrintStream(new File(dir, "coef.matrix.txt"));
//                    out6 = new PrintStream(new File(dir, "pag.long.txt"));
//                    out7 = new PrintStream(new File(dir, "pag.matrix.txt"));
//                    out8 = new PrintStream(new File(dir, "pattern.long.txt"));
//                    out9 = new PrintStream(new File(dir, "pattern.matrix.txt"));
//                    out10 = new PrintStream(new File(dir, "data.txt"));
//                    out11 = new PrintStream(new File(dir, "true.pag.long.txt"));
//                    out12 = new PrintStream(new File(dir, "true.pag.matrix.txt"));
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                    throw new RuntimeException(e);
//                }
//            }
//
//            out1.println("Num _vars = " + numVars);
//            out1.println("Num edges = " + (int) (numVars * edgesPerNode));
//            out1.println("Num cases = " + numCases);
//            out1.println("Alpha for PC = " + alphaPc);
//            out1.println("Alpha for FFCI = " + alphaGFci);
//            out1.println("Penalty discount = " + penaltyDiscount);
//            out1.println("Depth = " + depth);
//            out1.println("Maximum reachable path length for dsep search and discriminating undirectedPaths = " + maxPathLength);
//
//            List<Node> vars = new ArrayList<Node>();
//            for (int i = 0; i < numVars; i++) vars.add(new GraphNode("X" + (i + 1)));
//
////        Graph dag = DataGraphUtils.randomDagQuick2(varsWithLatents, 0, (int) (varsWithLatents.size() * edgesPerNode));
//            Graph dag = GraphUtils.randomGraph(vars, 0, (int) (vars.size() * edgesPerNode), 5, 5, 5, false);
//
//            GraphUtils.fixLatents1(numLatents, dag);
////        List<Node> varsWithLatents = new ArrayList<Node>();
////
////        Graph dag = getLatentGraph(_vars, varsWithLatents, edgesPerNode, numLatents);
//
//
//            out3.println(dag);
//
//            printDanMatrix(vars, dag, out4);
//
//            SemPm pm = new SemPm(dag);
//            SemIm im = new SemIm(pm);
//            NumberFormat nf = new DecimalFormat("0.0000");
//
//            for (int i = 0; i < vars.size(); i++) {
//                for (int j = 0; j < vars.size(); j++) {
//                    if (im.existsEdgeCoef(vars.get(j), vars.get(i))) {
//                        double coef = im.getEdgeCoef(vars.get(j), vars.get(i));
//                        out5.print(nf.format(coef) + "\t");
//                    } else {
//                        out5.print(nf.format(0) + "\t");
//                    }
//                }
//
//                out5.println();
//            }
//
//            out5.println();
//
//            out2.println(vars);
//
//            List<Node> _vars = new ArrayList<Node>();
//
//            for (Node node : vars) {
//                if (node.getNodeType() == NodeType.MEASURED) {
//                    _vars.add(node);
//                }
//            }
//
//            out2.println(_vars);
//
//            DataSet fullData = im.simulateData(numCases, false);
//
//            DataSet data = DataUtils.restrictToMeasured(fullData);
//
//            ICovarianceMatrix cov = new CovarianceMatrix(data);
//
//            final IndTestFisherZ independenceTestGFci = new IndTestFisherZ(cov, alphaGFci);
//
//            out6.println("FCI.FGS.PAG");
//
//            GFci GFci = new GFci(independenceTestGFci);
//            GFci.setVerbose(false);
//            GFci.setPenaltyDiscount(penaltyDiscount);
//            GFci.setDepth(depth);
//            GFci.setMaxPathLength(maxPathLength);
//            GFci.setPossibleDsepSearchDone(true);
//            GFci.setCompleteRuleSetUsed(true);
//
//            Graph pag = GFci.search();
//
//            pag = GraphUtils.replaceNodes(pag, _vars);
//
//            out6.println(pag);
//
//            printDanMatrix(_vars, pag, out7);
//
//            out8.println("PATTERN OVER MEASURED VARIABLES");
//
//            final IndTestFisherZ independencePc = new IndTestFisherZ(cov, alphaPc);
//
//            Pc pc = new Pc(independencePc);
//            pc.setVerbose(false);
//            pc.setDepth(depth);
//
//            Graph pattern = pc.search();
//
//            pattern = GraphUtils.replaceNodes(pattern, _vars);
//
//            out8.println(pattern);
//
//            printDanMatrix(_vars, pattern, out9);
//
//            out10.println(data);
//
//            out11.println("True PAG");
//            final Graph truePag = new DagToPag(dag).convert();
//            out11.println(truePag);
//            printDanMatrix(_vars, truePag, out12);
//
//            out1.close();
//            out2.close();
//            out3.close();
//            out4.close();
//            out5.close();
//            out6.close();
//            out7.close();
//            out8.close();
//            out9.close();
//            out10.close();
//            out11.close();
//            out12.close();
//        }
//    }

//    private void printDanMatrix(List<Node> vars, Graph pattern, PrintStream out) {
//        for (int i = 0; i < vars.size(); i++) {
//            for (int j = 0; j < vars.size(); j++) {
//                Edge edge = pattern.getEdge(vars.get(i), vars.get(j));
//
//                if (edge == null) {
//                    out.print(0 + "\t");
//                } else {
//                    Endpoint ej = edge.getProximalEndpoint(vars.get(j));
//
//                    if (ej == Endpoint.TAIL) {
//                        out.print(3 + "\t");
//                    } else if (ej == Endpoint.ARROW) {
//                        out.print(2 + "\t");
//                    } else if (ej == Endpoint.CIRCLE) {
//                        out.print(1 + "\t");
//                    }
//                }
//            }
//
//            out.println();
//        }
//
//        out.println();
//    }

    private void init(File file, String x) {
        if (writeToFile) {
            try {
                File dir = new File("performance");
                dir.mkdir();
                File _file = new File(dir, file.getName());
                out = new PrintStream(_file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        out.println(x);
    }

    private Graph makeDag(int numVars, double edgesPerNode) {
        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");

        //        printDegreeDistribution(dag, out);
        return GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode),
                30, 15, 15, false);
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
            names.put(i, new ArrayList<Node>());
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
            names.put(i, new ArrayList<Node>());
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


    private void directedComparison(Graph dag, Graph truePag, Graph estGraph) {
        System.out.println("Directed comparison");

        for (Edge edge : estGraph.getEdges()) {
            if (!estGraph.containsEdge(Edges.directedEdge(edge.getNode1(), edge.getNode2()))) {
                continue;
            }
            if (!truePag.containsEdge(Edges.partiallyOrientedEdge(edge.getNode1(), edge.getNode2()))) {
                continue;
            }

            boolean path = dag.existsDirectedPathFromTo(Edges.getDirectedEdgeTail(edge), Edges.getDirectedEdgeHead(edge));

            System.out.println("Edge " + edge + " directed path = " + path);
        }

        System.out.println();
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

    private void printAverageStatistics(String name, List<double[]> arrowStats, List<double[]> tailStats,
                                        List<Long> elapsedTimes) {
        NumberFormat nf =
                new DecimalFormat("0");
        NumberFormat nf2 = new DecimalFormat("0.00");

        out.println();
        out.println(name);

        double[] avgArrowStats = new double[6];

        for (int i = 0; i < avgArrowStats.length; i++) {
            double sum = 0.0;

            for (double[] d : arrowStats) {
                sum += d[i];
            }

            avgArrowStats[i] = sum / (double) arrowStats.size();
        }

        out.println();
        out.println("Avg Correct Arrows = " + nf.format(avgArrowStats[0]));
        out.println("Avg Estimated Arrows = " + nf.format(avgArrowStats[1]));
        out.println("Avg True Arrows = " + nf.format(avgArrowStats[2]));
        out.println("Avg Arrow Precision = " + nf2.format(avgArrowStats[3]));
        out.println("Avg Arrow Recall = " + nf2.format(avgArrowStats[4]));
        out.println("Avg Proportion Correct Nonancestor Relationships = " + nf2.format(avgArrowStats[5]));

        double[] avgTailStats = new double[6];

        for (int i = 0; i < avgTailStats.length; i++) {
            double sum = 0.0;

            for (double[] d : tailStats) {
                sum += d[i];
            }

            avgTailStats[i] = sum / (double) tailStats.size();
        }

        out.println();
        out.println("Avg Correct Tails = " + nf.format(avgTailStats[0]));
        out.println("Avg Estimated Tails = " + nf.format(avgTailStats[1]));
        out.println("Avg True Tails = " + nf.format(avgTailStats[2]));
        out.println("Avg Tail Precision = " + nf2.format(avgTailStats[3]));
        out.println("Avg Tail Recall = " + nf2.format(avgTailStats[4]));
        out.println("Avg Proportion Correct Ancestor Relationships = " + nf2.format(avgTailStats[5]));

        double sumElapsed = 0;

        for (Long e : elapsedTimes) {
            sumElapsed += (double) e;
        }

        out.println();
        out.println("Average Elapsed Time = " + nf.format(sumElapsed / (double) elapsedTimes.size()) + " ms");
        out.println();
    }

    private void printAverageConfusion(String name, List<int[][]> countsList) {
        final int rows = countsList.get(0).length;
        final int cols = countsList.get(0)[0].length;

        int[][] average = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int sum = 0;

                for (int[][] counts : countsList) {
                    sum += counts[i][j];
                }

                average[i][j] = (int) round((sum / (double) countsList.size()));
            }
        }

        out.println();
        out.println(name);
        out.println(GraphUtils.edgeMisclassifications(average));

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

            final Edge edge1 = truePag.getEdge(x, y);

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

        out.println();
        out.println("# correct arrows = " + correctArrows);
        out.println("# total estimated arrows = " + totalEstimatedArrows);
        out.println("# correct arrow nonancestor relationships = " + correctNonAncestorRelationships);
        out.println("# total true arrows = " + totalTrueArrows);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctArrows / (double) totalEstimatedArrows;
        out.println("Arrow precision = " + nf.format(precision));
        final double recall = correctArrows / (double) totalTrueArrows;
        out.println("Arrow recall = " + nf.format(recall));
        final double proportionCorrectNonAncestorRelationships = correctNonAncestorRelationships /
                (double) totalEstimatedArrows;
        out.println("Proportion correct arrow nonancestor relationships " + nf.format(proportionCorrectNonAncestorRelationships));

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

            final Edge edge1 = truePag.getEdge(x, y);

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

        out.println();
        out.println("# correct tails = " + correctTails);
        out.println("# total estimated tails = " + totalEstimatedTails);
        out.println("# correct tail ancestor relationships = " + correctAncestorRelationships);
        out.println("# total true tails = " + totalTrueTails);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctTails / (double) totalEstimatedTails;
        out.println("Tail precision = " + nf.format(precision));
        final double recall = correctTails / (double) totalTrueTails;
        out.println("Tail recall = " + nf.format(recall));
        final double proportionCorrectAncestorRelationships = correctAncestorRelationships /
                (double) totalEstimatedTails;
        out.println("Proportion correct tail ancestor relationships " + nf.format(proportionCorrectAncestorRelationships));

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

                int index1 = getIndex(endpoint1);
                int index2 = getIndex(endpoint2);

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
                if (i == 3 && j == 3) table2.setToken(i + 1, j + 1, "");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();

        //        println("\n" + name);
        //        println(table2.toString());
        //        println("");
    }


    private static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
    }

    private Graph getLatentGraph(List<Node> vars, double edgesPerNode, int numLatents) {
        final int numEdges = (int) (vars.size() * edgesPerNode);

        Graph dag = GraphUtils.randomGraph(vars,
                numLatents, numEdges, 3, 3, 3, false);

        return dag;
    }

    public static void main(String... args) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);
        System.out.println("Start ");

        PerformanceTests performanceTests = new PerformanceTests();

        if (args.length == 4) {
            switch (args[0]) {
                case "FGS":
                    final int numVars = Integer.parseInt(args[1]);
                    final double edgeFactor = Double.parseDouble(args[2]);
                    final int numCases = Integer.parseInt(args[3]);
                    performanceTests.testFgs(numVars, edgeFactor, numCases, 2.0);
                    break;
                case "FGSDiscrete":
                    String path = args[1];
                    final double structurePrior = Double.parseDouble(args[2]);
                    final double samplePrior = Double.parseDouble(args[3]);
                    performanceTests.testFgsDiscrete(path, structurePrior, samplePrior);
                    break;
                default:
                    throw new IllegalArgumentException("Not a configuration!");
            }
        } else if (args.length == 5) {
            switch (args[0]) {
                case "PC": {
                    final int numVars = Integer.parseInt(args[1]);
                    final double edgeFactor = Double.parseDouble(args[2]);
                    final int numCases = Integer.parseInt(args[3]);
                    final double alpha = Double.parseDouble(args[4]);
                    performanceTests.testPc(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "PCSTABLE": {
                    final int numVars = Integer.parseInt(args[1]);
                    final double edgeFactor = Double.parseDouble(args[2]);
                    final int numCases = Integer.parseInt(args[3]);
                    final double alpha = Double.parseDouble(args[4]);
                    performanceTests.testPcStable(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "CPCSTABLE": {
                    final int numVars = Integer.parseInt(args[1]);
                    final double edgeFactor = Double.parseDouble(args[2]);
                    final int numCases = Integer.parseInt(args[3]);
                    final double alpha = Double.parseDouble(args[4]);
                    performanceTests.testCpcStable(numVars, edgeFactor, numCases, alpha);
                    break;
                }
                case "FGS": {
                    final int numVars = Integer.parseInt(args[1]);
                    final double edgeFactor = Double.parseDouble(args[2]);
                    final int numCases = Integer.parseInt(args[3]);
                    final double penaltyDiscount = Double.parseDouble(args[4]);

                    performanceTests.testFgs(numVars, edgeFactor, numCases, penaltyDiscount);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Not a configuration: ");
            }
        } else if (args.length == 6) {
            if (args[0].equals("FGSDiscrete")) {
                final int numVars = Integer.parseInt(args[1]);
                final double edgeFactor = Double.parseDouble(args[2]);
                final int numCases = Integer.parseInt(args[3]);
                final double structurePrior = Double.parseDouble(args[4]);
                final double samplePrior = Double.parseDouble(args[5]);

                performanceTests.testFgsDiscrete(numVars, edgeFactor, numCases, structurePrior, samplePrior);
            } else {
                throw new IllegalArgumentException("Not a configuration!");
            }
        }
//        else {
//            new PerformanceTests().printStuffForKlea();
//            throw new IllegalArgumentException("Not a configuration!");
//        }

        System.out.println("Finish");
    }
}

