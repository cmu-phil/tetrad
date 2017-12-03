package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

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
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK",
        command = "fask",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Searches single continuous datasets for models with possible cycles and 2-cycles, assuming " +
                "the variables are skewed. Latent common causes are not supported. Uses the Fast Adjacency Search (FAS, that is, " +
                "the adjacency search of the PC algorithms) with the linear, Gaussian BIC score as a test of conditional " +
                "independence. One may adjust sparsity of the graph by adjusting the 'penaltyDiscount' parameter. The " +
                "orientation procedure assumes the variables are skewed. Sensitivity for detection of 2-cycles may be " +
                "adjusted using the 2-cycle alpha parameter."
)
public class Fask implements Algorithm, HasKnowledge, UsesScoreWrapper {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public Fask() {

    }

    public Fask(ScoreWrapper score) {
        this.score = score;
    }

    private Graph getGraph(edu.cmu.tetrad.search.Fask search) {
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
	        edu.cmu.tetrad.search.Fask search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet, score.getScore(dataSet, parameters));
	        search.setDepth(parameters.getInt("depth"));
	        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            search.setExtraEdgeThreshold(parameters.getDouble("extraEdgeThreshold"));
            search.setReverseOrientationsBySkewnessOfVariables(parameters.getBoolean("reverseOrientationsBySkewnessOfVariables"));
	        search.setReverseOrientationsBySignOfCorrelation(parameters.getBoolean("reverseOrientationsBySignOfCorrelation"));
            search.setUseFasAdjacencies(parameters.getBoolean("useFasAdjacencies"));
            search.setUseCorrDiffAdjacencies(parameters.getBoolean("useCorrDiffAdjacencies"));
            search.setAlpha(parameters.getDouble("twoCycleAlpha"));
	        search.setKnowledge(knowledge);
	        return getGraph(search);
		}else{
    		Fask fask = new Fask(score);
    		fask.setKnowledge(knowledge);
    		
    		DataSet data = (DataSet) dataSet;
    		GeneralBootstrapTest search = new GeneralBootstrapTest(data, fask, parameters.getInt("bootstrapSampleSize"));
    		
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
    		search.setParameters(parameters);    		
    		search.setVerbose(parameters.getBoolean("verbose"));
    		return search.search();
		}
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "FASK using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("depth");
//        parameters.add("penaltyDiscount");
        parameters.add("twoCycleAlpha");
        parameters.add("extraEdgeThreshold");
        parameters.add("reverseOrientationsBySkewnessOfVariables");
        parameters.add("reverseOrientationsBySignOfCorrelation");
        parameters.add("useFasAdjacencies");
        parameters.add("useCorrDiffAdjacencies");
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");

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

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
