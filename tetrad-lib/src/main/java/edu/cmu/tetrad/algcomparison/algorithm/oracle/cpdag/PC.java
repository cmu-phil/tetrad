package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC",
        command = "pc",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PC implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public PC() {
    }

    public PC(final IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final PcAll.ColliderDiscovery colliderDiscovery
                    = PcAll.ColliderDiscovery.FAS_SEPSETS;

            PcAll.ConflictRule conflictRule;

            switch (parameters.getInt(Params.CONFLICT_RULE)) {
                case 1:
                    conflictRule = PcAll.ConflictRule.OVERWRITE;
                    break;
                case 2:
                    conflictRule = PcAll.ConflictRule.BIDIRECTED;
                    break;
                case 3:
                    conflictRule = PcAll.ConflictRule.PRIORITY;
                    break;
                default:
                    throw new IllegalArgumentException("Not a choice.");
            }

            final edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(this.test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setHeuristic(parameters.getInt(Params.FAS_HEURISTIC));
            search.setKnowledge(this.knowledge);

            if (parameters.getBoolean(Params.STABLE_FAS)) {
                search.setFasType(PcAll.FasType.STABLE);
            } else {
                search.setFasType(PcAll.FasType.REGULAR);
            }

            if (parameters.getBoolean(Params.CONCURRENT_FAS)) {
                search.setConcurrent(PcAll.Concurrent.YES);
            } else {
                search.setConcurrent(PcAll.Concurrent.NO);
            }

            search.setColliderDiscovery(colliderDiscovery);
            search.setConflictRule(conflictRule);
            search.setUseHeuristic(parameters.getBoolean(Params.USE_MAX_P_ORIENTATION_HEURISTIC));
            search.setMaxPathLength(parameters.getInt(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            final PC pcAll = new PC(this.test);

            final DataSet data = (DataSet) dataSet;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, pcAll, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

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
    public Graph getComparisonGraph(final Graph graph) {
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PC using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.CONCURRENT_FAS);
//        parameters.add(Params.COLLIDER_DISCOVERY_RULE);
        parameters.add(Params.CONFLICT_RULE);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FAS_HEURISTIC);
        parameters.add(Params.USE_MAX_P_ORIENTATION_HEURISTIC);
        parameters.add(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(final IndependenceWrapper test) {
        this.test = test;
    }

}