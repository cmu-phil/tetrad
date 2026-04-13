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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import edu.cmu.tetrad.util.TMath;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serial;
import java.util.*;

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

    public static final String PROP_GRAPH = "graph";
    @Serial
    private static final long serialVersionUID = 1L;
    private final DataModel dataModel;
    private final Parameters parameters;
    private final Map<String, VertexSummary> summariesByVertex = new LinkedHashMap<>();
    private final Map<String, List<IndependenceResult>> resultsByVertex = new LinkedHashMap<>();
    private final CachedIndependenceQueries cachedQueries =
            new CachedIndependenceQueries(CachedIndependenceQueries.ErrorPolicy.TREAT_AS_INDEPENDENT);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private Graph graph;
    private String name = "";
    private transient IndependenceTest independenceTest;
    private ConditioningSetType conditioningSetType = ConditioningSetType.MARKOV_BLANKET;
    private Knowledge knowledge = new Knowledge();
    private List<String> vertexNames = new ArrayList<>();
    private boolean verbose = false;
    private ModelSummary modelSummary;

    // This controls whether the Vertex checker pays attention to Anderson-Darling or Kolomogorov-Smirnov uniformity
    // tests. Please keep this set to false unless you know what you're doing.
    private boolean useAndersonDarling = false;
//
//    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
//        this(dataModel, graphSource, null, parameters);
//    }
//
//    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, KnowledgeBoxModel knowledgeBox,
//                                   Parameters parameters) {
//        this.dataModel = dataModel.getSelectedDataModel();
//        this.graph = graphSource.getGraph();
//        this.parameters = parameters;
//
//        if (knowledgeBox != null) {
//            this.knowledge = knowledgeBox.getKnowledge();
//        }
//    }


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
     * @param knowlegeBox a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, KnowledgeBoxModel knowlegeBox,
                                   Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graphSource.getGraph();
        this.parameters = parameters;

        if (knowlegeBox != null) {
            this.knowledge = knowlegeBox.getKnowledge();
        }
    }

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

    public double getUniformityP(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        if (useAndersonDarling) {
            return getAndersonDarlingP(pvals);
        } else {
            return getKolomogorovP(pvals);
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
        return generalAndersonDarlingTest.getP();
    }

    public CachedIndependenceQueries getCachedQueries() {
        return cachedQueries;
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph g) {
        Graph old = this.graph;
        this.graph = g;
        pcs.firePropertyChange(PROP_GRAPH, old, g);
    }

    public DataModel getDataModel() {
        return dataModel;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public void setIndependenceTest(IndependenceTest test) {
        this.independenceTest = test;
        cachedQueries.setTest(test);  // clears caches, rebuilds mapping
        clearResults();

        List<Integer> rows = getSubsampleRows(1.0);
        cachedQueries.setRows(rows); // FisherZ will only calc pvalues to those rows

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


    public ConditioningSetType getConditioningSetType() {
        return conditioningSetType;
    }

    // --- Core API used by the editor ------------------------------------------------------------

    public void setConditioningSetType(ConditioningSetType conditioningSetType) {
        this.conditioningSetType = conditioningSetType;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge.copy();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // --- Implementation -------------------------------------------------------------------------

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
     */
    public void runAllVertices(boolean clearFirst) {
        if (independenceTest == null) {
            throw new IllegalStateException("IndependenceTest has not been set.");
        }

        if (clearFirst) {
            clearResults();
        }

        // Ensure node objects align between test variables and the graph.
        // (Same approach as MarkovCheck: replace graph nodes by test variables by name.)
        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        List<Node> vars = new ArrayList<>(independenceTest.getVariables());
        vars.sort(Comparator.comparing(Node::getName));

        for (Node x : vars) {
            runVertex(alignedGraph, x);
        }
    }

    /**
     * Runs the vertex checker for one vertex name (if present).
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

    public List<VertexSummary> getSummaries() {
        return new ArrayList<>(summariesByVertex.values());
    }

    public VertexSummary getSummary(String vertexName) {
        return summariesByVertex.get(vertexName);
    }

    public List<IndependenceResult> getResultsForVertex(String vertexName) {
        return resultsByVertex.getOrDefault(vertexName, List.of());
    }

    private void runVertex(Graph alignedGraph, Node x) {
        List<IndependenceFact> impliedFacts = computeImpliedFactsForVertex(alignedGraph, x, conditioningSetType);

        List<IndependenceResult> results = new ArrayList<>(impliedFacts.size());
        List<Double> pvals = new ArrayList<>();

        double alpha = independenceTest.getAlpha(); // for score convention you’re using

        int tried = 0;
        int ok = 0;

        for (IndependenceFact fact : impliedFacts) {
            tried++;

            // Cached eval keyed by (X,Y|Z) via name->id maps; rebinding handled internally.
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

        VertexSummary summary = summarizeVertex(
                x.getName(),
                /* csSize */ -1, // or conditioningSetSizeForSummary(impliedFacts)
                results,
                pvals
        );

        summariesByVertex.put(x.getName(), summary);
        resultsByVertex.put(x.getName(), results);

        // model-level cached summary is now stale
        modelSummary = null;
    }

    private VertexSummary summarizeVertex(String vertexName, int csSize,
                                          List<IndependenceResult> results, List<Double> pvals) {

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
                fracReject, numReject, minP, medianP);
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

    public static List<IndependenceFact> computeImpliedFactsForVertex(Graph alignedGraph, Node x, ConditioningSetType conditioningSetType) {
        return MarkovCheck.computeImpliedFactsForVertex(alignedGraph, x, conditioningSetType);
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

    public boolean isVertexComputed(String vertexName) {
        return summariesByVertex.containsKey(vertexName);
    }

    public void ensureVertexComputed(String vertexName) {
        if (independenceTest == null) {
            throw new IllegalStateException("IndependenceTest has not been set.");
        }
        if (isVertexComputed(vertexName)) return;

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        if (x == null) throw new IllegalArgumentException("Vertex not found: " + vertexName);

        runVertex(alignedGraph, x);
    }

    public int getMinConditioningSetSizeFast(String vertexName) {
        ConditioningSetSizeRange r = getConditioningSetSizeRangeFast(vertexName);
        return r.min();
    }


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

        List<IndependenceFact> facts = computeImpliedFactsForVertex(alignedGraph, x, conditioningSetType);
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

    public boolean hasViolationsForVertex(Node x) {
        if (x == null || graph == null || independenceTest == null) {
            return false;
        }

        String name = x.getName();

        // Only compute if needed
        if (!isVertexComputed(name)) {
            Graph aligned = alignGraphToTest(graph);

            Node ax = aligned.getNode(name);
            if (ax == null) {
                return false; // should not happen, but safe
            }

            runVertex(aligned, ax);
        }

        VertexSummary s = summariesByVertex.get(name);
        return s != null && s.numReject > 0;
    }

    private Graph alignGraphToTest(Graph g) {
        IndependenceTest test = this.independenceTest;
        if (g == null || test == null) return g;
        return GraphUtils.replaceNodes(g, test.getVariables());
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public ModelSummary getModelSummary() {
        if (modelSummary != null) return modelSummary;

        List<Double> pvals = getDedupedPvalues();

        double modelP = getUniformityP(pvals);
        modelSummary = new ModelSummary(pvals.size(), modelP);
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

    public boolean getUseAndersonDarling() {
        return useAndersonDarling;
    }

    public void setUseAndersonDarling(boolean useAndersonDarling) {
        this.useAndersonDarling = useAndersonDarling;
    }

    private record ConditioningSetSizeRange(int min, int max) {
    }

    /**
     * @param numFactsTotal  includes weird p-values too
     * @param numPValuesUsed p in [0,1]
     */
    public record VertexSummary(String vertex, int conditioningSetSize, int numFactsTotal, int numPValuesUsed,
                                double modelP, double asP, double binP, double fishP,
                                double fractionReject, long numReject, double minP,
                                double medianP) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record ModelSummary(int numPValues, double modelP) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
    }
}