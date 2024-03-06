package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.*;

/**
 * IcaLingD is an implementation of the Algorithm interface that performs the ICA-LiNG-D algorithm for discovering the
 * underlying causal structure of continuous data.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "ICA-LiNG-D",
        command = "ica-ling-d",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class IcaLingD implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The bootstrap graphs.
     */
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /**
     * Runs a search on the provided data set using the given parameters. All stable and unstable graphs are printed to
     * the console along with their B matrices.
     *
     * @param dataSet    The data set to run the search on.
     * @param parameters The parameters of the search.
     * @return The first stable graph returned, otherwise and empty graph.
     */
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
            double alpha = parameters.getDouble(Params.FAST_ICA_A);
            double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);
            double bThreshold = parameters.getDouble(Params.THRESHOLD_B);

            Matrix W = edu.cmu.tetrad.search.IcaLingD.estimateW(data, maxIter, tol, alpha);

            edu.cmu.tetrad.search.IcaLingD icaLingD = new edu.cmu.tetrad.search.IcaLingD();
            icaLingD.setBThreshold(bThreshold);
            List<Matrix> bHats = icaLingD.getScaledBHats(W);
            Set<Graph> _graphs = new HashSet<>();
            Map<Graph, Matrix> _bHats = new HashMap<>();
            Set<Graph> _stableGraphs = new HashSet<>();

            for (Matrix bHat : bHats) {
                Graph graph = edu.cmu.tetrad.search.IcaLingD.makeGraph(bHat, dataSet.getVariables());
                _graphs.add(graph);
                _bHats.put(graph, bHat);

                if (edu.cmu.tetrad.search.IcaLingD.isStable(bHat)) {
                    _stableGraphs.add(graph);
                }
            }

            List<Graph> graphs = new ArrayList<>(_graphs);
            List<Graph> stableGraphs = new ArrayList<>(_stableGraphs);
            graphs.sort(Comparator.comparingInt(Graph::getNumEdges));
            stableGraphs.sort(Comparator.comparingDouble(Graph::getNumEdges));

            int count = 0;

            for (Graph graph : graphs) {
                TetradLogger.getInstance().forceLogMessage("LiNG-D Model #" + (++count) + "  Stable = " + stableGraphs.contains(graph));
                TetradLogger.getInstance().forceLogMessage(_bHats.get(graph).toString());
                TetradLogger.getInstance().forceLogMessage(graph.toString());
            }

            if (stableGraphs.isEmpty()) {
                TetradLogger.getInstance().forceLogMessage("## There were no stable models. ##");
            }

            return stableGraphs.isEmpty() ? new EdgeListGraph() : stableGraphs.get(0);
        } else {
            IcaLingD algorithm = new IcaLingD();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm,
                    new Knowledge(), parameters);

            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return search.search();
        }
    }

    /**
     * Retrieves the comparison graph of the provided true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Retrieves the description of the algorithm.
     *
     * @return The description of the algorithm.
     */
    public String getDescription() {
        return "LiNG-D (Linear Non-Gaussian Discovery";
    }

    /**
     * Retrieves the data type of the algorithm.
     *
     * @return The data type of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters used by this method.
     *
     * @return A list of strings representing the parameters used by this method.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        parameters.add(Params.FAST_ICA_A);
        parameters.add(Params.FAST_ICA_MAX_ITER);
        parameters.add(Params.FAST_ICA_TOLERANCE);
        parameters.add(Params.THRESHOLD_B);
        return parameters;
    }

    /**
     * Retrieves the list of bootstrap graphs generated by the algorithm.
     *
     * @return A list of bootstrap graphs.
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }

}
