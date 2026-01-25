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
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.OrderedLocalMarkovProperty;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.UniformityTest;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model for a per-vertex ("local") Markov check, a.k.a. "Vertex Checker".
 *
 * For each vertex X, constructs a conditioning set CS(X) (e.g., Markov blanket, parents, etc.),
 * and tests all claims Ind(X, Y | CS(X)) for Y not in CS(X) (and Y != X) using the chosen
 * IndependenceTest. The resulting p-values are then tested for Uniform(0,1) using KS,
 * producing a per-vertex KS p-value and summary diagnostics.
 *
 * This is designed to support a Tetrad interface tool that highlights locally reliable regions
 * of an estimated graph relative to data.
 */
public class VertexCheckIndTestModel implements SessionModel, GraphSource, KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DataModel dataModel;
    private final Graph graph;
    private final Parameters parameters;
    // Results
    private final Map<String, VertexSummary> summariesByVertex = new LinkedHashMap<>();
    private final Map<String, List<IndependenceResult>> resultsByVertex = new LinkedHashMap<>();
    private final Map<String, List<String>> conditioningSetByVertex = new LinkedHashMap<>();
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

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String getName() {
        return name;
    }

    // --- Core API used by the editor ------------------------------------------------------------

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
        conditioningSetByVertex.clear();
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

    // --- Implementation -------------------------------------------------------------------------

    public List<String> getConditioningSetForVertex(String vertexName) {
        return conditioningSetByVertex.getOrDefault(vertexName, List.of());
    }

    private void runVertex(Graph alignedGraph, Node x) {
        Set<Node> cs = computeConditioningSet(alignedGraph, x);
        List<String> csNames = cs.stream().map(Node::getName).sorted().collect(Collectors.toList());
        conditioningSetByVertex.put(x.getName(), csNames);

        List<IndependenceResult> results = new ArrayList<>();
        List<Double> pvals = new ArrayList<>();

        for (Node y : alignedGraph.getNodes()) {
            if (y.equals(x)) continue;
            if (cs.contains(y)) continue;

            try {
                IndependenceResult r = independenceTest.checkIndependence(x, y, cs);

                // Only keep well-formed p-values for uniformity testing.
                double p = r.getPValue();
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    results.add(new IndependenceResult(new IndependenceFact(x, y, cs), r.isIndependent(), p, r.getScore()));
                    pvals.add(p);
                } else {
                    // Still record it as an IndependenceResult with whatever p is (editor can show it),
                    // but skip for uniformity.
                    results.add(new IndependenceResult(new IndependenceFact(x, y, cs), r.isIndependent(), p, r.getScore()));
                }

                if (verbose) {
                    TetradLogger.getInstance().log("VertexCheck: " + x.getName() + " vs " + y.getName()
                            + " | CS=" + csNames + "  p=" + p);
                }
            } catch (Exception ex) {
                TetradLogger.getInstance().log("VertexCheck: error checking " + x.getName() + " _||_ " + y.getName()
                        + " | CS(X): " + ex.getMessage());
            }
        }

        // Compute summary stats
        VertexSummary summary = summarizeVertex(x.getName(), cs.size(), results, pvals);
        summariesByVertex.put(x.getName(), summary);
        resultsByVertex.put(x.getName(), results);
    }

    private VertexSummary summarizeVertex(String vertexName, int csSize,
                                          List<IndependenceResult> results, List<Double> pvals) {

        int n = pvals.size();

        double ksP = Double.NaN;
        if (n >= 2) {
            ksP = UniformityTest.getKsPValue(pvals, 0, 1);
        }

        double alpha = independenceTest.getAlpha();
        long numReject = pvals.stream().filter(p -> p <= alpha).count();
        double fracReject = (n == 0) ? Double.NaN : (numReject / (double) n);

        double minP = pvals.stream().min(Double::compare).orElse(Double.NaN);
        double medianP = median(pvals);

        return new VertexSummary(vertexName, csSize, results.size(), n, ksP, fracReject, numReject, minP, medianP);
    }

    private Set<Node> computeConditioningSet(Graph alignedGraph, Node x) {
        switch (conditioningSetType) {
            case LOCAL_MARKOV: {
                Set<Node> z = new HashSet<>();
                for (Node w : alignedGraph.getAdjacentNodes(x)) {
                    if (alignedGraph.isParentOf(w, x)) z.add(w);
                }
                return z;
            }
            case PARENTS_AND_NEIGHBORS: {
                Set<Node> z = new HashSet<>();
                for (Node w : alignedGraph.getAdjacentNodes(x)) {
                    Edge e = alignedGraph.getEdge(w, x);
                    if (e != null && Edges.isUndirectedEdge(e)) z.add(w);
                    if (alignedGraph.isParentOf(w, x)) z.add(w);
                }
                return z;
            }
            case MARKOV_BLANKET:
                return GraphUtils.markovBlanket(x, alignedGraph);

            default:
                // For now, keep the Vertex Checker tight and predictable.
                // If you want to include ORDERED_LOCAL_MARKOV_MAG or GLOBAL_MARKOV here, we can,
                // but the UI story is less clean.
                throw new IllegalArgumentException("Unsupported conditioning set type for VertexCheck: " + conditioningSetType);
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

    public int getConditioningSetSizeFast(String vertexName) {
        if (conditioningSetByVertex.containsKey(vertexName)) {
            return conditioningSetByVertex.get(vertexName).size();
        }
        if (independenceTest == null) return -1;

        Graph alignedGraph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        Node x = alignedGraph.getNode(vertexName);
        if (x == null) return -1;

        Set<Node> cs = computeConditioningSet(alignedGraph, x);
        return cs.size();
    }

    // --- Summary record ----------------------------------------------------------------------------

    public static final class VertexSummary implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String vertex;
        private final int conditioningSetSize;
        private final int numFactsTotal;     // includes weird p-values too
        private final int numPValuesUsed;    // p in [0,1]
        private final double ksPValue;
        private final double fractionReject;
        private final long numReject;
        private final double minP;
        private final double medianP;

        public VertexSummary(String vertex, int conditioningSetSize, int numFactsTotal, int numPValuesUsed,
                             double ksPValue, double fractionReject, long numReject, double minP, double medianP) {
            this.vertex = vertex;
            this.conditioningSetSize = conditioningSetSize;
            this.numFactsTotal = numFactsTotal;
            this.numPValuesUsed = numPValuesUsed;
            this.ksPValue = ksPValue;
            this.fractionReject = fractionReject;
            this.numReject = numReject;
            this.minP = minP;
            this.medianP = medianP;
        }

        public String getVertex() {
            return vertex;
        }

        public int getConditioningSetSize() {
            return conditioningSetSize;
        }

        public int getNumFactsTotal() {
            return numFactsTotal;
        }

        public int getNumPValuesUsed() {
            return numPValuesUsed;
        }

        public double getKsPValue() {
            return ksPValue;
        }

        public double getFractionReject() {
            return fractionReject;
        }

        public long getNumReject() {
            return numReject;
        }

        public double getMinP() {
            return minP;
        }

        public double getMedianP() {
            return medianP;
        }
    }
}