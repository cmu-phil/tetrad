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
import edu.pitt.dbmi.algo.subsampling.GeneralSubSamplingTest;
import edu.pitt.dbmi.algo.subsampling.SubSamplingEdgeEnsemble;

import java.io.PrintStream;
import java.util.List;

/**
 * GFCI.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GFCI",
        command = "gfci",
        algoType = AlgType.allow_latent_common_causes
)
public class Gfci implements Algorithm, HasKnowledge, UsesScoreWrapper, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public Gfci() {
    }

    public Gfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("numberSubSampling") < 1) {
            GFci search = new GFci(test.getTest(dataSet, parameters), score.getScore(dataSet, parameters));
            search.setMaxDegree(parameters.getInt("maxDegree"));
            search.setKnowledge(knowledge);
            search.setVerbose(parameters.getBoolean("verbose"));
            search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
            search.setMaxPathLength(parameters.getInt("maxPathLength"));
            search.setCompleteRuleSetUsed(parameters.getBoolean("completeRuleSetUsed"));

            Object obj = parameters.get("printStream");

            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            Gfci algorithm = new Gfci(test, score);

            //algorithm.setKnowledge(knowledge);
//          if (initialGraph != null) {
//      		algorithm.setInitialGraph(initialGraph);
//  		}
            DataSet data = (DataSet) dataSet;
            GeneralSubSamplingTest search = new GeneralSubSamplingTest(data, algorithm, parameters.getInt("numberSubSampling"));
            search.setKnowledge(knowledge);

            search.setSubSampleSize(parameters.getInt("subSampleSize"));
            search.setSubSamplingWithReplacement(parameters.getBoolean("subSamplingWithReplacement"));
            
            SubSamplingEdgeEnsemble edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("subSamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = SubSamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "GFCI (Greedy Fast Causal Inference) using " + test.getDescription()
                + " and " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.addAll(score.getParameters());
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
//        parameters.add("printStream");
        parameters.add("maxPathLength");
        parameters.add("completeRuleSetUsed");
        // Subsampling
        parameters.add("numberSubSampling");
        parameters.add("subSampleSize");
        parameters.add("subSamplingWithReplacement");
        parameters.add("subSamplingEnsemble");
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
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

}
