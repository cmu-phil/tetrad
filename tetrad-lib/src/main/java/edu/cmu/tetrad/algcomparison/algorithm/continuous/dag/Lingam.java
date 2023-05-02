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
import edu.cmu.tetrad.search.LingD;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * LiNGAM.
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LiNGAM",
        command = "lingam",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Lingam implements Algorithm {

    static final long serialVersionUID = 23L;

    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
            double alpha = parameters.getDouble(Params.FAST_ICA_A);
            double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);
            double bThreshold = parameters.getDouble(Params.THRESHOLD_W);
            double spineThreshold = parameters.getDouble(Params.THRESHOLD_SPINE);

            Matrix W = LingD.estimateW(data, maxIter, tol, alpha);
            edu.cmu.tetrad.search.Lingam lingam = new edu.cmu.tetrad.search.Lingam();
            lingam.setBThreshold(bThreshold);
            lingam.setSpineThreshold(spineThreshold);

            Matrix bHat = lingam.search(W);
            Graph graph = LingD.makeGraph(bHat, data.getVariables());
            TetradLogger.getInstance().forceLogMessage(bHat.toString());
            TetradLogger.getInstance().forceLogMessage(graph.toString());

            return graph;
        } else {
            Lingam algorithm = new Lingam();

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
        return "LiNGAM (Linear Non-Gaussian Acyclic Model";
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
        parameters.add(Params.THRESHOLD_W);
        parameters.add(Params.THRESHOLD_SPINE);
        return parameters;
    }
}