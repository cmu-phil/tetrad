package edu.pitt.dbmi.algo.resampling.task;

import java.io.PrintStream;
import java.util.List;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch;

/**
 * 
 * Mar 19, 2017 9:45:44 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class GeneralResamplingSearchRunnable implements Runnable {

	private DataSet dataSet = null;

	private List<DataModel> dataSets = null;

	private Algorithm algorithm = null;
	
	private MultiDataSetAlgorithm multiDataSetAlgorithm = null;
	
	private Parameters parameters;

	private final GeneralResamplingSearch resamplingAlgorithmSearch;

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

	public GeneralResamplingSearchRunnable(DataSet dataSet, Algorithm algorithm, Parameters parameters,
			GeneralResamplingSearch resamplingAlgorithmSearch, boolean verbose){
		this.dataSet = dataSet;
		this.algorithm = algorithm;
		this.parameters = parameters;
		this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
		this.verbose = verbose;
	}
	
	public GeneralResamplingSearchRunnable(List<DataModel> dataSets, MultiDataSetAlgorithm multiDataSetAlgorithm, Parameters parameters,
			GeneralResamplingSearch resamplingAlgorithmSearch, boolean verbose){
		this.dataSets = dataSets;
		this.multiDataSetAlgorithm = multiDataSetAlgorithm;
		this.parameters = parameters;
		this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
		this.verbose = verbose;
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
		//System.out.println("#dataSet rows: " + dataSet.getNumRows());
		
		long start, stop;
		start = System.currentTimeMillis();
		if (verbose) {
			out.println("thread started ... ");
		}

		Graph graph = null;
		
		if(dataSet != null){
			if (algorithm instanceof HasKnowledge) {
                ((HasKnowledge) algorithm).setKnowledge(knowledge);
        		if (verbose) {
        			out.println("knowledge being set ... ");
        		}
            }
			graph = algorithm.search(dataSet, parameters);
		}else{
			if (multiDataSetAlgorithm instanceof HasKnowledge) {
                ((HasKnowledge) multiDataSetAlgorithm).setKnowledge(knowledge);
        		if (verbose) {
        			out.println("knowledge being set ... ");
        		}
            }
			graph = multiDataSetAlgorithm.search(dataSets, parameters);
		}

		graph.getEdges();
		
		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("processing time of resampling for a thread was: "
					+ (stop - start) / 1000.0 + " sec");
		}
		resamplingAlgorithmSearch.addPAG(graph);
	}

}
