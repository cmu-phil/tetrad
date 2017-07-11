package edu.cmu.tetrad.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapAlgName;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.BootstrapTest;

/**
 * 
 * Apr 17, 2017 11:13:21 AM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class TestBootstrapTest {

	public static void main(String[] args) {
		// testFGESc();
		// testFGESd();
		// testGFCIc();
		// testGFCId();
		// testRFCIc();
		// testRFCId();
	}

	@Test
	public void testRFCIc() {
		int penaltyDiscount = 2;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 2;
		int numCases = 100;
		int numBootstrapSamples = 10;
		boolean verbose = false;

		Graph dag = makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

		DagToPag dagToPag = new DagToPag(dag);
		Graph truePag = dagToPag.convert();

		System.out.println("Truth PAG_of_the_true_DAG Graph:");
		System.out.println(truePag.toString());

		int[] causalOrdering = new int[numVars];

		for (int i = 0; i < numVars; i++) {
			causalOrdering[i] = i;
		}

		LargeScaleSimulation simulator = new LargeScaleSimulation(dag, dag.getNodes(), causalOrdering);

		DataSet data = simulator.simulateDataFisher(numCases);

		Parameters parameters = new Parameters();
		parameters.set("penaltyDiscount", penaltyDiscount);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.RFCI, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated PAG_of_the_true_DAG Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(truePag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);

	}

	@Test
	public void testRFCId() {
		double structurePrior = 1, samplePrior = 1;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 2;
		int numCases = 100;
		int numBootstrapSamples = 10;
		boolean verbose = false;
		long seed = 123;

		Graph dag = makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

		DagToPag dagToPag = new DagToPag(dag);
		Graph truePag = dagToPag.convert();

		System.out.println("Truth PAG_of_the_true_DAG Graph:");
		System.out.println(truePag.toString());

		BayesPm pm = new BayesPm(dag, 2, 3);
		BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

		DataSet data = im.simulateData(numCases, seed, false);

		Parameters parameters = new Parameters();
		parameters.set("structurePrior", structurePrior);
		parameters.set("samplePrior", samplePrior);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.RFCI, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated PAG_of_the_true_DAG Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(truePag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);
	}

	@Test
	public void testGFCIc() {
		int penaltyDiscount = 2;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 2;
		int numCases = 100;
		int numBootstrapSamples = 10;
		boolean verbose = false;

		Graph dag = makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

		DagToPag dagToPag = new DagToPag(dag);
		Graph truePag = dagToPag.convert();

		System.out.println("Truth PAG_of_the_true_DAG Graph:");
		System.out.println(truePag.toString());

		int[] causalOrdering = new int[numVars];

		for (int i = 0; i < numVars; i++) {
			causalOrdering[i] = i;
		}

		LargeScaleSimulation simulator = new LargeScaleSimulation(dag, dag.getNodes(), causalOrdering);

		DataSet data = simulator.simulateDataFisher(numCases);

		Parameters parameters = new Parameters();
		parameters.set("penaltyDiscount", penaltyDiscount);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.GFCI, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated PAG_of_the_true_DAG Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(truePag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);

	}

	@Test
	public void testGFCId() {
		double structurePrior = 1, samplePrior = 1;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 4;
		int numCases = 100;
		int numBootstrapSamples = 100;
		boolean verbose = false;
		long seed = 123;

		Graph dag = makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

		DagToPag dagToPag = new DagToPag(dag);
		Graph truePag = dagToPag.convert();

		System.out.println("Truth PAG_of_the_true_DAG Graph:");
		System.out.println(truePag.toString());

		BayesPm pm = new BayesPm(dag, 2, 3);
		BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

		DataSet data = im.simulateData(numCases, seed, false);

		Parameters parameters = new Parameters();
		parameters.set("structurePrior", structurePrior);
		parameters.set("samplePrior", samplePrior);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.GFCI, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated Bootstrapped PAG_of_the_true_DAG Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(truePag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);

		BDeuScore bDeuScore = new BDeuScore(data);
		bDeuScore.setSamplePrior(samplePrior);
		bDeuScore.setStructurePrior(structurePrior);
		Score score = bDeuScore;

		IndependenceTest test = new IndTestChiSquare(data, parameters.getDouble("alpha", 0.5));

		GFci gfci = new GFci(test, score);
		resultGraph = gfci.search();
		System.out.println("Estimated PAG_of_the_true_DAG Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		adjAr = BootstrapTest.getAdjConfusionMatrix(truePag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(truePag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);

	}

	@Test
	public void testFGESc() {
		int penaltyDiscount = 2;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 0;
		int numCases = 1000;
		int numBootstrapSamples = 100;
		boolean verbose = true;

		Graph dag = makeContinuousDAG(numVars, numLatentConfounders, edgesPerNode);

		System.out.println("Truth Graph:");
		System.out.println(dag.toString());

		int[] causalOrdering = new int[numVars];

		for (int i = 0; i < numVars; i++) {
			causalOrdering[i] = i;
		}

		LargeScaleSimulation simulator = new LargeScaleSimulation(dag, dag.getNodes(), causalOrdering);

		DataSet data = simulator.simulateDataFisher(numCases);

		Parameters parameters = new Parameters();
		parameters.set("penaltyDiscount", penaltyDiscount);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.FGES, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(dag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(dag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);
	}

	@Test
	public void testFGESd() {
		double structurePrior = 1, samplePrior = 1;
		boolean faithfulnessAssumed = false;
		int maxDegree = -1;

		int numVars = 40;
		int edgesPerNode = 2;
		int numLatentConfounders = 0;
		int numCases = 100;
		int numBootstrapSamples = 10;
		boolean verbose = false;
		long seed = 123;

		Graph dag = makeDiscreteDAG(numVars, numLatentConfounders, edgesPerNode);

		System.out.println("Truth Graph:");
		System.out.println(dag.toString());

		BayesPm pm = new BayesPm(dag, 2, 3);
		BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

		DataSet data = im.simulateData(numCases, seed, false);

		Parameters parameters = new Parameters();
		parameters.set("structurePrior", structurePrior);
		parameters.set("samplePrior", samplePrior);
		parameters.set("faithfulnessAssumed", faithfulnessAssumed);
		parameters.set("maxDegree", maxDegree);
		parameters.set("numPatternsToStore", 0);
		parameters.set("verbose", verbose);

		BootstrapTest bootstrapTest = new BootstrapTest(data, BootstrapAlgName.FGES, numBootstrapSamples);
		bootstrapTest.setVerbose(verbose);
		bootstrapTest.setParameters(parameters);
		bootstrapTest.setEdgeEnsemble(BootstrapEdgeEnsemble.Highest);
		Graph resultGraph = bootstrapTest.search();
		System.out.println("Estimated Graph:");
		System.out.println(resultGraph.toString());

		// Adjacency Confusion Matrix
		int[][] adjAr = BootstrapTest.getAdjConfusionMatrix(dag, resultGraph);

		printAdjConfusionMatrix(adjAr);

		// Edge Type Confusion Matrix
		int[][] edgeAr = BootstrapTest.getEdgeTypeConfusionMatrix(dag, resultGraph);

		printEdgeTypeConfusionMatrix(edgeAr);
	}

	private static int sum2DArray(int[][] ar, int iStart, int iEnd, int jStart, int jEnd) {
		int sum = 0;
		if (iStart == iEnd) {
			if (jStart == jEnd) {
				return ar[iStart][jStart];
			} else if (jStart < jEnd) {
				int mid = (jStart + jEnd) / 2;
				sum += sum2DArray(ar, iStart, iEnd, jStart, mid) + sum2DArray(ar, iStart, iEnd, mid + 1, jEnd);
			}
		} else if (iStart < iEnd) {
			int mid = (iStart + iEnd) / 2;
			sum += sum2DArray(ar, iStart, mid, jStart, jEnd) + sum2DArray(ar, mid + 1, iEnd, jStart, jEnd);
		}
		return sum;
	}

	private static void printEdgeTypeConfusionMatrix(int[][] edgeAr) {
		int numEdges = sum2DArray(edgeAr, 0, edgeAr.length - 1, 0, edgeAr[0].length - 1);

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

	private static void printAdjConfusionMatrix(int[][] adjAr) {
		int numEdges = sum2DArray(adjAr, 0, adjAr.length - 1, 0, adjAr[0].length - 1);

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

	private static Graph makeContinuousDAG(int numVars, int numLatentConfounders, double edgesPerNode) {
		final int numEdges = (int) (numVars * edgesPerNode);

		List<Node> vars = new ArrayList<>();

		for (int i = 0; i < numVars; i++) {
			vars.add(new ContinuousVariable(Integer.toString(i)));
		}

		return GraphUtils.randomGraph(vars, numLatentConfounders, numEdges, 30, 15, 15, false);
	}

	private static Graph makeDiscreteDAG(int numVars, int numLatentConfounders, double edgesPerNode) {
		final int numEdges = (int) (numVars * edgesPerNode);

		// System.out.println("Making list of vars");

		List<Node> vars = new ArrayList<>();

		for (int i = 0; i < numVars; i++) {
			vars.add(new DiscreteVariable(Integer.toString(i)));
		}

		// System.out.println("Making dag");
		return GraphUtils.randomGraph(vars, numLatentConfounders, numEdges, 30, 15, 15, false);
	}

}
