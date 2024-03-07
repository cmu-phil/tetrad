package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
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
 * LiNGAM.
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * {@inheritDoc}
     */
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalStateException("Expecting a dataset.");
        }

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            int maxIter = parameters.getInt(Params.FAST_ICA_MAX_ITER);
            double alpha = parameters.getDouble(Params.FAST_ICA_A);
            double tol = parameters.getDouble(Params.FAST_ICA_TOLERANCE);

            Matrix W = IcaLingD.estimateW(data, maxIter, tol, alpha);
            edu.cmu.tetrad.search.IcaLingam icaLingam = new edu.cmu.tetrad.search.IcaLingam();
            icaLingam.setBThreshold(parameters.getDouble(Params.THRESHOLD_B));
            icaLingam.setAcyclicityGuaranteed(parameters.getBoolean(Params.GUARANTEE_ACYCLIC));

            Matrix bHat = icaLingam.fitW(W);
            Graph graph = IcaLingD.makeGraph(bHat, data.getVariables());
            TetradLogger.getInstance().forceLogMessage(bHat.toString());
            TetradLogger.getInstance().forceLogMessage(graph.toString());

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
        return "ICA-LiNGAM (ICA Linear Non-Gaussian Acyclic Model";
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
        parameters.add(Params.FAST_ICA_MAX_ITER);
        parameters.add(Params.FAST_ICA_A);
        parameters.add(Params.FAST_ICA_TOLERANCE);
        parameters.add(Params.THRESHOLD_B);
        parameters.add(Params.GUARANTEE_ACYCLIC);
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
