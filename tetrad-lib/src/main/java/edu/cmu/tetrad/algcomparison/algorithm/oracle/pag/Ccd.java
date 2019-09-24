package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
@Bootstrapping
public class Ccd implements Algorithm {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;

    public Ccd(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.Ccd search = new edu.cmu.tetrad.search.Ccd(
                    test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setApplyR1(parameters.getBoolean(Params.APPLY_R1));

            return search.search();
    	}else{
    		Ccd algorithm = new Ccd(test);
    		
    		DataSet data = (DataSet) dataSet;
    		GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
    		
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
        return "CCD (Cyclic Causal Discovery using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add(Params.DEPTH);
        parameters.add(Params.APPLY_R1);

        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
