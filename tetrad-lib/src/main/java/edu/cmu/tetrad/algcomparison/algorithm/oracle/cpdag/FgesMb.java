package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FGES-MB (the heuristic version).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FGES-MB",
        command = "fges-mb",
        algoType = AlgType.search_for_Markov_blankets
)
@Bootstrapping
public class FgesMb extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, UsesScoreWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

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
     * The targets.
     */
    private List<Node> targets = null;


    /**
     * <p>Constructor for FgesMb.</p>
     */
    public FgesMb() {
    }

    /**
     * <p>Constructor for FgesMb.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FgesMb(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        int trimmingStyle = parameters.getInt(Params.TRIMMING_STYLE);

        Score myScore = this.score.getScore(dataModel, parameters);
        edu.cmu.tetrad.search.FgesMb search = new edu.cmu.tetrad.search.FgesMb(myScore);
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setNumExpansions(parameters.getInt(Params.NUMBER_OF_EXPANSIONS));
        search.setTrimmingStyle(trimmingStyle);
        search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream ps) {
            search.setOut(ps);
        }

        String string = parameters.getString(Params.TARGETS);
        String[] _targets;

        if (string.contains(",")) {
            _targets = string.split(",");
        } else {
            _targets = string.split(" ");
        }

        List<Node> myTargets = new ArrayList<>();

        for (String _target : _targets) {
            Node variable = dataModel.getVariable(_target);

            if (variable == null) {
                throw new IllegalArgumentException("Target not in data: " + _target);
            }

            myTargets.add(variable);
        }

        this.targets = myTargets;

        return search.search(myTargets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        if (targets == null) {
            throw new IllegalArgumentException("Targets not set.");
        }

        return GraphUtils.markovBlanketSubgraph(targets.get(0), new EdgeListGraph(graph));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FGES-MB (Fast Greedy Search MB) using " + this.score.getDescription();
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
        parameters.add(Params.TARGETS);
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.TRIMMING_STYLE);
        parameters.add(Params.NUMBER_OF_EXPANSIONS);
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

}
