package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.util.ArrayList;
import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
@Bootstrapping
public class Pcd implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public Pcd() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            IndTestScore test;

            if (dataSet instanceof ICovarianceMatrix) {
                SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataSet);
                score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
                score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
                test = new IndTestScore(score);
            } else if (dataSet instanceof DataSet) {
                SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
                score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
                score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
                test = new IndTestScore(score);
            } else {
                throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
            }

            edu.cmu.tetrad.search.Pcd search = new edu.cmu.tetrad.search.Pcd(test);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
    	}else{
    		Pcd algorithm = new Pcd();
   		
 			DataSet data = (DataSet) dataSet;
			GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
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
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\") Deternimistic";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.DEPTH);
        parameters.add(Params.DETERMINISM_THRESHOLD);

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
