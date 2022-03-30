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

public final class HbmsBeam implements Hbsms {
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

    public HbmsBeam(Graph graph, DataSet data, IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(data.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = new CovarianceMatrix(data);
        this.scorer = new DagScorer(this.cov);
    }

    public HbmsBeam(Graph graph, CovarianceMatrix cov, IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(cov.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = cov;
        this.scorer = new DagScorer(cov);
    }

    public Graph search() {
        EdgeListGraph _graph = new EdgeListGraph(this.externalGraph);
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

        Score score0 = scoreGraph(bestGraph);
        double bestScore = score0.getScore();
        this.originalSemIm = score0.getEstimatedSem();

        System.out.println("Graph from search = " + bestGraph);

        if (this.trueModel != null) {
            this.trueModel = GraphUtils.replaceNodes(this.trueModel, bestGraph.getNodes());
            this.trueModel = SearchGraphUtils.cpdagForDag(this.trueModel);
        }

        System.out.println("Initial Score = " + this.nf.format(bestScore));
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());

        {
            bestGraph = increaseScoreLoop(bestGraph, getAlpha());
            bestGraph = removeZeroEdges(bestGraph);
        }

        Score score = scoreGraph(bestGraph);
        SemIm estSem = score.getEstimatedSem();

        this.newSemIm = estSem;
        this.newDag = bestGraph;

        return bestGraph;
    }


    private Graph increaseScoreLoop(Graph bestGraph, double alpha) {
        System.out.println("Increase score loop2");

        double initialScore = scoreGraph(bestGraph).getScore();

        Map<Graph, Double> S = new HashMap<>();
        S.put(bestGraph, initialScore);
//        Set<Graph> P = new HashSet<Graph>();
//        P.add(bestGraph);
        boolean changed = true;

        LOOP:
        while (changed) {
            changed = false;

            for (Graph s : new HashMap<>(S).keySet()) {
                List<Move> moves = new ArrayList<>();
                moves.addAll(getAddMoves(s));
//                moves.addAll(getRemoveMoves(s));
                moves.addAll(getRedirectMoves(s));
//                moves.addAll(getAddColliderMoves(s));
//                moves.addAll(getDoubleRemoveMoves(s));
//                moves.addAll(getRemoveColliderMoves(s));
//                moves.addAll(getRemoveTriangleMoves(s));
//                moves.addAll(getSwapMoves(s));

                boolean found = false;

                for (Move move : moves) {
                    Graph graph = makeMove(s, move, false);
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

                    Score _score = scoreGraph(graph);
                    double score = _score.getScore();

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


    private Graph increaseDfLoop(Graph bestGraph, double alpha) {
        System.out.println("Increase df loop");

        Score score1 = scoreGraph(getGraph());
        int initialDof = score1.getDof();

        Map<Graph, Integer> S = new LinkedHashMap<>();
        S.put(bestGraph, initialDof);
        boolean changed = true;
        final boolean switched = false;

        while (changed) {
            changed = false;

            Map<Graph, Integer> SPrime = new LinkedHashMap<>(S);

            for (Graph s : SPrime.keySet()) {
                List<Move> moves = new ArrayList<>();
                moves.addAll(getAddMoves(s));
                moves.addAll(getRedirectMoves(s));

                for (Move move : moves) {
                    Graph graph = makeMove(s, move, false);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.existsDirectedCycle()) {
                        continue;
                    }

                    Score _score = scoreGraph(graph);
                    int dof = _score.getDof();

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

    private Graph maximum(Map<Graph, Integer> s) {
        int maxDof = Integer.MIN_VALUE;
        Graph maxGraph = null;

        for (Graph graph : s.keySet()) {
            if (s.containsKey(graph) && s.get(graph) > maxDof) {
                maxDof = s.get(graph);
                maxGraph = graph;
            }
        }

        return maxGraph;
    }

    private void removeMinimalDof(Map<Graph, Integer> s) {
        int minDof = Integer.MAX_VALUE;
        Graph minGraph = null;

        for (Graph graph : s.keySet()) {
            if (s.get(graph) < minDof) {
                minDof = s.get(graph);
                minGraph = graph;
            }
        }

        s.remove(minGraph);
    }

    private boolean increasesScore(Map<Graph, Double> s, double score) {
        double minScore = Double.MAX_VALUE;

        for (Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
            }
        }

        return score > minScore;
    }

    private Graph maximumScore(Map<Graph, Double> s) {
        double maxScore = Double.NEGATIVE_INFINITY;
        Graph maxGraph = null;

        for (Graph graph : s.keySet()) {
            if (graph == null) {
                throw new NullPointerException();
            }

            double score = s.get(graph);

            if (score > maxScore) {
                maxScore = score;
                maxGraph = graph;
            }
        }

        return maxGraph;
    }

    private void removeMinimalScore(Map<Graph, Double> s) {
        double minScore = Integer.MAX_VALUE;
        Graph minGraph = null;

        for (Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
                minGraph = graph;
            }
        }

        s.remove(minGraph);
    }

    private double minimalScore(Map<Graph, Double> s) {
        double minScore = Integer.MAX_VALUE;

        for (Graph graph : s.keySet()) {
            if (s.get(graph) < minScore) {
                minScore = s.get(graph);
            }
        }

        return minScore;
    }

    private boolean increasesDof(Map<Graph, Integer> s, int dof) {
        int minDof = Integer.MAX_VALUE;

        for (Graph graph : s.keySet()) {
            if (s.get(graph) < minDof) {
                minDof = s.get(graph);
            }
        }

        return dof > minDof;
    }

    public Graph removeZeroEdges(Graph bestGraph) {
        boolean changed = true;
        Graph graph = new EdgeListGraph(bestGraph);

        while (changed) {
            changed = false;
            Score score = scoreGraph(graph);
            SemIm estSem = score.getEstimatedSem();

            for (Parameter param : estSem.getSemPm().getParameters()) {
                if (param.getType() != ParamType.COEF) {
                    continue;
                }

                Node nodeA = param.getNodeA();
                Node nodeB = param.getNodeB();
                Node parent;
                Node child;

                if (this.graph.isParentOf(nodeA, nodeB)) {
                    parent = nodeA;
                    child = nodeB;
                } else {
                    parent = nodeB;
                    child = nodeA;
                }

                Regression regression = new RegressionCovariance(this.cov);
                List<Node> parents = graph.getParents(child);
                RegressionResult result = regression.regress(child, parents);
                double p = result.getP()[parents.indexOf(parent) + 1];

                if (p > getHighPValueAlpha()) {
                    Edge edge = graph.getEdge(param.getNodeA(), param.getNodeB());

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

    private Graph makeMove(Graph graph, Move move, boolean finalMove) {
        graph = new EdgeListGraph(graph);
        Edge firstEdge = move.getFirstEdge();
        Edge secondEdge = move.getSecondEdge();

        if (firstEdge != null && move.getType() == HbmsBeam.Move.Type.ADD) {
            graph.removeEdge(firstEdge.getNode1(), firstEdge.getNode2());
            graph.addEdge(firstEdge);

            if (finalMove) {
                Node node1 = firstEdge.getNode1();
                Node node2 = firstEdge.getNode2();

                for (Node node3 : graph.getNodes()) {
                    if (graph.isAdjacentTo(node1, node3) && graph.isAdjacentTo(node2, node3)) {
                        System.out.println("TRIANGLE completed:");
                        System.out.println("\t" + graph.getEdge(node1, node3));
                        System.out.println("\t" + graph.getEdge(node2, node3));
                        System.out.println("\t" + graph.getEdge(node1, node2) + " added");
                    }
                }
            }


        } else if (firstEdge != null && move.getType() == HbmsBeam.Move.Type.REMOVE) {
            graph.removeEdge(firstEdge);
        } else if (firstEdge != null && move.getType() == HbmsBeam.Move.Type.DOUBLE_REMOVE) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && move.getType() == HbmsBeam.Move.Type.REDIRECT) {
            graph.removeEdge(graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2()));
            graph.addEdge(firstEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbmsBeam.Move.Type.ADD_COLLIDER) {
            Edge existingEdge1 = graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2());
            Edge existingEdge2 = graph.getEdge(secondEdge.getNode1(), secondEdge.getNode2());

            if (existingEdge1 != null) {
                graph.removeEdge(existingEdge1);
            }

            if (existingEdge2 != null) {
                graph.removeEdge(existingEdge2);
            }

            graph.addEdge(firstEdge);
            graph.addEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbmsBeam.Move.Type.REMOVE_COLLIDER) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbmsBeam.Move.Type.SWAP) {
            graph.removeEdge(firstEdge);
            Edge secondEdgeStar = graph.getEdge(secondEdge.getNode1(), secondEdge.getNode2());

            if (secondEdgeStar != null) {
                graph.removeEdge(secondEdgeStar);
            }

            graph.addEdge(secondEdge);
        }

        return graph;
    }

    private List<Move> getAddMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        // Add moves:
        List<Node> nodes = graph.getNodes();
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
                    Edge edge = Edges.directedEdge(nodes.get(i), nodes.get(j));
                    moves.add(new Move(edge, HbmsBeam.Move.Type.ADD));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        // Remove moves:
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        Collections.sort(edges);

        for (Edge edge : edges) {
            Node i = edge.getNode1();
            Node j = edge.getNode2();

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            moves.add(new Move(edge, HbmsBeam.Move.Type.REMOVE));
        }

        return moves;
    }

    private List<Move> getRedirectMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        // Reverse moves:
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        Collections.sort(edges);

        for (Edge edge : edges) {
            Node i = edge.getNode1();
            Node j = edge.getNode2();
            if (this.knowledge.isForbidden(j.getName(), i.getName())) {
                continue;
            }

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            if (graph.isAncestorOf(j, i)) {
                continue;
            }

            moves.add(new Move(Edges.directedEdge(j, i), HbmsBeam.Move.Type.REDIRECT));
        }

        return moves;
    }

    private List<Move> getAddColliderMoves(Graph graph) {
//         Make collider moves:

        List<Move> moves = new ArrayList<>();

        for (Node b : graph.getNodes()) {
            if (graph.getAdjacentNodes(b).isEmpty()) {
                List<Node> nodes = graph.getAdjacentNodes(b);

                if (nodes.size() >= 2) {
                    ChoiceGenerator gen = new ChoiceGenerator(nodes.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        List<Node> _nodes = GraphUtils.asList(choice, nodes);
                        Node a = _nodes.get(0);
                        Node c = _nodes.get(1);

                        if (a == b || c == b) continue;

                        Edge edge1 = Edges.directedEdge(a, b);
                        Edge edge2 = Edges.directedEdge(c, b);

                        if (getKnowledge().isForbidden(edge1.getNode1().getName(), edge1.getNode2().getName())) {
                            continue;
                        }

                        if (getKnowledge().isForbidden(edge2.getNode1().getName(), edge2.getNode2().getName())) {
                            continue;
                        }

                        moves.add(new Move(edge1, edge2, HbmsBeam.Move.Type.ADD_COLLIDER));
                    }
                }
            }
        }

        return moves;
    }

    private List<Move> getSwapMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        for (Node b : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> set = GraphUtils.asList(choice, adj);

                Node a = set.get(0);
                Node c = set.get(1);

                if (graph.getEdge(a, b) != null && graph.getEdge(b, c) != null &&
                        graph.getEdge(a, b).pointsTowards(b) && graph.getEdge(b, c).pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(a, b), Edges.directedEdge(b, c), HbmsBeam.Move.Type.SWAP));
                } else if (graph.getEdge(b, a) != null && graph.getEdge(a, c) != null &&
                        graph.getEdge(b, a).pointsTowards(a) && graph.getEdge(a, c).pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(b, a), Edges.directedEdge(a, c), HbmsBeam.Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveTriangleMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        for (Node b : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> set = GraphUtils.asList(choice, adj);

                Node a = set.get(0);
                Node c = set.get(1);

                Edge edge1 = graph.getEdge(a, b);
                Edge edge2 = graph.getEdge(b, c);
                Edge edge3 = graph.getEdge(a, c);

                if (edge1 != null && edge2 != null && edge3 != null &&
                        edge1.pointsTowards(a) && edge3.pointsTowards(c) &&
                        edge2.pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(c, a), HbmsBeam.Move.Type.SWAP));
                } else if (edge1 != null && edge2 != null && edge3 != null &&
                        edge3.pointsTowards(a) && edge1.pointsTowards(b) &&
                        edge2.pointsTowards(b)) {
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(b, a), HbmsBeam.Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveColliderMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();

        for (Node b : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> set = GraphUtils.asList(choice, adj);

                Node a = set.get(0);
                Node c = set.get(1);

                if (getGraph().isDefCollider(a, b, c)) {
                    Edge edge1 = Edges.directedEdge(a, b);
                    Edge edge2 = Edges.directedEdge(c, b);

                    moves.add(new Move(edge1, edge2, HbmsBeam.Move.Type.REMOVE_COLLIDER));
                }
            }
        }

        return moves;
    }

    private List<Move> getDoubleRemoveMoves(Graph graph) {
        List<Move> moves = new ArrayList<>();
        List<Edge> edges = new ArrayList<>(graph.getEdges());

        // Remove moves:
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                moves.add(new Move(edges.get(i), edges.get(j), HbmsBeam.Move.Type.DOUBLE_REMOVE));
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

    public void setHighPValueAlpha(double highPValueAlpha) {
        this.highPValueAlpha = highPValueAlpha;
    }

    public boolean isCheckingCycles() {
        return this.checkingCycles;
    }

    public void setCheckingCycles(boolean checkingCycles) {
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
        private final HbmsBeam.Move.Type type;

        public Move(Edge edge, HbmsBeam.Move.Type type) {
            this.edge = edge;
            this.type = type;
        }

        public Move(Edge edge, Edge secondEdge, HbmsBeam.Move.Type type) {
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

        public HbmsBeam.Move.Type getType() {
            return this.type;
        }

        public String toString() {
            String s = (this.secondEdge != null) ? (this.secondEdge + ", ") : "";
            return "<" + this.edge + ", " + s + this.type + ">";

        }
    }

    public static class GraphWithPValue {
        private final Graph graph;
        private final double pValue;

        public GraphWithPValue(Graph graph, double pValue) {
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

        public boolean equals(Object o) {
            if (o == null) return false;
            GraphWithPValue p = (GraphWithPValue) o;
            return (p.graph.equals(this.graph));
        }
    }

    public Score scoreGraph(Graph graph) {
        if (graph == null) {
            return Score.negativeInfinity();
        }

        this.scorer.score(graph);
        return new Score(this.scorer);
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;

        if (knowledge.isViolatedBy(this.graph)) {
            throw new IllegalArgumentException("Graph violates knowledge.");
        }
    }

    public void setTrueModel(Graph trueModel) {
        this.trueModel = trueModel;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setBeamWidth(int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException();
        this.beamWidth = beamWidth;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public Set<GraphWithPValue> getSignificantModels() {
        return this.significantModels;
    }

    private void addRequiredEdges(Graph graph) {
        for (Iterator<KnowledgeEdge> it =
             this.getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            String a = next.getFrom();
            String b = next.getTo();
            Node nodeA = null, nodeB = null;
            Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                Node nextNode = itn.next();
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
        for (Iterator<KnowledgeEdge> it =
             getKnowledge().forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            String a = next.getFrom();
            String b = next.getTo();
            Node nodeA = null, nodeB = null;
            Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                Node nextNode = itn.next();
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

        public Score(Scorer scorer) {
            this.scorer = scorer;
            this.fml = scorer.getFml();
            this.dof = scorer.getDof();
            int sampleSize = scorer.getSampleSize();

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
            int sampleSize = this.scorer.getSampleSize();
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


