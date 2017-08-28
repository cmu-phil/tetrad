package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class CpcStable implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public CpcStable(IndependenceWrapper type) {
        this.test = type;
    }

    public CpcStable(IndependenceWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if(!parameters.getBoolean("bootstrapping")){
            Graph init =  null;
            if (initialGraph != null) {
                init = initialGraph.search(dataSet, parameters);
            }
            edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters), init);
            search.setDepth(parameters.getInt("depth"));
            search.setKnowledge(knowledge);
            search.setFasRule(PcAll.FasRule.FAS_STABLE);
            search.setColliderDiscovery(edu.cmu.tetrad.search.PcAll.ColliderDiscovery.CONSERVATIVE);
            search.setConflictRule(edu.cmu.tetrad.search.PcAll.ConflictRule.PRIORITY);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
    	}else{
    		CpcStable algorithm = new CpcStable(test);
    		
        	algorithm.setKnowledge(knowledge);
//          if (initialGraph != null) {
//      		algorithm.setInitialGraph(initialGraph);
//  		}

    		DataSet data = (DataSet) dataSet;
    		
    		GeneralBootstrapTest search = new GeneralBootstrapTest(data, algorithm, parameters.getInt("bootstrapSampleSize"));
    		
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
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "CPC-Stable (Conservative \"Peter and Clark\" Stable), Priority Rule, using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        // Bootstrapping
        parameters.add("bootstrapping");
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
