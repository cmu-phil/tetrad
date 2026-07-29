package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Algcomparison wrapper for the CD-NOD-BOSS search
 * ({@link edu.cmu.tetrad.search.CdnodBoss}): a BOSS backbone with
 * constraint-based collider orientation applied only to unshielded triples
 * that BOSS leaves fully undirected, followed by Meek closure.
 *
 * <p>Context variables are the Tier-0 variables of the supplied
 * {@link Knowledge}; the search forbids edges into contexts during the BOSS
 * run and forces Context → X for adjacent non-context variables. The
 * constraint-based step requires both a {@link ScoreWrapper} (for the BOSS
 * backbone) and an {@link IndependenceWrapper} (for sepset derivation), so
 * this wrapper takes both. A tabular {@link DataSet} is required; covariance
 * matrices alone are not sufficient for the CI step in general.</p>
 *
 * <p>Non-standard parameter keys (string keys for now; promote to
 * {@link Params} when the algorithm stabilizes):</p>
 * <ul>
 *   <li>{@link #CDNOD_COLLIDER_STYLE}: 1 = SEPSETS, 2 = CONSERVATIVE,
 *       3 = MAX_P (default 1).</li>
 *   <li>{@link #CDNOD_MAX_P_MARGIN}: tie-guard margin for MAX_P
 *       (default 0.0).</li>
 *   <li>{@link #CDNOD_EXCLUDE_CONTEXTS_FROM_S}: if true, contexts are
 *       excluded from conditioning sets in the collider step, uniformly
 *       (default false, per Huang et al. 2020).</li>
 * </ul>
 *
 * @see edu.cmu.tetrad.search.CdnodBoss
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD-BOSS",
        command = "cd-nod-boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class CdnodBoss extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper,
        TakesIndependenceWrapper, AcceptsKnowledge, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Parameter key selecting the collider orientation style for triples BOSS leaves unresolved:
     * 1 = SEPSETS, 2 = CONSERVATIVE, 3 = MAX_P. Default 1.
     */
    public static final String CDNOD_COLLIDER_STYLE = "cdnodColliderStyle";

    /**
     * Parameter key for the MAX_P tie-guard margin. Default 0.0 (classic MAX-P).
     */
    public static final String CDNOD_MAX_P_MARGIN = "cdnodMaxPMargin";

    /**
     * Parameter key: if true, context variables are excluded from conditioning sets in the
     * collider-orientation step, uniformly across styles (including RecursiveBlocking hints).
     * Default false (contexts admissible, per Huang et al. 2020).
     */
    public static final String CDNOD_EXCLUDE_CONTEXTS_FROM_S = "cdnodExcludeContextsFromS";

    /**
     * The score wrapper supplying the score for the BOSS backbone.
     */
    private ScoreWrapper score;

    /**
     * The independence wrapper supplying the CI test for sepset derivation in the
     * collider-orientation step.
     */
    private IndependenceWrapper test;

    /**
     * Background knowledge; Tier-0 variables are treated as context variables.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Default constructor. Score and test wrappers must be supplied before use.
     */
    public CdnodBoss() {
    }

    /**
     * Constructs the wrapper with a score wrapper for the BOSS backbone.
     *
     * @param score the score wrapper.
     */
    public CdnodBoss(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Constructs the wrapper with both a score wrapper (for the BOSS backbone) and an
     * independence wrapper (for the collider-orientation step).
     *
     * @param score the score wrapper.
     * @param test  the independence wrapper.
     */
    public CdnodBoss(ScoreWrapper score, IndependenceWrapper test) {
        this.score = score;
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet raw)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }
            DataSet timeSeries = TsUtils.createLagData(raw, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataModel.getName() != null) timeSeries.setName(dataModel.getName());
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("CD-NOD-BOSS requires a tabular DataSet; " +
                    "the collider-orientation step needs raw data for its CI tests.");
        }

        IndependenceTest ciTest = this.test.getTest(dataModel, parameters);
        Score myScore = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.CdnodBoss.ColliderOrientationStyle style =
                switch (parameters.getInt(CDNOD_COLLIDER_STYLE, 1)) {
                    case 2 -> edu.cmu.tetrad.search.CdnodBoss.ColliderOrientationStyle.CONSERVATIVE;
                    case 3 -> edu.cmu.tetrad.search.CdnodBoss.ColliderOrientationStyle.MAX_P;
                    default -> edu.cmu.tetrad.search.CdnodBoss.ColliderOrientationStyle.SEPSETS;
                };

        edu.cmu.tetrad.search.CdnodBoss search = new edu.cmu.tetrad.search.CdnodBoss.Builder()
                .test(ciTest)
                .data(dataSet)
                .score(myScore)
                .knowledge(this.knowledge)
                .colliderStyle(style)
                .depth(parameters.getInt(Params.DEPTH))
                .maxPMargin(parameters.getDouble(CDNOD_MAX_P_MARGIN, 0.0))
                .excludeContextsFromS(parameters.getBoolean(CDNOD_EXCLUDE_CONTEXTS_FROM_S, false))
                .useBes(parameters.getBoolean(Params.USE_BES))
                .numStarts(parameters.getInt(Params.NUM_STARTS))
                .numThreads(parameters.getInt(Params.NUM_THREADS))
                .useDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER))
                .seed(parameters.getLong(Params.SEED))
                .verbose(parameters.getBoolean(Params.VERBOSE))
                .build();

        Graph g;
        try {
            g = search.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        LogUtilsSearch.stampWithBic(g, dataModel);
        return g;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Compares against the CPDAG of the true DAG. Note this is slightly conservative for
     * CD-NOD: the algorithm additionally orients Context → X edges that a plain CPDAG may leave
     * undirected, so those orientations are not credited by this comparison graph.</p>
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(graph);
    }

    @Override
    public String getDescription() {
        return "CD-NOD-BOSS: BOSS backbone with CI collider orientation on unresolved triples, using "
                + this.score.getDescription() + " and " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

//         BOSS backbone
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.NUM_THREADS);
        params.add(Params.USE_DATA_ORDER);

        // Collider orientation
        params.add(Params.DEPTH);
        params.add(CDNOD_COLLIDER_STYLE);
        params.add(CDNOD_MAX_P_MARGIN);
        params.add(CDNOD_EXCLUDE_CONTEXTS_FROM_S);

        // General
        params.add(Params.TIME_LAG);
//        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        return params;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
