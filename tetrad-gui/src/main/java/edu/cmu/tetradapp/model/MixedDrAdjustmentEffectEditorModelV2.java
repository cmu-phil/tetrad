package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.search.RecursiveAdjustmentMultiple;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetrad.estimate.v1.AdjustmentEffectEstimatorV1;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * v2: Model for "Mixed DR Adjustment Effect" editor.
 *
 * v2 goals (borrowed from LinearAdjustmentTotalEffectsModel):
 *  (1) Pairwise vs Joint modes.
 *  (2) Treatments/outcomes specified by wildcards and lists (parsed in editor, stored here).
 *  (3) Automatically compute adjustment sets and populate the results table.
 *  (4) Keep the main table simple; store richer details per row for "View Details...".
 *
 * v2 constraints:
 *  - One data set + one graph input (graph should be a DAG for now).
 *  - Treatment X must be binary discrete (2 categories; user discretizes).
 *  - Outcome Y must be continuous (not discrete).
 *  - Joint mode supported only for |X| = 1 in v2 (because estimator is binary-X).
 */
public final class MixedDrAdjustmentEffectEditorModelV2 implements SessionModel, GraphSource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // inputs
    private final DataSet dataSet;
    private final Graph graph;
    private final Parameters parameters;

    // (kept like the linear model; useful for future wiring)
    private final DataWrapper dataWrapper;
    private final GraphSource graphSource;

    // selections (set by editor after parsing wildcard/list strings)
    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();

    // UI state
    private String name = "";
    private EffectMode effectMode = EffectMode.PAIRWISE;
    private String treatmentsText = "*";
    private String outcomesText = "*";

    // RA parameters (mirrors linear tool patterns; editor can expose these)
    private String graphType = "DAG";
    private int maxNumSets = 20;
    private int maxRadius = -1;
    private int nearWhichEndpoint = 1;
    private int maxPathLength = -1;
    private boolean avoidAmenable = true;
    private Set<Node> notFollowed = Collections.emptySet();
    private Set<Node> containing = Collections.emptySet();

    // estimator config (v1 estimator under the hood, but v2 tool)
    private final AdjustmentEffectEstimatorV1.ConfigV1 cfg = new AdjustmentEffectEstimatorV1.ConfigV1();

    // results
    private final List<ResultRowV2> results = new ArrayList<>();

    public MixedDrAdjustmentEffectEditorModelV2(DataWrapper dataWrapper,
                                                GraphSource graphSource,
                                                Parameters parameters) {
        this.dataWrapper = Objects.requireNonNull(dataWrapper, "dataWrapper");
        this.graphSource = Objects.requireNonNull(graphSource, "graphSource");
        this.parameters = Objects.requireNonNull(parameters, "parameters");

        // v2: match the linear model convention: first DataModel should be a DataSet
        DataModel dm = dataWrapper.getDataModelList().getFirst();
        if (!(dm instanceof DataSet ds)) {
            throw new IllegalArgumentException("v2: Mixed DR Adjustment Effect requires a tabular DataSet.");
        }
        this.dataSet = ds;

        // v2: replace graph nodes with dataset variables (important for name alignment)
        Graph rawGraph = graphSource.getGraph();
        this.graph = GraphUtils.replaceNodes(rawGraph, dataSet.getVariables());

        // v2: infer graph type string used by RA / RAMultiple
        this.graphType = inferGraphType(this.graph);
    }

    private static String inferGraphType(Graph g) {
        boolean hasCircle = false;
        boolean hasNonDAGEndpoints = false;

        for (Edge e : g.getEdges()) {
            Endpoint e1 = e.getEndpoint1();
            Endpoint e2 = e.getEndpoint2();
            if (e1 == Endpoint.CIRCLE || e2 == Endpoint.CIRCLE) hasCircle = true;

            boolean directed = (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW)
                    || (e1 == Endpoint.ARROW && e2 == Endpoint.TAIL);

            if (!directed) hasNonDAGEndpoints = true;
        }

        if (hasCircle) return "PAG";
        if (!hasNonDAGEndpoints) return "DAG";
        return "PDAG";
    }

    // -----------------------
    // SessionModel
    // -----------------------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null.");
        this.name = name;
    }

    // -----------------------
    // GraphSource
    // -----------------------

    @Override
    public Graph getGraph() {
        return graph;
    }

    // -----------------------
    // Public getters/setters
    // -----------------------

    public DataSet getDataSet() {
        return dataSet;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public EffectMode getEffectMode() {
        return effectMode;
    }

    public void setEffectMode(EffectMode effectMode) {
        this.effectMode = Objects.requireNonNull(effectMode, "effectMode");
    }

    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    public void setX(Collection<Node> nodes) {
        X.clear();
        if (nodes != null) X.addAll(nodes);
    }

    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    public void setY(Collection<Node> nodes) {
        Y.clear();
        if (nodes != null) Y.addAll(nodes);
    }

    public String getTreatmentsText() {
        return treatmentsText;
    }

    public void setTreatmentsText(String treatmentsText) {
        if (treatmentsText == null) throw new IllegalArgumentException("Treatments text cannot be null.");
        this.treatmentsText = treatmentsText;
    }

    public String getOutcomesText() {
        return outcomesText;
    }

    public void setOutcomesText(String outcomesText) {
        if (outcomesText == null) throw new IllegalArgumentException("Outcomes text cannot be null.");
        this.outcomesText = outcomesText;
    }

    // RA knobs (optional to expose in editor)
    public String getGraphType() { return graphType; }
    public void setGraphType(String graphType) { this.graphType = (graphType == null) ? "DAG" : graphType; }
    public int getMaxNumSets() { return maxNumSets; }
    public void setMaxNumSets(int maxNumSets) { this.maxNumSets = maxNumSets; }
    public int getMaxRadius() { return maxRadius; }
    public void setMaxRadius(int maxRadius) { this.maxRadius = maxRadius; }
    public int getNearWhichEndpoint() { return nearWhichEndpoint; }
    public void setNearWhichEndpoint(int nearWhichEndpoint) { this.nearWhichEndpoint = nearWhichEndpoint; }
    public int getMaxPathLength() { return maxPathLength; }
    public void setMaxPathLength(int maxPathLength) { this.maxPathLength = maxPathLength; }
    public boolean isAvoidAmenable() { return avoidAmenable; }
    public void setAvoidAmenable(boolean avoidAmenable) { this.avoidAmenable = avoidAmenable; }
    public Set<Node> getNotFollowed() { return notFollowed; }
    public void setNotFollowed(Set<Node> notFollowed) {
        this.notFollowed = (notFollowed == null) ? Collections.emptySet() : new LinkedHashSet<>(notFollowed);
    }
    public Set<Node> getContaining() { return containing; }
    public void setContaining(Set<Node> containing) {
        this.containing = (containing == null) ? Collections.emptySet() : new LinkedHashSet<>(containing);
    }

    public AdjustmentEffectEstimatorV1.ConfigV1 getCfg() {
        return cfg;
    }

    public List<ResultRowV2> getResults() {
        return Collections.unmodifiableList(results);
    }

    public ResultRowV2 getResultRow(int i) {
        return results.get(i);
    }

    // -----------------------
    // Main computation
    // -----------------------

    public void recompute() {
        class MyWatchedProcess extends WatchedProcess {
            @Override
            public void watch() {
                results.clear();

                if (X.isEmpty() || Y.isEmpty()) return;

                if (effectMode == EffectMode.PAIRWISE) {
                    for (Node x : X) {
                        for (Node y : Y) {
                            if (x.equals(y)) continue;
                            computeRowsForPair(x, y);
                        }
                    }
                } else {
                    // v2: JOINT supported only for |X|=1 (binary estimator)
                    if (X.size() != 1) {
                        for (Node y : Y) {
                            results.add(ResultRowV2.notSupportedJoint(y));
                        }
                        return;
                    }

                    Node x = X.iterator().next();
                    for (Node y : Y) {
                        if (x.equals(y)) continue;
                        computeRowsForJointSingleX(x, y);
                    }
                }
            }
        }

        new MyWatchedProcess();
    }

    private void computeRowsForPair(Node x, Node y) {
        EligibilityV2 elig = checkEligibility(x, y);
        if (!elig.ok) {
            results.add(ResultRowV2.ineligible(x, y, elig.reason));
            return;
        }

        List<Set<Node>> zSets = computeSinglePairAdjustmentSets(x, y);

        if (zSets.isEmpty()) {
            results.add(ResultRowV2.noAdjustmentSet(x, y));
            return;
        }

        for (Set<Node> z : zSets) {
            LinkedHashSet<Node> zClean = new LinkedHashSet<>(z);
            zClean.remove(x);

            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er =
                    AdjustmentEffectEstimatorV1.estimateAteV1(dataSet, x, y, zClean, cfg);

            results.add(ResultRowV2.ok(x, y, zClean, er));
        }
    }

    private void computeRowsForJointSingleX(Node x, Node y) {
        EligibilityV2 elig = checkEligibility(x, y);
        if (!elig.ok) {
            results.add(ResultRowV2.ineligible(x, y, elig.reason));
            return;
        }

        Set<Node> Xset = new LinkedHashSet<>(Collections.singleton(x));
        Set<Node> Yset = new LinkedHashSet<>(Collections.singleton(y));

        List<Set<Node>> zSets = computeJointAdjustmentSets(Xset, Yset);

        if (zSets.isEmpty()) {
            results.add(ResultRowV2.noAdjustmentSet(x, y));
            return;
        }

        for (Set<Node> z : zSets) {
            LinkedHashSet<Node> zClean = new LinkedHashSet<>(z);
            zClean.removeAll(Xset);

            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er =
                    AdjustmentEffectEstimatorV1.estimateAteV1(dataSet, x, y, zClean, cfg);

            results.add(ResultRowV2.ok(x, y, zClean, er));
        }
    }

    private EligibilityV2 checkEligibility(Node x, Node y) {
        // X must be binary discrete
        Node xVar = dataSet.getVariable(x.getName());
        if (!(xVar instanceof DiscreteVariable dx) || dx.getNumCategories() != 2) {
            return EligibilityV2.bad("X must be a 2-category discrete variable (please discretize).");
        }

        // Y must be continuous
        Node yVar = dataSet.getVariable(y.getName());
        if (yVar instanceof DiscreteVariable) {
            return EligibilityV2.bad("Y must be continuous (not discrete).");
        }

        // v2: you said "one DAG input" for now—if you want to hard-enforce:
        // if (!"DAG".equalsIgnoreCase(graphType)) return EligibilityV2.bad("v2 currently expects a DAG input.");

        return EligibilityV2.ok();
    }

    private static final class EligibilityV2 {
        final boolean ok;
        final String reason;

        private EligibilityV2(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        static EligibilityV2 ok() { return new EligibilityV2(true, ""); }
        static EligibilityV2 bad(String reason) { return new EligibilityV2(false, reason); }
    }

    // -----------------------
    // RA computations (borrowed style from linear tool)
    // -----------------------

    private List<Set<Node>> computeSinglePairAdjustmentSets(Node x, Node y) {
        RecursiveAdjustment ra = new RecursiveAdjustment(graph);
        ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
        ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

        return ra.adjustmentSets(
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
    }

    private List<Set<Node>> computeJointAdjustmentSets(Set<Node> Xset, Set<Node> Yset) {
        // v2: for singleton sets, reuse the single-pair logic (keeps behavior consistent)
        if (Xset.size() == 1 && Yset.size() == 1) {
            return computeSinglePairAdjustmentSets(Xset.iterator().next(), Yset.iterator().next());
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

    // -----------------------
    // Enums / rows
    // -----------------------

    public enum EffectMode {
        PAIRWISE,
        JOINT
    }

    public static final class ResultRowV2 implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 2L;

        public final Node x;
        public final Node y;
        public final Set<Node> z; // may be empty for non-OK rows
        public final Status status;

        // v2: table shows only ATE_DR
        public final double ateDr;

        // v2: full details for "View Details..."
        public final AdjustmentEffectEstimatorV1.EffectEstimateResultV1 details;

        // v2: human-readable message for ineligible / not supported rows
        public final String message;

        private ResultRowV2(Node x, Node y, Set<Node> z, Status status,
                            double ateDr,
                            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 details,
                            String message) {
            this.x = x;
            this.y = y;
            this.z = (z == null) ? Collections.emptySet() : new LinkedHashSet<>(z);
            this.status = status;
            this.ateDr = ateDr;
            this.details = details;
            this.message = message;
        }

        public static ResultRowV2 ok(Node x, Node y, Set<Node> z,
                                     AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er) {
            return new ResultRowV2(x, y, z, Status.OK, er.ateDr, er, "");
        }

        public static ResultRowV2 noAdjustmentSet(Node x, Node y) {
            return new ResultRowV2(x, y, Collections.emptySet(), Status.NO_ADJUSTMENT_SET,
                    Double.NaN, null, "No adjustment set");
        }

        public static ResultRowV2 ineligible(Node x, Node y, String reason) {
            return new ResultRowV2(x, y, Collections.emptySet(), Status.INELIGIBLE,
                    Double.NaN, null, reason);
        }

        public static ResultRowV2 notSupportedJoint(Node y) {
            return new ResultRowV2(null, y, Collections.emptySet(), Status.NOT_SUPPORTED,
                    Double.NaN, null, "v2 supports JOINT only for a single binary treatment.");
        }

        public enum Status {
            OK,
            NO_ADJUSTMENT_SET,
            INELIGIBLE,
            NOT_SUPPORTED
        }

        public String formatX() {
            return (x == null) ? "(multiple X)" : x.getName();
        }

        public String formatY() {
            return (y == null) ? "" : y.getName();
        }

        public String formatZ() {
            if (status == Status.INELIGIBLE) return "(Ineligible)";
            if (status == Status.NOT_SUPPORTED) return "(Not supported)";
            if (status == Status.NO_ADJUSTMENT_SET) return "(No set)";
            if (z.isEmpty()) return "∅";
            return z.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
        }
    }
}