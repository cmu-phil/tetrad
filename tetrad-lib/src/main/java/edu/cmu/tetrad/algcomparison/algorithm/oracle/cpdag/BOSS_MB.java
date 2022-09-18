package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BossMB;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

///**
// * BOSS-MB.
// *
// * @author jdramsey
// */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BOSS-MB",
//        command = "boss-mb",
//        algoType = AlgType.search_for_Markov_blankets
//)
//@Bootstrapping
//@Experimental
public class BOSS_MB implements Algorithm, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();
    private String targets;

    public BOSS_MB() {
    }

    public BOSS_MB(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            this.targets = parameters.getString(Params.TARGETS);

            String[] tokens = this.targets.split(",");
            List<Node> targets = new ArrayList<>();

            Score score = this.score.getScore(dataSet, parameters);

            for (String t : tokens) {
                String name = t.trim();
                targets.add(score.getVariable(name));
            }


            BossMB boss = new BossMB(score);

            boss.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            boss.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
            boss.setFindMb(parameters.getBoolean(Params.MB));

            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            boss.setKnowledge(this.knowledge);

            boss.bestOrder(score.getVariables(), targets);
            return boss.getGraph();
        } else {
            BOSS_MB fgesMb = new BOSS_MB(this.score);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fgesMb, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Node target = graph.getNode(this.targets);
        return GraphUtils.markovBlanketDag(target, new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "BOSS-MB using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.TARGETS);
        params.add(Params.MB);

        // Flags
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.CACHE_SCORES);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

}
