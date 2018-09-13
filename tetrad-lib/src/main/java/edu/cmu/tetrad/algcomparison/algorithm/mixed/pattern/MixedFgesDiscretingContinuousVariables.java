package edu.cmu.tetrad.algcomparison.algorithm.mixed.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.subsampling.GeneralSubSamplingTest;
import edu.pitt.dbmi.algo.subsampling.SubSamplingEdgeEnsemble;

import java.util.List;

/**
 * @author jdramsey
 */
public class MixedFgesDiscretingContinuousVariables implements Algorithm {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;

    public MixedFgesDiscretingContinuousVariables(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("numberSubSampling") < 1) {
            Discretizer discretizer = new Discretizer(DataUtils.getContinuousDataSet(dataSet));
            List<Node> nodes = dataSet.getVariables();

            for (Node node : nodes) {
                if (node instanceof ContinuousVariable) {
                    discretizer.equalIntervals(node, parameters.getInt("numCategories"));
                }
            }

            dataSet = discretizer.discretize();
            DataSet _dataSet = DataUtils.getDiscreteDataSet(dataSet);
            Fges fges = new Fges(score.getScore(_dataSet, parameters));
            Graph p = fges.search();
            return convertBack(_dataSet, p);
    	}else{
    		MixedFgesDiscretingContinuousVariables algorithm = new MixedFgesDiscretingContinuousVariables(score);
    		
    		DataSet data = (DataSet) dataSet;
    		GeneralSubSamplingTest search = new GeneralSubSamplingTest(data, algorithm, parameters.getInt("numberSubSampling"));
            
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
        parameters.add("numCategories");
     // Subsampling
        parameters.add("numberSubSampling");
        parameters.add("subSampleSize");
        parameters.add("subSamplingWithReplacement");
        parameters.add("subSamplingEnsemble");
        parameters.add("verbose");
        return parameters;
    }
}
