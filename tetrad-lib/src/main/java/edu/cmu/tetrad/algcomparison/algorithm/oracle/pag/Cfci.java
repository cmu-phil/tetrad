package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.subsampling.GeneralSubSamplingTest;
import edu.pitt.dbmi.algo.subsampling.SubSamplingEdgeEnsemble;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import java.util.List;

/**
 * Conserative FCI.
 *
 * @author jdramsey
 */
public class Cfci implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public Cfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("numberSubSampling") < 1) {
            edu.cmu.tetrad.search.Cfci search = new edu.cmu.tetrad.search.Cfci(test.getTest(dataSet, parameters));
            search.setKnowledge(knowledge);
            search.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed"));
            search.setDepth(parameters.getInt("depth"));
            return search.search();
    	}else{
    		Cfci algorithm = new Cfci(test);
    		
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
        return new DagToPag(new EdgeListGraph(graph)).convert();
    }

    @Override
    public String getDescription() {
        return "CFCI (Conservative Fast Causal Inference), using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("completeRuleSetUsed");
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
