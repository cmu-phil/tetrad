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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FGES-MB-Ancestors",
        command = "fges-mb-ancestors",
        algoType = AlgType.search_for_Markov_blankets,
        description = ""
)
public class FgesMbAncestors implements Algorithm, TakesInitialGraph, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();
    private String targetName;

    public FgesMbAncestors() {
    }

    public FgesMbAncestors(ScoreWrapper score) {
        this.score = score;
    }

    public FgesMbAncestors(ScoreWrapper score, Algorithm algorithm) {
        this.score = score;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _dataSet = (DataSet) dataSet.copy();
        this.targetName = parameters.getString("targetName");
        Node target = dataSet.getVariable(targetName);
        List<Node> _selectionVars = DataUtils.selectVariables(_dataSet, target, parameters.getDouble("selectionAlpha"), 40);
        _selectionVars.add(_dataSet.getVariable(targetName));
        _dataSet = _dataSet.subsetColumns(_selectionVars);

    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            Set<Node> ancestors = new HashSet<>();

            while (true) {
                Score score = this.score.getScore(_dataSet, parameters);

                edu.cmu.tetrad.search.FgesMb search = new edu.cmu.tetrad.search.FgesMb(score);
                search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
                search.setKnowledge(knowledge);

                if (initialGraph != null) {
                    search.setInitialGraph(initialGraph);
                }

                Graph _adj = search.search(Collections.singletonList(target));
                List<Node> parents =_adj.getParents(target);

                if (parents.isEmpty()) break;

                for (Node p : parents) {
                    _dataSet.removeColumn(p);
                }

                ancestors.addAll(parents);
            }

            Graph out = new EdgeListGraph();
            out.addNode(target);

            for (Node node : ancestors) {
                out.addNode(node);
                out.addDirectedEdge(node, target);
            }

            return out;
        } else {
            FgesMbAncestors fgesMb = new FgesMbAncestors(score, algorithm);

            //fgesMb.setKnowledge(knowledge);
            if (initialGraph != null) {
                fgesMb.setInitialGraph(initialGraph);
            }
            DataSet data = (DataSet) dataSet;
            GeneralBootstrapTest search = new GeneralBootstrapTest(data, fgesMb,
                    parameters.getInt("bootstrapSampleSize"));
            search.setKnowledge(knowledge);

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
