package edu.cmu.tetrad.algcomparison.algorithm.mixed;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "MGM",
        command = "mgm",
        algoType = AlgType.produce_undirected_graphs
)
public class Mgm implements Algorithm {

    static final long serialVersionUID = 23L;

    public Mgm() {
    }

    @Override
    public Graph search(DataModel ds, Parameters parameters) {
    	// Notify the user that you need at least one continuous and one discrete variable to run MGM
        List<Node> variables = ds.getVariables();
        boolean hasContinuous = false;
        boolean hasDiscrete = false;

        for (Node node : variables) {
            if (node instanceof ContinuousVariable) {
                hasContinuous = true;
            }

            if (node instanceof DiscreteVariable) {
                hasDiscrete = true;
            }
        }

        if (!hasContinuous || !hasDiscrete) {
            throw new IllegalArgumentException("You need at least one continuous and one discrete variable to run MGM.");
        }
        
        if (parameters.getInt("numberResampling") < 1) {
            DataSet _ds = DataUtils.getMixedDataSet(ds);

            double mgmParam1 = parameters.getDouble("mgmParam1");
            double mgmParam2 = parameters.getDouble("mgmParam2");
            double mgmParam3 = parameters.getDouble("mgmParam3");

            double[] lambda = {
                mgmParam1,
                mgmParam2,
                mgmParam3
            };

            MGM m = new MGM(_ds, lambda);

            return m.search();
        } else {
            Mgm algorithm = new Mgm();

            DataSet data = (DataSet) ds;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt("numberResampling"));
            
            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
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
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    // Need to marry the parents on this.
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Returns the output of the MGM (Mixed Graphical Model) algorithm (a Markov random field)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("mgmParam1");
        parameters.add("mgmParam2");
        parameters.add("mgmParam3");
        // Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
        parameters.add("verbose");
        return parameters;
    }
}
