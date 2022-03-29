///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Best Fit Finder using a beam search.
 * </p>
 * Improves the P value of a SEM IM by adding, removing, or reversing single edges.
 *
 * @author Joseph Ramsey
 */

public final class BffBeam implements Bff {
    private final CovarianceMatrix cov;
    private IKnowledge knowledge = new Knowledge2();
    private final Graph externalGraph;
    private Graph graph;
    private double alpha = 0.05;
    private double highPValueAlpha = 0.05;
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    private final Set<GraphWithPValue> significantModels = new LinkedHashSet<>();
    private Graph trueModel;
    private SemIm originalSemIm;
    private SemIm newSemIm;
    private final Scorer scorer;
    private boolean checkingCycles = true;
    private Graph newDag;
    private int beamWidth = 1;

    public BffBeam(Graph graph, final DataSet data, final IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(data.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = new CovarianceMatrix(data);
        this.scorer = new DagScorer(this.cov);
    }

    public BffBeam(Graph graph, final CovarianceMatrix cov, final IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(cov.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = cov;
        this.scorer = new DagScorer(cov);
    }

    public Graph search() {
        final EdgeListGraph _graph = new EdgeListGraph(this.externalGraph);
        addRequiredEdges(_graph);
        Graph bestGraph = SearchGraphUtils.dagFromCPDAG(_graph);

        if (getGraph().getNumEdges() == 0) {
            System.out.println("Found one!");
        }

        if (_graph.getNumEdges() == 0) {
            System.out.println("Found one!");
        }

        if (bestGraph.getNumEdges() == 0) {
            System.out.println("Found one!");
        }

        final Score score0 = scoreGraph(bestGraph);
        final double bestScore = score0.getScore();
        this.originalSemIm = score0.getEstimatedSem();

        System.out.println("Graph from search = " + bestGraph);

        if (this.trueModel != null) {
            this.trueModel = GraphUtils.replaceNodes(this.trueModel, bestGraph.getNodes());
            this.trueModel = SearchGraphUtils.cpdagForDag(this.trueModel);
        }

        System.out.println("Initial Score = " + this.nf.format(bestScore));
        final MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());

        {
            bestGraph = increaseScoreLoop(bestGraph, getAlpha());
            bestGraph = removeZeroEdges(bestGraph);
        }

        final Score score = scoreGraph(bestGraph);
        final SemIm estSem = score.getEstimatedSem();

        this.newSemIm = estSem;
        this.newDag = bestGraph;

        return bestGraph;
    }


    private Graph increaseScoreLoop(final Graph bestGraph, final double alpha) {
        System.out.println("Increase score loop2");

        final double initialScore = scoreGraph(bestGraph).getScore();

        final Map<Graph, Double> S = new HashMap<>();
        S.put(bestGraph, initialScore);
//        Set<Graph> P = new HashSet<Graph>();
//        P.add(bestGraph);
        boolean changed = true;

        LOOP:
        while (changed) {
            changed = false;

            for (final Graph s : new HashMap<>(S).keySet()) {
                final List<Move> moves = new ArrayList<>();
                moves.addAll(getAddMoves(s));
//                moves.addAll(getRemoveMoves(s));
                moves.addAll(getRedirectMoves(s));
//                moves.addAll(getAddColliderMoves(s));
//                moves.addAll(getDoubleRemoveMoves(s));
//                moves.addAll(getRemoveColliderMoves(s));
//                moves.addAll(getRemoveTriangleMoves(s));
//                moves.addAll(getSwapMoves(s));

                boolean found = false;

                for (final Move move : moves) {
                    final Graph graph = makeMove(s, move, false);
//                    if (P.contains(graph)) continue;
//                    P.add(graph);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.existsDirectedCycle()) {
                        continue;
                    }

                    if (S.containsKey(graph)) {
                        continue;
                    }

                    final Score _score = scoreGraph(graph);
                    final double score = _score.getScore();

                    if (S.keySet().size() < this.beamWidth) {
                        S.put(graph, score);
                        changed = true;
                    } else if (increasesScore(S, score)) {
                        System.out.println("Increase score (" + move.getType() + "): score = " + score);

                        removeMinimalScore(S);
                        S.put(graph, score);
                        changed = true;

                        if (scoreGraph(removeZeroEdges(graph)).getPValue() > alpha) {
                            found = true;
                        }
                    }
                }

                if (found) break LOOP;
            }
        }

        System.out.println("DOF = " + scoreGraph(maximumScore(S)).getDof());
        this.graph = maximumScore(S);
        return maximumScore(S);
    }


    private Graph increaseDfLoop(final Graph bestGraph, final double alpha) {
        System.out.println("Increase df loop");

        final Score score1 = scoreGraph(getGraph());
        final int initialDof = score1.getDof();

        final Map<Graph, Integer> S = new LinkedHashMap<>();
        S.put(bestGraph, initialDof);
        boolean changed = true;
        final boolean switched = false;

        while (changed) {
            changed = false;

            final Map<Graph, Integer> SPrime = new LinkedHashMap<>(S);

            for (final Graph s : SPrime.keySet()) {
                final List<Move> moves = new ArrayList<>();
                moves.addAll(getAddMoves(s));
                moves.addAll(getRedirectMoves(s));

                for (final Move move : moves) {
                    final Graph graph = makeMove(s, move, false);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.existsDirectedCycle()) {
                        continue;
                    }

                    final Score _score = scoreGraph(graph);
                    final int dof = _score.getDof();

                    if (S.containsKey(graph)) {
                        continue;
                    }

                    if (S.keySet().size() < this.beamWidth) {
                        S.put(graph, dof);
                        changed = true;
                    } else if (increasesDof(S, dof)) {
                        removeMinimalDof(S);
                        S.put(new EdgeListGraph(graph), dof);
                        System.out.println("==INSERTING== DOF = " + dof);
                        changed = true;
                    }
                }
            }
        }

        this.graph = maximum(S);
        return this.graph;
    }

    private Graph maximum(final Map<Graph, Integer> s) {
        int maxDof = Integer.MIN_VALUE;
        Graph maxGraph = null;

        for (final Graph graph : s.keySet()) {
            if (s.containsKey(graph) && s.get(graph) > maxDof) {
                maxDof = s.get(graph);
                maxGraph = graph;
            }
        }

        return maxGraph;
    }

    private void removeMinimalDof(final Map<Graph, Integer> s) {
        int minDof = Integer.MAX_VALUE;
        Graph minGraph = null;

        for (final Graph graph : s.keySet()) {
            if (s.get(graph) < minDof) {
                minDof = s.get(graph);
                minGraph = graph;
            }
        }

        s.remove(minGraph);
    }

    private boolean increasesScore(final Map<Graph, Double> s, final double score) {
        double minScore = Double.MAX_VALUE;

        for (final Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
            }
        }

        return score > minScore;
    }

    private Graph maximumScore(final Map<Graph, Double> s) {
        double maxScore = Double.NEGATIVE_INFINITY;
        Graph maxGraph = null;

        for (final Graph graph : s.keySet()) {
            if (graph == null) {
                throw new NullPointerException();
            }

            final double score = s.get(graph);

            if (score > maxScore) {
                maxScore = score;
                maxGraph = graph;
            }
        }

        return maxGraph;
    }

    private void removeMinimalScore(final Map<Graph, Double> s) {
        double minScore = Integer.MAX_VALUE;
        Graph minGraph = null;

        for (final Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
                minGraph = graph;
            }
        }

        s.remove(minGraph);
    }

    private double minimalScore(final Map<Graph, Double> s) {
        double minScore = Integer.MAX_VALUE;

        for (final Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
            }
        }

        return minScore;
    }

    private boolean increasesDof(final Map<Graph, Integer> s, final int dof) {
        int minDof = Integer.MAX_VALUE;

        for (final Graph graph : s.keySet()) {
            if (s.get(graph) < minDof) {
                minDof = s.get(graph);
            }
        }

        return dof > minDof;
    }

    public Graph removeZeroEdges(final Graph bestGraph) {
        boolean changed = true;
        final Graph graph = new EdgeListGraph(bestGraph);

        while (changed) {
            changed = false;
            final Score score = scoreGraph(graph);
            final SemIm estSem = score.getEstimatedSem();

            for (final Parameter param : estSem.getSemPm().getParameters()) {
                if (param.getType() != ParamType.COEF) {
                    continue;
                }

                final Node nodeA = param.getNodeA();
                final Node nodeB = param.getNodeB();
                final Node parent;
                final Node child;

                if (this.graph.isParentOf(nodeA, nodeB)) {
                    parent = nodeA;
                    child = nodeB;
                } else {
                    parent = nodeB;
                    child = nodeA;
                }

                final Regression regression = new RegressionCovariance(this.cov);
                final List<Node> parents = graph.getParents(child);
                final RegressionResult result = regression.regress(child, parents);
                final double p = result.getP()[parents.indexOf(parent) + 1];

                if (p > getHighPValueAlpha()) {
                    final Edge edge = graph.getEdge(param.getNodeA(), param.getNodeB());

                    if (getKnowledge().isRequired(edge.getNode1().getName(), edge.getNode2().getName())) {
                        System.out.println("Not removing " + edge + " because it is required.");
                        TetradLogger.getInstance().log("details", "Not removing " + edge + " because it is required.");
                        continue;
                    }

                    System.out.println("Removing edge " + edge + " because it has p = " + p);
                    TetradLogger.getInstance().log("details", "Removing edge " + edge + " because it has p = " + p);
                    graph.removeEdge(edge);
                    changed = true;
                }
            }
        }

        return graph;
    }

    private Graph makeMove(Graph graph, final Move move, final boolean finalMove) {
        graph = new EdgeListGraph(graph);
        final Edge firstEdge = move.getFirstEdge();
        final Edge secondEdge = move.getSecondEdge();

        if (firstEdge != null && move.getType() == BffBeam.Move.Type.ADD) {
            graph.removeEdge(firstEdge.getNode1(), firstEdge.getNode2());
            graph.addEdge(firstEdge);

            if (finalMove) {
                final Node node1 = firstEdge.getNode1();
                final Node node2 = firstEdge.getNode2();

                for (final Node node3 : graph.getNodes()) {
                    if (graph.isAdjacentTo(node1, node3) && graph.isAdjacentTo(node2, node3)) {
                        System.out.println("TRIANGLE completed:");
                        System.out.println("\t" + graph.getEdge(node1, node3));
                        System.out.println("\t" + graph.getEdge(node2, node3));
                        System.out.println("\t" + graph.getEdge(node1, node2) + " added");
                    }
                }
            }


        } else if (firstEdge != null && move.getType() == BffBeam.Move.Type.REMOVE) {
            graph.removeEdge(firstEdge);
        } else if (firstEdge != null && move.getType() == BffBeam.Move.Type.DOUBLE_REMOVE) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && move.getType() == BffBeam.Move.Type.REDIRECT) {
            graph.removeEdge(graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2()));
            graph.addEdge(firstEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == BffBeam.Move.Type.ADD_COLLIDER) {
            final Edge existingEdge1 = graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2());
            final Edge existingEdge2 = graph.getEdge(secondEdge.getNode1(), secondEdge.getNode2());

            if (existingEdge1 != null) {
                graph.removeEdge(existingEdge1);
            }

            if (existingEdge2 != null) {
                graph.removeEdge(existingEdge2);
            }

            graph.addEdge(firstEdge);
            graph.addEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == BffBeam.Move.Type.REMOVE_COLLIDER) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == BffBeam.Move.Type.SWAP) {
            graph.removeEdge(firstEdge);
            final Edge secondEdgeStar = graph.getEdge(secondEdge.getNode1(), secondEdge.getNode2());

            if (secondEdgeStar != null) {
                graph.removeEdge(secondEdgeStar);
            }

            graph.addEdge(secondEdge);
        }

        return graph;
    }

    private List<Move> getAddMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        // Add moves:
        final List<Node> nodes = graph.getNodes();
        Collections.sort(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }

                if (graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    continue;
                }

                if (getKnowledge().isForbidden(nodes.get(i).getName(), nodes.get(j).getName())) {
                    continue;
                }

                if (getKnowledge().isRequired(nodes.get(j).getName(), nodes.get(i).getName())) {
                    continue;
                }

                if (!graph.isAncestorOf(nodes.get(j), nodes.get(i))) {
                    final Edge edge = Edges.directedEdge(nodes.get(i), nodes.get(j));
                    moves.add(new Move(edge, BffBeam.Move.Type.ADD));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        // Remove moves:
        final List<Edge> edges = new ArrayList<>(graph.getEdges());
        Collections.sort(edges);

        for (final Edge edge : edges) {
            final Node i = edge.getNode1();
            final Node j = edge.getNode2();

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            moves.add(new Move(edge, BffBeam.Move.Type.REMOVE));
        }

        return moves;
    }

    private List<Move> getRedirectMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        // Reverse moves:
        final List<Edge> edges = new ArrayList<>(graph.getEdges());
        Collections.sort(edges);

        for (final Edge edge : edges) {
            final Node i = edge.getNode1();
            final Node j = edge.getNode2();
            if (this.knowledge.isForbidden(j.getName(), i.getName())) {
                continue;
            }

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            if (graph.isAncestorOf(j, i)) {
                continue;
            }

            moves.add(new Move(Edges.directedEdge(j, i), BffBeam.Move.Type.REDIRECT));
        }

        return moves;
    }

    private List<Move> getAddColliderMoves(final Graph graph) {
//         Make collider moves:

        final List<Move> moves = new ArrayList<>();

        for (final Node b : graph.getNodes()) {
            if (graph.getAdjacentNodes(b).isEmpty()) {
                final List<Node> nodes = graph.getAdjacentNodes(b);

                if (nodes.size() >= 2) {
                    final ChoiceGenerator gen = new ChoiceGenerator(nodes.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        final List<Node> _nodes = GraphUtils.asList(choice, nodes);
                        final Node a = _nodes.get(0);
                        final Node c = _nodes.get(1);

                        if (a == b || c == b) continue;

                        final Edge edge1 = Edges.directedEdge(a, b);
                        final Edge edge2 = Edges.directedEdge(c, b);

                        if (getKnowledge().isForbidden(edge1.getNode1().getName(), edge1.getNode2().getName())) {
                            continue;
                        }

                        if (getKnowledge().isForbidden(edge2.getNode1().getName(), edge2.getNode2().getName())) {
                            continue;
                        }

                        moves.add(new Move(edge1, edge2, BffBeam.Move.Type.ADD_COLLIDER));
                    }
                }
            }
        }

        return moves;
    }

    private List<Move> getSwapMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        for (final Node b : graph.getNodes()) {
            final List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> set = GraphUtils.asList(choice, adj);

                final Node a = set.get(0);
                final Node c = set.get(1);

                if (graph.getEdge(a, b) != null && graph.getEdge(b, c) != null &&
                        graph.getEdge(a, b).pointsTowards(b) && graph.getEdge(b, c).pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(a, b), Edges.directedEdge(b, c), BffBeam.Move.Type.SWAP));
                } else if (graph.getEdge(b, a) != null && graph.getEdge(a, c) != null &&
                        graph.getEdge(b, a).pointsTowards(a) && graph.getEdge(a, c).pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(b, a), Edges.directedEdge(a, c), BffBeam.Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveTriangleMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        for (final Node b : graph.getNodes()) {
            final List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> set = GraphUtils.asList(choice, adj);

                final Node a = set.get(0);
                final Node c = set.get(1);

                final Edge edge1 = graph.getEdge(a, b);
                final Edge edge2 = graph.getEdge(b, c);
                final Edge edge3 = graph.getEdge(a, c);

                if (edge1 != null && edge2 != null && edge3 != null &&
                        edge1.pointsTowards(a) && edge3.pointsTowards(c) &&
                        edge2.pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(c, a), BffBeam.Move.Type.SWAP));
                } else if (edge1 != null && edge2 != null && edge3 != null &&
                        edge3.pointsTowards(a) && edge1.pointsTowards(b) &&
                        edge2.pointsTowards(b)) {
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(b, a), BffBeam.Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveColliderMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();

        for (final Node b : graph.getNodes()) {
            final List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> set = GraphUtils.asList(choice, adj);

                final Node a = set.get(0);
                final Node c = set.get(1);

                if (getGraph().isDefCollider(a, b, c)) {
                    final Edge edge1 = Edges.directedEdge(a, b);
                    final Edge edge2 = Edges.directedEdge(c, b);

                    moves.add(new Move(edge1, edge2, BffBeam.Move.Type.REMOVE_COLLIDER));
                }
            }
        }

        return moves;
    }

    private List<Move> getDoubleRemoveMoves(final Graph graph) {
        final List<Move> moves = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>(graph.getEdges());

        // Remove moves:
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                moves.add(new Move(edges.get(i), edges.get(j), BffBeam.Move.Type.DOUBLE_REMOVE));
            }
        }

        return moves;
    }

    public Graph getGraph() {
        return this.graph;
    }

    public SemIm getOriginalSemIm() {
        return this.originalSemIm;
    }

    public SemIm getNewSemIm() {
        return this.newSemIm;
    }

    public double getHighPValueAlpha() {
        return this.highPValueAlpha;
    }

    public void setHighPValueAlpha(final double highPValueAlpha) {
        this.highPValueAlpha = highPValueAlpha;
    }

    public boolean isCheckingCycles() {
        return this.checkingCycles;
    }

    public void setCheckingCycles(final boolean checkingCycles) {
        this.checkingCycles = checkingCycles;
    }

    public Graph getNewDag() {
        return this.newDag;
    }

    public static class Move {
        public enum Type {
            ADD, REMOVE, REDIRECT, ADD_COLLIDER, REMOVE_COLLIDER, SWAP, DOUBLE_REMOVE
        }

        private final Edge edge;
        private Edge secondEdge;
        private final BffBeam.Move.Type type;

        public Move(final Edge edge, final BffBeam.Move.Type type) {
            this.edge = edge;
            this.type = type;
        }

        public Move(final Edge edge, final Edge secondEdge, final BffBeam.Move.Type type) {
            this.edge = edge;
            this.secondEdge = secondEdge;
            this.type = type;
        }

        public Edge getFirstEdge() {
            return this.edge;
        }

        public Edge getSecondEdge() {
            return this.secondEdge;
        }

        public BffBeam.Move.Type getType() {
            return this.type;
        }

        public String toString() {
            final String s = (this.secondEdge != null) ? (this.secondEdge + ", ") : "";
            return "<" + this.edge + ", " + s + this.type + ">";

        }
    }

    public static class GraphWithPValue {
        private final Graph graph;
        private final double pValue;

        public GraphWithPValue(final Graph graph, final double pValue) {
            this.graph = graph;
            this.pValue = pValue;
        }

        public Graph getGraph() {
            return this.graph;
        }

        public double getPValue() {
            return this.pValue;
        }

        public int hashCode() {
            return 17 * this.graph.hashCode();
        }

        public boolean equals(final Object o) {
            if (o == null) return false;
            final GraphWithPValue p = (GraphWithPValue) o;
            return (p.graph.equals(this.graph));
        }
    }

    public Score scoreGraph(final Graph graph) {
        if (graph == null) {
            return Score.negativeInfinity();
        }

        this.scorer.score(graph);
        return new Score(this.scorer);
    }

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;

        if (knowledge.isViolatedBy(this.graph)) {
            throw new IllegalArgumentException("Graph violates knowledge.");
        }
    }

    public void setTrueModel(final Graph trueModel) {
        this.trueModel = trueModel;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    public void setBeamWidth(final int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException();
        this.beamWidth = beamWidth;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public Set<GraphWithPValue> getSignificantModels() {
        return this.significantModels;
    }

    private void addRequiredEdges(final Graph graph) {
        for (final Iterator<KnowledgeEdge> it =
             this.getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            final KnowledgeEdge next = it.next();
            final String a = next.getFrom();
            final String b = next.getTo();
            Node nodeA = null, nodeB = null;
            final Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                final Node nextNode = itn.next();
                if (nextNode.getName().equals(a)) {
                    nodeA = nextNode;
                }
                if (nextNode.getName().equals(b)) {
                    nodeB = nextNode;
                }
            }
            if (!graph.isAncestorOf(nodeB, nodeA)) {
                graph.removeEdge(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);
                TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
            }
        }
        for (final Iterator<KnowledgeEdge> it =
             getKnowledge().forbiddenEdgesIterator(); it.hasNext(); ) {
            final KnowledgeEdge next = it.next();
            final String a = next.getFrom();
            final String b = next.getTo();
            Node nodeA = null, nodeB = null;
            final Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                final Node nextNode = itn.next();
                if (nextNode.getName().equals(a)) {
                    nodeA = nextNode;
                }
                if (nextNode.getName().equals(b)) {
                    nodeB = nextNode;
                }
            }
            if (nodeA != null && nodeB != null && graph.isAdjacentTo(nodeA, nodeB) &&
                    !graph.isChildOf(nodeA, nodeB)) {
                if (!graph.isAncestorOf(nodeA, nodeB)) {
                    graph.removeEdges(nodeA, nodeB);
                    graph.addDirectedEdge(nodeB, nodeA);
                    TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                }
            }
        }
    }

    public static class Score {
        private final Scorer scorer;
        private final double pValue;
        private final double fml;
        private final double chisq;
        private final double bic;
        private final double aic;
        private double kic;
        private int dof;

        public Score(final Scorer scorer) {
            this.scorer = scorer;
            this.fml = scorer.getFml();
            this.dof = scorer.getDof();
            final int sampleSize = scorer.getSampleSize();

            this.chisq = (sampleSize - 1) * getFml();
            this.pValue = 1.0 - ProbUtils.chisqCdf(this.chisq, this.dof);
            this.bic = this.chisq - this.dof * Math.log(sampleSize);
            this.aic = this.chisq - 2 * this.dof;

//            this.chisq = scorer.getChiSquare();
//            this.pValue = scorer.getScore();
//            this.bic = scorer.getBicScore();
//            this.aic = scorer.getAicScore();
        }

        private Score() {
            this.scorer = null;
            this.pValue = 0.0;
            final int sampleSize = this.scorer.getSampleSize();
            this.fml = Double.POSITIVE_INFINITY;
            this.chisq = (sampleSize - 1) * this.fml;
            this.bic = this.chisq - this.dof * Math.log(sampleSize);
            this.aic = this.chisq - 2 * this.dof;
        }

        public SemIm getEstimatedSem() {
            return this.scorer.getEstSem();
        }

        public double getPValue() {
            return this.scorer.getPValue();
        }

        public double getScore() {
//                return pValue;
//                return -fml;
//            return -chisq;
            return -this.bic;
//                return -aic;
        }

        public double getFml() {
            return this.scorer.getFml();
        }

        public static Score negativeInfinity() {
            return new Score();
        }

        public int getDof() {
            return this.dof;
        }

        public double getChiSquare() {
            return this.chisq;
        }

        public double getBic() {
            return this.bic;
        }
    }

}


