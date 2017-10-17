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
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import java.util.Collections;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FgesMb",
        command = "fges-mb",
        algoType = AlgType.search_for_Markov_blankets,
        description = "This is a restriction of the FGES algorithm to union of edges over the combined Markov blankets of a set of targets, including the targets. In the interface, just one target may be specified. See Ramsey et al., 2017 for details. In the general case, finding the graph over the Markov blanket variables of a target (including the target) is far faster than finding the pattern for all of the variables.\n" +
                "\n" +
                "Input Assumptions: The same as FGES\n" +
                "\n" +
                "Output Format: A graph over a selected group of nodes that includes the target and each node in the Markov blanket of the target. This will be the same as if FGES were run and the result restricted to just these variables, so some edges may be oriented in the returned graph that may not have been oriented in a pattern over the selected nodes.\n" +
                "\n" +
                "Parameters: Uses the parameters of FGES.\n" +
                "- Target Name. The name of the target variables for the Markov blanket one wishes to construct. Default blank (that is, unspecified.) A variable must be specified here to run the algorithm."
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
        if (!parameters.getBoolean("bootstrapping")) {
            if (algorithm != null) {
//                initialGraph = algorithm.search(dataSet, parameters);
            }

            Score score = this.score.getScore(dataSet, parameters);
            edu.cmu.tetrad.search.FgesMb search = new edu.cmu.tetrad.search.FgesMb(score);
            search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
            search.setKnowledge(knowledge);

            if (initialGraph != null) {
                search.setInitialGraph(initialGraph);
            }

            this.targetName = parameters.getString("targetName");
            Node target = this.score.getVariable(targetName);

            return search.search(Collections.singletonList(target));
        } else {
            FgesMb fgesMb = new FgesMb(score, algorithm);

            fgesMb.setKnowledge(knowledge);
            if (initialGraph != null) {
                fgesMb.setInitialGraph(initialGraph);
            }
            DataSet data = (DataSet) dataSet;
            GeneralBootstrapTest search = new GeneralBootstrapTest(data, fgesMb,
                    parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
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
        // Bootstrapping
        parameters.add("bootstrapping");
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
