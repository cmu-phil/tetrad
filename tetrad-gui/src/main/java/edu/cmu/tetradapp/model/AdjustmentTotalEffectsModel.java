package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.search.RecursiveAdjustmentMultiple;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model for the {@code "Adjustment & Total Effects"} regression tool.
 *
 * <p>This model:</p>
 *
 * <ul>
 *   <li>Takes a graph and a dataset.</li>
 *   <li>Accepts treatments <i>X</i> and outcomes <i>Y</i> as sets of nodes.</li>
 *   <li>Has two modes:
 *     <ol>
 *       <li><b>PAIRWISE</b>: For all (x, y) in X&times;Y, runs
 *           {@code RecursiveAdjustment} (single pair), then regression,
 *           producing one {@code ResultRow} per (x, y, Z).</li>
 *       <li><b>JOINT</b>: Treats X and Y as joint sets, runs
 *           {@code RecursiveAdjustmentMultiple(X, Y)}, then for each adjustment set Z
 *           and each y in Y, runs regression with all X.</li>
 *     </ol>
 *   </li>
 *   <li>Stores the regression coefficients for X on y | Z as “total effects”.</li>
 * </ul>
 */
public final class AdjustmentTotalEffectsModel implements SessionModel, GraphSource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final DataSet dataSet;
    private final Graph graph;
    // Selected treatment(s) and outcome(s)
    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();
    private final Parameters parameters;
    private final DataWrapper dataModel;
    private final GraphSource graphSource;
    // Computed results
    private final List<ResultRow> results = new ArrayList<>();
    private String name = "";
    // RA parameters (defaults are reasonable starting points)
    private String graphType = "PDAG";
    private int maxNumSets = 20;
    private int maxRadius = -1;           // <0 means "no radius limit"
    private int nearWhichEndpoint = 1;    // 0 = source, 1 = target, else min
    private int maxPathLength = -1;       // <0 means "unbounded"
    private boolean avoidAmenable = true;
    private Set<Node> notFollowed = Collections.emptySet();
    private Set<Node> containing = Collections.emptySet();

    // Mode: pairwise vs joint
    private EffectMode effectMode = EffectMode.PAIRWISE;
    private boolean doDiscreteRegressions = false;
    private String treatmentsText = "";
    private String outcomesText = "";

    /**
     * Constructs an instance of the AdjustmentTotalEffectsModel.
     *
     * @param dataModel   the data wrapper containing the data model list
     * @param graphSource the source of the graph used in the computations
     * @param parameters  the parameters required for the adjustment and total effects analysis
     */
    public AdjustmentTotalEffectsModel(DataWrapper dataModel,
                                       GraphSource graphSource,
                                       Parameters parameters) {
        this.dataSet = (DataSet) Objects.requireNonNull(dataModel)
                .getDataModelList().getFirst();
        this.graph = GraphUtils.replaceNodes(Objects.requireNonNull(graphSource).getGraph(), dataSet.getVariables());
        this.parameters = Objects.requireNonNull(parameters);

        boolean containsCircle = false;

        for (Edge edge : graph.getEdges()) {
            if (edge.getEndpoint(edge.getNode1()) == Endpoint.CIRCLE || edge.getEndpoint(edge.getNode2()) == Endpoint.CIRCLE) {
                containsCircle = true;
                break;
            }
        }

        this.graphType = containsCircle ? "PAG" : "PDAG";

        this.dataModel = dataModel;
        this.graphSource = graphSource;
    }

    /**
     * Returns the dataset used in the analysis.
     *
     * @return the dataset
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    // ---------------------------------------------------------------------
    // Configuration API
    // ---------------------------------------------------------------------

    /**
     * Returns the graph used in the analysis.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Returns the set of treatment nodes.
     *
     * @return the set of treatment nodes
     */
    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    /**
     * Sets the set of treatment nodes.
     *
     * @param nodes the set of treatment nodes
     */
    public void setX(Collection<Node> nodes) {
        X.clear();
        if (nodes != null) X.addAll(nodes);
    }

    /**
     * Returns the set of outcome nodes.
     *
     * @return the set of outcome nodes
     */
    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    /**
     * Sets the set of outcome nodes.
     *
     * @param nodes the set of outcome nodes
     */
    public void setY(Collection<Node> nodes) {
        Y.clear();
        if (nodes != null) Y.addAll(nodes);
    }

    /**
     * Returns the type of graph used in the analysis.
     *
     * @return the graph type
     */
    public String getGraphType() {
        return graphType;
    }

    /**
     * Sets the type of graph used in the analysis.
     *
     * @param graphType the graph type
     */
    public void setGraphType(String graphType) {
        this.graphType = (graphType == null) ? "PDAG" : graphType;
    }

    /**
     * Returns the maximum number of adjustment sets.
     *
     * @return the maximum number of adjustment sets
     */
    public int getMaxNumSets() {
        return maxNumSets;
    }

    /**
     * Sets the maximum number of adjustment sets.
     *
     * @param maxNumSets the maximum number of adjustment sets
     */
    public void setMaxNumSets(int maxNumSets) {
        this.maxNumSets = maxNumSets;
    }

    /**
     * Returns the maximum radius for adjustment sets.
     *
     * @return the maximum radius
     */
    public int getMaxRadius() {
        return maxRadius;
    }

    /**
     * Sets the maximum radius for adjustment sets.
     *
     * @param maxRadius the maximum radius
     */
    public void setMaxRadius(int maxRadius) {
        this.maxRadius = maxRadius;
    }

    /**
     * Returns the endpoint to which the adjustment sets should be near.
     *
     * @return the endpoint
     */
    public int getNearWhichEndpoint() {
        return nearWhichEndpoint;
    }

    /**
     * Sets the endpoint to which the adjustment sets should be near.
     *
     * @param nearWhichEndpoint the endpoint
     */
    public void setNearWhichEndpoint(int nearWhichEndpoint) {
        this.nearWhichEndpoint = nearWhichEndpoint;
    }

    /**
     * Returns the maximum path length for adjustment sets.
     *
     * @return the maximum path length
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * Sets the maximum path length for adjustment sets.
     *
     * @param maxPathLength the maximum path length
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * Returns whether to avoid amenability.
     *
     * @return true if avoiding amenability, false otherwise
     */
    public boolean isAvoidAmenable() {
        return avoidAmenable;
    }

    /**
     * Sets whether to avoid amenability.
     *
     * @param avoidAmenable true to avoid amenability, false otherwise
     */
    public void setAvoidAmenable(boolean avoidAmenable) {
        this.avoidAmenable = avoidAmenable;
    }

    /**
     * Returns the set of nodes that should not be followed in adjustment sets.
     *
     * @return the set of not followed nodes
     */
    public Set<Node> getNotFollowed() {
        return notFollowed;
    }

    /**
     * Sets the set of nodes that should not be followed in adjustment sets.
     *
     * @param notFollowed the set of not followed nodes
     */
    public void setNotFollowed(Set<Node> notFollowed) {
        this.notFollowed = (notFollowed == null)
                ? Collections.emptySet()
                : new LinkedHashSet<>(notFollowed);
    }

    /**
     * Returns the set of nodes that should be contained in adjustment sets.
     *
     * @return the set of containing nodes
     */
    public Set<Node> getContaining() {
        return containing;
    }

    /**
     * Sets the set of nodes that should be contained in adjustment sets.
     *
     * @param containing the set of containing nodes
     */
    public void setContaining(Set<Node> containing) {
        this.containing = (containing == null)
                ? Collections.emptySet()
                : new LinkedHashSet<>(containing);
    }

    /**
     * Returns the effect mode for the analysis.
     *
     * @return the effect mode
     */
    public EffectMode getEffectMode() {
        return effectMode;
    }

    /**
     * Sets the effect mode for the analysis.
     *
     * @param mode the effect mode
     */
    public void setEffectMode(EffectMode mode) {
        this.effectMode = Objects.requireNonNull(mode);
    }

    /**
     * Returns an unmodifiable list of result rows from the analysis.
     *
     * @return an unmodifiable list of results
     */
    public List<ResultRow> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns a result row from the analysis at the specified index.
     *
     * @param rowIndex the index of the result row
     * @return the result row at the specified index
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public ResultRow getResultRow(int rowIndex) {
        return results.get(rowIndex);
    }

    /**
     * Recomputes adjustment sets and total effects for the current X, Y, mode, and parameters.
     *
     * <p><b>PAIRWISE mode</b>:</p>
     * <ul>
     *   <li>For each x in X and y in Y:
     *     <ul>
     *       <li>Find adjustment sets via {@code RecursiveAdjustment(x, y)}.</li>
     *       <li>For each adjustment set Z, regress y on {@code {x} ∪ Z}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><b>JOINT mode</b>:</p>
     * <ul>
     *   <li>For the joint sets X and Y:
     *     <ul>
     *       <li>Find adjustment sets via {@code RecursiveAdjustmentMultiple(X, Y)}.</li>
     *       <li>For each adjustment set Z and each y in Y, regress y on {@code X ∪ Z}.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    // inside AdjustmentTotalEffectsModel
    public void recompute() {
        results.clear();

        if (effectMode == EffectMode.PAIRWISE) {
            for (Node x : X) {
                for (Node y : Y) {
                    if (x.equals(y)) continue;

                    // If discrete regressions are NOT allowed and x or y is discrete, show "(Discrete)" immediately.
                    boolean discrete0 = !doDiscreteRegressions && involvesDiscrete(Collections.singleton(x), y, Collections.emptySet());

                    // Compute adjustment sets. Empty list => not amenable (by your current RA contract).
                    List<Set<Node>> zSets = computeSinglePairAdjustmentSets(x, y);
                    boolean amenable = graph.paths().isGraphAmenable(x, y, graphType, maxPathLength, Set.of());
                    Set<List<Node>> pdPaths = graph.paths().potentiallyDirectedPaths(x, y, maxPathLength);

                    if (discrete0) {
                        results.add(new ResultRow(
                                Collections.singleton(x),
                                Collections.singleton(y),
                                Collections.emptySet(),
                                amenable,   // notAmenable
                                discrete0,    // discreteRegression == "skip due to discrete"
                                null,
                                null
                        ));
                        continue;
                    }

                    if (!amenable) {
                        results.add(new ResultRow(
                                Collections.singleton(x),
                                Collections.singleton(y),
                                Collections.emptySet(),
                                amenable,
                                discrete0,
                                null,
                                null
                        ));
                        continue;
                    }

                    if (pdPaths.isEmpty()) {
                        results.add(new ResultRow(
                                Collections.singleton(x),
                                Collections.singleton(y),
                                null,
                                amenable,
                                discrete0,
                                new double[]{0.0},
                                null
                        ));
                        continue;
                    }

                    // One row per adjustment set
                    for (Set<Node> z : zSets) {
                        LinkedHashSet<Node> zClean = new LinkedHashSet<>(z);
                        zClean.remove(x); // just in case

                        // If discrete regressions are NOT allowed and Z contains discrete vars, show "(Discrete)" row.
                        boolean discrete = !doDiscreteRegressions && involvesDiscrete(Collections.singleton(x), y, zClean);
                        if (discrete) {
                            results.add(new ResultRow(
                                    Collections.singleton(x),
                                    Collections.singleton(y),
                                    zClean,
                                    amenable,
                                    discrete,
                                    null,
                                    null
                            ));
                            continue;
                        }

                        // Otherwise run regression
                        ResultRow rr = runRegressionFor(Collections.singleton(x), y, zClean);
                        results.add(rr);
                    }
                }
            }
            return;
        } else {
            // JOINT mode: treat X as a set, but compute adjustment sets separately for each outcome y.
            for (Node y : Y) {

                // (1) Compute adjustment sets for this specific outcome.
                List<Set<Node>> zSetsForY = computeJointAdjustmentSets(X, Collections.singleton(y));

                // (2) If none, emit a placeholder row so the user sees "(Not amenable)" for this y.
                if (zSetsForY.isEmpty()) {
                    results.add(new ResultRow(
                            new LinkedHashSet<>(X),
                            new LinkedHashSet<>(Collections.singleton(y)),
                            Collections.emptySet(),
                            true,   // notAmenable
                            false,  // discreteRegression / skipDueToDiscrete
                            null,
                            null
                    ));
                    continue;
                }

                // (3) Otherwise, emit one row per Z for this y.
                for (Set<Node> Z : zSetsForY) {
                    LinkedHashSet<Node> zClean = new LinkedHashSet<>(Z);
                    zClean.removeAll(X); // defensive, avoid duplication

                    // If discrete regressions are NOT allowed and any variable in X ∪ {y} ∪ Z is discrete,
                    // emit a "(Discrete)" row and do NOT run regression.
                    if (!doDiscreteRegressions && involvesDiscrete(X, y, zClean)) {
                        results.add(new ResultRow(
                                new LinkedHashSet<>(X),
                                new LinkedHashSet<>(Collections.singleton(y)),
                                zClean,
                                false,  // notAmenable
                                true,   // discreteRegression == skipDueToDiscrete
                                null,
                                null
                        ));
                        continue;
                    }

                    // Otherwise run regression and add the computed row.
                    results.add(runRegressionFor(X, y, zClean));
                }
            }
        }
    }

    private boolean involvesDiscrete(Collection<Node> Xset, Node y, Set<Node> Z) {
        for (Node x : Xset) {
            if (dataSet.getVariable(x.getName()) instanceof DiscreteVariable) return true;
        }
        if (dataSet.getVariable(y.getName()) instanceof DiscreteVariable) return true;

        for (Node z : Z) {
            if (dataSet.getVariable(z.getName()) instanceof DiscreteVariable) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Core computation
    // ---------------------------------------------------------------------

    /**
     * Single-pair RecursiveAdjustment for (x, y).
     *
     * @param x the node x
     * @param y the node y
     * @return the adjustment sets for (x, y)
     */
    private List<Set<Node>> computeSinglePairAdjustmentSets(Node x, Node y) {
        RecursiveAdjustment ra = new RecursiveAdjustment(graph);
        ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
        ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

        boolean graphAmenable =
                graph.paths().isGraphAmenable(x, y, graphType, maxPathLength, Set.of());

        if (!graphAmenable) {
            return Collections.emptyList();
        }

        List<Set<Node>> adjustmentSets = ra.adjustmentSets(
                x, y,
                graphType,
                maxNumSets,
                maxRadius,
                nearWhichEndpoint,
                maxPathLength,
                RecursiveAdjustment.ColliderPolicy.OFF,
                avoidAmenable,
                notFollowed,
                containing,
                Set.of()
        );

        return adjustmentSets;
    }

    // ---------------------------------------------------------------------
    // Adjustment set calculation
    // ---------------------------------------------------------------------

    /**
     * Joint adjustment sets for (X, Y). Delegates to RecursiveAdjustmentMultiple(X, Y), except that if |X|=|Y|=1 it
     * falls back to computeSinglePairAdjustmentSets.
     *
     * @param Xset the set of nodes X
     * @param Yset the set of nodes Y
     * @return the adjustment sets for (X, Y)
     */
    private List<Set<Node>> computeJointAdjustmentSets(Set<Node> Xset, Set<Node> Yset) {
        if (Xset.size() == 1 && Yset.size() == 1) {
            Node x = Xset.iterator().next();
            Node y = Yset.iterator().next();
            return computeSinglePairAdjustmentSets(x, y);
        }

        RecursiveAdjustmentMultiple ra = new RecursiveAdjustmentMultiple(graph);
        ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
        ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

        return ra.adjustmentSets(
                Xset, Yset,
                graphType,
                maxNumSets,
                maxRadius,
                nearWhichEndpoint,
                maxPathLength,
                avoidAmenable,
                notFollowed,
                containing
        );
    }

    /**
     * Runs a linear regression for outcome {@code y} on {@code Xset ∪ Z}, and extracts total effects (the regression
     * coefficients for all variables in {@code Xset}).
     *
     * <p>Each result row corresponds to:</p>
     * <ul>
     *   <li>The X-set used in this regression (a singleton in PAIRWISE mode).</li>
     *   <li>The specific outcome variable {@code y}.</li>
     *   <li>The specific adjustment set {@code Z}.</li>
     * </ul>
     */
    private ResultRow runRegressionFor(Collection<Node> Xset, Node y, Set<Node> Z) {
        LinkedHashSet<Node> Xlinked = new LinkedHashSet<>(Xset);

        LinkedHashSet<Node> Zclean = new LinkedHashSet<>(Z);
        Zclean.removeAll(Xlinked);

        List<Node> regressors = new ArrayList<>(Xlinked.size() + Zclean.size());
        regressors.addAll(Xlinked);
        regressors.addAll(Zclean);

        Regression regression = new RegressionDataset(dataSet);
        RegressionResult result = regression.regress(y, regressors);

        double[] coeffs = result.getCoef();
        double[] betas = new double[Xlinked.size()];

        int i = 0;
        for (Node x : Xlinked) {
            int coefIndex = i + 1; // intercept at 0, X's come first
            betas[i] = (coefIndex >= 0 && coefIndex < coeffs.length) ? coeffs[coefIndex] : Double.NaN;
            i++;
        }

        return new ResultRow(
                Xlinked,
                new LinkedHashSet<>(Collections.singleton(y)),
                Zclean,
                true,
                false, // discreteRegression == skipDueToDiscrete
                betas,
                result
        );
    }

    // ---------------------------------------------------------------------
    // Regression
    // ---------------------------------------------------------------------

    /**
     * Returns the name of the model.
     *
     * @return the name of the model
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     */
    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        this.name = name;
    }

    public boolean getDoDiscreteRegressions() {
        return this.doDiscreteRegressions;
    }

    public void setDoDiscreteRegressions(boolean selected) {
        this.doDiscreteRegressions = selected;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public String getTreatmentsText() {
        return treatmentsText;
    }

    public void setTreatmentsText(String treatmentsText) {
        if (treatmentsText == null) {
            throw new IllegalArgumentException("Treatments text cannot be null");
        }
        this.treatmentsText = treatmentsText;
    }

    public String getOutcomesText() {
        return outcomesText;
    }

    public void setOutcomesText(String outcomesText) {
        if (outcomesText == null) {
            throw new IllegalArgumentException("Outcomes text cannot be null");
        }
        this.outcomesText = outcomesText;
    }

    public enum EffectMode {
        PAIRWISE,  // total effects for all (x, y) ∈ X×Y using RecursiveAdjustment
        JOINT      // joint intervention p(Y | do(X)) using RecursiveAdjustmentMultiple
    }

    // ---------------------------------------------------------------------
    // Result row
    // ---------------------------------------------------------------------

    /**
     * Represents a result row from the adjustment total effects analysis.
     */
    public static final class ResultRow implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        public final Set<Node> X;
        public final Set<Node> Y;

        /**
         * The chosen adjustment set for this row (may be empty if amenable with empty adjustment).
         */
        public final Set<Node> Z;

        /**
         * True iff an adjustment set exists (RA returned a nonempty list).
         */
        public final boolean amenable;

        /**
         * True iff the regression involves a discrete variable
         */
        public final boolean discreteRegression;

        /**
         * Per-X betas from the regression (null if not computed).
         */
        public final double[] betas;

        /**
         * Full regression result (null if not computed).
         */
        public final RegressionResult regressionResult;

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

        private static String formatSet(Set<Node> s) {
            if (s == null) throw new IllegalArgumentException("Set cannot be null");
            if (s.isEmpty()) return "∅";
            return s.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
        }

        public String formatXSet() {
            return formatSet(X);
        }

        public String formatYSet() {
            return formatSet(Y);
        }

        public String formatZSet() {
            if (discreteRegression) return "(Discrete)";
            else if (!amenable) return "(Not amenable)";
                // Distinguish “amenable with empty adjustment set”
            else if (Z == null) return "-";
            else if (Z.isEmpty()) return "∅";
            else return formatSet(Z);
        }
    }
}