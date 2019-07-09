/**
 * 
 */
package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import java.util.ArrayList;
import java.util.List;

/**
 * Jan 4, 2019 4:32:05 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RFCI-BSC",
        command = "rfci-bsc",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Discrete
)
@Experimental
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
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        
        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc RfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(search);
        RfciBsc.setNumRandomizedSearchModels(parameters.getInt(Params.NUM_RANDOMIZED_SEARCH_MODELS));
        RfciBsc.setThresholdNoRandomDataSearch(parameters.getBoolean(Params.THRESHOLD_NO_RANDOM_DATA_SEARCH));
        RfciBsc.setCutoffDataSearch(parameters.getDouble(Params.CUTOFF_DATA_SEARCH));
        
        RfciBsc.setNumBscBootstrapSamples(parameters.getInt(Params.NUM_BSC_BOOTSTRAP_SAMPLES));
        RfciBsc.setThresholdNoRandomConstrainSearch(parameters.getBoolean(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH));
        RfciBsc.setCutoffConstrainSearch(parameters.getDouble(Params.CUTOFF_CONSTRAIN_SEARCH));
        
        RfciBsc.setLowerBound(parameters.getDouble(Params.LOWER_BOUND));
        RfciBsc.setUpperBound(parameters.getDouble(Params.UPPER_BOUND));
        RfciBsc.setOutputRBD(parameters.getBoolean(Params.OUTPUT_RBD));
        RfciBsc.setVerbose(parameters.getBoolean(Params.VERBOSE));
		return RfciBsc.search();
	}

	@Override
	public Graph getComparisonGraph(Graph graph) {
		return new DagToPag2(new EdgeListGraph(graph)).convert();
	}

	@Override
	public String getDescription() {
		return "RFCI-BSC using " + test.getDescription();
	}

	@Override
	public DataType getDataType() {
		return DataType.Discrete;
	}

	@Override
	public List<String> getParameters() {
            List<String> parameters = new ArrayList<>();
            // RFCI
            parameters.add(Params.DEPTH);
            parameters.add(Params.MAX_PATH_LENGTH);
            parameters.add(Params.COMPLETE_RULE_SET_USED);
            // RFCI-BSC
            parameters.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
            parameters.add(Params.THRESHOLD_NO_RANDOM_DATA_SEARCH);
            parameters.add(Params.CUTOFF_DATA_SEARCH);
            parameters.add(Params.NUM_BSC_BOOTSTRAP_SAMPLES);
            parameters.add(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH);
            parameters.add(Params.CUTOFF_CONSTRAIN_SEARCH);
            parameters.add(Params.LOWER_BOUND);
            parameters.add(Params.UPPER_BOUND);
            parameters.add(Params.OUTPUT_RBD);

            parameters.add(Params.VERBOSE);

            return parameters;
	}

}
