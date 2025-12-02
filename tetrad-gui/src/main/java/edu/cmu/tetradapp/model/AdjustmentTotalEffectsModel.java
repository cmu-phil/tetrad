package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
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

/**
 * Model for the "Adjustment & Total Effects" regression tool.
 *
 * - Takes a graph + dataset.
 * - Accepts treatments X and outcomes Y as sets of nodes.
 * - Uses RecursiveAdjustment for |X|=|Y|=1, and RecursiveAdjustmentMultiple otherwise.
 * - For each adjustment set Z it finds, runs a linear regression and
 *   stores the total effect(s) (regression coefficients for X on Y | Z).
 */
public final class AdjustmentTotalEffectsModel implements SessionModel, GraphSource, Serializable {
    private static final long serialVersionUID = 1L;

    private final DataSet dataSet;
    private final Graph graph;

    // Selected treatment(s) and outcome(s)
    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();

    private final Parameters parameters;
    private final DataWrapper dataModel;
    private final GraphSource graphSource;

    // RA parameters (defaults are reasonable starting points)
    private String graphType = "dag";
    private int maxNumSets = 20;
    private int maxRadius = -1;           // <0 means "no radius limit"
    private int nearWhichEndpoint = 1;    // 0 = source, 1 = target, else min
    private int maxPathLength = -1;       // <0 means "unbounded"
    private boolean avoidAmenable = true;
    private Set<Node> notFollowed = Collections.emptySet();
    private Set<Node> containing = Collections.emptySet();

    // Computed results
    private final List<ResultRow> results = new ArrayList<>();
    private String name = "";

    public AdjustmentTotalEffectsModel(DataWrapper dataModel,
                                       GraphSource graphSource,
                                       Parameters parameters) {
        this.dataSet = (DataSet) Objects.requireNonNull(dataModel)
                .getDataModelList().getFirst();
        Graph graph1 = Objects.requireNonNull(graphSource).getGraph();
        this.graph = GraphUtils.replaceNodes(graph1, dataSet.getVariables());
        this.parameters = Objects.requireNonNull(parameters);

        this.dataModel = dataModel;
        this.graphSource = graphSource;
    }

    // ---------------------------------------------------------------------
    // Configuration API
    // ---------------------------------------------------------------------

    public DataSet getDataSet() {
        return dataSet;
    }

    public Graph getGraph() {
        return graph;
    }

    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    public void setX(Collection<Node> nodes) {
        X.clear();
        if (nodes != null) X.addAll(nodes);
    }

    public void setY(Collection<Node> nodes) {
        Y.clear();
        if (nodes != null) Y.addAll(nodes);
    }

    public String getGraphType() {
        return graphType;
    }

    public void setGraphType(String graphType) {
        this.graphType = (graphType == null) ? "dag" : graphType;
    }

    public int getMaxNumSets() {
        return maxNumSets;
    }

    public void setMaxNumSets(int maxNumSets) {
        this.maxNumSets = maxNumSets;
    }

    public int getMaxRadius() {
        return maxRadius;
    }

    public void setMaxRadius(int maxRadius) {
        this.maxRadius = maxRadius;
    }

    public int getNearWhichEndpoint() {
        return nearWhichEndpoint;
    }

    public void setNearWhichEndpoint(int nearWhichEndpoint) {
        this.nearWhichEndpoint = nearWhichEndpoint;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public boolean isAvoidAmenable() {
        return avoidAmenable;
    }

    public void setAvoidAmenable(boolean avoidAmenable) {
        this.avoidAmenable = avoidAmenable;
    }

    public Set<Node> getNotFollowed() {
        return notFollowed;
    }

    public void setNotFollowed(Set<Node> notFollowed) {
        this.notFollowed = (notFollowed == null)
                ? Collections.emptySet()
                : new LinkedHashSet<>(notFollowed);
    }

    public Set<Node> getContaining() {
        return containing;
    }

    public void setContaining(Set<Node> containing) {
        this.containing = (containing == null)
                ? Collections.emptySet()
                : new LinkedHashSet<>(containing);
    }

    public List<ResultRow> getResults() {
        return Collections.unmodifiableList(results);
    }

    public ResultRow getResultRow(int rowIndex) {
        return results.get(rowIndex);
    }

    // ---------------------------------------------------------------------
    // Core computation
    // ---------------------------------------------------------------------

    /**
     * Recompute adjustment sets and total effects for the current X,Y and parameters.
     *
     * Produces one ResultRow per (X-set, {y}, Z), i.e., one row per outcome y in Y
     * and per adjustment set Z, with vector of betas for all X in X.
     */
    public void recompute() {
        results.clear();

        if (X.isEmpty() || Y.isEmpty()) {
            return;
        }

        List<Set<Node>> adjustmentSets = computeAdjustmentSets();

        // For each adjustment set Z and each outcome y in Y, run a regression.
        for (Set<Node> Z : adjustmentSets) {
            for (Node y : Y) {
                ResultRow row = runRegressionFor(y, Z);
                if (row != null) {
                    results.add(row);
                }
            }
        }
    }

    /**
     * Choose RecursiveAdjustment vs RecursiveAdjustmentMultiple and find adjustment sets.
     */
    private List<Set<Node>> computeAdjustmentSets() {
        if (X.size() == 1 && Y.size() == 1) {
            // Use the well-tuned single-pair engine.
            Node x = X.getFirst();
            Node y = Y.getFirst();

            RecursiveAdjustment ra = new RecursiveAdjustment(graph);
            ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
            ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

            return ra.adjustmentSetsRB(
                    x, y,
                    graphType,
                    maxNumSets,
                    maxRadius,
                    nearWhichEndpoint,
                    maxPathLength,
                    avoidAmenable,
                    notFollowed,
                    containing
            );
        } else {
            // Multi-treatment / multi-effect case.
            RecursiveAdjustmentMultiple ra = new RecursiveAdjustmentMultiple(graph);
            ra.setColliderPolicy(RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST);
            ra.setNoAmenablePolicy(RecursiveAdjustment.NoAmenablePolicy.SEARCH);

            return ra.adjustmentSets(
                    X, Y,
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
    }

    /**
     * Run a linear regression for outcome y on X ∪ Z, and extract total effects (betas for X).
     *
     * One row corresponds to:
     *   - the common X-set,
     *   - this specific y,
     *   - this specific Z.
     */
    private ResultRow runRegressionFor(Node y, Set<Node> Z) {
        // Build regressor list in a fixed order: all X (in X's insertion order), then Z.
        List<Node> regressors = new ArrayList<>(X.size() + Z.size());
        regressors.addAll(X);
        regressors.addAll(Z);

        // Use the same regression engine as elsewhere in the GUI.
        // If you prefer your earlier constructor, you can revert to that.
        Regression regression = new RegressionDataset(dataSet);
        // Or, if you've been using this variant and it compiles for you:
        // Regression regression = new RegressionDataset(dataSet.getDoubleData(), dataModel.getVariables());

        RegressionResult result = regression.regress(y, regressors);

        // Extract coefficients and SEs for each X.
        double[] coeffs = result.getCoef();
        double[] ses = result.getSe();

        double[] betas = new double[X.size()];
        double[] seX = new double[X.size()];

        int i = 0;
        for (Node x : X) {
            int idx = regressors.indexOf(x);
            // +1 if coef[0] is intercept; adjust if your API differs.
            int coefIndex = idx + 1;
            if (coefIndex < 0 || coefIndex >= coeffs.length) {
                // Shouldn't happen; fail soft.
                betas[i] = Double.NaN;
                seX[i] = Double.NaN;
            } else {
                betas[i] = coeffs[coefIndex];
                seX[i] = (ses == null || coefIndex >= ses.length)
                        ? Double.NaN
                        : ses[coefIndex];
            }
            i++;
        }

        // NOTE: store {y} as the Y-set for this row, not the whole Y.
        return new ResultRow(
                new LinkedHashSet<>(X),
                new LinkedHashSet<>(Collections.singleton(y)),
                new LinkedHashSet<>(Z),
                betas,
                seX,
                result
        );
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        if (name == null) {throw new IllegalArgumentException("Name cannot be null");}
        this.name = name;
    }

    // ---------------------------------------------------------------------
    // Result row
    // ---------------------------------------------------------------------

    public static final class ResultRow implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        public final LinkedHashSet<Node> X;
        public final LinkedHashSet<Node> Y;   // currently singleton {y}
        public final LinkedHashSet<Node> Z;   // adjustment set

        public final double[] betas;          // |X|-vector of total effects
        public final double[] se;             // |X|-vector of standard errors (optional)

        public final RegressionResult regressionResult;

        public ResultRow(LinkedHashSet<Node> X,
                         LinkedHashSet<Node> Y,
                         LinkedHashSet<Node> Z,
                         double[] betas,
                         double[] se,
                         RegressionResult regressionResult) {
            this.X = X;
            this.Y = Y;
            this.Z = Z;
            this.betas = betas;
            this.se = se;
            this.regressionResult = regressionResult;
        }

        public String formatXSet() {
            return formatNodeSet(X);
        }

        public String formatYSet() {
            return formatNodeSet(Y);
        }

        public String formatZSet() {
            return formatNodeSet(Z);
        }

        public String formatBetas() {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Node x : X) {
                if (i > 0) sb.append(", ");
                sb.append(x.getName())
                        .append(": ")
                        .append(String.format("%.4f", betas[i]));
                i++;
            }
            return sb.toString();
        }

        private static String formatNodeSet(Set<Node> s) {
            if (s.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Node n : s) {
                if (!first) sb.append(", ");
                sb.append(n.getName());
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
    }
}