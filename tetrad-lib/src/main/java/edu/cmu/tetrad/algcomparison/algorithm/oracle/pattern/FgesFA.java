package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradMatrix;

import java.io.PrintStream;
import java.util.List;
import java.util.Vector;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
public class FgesFA implements Algorithm, TakesInitialGraph, HasKnowledge {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public FgesFA(ScoreWrapper score) {
        this.score = score;
        this.compareToTrue = false;
    }

    public FgesFA(ScoreWrapper score, boolean compareToTrueGraph) {
        this.score = score;
        this.compareToTrue = compareToTrueGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        dataSet = DataUtils.center((DataSet) dataSet);
        CovarianceMatrix covarianceMatrix = new CovarianceMatrix((DataSet) dataSet);
        edu.cmu.tetrad.search.FactorAnalysis analysis = new edu.cmu.tetrad.search.FactorAnalysis(covarianceMatrix);

        TetradMatrix unrotatedL = analysis.successiveResidual();
        TetradMatrix rotatedL = edu.cmu.tetrad.search.FactorAnalysis.successiveFactorVarimax(unrotatedL);

        ICovarianceMatrix cov2 = new CovarianceMatrix(covarianceMatrix.getVariables(), rotatedL.times(rotatedL.transpose()),
                covarianceMatrix.getSampleSize());

        edu.cmu.tetrad.search.Fges search
                = new edu.cmu.tetrad.search.Fges(score.getScore(cov2, parameters));
        search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        search.setMaxDegree(parameters.getInt("maxDegree"));
        search.setSymmetricFirstStep(parameters.getBoolean("symmetricFirstStep"));

        Object obj = parameters.get("printStream");
        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        if (compareToTrue) {
            return new EdgeListGraph(graph);
        } else {
            return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
        }
    }

    @Override
    public String getDescription() {
        return "FGES (Fast Greedy Equivalence Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("symmetricFirstStep");
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
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

    public void setCompareToTrue(boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
    }
}
