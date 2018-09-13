package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.subsampling.GeneralSubSamplingTest;
import edu.pitt.dbmi.algo.subsampling.SubSamplingEdgeEnsemble;

import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
/*@AlgorithmDescription(
        name = "CCD_MAX",
        algType = AlgType.forbid_latent_common_causes,
        oracleType = OracleType.Test
)*/
public class CcdMax implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public CcdMax(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("numberSubSampling") < 1) {
            IndependenceTest test = this.test.getTest(dataSet, parameters);
            edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
            search.setDoColliderOrientations(parameters.getBoolean("doColliderOrientation"));
            search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
            search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
            search.setKnowledge(knowledge);
            search.setDepth(parameters.getInt("depth"));
            search.setApplyOrientAwayFromCollider(parameters.getBoolean("applyR1"));
            search.setUseOrientTowardDConnections(parameters.getBoolean("orientTowardDConnections"));
            search.setDepth(parameters.getInt("depth"));
            return search.search();
    	}else{
    		CcdMax algorithm = new CcdMax(test);
    		
    		DataSet data = (DataSet) dataSet;
    		GeneralSubSamplingTest search = new GeneralSubSamplingTest(data, algorithm, parameters.getInt("numberSubSampling"));
            search.setKnowledge(knowledge);
    		
            search.setSubSampleSize(parameters.getInt("subSampleSize"));
            search.setSubSamplingWithReplacement(parameters.getBoolean("subSamplingWithReplacement"));
            
            SubSamplingEdgeEnsemble edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("subSamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Majority;
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
        return "CCD-Max (Cyclic Discovery Search Max) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("orientVisibleFeedbackLoops");
        parameters.add("doColliderOrientation");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
        parameters.add("applyR1");
        parameters.add("orientTowardDConnections");
        // Subsampling
        parameters.add("numberSubSampling");
        parameters.add("subSampleSize");
        parameters.add("subSamplingWithReplacement");
        parameters.add("subSamplingEnsemble");
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
}
