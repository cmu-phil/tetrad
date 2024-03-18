package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IcaLingD;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * IcaLingam class implements the Algorithm and ReturnsBootstrapGraphs interface. It provides the implementation of the
 * ICA-LiNGAM algorithm for causal discovery.
 *
 * @see edu.cmu.tetrad.search.IcaLingam
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "ICA-LiNGAM",
        command = "ica-lingam",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class IcaLingam extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;
    private Matrix bHat = null;

    /**
     * Constructs a new instance of the IcaLingam algorithm.
     */
    public IcaLingam() {

    }

    /**
     * Searches for a graph structure based on the given data set and parameters.
     *
     * @param dataSet    The data set to run the search on.
     * @param parameters The parameters of the search.
     * @return The resulting graph structure.
     */
    public Graph runSearch(DataModel dataSet, Parameters parameters) {
        DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

        int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
        double alpha = parameters.getDouble(Params.FAST_ICA_A);
        double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);

        Matrix W = IcaLingD.estimateW(data, maxIter, tol, alpha, parameters.getBoolean(Params.VERBOSE));
        edu.cmu.tetrad.search.IcaLingam icaLingam = new edu.cmu.tetrad.search.IcaLingam();
        icaLingam.setVerbose(parameters.getBoolean(Params.VERBOSE));
        icaLingam.setBThreshold(parameters.getDouble(Params.THRESHOLD_B));
        bHat = icaLingam.getAcyclicTrimmedBHat(W);
        Graph graph = IcaLingD.makeGraph(bHat, data.getVariables());

        if (parameters.getBoolean(Params.VERBOSE)) {
            TetradLogger.getInstance().forceLogMessage("BHat = " + bHat);
            TetradLogger.getInstance().forceLogMessage("Graph = " + graph);
        }

        LogUtilsSearch.stampWithBic(graph, dataSet);
        return graph;
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true, directed graph, if there is one.
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
     * Retrieves the bHat matrix.
     *
     * @return The bHat matrix.
     */
    public Matrix getbHat() {
        return bHat;
    }
}
