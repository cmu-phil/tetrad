package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FASK-PW (pairwise).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-PW",
        command = "fask-pw",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous
)
@Bootstrapping
public class FaskPw implements Algorithm, TakesExternalGraph {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The algorithm to use for the initial graph.
     */
    private Algorithm algorithm;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * <p>Constructor for FaskPw.</p>
     */
    public FaskPw() {
    }

    /**
     * <p>Constructor for FaskPw.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public FaskPw(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (this.externalGraph == null) {
                this.externalGraph = this.algorithm.search(dataModel, parameters);
            }

            boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

            if (this.externalGraph == null) {
                throw new IllegalArgumentException(
                        "This FASK-PW (pairwise) algorithm needs both data and a graph source as inputs; it \n"
                                + "will orient the edges in the input graph using the data");
            }

            DataSet dataSet = SimpleDataLoader.getContinuousDataSet(dataModel);

            Fask fask = new Fask(dataSet, new SemBicScore(dataSet, precomputeCovariances), new IndTestFisherZ(dataSet, 0.01));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            fask.setExternalGraph(this.externalGraph);
            fask.setSkewEdgeThreshold(Double.POSITIVE_INFINITY);

            return fask.search();
        } else {
            FaskPw rSkew = new FaskPw(this.algorithm);
            if (this.externalGraph != null) {
                rSkew.setExternalGraph(this.algorithm);
            }

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, rSkew,
                    new Knowledge(), parameters);

            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
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
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "RSkew" + (this.algorithm != null ? " with initial graph from "
                + this.algorithm.getDescription() : "");
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

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This FASK-PW (pairwise) algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }
}
