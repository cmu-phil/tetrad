/**
 * 
 */
package edu.pitt.dbmi.algo.sampling;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * Sep 7, 2018 1:38:50 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class GeneralSamplingSearch {
	
	private Algorithm algorithm = null;
	
	private MultiDataSetAlgorithm multiDataSetAlgorithm = null;
	
	private int sampleSize = 0;
	
	private boolean samplingWithReplacement = true;

	private int numberSampling = 0;
	
	private boolean runParallel = false;
	
	private boolean verbose = false;
	
	private List<Graph> PAGs = new ArrayList<>();
	
	//private ForkJoinPool pool = null;
	
	private final ExecutorService pool;
	
	private DataSet data = null;
	
	private List<DataSet> dataSets = null;
	
	/**
	 * Specification of forbidden and required edges.
	 */
	private IKnowledge knowledge = new Knowledge2();

	private PrintStream out = System.out;

	private Parameters parameters;

	/**
	 * An initial graph to start from.
	 */
	private Graph initialGraph = null;

	public GeneralSamplingSearch(DataSet data) {
		this.data = data;
		//pool = ForkJoinPoolInstance.getInstance().getPool();
		pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public GeneralSamplingSearch(List<DataSet> dataSets) {
		this.dataSets = dataSets;
		//pool = ForkJoinPoolInstance.getInstance().getPool();
		pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public void addPAG(Graph pag) {
		PAGs.add(pag);
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
		this.multiDataSetAlgorithm = null;
	}

	public void setMultiDataSetAlgorithm(MultiDataSetAlgorithm multiDataSetAlgorithm) {
		this.multiDataSetAlgorithm = multiDataSetAlgorithm;
		this.algorithm = null;
	}

	public void setSampleSize(int sampleSize) {
		this.sampleSize = sampleSize;
	}

	public void setSamplingWithReplacement(boolean samplingWithReplacement) {
		this.samplingWithReplacement = samplingWithReplacement;
	}

	public void setNumberSampling(int numberSampling) {
		this.numberSampling = numberSampling;
	}

	public void setRunParallel(boolean runParallel) {
		this.runParallel = runParallel;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setData(DataSet data) {
		this.data = data;
	}

	public void setDataSets(List<DataSet> dataSets) {
		this.dataSets = dataSets;
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

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public List<Graph> search() {
		return null;
	}

}
