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
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.OrderedLocalMarkovProperty;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.BinomialTest;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model for a per-vertex ("local") Markov check, a.k.a. "Vertex Checker".
 * <p>
 * For each vertex X, constructs a conditioning set CS(X) (e.g., Markov blanket, parents, etc.),
 * and tests all claims Ind(X, Y | CS(X)) for Y not in CS(X) (and Y != X) using the chosen
 * IndependenceTest. The resulting p-values are then tested for Uniform(0,1) using KS,
 * producing a per-vertex KS p-value and summary diagnostics.
 * <p>
 * This is designed to support a Tetrad interface tool that highlights locally reliable regions
 * of an estimated graph relative to data.
 */
public class VertexCheckIndTestModel implements SessionModel, GraphSource, KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DataModel dataModel;
    private Graph graph;
    private final Parameters parameters;
    // Results
    private final Map<String, VertexSummary> summariesByVertex = new LinkedHashMap<>();
    private final Map<String, List<IndependenceResult>> resultsByVertex = new LinkedHashMap<>();
    //    private final Map<String, List<String>> conditioningSetByVertex = new LinkedHashMap<>();
    private String name = "";
    private transient IndependenceTest independenceTest;
    private ConditioningSetType conditioningSetType = ConditioningSetType.MARKOV_BLANKET;
    private Knowledge knowledge;
    private List<String> vertexNames = new ArrayList<>();
    private boolean verbose = false;
    // For RECURSIVE_MSEP-like options (optional; default -1 means no limit)
    private int maxLength = -1;

    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
        this(dataModel, graphSource, null, parameters);
    }

    public VertexCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, KnowledgeBoxModel knowledgeBox,
                                   Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graphSource.getGraph();
        this.parameters = parameters;

        if (knowledgeBox != null) {
            this.knowledge = knowledgeBox.getKnowledge();
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

    /// /            conditioningSetByVertex.put(xName, List.of()); // varying-Z; show Z per row in results table
//        }
//    }
    private static int conditioningSetSizeForSummary(List<IndependenceFact> impliedFacts) {
        // Preserve your existing "CS size" column semantics:
        // - uniform-Z: that size
        // - varying-Z: return -1 (or 0) and let UI show “varies”.
        Set<Set<Node>> distinct = impliedFacts.stream()
                .map(IndependenceFact::getZ)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinct.size() == 1) return distinct.iterator().next().size();
        return -1;
    }

    private static List<IndependenceFact> factsForUniformZ(Graph g, Node x, Set<Node> z) {
        List<IndependenceFact> out = new ArrayList<>();
        for (Node y : g.getNodes()) {
            if (y.equals(x)) continue;
            if (z.contains(y)) continue;
            if (g.isAdjacentTo(x, y)) continue;   // <-- NEW LINE
            out.add(new IndependenceFact(x, y, z));
        }
        return out;
    }

    @Override
    public Graph getGraph() {
        return graph;
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
        this.vertexNames = new ArrayList<>(); // invalidate
    }

    public ConditioningSetType getConditioningSetType() {
        return conditioningSetType;
    }

    public void setConditioningSetType(ConditioningSetType conditioningSetType) {
        this.conditioningSetType = conditioningSetType;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? null : knowledge.copy();
    }

    public boolean isVerbose() {
        return verbose;
    }

    // --- Core API used by the editor ------------------------------------------------------------

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
//        conditioningSetByVertex.clear();
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

    // --- Implementation -------------------------------------------------------------------------

//    public List<String> getConditioningSetForVertex(String vertexName) {
//        return conditioningSetByVertex.getOrDefault(vertexName, List.of());
//    }

//    private void runVertex(Graph alignedGraph, Node x) {
//        Set<Node> cs = computeConditioningSet(alignedGraph, x);
//        List<String> csNames = cs.stream().map(Node::getName).sorted().collect(Collectors.toList());
//        conditioningSetByVertex.put(x.getName(), csNames);
//
//        List<IndependenceResult> results = new ArrayList<>();
//        List<Double> pvals = new ArrayList<>();
//
//        for (Node y : alignedGraph.getNodes()) {
//            if (y.equals(x)) continue;
//            if (cs.contains(y)) continue;
//
//            try {
//                IndependenceResult r = independenceTest.checkIndependence(x, y, cs);
//
//                // Only keep well-formed p-values for uniformity testing.
//                double p = r.getPValue();
//                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
//                    results.add(new IndependenceResult(new IndependenceFact(x, y, cs), r.isIndependent(), p, r.getScore()));
//                    pvals.add(p);
//                } else {
//                    // Still record it as an IndependenceResult with whatever p is (editor can show it),
//                    // but skip for uniformity.
//                    results.add(new IndependenceResult(new IndependenceFact(x, y, cs), r.isIndependent(), p, r.getScore()));
//                }
//
//                if (verbose) {
//                    TetradLogger.getInstance().log("VertexCheck: " + x.getName() + " vs " + y.getName()
//                            + " | CS=" + csNames + "  p=" + p);
//                }
//            } catch (Exception ex) {
//                TetradLogger.getInstance().log("VertexCheck: error checking " + x.getName() + " _||_ " + y.getName()
//                        + " | CS(X): " + ex.getMessage());
//            }
//        }
//
//        // Compute summary stats
//        VertexSummary summary = summarizeVertex(x.getName(), cs.size(), results, pvals);
//        summariesByVertex.put(x.getName(), summary);
//        resultsByVertex.put(x.getName(), results);
//    }

    public VertexSummary getSummary(String vertexName) {
        return summariesByVertex.get(vertexName);
    }

//    private void storeConditioningSetSummary(String xName, List<IndependenceFact> impliedFacts) {
//        // If all facts share the same Z, store it; else store empty.
//        Set<Set<Node>> distinct = impliedFacts.stream()
//                .map(IndependenceFact::getZ)
//                .collect(Collectors.toCollection(LinkedHashSet::new));
//
//        if (distinct.size() == 1) {
//            Set<Node> z = distinct.iterator().next();
//            List<String> names = z.stream().map(Node::getName).sorted().toList();
////            conditioningSetByVertex.put(xName, names);
//        } else {

    public List<IndependenceResult> getResultsForVertex(String vertexName) {
        return resultsByVertex.getOrDefault(vertexName, List.of());
    }

    private void runVertex(Graph alignedGraph, Node x) {
        List<IndependenceFact> impliedFacts = computeImpliedFactsForVertex(alignedGraph, x);

//        List<IndependenceFact> impliedFacts = computeImpliedFactsForVertex(alignedGraph, x);
        TetradLogger.getInstance().log("VertexCheck: x=" + x.getName() + " g impliedFacts=" + impliedFacts.size());

        int tried = 0;
        int ok = 0;

        for (IndependenceFact fact : impliedFacts) {
            tried++;

            Node X = independenceTest.getVariable(fact.getX().getName());
            Node Y = independenceTest.getVariable(fact.getY().getName());

            Set<Node> Z = new HashSet<>();
            for (Node _z : fact.getZ()) {
                Z.add(independenceTest.getVariable(_z.getName()));
            }

            try {
                IndependenceResult r = independenceTest.checkIndependence(X, Y, Z);
                double p = r.getPValue();
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) ok++;
            } catch (Exception ex) {
                TetradLogger.getInstance().log("VertexCheck: exception for " + fact + " : " + ex);
            }
        }

        TetradLogger.getInstance().log("VertexCheck: x=" + x.getName() + " tried=" + tried + " okP=" + ok);

        // Conditioning-set “summary” for UI purposes only:
        // - If uniform-Z, store that Z.
        // - If varying-Z (e.g. OLMP), store empty and let the facts table carry Z.
//        storeConditioningSetSummary(x.getName(), impliedFacts);

        List<IndependenceResult> results = new ArrayList<>();
        List<Double> pvals = new ArrayList<>();

        for (IndependenceFact fact : impliedFacts) {
            Node y = fact.getY();
            Set<Node> z = fact.getZ();

            try {
                Node X = independenceTest.getVariable(fact.getX().getName());
                Node Y = independenceTest.getVariable(y.getName());

                Set<Node> Z = new HashSet<>();
                for (Node _z : z) {
                    Z.add(independenceTest.getVariable(_z.getName()));
                }


                IndependenceResult r = independenceTest.checkIndependence(X, Y, Z);

                double p = r.getPValue();
                IndependenceResult stored = new IndependenceResult(
                        fact,
                        r.isIndependent(),
                        p,
                        r.getScore()
                );
                results.add(stored);

                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    pvals.add(p);
                }

                if (verbose) {
                    TetradLogger.getInstance().log("VertexCheck: " + fact + "  p=" + p);
                }
            } catch (Exception ex) {
                TetradLogger.getInstance().log("VertexCheck: error checking " + fact + ": " + ex.getMessage());
            }
        }

//        VertexSummary summary = summarizeVertex(
//                x.getName(),
//                conditioningSetSizeForSummary(impliedFacts),
//                results,
//                pvals
//        );
//        summariesByVertex.put(x.getName(), summary);

        VertexSummary summary = summarizeVertex(
                x.getName(),
                /* csSize */ -1, // or conditioningSetSizeForSummary(impliedFacts) if you still want it
                results,
                pvals
        );
        summariesByVertex.put(x.getName(), summary);
        resultsByVertex.put(x.getName(), results);

    }

    private VertexSummary summarizeVertex(String vertexName, int csSize,
                                          List<IndependenceResult> results, List<Double> pvals) {

        int n = pvals.size();

        double ksP = Double.NaN;
        double adP = Double.NaN;
        double binP = Double.NaN;
        double fishP = Double.NaN;
        double aSquared = Double.NaN;
        double aSquaredStar = Double.NaN;


        GeneralAndersonDarlingTest _generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pvals, new UniformRealDistribution(0, 1));

        if (n >= 2) {
            aSquared = _generalAndersonDarlingTest.getASquared();
            aSquaredStar = _generalAndersonDarlingTest.getASquaredStar();
            adP = 1. - _generalAndersonDarlingTest.getProbTail(pvals.size(), aSquaredStar);
            ksP = UniformityTest.getKsPValue(pvals, 0, 1);
            fishP = getFisherCombinedPValue(pvals);
            binP = getBinomialPValue(pvals);
        }

        double alpha = independenceTest.getAlpha();
        long numReject = pvals.stream().filter(p -> p <= alpha).count();
        double fracReject = (n == 0) ? Double.NaN : (numReject / (double) n);

        double minP = pvals.stream().min(Double::compare).orElse(Double.NaN);
        double medianP = median(pvals);

        return new VertexSummary(vertexName, csSize, results.size(), n, ksP, adP, binP, fishP, aSquared, aSquaredStar,
                fracReject, numReject, minP, medianP);
    }

    /**
     * Calculates the combined p-value using Fisher's method for a given list of independence test results. Fisher's
     * method is used to combine independent p-values from multiple tests to determine overall significance.
     *
     * @param pvals a list of p-values from independence tests
     * @return the combined p-value. If the inputs are invalid or computation fails, returns Double.NaN.
     */
    public double getFisherCombinedPValue(List<Double> pvals) {

        double sum = 0.0;

        for (double pValue : pvals) {
            double p = Math.max(pValue, 1e-300);
            sum += Math.log(p);
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
    private double getBinomialPValue(List<Double> pValues) {
        int n = pValues.size();
        double q = independenceTest.getAlpha();
        int k = (int) pValues.stream().filter(p -> p <= q).count();

        BinomialDistribution bd = new BinomialDistribution(n, q);

        double leftTail = bd.cumulativeProbability(k);
        double rightTail = 1.0 - bd.cumulativeProbability(k - 1);
        double pValue = Math.min(1.0, 2.0 * Math.min(leftTail, rightTail));

        return pValue;
    }

    public List<IndependenceFact> computeImpliedFactsForVertex(Graph alignedGraph, Node x) {
        switch (conditioningSetType) {

            // ---------------- uniform-Z families ----------------

            case LOCAL_MARKOV: {
                Set<Node> z = new HashSet<>();
                for (Node w : alignedGraph.getAdjacentNodes(x)) {
                    if (alignedGraph.isParentOf(w, x)) z.add(w);
                }
                return factsForUniformZ(alignedGraph, x, z);
            }

            case PARENTS_AND_NEIGHBORS: {
                Set<Node> z = new HashSet<>();
                for (Node w : alignedGraph.getAdjacentNodes(x)) {
                    Edge e = alignedGraph.getEdge(w, x);
                    if (e != null && Edges.isUndirectedEdge(e)) z.add(w);
                    if (alignedGraph.isParentOf(w, x)) z.add(w);
                }
                return factsForUniformZ(alignedGraph, x, z);
            }

            case MARKOV_BLANKET: {
                Set<Node> z = GraphUtils.markovBlanket(x, alignedGraph);
                return factsForUniformZ(alignedGraph, x, z);
            }

            case ORDERED_LOCAL_MARKOV_MAG: {
                Graph mag;// = GraphTransforms.zhangMagFromPag(graph);

                if (graph.paths().isLegalDag()) {
                    mag = graph;
                } else if (graph.paths().isLegalCpdag()) {
                    mag = GraphTransforms.dagFromCpdag(alignedGraph);
                } else if (graph.paths().isLegalMag()) {
                    mag = graph;
                } else {
                    mag = GraphTransforms.zhangMagFromPag(alignedGraph);
                }

                Set<IndependenceFact> raw = OrderedLocalMarkovProperty.getModelForNode(mag, x);

                return new ArrayList<>(raw);
            }

            default:
                throw new IllegalArgumentException(
                        "Unsupported conditioning set type for VertexCheck: " + conditioningSetType
                );
        }
    }

    // --- Required by KnowledgeBoxInput / GraphSource (kept consistent with MarkovCheckIndTestModel) ----

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
            vertexNames = independenceTest.getVariables().stream()
                    .map(Node::getName)
                    .sorted()
                    .toList();
        }
        return vertexNames;
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
                min = Math.min(min, sz);
                max = Math.max(max, sz);
            }
            return new ConditioningSetSizeRange(min, max);
        }

        // ---- Case 2: compute implied facts only (no CI tests) ----
        if (independenceTest == null) return new ConditioningSetSizeRange(-1, -1);

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        if (x == null) return new ConditioningSetSizeRange(-1, -1);

        List<IndependenceFact> facts = computeImpliedFactsForVertex(alignedGraph, x);
        if (facts.isEmpty()) return new ConditioningSetSizeRange(0, 0);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (IndependenceFact f : facts) {
            int sz = f.getZ().size();
            min = Math.min(min, sz);
            max = Math.max(max, sz);
        }

        return new ConditioningSetSizeRange(min, max);
    }

//    public void setGraph(Graph graph) {
//        this.graph = graph;
//    }

    public boolean hasViolationsForVertex(Node x) {
        runVertex(graph, x);
        VertexSummary defaultValue = new VertexSummary(x.getName(), -1, 0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN);
        return summariesByVertex.getOrDefault(x.getName(), defaultValue).numReject > 0;
    }

    private record ConditioningSetSizeRange(int min, int max) {
    }


    // --- Summary record ----------------------------------------------------------------------------

    /**
     * @param numFactsTotal  includes weird p-values too
     * @param numPValuesUsed p in [0,1]
     */
    public record VertexSummary(String vertex, int conditioningSetSize, int numFactsTotal, int numPValuesUsed,
                                double ksPValue, double asP, double binP, double fishP, double aSquared, double aSquaredStar,
                                double fractionReject, long numReject, double minP,
                                double medianP) implements TetradSerializable {
            @Serial
            private static final long serialVersionUID = 1L;
    }

    public static final String PROP_GRAPH = "graph";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    public void setGraph(Graph g) {
        Graph old = this.graph;
        this.graph = g;
        clearResults(); // strongly recommended since cached results are now invalid
        pcs.firePropertyChange(PROP_GRAPH, old, g);
    }
}