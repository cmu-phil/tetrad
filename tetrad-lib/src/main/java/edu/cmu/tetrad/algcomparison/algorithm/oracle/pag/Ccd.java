package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * CCD (Cyclic Causal Discovery)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CCD",
        command = "ccd",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Ccd implements Algorithm, TakesIndependenceWrapper, ReturnsBootstrapGraphs {
    @Serial
    private static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    /**
     * Constructs a new CCD algorithm.
     */
    public Ccd() {
        // Used in reflection; do not delete.
    }

    /**
     * Constructs a new CCD algorithm with the given independence test.
     *
     * @param test the independence test
     */
    public Ccd(IndependenceWrapper test) {
        this.test = test;
    }

    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.Ccd search = new edu.cmu.tetrad.search.Ccd(
                    test.getTest(dataSet, parameters));
            search.setApplyR1(parameters.getBoolean(Params.APPLY_R1));

            return search.search();
        } else {
            Ccd algorithm = new Ccd(this.test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "CCD (Cyclic Causal Discovery using " + test.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add(Params.DEPTH);
        parameters.add(Params.APPLY_R1);

        parameters.add(Params.VERBOSE);
        return parameters;
    }


    /** {@inheritDoc} */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /** {@inheritDoc} */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
