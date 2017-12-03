package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.io.PrintStream;
import java.util.List;

/**
 * FASK GFCI.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-GFCI",
        command = "faskgfci",
        algoType = AlgType.allow_latent_common_causes,
        description = "Greedy Fast Causal Inference Search (GFCI) using knowledge provided by FASK."
)
public class FaskGfci implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public FaskGfci() {
    }

    public FaskGfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        dataSet = DataUtils.center((DataSet) dataSet);
        edu.cmu.tetrad.search.FaskGfci search = new edu.cmu.tetrad.search.FaskGfci(
                test.getTest(dataSet, parameters), (DataSet) dataSet);
        search.setDepth(parameters.getInt("depth"));
        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        search.setPresumePositiveCoefficients(parameters.getBoolean("presumePositiveCoefficients"));
        search.setTwoCycleAlpha(parameters.getDouble("twoCycleAlpha"));
        search.setVerbose(parameters.getBoolean("verbose"));
        search.setKnowledge(knowledge);
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "FASK-GFCI (Greedy Fast Causal Inference with FASK knowledge) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("presumePositiveCoefficients");
        parameters.add("twoCycleAlpha");
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
        parameters.add("maxPathLength");
        parameters.add("completeRuleSetUsed");
        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

}
