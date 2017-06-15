package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScoreDeterministic;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.*;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
public class FgesFA implements Algorithm, TakesInitialGraph, HasKnowledge {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private IKnowledge knowledge = new Knowledge2();
    private ScoreWrapper score = new SemBicScoreDeterministic();

    public FgesFA() {
        setCompareToTrue(false);
    }

    public FgesFA(boolean compareToTrueGraph) {
        setCompareToTrue(compareToTrueGraph);
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        dataSet = DataUtils.center((DataSet) dataSet);
        CovarianceMatrix covarianceMatrix = new CovarianceMatrix((DataSet) dataSet);
        edu.cmu.tetrad.search.FactorAnalysis analysis = new edu.cmu.tetrad.search.FactorAnalysis(covarianceMatrix);
        analysis.setConvergenceThreshold(parameters.getDouble("convergenceThreshold"));

        TetradMatrix unrotatedL = analysis.successiveResidual();
        TetradMatrix rotatedL = analysis.successiveFactorVarimax(unrotatedL);


        ICovarianceMatrix covFa = new CovarianceMatrix(covarianceMatrix.getVariables(), rotatedL.times(rotatedL.transpose()),
                covarianceMatrix.getSampleSize());

        Score score = this.score.getScore(covFa, parameters);
        edu.cmu.tetrad.search.Fges2 search = new edu.cmu.tetrad.search.Fges2(score);
        search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        search.setMaxDegree(parameters.getInt("maxDegree"));
        search.setSymmetricFirstStep(parameters.getBoolean("symmetricFirstStep"));

        Object obj = parameters.get("printStream");
        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        if (parameters.getBoolean("verbose")) {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
            String output = "Unrotated Factor Loading Matrix:\n";
            double threshold = parameters.getDouble("fa_threshold");

            output += tableString(unrotatedL, nf, Double.POSITIVE_INFINITY);

            if (unrotatedL.columns() != 1) {
                output += "\n\nRotated Matrix (using sequential varimax):\n";
                output += tableString(rotatedL, nf, threshold);
            }

            System.out.println(output);
            TetradLogger.getInstance().forceLogMessage(output);
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
        parameters.add("determinismThreshold");
        parameters.add("convergenceThreshold");
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

    private String tableString(TetradMatrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.rows() + 1, matrix.columns() + 1);

        for (int i = 0; i < matrix.rows() + 1; i++) {
            for (int j = 0; j < matrix.columns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, j, "X" + i);
                } else if (i == 0 && j > 0) {
                    table.setToken(i, j, "Factor " + j);
                } else if (i > 0 && j > 0) {
                    double coefficient = matrix.get(i - 1, j - 1);
                    String token = !Double.isNaN(coefficient) ? nf.format(coefficient) : "Undefined";
                    token += Math.abs(coefficient) > threshold ? "*" : " ";
                    table.setToken(i, j, token);
                }
            }
        }

        return "\n" + table.toString();

    }
}
