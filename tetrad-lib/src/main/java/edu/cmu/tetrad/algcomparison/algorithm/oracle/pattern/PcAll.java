package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC All",
        command = "pc-all",
        algoType = AlgType.forbid_latent_common_causes
)
public class PcAll implements Algorithm, TakesInitialGraph, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcAll() {
    }

    public PcAll(IndependenceWrapper test) {
        this.test = test;
    }

    public PcAll(IndependenceWrapper test, Algorithm algorithm) {
        this.test = test;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("bootstrapSampleSize") < 1) {

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

            edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(test.getTest(dataSet, parameters), initialGraph);
            search.setDepth(parameters.getInt("depth"));
            search.setKnowledge(knowledge);
            search.setFasRule(fasRule);
            search.setColliderDiscovery(colliderDiscovery);
            search.setConflictRule(conflictRule);
            search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
            search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
            search.setVerbose(parameters.getBoolean("verbose"));

            return search.search();
        } else {
            PcAll pcAll = new PcAll(test, algorithm);

            //pcAll.setKnowledge(knowledge);
            if (initialGraph != null) {
                pcAll.setInitialGraph(initialGraph);
            }

            DataSet data = (DataSet) dataSet;

            GeneralBootstrapTest search = new GeneralBootstrapTest(data, pcAll, parameters.getInt("bootstrapSampleSize"));
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
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PC using " + test.getDescription() + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("fasRule");
        parameters.add("colliderDiscoveryRule");
        parameters.add("conflictRule");
        parameters.add("depth");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
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
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

}
