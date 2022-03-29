/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.independence.BDeuTest;
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Aug 17, 2017 2:28:48 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class TestGeneralResamplingTest {

    /**
     * @param args
     */
    public static void main(final String[] args) {
        //testFGESc();
        //testFGESd();
        //testGFCIc();
        //testGFCId();
        //testFCIc();
        //testFCId();
    }

    @Test
    public void testFGESc() {
        final int penaltyDiscount = 2;
        final boolean faithfulnessAssumed = false;
        final int maxDegree = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 0;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;

        final Graph dag = TestGeneralResamplingTest.makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

        System.out.println("Truth Graph:");
        System.out.println(dag);

        final int[] causalOrdering = new int[numVars];

        for (int i = 0; i < numVars; i++) {
            causalOrdering[i] = i;
        }

        final BayesPm pm = new BayesPm(dag);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        final DataSet data = im.simulateData(1000, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, penaltyDiscount);
        parameters.set(Params.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(Params.MAX_DEGREE, maxDegree);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final ScoreWrapper score = new BdeuScore();
        final Algorithm algorithm = new Fges(score);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(false);
        bootstrapTest.setPercentResampleSize(80.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Highest);
        final Graph resultGraph = bootstrapTest.search();
//		System.out.println("Estimated Graph:");
//		System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(dag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(dag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    @Ignore
    @Test
    public void testFGESd() {
        final double structurePrior = 1;
        final double samplePrior = 1;
        final boolean faithfulnessAssumed = false;
        final int maxDegree = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 0;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;
        final long seed = 123;

        final Graph dag = TestGeneralResamplingTest.makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

        //System.out.println("Truth Graph:");
        //System.out.println(dag.toString());

        final BayesPm pm = new BayesPm(dag, 2, 3);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        final DataSet data = im.simulateData(numCases, seed, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.STRUCTURE_PRIOR, structurePrior);
        parameters.set(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE, samplePrior);
        parameters.set(Params.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(Params.MAX_DEGREE, maxDegree);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final ScoreWrapper score = new BdeuScore();
        final Algorithm algorithm = new Fges(score);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(true);
        bootstrapTest.setPercentResampleSize(100.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Highest);
        final Graph resultGraph = bootstrapTest.search();
        //System.out.println("Estimated Graph:");
        //System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(dag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(dag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    @Test
    public void testGFCIc() {
        final int penaltyDiscount = 2;
        final boolean faithfulnessAssumed = false;
        final int maxDegree = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 2;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;

        final Graph dag = TestGeneralResamplingTest.makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

        final BayesPm pm = new BayesPm(dag, 2, 3);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        final DagToPag2 dagToPag = new DagToPag2(dag);
        final Graph truePag = dagToPag.convert();

        //System.out.println("Truth PAG_of_the_true_DAG Graph:");
        //System.out.println(truePag.toString());

        final int[] causalOrdering = new int[numVars];

        for (int i = 0; i < numVars; i++) {
            causalOrdering[i] = i;
        }

        final DataSet data = im.simulateData(numCases, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, penaltyDiscount);
        parameters.set(Params.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(Params.MAX_DEGREE, maxDegree);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final ScoreWrapper score = new BdeuScore();
        final IndependenceWrapper test = new BDeuTest();
        final Algorithm algorithm = new Gfci(test, score);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(true);
        bootstrapTest.setPercentResampleSize(100.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Highest);
        final Graph resultGraph = bootstrapTest.search();
        //System.out.println("Estimated PAG_of_the_true_DAG Graph:");
        //System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    @Ignore
    @Test
    public void testGFCId() {
        final double structurePrior = 1;
        final double samplePrior = 1;
        final boolean faithfulnessAssumed = false;
        final int maxDegree = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 4;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;
        final long seed = 123;

        final Graph dag = TestGeneralResamplingTest.makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

        final DagToPag2 dagToPag = new DagToPag2(dag);
        final Graph truePag = dagToPag.convert();

        //System.out.println("Truth PAG_of_the_true_DAG Graph:");
        //System.out.println(truePag.toString());

        final BayesPm pm = new BayesPm(dag, 2, 3);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        final DataSet data = im.simulateData(numCases, seed, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.STRUCTURE_PRIOR, structurePrior);
        parameters.set(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE, samplePrior);
        parameters.set(Params.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(Params.MAX_DEGREE, maxDegree);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final ScoreWrapper score = new BdeuScore();
        final IndependenceWrapper test = new ChiSquare();
        final Algorithm algorithm = new Gfci(test, score);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(true);
        bootstrapTest.setPercentResampleSize(100.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Highest);
        final Graph resultGraph = bootstrapTest.search();
        //System.out.println("Estimated Bootstrapped PAG_of_the_true_DAG Graph:");
        //System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    @Ignore
    @Test
    public void testFCIc() {
        final int penaltyDiscount = 2;
        final int depth = 3;
        final int maxPathLength = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 2;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;

        final Graph dag = TestGeneralResamplingTest.makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

        final DagToPag2 dagToPag = new DagToPag2(dag);
        final Graph truePag = dagToPag.convert();

        //System.out.println("Truth PAG_of_the_true_DAG Graph:");
        //System.out.println(truePag.toString());

        final int[] causalOrdering = new int[numVars];

        for (int i = 0; i < numVars; i++) {
            causalOrdering[i] = i;
        }

        final LargeScaleSimulation simulator = new LargeScaleSimulation(dag, dag.getNodes(), causalOrdering);

        final DataSet data = simulator.simulateDataFisher(numCases);

        final Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, penaltyDiscount);
        parameters.set(Params.DEPTH, depth);
        parameters.set(Params.MAX_PATH_LENGTH, maxPathLength);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final IndependenceWrapper test = new FisherZ();
        final Fci algorithm = new Fci(test);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(true);
        bootstrapTest.setPercentResampleSize(100.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Preserved);
        //bootstrapTest.setParallelMode(false);
        final Graph resultGraph = bootstrapTest.search();
        //System.out.println("Estimated PAG_of_the_true_DAG Graph:");
        //System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    @Ignore
    @Test
    public void testFCId() {
        final double structurePrior = 1;
        final double samplePrior = 1;
        final int depth = -1;
        final int maxPathLength = -1;

        final int numVars = 20;
        final int edgesPerNode = 2;
        final int numLatentConfounders = 4;
        final int numCases = 50;
        final int numBootstrapSamples = 5;
        final boolean verbose = true;
        final long seed = 123;

        final Graph dag = TestGeneralResamplingTest.makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

        final DagToPag2 dagToPag = new DagToPag2(dag);
        final Graph truePag = dagToPag.convert();

        //System.out.println("Truth PAG_of_the_true_DAG Graph:");
        //System.out.println(truePag.toString());

        final BayesPm pm = new BayesPm(dag, 2, 3);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        final DataSet data = im.simulateData(numCases, seed, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.STRUCTURE_PRIOR, structurePrior);
        parameters.set(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE, samplePrior);
        parameters.set(Params.DEPTH, depth);
        parameters.set(Params.MAX_PATH_LENGTH, maxPathLength);
        parameters.set("numCPDAGsToStore", 0);
        parameters.set(Params.VERBOSE, verbose);

        final IndependenceWrapper test = new ChiSquare();
        final Algorithm algorithm = new Fci(test);

        final GeneralResamplingTest bootstrapTest = new GeneralResamplingTest(data, algorithm, numBootstrapSamples);
        bootstrapTest.setResamplingWithReplacement(true);
        bootstrapTest.setPercentResampleSize(100.00);
        bootstrapTest.setVerbose(verbose);
        bootstrapTest.setParameters(parameters);
        bootstrapTest.setEdgeEnsemble(ResamplingEdgeEnsemble.Highest);
        final Graph resultGraph = bootstrapTest.search();
        //System.out.println("Estimated Bootstrapped PAG_of_the_true_DAG Graph:");
        //System.out.println(resultGraph.toString());

        // Adjacency Confusion Matrix
        final int[][] adjAr = GeneralResamplingTest.getAdjConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printAdjConfusionMatrix(adjAr);

        // Edge Type Confusion Matrix
        final int[][] edgeAr = GeneralResamplingTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

        TestGeneralResamplingTest.printEdgeTypeConfusionMatrix(edgeAr);
    }

    private static int sum2DArray(final int[][] ar, final int iStart, final int iEnd, final int jStart, final int jEnd) {
        int sum = 0;
        if (iStart == iEnd) {
            if (jStart == jEnd) {
                return ar[iStart][jStart];
            } else if (jStart < jEnd) {
                final int mid = (jStart + jEnd) / 2;
                sum += TestGeneralResamplingTest.sum2DArray(ar, iStart, iEnd, jStart, mid) + TestGeneralResamplingTest.sum2DArray(ar, iStart, iEnd, mid + 1, jEnd);
            }
        } else if (iStart < iEnd) {
            final int mid = (iStart + iEnd) / 2;
            sum += TestGeneralResamplingTest.sum2DArray(ar, iStart, mid, jStart, jEnd) + TestGeneralResamplingTest.sum2DArray(ar, mid + 1, iEnd, jStart, jEnd);
        }
        return sum;
    }

    private static void printEdgeTypeConfusionMatrix(final int[][] edgeAr) {
        final int numEdges = TestGeneralResamplingTest.sum2DArray(edgeAr, 0, edgeAr.length - 1, 0, edgeAr[0].length - 1);

        System.out.println("=================================");
        System.out.println("Edge Orientation Confusion Matrix");
        System.out.println("=================================");
        System.out.println("\t\tEstimated");
        System.out.println("n=" + numEdges + "\t\tnil\t-->\t<--\to->\t<-o\to-o\t<->\t---");
        System.out.println("Truth: nil\t" + edgeAr[0][0] + "\t" + edgeAr[0][1] + "\t" + edgeAr[0][2] + "\t"
                + edgeAr[0][3] + "\t" + edgeAr[0][4] + "\t" + edgeAr[0][5] + "\t" + edgeAr[0][6] + "\t" + edgeAr[0][7]);
        System.out.println("Truth: -->\t" + edgeAr[1][0] + "\t" + edgeAr[1][1] + "\t" + edgeAr[1][2] + "\t"
                + edgeAr[1][3] + "\t" + edgeAr[1][4] + "\t" + edgeAr[1][5] + "\t" + edgeAr[1][6] + "\t" + edgeAr[1][7]);
        System.out.println("Truth: <--\t" + edgeAr[2][0] + "\t" + edgeAr[2][1] + "\t" + edgeAr[2][2] + "\t"
                + edgeAr[2][3] + "\t" + edgeAr[2][4] + "\t" + edgeAr[2][5] + "\t" + edgeAr[2][6] + "\t" + edgeAr[2][7]);
        System.out.println("Truth: o->\t" + edgeAr[3][0] + "\t" + edgeAr[3][1] + "\t" + edgeAr[3][2] + "\t"
                + edgeAr[3][3] + "\t" + edgeAr[3][4] + "\t" + edgeAr[3][5] + "\t" + edgeAr[3][6] + "\t" + edgeAr[3][7]);
        System.out.println("Truth: <-o\t" + edgeAr[4][0] + "\t" + edgeAr[4][1] + "\t" + edgeAr[4][2] + "\t"
                + edgeAr[4][3] + "\t" + edgeAr[4][4] + "\t" + edgeAr[4][5] + "\t" + edgeAr[4][6] + "\t" + edgeAr[4][7]);
        System.out.println("Truth: o-o\t" + edgeAr[5][0] + "\t" + edgeAr[5][1] + "\t" + edgeAr[5][2] + "\t"
                + edgeAr[5][3] + "\t" + edgeAr[5][4] + "\t" + edgeAr[5][5] + "\t" + edgeAr[5][6] + "\t" + edgeAr[5][7]);
        System.out.println("Truth: <->\t" + edgeAr[6][0] + "\t" + edgeAr[6][1] + "\t" + edgeAr[6][2] + "\t"
                + edgeAr[6][3] + "\t" + edgeAr[6][4] + "\t" + edgeAr[6][5] + "\t" + edgeAr[6][6] + "\t" + edgeAr[6][7]);
        System.out.println("Truth: ---\t" + edgeAr[7][0] + "\t" + edgeAr[7][1] + "\t" + edgeAr[7][2] + "\t"
                + edgeAr[7][3] + "\t" + edgeAr[7][4] + "\t" + edgeAr[7][5] + "\t" + edgeAr[7][6] + "\t" + edgeAr[7][7]);
        int numerator = 0;
        for (int i = 0; i < 8; i++) {
            numerator += edgeAr[i][i];
        }
        System.out.println("Accuracy: " + numerator / (double) (numEdges));
        numerator -= edgeAr[0][0];
        int denominator = numEdges;
        for (int i = 0; i < 8; i++) {
            denominator -= edgeAr[i][0];
        }
        System.out.println("Precision: " + numerator / (double) (denominator));
        denominator = numEdges;
        for (int i = 0; i < 8; i++) {
            denominator -= edgeAr[0][i];
        }
        System.out.println("Recall: " + numerator / (double) (denominator));
    }

    private static void printAdjConfusionMatrix(final int[][] adjAr) {
        final int numEdges = TestGeneralResamplingTest.sum2DArray(adjAr, 0, adjAr.length - 1, 0, adjAr[0].length - 1);

        System.out.println("============================");
        System.out.println("Adjacency Confusion Matrix");
        System.out.println("============================");
        System.out.println("\t\tEstimated");
        System.out.println("n=" + numEdges + "\t\tNo\tYes");
        System.out.println("Truth: No\t" + adjAr[0][0] + "\t" + adjAr[0][1]);
        System.out.println("Truth: Yes\t" + adjAr[1][0] + "\t" + adjAr[1][1]);
        System.out.println("Accuracy: " + (adjAr[0][0] + adjAr[1][1]) / (double) (numEdges));
        System.out.println("Precision: " + (adjAr[1][1]) / (double) (adjAr[1][1] + adjAr[0][1]));
        System.out.println("Recall: " + (adjAr[1][1]) / (double) (adjAr[1][1] + adjAr[1][0]));
        System.out.println("============================");
        System.out.println();
    }

    private static Graph makeContinuousDAG(final int numVars, final int numLatentConfounders, final double edgesPerNode) {
        final int numEdges = (int) (numVars * edgesPerNode);

        final List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable(Integer.toString(i)));
        }

        return GraphUtils.randomGraph(vars, numLatentConfounders, numEdges, 30, 15, 15, false);
    }

    private static Graph makeDiscreteDAG(final int numVars, final int numLatentConfounders, final double edgesPerNode) {
        final int numEdges = (int) (numVars * edgesPerNode);

        // System.out.println("Making list of vars");

        final List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new DiscreteVariable(Integer.toString(i)));
        }

        // System.out.println("Making dag");
        return GraphUtils.randomGraph(vars, numLatentConfounders, numEdges, 30, 15, 15, false);
    }

}
