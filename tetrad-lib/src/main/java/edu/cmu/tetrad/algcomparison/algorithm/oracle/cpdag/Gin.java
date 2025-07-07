package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * GIN (Generalized Independent Noise Search)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GIN",
        command = "gin",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class Gin extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;
    private IndependenceWrapper test;

    /**
     * Constructs a new BOSS algorithm.
     */
    public Gin() {
        // Used in reflection; do not delete.
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the BOSS algorithm.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet dataSet = (DataSet) dataModel;
        IndependenceTest test = this.test.getTest(dataSet, parameters);

        if (!(test instanceof RawMarginalIndependenceTest)) {
            throw new IllegalArgumentException("Test must implement RawIndependenceTest");
        }

        edu.cmu.tetrad.search.Gin gin = new edu.cmu.tetrad.search.Gin(parameters.getDouble(Params.ALPHA),
                (RawMarginalIndependenceTest) test);

        return gin.search(dataSet);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "GIN (Generalized Independent Noise Search)";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Parameters
        params.add(Params.ALPHA);
        return params;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
