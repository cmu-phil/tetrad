package edu.cmu.tetrad.algcomparison.algorithm.mixed.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.List;

/**
 * @author jdramsey
 */
@Bootstrapping
public class MixedFgesDiscretingContinuousVariables implements Algorithm {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;

    public MixedFgesDiscretingContinuousVariables(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Discretizer discretizer = new Discretizer(DataUtils.getContinuousDataSet(dataSet));
            List<Node> nodes = dataSet.getVariables();

            for (Node node : nodes) {
                if (node instanceof ContinuousVariable) {
                    discretizer.equalIntervals(node, parameters.getInt(Params.NUM_CATEGORIES));
                }
            }

            dataSet = discretizer.discretize();
            DataSet _dataSet = DataUtils.getDiscreteDataSet(dataSet);
            Fges fges = new Fges(score.getScore(_dataSet, parameters));
            fges.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph p = fges.search();
            return convertBack(_dataSet, p);
    	}else{
    		MixedFgesDiscretingContinuousVariables algorithm = new MixedFgesDiscretingContinuousVariables(score);
    		
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
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES after discretizing the continuous variables in the data set using " + score.getDescription();
    }

    private Graph convertBack(DataSet Dk, Graph p) {
        Graph p2 = new EdgeListGraph(Dk.getVariables());

        for (int i = 0; i < p.getNodes().size(); i++) {
            for (int j = i + 1; j < p.getNodes().size(); j++) {
                Node v1 = p.getNodes().get(i);
                Node v2 = p.getNodes().get(j);

                Edge e = p.getEdge(v1, v2);

                if (e != null) {
                    Node w1 = Dk.getVariable(e.getNode1().getName());
                    Node w2 = Dk.getVariable(e.getNode2().getName());

                    Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                    p2.addEdge(e2);
                }
            }
        }
        return p2;
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add(Params.NUM_CATEGORIES);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
