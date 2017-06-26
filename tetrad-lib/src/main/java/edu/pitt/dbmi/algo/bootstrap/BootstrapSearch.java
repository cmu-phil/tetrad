package edu.pitt.dbmi.algo.bootstrap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;

//MP: These libraries are required for multi-threading
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.task.BootstrapDatasetAction;
import edu.pitt.dbmi.algo.bootstrap.task.BootstrapSearchAction;

/**
 * Created by Mahdi Pakdaman Naeini on 1/13/17.
 * 
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 4/5/2017
 * 
 */
public class BootstrapSearch {

    private BootstrapAlgName algName = BootstrapAlgName.RFCI;
    private int numBootstrap = 1;
    private boolean runParallel = false;
    private boolean verbose = false;
    private List<Graph> PAGs = new ArrayList<>();
    private ForkJoinPool pool = null;
    private DataSet data = null;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    private PrintStream out = System.out;

    private final List<DataSet> bootstrapDatasets = Collections
	    .synchronizedList(new ArrayList<DataSet>());

    private Parameters parameters;

    /**
     * An initial graph to start from.
     */
    private Graph initialGraph;

    public BootstrapSearch(DataSet data) {
	this.data = data;
	pool = ForkJoinPoolInstance.getInstance().getPool();
    }

    public void addPAG(Graph pag) {
	PAGs.add(pag);
    }

    public DataSet getBootstrapDataset(int id) {
	return bootstrapDatasets.get(id);
    }

    public void addBootstrapDataset(DataSet dataSet) {
	bootstrapDatasets.add(dataSet);
    }

    public void setAlgorithm(BootstrapAlgName algName) {
	this.algName = algName;
    }

    public void setVerbose(boolean verbose) {
	this.verbose = verbose;
    }

    public void setRunningMode(boolean runParallel) {
	this.runParallel = runParallel;
    }

    public void setNumOfBootstrap(int numBootstrap) {
	this.numBootstrap = numBootstrap;
	bootstrapDatasets.clear();
	BootstrapDatasetAction task = new BootstrapDatasetAction(numBootstrap,
		data, this);
	pool.invoke(task);
	while (!pool.isQuiescent()) {
	    try {
		Thread.sleep(100);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
	out.println("Bootstrapping dataset are all generated.");
    }

    public Parameters getParameters() {
	return parameters;
    }

    public void setParameters(Parameters parameters) {
	this.parameters = parameters;
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
		out.println("Running Bootstraps in Sequential Mode, numBoostrap = "
			+ numBootstrap);
	    }
	    for (int i1 = 0; i1 < this.numBootstrap; i1++) {
		start = System.currentTimeMillis();

		BootstrapSearchAction task = new BootstrapSearchAction(i1, 1,
			algName, parameters, this, verbose);
		task.setKnowledge(knowledge);
		task.compute();

		stop = System.currentTimeMillis();
		if (verbose) {
		    out.println("processing time of bootstrap : "
			    + (stop - start) / 1000.0 + " sec");
		}
	    }
	} else {
	    // Running in the parallel multiThread form
	    if (verbose) {
		out.println("Running Bootstraps in Parallel Mode, numBoostrap = "
			+ numBootstrap);
	    }

	    BootstrapSearchAction task = new BootstrapSearchAction(0,
		    numBootstrap, algName, parameters, this, verbose);
	    if (knowledge != null) {
		task.setKnowledge(knowledge);
	    }

	    pool.invoke(task);
	}
	return PAGs;
    }

}
