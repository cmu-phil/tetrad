package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FTFC.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FTFC",
        command = "ftfc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Ftfc implements Algorithm, HasKnowledge, ClusterAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Ftfc.</p>
     */
    public Ftfc() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            ICovarianceMatrix cov;

            if (dataSet instanceof DataSet) {
                boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);
                cov = SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances);
            } else if (dataSet instanceof ICovarianceMatrix) {
                cov = (ICovarianceMatrix) dataSet;
            } else {
                throw new IllegalArgumentException("Expected a dataset or a covariance matrix.");
            }

            double alpha = parameters.getDouble(Params.ALPHA);

            boolean gap = parameters.getBoolean(Params.USE_GAP, true);
            edu.cmu.tetrad.search.Ftfc.Algorithm algorithm;

            if (gap) {
                algorithm = edu.cmu.tetrad.search.Ftfc.Algorithm.GAP;
            } else {
                algorithm = edu.cmu.tetrad.search.Ftfc.Algorithm.SAG;
            }

            edu.cmu.tetrad.search.Ftfc search
                    = new edu.cmu.tetrad.search.Ftfc(cov, algorithm, alpha);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            Ftfc algorithm = new Ftfc();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.cpdagForDag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FTFC (Find Two Factor Clusters)";
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
        parameters.add(Params.ALPHA);
        parameters.add(Params.USE_WISHART);
        parameters.add(Params.USE_GAP);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

}
