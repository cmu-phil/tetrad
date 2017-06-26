package edu.cmu.tetrad.algcomparison.algorithm.bootstrap;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.pitt.dbmi.algo.bootstrap.BootstrapAlgName;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;

import java.util.List;

/**
 * 
 * Apr 28, 2017 12:25:44 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class BootstrapRfci implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public BootstrapRfci(IndependenceWrapper test) {
	this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
	if (dataSet == null || !(dataSet instanceof DataSet)) {
	    throw new IllegalArgumentException(
		    "Sorry, I was expecting a (tabular) data set.");
	}
	DataSet data = (DataSet) dataSet;
	edu.pitt.dbmi.algo.bootstrap.BootstrapTest search = new edu.pitt.dbmi.algo.bootstrap.BootstrapTest(
		data, BootstrapAlgName.RFCI);
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

	return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
	return new DagToPag(new EdgeListGraph(graph)).convert();
    }

    public String getDescription() {
	return "Bootstrapping RFCI (Really Fast Causal Inference) using "
		+ test.getDescription();
    }

    @Override
    public DataType getDataType() {
	return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
	List<String> parameters = test.getParameters();
	parameters.add("depth");
	parameters.add("maxPathLength");
	parameters.add("completeRuleSetUsed");
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
