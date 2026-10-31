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

import javax.swing.*;
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
 *  - Treatment X must be binary discrete OR a derived binary treatment (binarize tool).
 *  - Outcome Y must be continuous (not discrete).
 *  - Joint mode supported only for |X| = 1 in v2 (because estimator is binary-X).
 */
public final class DoublyRobustEstModelV2 implements SessionModel, GraphSource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // inputs
    /**
     * The data set the effects are estimated from.
     */
    private final DataSet dataSet;
    /**
     * The input graph, with its nodes replaced by the data set's variables.
     */
    private final Graph graph;
    /**
     * The parameters for this model.
     */
    private final Parameters parameters;

    // kept (like the linear model; useful for future wiring)
    /**
     * The data wrapper this model was constructed from.
     */
    private final DataWrapper dataWrapper;
    /**
     * The graph source this model was constructed from.
     */
    private final GraphSource graphSource;

    // selections (set by editor after parsing wildcard/list strings)
    /**
     * The selected treatments.
     */
    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    /**
     * The selected outcomes.
     */
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();

    // UI state
    /**
     * The name of this session model.
     */
    private String name = "";
    /**
     * The effect mode.
     */
    private EffectMode effectMode = EffectMode.PAIRWISE;
    /**
     * The text the user entered to specify the treatments, kept for redisplay.
     */
    private String treatmentsText = "*";
    /**
     * The text the user entered to specify the outcomes, kept for redisplay.
     */
    private String outcomesText = "*";

    // RA parameters (editor can expose these)
    /**
     * The graph type passed to the adjustment set search ("DAG", "PDAG" or "PAG"), inferred from the graph by default.
     */
    private String graphType = "DAG";
    /**
     * The maximum number of adjustment sets to compute for each treatment/outcome pair.
     */
    private int maxNumSets = 20;
    /**
     * The maximum radius for the adjustment set search; -1 for no limit.
     */
    private int maxRadius = -1;
    /**
     * Which endpoint the adjustment set candidates are drawn near: 1 = the treatment, 2 = the outcome, 3 = both.
     */
    private int nearWhichEndpoint = 1;
    /**
     * The maximum path length considered by the adjustment set search; -1 for no limit.
     */
    private int maxPathLength = -1;
    /**
     * Whether the adjustment set search avoids amenable paths.
     */
    private boolean avoidAmenable = true;
    /**
     * Nodes that the adjustment set search should not follow during path exploration.
     */
    private Set<Node> notFollowed = Collections.emptySet();
    /**
     * Nodes that must be included in the adjustment sets.
     */
    private Set<Node> containing = Collections.emptySet();

    // estimator config (v1 estimator under the hood, but v2 tool)
    /**
     * The configuration for the doubly robust effect estimator.
     */
    private final AdjustmentEffectEstimatorV1.ConfigV1 cfg = new AdjustmentEffectEstimatorV1.ConfigV1();

    // derived/binarized treatments (name -> spec)
    /**
     * The derived (binarized) treatments, keyed by derived variable name.
     */
    private final Map<String, DerivedTreatmentSpecV2> derivedTreatments = new LinkedHashMap<>();

    // results
    /**
     * The result rows from the last computation.
     */
    private final List<ResultRowV2> results = new ArrayList<>();

    /**
     * Constructs the model from the first data set of the given data wrapper and the given graph. The graph's nodes are
     * replaced by the data set's variables, and the graph type for the adjustment set search is inferred from the graph's
     * edge marks.
     *
     * @param dataWrapper the data; its first data model must be a DataSet
     * @param graphSource the graph
     * @param parameters  the parameters
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the first data model is not a DataSet
     */
    public DoublyRobustEstModelV2(DataWrapper dataWrapper,
                                  GraphSource graphSource,
                                  Parameters parameters) {
        this.dataWrapper = Objects.requireNonNull(dataWrapper, "dataWrapper");
        this.graphSource = Objects.requireNonNull(graphSource, "graphSource");
        this.parameters = Objects.requireNonNull(parameters, "parameters");

        DataModel dm = this.dataWrapper.getDataModelList().getFirst();
        if (!(dm instanceof DataSet ds)) {
            throw new IllegalArgumentException("v2: Mixed DR Adjustment Effect requires a tabular DataSet.");
        }
        this.dataSet = ds;

        // replace graph nodes with dataset variables (important for name alignment)
        Graph rawGraph = this.graphSource.getGraph();
        this.graph = GraphUtils.replaceNodes(rawGraph, dataSet.getVariables());

        // infer default graph type string used by RA / RAMultiple
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

    /**
     * Returns the data set.
     *
     * @return the data set
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Returns the effect mode.
     *
     * @return the effect mode
     */
    public EffectMode getEffectMode() {
        return effectMode;
    }

    /**
     * Sets the effect mode.
     *
     * @param effectMode the effect mode; must not be null
     */
    public void setEffectMode(EffectMode effectMode) {
        this.effectMode = Objects.requireNonNull(effectMode, "effectMode");
    }

    /**
     * Returns the selected treatments.
     *
     * @return an unmodifiable view of the treatments
     */
    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    /**
     * v2: Editor should pass dataset-aligned nodes; we still normalize by name defensively.
     *
     * @param nodes the treatments; null clears the treatments
     */
    public void setX(Collection<Node> nodes) {
        X.clear();
        if (nodes != null) {
            for (Node n : nodes) {
                Node dn = dataSet.getVariable(n.getName());
                X.add(dn != null ? dn : n);
            }
        }
    }

    /**
     * Returns the selected outcomes.
     *
     * @return an unmodifiable view of the outcomes
     */
    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    /**
     * v2: Editor should pass dataset-aligned nodes; we still normalize by name defensively.
     *
     * @param nodes the outcomes; null clears the outcomes
     */
    public void setY(Collection<Node> nodes) {
        Y.clear();
        if (nodes != null) {
            for (Node n : nodes) {
                Node dn = dataSet.getVariable(n.getName());
                Y.add(dn != null ? dn : n);
            }
        }
    }

    /**
     * Returns the text the user entered to specify the treatments.
     *
     * @return the treatments text
     */
    public String getTreatmentsText() {
        return treatmentsText;
    }

    /**
     * Sets the text the user entered to specify the treatments.
     *
     * @param treatmentsText the treatments text
     * @throws IllegalArgumentException if treatmentsText is null
     */
    public void setTreatmentsText(String treatmentsText) {
        if (treatmentsText == null) throw new IllegalArgumentException("Treatments text cannot be null.");
        this.treatmentsText = treatmentsText;
    }

    /**
     * Returns the text the user entered to specify the outcomes.
     *
     * @return the outcomes text
     */
    public String getOutcomesText() {
        return outcomesText;
    }

    /**
     * Sets the text the user entered to specify the outcomes.
     *
     * @param outcomesText the outcomes text
     * @throws IllegalArgumentException if outcomesText is null
     */
    public void setOutcomesText(String outcomesText) {
        if (outcomesText == null) throw new IllegalArgumentException("Outcomes text cannot be null.");
        this.outcomesText = outcomesText;
    }

    // derived treatments
    /**
     * Returns whether a derived treatment with the given name has been defined.
     *
     * @param name the derived variable name
     * @return true if a derived treatment with that name exists
     */
    public boolean hasDerivedTreatment(String name) {
        return derivedTreatments.containsKey(name);
    }

    /**
     * Returns the derived treatment with the given name.
     *
     * @param name the derived variable name
     * @return the derived treatment specification, or null if there is none
     */
    public DerivedTreatmentSpecV2 getDerivedTreatment(String name) {
        return derivedTreatments.get(name);
    }

    /**
     * Returns all derived treatments.
     *
     * @return an unmodifiable view of the derived treatment specifications
     */
    public Collection<DerivedTreatmentSpecV2> getDerivedTreatments() {
        return Collections.unmodifiableCollection(derivedTreatments.values());
    }

    /**
     * Adds a derived treatment, replacing any existing one with the same derived name.
     *
     * @param spec the derived treatment specification; must not be null
     */
    public void addOrReplaceDerivedTreatment(DerivedTreatmentSpecV2 spec) {
        Objects.requireNonNull(spec, "spec");
        derivedTreatments.put(spec.getDerivedName(), spec);
    }

    /**
     * Removes the derived treatment with the given name, if any.
     *
     * @param derivedName the derived variable name
     */
    public void removeDerivedTreatment(String derivedName) {
        derivedTreatments.remove(derivedName);
    }

    // RA knobs (optional to expose in editor)
    /**
     * Returns the graph type passed to the adjustment set search.
     *
     * @return the graph type
     */
    public String getGraphType() { return graphType; }
    /**
     * Sets the graph type passed to the adjustment set search.
     *
     * @param graphType the graph type (e.g., "DAG", "PDAG", "PAG"); null means "DAG"
     */
    public void setGraphType(String graphType) { this.graphType = (graphType == null) ? "DAG" : graphType; }

    /**
     * Returns the maximum number of adjustment sets computed per pair.
     *
     * @return the maximum number of adjustment sets
     */
    public int getMaxNumSets() { return maxNumSets; }
    /**
     * Sets the maximum number of adjustment sets computed per pair.
     *
     * @param maxNumSets the maximum number of adjustment sets
     */
    public void setMaxNumSets(int maxNumSets) { this.maxNumSets = maxNumSets; }

    /**
     * Returns the maximum radius for the adjustment set search.
     *
     * @return the maximum radius; -1 for no limit
     */
    public int getMaxRadius() { return maxRadius; }
    /**
     * Sets the maximum radius for the adjustment set search.
     *
     * @param maxRadius the maximum radius; -1 for no limit
     */
    public void setMaxRadius(int maxRadius) { this.maxRadius = maxRadius; }

    /**
     * Returns which endpoint the adjustment set search should stay near.
     *
     * @return 1 (near the treatment), 2 (near the outcome) or 3 (both)
     */
    public int getNearWhichEndpoint() { return nearWhichEndpoint; }
    /**
     * Sets which endpoint the adjustment set search should stay near.
     *
     * @param nearWhichEndpoint 1 (near the treatment), 2 (near the outcome) or 3 (both)
     */
    public void setNearWhichEndpoint(int nearWhichEndpoint) { this.nearWhichEndpoint = nearWhichEndpoint; }

    /**
     * Returns the maximum path length considered by the adjustment set search.
     *
     * @return the maximum path length; -1 for no limit
     */
    public int getMaxPathLength() { return maxPathLength; }
    /**
     * Sets the maximum path length considered by the adjustment set search.
     *
     * @param maxPathLength the maximum path length; -1 for no limit
     */
    public void setMaxPathLength(int maxPathLength) { this.maxPathLength = maxPathLength; }

    /**
     * Returns whether the adjustment set search avoids amenable paths.
     *
     * @return true if amenable paths are avoided
     */
    public boolean isAvoidAmenable() { return avoidAmenable; }
    /**
     * Sets whether the adjustment set search avoids amenable paths.
     *
     * @param avoidAmenable true to avoid amenable paths
     */
    public void setAvoidAmenable(boolean avoidAmenable) { this.avoidAmenable = avoidAmenable; }

    /**
     * Returns the nodes the adjustment set search should not follow.
     *
     * @return the nodes not followed
     */
    public Set<Node> getNotFollowed() { return notFollowed; }
    /**
     * Sets the nodes the adjustment set search should not follow.
     *
     * @param notFollowed the nodes not followed; null means none
     */
    public void setNotFollowed(Set<Node> notFollowed) {
        this.notFollowed = (notFollowed == null) ? Collections.emptySet() : new LinkedHashSet<>(notFollowed);
    }

    /**
     * Returns the nodes that must be included in the adjustment sets.
     *
     * @return the required nodes
     */
    public Set<Node> getContaining() { return containing; }
    /**
     * Sets the nodes that must be included in the adjustment sets.
     *
     * @param containing the required nodes; null means none
     */
    public void setContaining(Set<Node> containing) {
        this.containing = (containing == null) ? Collections.emptySet() : new LinkedHashSet<>(containing);
    }

    /**
     * Returns the (mutable) configuration for the doubly robust effect estimator.
     *
     * @return the estimator configuration
     */
    public AdjustmentEffectEstimatorV1.ConfigV1 getCfg() {
        return cfg;
    }

    /**
     * Returns the result rows from the last computation.
     *
     * @return an unmodifiable view of the result rows
     */
    public List<ResultRowV2> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the result row at the given index.
     *
     * @param i the row index
     * @return the result row
     */
    public ResultRowV2 getResultRow(int i) {
        return results.get(i);
    }

    // -----------------------
    // Main computation
    // -----------------------

    /**
     * Recomputes the results in a watched process. The results are cleared; then, if both the treatments and outcomes
     * are nonempty, for each treatment/outcome pair with x &ne; y, adjustment sets are computed from the graph (using
     * the source variable for a derived treatment) and the doubly robust ATE is estimated for each set. Currently every
     * pair is treated pairwise, whatever the effect mode.
     *
     * @param onFinish a callback run on the event thread after the results are computed; it is not run if the
     *                 treatments or outcomes are empty
     */
    public void recomputeAsync(Runnable onFinish) {
        class MyWatchedProcess extends WatchedProcess {
            @Override
            public void watch() {
                results.clear();

                if (X.isEmpty() || Y.isEmpty()) return;

//                if (effectMode == EffectMode.PAIRWISE) {
//                    for (Node x : X) {
//                        for (Node y : Y) {
//                            if (x == null || y == null) continue;
//                            if (x.equals(y)) continue;
//                            computeRowsForPair(x, y);
//                        }
//                    }
//                } else {
//                    // v2: JOINT supported only for |X|=1 (binary estimator)
//                    if (X.size() != 1) {
//                        for (Node y : Y) {
//                            if (y == null) continue;
//                            results.add(ResultRowV2.notSupportedJoint(y));
//                        }
//                        return;
//                    }
//
//                    Node x = X.iterator().next();
//                    for (Node y : Y) {
//                        if (y == null) continue;
//                        if (x.equals(y)) continue;
//                        computeRowsForJointSingleX(x, y);
//                    }
//                }

                for (Node x : X) {
                    for (Node y : Y) {
                        if (x == null || y == null) continue;
                        if (x.equals(y)) continue;
                        computeRowsForPair(x, y);
                    }
                }

                SwingUtilities.invokeLater(onFinish);
            }
        }

        // NOTE: LinearAdjustmentTotalEffectsModel uses the same pattern:
        // instantiating WatchedProcess triggers the work.
        new MyWatchedProcess();
    }

    private void computeRowsForPair(Node xSelected, Node y) {
        // If derived, compute adjustment sets using the SOURCE node in the graph.
        Node xForRa = xSelected;

        DerivedTreatmentSpecV2 spec = null;
        boolean isDerived = hasDerivedTreatment(xSelected.getName());
        if (isDerived) {
            spec = getDerivedTreatment(xSelected.getName());
            xForRa = graph.getNode(spec.getSourceName());
            if (xForRa == null) {
                results.add(ResultRowV2.ineligible(xSelected, y,
                        "Derived treatment " + spec.getDerivedName()
                                + " refers to source variable " + spec.getSourceName()
                                + " which is not in the graph."));
                return;
            }
        }

        EligibilityV2 elig = checkEligibility(xSelected, y);
        if (!elig.ok) {
            results.add(ResultRowV2.ineligible(xSelected, y, elig.reason));
            return;
        }

        // IMPORTANT: compute Z using xForRa (graph node), not xSelected (placeholder).
        List<Set<Node>> zSets = computeSinglePairAdjustmentSets(xForRa, y);

        if (zSets.isEmpty()) {
            results.add(ResultRowV2.noAdjustmentSet(xSelected, y));
            return;
        }

        for (Set<Node> z : zSets) {
            LinkedHashSet<Node> zClean = new LinkedHashSet<>(z);
            zClean.remove(xForRa);      // remove the SOURCE node if present

            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er;

            if (!isDerived) {
                er = AdjustmentEffectEstimatorV1.estimateAteV1(dataSet, xSelected, y, zClean, cfg);
            } else {
                int[] x01Full = spec.computeX01Full(dataSet);

                er = AdjustmentEffectEstimatorV1.estimateAteBinaryVectorV1(
                        dataSet,
                        spec.getDerivedName(),
                        x01Full,
                        y,
                        zClean,
                        cfg
                );
            }

            // Row should still show xSelected (i.e. "X5_bin") in the table.
            results.add(ResultRowV2.ok(xSelected, y, zClean, er));
        }
    }

    private void computeRowsForJointSingleX(Node xSelected, Node y) {
        // v2: JOINT supported only for |X| = 1 (binary estimator),
        // but X may be a derived/binarized placeholder (e.g., "X5_bin").

        // 1) If derived, use SOURCE node for RA (graph-based adjustment sets).
        Node xForRa = xSelected;

        DerivedTreatmentSpecV2 spec = null;
        boolean isDerived = hasDerivedTreatment(xSelected.getName());
        if (isDerived) {
            spec = getDerivedTreatment(xSelected.getName());
            xForRa = graph.getNode(spec.getSourceName());
            if (xForRa == null) {
                results.add(ResultRowV2.ineligible(
                        xSelected, y,
                        "Derived treatment " + spec.getDerivedName()
                                + " refers to source variable " + spec.getSourceName()
                                + " which is not in the graph."
                ));
                return;
            }
        }

        // 2) Eligibility check should apply to the selected treatment (derived allowed).
        EligibilityV2 elig = checkEligibility(xSelected, y);
        if (!elig.ok) {
            results.add(ResultRowV2.ineligible(xSelected, y, elig.reason));
            return;
        }

        // 3) v2 JOINT is effectively singleton do(X) anyway, so compute Z using xForRa.
        // (We use the single-pair RA to keep behavior consistent.)
        List<Set<Node>> zSets = computeSinglePairAdjustmentSets(xForRa, y);

        if (zSets.isEmpty()) {
            results.add(ResultRowV2.noAdjustmentSet(xSelected, y));
            return;
        }

        for (Set<Node> z : zSets) {
            LinkedHashSet<Node> zClean = new LinkedHashSet<>(z);
            zClean.remove(xForRa); // remove SOURCE node, not derived placeholder

            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er;

            if (!isDerived) {
                // Standard binary discrete X in dataset
                er = AdjustmentEffectEstimatorV1.estimateAteV1(dataSet, xSelected, y, zClean, cfg);
            } else {
                // Derived/binarized X: pass provided binary vector
                int[] x01Full = spec.computeX01Full(dataSet);

                er = AdjustmentEffectEstimatorV1.estimateAteBinaryVectorV1(
                        dataSet,
                        spec.getDerivedName(),
                        x01Full,
                        y,
                        zClean,
                        cfg
                );
            }

            // Row should display the selected X (e.g., "X5_bin"), but Z comes from source X (e.g., "X5").
            results.add(ResultRowV2.ok(xSelected, y, zClean, er));
        }
    }

    private AdjustmentEffectEstimatorV1.EffectEstimateResultV1 estimateFor(Node x, Node y, Set<Node> zClean) {
        boolean isDerived = hasDerivedTreatment(x.getName());

        if (!isDerived) {
            return AdjustmentEffectEstimatorV1.estimateAteV1(dataSet, x, y, zClean, cfg);
        }

        DerivedTreatmentSpecV2 spec = getDerivedTreatment(x.getName());
        int[] x01Full = spec.computeX01Full(dataSet);

        return AdjustmentEffectEstimatorV1.estimateAteBinaryVectorV1(
                dataSet,
                spec.getDerivedName(),
                x01Full,
                y,
                zClean,
                cfg
        );
    }

    private EligibilityV2 checkEligibility(Node x, Node y) {
        boolean isDerived = hasDerivedTreatment(x.getName());

        if (!isDerived) {
            Node xVar = dataSet.getVariable(x.getName());
            if (!(xVar instanceof DiscreteVariable dx) || dx.getNumCategories() != 2) {
                return EligibilityV2.bad("X must be a 2-category discrete variable OR use Binarize...");
            }
        }

        Node yVar = dataSet.getVariable(y.getName());
        if (yVar instanceof DiscreteVariable) {
            return EligibilityV2.bad("Y must be continuous (not discrete).");
        }

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

        Node x1 = graph.getNode(x.getName()) != null ? graph.getNode(x.getName()) : x;
        Node y1 = graph.getNode(y.getName()) != null ? graph.getNode(y.getName()) : y;

        if (x1 == y1) return Collections.emptyList();

        return ra.adjustmentSets(
                x1,
                y1,
                graphType,
                maxNumSets,
                maxRadius,
                nearWhichEndpoint,
                maxPathLength,
                RecursiveAdjustment.ColliderPolicy.OFF,
                avoidAmenable,
                normalizeToGraphNodes(notFollowed),
                normalizeToGraphNodes(containing),
                Set.of()
        );
    }

    private List<Set<Node>> computeJointAdjustmentSets(Set<Node> Xset, Set<Node> Yset) {
        if (Xset.size() == 1 && Yset.size() == 1) {
            return computeSinglePairAdjustmentSets(Xset.iterator().next(), Yset.iterator().next());
        }

        RecursiveAdjustmentMultiple ra = new RecursiveAdjustmentMultiple(graph);
        ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
        ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

        return ra.adjustmentSets(
                normalizeToGraphNodes(Xset),
                normalizeToGraphNodes(Yset),
                graphType,
                maxNumSets,
                maxRadius,
                nearWhichEndpoint,
                maxPathLength,
                avoidAmenable,
                normalizeToGraphNodes(notFollowed),
                normalizeToGraphNodes(containing)
        );
    }

    private Set<Node> normalizeToGraphNodes(Set<Node> s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        LinkedHashSet<Node> out = new LinkedHashSet<>();
        for (Node n : s) {
            if (n == null) continue;
            Node gNode = graph.getNode(n.getName());
            out.add(gNode != null ? gNode : n);
        }
        return out;
    }

    // -----------------------
    // Enums / rows
    // -----------------------

    /**
     * Which interventions the effects are computed for.
     */
    public enum EffectMode {
        /**
         * The effect of do(x) on y for each treatment/outcome pair.
         */
        PAIRWISE,
        /**
         * The effect of the joint intervention on the treatments on each outcome (supported only for a single
         * treatment).
         */
        JOINT
    }

    /**
     * One result row: a treatment, an outcome, an adjustment set, a status, and the estimated effect.
     */
    public static final class ResultRowV2 implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 2L;

        /**
         * The treatment, or null for a joint row with multiple treatments.
         */
        public final Node x;
        /**
         * The outcome.
         */
        public final Node y;
        /**
         * The adjustment set; empty for rows that are not OK.
         */
        public final Set<Node> z; // may be empty for non-OK rows
        /**
         * The status of this row.
         */
        public final Status status;

        // v2: table shows only ATE_DR
        /**
         * The doubly robust ATE estimate; NaN for rows that are not OK.
         */
        public final double ateDr;

        // v2: full details for "View Details..."
        /**
         * The full estimation result, or null for rows that are not OK.
         */
        public final AdjustmentEffectEstimatorV1.EffectEstimateResultV1 details;

        // v2: human-readable message for ineligible / not supported rows
        /**
         * A human-readable message for ineligible or unsupported rows; empty for OK rows.
         */
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

        /**
         * Creates a row for a successful estimate.
         *
         * @param x  the treatment
         * @param y  the outcome
         * @param z  the adjustment set
         * @param er the estimation result
         * @return the row
         */
        public static ResultRowV2 ok(Node x, Node y, Set<Node> z,
                                     AdjustmentEffectEstimatorV1.EffectEstimateResultV1 er) {
            return new ResultRowV2(x, y, z, Status.OK, er.ateDr, er, "");
        }

        /**
         * Creates a row recording that no adjustment set was found.
         *
         * @param x the treatment
         * @param y the outcome
         * @return the row
         */
        public static ResultRowV2 noAdjustmentSet(Node x, Node y) {
            return new ResultRowV2(x, y, Collections.emptySet(), Status.NO_ADJUSTMENT_SET,
                    Double.NaN, null, "No adjustment set");
        }

        /**
         * Creates a row recording that the pair is ineligible for estimation.
         *
         * @param x      the treatment
         * @param y      the outcome
         * @param reason why the pair is ineligible
         * @return the row
         */
        public static ResultRowV2 ineligible(Node x, Node y, String reason) {
            return new ResultRowV2(x, y, Collections.emptySet(), Status.INELIGIBLE,
                    Double.NaN, null, reason);
        }

        /**
         * Creates a row recording that the joint mode is not supported for multiple treatments.
         *
         * @param y the outcome
         * @return the row
         */
        public static ResultRowV2 notSupportedJoint(Node y) {
            return new ResultRowV2(null, y, Collections.emptySet(), Status.NOT_SUPPORTED,
                    Double.NaN, null, "v2 supports JOINT only for a single binary treatment.");
        }

        /**
         * The status of a result row.
         */
        public enum Status {
            /**
             * The effect was estimated.
             */
            OK,
            /**
             * No adjustment set was found.
             */
            NO_ADJUSTMENT_SET,
            /**
             * The treatment or outcome has the wrong type for the estimator.
             */
            INELIGIBLE,
            /**
             * The requested estimate is not supported.
             */
            NOT_SUPPORTED
        }

        /**
         * Returns the treatment name for display.
         *
         * @return the treatment name, or "(multiple X)" if there is no single treatment
         */
        public String formatX() {
            return (x == null) ? "(multiple X)" : x.getName();
        }

        /**
         * Returns the outcome name for display.
         *
         * @return the outcome name, or an empty string if there is none
         */
        public String formatY() {
            return (y == null) ? "" : y.getName();
        }

        /**
         * Returns the adjustment set for display, with names sorted, or a status marker for rows that are not OK.
         *
         * @return the formatted adjustment set
         */
        public String formatZ() {
            if (status == Status.INELIGIBLE) return "(Ineligible)";
            if (status == Status.NOT_SUPPORTED) return "(Not supported)";
            if (status == Status.NO_ADJUSTMENT_SET) return "(No set)";
            if (z.isEmpty()) return "∅";
            return z.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
        }
    }
}