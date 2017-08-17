package edu.pitt.dbmi.algo.bootstrap;

import java.io.PrintStream;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;

//MP: These libraries are required for multi-threading
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;
//import edu.pitt.dbmi.algo.bootstrap.task.BootstrapDatasetAction;
import edu.pitt.dbmi.algo.bootstrap.task.BootstrapSearchAction;
import edu.pitt.dbmi.algo.bootstrap.task.BootstrapSearchRunnable;

/**
 * Chirayu Kong Wongchokprasitti, PhD
 * 
 */
public class GenericBootstrapSearch {

	private Algorithm algorithm;
	private int numBootstrap = 1;
	private boolean runParallel = false;
	private boolean verbose = false;
	private List<Graph> PAGs = new ArrayList<>();
	private final ExecutorService pool;
	private DataSet data = null;

	/**
	 * Specification of forbidden and required edges.
	 */
	private IKnowledge knowledge = new Knowledge2();

	private PrintStream out = System.out;

	//private final List<DataSet> bootstrapDatasets = Collections.synchronizedList(new ArrayList<DataSet>());

	private Parameters parameters;

	/**
	 * An initial graph to start from.
	 */
	private Graph initialGraph;

	public GenericBootstrapSearch(DataSet data) {
		this.data = data;
		pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public void addPAG(Graph pag) {
		PAGs.add(pag);
	}

	/*public DataSet getBootstrapDataset(int id) {
		return bootstrapDatasets.get(id);
	}

	public void addBootstrapDataset(DataSet dataSet) {
		bootstrapDatasets.add(dataSet);
	}*/

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setRunningMode(boolean runParallel) {
		this.runParallel = runParallel;
	}

	public void setNumOfBootstrap(int numBootstrap) {
		this.numBootstrap = numBootstrap;
	}

	public Parameters getParameters() {
		return parameters;
	}

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public DataSet getData() {
		return data;
	}

	public void setData(DataSet data) {
		this.data = data;
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

	public List<Graph> search() {

		PAGs.clear();

		long start, stop;
		if (!this.runParallel) {
			// Running in the sequential form
			if (verbose) {
				out.println("Running Bootstraps in Sequential Mode, numBoostrap = " + numBootstrap);
			}
			for (int i1 = 0; i1 < this.numBootstrap; i1++) {
				start = System.currentTimeMillis();

	            DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows()); 
				BootstrapSearchRunnable task = new BootstrapSearchRunnable(dataSet, algorithm, parameters, this, verbose);
				task.setInitialGraph(initialGraph);
				task.setKnowledge(knowledge);
				task.run();

				stop = System.currentTimeMillis();
				if (verbose) {
					out.println("processing time of bootstrap : " + (stop - start) / 1000.0 + " sec");
				}
			}
		} else {
			// Running in the parallel multiThread form
			if (verbose) {
				out.println("Running Bootstraps in Parallel Mode, numBoostrap = " + numBootstrap);
			}

			for (int i1 = 0; i1 < this.numBootstrap; i1++) {
				DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows()); 
				BootstrapSearchRunnable task = new BootstrapSearchRunnable(dataSet, algorithm, parameters, this, verbose);
				task.setInitialGraph(initialGraph);
				task.setKnowledge(knowledge);
				pool.submit(task);
			}
			
			pool.shutdown();
			
			if(!pool.isTerminated()){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		return PAGs;
	}

}
