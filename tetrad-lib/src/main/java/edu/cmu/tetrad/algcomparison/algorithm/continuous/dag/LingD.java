package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
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
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LiNG-D",
        command = "ling-d",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class LingD implements Algorithm {

    static final long serialVersionUID = 23L;

    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
            double alpha = parameters.getDouble(Params.FAST_ICA_A);
            double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);
            double bThreshold = parameters.getDouble(Params.THRESHOLD_B);
            double spineThreshold = parameters.getDouble(Params.THRESHOLD_SPINE);

            Matrix W = edu.cmu.tetrad.search.LingD.estimateW(data, maxIter, tol, alpha);

            edu.cmu.tetrad.search.LingD lingD = new edu.cmu.tetrad.search.LingD();
            lingD.setBThreshold(bThreshold);
            lingD.setSpineThreshold(spineThreshold);
            List<Matrix> bHats = lingD.search(W);

            int count = 0;

            for (Matrix bHat : bHats) {
                TetradLogger.getInstance().forceLogMessage("LiNG-D Model #" + (++count));
                Graph graph = edu.cmu.tetrad.search.LingD.makeGraph(bHat, dataSet.getVariables());
                TetradLogger.getInstance().forceLogMessage(bHat.toString());
                TetradLogger.getInstance().forceLogMessage(graph.toString());
                TetradLogger.getInstance().forceLogMessage("Stable = " + edu.cmu.tetrad.search.LingD.isStable(bHat));
            }

            if (bHats.size() > 0) {
                return edu.cmu.tetrad.search.LingD.makeGraph(bHats.get(0), dataSet.getVariables());
            } else {
                throw new IllegalArgumentException("LiNG-D couldn't find a model.");
            }
        } else {
            LingD algorithm = new LingD();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    public String getDescription() {
        return "LiNG-D (Linear Non-Gaussian Discovery";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

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
}