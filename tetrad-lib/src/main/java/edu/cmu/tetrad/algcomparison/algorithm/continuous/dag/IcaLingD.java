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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * LiNG-D.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "ICA-LiNG-D",
        command = "ica-ling-d",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class IcaLingD extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The bootstrap graphs.
     */
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

        int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
        double alpha = parameters.getDouble(Params.FAST_ICA_A);
        double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);
        double bThreshold = parameters.getDouble(Params.THRESHOLD_B);
        double spineThreshold = parameters.getDouble(Params.THRESHOLD_SPINE);

        Matrix W = edu.cmu.tetrad.search.IcaLingD.estimateW(data, maxIter, tol, alpha);

        edu.cmu.tetrad.search.IcaLingD icaLingD = new edu.cmu.tetrad.search.IcaLingD();
        icaLingD.setBThreshold(bThreshold);
        icaLingD.setSpineThreshold(spineThreshold);
        List<Matrix> bHats = icaLingD.fitW(W);

        int count = 0;

        Graph outGraph = null;
        TetradLogger.getInstance().forceLogMessage("STABLE MODELS:\n");

        for (Matrix bHat : bHats) {
            TetradLogger.getInstance().forceLogMessage("LiNG-D Model #" + (++count));
            boolean stable = edu.cmu.tetrad.search.IcaLingD.isStable(bHat);

            if (stable) {
                Graph graph = edu.cmu.tetrad.search.IcaLingD.makeGraph(bHat, dataSet.getVariables());
                TetradLogger.getInstance().forceLogMessage(bHat.toString());
                TetradLogger.getInstance().forceLogMessage(graph.toString());

                if (outGraph == null) outGraph = graph;
            }
        }

        if (outGraph == null) {
            TetradLogger.getInstance().forceLogMessage("## There were no stable models.##");
        }

        return outGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return "LiNG-D (Linear Non-Gaussian Discovery";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        parameters.add(Params.FAST_ICA_A);
        parameters.add(Params.FAST_ICA_MAX_ITER);
        parameters.add(Params.FAST_ICA_TOLERANCE);
        parameters.add(Params.THRESHOLD_B);
        parameters.add(Params.THRESHOLD_SPINE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }

}
