package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
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
    private String graphType = "dag";
    private int maxNumSets = 20;
    private int maxRadius = -1;           // <0 means "no radius limit"
    private int nearWhichEndpoint = 1;    // 0 = source, 1 = target, else min
    private int maxPathLength = -1;       // <0 means "unbounded"
    private boolean avoidAmenable = true;
    private Set<Node> notFollowed = Collections.emptySet();
    private Set<Node> containing = Collections.emptySet();

    // Mode: pairwise vs joint
    private EffectMode effectMode = EffectMode.PAIRWISE;

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
        this.graph = Objects.requireNonNull(graphSource).getGraph();
        this.parameters = Objects.requireNonNull(parameters);

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
        this.graphType = (graphType == null) ? "dag" : graphType;
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
    public void recompute() {
        results.clear();

        if (X.isEmpty() || Y.isEmpty()) {
            return;
        }

        if (effectMode == EffectMode.JOINT) {
            // Joint mode: use X, Y as sets with RAMultiple (or RA for 1×1).
            List<Set<Node>> adjustmentSets = computeJointAdjustmentSets(X, Y);

            for (Set<Node> Z : adjustmentSets) {
                for (Node y : Y) {
                    ResultRow row = runRegressionFor(X, y, Z);
                    if (row != null) {
                        results.add(row);
                    }
                }
            }
        } else {
            // Pairwise mode: all combinations of X×Y via single-pair RA.
            for (Node x : X) {
                for (Node y : Y) {
                    if (x == y) continue;

                    List<Set<Node>> adjustmentSets = computeSinglePairAdjustmentSets(x, y);
                    for (Set<Node> Z : adjustmentSets) {
                        ResultRow row = runRegressionFor(
                                Collections.singleton(x), y, Z);
                        if (row != null) {
                            results.add(row);
                        }
                    }
                }
            }
        }
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

        // Build regressor list in a fixed order: all X (in Xlinked order), then Z.
        List<Node> regressors = new ArrayList<>(Xlinked.size() + Z.size());
        regressors.addAll(Xlinked);
        regressors.addAll(Z);

        Regression regression = new RegressionDataset(dataSet);
        RegressionResult result = regression.regress(y, regressors);

        double[] coeffs = result.getCoef();
        double[] ses = result.getSe();

        double[] betas = new double[Xlinked.size()];
        double[] seX = new double[Xlinked.size()];

        int i = 0;
        for (Node x : Xlinked) {
            int idx = regressors.indexOf(x);
            int coefIndex = idx + 1; // assuming coef[0] is intercept
            if (coefIndex < 0 || coefIndex >= coeffs.length) {
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

        return new ResultRow(
                Xlinked,
                new LinkedHashSet<>(Collections.singleton(y)),
                new LinkedHashSet<>(Z),
                betas,
                seX,
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

        /**
         * The adjustment set Z.
         */
        public final LinkedHashSet<Node> X;
        /**
         * The outcome variable y.
         */
        public final LinkedHashSet<Node> Y;   // singleton {y}
        /**
         * Represents the adjustment set Z used in the adjustment total effects analysis.
         * <p>
         * This set contains nodes that are included as covariates in a regression model to control for confounding
         * effects in the analysis. It is typically used alongside the predictor variables (X) and the outcome variable
         * (Y).
         */
        public final LinkedHashSet<Node> Z;   // adjustment set

        /**
         * Represents the total effects calculated for each predictor variable in the adjustment total effects analysis.
         * The total effects are stored in the same order as the predictor variables in the set X.
         * <p>
         * Each entry in the array corresponds to the total effect of a specific variable in X on the outcome variable
         * Y, as estimated using the adjustment set Z. These total effects quantify the direct and indirect
         * relationships between each predictor variable and the outcome variable, after accounting for the adjustment
         * set.
         */
        public final double[] betas;          // |X|-vector of total effects
        /**
         * Represents an array of standard errors corresponding to the values in the regression analysis or computation.
         * Each element in this array contains the standard error for a specific parameter or estimate, typically
         * representing the precision of the associated value.
         * <p>
         * This field is optional and may not always be populated, depending on the context or the availability of
         * standard error calculations.
         */
        public final double[] se;             // |X|-vector of standard errors (optional)

        /**
         * Represents the result of a regression analysis encapsulated in the current result row. This field holds an
         * instance of {@code RegressionResult}, which contains the outcomes of a regression performed on the variables
         * represented in this object. The instance provides access to statistical results such as coefficients,
         * standard errors, and other metrics derived from the regression computation.
         */
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

        /**
         * Formats the set of nodes represented by the field X into a string.
         * The format follows the structure of a comma-separated list of node names enclosed in square brackets.
         *
         * @return A string representation of the set of nodes in X. If the set is empty, returns "[]".
         */
        public String formatXSet() {
            return formatNodeSet(X);
        }

        /**
         * Formats the set of nodes represented by the field Y into a string.
         * The format follows the structure of a comma-separated list of node names enclosed in square brackets.
         *
         * @return A string representation of the set of nodes in Y. If the set is empty, returns "[]".
         */
        public String formatYSet() {
            return formatNodeSet(Y);
        }

        /**
         * Formats the set of nodes represented by the field Z into a string.
         * The format follows the structure of a comma-separated list of node names enclosed in square brackets.
         *
         * @return A string representation of the set of nodes in Z. If the set is empty, returns "[]".
         */
        public String formatZSet() {
            return formatNodeSet(Z);
        }

        /**
         * Formats the set of beta values into a string representation.
         * Each beta value is formatted with 4 decimal places and includes the corresponding node name.
         *
         * @return A string representation of the beta values and their corresponding nodes.
         */
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
    }
}