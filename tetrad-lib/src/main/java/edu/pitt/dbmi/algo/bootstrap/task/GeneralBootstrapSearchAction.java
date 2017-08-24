package edu.pitt.dbmi.algo.bootstrap.task;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapSearch;

/**
 * 
 * Aug 24, 2017 12:15:15 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class GeneralBootstrapSearchAction extends RecursiveAction {

	private static final long serialVersionUID = 71691278579539851L;

	private int dataSetId;

	private int workLoad;

	private Algorithm algorithm;

	private Parameters parameters;

	private final GeneralBootstrapSearch generalBootstrapSearch;

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

	public GeneralBootstrapSearchAction(int dataSetId, int workLoad, Algorithm algorithm, Parameters parameters,
			GeneralBootstrapSearch generalBootstrapSearch, boolean verbose) {
		this.dataSetId = dataSetId;
		this.workLoad = workLoad;
		this.algorithm = algorithm;
		this.parameters = parameters;
		this.generalBootstrapSearch = generalBootstrapSearch;
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
	public void compute() {
		if (workLoad < 2) {
			long start, stop;
			start = System.currentTimeMillis();
			if (verbose) {
				out.println("thread started ... ");
			}
			DataSet data = generalBootstrapSearch.getData();
			DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows());
			
			Graph graph = algorithm.search(dataSet, parameters);

			stop = System.currentTimeMillis();
			if (verbose) {
				out.println("processing time of bootstrap for thread id : " + dataSetId + " was: "
						+ (stop - start) / 1000.0 + " sec");
			}

			generalBootstrapSearch.addPAG(graph);
		} else {
			GeneralBootstrapSearchAction task1 = new GeneralBootstrapSearchAction(dataSetId, workLoad / 2, algorithm,
					parameters, generalBootstrapSearch, verbose);
			GeneralBootstrapSearchAction task2 = new GeneralBootstrapSearchAction(dataSetId + workLoad / 2,
					workLoad - workLoad / 2, algorithm, parameters, generalBootstrapSearch, verbose);

			List<GeneralBootstrapSearchAction> tasks = new ArrayList<>();
			tasks.add(task1);
			tasks.add(task2);

			invokeAll(tasks);
		}

	}

}
