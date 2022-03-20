package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * RSkew.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-PW",
        command = "faskpw",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous
)
@Bootstrapping
public class FaskPW implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;

    public FaskPW() {
    }

    public FaskPW(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Graph graph = algorithm.search(dataModel, parameters);

            if (graph != null) {
                initialGraph = graph;
            } else {
                throw new IllegalArgumentException("This RSkew algorithm needs both data and a graph source as inputs; it \n"
                        + "will orient the edges in the input graph using the data");
            }

            DataSet dataSet = DataUtils.getContinuousDataSet(dataModel);

            Fask fask = new Fask(dataSet, new SemBicScore(dataSet), new IndTestFisherZ(dataSet, 0.01));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            fask.setExternalGraph(graph);
            fask.setSkewEdgeThreshold(Double.POSITIVE_INFINITY);

            return fask.search();
        } else {
            FaskPW rSkew = new FaskPW(algorithm);
            if (initialGraph != null) {
                rSkew.setInitialGraph(initialGraph);
            }

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, rSkew, parameters.getInt(Params.NUMBER_RESAMPLING));

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
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
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "RSkew" + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (algorithm != null && !algorithm.getParameters().isEmpty()) {
            parameters.addAll(algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This RSkew algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
