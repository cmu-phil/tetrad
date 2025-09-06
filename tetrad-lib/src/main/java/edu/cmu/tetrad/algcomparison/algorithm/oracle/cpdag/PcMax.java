package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.ClassicPc;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * PC-Max. This version of PC will orient unshielded colliders similarly to CPC, but instead of listing all subsets S
 * for X--Y--Z, !adj(X, Z), or adj(X) \ {Z} or adj(Z) \ {X} and seeing whether the facts X _||_ Z | S that test as true
 * contain or do not contain Y, we list the p-values for all of these facts and pick the S that maximizes the p-value to
 * do the unshielded collider orientations.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Pc
 * @see Cpc
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC-Max",
        command = "pc-max",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PcMax extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for PcMax.</p>
     */
    public PcMax() {
    }

    /**
     * <p>Constructor for PcMax.</p>
     *
     * @param test a {@link IndependenceWrapper} object
     */
    public PcMax(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        PcCommon.ConflictRule conflictRule = switch (parameters.getInt(Params.CONFLICT_RULE)) {
            case 1 -> PcCommon.ConflictRule.PRIORITIZE_EXISTING;
            case 2 -> PcCommon.ConflictRule.ORIENT_BIDIRECTED;
            case 3 -> PcCommon.ConflictRule.OVERWRITE_EXISTING;
            default ->
                    throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
        };

        edu.cmu.tetrad.search.ClassicPc search = new edu.cmu.tetrad.search.ClassicPc(getIndependenceWrapper().getTest(dataModel, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);
        search.setFasStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setColliderRule(ClassicPc.ColliderRule.MAX_P);
        search.setAllowBidirected(parameters.getBoolean(Params.ALLOW_BIDIRECTED) ? ClassicPc.AllowBidirected.ALLOW : ClassicPc.AllowBidirected.DISALLOW);
        search.setLogMaxPTies(true);
        search.setMaxPGlobalOrder(false);
        Graph graph = search.search();
        stampWithBic(graph, dataModel);

        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "PC-Max using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.ALLOW_BIDIRECTED);
        parameters.add(Params.DEPTH);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

}
