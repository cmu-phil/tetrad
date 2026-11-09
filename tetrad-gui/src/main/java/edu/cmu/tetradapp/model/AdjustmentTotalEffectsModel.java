package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The session model behind the Linear Adjustment Effects box in Tetrad 7.6.10, now called
 * {@link LinearAdjustmentTotalEffectsModel}.
 * <p>
 * <b>Backward compatibility only.</b> Sessions are saved by plain Java serialization, and
 * {@code SessionNode} stores the model instance together with {@code Class[] modelClasses} and
 * {@code Class lastModelClass}. A missing model class therefore makes the entire saved session fail to
 * open, not merely the box that used it. This class is retained at its original name, with the original
 * {@code serialVersionUID} of 1L and the original field names and types, so that the stream binds.
 * <p>
 * It is not registered in {@code tetradConfig.xml}, so it is not offered as a box. On deserialization,
 * {@link #readResolve()} replaces the restored instance with an equivalent
 * {@link LinearAdjustmentTotalEffectsModel}, which is registered and so has an editor; the session
 * therefore comes back as the current box, with its settings carried over.
 * <p>
 * The two nested types are kept because the stream contains them: {@code EffectMode} as the value of the
 * {@code effectMode} field, and {@code ResultRow} as the element type of {@code results}. Removing either
 * would fail the read even with this class present. Computed results are not carried over, since the
 * current box recomputes them; the stored settings are what matter.
 * <p>
 * If the restored state is too incomplete to build the current model — a null data wrapper, graph source,
 * or parameters, or a node set the current model rejects — {@code readResolve} logs and returns this
 * instance instead of throwing, so that the session still opens. Such a box will have no editor, which is
 * a worse outcome than a converted box but a much better one than an unopenable session.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see LinearAdjustmentTotalEffectsModel
 */
public final class AdjustmentTotalEffectsModel implements SessionModel, GraphSource, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The data set the box was built on.
     */
    private DataSet dataSet;

    /**
     * The graph the box was built on.
     */
    private Graph graph;

    /**
     * The selected treatments.
     */
    private LinkedHashSet<Node> X = new LinkedHashSet<>();

    /**
     * The selected outcomes.
     */
    private LinkedHashSet<Node> Y = new LinkedHashSet<>();

    /**
     * The parameters.
     */
    private Parameters parameters;

    /**
     * The upstream data wrapper.
     */
    private DataWrapper dataModel;

    /**
     * The upstream graph source.
     */
    private GraphSource graphSource;

    /**
     * The computed result rows. Not carried over on conversion; the current model recomputes them.
     */
    private List<ResultRow> results = new ArrayList<>();

    /**
     * The true SEM IM, where the data came from a simulation.
     */
    private SemIm trueSemIm;

    /**
     * The name of this box.
     */
    private String name = "";

    /**
     * Either "PDAG" or "PAG".
     */
    private String graphType = "PDAG";

    /**
     * The maximum number of adjustment sets to report.
     */
    private int maxNumSets = 20;

    /**
     * The maximum radius; negative means no limit.
     */
    private int maxRadius = -1;

    /**
     * 0 = source, 1 = target, otherwise minimum.
     */
    private int nearWhichEndpoint = 1;

    /**
     * The maximum path length; negative means unbounded.
     */
    private int maxPathLength = -1;

    /**
     * Whether non-amenable pairs are avoided.
     */
    private boolean avoidAmenable = true;

    /**
     * Nodes not to be followed.
     */
    private Set<Node> notFollowed = Collections.emptySet();

    /**
     * Nodes required to be contained.
     */
    private Set<Node> containing = Collections.emptySet();

    /**
     * Pairwise or joint effects.
     */
    private EffectMode effectMode = EffectMode.PAIRWISE;

    /**
     * Whether discrete regressions are performed.
     */
    private boolean doDiscreteRegressions = false;

    /**
     * The treatments text.
     */
    private String treatmentsText = "*";

    /**
     * The outcomes text.
     */
    private String outcomesText = "*";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * Replaces this restored instance with an equivalent {@link LinearAdjustmentTotalEffectsModel}, so that
     * a session saved by 7.6.10 comes back as the current box.
     *
     * @return the equivalent current model, or this instance if one cannot be built
     * @throws ObjectStreamException never thrown; declared to match the serialization contract
     */
    @Serial
    private Object readResolve() throws ObjectStreamException {
        if (this.dataModel == null || this.graphSource == null || this.parameters == null) {
            TetradLogger.getInstance().log("Restored a Tetrad 7.6.10 Linear Adjustment Effects box with "
                                           + "incomplete state; leaving it unconverted.");
            return this;
        }

        try {
            LinearAdjustmentTotalEffectsModel converted =
                    new LinearAdjustmentTotalEffectsModel(this.dataModel, this.graphSource, this.parameters);

            converted.setName(this.name == null ? "" : this.name);

            if (this.graphType != null) {
                converted.setGraphType(this.graphType);
            }

            converted.setMaxNumSets(this.maxNumSets);
            converted.setMaxRadius(this.maxRadius);
            converted.setNearWhichEndpoint(this.nearWhichEndpoint);
            converted.setMaxPathLength(this.maxPathLength);
            converted.setAvoidAmenable(this.avoidAmenable);
            converted.setDoDiscreteRegressions(this.doDiscreteRegressions);

            if (this.treatmentsText != null) {
                converted.setTreatmentsText(this.treatmentsText);
            }

            if (this.outcomesText != null) {
                converted.setOutcomesText(this.outcomesText);
            }

            if (this.notFollowed != null) {
                converted.setNotFollowed(this.notFollowed);
            }

            if (this.containing != null) {
                converted.setContaining(this.containing);
            }

            if (this.effectMode != null) {
                converted.setEffectMode(
                        LinearAdjustmentTotalEffectsModel.EffectMode.valueOf(this.effectMode.name()));
            }

            if (this.X != null && !this.X.isEmpty()) {
                converted.setX(this.X);
            }

            if (this.Y != null && !this.Y.isEmpty()) {
                converted.setY(this.Y);
            }

            return converted;
        } catch (Exception e) {
            TetradLogger.getInstance().log("Could not convert a Tetrad 7.6.10 Linear Adjustment Effects box to "
                                           + "the current model (" + e.getMessage() + "); leaving it unconverted.");
            return this;
        }
    }

    /**
     * Whether effects are computed pairwise or jointly. Retained because the stream stores the
     * {@code effectMode} field as a constant of this enum.
     */
    public enum EffectMode {

        /**
         * Total effects for all (x, y) in X × Y, computed pairwise.
         */
        PAIRWISE,

        /**
         * Joint effects, conditioning on all of X jointly.
         */
        JOINT
    }

    /**
     * A result row. Retained because the stream stores the {@code results} field as a list of these.
     */
    public static final class ResultRow implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The treatments.
         */
        public final Set<Node> X;

        /**
         * The outcomes.
         */
        public final Set<Node> Y;

        /**
         * The chosen adjustment set, possibly empty.
         */
        public final Set<Node> Z;

        /**
         * Whether an adjustment set exists.
         */
        public final boolean amenable;

        /**
         * Whether the regression involves a discrete variable.
         */
        public final boolean discreteRegression;

        /**
         * Per-treatment betas, null if not computed.
         */
        public final double[] betas;

        /**
         * The full regression result, null if not computed.
         */
        public final RegressionResult regressionResult;

        /**
         * Constructs a result row.
         *
         * @param X                  the treatments
         * @param Y                  the outcomes
         * @param Z                  the adjustment set
         * @param amenable           whether an adjustment set exists
         * @param discreteRegression whether a discrete variable is involved
         * @param betas              the betas, possibly null
         * @param regressionResult   the regression result, possibly null
         */
        public ResultRow(Set<Node> X,
                         Set<Node> Y,
                         Set<Node> Z,
                         boolean amenable,
                         boolean discreteRegression,
                         double[] betas,
                         RegressionResult regressionResult) {
            this.X = (X == null) ? null : new LinkedHashSet<>(X);
            this.Y = (Y == null) ? null : new LinkedHashSet<>(Y);
            this.Z = (Z == null) ? null : new LinkedHashSet<>(Z);
            this.amenable = amenable;
            this.discreteRegression = discreteRegression;
            this.betas = betas;
            this.regressionResult = regressionResult;
        }
    }
}
