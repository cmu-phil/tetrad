package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.*;

/**
 * BOSS-DC (Best Order Score Search Divide and Conquer)
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "Restricted-BOSS",
//        command = "r-boss",
//        algoType = AlgType.forbid_latent_common_causes
//)
@Bootstrapping
public class RestrictedBoss implements Algorithm, UsesScoreWrapper,
        ReturnsBootstrapGraphs {
    private static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    // Don't delete.
    /**
     * <p>Constructor for RestrictedBoss.</p>
     */
    public RestrictedBoss() {
    }

    /**
     * <p>Constructor for RestrictedBoss.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public RestrictedBoss(ScoreWrapper score) {
        this.score = score;
    }


    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet dataSet = (DataSet) dataModel;

            String string = parameters.getString(Params.TARGETS);
            String[] _targets;

            if (string.contains(",")) {
                _targets = string.split(",");
            } else {
                _targets = string.split(" ");
            }

            List<Node> targets = new ArrayList<>();

            for (String _target : _targets) {
                targets.add(dataSet.getVariable(_target));
            }

            for (Node node : targets) {
                if (node == null) {
                    throw new IllegalArgumentException("Targets need to be specified correctly.");
                }
            }

            System.out.println("targets: " + targets);

            // We will run BOSS with the target variables Tier 2 and the rest as Tier 1,
            // with edges forbidden in Tier1, then grab all the first layer variables
            // together with the target nodes, restrict the score to just these variables,
            // for the knowedge again restrict to just these variables, with the target
            // variables as Tier 2 and the rest as Tier 1, and run BOSS again and return
            // the result.

            Knowledge knowledge = new Knowledge();
            for (Node node : targets) knowledge.addToTier(2, node.getName());
            for (Node node : dataSet.getVariables()) {
                if (!targets.contains(node)) knowledge.addToTier(1, node.getName());
            }
            knowledge.setTierForbiddenWithin(1, true);
//            knowledge.setTierForbiddenWithin(2, true);

            Score score = this.score.getScore(dataSet, parameters);
            edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
            boss.setUseBes(parameters.getBoolean(Params.USE_BES));
            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            PermutationSearch permutationSearch = new PermutationSearch(boss);
            permutationSearch.setSeed(parameters.getLong(Params.SEED));
            permutationSearch.setKnowledge(knowledge);
            permutationSearch.search();

            Set<Node> restrictedSet = new HashSet<>(targets);

            for (Node node : targets) {
                restrictedSet.addAll(permutationSearch.getGST(node).getFirstLayer());
            }

            List<Node> restrictedList = new ArrayList<>(restrictedSet);
            Collections.sort(restrictedList);

            DataSet restrictedData = dataSet.subsetColumns(restrictedList);

            System.out.println("Restricted data # vars: " + restrictedData.getVariables().size());

            knowledge = new Knowledge();
            for (Node node : targets) knowledge.addToTier(2, node.getName());
            for (Node node : restrictedData.getVariables()) {
                if (!targets.contains(node)) knowledge.addToTier(1, node.getName());
            }
            knowledge.setTierForbiddenWithin(1, false);
//            knowledge.setTierForbiddenWithin(2, true);

            score = this.score.getScore(restrictedData, parameters);
            boss = new edu.cmu.tetrad.search.Boss(score);
            boss.setUseBes(parameters.getBoolean(Params.USE_BES));
            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            permutationSearch = new PermutationSearch(boss);
            permutationSearch.setKnowledge(knowledge);

            Graph graph = permutationSearch.search();
            graph = GraphUtils.trimGraph(targets, graph, parameters.getInt(Params.TRIMMING_STYLE));
            return graph;
        } else {
            RestrictedBoss algorithm = new RestrictedBoss(this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
//            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
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
        return "Restricted BOSS (Best Order Score Search) using " + this.score.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Parameters
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.TARGETS);
        params.add(Params.TRIMMING_STYLE);
        params.add(Params.SEED);

        return params;
    }

    /** {@inheritDoc} */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /** {@inheritDoc} */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
