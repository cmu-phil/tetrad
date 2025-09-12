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
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Peter/Clark algorithm (PC).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC",
        command = "pc",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Pc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
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
     * <p>Constructor for Pc.</p>
     */
    public Pc() {
    }

    /**
     * <p>Constructor for Pc.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public Pc(IndependenceWrapper test) {
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

        boolean allowBidirected = parameters.getBoolean(Params.ALLOW_BIDIRECTED);

        edu.cmu.tetrad.search.Pc.ColliderOrientationStyle colliderOrientationStyle = switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
            case 1 -> edu.cmu.tetrad.search.Pc.ColliderOrientationStyle.SEPSETS;
            case 2 -> edu.cmu.tetrad.search.Pc.ColliderOrientationStyle.CONSERVATIVE;
            case 3 -> edu.cmu.tetrad.search.Pc.ColliderOrientationStyle.MAX_P;
            default -> throw new IllegalArgumentException("Invalid collider orientation style");
        };

        IndependenceTest test = getIndependenceWrapper().getTest(dataModel, parameters);
        test.setVerbose(true);





        Graph graph;

        double fdr_q = parameters.getDouble(Params.FDR_Q);

        if (fdr_q == 0.0) {
            edu.cmu.tetrad.search.Pc search = new edu.cmu.tetrad.search.Pc(test);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(this.knowledge);
            search.setFasStable(parameters.getBoolean(Params.STABLE_FAS));
            search.setColliderOrientationStyle(colliderOrientationStyle);
            search.setAllowBidirected(allowBidirected ? edu.cmu.tetrad.search.Pc.AllowBidirected.ALLOW
                    : edu.cmu.tetrad.search.Pc.AllowBidirected.DISALLOW);

            graph = search.search();
        } else {
            IndTestFdrWrapper wrap = new IndTestFdrWrapper(test, IndTestFdrWrapper.FdrMode.BH,
                    fdr_q, IndTestFdrWrapper.Scope.BY_COND_SET);
            wrap.setVerbose(parameters.getBoolean(Params.VERBOSE));
            wrap.startRecordingEpoch();

            edu.cmu.tetrad.search.Pc search = new edu.cmu.tetrad.search.Pc(wrap);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(this.knowledge);
            search.setFasStable(parameters.getBoolean(Params.STABLE_FAS));
            search.setColliderOrientationStyle(colliderOrientationStyle);
            search.setAllowBidirected(allowBidirected ? edu.cmu.tetrad.search.Pc.AllowBidirected.ALLOW
                    : edu.cmu.tetrad.search.Pc.AllowBidirected.DISALLOW);

            graph = doFdrLoop(search, wrap);
        }

        stampWithBic(graph, dataModel);

        return graph;
    }

    private static Graph doFdrLoop(IGraphSearch search, IndTestFdrWrapper wrap) throws InterruptedException {
        // Epoch 0: record raw p-values under base alpha (or just cache p's)
        wrap.startRecordingEpoch();
        Graph graph = search.search();

        // Freeze FDR cutoffs from the recorded p's
        wrap.computeCutoffsFromRecordedPvals();

        // Decision epochs: now use α* (global or per-|Z|). DO NOT call startRecordingEpoch() here.
        final int maxEpochs = 5;
        final int tauChanges = 0; // stop when <= this many flips

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            graph = search.search();                    // uses FDR α* cutoffs
            int changes = wrap.countMindChangesAndSnapshot();  // counts both true→false and false→true
            if (changes <= tauChanges) break;
        }
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
        return "PC using " + this.test.getDescription();
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
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.ALLOW_BIDIRECTED);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
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
