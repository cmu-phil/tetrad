package edu.cmu.tetrad.algcomparison.algorithm.bootstrap;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapAlgName;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;

import java.io.PrintStream;
import java.util.List;

/**
 * 
 * Apr 24, 2017 9:45:44 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class BootstrapFges implements Algorithm, TakesInitialGraph,
	HasKnowledge {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public BootstrapFges(ScoreWrapper score) {
	this.score = score;
    }

    public BootstrapFges(ScoreWrapper score, Algorithm initialGraph) {
	this.score = score;
	this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
	Graph initial = null;

	if (initialGraph != null) {
	    initial = initialGraph.search(dataSet, parameters);
	}

	if (dataSet == null || !(dataSet instanceof DataSet)) {
	    throw new IllegalArgumentException(
		    "Sorry, I was expecting a (tabular) data set.");
	}
	DataSet data = (DataSet) dataSet;
	edu.pitt.dbmi.algo.bootstrap.BootstrapTest search = new edu.pitt.dbmi.algo.bootstrap.BootstrapTest(
		data, BootstrapAlgName.FGES);
	search.setParameters(parameters);
	search.setKnowledge(knowledge);
	search.setVerbose(parameters.getBoolean("verbose"));
	search.setNumBootstrapSamples(parameters.getInt("bootstrapSampleSize"));

	BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
	switch (parameters.getInt("bootstrapEnsemble", 1)) {
	case 0:
	    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
	    break;
	case 1:
	    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
	    break;
	case 2:
	    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
	}
	search.setEdgeEnsemble(edgeEnsemble);

	Object obj = parameters.get("printStedu.cmream");
	if (obj instanceof PrintStream) {
	    search.setOut((PrintStream) obj);
	}

	if (initial != null) {
	    search.setInitialGraph(initial);
	}

	return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
	// return new EdgeListGraph(graph);
	return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
	return "Bootstrapping FGES (Fast Greedy Equivalence Search) using "
		+ score.getDescription();
    }

    @Override
    public DataType getDataType() {
	return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
	List<String> parameters = score.getParameters();
	parameters.add("symmetricFirstStep");
	parameters.add("faithfulnessAssumed");
	parameters.add("maxDegree");
	parameters.add("verbose");
	parameters.add("bootstrapSampleSize");
	parameters.add("bootstrapEnsemble");
	return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
	return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
	this.knowledge = knowledge;
    }

}
