package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.LinkedList;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
/*@AlgorithmDescription(
        name = "CCD_MAX",
        algType = AlgType.forbid_latent_common_causes,
        oracleType = OracleType.Test
)*/
@Bootstrapping
public class CcdMax implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public CcdMax(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            IndependenceTest test = this.test.getTest(dataSet, parameters);
            edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
            search.setDoColliderOrientations(parameters.getBoolean(Params.DO_COLLIDER_ORIENTATION));
            search.setUseHeuristic(parameters.getBoolean(Params.USE_MAX_P_ORIENTATION_HEURISTIC));
            search.setMaxPathLength(parameters.getInt(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH));
            search.setKnowledge(this.knowledge);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setApplyOrientAwayFromCollider(parameters.getBoolean(Params.APPLY_R1));
            search.setUseOrientTowardDConnections(parameters.getBoolean(Params.ORIENT_TOWARD_DCONNECTIONS));
            return search.search();
        } else {
            CcdMax algorithm = new CcdMax(this.test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CCD-Max (Cyclic Discovery Search Max) using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        if (this.test != null) {
            parameters.addAll(this.test.getParameters());
        }
        parameters.add(Params.DEPTH);
        parameters.add(Params.ORIENT_VISIBLE_FEEDBACK_LOOPS);
        parameters.add(Params.DO_COLLIDER_ORIENTATION);
        parameters.add(Params.USE_MAX_P_ORIENTATION_HEURISTIC);
        parameters.add(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH);
        parameters.add(Params.APPLY_R1);
        parameters.add(Params.ORIENT_TOWARD_DCONNECTIONS);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
