package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSampling;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TaskRunner;
import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * GFCI.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GFCI",
        command = "gfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class Gfci implements Algorithm, HasKnowledge, UsesScoreWrapper, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The bootstrap graphs.
     */
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /**
     * <p>
     * Constructor for Gfci.</p>
     */
    public Gfci() {
    }

    /**
     * <p>
     * Constructor for Gfci.</p>
     *
     * @param test a
     * {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper}
     * object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper}
     * object
     */
    public Gfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        List<DataSet> dataSets = DataSampling.createDataSamples((DataSet) dataModel, parameters);

        Graph graph;
        if (Thread.currentThread().isInterrupted()) {
            // release resources
            dataSets.clear();
            System.gc();

            graph = new EdgeListGraph();
        } else {
            List<Callable<Graph>> tasks = new LinkedList<>();
            for (DataSet dataSet : dataSets) {
                tasks.add(() -> runSearch(dataSet, parameters));
            }

            TaskRunner<Graph> taskRunner = new TaskRunner<>(parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));
            List<Graph> graphs = taskRunner.run(tasks);

            if (graphs.isEmpty()) {
                graph = new EdgeListGraph();
            } else {
                if (parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                    this.bootstrapGraphs = graphs;
                    graph = GraphSampling.createGraphWithHighProbabilityEdges(graphs);
                } else {
                    graph = graphs.get(0);
                }
            }
        }

        // release resources
        dataSets.clear();
        System.gc();

        return graph;
    }

    private Graph runSearch(DataSet dataSet, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataSet = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        GFci search = new GFci(this.test.getTest(dataSet, parameters), this.score.getScore(dataSet, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
        search.setPossibleMsepSearchDone(parameters.getBoolean((Params.POSSIBLE_MSEP_DONE)));
        search.setNumThreads(parameters.getInt(Params.NUM_THREADS));

        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream printStream) {
            search.setOut(printStream);
        }

        return search.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "GFCI (Greedy Fast Causal Inference) using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DO_DISCRIMINATING_PATH_RULE);
        parameters.add(Params.POSSIBLE_MSEP_DONE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.NUM_THREADS);

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
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
