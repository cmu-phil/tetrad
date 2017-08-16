package edu.pitt.dbmi.algo.bootstrap.task;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapAlgName;
import edu.pitt.dbmi.algo.bootstrap.BootstrapAlgorithm;
import edu.pitt.dbmi.algo.bootstrap.GenericBootstrapSearch;
import edu.pitt.dbmi.algo.bootstrap.BootstrapSearch;

/**
 * 
 * Mar 19, 2017 9:45:44 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class BootstrapSearchRunnable implements Runnable {

	private DataSet dataSet;

	private BootstrapAlgorithm algorithm;
	
	private Parameters parameters;

	private final GenericBootstrapSearch bootstrapAlgorithmSearch;

	private boolean verbose;

	/**
	 * An initial graph to start from.
	 */
	private Graph initialGraph = null;

	/**
	 * Specification of forbidden and required edges.
	 */
	private IKnowledge knowledge = new Knowledge2();

	private PrintStream out = System.out;

	public BootstrapSearchRunnable(DataSet dataSet, BootstrapAlgorithm algorithm, Parameters parameters,
			GenericBootstrapSearch bootstrapAlgorithmSearch, boolean verbose){
		this.dataSet = dataSet;
		this.algorithm = algorithm;
		this.parameters = parameters;
		this.bootstrapAlgorithmSearch = bootstrapAlgorithmSearch;
		this.verbose = verbose;
	}
	
	public Graph learnGraph() {
		return algorithm.search(dataSet, parameters);
	}

	/**
	 * @return the background knowledge.
	 */

	public IKnowledge getKnowledge() {
		return knowledge;
	}

	/**
	 * Sets the background knowledge.
	 *
	 * @param knowledge
	 *            the knowledge object, specifying forbidden and required edges.
	 */
	public void setKnowledge(IKnowledge knowledge) {
		if (knowledge == null)
			throw new NullPointerException();
		this.knowledge = knowledge;
	}

	public Graph getInitialGraph() {
		return initialGraph;
	}

	public void setInitialGraph(Graph initialGraph) {
		this.initialGraph = initialGraph;
	}

	/**
	 * Sets the output stream that output (except for log output) should be sent
	 * to. By detault System.out.
	 */
	public void setOut(PrintStream out) {
		this.out = out;
	}

	/**
	 * @return the output stream that output (except for log output) should be
	 *         sent to.
	 */
	public PrintStream getOut() {
		return out;
	}

	@Override
	public void run() {
		long start, stop;
		start = System.currentTimeMillis();
		if (verbose) {
			out.println("thread started ... ");
		}

		algorithm.setKnowledge(knowledge);
		Graph graph = algorithm.search(dataSet, parameters);

		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("processing time of bootstrap for a thread was: "
					+ (stop - start) / 1000.0 + " sec");
		}
		bootstrapAlgorithmSearch.addPAG(graph);
	}

}
