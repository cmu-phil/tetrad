package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
public class Ccd implements Algorithm {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;

    public Ccd(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            edu.cmu.tetrad.search.Ccd search = new edu.cmu.tetrad.search.Ccd(
                    test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt("depth"));
            search.setApplyR1(parameters.getBoolean("applyR1"));

            return search.search();
    	}else{
    		Ccd algorithm = new Ccd(test);
    		
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
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CCD (Cyclic Causal Discovery using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("applyR1");
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");
        return parameters;
    }
}
