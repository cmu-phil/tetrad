package edu.pitt.dbmi.algo.bootstrap.task;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

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
import edu.pitt.dbmi.algo.bootstrap.BootstrapSearch;

/**
 * 
 * Mar 19, 2017 9:45:44 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class BootstrapSearchAction extends RecursiveAction {

    private static final long serialVersionUID = -5781260555185260539L;

    private int dataSetId;

    private int workLoad;

    private BootstrapAlgName algName;

    private Parameters parameters;

    private final BootstrapSearch bootstrapSearch;

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

    public BootstrapSearchAction(int dataSetId, int workLoad,
	    BootstrapAlgName algName, Parameters parameters,
	    BootstrapSearch bootstrapSearch, boolean verbose) {
	this.dataSetId = dataSetId;
	this.workLoad = workLoad;
	this.algName = algName;
	this.parameters = parameters;
	this.bootstrapSearch = bootstrapSearch;
	this.verbose = verbose;
    }

    public Graph learnGraph(DataSet dataSet) {
	Score score = null;
	
	if (dataSet.isContinuous()) {
	    ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(dataSet);
	    SemBicScore semBicScore = new SemBicScore(cov);
	    double penaltyDiscount = parameters.getDouble("penaltyDiscount", 4.0);
	    semBicScore.setPenaltyDiscount(penaltyDiscount);
	    score = semBicScore;
	} else if (dataSet.isDiscrete()) {
	    BDeuScore bDeuScore = new BDeuScore(dataSet);
	    double samplePrior = parameters.getDouble("samplePrior", 1.0);
	    double structurePrior = parameters.getDouble("structurePrior", 1.0);
	    bDeuScore.setSamplePrior(samplePrior);
	    bDeuScore.setStructurePrior(structurePrior);
	    score = bDeuScore;
	}

	IndependenceTest independenceTest = null;
	if (dataSet.isContinuous()) {
	    independenceTest = new IndTestFisherZ(dataSet, parameters.getDouble("alpha", 0.5));
	} else if (dataSet.isDiscrete()) {
	    independenceTest = new IndTestChiSquare(dataSet, parameters.getDouble("alpha", 0.5));
	}

	if (algName == BootstrapAlgName.FGES) {
	    Fges fges = new Fges(score);
	    fges.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed", true));
	    fges.setNumPatternsToStore(parameters.getInt("numPatternsToStore", 0));
	    fges.setSymmetricFirstStep(parameters.getBoolean("symmetricFirstStep"));
	    fges.setParallelism(parameters.getInt("numofthreads", 2));
	    if(initialGraph != null)fges.setInitialGraph(initialGraph);
	    return fges.search();
	} else if (algName == BootstrapAlgName.GFCI) {
	    GFci gFci = new GFci(independenceTest, score);
	    gFci.setMaxDegree(parameters.getInt("maxDegree", 5));
	    gFci.setMaxPathLength(parameters.getInt("maxPathLength", -1));
	    gFci.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed", true));
	    gFci.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed", false));
	    return gFci.search();
	} else if (algName == BootstrapAlgName.RFCI) {
	    Rfci rfci = new Rfci(independenceTest);
	    rfci.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed", true));
	    rfci.setDepth(parameters.getInt("depth", 3));
	    rfci.setMaxPathLength(parameters.getInt("maxPathLength", -1));
	    return rfci.search();
	} else {
	    throw new IllegalArgumentException(
		    "Bootstrap Search does not support the " + algName
			    + " algorithm yet.");
	}
    }

    @Override
    public void compute() {
	if (workLoad < 2) {
	    long start, stop;
	    start = System.currentTimeMillis();
	    if (verbose) {
		out.println("thread started ... ");
	    }

	    DataSet dataSet = bootstrapSearch.getBootstrapDataset(dataSetId);
	    Graph graph = learnGraph(dataSet);

	    stop = System.currentTimeMillis();
	    if (verbose) {
		out
			.println("processing time of bootstrap for thread id : "
				+ dataSetId
				+ " was: "
				+ (stop - start)
				/ 1000.0 + " sec");
	    }
	    bootstrapSearch.addPAG(graph);
	} else {
	    BootstrapSearchAction task1 = new BootstrapSearchAction(dataSetId,
		    workLoad / 2, algName, parameters,
		    bootstrapSearch, verbose);
	    BootstrapSearchAction task2 = new BootstrapSearchAction(dataSetId
		    + workLoad / 2, workLoad - workLoad / 2, algName,
		    parameters, bootstrapSearch, verbose);

	    List<BootstrapSearchAction> tasks = new ArrayList<>();
	    tasks.add(task1);
	    tasks.add(task2);

	    invokeAll(tasks);
	}

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
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
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

}
