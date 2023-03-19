package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BossNew2;
import edu.cmu.tetrad.search.PermutationSearch2;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SpNew;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * SP (Sparsest Permutation)
 *
 * @author bryanandrews
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SP-New",
        command = "sp-new",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class SPNEW implements Algorithm, UsesScoreWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();

    public SPNEW() {
        // Used in reflection; do not delete.
    }

    public SPNEW(ScoreWrapper score) {
        this.score = score;
    }


    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        Score score = this.score.getScore(dataModel, parameters);
        PermutationSearch2 permutationSearch2 = new PermutationSearch2(new SpNew(score));

//        int numPerTier = 10;
//        for (int tier = 0; tier < 2; tier++) {
//            for (int i = 1; i <= numPerTier; i++) {
//                this.knowledge.addToTier(tier, "X" + (numPerTier * tier + i));
//            }
//        }

        permutationSearch2.setKnowledge(this.knowledge);
        permutationSearch2.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return permutationSearch2.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "SP New (Sparsest Permutation) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.VERBOSE);


        return params;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
