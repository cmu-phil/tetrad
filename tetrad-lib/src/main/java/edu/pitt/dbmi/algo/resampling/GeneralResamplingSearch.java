/**
 * 
 */
package edu.pitt.dbmi.algo.resampling;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable;

/**
 * Sep 7, 2018 1:38:50 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class GeneralResamplingSearch {

	private Algorithm algorithm = null;

	private MultiDataSetAlgorithm multiDataSetAlgorithm = null;

	private double percentResampleSize = 100;

	private boolean resamplingWithReplacement = true;

	private int numberResampling = 0;

	private boolean runParallel = false;

	private boolean verbose = false;

	private List<Graph> PAGs = new ArrayList<>();

	// private ForkJoinPool pool = null;

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

	public GeneralResamplingSearch(DataSet data) {
		this.data = data;
		// pool = ForkJoinPoolInstance.getInstance().getPool();
		pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public GeneralResamplingSearch(List<DataSet> dataSets) {
		this.dataSets = dataSets;
		// pool = ForkJoinPoolInstance.getInstance().getPool();
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

	public void setPercentResampleSize(double percentResampleSize) {
		this.percentResampleSize = percentResampleSize;
	}

	public void setResamplingWithReplacement(boolean resamplingWithReplacement) {
		this.resamplingWithReplacement = resamplingWithReplacement;
	}

	public void setNumberResampling(int numberResampling) {
		this.numberResampling = numberResampling;
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
	 * Sets the output stream that output (except for log output) should be sent to.
	 * By detault System.out.
	 */
	public void setOut(PrintStream out) {
		this.out = out;
	}

	/**
	 * @return the output stream that output (except for log output) should be sent
	 *         to.
	 */
	public PrintStream getOut() {
		return out;
	}

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public List<Graph> search() {

		PAGs.clear();
		parameters.set("numberResampling", 0); // This needs to be set to zero to not loop indefinitely

		long start, stop;

		if (!this.runParallel) {
			// Running in the sequential form
			if (verbose) {
				out.println("Running Resamplings in Sequential Mode, numberResampling = " + numberResampling);
			}
			for (int i1 = 0; i1 < this.numberResampling; i1++) {
				start = System.currentTimeMillis();

				GeneralResamplingSearchRunnable task = null;

				// Bootstrapping
				if (resamplingWithReplacement) {
					if (data != null) {
						DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				// Resampling
				} else {
					if (data != null) {
						DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				}

				if (initialGraph != null) {
					task.setInitialGraph(initialGraph);
				}
				task.setKnowledge(knowledge);
				task.run();

				stop = System.currentTimeMillis();
				if (verbose) {
					out.println("processing time of resamplings : " + (stop - start) / 1000.0 + " sec");
				}
			}
		} else {
			// Running in the parallel multiThread form
			if (verbose) {
				out.println("Running Resamplings in Parallel Mode, numberResampling = " + numberResampling);
			}

			for (int i1 = 0; i1 < this.numberResampling; i1++) {

				GeneralResamplingSearchRunnable task = null;

				// Bootstrapping
				if (resamplingWithReplacement) {
					if (data != null) {
						DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				// Resampling
				} else {
					if (data != null) {
						DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				}

				if (initialGraph != null) {
					task.setInitialGraph(initialGraph);
				}
				task.setKnowledge(knowledge);
				pool.submit(task);
			}

			pool.shutdown();

			while (!pool.isTerminated()) {
				try {
					Thread.sleep(1000);
					// out.println("Waiting...");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// out.println("Is terminated: " + pool.isTerminated());
		}

		// If the pool is prematurely terminated, do sequentially
		if (PAGs == null || PAGs.size() == 0) {
			for (int i1 = 0; i1 < this.numberResampling; i1++) {
				start = System.currentTimeMillis();

				GeneralResamplingSearchRunnable task = null;

				// Bootstrapping
				if (resamplingWithReplacement) {
					if (data != null) {
						DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getBootstrapSample(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				// Resampling
				} else {
					if (data != null) {
						DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
						task = new GeneralResamplingSearchRunnable(dataSet, algorithm, parameters, this, verbose);
					} else {
						List<DataModel> dataModels = new ArrayList<>();
						for (DataSet data : dataSets) {
							DataSet dataSet = DataUtils.getResamplingDataset(data, (int)(data.getNumRows()*percentResampleSize/100.0));
							dataModels.add(dataSet);
						}
						task = new GeneralResamplingSearchRunnable(dataModels, multiDataSetAlgorithm, parameters, this,
								verbose);
					}
				}

				if (initialGraph != null) {
					task.setInitialGraph(initialGraph);
				}
				task.setKnowledge(knowledge);
				task.run();

				stop = System.currentTimeMillis();
				if (verbose) {
					out.println("processing time of resamplings : " + (stop - start) / 1000.0 + " sec");
				}
			}
		}

		parameters.set("numberResampling", numberResampling); // This needs to be reset back to the previous value

		return PAGs;
	}

}
