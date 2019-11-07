package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.OrientColliders;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * WW.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "WaynesWorld",
        command = "wayne",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class WaynesWorld implements Algorithm, TakesInitialGraph, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public WaynesWorld() {
    }

    public WaynesWorld(IndependenceWrapper test) {
        this.test = test;
    }

    public WaynesWorld(IndependenceWrapper test, Algorithm algorithm) {
        this.test = test;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            OrientColliders.ColliderMethod colliderDiscovery;

            switch (parameters.getInt(Params.COLLIDER_DISCOVERY_RULE)) {
                case 1:
                    colliderDiscovery = OrientColliders.ColliderMethod.SEPSETS;
                    break;
                case 2:
                    colliderDiscovery = OrientColliders.ColliderMethod.CPC;
                    break;
                case 3:
                    colliderDiscovery = OrientColliders.ColliderMethod.MPC;
                    break;
                case 4:
                    colliderDiscovery = OrientColliders.ColliderMethod.PC_MAX;
                    break;
                default:
                    throw new IllegalArgumentException("Not a choice.");
            }

            OrientColliders.ConflictRule conflictRule;

            switch (parameters.getInt(Params.CONFLICT_RULE)) {
                case 1:
                    conflictRule = OrientColliders.ConflictRule.OVERWRITE;
                    break;
                case 2:
                    conflictRule = OrientColliders.ConflictRule.BIDIRECTED;
                    break;
                case 3:
                    conflictRule = OrientColliders.ConflictRule.PRIORITY;
                    break;
                default:
                    throw new IllegalArgumentException("Not a choice.");
            }

            edu.cmu.tetrad.search.WaynesWorld search = new edu.cmu.tetrad.search.WaynesWorld(test.getTest(dataSet, parameters), initialGraph);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(knowledge);

//            if (parameters.getBoolean(Params.STABLE_FAS)) {
                search.setFasType(edu.cmu.tetrad.search.WaynesWorld.FasType.STABLE);
//            } else {
//                search.setFasType(edu.cmu.tetrad.search.WaynesWorld.FasType.REGULAR);
//            }

//            if (parameters.getBoolean(Params.CONCURRENT_FAS)) {
//                search.setConcurrent(edu.cmu.tetrad.search.WaynesWorld.Concurrent.YES);
//            } else {
                search.setConcurrent(edu.cmu.tetrad.search.WaynesWorld.Concurrent.NO);
//            }

            OrientColliders.IndependenceDetectionMethod independence_detection_method;

            search.setColliderDiscovery(OrientColliders.ColliderMethod.SEPSETS);
            search.setConflictRule(OrientColliders.ConflictRule.PRIORITY);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            WaynesWorld ww = new WaynesWorld(test, algorithm);

            if (initialGraph != null) {
                ww.setInitialGraph(initialGraph);
            }

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, ww, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
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
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
//        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "WaynesWorld using " + test.getDescription() + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.CONCURRENT_FAS);
        parameters.add(Params.COLLIDER_DISCOVERY_RULE);
        parameters.add(Params.CONFLICT_RULE);
        parameters.add(Params.USE_FDR_FOR_INDEPENDENCE);
        parameters.add(Params.DO_MARKOV_LOOP);
        parameters.add(Params.DEPTH);
        parameters.add(Params.ORIENTATION_ALPHA);

        parameters.add(Params.VERBOSE);
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
    
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

}