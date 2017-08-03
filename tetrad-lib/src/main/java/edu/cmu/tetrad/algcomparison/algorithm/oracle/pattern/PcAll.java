package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
public class PcAll implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcAll(IndependenceWrapper type) {
        this.test = type;
    }

    public PcAll(IndependenceWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.PcAll.FasRule fasRule;

        switch (parameters.getInt("fasRule")) {
            case 1:
                fasRule = edu.cmu.tetrad.search.PcAll.FasRule.FAS;
                break;
            case 2:
                fasRule = edu.cmu.tetrad.search.PcAll.FasRule.FAS_STABLE;
                break;
            case 3:
                fasRule = edu.cmu.tetrad.search.PcAll.FasRule.FAS_STABLE_CONCURRENT;
                break;
            default:
                    throw new IllegalArgumentException("Not a choice.");
        }

        edu.cmu.tetrad.search.PcAll.ColliderDiscovery colliderDiscovery;

        switch (parameters.getInt("colliderDiscoveryRule")) {
            case 1:
                colliderDiscovery = edu.cmu.tetrad.search.PcAll.ColliderDiscovery.FAS_SEPSETS;
                break;
            case 2:
                colliderDiscovery = edu.cmu.tetrad.search.PcAll.ColliderDiscovery.CONSERVATIVE;
                break;
            case 3:
                colliderDiscovery = edu.cmu.tetrad.search.PcAll.ColliderDiscovery.MAX_P;
                break;
            default:
                throw new IllegalArgumentException("Not a choice.");
        }

        edu.cmu.tetrad.search.PcAll.ConflictRule conflictRule;

        switch (parameters.getInt("conflictRule")) {
            case 1:
                conflictRule = edu.cmu.tetrad.search.PcAll.ConflictRule.OVERWRITE;
                break;
            case 2:
                conflictRule = edu.cmu.tetrad.search.PcAll.ConflictRule.BIDIRECTED;
                break;
            case 3:
                conflictRule = edu.cmu.tetrad.search.PcAll.ConflictRule.PRIORITY;
                break;
            default:
                throw new IllegalArgumentException("Not a choice.");
        }

        Graph init = null;

        if (initial != null) {
            init = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters), init);
        search.setDepth(parameters.getInt("depth"));
        search.setKnowledge(knowledge);
        search.setFasRule(fasRule);
        search.setColliderDiscovery(colliderDiscovery);
        search.setConflictRule(conflictRule);
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "CPC (Conservative \"Peter and Clark\") using " + test.getDescription() + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();

//        public enum FasRule {FAS, FAS_STABLE, FAS_STABLE_CONCURRENT}
//        public enum ColliderDiscovery {FAS_SEPSETS, CONSERVATIVE, MAX_P}
//        public enum ConflictRule {PRIORITY, BIDIRECTED, OVERWRITE}


        parameters.add("fasRule");
        parameters.add("colliderDiscoveryRule");
        parameters.add("conflictRule");
        parameters.add("depth");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
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
}
