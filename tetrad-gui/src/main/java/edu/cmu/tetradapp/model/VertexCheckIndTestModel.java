/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.WildBootstrapMarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serial;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Model for a per-vertex ("local") Markov check, a.k.a. "Vertex Checker".
 * <p>
 * For each vertex X, constructs a conditioning set CS(X) (e.g., Markov blanket, parents, etc.),
 * and tests all claims Ind(X, Y | CS(X)) for Y not in CS(X) (and Y != X) using the chosen
 * IndependenceTest. The resulting p-values are then tested for Uniform(0,1) using Anderson-Darling
 * or Kolomogorov-Smirnov.
 * <p>
 * This is designed to support a Tetrad interface tool that highlights locally reliable regions
 * of an estimated graph relative to data.
 */
public class VertexCheckIndTestModel implements SessionModel, GraphSource, KnowledgeBoxInput {

    /**
     * The name of the property fired when the graph changes.
     */
    public static final String PROP_GRAPH = "graph";
    /**
     * The parameter key for the uniformity test used on p-values.
     */
    public static final String UNIFORMITY_TEST_PARAM = "uniformityTestParam";
    /**
     * Uniformity test value: Kolmogorov-Smirnov.
     */
    public static final String KOLMOGOROV_SMIRNOFF = "kolmogorovSmirnoff";
    /**
     * Uniformity test value: Anderson-Darling.
     */
    public static final String ANDERSON_DARLING = "andersonDarling";
    /**
     * Uniformity test value: wild bootstrap.
     */
    public static final String WILD_BOOTSTRAP = "wildBootstrap";
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * The data model being checked.
     */
    private DataModel dataModel;
    /**
     * The data models the parent data box offers, in order; the check runs on one of them. Null in sessions saved
     * before the choice existed, in which case dataModel is the only one.
     */
    private List<DataModel> dataModels;
    /**
     * The index, in dataModels, of the data model being checked.
     */
    private int dataIndex;
    /**
     * The parameters for the check.
     */
    private final Parameters parameters;
    /**
     * The per-vertex summaries, keyed by vertex name.
     */
    private final Map<String, VertexSummary> summariesByVertex = new LinkedHashMap<>();
    /**
     * The per-vertex independence results, keyed by vertex name.
     */
    private final Map<String, List<IndependenceResult>> resultsByVertex = new LinkedHashMap<>();
    /**
     * The cache of independence queries; test errors are treated as independent.
     */
    private final CachedIndependenceQueries cachedQueries =
            new CachedIndependenceQueries(CachedIndependenceQueries.ErrorPolicy.TREAT_AS_INDEPENDENT);
    /**
     * Support for property change listeners.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    /**
     * The graph being checked.
     */
    private Graph graph;
    /**
     * The name of this model.
     */
    private String name = "";
    /**
     * The independence test used for the check; transient.
     */
    private transient IndependenceTest independenceTest;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The names of the vertices to check; computed from the independence test's variables if empty.
     */
    private List<String> vertexNames = new ArrayList<>();
    /**
     * Whether verbose output is logged.
     */
    private boolean verbose = false;
    /**
     * The cached model-level summary, or null if not computed.
     */
    private ModelSummary modelSummary;

    /**
     * Constructs a new Vertex checer with the given data model, graph, and parameters.
     *
     * @param dataModel   the data model.
     * @param graphSource the graph source.
     * @param parameters  the parameters.
     */
    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
        this(dataModel, graphSource, null, parameters);
    }

    /**
     * Constructs a new Vertex checker with the given data model, graph, and parameters.
     *
     * @param dataModel   the data model.
     * @param graphSource the graph source.
     * @param parameters  the parameters.
     * @param knowlegeBox a {@link KnowledgeBoxModel} object
     */
    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, KnowledgeBoxModel knowlegeBox,
                                   Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.dataModels = new ArrayList<>(dataModel.getDataModelList());

        // By identity, not equals(): equal-content data sets (and equals() on large ones) should not matter.
        for (int i = 0; i < this.dataModels.size(); i++) {
            if (this.dataModels.get(i) == this.dataModel) this.dataIndex = i;
        }

        this.graph = graphSource.getGraph();
        this.parameters = parameters;

        if (knowlegeBox != null) {
            this.knowledge = knowlegeBox.getKnowledge();
        }
    }

    /**
     * Generates a simple exemplar to test serialization. Note that this returns a Knowledge object, not a
     * VertexCheckIndTestModel.
     *
     * @return a new, empty Knowledge object.
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    private static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<>(xs);
        copy.sort(Double::compare);
        int n = copy.size();
        if (n % 2 == 1) {
            return copy.get(n / 2);
        } else {
            return 0.5 * (copy.get(n / 2 - 1) + copy.get(n / 2));
        }
    }

    private static double getKolomogorovP(List<Double> pvals) {
        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    /**
     * Tests a list of p-values against the Anderson-Darling Test.
     *
     * @param pValues the list of p-values to be tested
     * @return the p-value obtained from the Anderson-Darling Test
     */
    public static double getAndersonDarlingP(List<Double> pValues) {
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        // A-squared evaluated against getProbTail, not getP(). The null here is a fully specified Uniform(0, 1)
        // with no estimated parameters (case 0), but getP() returns Stephens' case-3 approximation, which both
        // inflates the statistic by (1 + 0.75/n + 2.25/n^2) and evaluates it with the piecewise formulas for
        // testing normality with an estimated mean and variance. On exactly uniform p-values that combination
        // rejects at roughly 52-63% against a nominal 5%, and unlike a small-sample correction the error does not
        // decay with n. See MarkovCheck.calcStats and TestAndersonDarlingCalibration.
        return 1. - generalAndersonDarlingTest.getProbTail(pValues.size(), generalAndersonDarlingTest.getASquared());
    }

    /**
     * Computes the independence facts implied by the graph for the given vertex, using the given type of
     * conditioning set.
     *
     * @param alignedGraph        the graph, with nodes aligned to the test's variables.
     * @param x                   the vertex.
     * @param conditioningSetType the type of conditioning set.
     * @return the implied facts for the vertex.
     */
    public static List<IndependenceFact> computeImpliedFactsForVertex(Graph alignedGraph, Node x, ConditioningSetType conditioningSetType) {
        return MarkovCheck.computeImpliedFactsForVertex(alignedGraph, x, conditioningSetType);
    }

    /**
     * Returns the uniformity p-value of the given p-values under the selected uniformity test (Anderson-Darling
     * or Kolmogorov-Smirnov). For the wild bootstrap, which does not use a p-value list, NaN is returned.
     *
     * @param pvals the p-values.
     * @return the uniformity p-value, or NaN if there are fewer than two p-values.
     */
    public double getUniformityP(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        return switch (getUniformityTest()) {
            case ANDERSON_DARLING -> getAndersonDarlingP(pvals);
            // Wild bootstrap consumes facts + data, not a p-value list; it is computed at the
            // fact-bearing call sites (see getModelSummary). NaN here is a defensive guard.
            case WILD_BOOTSTRAP -> Double.NaN;
            default -> getKolomogorovP(pvals);
        };
    }

    /**
     * Runs the joint wild-bootstrap Markov check over an explicit fact list (requires row data).
     * Returns null if the data model is not a DataSet or there are no facts.
     */
    private WildBootstrapMarkovCheck.Result runWildBootstrap(List<IndependenceFact> facts) throws InterruptedException {
        if (!(dataModel instanceof DataSet ds)) return null;
        if (facts == null || facts.isEmpty()) return null;
        return new WildBootstrapMarkovCheck(ds)
                .setNumBootstraps(parameters.getInt("wbNumBootstrapsParam", 1000))
                .setSeed(parameters.getInt("wbSeedParam", 0))
                .checkFacts(facts);
    }

    /**
     * Per-vertex wild-bootstrap omnibus p-value (sum T^2) over a fact list, or NaN when the
     * data is not a DataSet, there are fewer than two facts, or the run is interrupted. Unlike
     * KS/AD this reads residual products from the data directly and ignores the CI test's p-values.
     */
    private double wildBootstrapP(List<IndependenceFact> facts) {
        if (!(dataModel instanceof DataSet) || facts == null || facts.size() < 2) return Double.NaN;
        try {
            WildBootstrapMarkovCheck.Result r = runWildBootstrap(facts);
            return (r == null) ? Double.NaN : r.pSumSquares;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Double.NaN;
        }
    }

    /**
     * Returns the cache of independence queries.
     *
     * @return the cached queries.
     */
    public CachedIndependenceQueries getCachedQueries() {
        return cachedQueries;
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    /**
     * Sets the graph and fires a property change event for {@link #PROP_GRAPH}.
     *
     * @param g the graph.
     */
    public void setGraph(Graph g) {
        Graph old = this.graph;
        this.graph = g;
        pcs.firePropertyChange(PROP_GRAPH, old, g);
    }

    /**
     * Returns the data model being checked.
     *
     * @return the data model.
     */
    public DataModel getDataModel() {
        return dataModel;
    }

    /**
     * The data models that can be checked, in the order of the parent data box.
     *
     * @return the data models; just the one in use for sessions saved before the choice existed.
     */
    public List<DataModel> getDataModels() {
        return dataModels == null ? List.of(dataModel) : dataModels;
    }

    /**
     * The index, in getDataModels(), of the data model being checked.
     *
     * @return the index.
     */
    public int getDataIndex() {
        return dataModels == null ? 0 : dataIndex;
    }

    /**
     * Chooses the data model to check. Results, cached queries and the per-fact ESS block assignment all belong to
     * the previous data model and are dropped. The independence test in use was built on the previous data model
     * too, so the caller must build a new one and pass it to setIndependenceTest before running the check again.
     *
     * @param index the index, in getDataModels(), of the data model to check.
     */
    public void setDataIndex(int index) {
        if (index < 0 || index >= getDataModels().size()) {
            throw new IllegalArgumentException("No data model at index " + index + ".");
        }

        this.dataModel = getDataModels().get(index);
        this.dataIndex = index;
        this.essBlockIds = null;
        this.cachedQueries.clearCaches();
        clearResults();
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters.
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Returns the independence test.
     *
     * @return the independence test, or null if not set.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * Sets the independence test, sets its rows if it is row-settable and the data are tabular, rebinds the
     * cached queries to it, and clears all results.
     *
     * @param test the independence test.
     */
    public void setIndependenceTest(IndependenceTest test) {
        this.independenceTest = test;

        // Row assignment only applies to row data. When the model was constructed with a
        // covariance matrix there are no rows to set; covariance-capable tests (e.g. Fisher Z)
        // compute directly from the matrix. Prior to 2026-8-16 the unguarded cast in
        // getSubsampleRows threw ClassCastException for covariance input. (At fraction 1.0
        // this sets ALL rows, shuffled — an identity for order-invariant tests — and is
        // retained for row data to preserve existing behavior exactly.)
        if (test instanceof RowsSettable && dataModel instanceof DataSet) {
            ((RowsSettable) test).setRows(getSubsampleRows(1.0));
        }

        cachedQueries.setTest(test);  // clears caches, rebuilds mapping
        clearResults();
    }

    /**
     * True if each implied fact is tested at its own block-based effective sample size (see
     * edu.cmu.tetrad.search.utils.PerFactEss). Added 2026-8-30.
     */
    private boolean perFactEss = false;
    /**
     * The block-defining columns for per-fact ESS.
     */
    private java.util.List<String> essBlockColumns = null;
    /**
     * Cached row-to-block assignment derived from the block columns.
     */
    private transient int[] essBlockIds = null;

    /**
     * Enables or disables per-fact effective sample sizes. Cached query results are cleared,
     * since cached p-values computed under a different regime are stale.
     *
     * @param perFactEss True to enable.
     */
    public void setPerFactEss(boolean perFactEss) {
        this.perFactEss = perFactEss;
        this.cachedQueries.clearCaches();
        clearResults();
    }

    /**
     * Returns whether per-fact effective sample sizes are enabled.
     *
     * @return True if per-fact effective sample sizes are enabled.
     */
    public boolean isPerFactEss() {
        return perFactEss;
    }

    /**
     * Sets the columns whose distinct joint values define the blocks for per-fact ESS.
     *
     * @param essBlockColumns The block-defining column names.
     */
    public void setEssBlockColumns(java.util.List<String> essBlockColumns) {
        this.essBlockColumns = essBlockColumns == null ? null
                : new java.util.ArrayList<>(essBlockColumns);
        this.essBlockIds = null;
        this.cachedQueries.clearCaches();
        clearResults();
    }

    /**
     * If per-fact ESS is enabled and applicable, sets the effective sample size for the given
     * fact's variables on the underlying independence test. Same fact always maps to the same
     * value, so cached query results remain consistent while the regime is unchanged. No-op if
     * disabled, if no block columns are set, if the test cannot set an effective sample size, or
     * if the data is not tabular.
     *
     * @param x The first variable.
     * @param y The second variable.
     * @param z The conditioning variables.
     */
    public void applyPerFactEss(Node x, Node y, java.util.Set<Node> z) {
        if (!perFactEss || essBlockColumns == null || essBlockColumns.isEmpty()) return;
        if (!(independenceTest instanceof edu.cmu.tetrad.util.EffectiveSampleSizeSettable settable)) return;
        if (!(dataModel instanceof DataSet dataSet)) return;

        if (essBlockIds == null) {
            essBlockIds = edu.cmu.tetrad.search.utils.PerFactEss.blockIds(dataSet, essBlockColumns);
        }

        settable.setEffectiveSampleSize(
                edu.cmu.tetrad.search.utils.PerFactEss.effectiveSampleSize(dataSet, essBlockIds, x, y, z));
    }

    /**
     * Returns a list of row indices for a subsample of the data set.
     *
     * @param v The fraction of the data set to use.
     * @return A list of row indices for a subsample of the data set.
     */
    private List<Integer> getSubsampleRows(double v) {
        int sampleSize = ((DataSet) dataModel).getNumRows();
        int subsampleSize = (int) TMath.floor(sampleSize * v);
        List<Integer> rows = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            rows.add(i);
        }

        Collections.shuffle(rows);
        List<Integer> integers = rows.subList(0, subsampleSize);

        List<Integer> selectedRows = new ArrayList<>(integers.size());

        for (int row : rows) {
            if (integers.contains(row)) {
                selectedRows.add(row);
            }
        }

        return selectedRows;
    }

    // --- Core API used by the editor ------------------------------------------------------------

    /**
     * Returns the conditioning set type, stored in the parameters; defaults to ORDERED_LOCAL_MARKOV_PROPERTY.
     *
     * @return the conditioning set type.
     */
    public ConditioningSetType getConditioningSetType() {
        return (ConditioningSetType) this.parameters.get("conditioningSetParam", ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
    }

    /**
     * Stores the conditioning set type in the parameters.
     *
     * @param conditioningSetType the conditioning set type.
     */
    public void setConditioningSetType(ConditioningSetType conditioningSetType) {
        this.parameters.set("conditioningSetParam", conditioningSetType);
    }

    /**
     * Returns the knowledge.
     *
     * @return the knowledge.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge to a copy of the given knowledge.
     *
     * @param knowledge the knowledge; null is replaced by empty knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge.copy();
    }

    /**
     * Returns whether verbose output is logged.
     *
     * @return true if verbose.
     */
    public boolean isVerbose() {
        return verbose;
    }

    // --- Implementation -------------------------------------------------------------------------

    /**
     * Sets whether verbose output is logged.
     *
     * @param verbose true if verbose.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Clears all cached results.
     */
    public void clearResults() {
        summariesByVertex.clear();
        resultsByVertex.clear();
        modelSummary = null;
        cachedQueries.clearCaches();
    }

    /**
     * Runs the vertex checker for all vertices in the graph.
     * Requires an IndependenceTest to have been set.
     *
     * @param clear whether to clear existing results first.
     */
    public void runAllVertices(boolean clear) {
        runAllVertices(clear, () -> false);
    }

    /**
     * Runs the vertex checker for all vertices in the graph, stopping early if cancelled, and then computes the
     * model-level summary. Data variables not in the graph are skipped. Requires an IndependenceTest to have been
     * set.
     *
     * @param clear     whether to clear existing results first.
     * @param cancelled returns true if the run should be cancelled.
     * @throws IllegalStateException if the independence test has not been set.
     */
    public void runAllVertices(boolean clear, BooleanSupplier cancelled) {
        if (independenceTest == null) {
            throw new IllegalStateException("IndependenceTest has not been set.");
        }

        if (clear) {
            clearResults();
        }

        // Ensure node objects align between test variables and the graph.
        // (Same approach as MarkovCheck: replace graph nodes by test variables by name.)
        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        List<Node> vars = new ArrayList<>(independenceTest.getVariables());
        vars.sort(Comparator.comparing(Node::getName));

        // Data variables absent from the graph are skipped, not failed on (added
        // 2026-9-9). The overview lists every data variable, but a graph learned on or
        // edited to a subset of the data columns implies no facts about the missing
        // ones; their rows stay blank. Before this, the first such variable killed the
        // whole sweep (NPE deep in the ordered-local-Markov fact computation), leaving
        // EVERY row blank -- e.g. a fire-weather data set containing BUI checked
        // against a graph from which BUI was excluded for near-determinism.
        List<String> notInGraph = new ArrayList<>();
        for (Node x : vars) {
            if (cancelled.getAsBoolean()) return;     // skip vertices not yet started
            if (alignedGraph.getNode(x.getName()) == null) {
                notInGraph.add(x.getName());
                continue;
            }
            runVertex(alignedGraph, x, cancelled);
        }
        if (!notInGraph.isEmpty()) {
            TetradLogger.getInstance().log("Vertex check: " + notInGraph.size()
                    + " data variable(s) not in the graph were skipped (no implied facts): "
                    + String.join(", ", notInGraph));
        }

        // Compute the model-level summary HERE, on the worker thread, so the
        // (potentially expensive) wild bootstrap never runs on the EDT. done()
        // then just reads the cached value.
        if (cancelled.getAsBoolean()) return;
        try {
            getModelSummary(cancelled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();       // cancelled: leave modelSummary null
        }
    }
    /**
     * Runs the vertex checker for one vertex name (if present).
     *
     * @param vertexName the name of the vertex.
     * @param clearFirst whether to clear existing results first.
     * @throws IllegalStateException    if the independence test has not been set.
     * @throws IllegalArgumentException if the vertex is not in the graph.
     */
    public void runVertexByName(String vertexName, boolean clearFirst) {
        if (independenceTest == null) {
            throw new IllegalStateException("IndependenceTest has not been set.");
        }
        if (clearFirst) {
            clearResults();
        }

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        if (x == null) {
            throw new IllegalArgumentException("Vertex not found in graph: " + vertexName);
        }
        runVertex(alignedGraph, x);
    }

    /**
     * Returns the summaries of all computed vertices.
     *
     * @return the vertex summaries.
     */
    public List<VertexSummary> getSummaries() {
        return new ArrayList<>(summariesByVertex.values());
    }

    /**
     * Returns the summary for the given vertex.
     *
     * @param vertexName the name of the vertex.
     * @return the summary, or null if the vertex has not been computed.
     */
    public VertexSummary getSummary(String vertexName) {
        return summariesByVertex.get(vertexName);
    }

    /**
     * Returns the independence results for the given vertex.
     *
     * @param vertexName the name of the vertex.
     * @return the results, or an empty list if the vertex has not been computed.
     */
    public List<IndependenceResult> getResultsForVertex(String vertexName) {
        return resultsByVertex.getOrDefault(vertexName, List.of());
    }

    /**
     * Counts, over all computed vertices, the implied facts tested so far and how many of them came back with
     * no p-value (NaN). Under the caching layer's TREAT_AS_INDEPENDENT error policy, a fact whose test throws
     * is recorded as independent with a NaN p-value, so a large NaN count means the chosen test could not
     * actually be run on those facts (e.g., too few usable rows after deletion of missing values) and the
     * displayed judgments for them carry no evidence. Interfaces use this to warn instead of staying silent.
     *
     * @return An array {total facts, facts with NaN p-value}.
     */
    public int[] countUntestableFacts() {
        int total = 0;
        int untestable = 0;

        for (List<IndependenceResult> rs : resultsByVertex.values()) {
            for (IndependenceResult r : rs) {
                total++;
                if (Double.isNaN(r.getPValue())) untestable++;
            }
        }

        return new int[]{total, untestable};
    }

    /**
     * Returns the message of the most recent test error swallowed by the caching layer's error policy, as an
     * example of why facts came back with no p-value, or null if none.
     *
     * @return The message, or null.
     */
    public String getUntestableExample() {
        return cachedQueries.getLastErrorMessage();
    }

    private void runVertex(Graph alignedGraph, Node x) {
        runVertex(alignedGraph, x, () -> false);
    }

    private void runVertex(Graph alignedGraph, Node x, BooleanSupplier cancelled) {
        List<IndependenceFact> impliedFacts = computeImpliedFactsForVertex(alignedGraph, x,
                getConditioningSetType());

        List<IndependenceResult> results = new ArrayList<>(impliedFacts.size());
        List<Double> pvals = new ArrayList<>();

        double alpha = independenceTest.getAlpha(); // for score convention you’re using

        int tried = 0;
        int ok = 0;

        for (IndependenceFact fact : impliedFacts) {
            if (cancelled.getAsBoolean()) return;     // bail; store nothing partial for x
            tried++;

            // Cached eval keyed by (X,Y|Z) via name->id maps; rebinding handled internally.
            applyPerFactEss(fact.getX(), fact.getY(), fact.getZ());
            CachedIndependenceQueries.Eval e = cachedQueries.eval(fact);

            double p = e.pValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                ok++;
                pvals.add(p);
            }

            // Keep your implied fact for display (graph semantics), not the rebinding fact.
            double score = Double.isNaN(p) ? -alpha : (alpha - p);  // matches CachedIndependenceQueries.checkIndependence()
            results.add(new IndependenceResult(fact, e.independent(), p, score));

            if (verbose) {
                TetradLogger.getInstance().log("VertexCheck: " + fact + "  p=" + p);
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("VertexCheck: x=" + x.getName() + " tried=" + tried + " okP=" + ok);
        }

        double wbP = (!cancelled.getAsBoolean() && WILD_BOOTSTRAP.equals(getUniformityTest()))
                ? wildBootstrapP(impliedFacts)
                : Double.NaN;

        VertexSummary summary = summarizeVertex(
                x.getName(),
                /* csSize */ -1, // or conditioningSetSizeForSummary(impliedFacts)
                results,
                pvals,
                wbP
        );

        summariesByVertex.put(x.getName(), summary);
        resultsByVertex.put(x.getName(), results);
        // NB: do NOT null modelSummary here — it depends on the graph, not on which
        // vertices are computed. clearResults() invalidates it when the graph/test/
        // conditioning/uniformity actually changes.
    }

    private VertexSummary summarizeVertex(String vertexName, int csSize,
                                          List<IndependenceResult> results, List<Double> pvals, double wbP) {

        int n = pvals.size();

        double ksP = Double.NaN;
        double adP = Double.NaN;
        double binP = Double.NaN;
        double fishP = Double.NaN;

        if (n >= 2) {
            adP = getAndersonDarlingP(pvals);
            ksP = getKolomogorovP(pvals);
            fishP = getFisherCombinedP(pvals);
            binP = getBinomialP(pvals);
        }

        double alpha = independenceTest.getAlpha();
        long numReject = pvals.stream().filter(p -> p <= alpha).count();
        double fracReject = (n == 0) ? Double.NaN : (numReject / (double) n);

        double minP = pvals.stream().min(Double::compare).orElse(Double.NaN);
        double medianP = median(pvals);

        return new VertexSummary(vertexName, csSize, results.size(), n, ksP, adP, binP, fishP,
                fracReject, numReject, minP, medianP, wbP);
    }

    /**
     * Calculates the combined p-value using Fisher's method for a given list of independence test results. Fisher's
     * method is used to combine independent p-values from multiple tests to determine overall significance.
     *
     * @param pvals a list of p-values from independence tests
     * @return the combined p-value. If the inputs are invalid or computation fails, returns Double.NaN.
     */
    public double getFisherCombinedP(List<Double> pvals) {

        double sum = 0.0;

        for (double pValue : pvals) {
            double p = TMath.max(pValue, 1e-300);
            sum += TMath.log(p);
        }

        double c = -2.0 * sum;
        int m = pvals.size();

        if (m > 0 && (Double.isNaN(c) || c == Double.NEGATIVE_INFINITY)) {
            return Double.NaN;
        } else if (m > 0 && c == Double.POSITIVE_INFINITY) {
            return 0.0;
        } else if (m > 0 && !(Double.isNaN(c))) {
            return StatUtils.getChiSquareP(2 * m, c);
        } else {
            return Double.NaN;
        }
    }

    /**
     * Returns a Binomial p-value for the hypothesis that the distribution of p-values is not Uniform under the null
     * hypothesis. Values less than alpha imply non-uniform distributions.
     *
     * @param pValues The p-values.
     * @return The Binomial p-value for non-uniformity.
     */
    private double getBinomialP(List<Double> pValues) {
        int n = pValues.size();
        double q = independenceTest.getAlpha();
        int k = (int) pValues.stream().filter(p -> p <= q).count();

        BinomialDistribution bd = new BinomialDistribution(n, q);

        double leftTail = bd.cumulativeProbability(k);
        double rightTail = 1.0 - bd.cumulativeProbability(k - 1);
        double pValue = TMath.min(1.0, 2.0 * TMath.min(leftTail, rightTail));

        return pValue;
    }

    @Override
    public Graph getSourceGraph() {
        return null;
    }

    @Override
    public Graph getResultGraph() {
        return null;
    }

    @Override
    public List<Node> getVariables() {
        return null;
    }

    @Override
    public List<String> getVariableNames() {
        return null;
    }

    /**
     * Returns the names of the vertices to check, in natural sort order. If none are stored, they are taken from
     * the independence test's variables.
     *
     * @return the vertex names; empty if no independence test is set.
     */
    public List<String> getVertexNames() {
        if (vertexNames == null || vertexNames.isEmpty()) {
            if (independenceTest == null) return List.of();

            List<Node> variables = independenceTest.getVariables();

            vertexNames = variables.stream()
                    .map(Node::getName)
                    .toList();
        }

        List<String> _vertexNames = new ArrayList<>(vertexNames);
        _vertexNames.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        return _vertexNames;
    }

    /**
     * Returns whether the given vertex has been computed.
     *
     * @param vertexName the name of the vertex.
     * @return true if the vertex has been computed.
     */
    public boolean isVertexComputed(String vertexName) {
        return summariesByVertex.containsKey(vertexName);
    }

    /**
     * Computes the given vertex if it has not been computed yet. Does nothing if the vertex is not in the graph.
     *
     * @param vertexName the name of the vertex.
     * @throws IllegalStateException if the independence test has not been set.
     */
    public void ensureVertexComputed(String vertexName) {
        if (independenceTest == null) {
            throw new IllegalStateException("IndependenceTest has not been set.");
        }
        if (isVertexComputed(vertexName)) return;

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        // Lenient for the UI (changed 2026-9-9 from a throw): the overview lists every
        // data variable, so clicking the row of one not in the graph is a legitimate
        // action; there is simply nothing to compute for it, and its row stays blank.
        if (x == null) {
            TetradLogger.getInstance().log("Vertex check: '" + vertexName
                    + "' is not in the graph; nothing to compute.");
            return;
        }

        runVertex(alignedGraph, x);
    }

    /**
     * Returns the minimum conditioning set size for the given vertex, from stored results if computed, otherwise
     * from the implied facts without running tests.
     *
     * @param vertexName the name of the vertex.
     * @return the minimum conditioning set size, or -1 if it cannot be determined.
     */
    public int getMinConditioningSetSizeFast(String vertexName) {
        ConditioningSetSizeRange r = getConditioningSetSizeRangeFast(vertexName);
        return r.min();
    }


    /**
     * Returns the maximum conditioning set size for the given vertex, from stored results if computed, otherwise
     * from the implied facts without running tests.
     *
     * @param vertexName the name of the vertex.
     * @return the maximum conditioning set size, or -1 if it cannot be determined.
     */
    public int getMaxConditioningSetSizeFast(String vertexName) {
        ConditioningSetSizeRange r = getConditioningSetSizeRangeFast(vertexName);
        return r.max();
    }

    private ConditioningSetSizeRange getConditioningSetSizeRangeFast(String vertexName) {

        // ---- Case 1: already computed → cheap and exact ----
        if (resultsByVertex.containsKey(vertexName)) {
            List<IndependenceResult> results = resultsByVertex.get(vertexName);
            if (results.isEmpty()) return new ConditioningSetSizeRange(0, 0);

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;

            for (IndependenceResult r : results) {
                int sz = r.getFact().getZ().size();
                min = TMath.min(min, sz);
                max = TMath.max(max, sz);
            }
            return new ConditioningSetSizeRange(min, max);
        }

        // ---- Case 2: compute implied facts only (no CI tests) ----
        if (independenceTest == null) return new ConditioningSetSizeRange(-1, -1);

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        if (x == null) return new ConditioningSetSizeRange(-1, -1);

        List<IndependenceFact> facts = computeImpliedFactsForVertex(alignedGraph, x,
                (ConditioningSetType) parameters.get("conditioningSetType",
                        ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY));
        if (facts.isEmpty()) return new ConditioningSetSizeRange(0, 0);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (IndependenceFact f : facts) {
            int sz = f.getZ().size();
            min = TMath.min(min, sz);
            max = TMath.max(max, sz);
        }

        return new ConditioningSetSizeRange(min, max);
    }

//    public boolean hasViolationsForVertex(Node x) {
//        if (x == null || graph == null || independenceTest == null) {
//            return false;
//        }
//
//        String name = x.getName();
//
//        // Only compute if needed
//        if (!isVertexComputed(name)) {
//            Graph aligned = alignGraphToTest(graph);
//
//            Node ax = aligned.getNode(name);
//            if (ax == null) {
//                return false; // should not happen, but safe
//            }
//
//            runVertex(aligned, ax);
//        }
//
//        VertexSummary s = summariesByVertex.get(name);
//        return s != null && s.numReject > 0;
//    }

//    private Graph alignGraphToTest(Graph g) {
//        IndependenceTest test = this.independenceTest;
//        if (g == null || test == null) return g;
//        return GraphUtils.replaceNodes(g, test.getVariables());
//    }

    /**
     * Adds a property change listener.
     *
     * @param l the listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    /**
     * Cached value or null; never triggers (re)computation. Safe to call on the EDT.
     *
     * @return the cached model summary, or null.
     */
    public ModelSummary peekModelSummary() {
        return modelSummary;
    }

    /**
     * Returns the model-level summary, computing it if necessary.
     *
     * @return the model summary.
     * @throws InterruptedException if the computation is interrupted.
     */
    public ModelSummary getModelSummary() throws InterruptedException {
        return getModelSummary(() -> false);
    }

    /**
     * Returns the model-level summary, computing it if necessary. The p-value is the selected uniformity test
     * applied to all implied facts of the graph, or the wild-bootstrap p-value if that test is selected.
     *
     * @param cancelled returns true if the computation should be cancelled.
     * @return the model summary.
     * @throws InterruptedException if the computation is cancelled or interrupted.
     */
    public ModelSummary getModelSummary(BooleanSupplier cancelled) throws InterruptedException {
        if (modelSummary != null) return modelSummary;

        ConditioningSetType conditioningSetType = getConditioningSetType();
        Set<IndependenceFact> impliedFacts = MarkovCheck.computeAllImpliedFacts(graph, conditioningSetType);
        double modelP;
        int numUsed;
        if (WILD_BOOTSTRAP.equals(getUniformityTest())) {
            // WB reads residual products from the data directly; the CI-test p-value loop
            // is not needed here, so skip it (it was the main wasted work in WB mode).
            WildBootstrapMarkovCheck.Result wb = runWildBootstrap(new ArrayList<>(impliedFacts));
            modelP = (wb == null) ? Double.NaN : wb.pSumSquares;   // wb.pMax also available
            numUsed = (wb == null) ? 0 : wb.numConstraints;
        } else {
            List<Double> pvals = new ArrayList<>();
            for (IndependenceFact fact : impliedFacts) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");

                // Cached eval keyed by (X,Y|Z) via name->id maps; rebinding handled internally.
                applyPerFactEss(fact.getX(), fact.getY(), fact.getZ());
                CachedIndependenceQueries.Eval e = cachedQueries.eval(fact);

                double p = e.pValue();
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    pvals.add(p);
                }
            }
            modelP = getUniformityP(pvals);
            numUsed = pvals.size();
        }
        
        modelSummary = new ModelSummary(numUsed, modelP);
        return modelSummary;
    }

    private @NotNull List<Double> getDedupedPvalues() {
        Map<IndependenceFact, Double> map = new HashMap<>();

        for (String v : getVertexNames()) {
            VertexSummary s = getSummary(v);
            if (s == null) continue;
            // better: pull raw p-values from stored results
            for (IndependenceResult r : getResultsForVertex(v)) {
                double p = r.getPValue();
                if (!Double.isNaN(p) && p >= 0 && p <= 1) {
                    map.put(r.getFact(), r.getPValue());
                }
            }
        }

        List<Double> pvals = new ArrayList<>(map.values());
        return pvals;
    }

    private Node var(String name) {
        if (name == null) return null;

        // prefer cachedQueries' test if set, else fall back
        IndependenceTest t = cachedQueries.getTest();
        if (t == null) t = independenceTest;
        if (t == null) return null;

        return t.getVariable(name);
    }

    private Set<Node> varSet(Set<Node> z) {
        if (z == null || z.isEmpty()) return Set.of();
        Set<Node> out = new LinkedHashSet<>(z.size());
        for (Node n : z) {
            Node v = var(n.getName());
            if (v != null) out.add(v);
        }
        return out;
    }

    /**
     * Returns the selected uniformity test, stored in the parameters; defaults to Kolmogorov-Smirnov.
     *
     * @return the uniformity test: KOLMOGOROV_SMIRNOFF, ANDERSON_DARLING or WILD_BOOTSTRAP.
     */
    public String getUniformityTest() {
        return parameters.getString(UNIFORMITY_TEST_PARAM, KOLMOGOROV_SMIRNOFF);
    }

    /**
     * Stores the selected uniformity test in the parameters.
     *
     * @param mode the uniformity test: KOLMOGOROV_SMIRNOFF, ANDERSON_DARLING or WILD_BOOTSTRAP.
     */
    public void setUniformityTest(String mode) {
        parameters.set(UNIFORMITY_TEST_PARAM, mode);
    }

    /**
     * The per-vertex uniformity p-value for the currently selected method: KS ({@code modelP}),
     * AD ({@code asP}), or the per-vertex wild-bootstrap omnibus ({@code wbP}). This is what the
     * overview table's single "PV" column shows. NaN if not computed / not applicable.
     *
     * @param s the vertex summary; may be null.
     * @return the selected per-vertex p-value.
     */
    public double selectedVertexP(VertexSummary s) {
        if (s == null) return Double.NaN;
        return switch (getUniformityTest()) {
            case ANDERSON_DARLING -> s.asP();
            case WILD_BOOTSTRAP -> s.wbP();
            default -> s.modelP();
        };
    }

    /**
     * Sets the uniformity test to Anderson-Darling or Kolmogorov-Smirnov.
     *
     * @param useAndersonDarling true for Anderson-Darling, false for Kolmogorov-Smirnov.
     * @deprecated use {@link #setUniformityTest(String)}.
     */
    @Deprecated
    public void setUseAndersonDarling(boolean useAndersonDarling) {
        setUniformityTest(useAndersonDarling ? ANDERSON_DARLING : KOLMOGOROV_SMIRNOFF);
    }

    /**
     * Returns the saved name of the independence test class, stored in the parameters.
     *
     * @return the class name, or null if none is saved.
     */
    public String getSavedClassName() {
        return this.parameters.getString("independenceTestClassParam", null);
    }

    /**
     * Stores the name of the independence test class in the parameters.
     *
     * @param name the class name.
     */
    public void setSavedClassName(String name) {
        this.parameters.set("independenceTestClassParam", name);
    }

    /**
     * Returns whether the uniformity test is Anderson-Darling.
     *
     * @return true if the uniformity test is Anderson-Darling.
     * @deprecated use {@link #getUniformityTest()}.
     */
    @Deprecated
    public boolean isUseAndersonDarling() {
        return ANDERSON_DARLING.equals(getUniformityTest());
    }

    private record ConditioningSetSizeRange(int min, int max) {
    }

    /**
     * A per-vertex summary of the check.
     *
     * @param vertex              the name of the vertex.
     * @param conditioningSetSize the conditioning set size; -1 if not recorded.
     * @param numFactsTotal       includes weird p-values too
     * @param numPValuesUsed      p in [0,1]
     * @param modelP              the Kolmogorov-Smirnov uniformity p-value.
     * @param asP                 the Anderson-Darling uniformity p-value.
     * @param binP                the binomial p-value for the number of rejections at alpha.
     * @param fishP               the Fisher combined p-value.
     * @param fractionReject      the fraction of p-values at or below alpha.
     * @param numReject           the number of p-values at or below alpha.
     * @param minP                the minimum p-value.
     * @param medianP             the median p-value.
     * @param wbP                 the per-vertex wild-bootstrap omnibus p-value; NaN if not computed.
     */
    public record VertexSummary(String vertex, int conditioningSetSize, int numFactsTotal, int numPValuesUsed,
                                double modelP, double asP, double binP, double fishP,
                                double fractionReject, long numReject, double minP,
                                double medianP, double wbP) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 2L;
    }

    /**
     * A model-level summary of the check.
     *
     * @param numPValues the number of p-values used.
     * @param modelP     the model-level uniformity p-value.
     */
    public record ModelSummary(int numPValues, double modelP) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
    }
}