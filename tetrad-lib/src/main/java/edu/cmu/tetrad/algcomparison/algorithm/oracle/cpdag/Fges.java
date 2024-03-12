package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FGES",
        command = "fges",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Fges extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        UsesScoreWrapper, TakesExternalGraph, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The external graph.
     */
    private Graph externalGraph = null;

    /**
     * The algorithm.
     */
    private Algorithm algorithm = null;

    /**
     * <p>Constructor for Fges.</p>
     */
    public Fges() {

    }

    /**
     * <p>Constructor for Fges.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Fges(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        if (this.algorithm != null) {
            Graph _graph = this.algorithm.search(dataModel, parameters);

            if (_graph != null) {
                this.externalGraph = _graph;
            }
        }

        Score myScore = this.score.getScore(dataModel, parameters);
        Graph graph;

        edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(myScore);
        search.setBoundGraph(externalGraph);
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setMeekVerbose(parameters.getBoolean(Params.MEEK_VERBOSE));
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setSymmetricFirstStep(parameters.getBoolean(Params.SYMMETRIC_FIRST_STEP));
        search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
        search.setNumThreads(parameters.getInt(Params.NUM_THREADS));

        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream ps) {
            search.setOut(ps);
        }

        graph = search.search();

        LogUtilsSearch.stampWithScore(graph, myScore);
        LogUtilsSearch.stampWithBic(graph, dataModel);

        return graph;
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
        return "FGES (Fast Greedy Equivalence Search) using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.SYMMETRIC_FIRST_STEP);
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.NUM_THREADS);
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.TIME_LAG);

        parameters.add(Params.VERBOSE);
        parameters.add(Params.MEEK_VERBOSE);

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

    /**
     * {@inheritDoc}
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

}
