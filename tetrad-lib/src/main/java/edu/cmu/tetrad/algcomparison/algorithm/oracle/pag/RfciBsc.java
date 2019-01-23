/**
 * 
 */
package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.util.Parameters;

/**
 * Jan 4, 2019 4:32:05 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RFCI-BSC",
        command = "rfci-bsc",
        algoType = AlgType.forbid_latent_common_causes
)
public class RfciBsc implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test = new ProbabilisticTest();
    private IKnowledge knowledge = new Knowledge2();

	@Override
	public IKnowledge getKnowledge() {
        return knowledge;
	}

	@Override
	public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
	}

	@Override
	public Graph search(DataModel dataSet, Parameters parameters) {
		edu.cmu.tetrad.search.Rfci search = new edu.cmu.tetrad.search.Rfci(test.getTest(dataSet, parameters));
		search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
        search.setMaxPathLength(parameters.getInt("maxPathLength"));
        search.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed"));
        search.setVerbose(parameters.getBoolean("verbose"));
        
        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc RfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(search);
        RfciBsc.setNumRandomizedSearchModels(parameters.getInt("numRandomizedSearchModels"));
        RfciBsc.setThresholdNoRandomDataSearch(parameters.getBoolean("thresholdNoRandomDataSearch"));
        RfciBsc.setCutoffDataSearch(parameters.getDouble("cutoffDataSearch"));
        
        RfciBsc.setNumBscBootstrapSamples(parameters.getInt("numBscBootstrapSamples"));
        RfciBsc.setThresholdNoRandomConstrainSearch(parameters.getBoolean("thresholdNoRandomConstrainSearch"));
        RfciBsc.setCutoffConstrainSearch(parameters.getDouble("cutoffConstrainSearch"));
        
        RfciBsc.setLowerBound(parameters.getDouble("lowerBound"));
        RfciBsc.setUpperBound(parameters.getDouble("upperBound"));
        RfciBsc.setOutputRBD(parameters.getBoolean("outputRBD"));
        RfciBsc.setVerbose(parameters.getBoolean("verbose"));
		return RfciBsc.search();
	}

	@Override
	public Graph getComparisonGraph(Graph graph) {
		return new DagToPag(new EdgeListGraph(graph)).convert();
	}

	@Override
	public String getDescription() {
		return "RFCI-BSC using " + test.getDescription();
	}

	@Override
	public DataType getDataType() {
		return DataType.Discrete;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.algcomparison.algorithm.Algorithm#getParameters()
	 */
	@Override
	public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        // RFCI
        parameters.add("depth");
        parameters.add("maxPathLength");
        parameters.add("completeRuleSetUsed");
        parameters.add("verbose");
        // RFCI-BSC
        parameters.add("numRandomizedSearchModels");
        parameters.add("thresholdNoRandomDataSearch");
        parameters.add("cutoffDataSearch");
		parameters.add("numBscBootstrapSamples");
		parameters.add("thresholdNoRandomConstrainSearch");
		parameters.add("cutoffConstrainSearch");
		parameters.add("lowerBound");
        parameters.add("upperBound");
        parameters.add("outputRBD");
		return parameters;
	}

}
