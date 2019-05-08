package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
public class FasLofs implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private final Lofs2.Rule rule;
    private IKnowledge knowledge = new Knowledge2();

    public FasLofs(Lofs2.Rule rule) {
        this.rule = rule;
    }

    private Graph getGraph(edu.cmu.tetrad.search.FasLofs search) {
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.FasLofs search = new edu.cmu.tetrad.search.FasLofs((DataSet) dataSet, rule);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            search.setKnowledge(knowledge);
            return getGraph(search);
    	}else{
    		FasLofs fasLofs = new FasLofs(rule);
    		//fasLofs.setKnowledge(knowledge);
    		
    		DataSet data = (DataSet) dataSet;
    		GeneralResamplingTest search = new GeneralResamplingTest(data, fasLofs, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
    		search.setEdgeEnsemble(edgeEnsemble);
    		search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
    		
    		search.setParameters(parameters);    		
    		search.setVerbose(parameters.getBoolean(Params.VERBOSE));
    		return search.search();
    	}
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "FAS followed by " + rule;
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
	parameters.add(Params.PENALTY_DISCOUNT);

        parameters.add(Params.VERBOSE);
        
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
