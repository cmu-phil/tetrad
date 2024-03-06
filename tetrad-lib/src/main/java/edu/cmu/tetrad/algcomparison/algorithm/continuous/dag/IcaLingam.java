package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IcaLingD;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * IcaLingam class implements the Algorithm and ReturnsBootstrapGraphs interface.
 * It provides the implementation of the ICA-LiNGAM algorithm for causal discovery.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "ICA-LiNGAM",
        command = "ica-lingam",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class IcaLingam implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The bootstrap graphs.
     */
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /**
     * Searches for a graph structure based on the given data set and parameters.
     *
     * @param dataSet    The data set to run the search on.
     * @param parameters The parameters of the search.
     * @return The resulting graph structure.
     */
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
            double alpha = parameters.getDouble(Params.FAST_ICA_A);
            double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);

            Matrix W = IcaLingD.estimateW(data, maxIter, tol, alpha);
            edu.cmu.tetrad.search.IcaLingam icaLingam = new edu.cmu.tetrad.search.IcaLingam();
            icaLingam.setBThreshold(parameters.getDouble(Params.THRESHOLD_B));
            Matrix bHat = icaLingam.getAcyclicTrimmedBHat(W);
            Graph graph = IcaLingD.makeGraph(bHat, data.getVariables());
            TetradLogger.getInstance().forceLogMessage("BHat = " + bHat);
            TetradLogger.getInstance().forceLogMessage("Graph = " + graph);

            LogUtilsSearch.stampWithBic(graph, dataSet);
            return graph;
        } else {
            IcaLingam algorithm = new IcaLingam();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data,
                    algorithm,
                    new Knowledge(),
                    parameters
            );

            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return search.search();
        }
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns the description of the ICA-LiNGAM algorithm.
     *
     * @return The description of the ICA-LiNGAM algorithm.
     */
    public String getDescription() {
        return "ICA-LiNGAM (ICA Linear Non-Gaussian Acyclic Model";
    }

    /**
     * Returns the data type of the given method.
     *
     * @return The data type of the method. It can be Continuous, Discrete, Mixed, Graph, Covariance, or All.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns a list of parameters used by the getParameters method.
     *
     * @return A list of parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        parameters.add(Params.FAST_ICA_MAX_ITER);
        parameters.add(Params.FAST_ICA_A);
        parameters.add(Params.FAST_ICA_TOLERANCE);
        parameters.add(Params.THRESHOLD_B);
        return parameters;
    }

    /**
     * Returns the list of bootstrap graphs generated by the algorithm.
     *
     * @return A list of {@link Graph} objects representing the bootstrap graphs.
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
