package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FGES-MB",
        command = "fges-mb",
        algoType = AlgType.search_for_Markov_blankets
)
public class FgesMb implements Algorithm, TakesInitialGraph, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();
    private String targetName;

    public FgesMb() {
    }

    public FgesMb(ScoreWrapper score) {
        this.score = score;
    }

    public FgesMb(ScoreWrapper score, Algorithm algorithm) {
        this.score = score;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("numberResampling") < 1) {
            if (algorithm != null) {
//                initialGraph = algorithm.search(dataSet, parameters);
            }

            Score score = this.score.getScore(dataSet, parameters);
            edu.cmu.tetrad.search.FgesMb search = new edu.cmu.tetrad.search.FgesMb(score);
            search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
            search.setKnowledge(knowledge);
            search.setVerbose(parameters.getBoolean("verbose"));
            search.setMaxDegree(parameters.getInt("maxDegree"));

            Object obj = parameters.get("printStream");
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            if (initialGraph != null) {
                search.setInitialGraph(initialGraph);
            }

            this.targetName = parameters.getString("targetName");
            Node target = this.score.getVariable(targetName);

            return search.search(Collections.singletonList(target));
        } else {
            FgesMb fgesMb = new FgesMb(score, algorithm);

            if (initialGraph != null) {
                fgesMb.setInitialGraph(initialGraph);
            }
            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fgesMb, parameters.getInt("numberResampling"));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Node target = graph.getNode(targetName);
        return GraphUtils.markovBlanketDag(target, new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES (Fast Greedy Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("targetName");
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
        parameters.add("verbose");

        // Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");

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
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

}
