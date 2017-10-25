package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
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
public class Fask implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private boolean empirical = false;
    private IKnowledge knowledge = new Knowledge2();

    public Fask() {
        this.empirical = false;
    }

    public Fask(boolean empirical) {
        this.empirical = empirical;
    }

    private Graph getGraph(edu.cmu.tetrad.search.Fask search) {
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
	        edu.cmu.tetrad.search.Fask search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet);
	        search.setDepth(parameters.getInt("depth"));
	        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
	        search.setAlpha(parameters.getDouble("twoCycleAlpha"));
	        search.setKnowledge(knowledge);
	        return getGraph(search);
		}else{
    		Fask fask = new Fask(empirical);
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
        return "FASK";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("depth");
        parameters.add("penaltyDiscount");
        parameters.add("twoCycleAlpha");
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
}
