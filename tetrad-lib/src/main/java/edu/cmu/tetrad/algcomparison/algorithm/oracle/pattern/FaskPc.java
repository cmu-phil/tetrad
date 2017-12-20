package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK PC",
        command = "fask-pc",
        algoType = AlgType.forbid_latent_common_causes,
        description = "PC using FASk to create forbidden knowledge."
)
public class FaskPc implements Algorithm, TakesInitialGraph, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public FaskPc(IndependenceWrapper test) {
        this.test = test;
    }

    public FaskPc(IndependenceWrapper test, Algorithm algorithm) {
        this.test = test;
        this.algorithm = algorithm;
    }

    public FaskPc() {
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            edu.cmu.tetrad.search.FaskPc search = new edu.cmu.tetrad.search.FaskPc(test.getTest(dataSet, parameters), (DataSet) dataSet);
            return search.search();
        }else{
    		FaskPc algorithm = new FaskPc(test);

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
        return "PC (\"Peter and Clark\"), Priority Rule, using " + test.getDescription()
                + (algorithm != null ? " with initial graph from " +
                algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("verbose");

        parameters.add("twoCycleAlpha");
        parameters.add("extraEdgeThreshold");
        parameters.add("reverseOrientationsBySkewnessOfVariables");
        parameters.add("reverseOrientationsBySignOfCorrelation");
        parameters.add("useFasAdjacencies");
        parameters.add("useCorrDiffAdjacencies");
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        return parameters;
    }

	@Override
	public Graph getInitialGraph() {
		return initialGraph;
	}

	@Override
	public void setInitialGraph(Graph initialGraph) {
		this.initialGraph = initialGraph;
	}

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

}
