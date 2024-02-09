package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

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
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

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
public class IcaLingD implements Algorithm, ReturnsBootstrapGraphs {

    private static final long serialVersionUID = 23L;

    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /** {@inheritDoc} */
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
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

            for (Matrix bHat : bHats) {
                TetradLogger.getInstance().forceLogMessage("LiNG-D Model #" + (++count));
                Graph graph = edu.cmu.tetrad.search.IcaLingD.makeGraph(bHat, dataSet.getVariables());
                TetradLogger.getInstance().forceLogMessage(bHat.toString());
                TetradLogger.getInstance().forceLogMessage(graph.toString());
                TetradLogger.getInstance().forceLogMessage("Stable = " + edu.cmu.tetrad.search.IcaLingD.isStable(bHat));
            }

            if (bHats.size() > 0) {
                return edu.cmu.tetrad.search.IcaLingD.makeGraph(bHats.get(0), dataSet.getVariables());
            } else {
                throw new IllegalArgumentException("LiNG-D couldn't find a model.");
            }
        } else {
            IcaLingD algorithm = new IcaLingD();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return search.search();
        }
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }

}
