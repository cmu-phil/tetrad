package edu.pitt.dbmi.algo.bootstrap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;

//MP: These libraries are required for multi-threading
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.task.GeneralBootstrapSearchAction;
import edu.pitt.dbmi.algo.bootstrap.task.GeneralBootstrapSearchRunnable;

/**
 * Chirayu Kong Wongchokprasitti, PhD
 * 
 */
public class GeneralBootstrapSearch {

	private Algorithm algorithm = null;
	
	private MultiDataSetAlgorithm multiDataSetAlgorithm = null;
	
	private int numBootstrap = 1;
	
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

	public GeneralBootstrapSearch(DataSet data) {
		this.data = data;
		//pool = ForkJoinPoolInstance.getInstance().getPool();
		pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public GeneralBootstrapSearch(List<DataSet> dataSets) {
		this.dataSets = dataSets;
		//pool = ForkJoinPoolInstance.getInstance().getPool();
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
		this.multiDataSetAlgorithm = null;
	}

	public void setMultiDataSetAlgorithm(MultiDataSetAlgorithm multiDataSetAlgorithm) {
		this.multiDataSetAlgorithm = multiDataSetAlgorithm;
		this.algorithm = null;
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

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public void setData(DataSet data) {
		this.data = data;
	}

	public DataSet getData() {
		return data;
	}

	public void setDatasets(List<DataSet> dataSets) {
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

	public List<Graph> search() {
		
		PAGs.clear();
		parameters.set("bootstrapSampleSize", 0); // This needs to be set to zero to not loop indefinitely
		
		long start, stop;
		
		if (!this.runParallel) {
			// Running in the sequential form
			if (verbose) {
				out.println("Running Bootstraps in Sequential Mode, numBoostrap = " + numBootstrap);
			}
			for (int i1 = 0; i1 < this.numBootstrap; i1++) {
				start = System.currentTimeMillis();

				GeneralBootstrapSearchRunnable task = null;
				
				if(data != null){
					DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows()); 
					task = new GeneralBootstrapSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					//GeneralBootstrapSearchAction task = new GeneralBootstrapSearchAction(i1, 1, algorithm, parameters, this, verbose);
				}else{
					List<DataModel> dataModels = new ArrayList<>();
					for(DataSet data : dataSets){
						DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows());
						dataModels.add(dataSet);
					}
					
					task = new GeneralBootstrapSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this, verbose);
				}
	            
				if(initialGraph != null){
					task.setInitialGraph(initialGraph);
				}
				task.setKnowledge(knowledge);
				task.run();
				//task.compute();
				
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

			//GeneralBootstrapSearchAction task = new GeneralBootstrapSearchAction(0, numBootstrap, algorithm, parameters, this, verbose);
			//task.setKnowledge(knowledge);
			//pool.invoke(task);
			
			for (int i1 = 0; i1 < this.numBootstrap; i1++) {
				
				GeneralBootstrapSearchRunnable task = null;
				
				if(data != null){
					DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows()); 
					task = new GeneralBootstrapSearchRunnable(dataSet, algorithm, parameters, this, verbose);
				}else{
					List<DataModel> dataModels = new ArrayList<>();
					for(DataSet data : dataSets){
						DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows());
						dataModels.add(dataSet);
					}
					
					task = new GeneralBootstrapSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this, verbose);
				}
				task.setKnowledge(knowledge);
				pool.submit(task);
			}
			
			pool.shutdown();
			
			while(!pool.isTerminated()){
				try {
					Thread.sleep(1000);
					//out.println("Waiting...");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			//out.println("Is terminated: " + pool.isTerminated());
		}
		
		// If the pool is prematurely terminated, do sequentially
		if(PAGs == null || PAGs.size() == 0) {
			for (int i1 = 0; i1 < this.numBootstrap; i1++) {
				start = System.currentTimeMillis();

				GeneralBootstrapSearchRunnable task = null;
				
				if(data != null){
					DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows()); 
					task = new GeneralBootstrapSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					//GeneralBootstrapSearchAction task = new GeneralBootstrapSearchAction(i1, 1, algorithm, parameters, this, verbose);
				}else{
					List<DataModel> dataModels = new ArrayList<>();
					for(DataSet data : dataSets){
						DataSet dataSet = DataUtils.getBootstrapSample(data, data.getNumRows());
						dataModels.add(dataSet);
					}
					
					task = new GeneralBootstrapSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this, verbose);
				}
	            
				if(initialGraph != null){
					task.setInitialGraph(initialGraph);
				}
				task.setKnowledge(knowledge);
				task.run();
				//task.compute();
				
				stop = System.currentTimeMillis();
				if (verbose) {
					out.println("processing time of bootstrap : " + (stop - start) / 1000.0 + " sec");
				}
			}
		}
		
		parameters.set("bootstrapping", true);
		parameters.set("bootstrapSampleSize", numBootstrap); // This needs to be reset back to the previous value
		
		return PAGs;
	}

}
