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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Improves the P value of a SEM IM by adding, removing, or reversing single edges.
 *
 * @author Joseph Ramsey
 */

public final class HbsmsBeam implements Hbsms {
    private DataSet dataSet = null;
    private CovarianceMatrix cov = null;
    private IKnowledge knowledge = new Knowledge2();
    private Graph graph;
    private double alpha = 0.05;
    private double highPValueAlpha = 0.05;
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    private Set<GraphWithPValue> significantModels = new LinkedHashSet<GraphWithPValue>();
    private Graph trueModel;
    private SemIm originalSemIm;
    private SemIm newSemIm;
    private Scorer scorer;
    private boolean checkingCycles = false;
    private Graph newDag;
    private int beamWidth = 1;
    private boolean shuffleMoves = false;

    public HbsmsBeam(Graph graph, DataSet data, IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(data.getVariables());

        this.knowledge = knowledge;

        boolean allowArbitraryOrientations = false;
        boolean allowNewColliders = true;
        DagInPatternIterator iterator = new DagInPatternIterator(graph, getKnowledge(), allowArbitraryOrientations,
                allowNewColliders);
        Graph graph2 = iterator.next();

        if (graph2 == null) {
            DagIterator iterator2 = new DagIterator(graph);
            graph = iterator2.next();
        } else {
            graph = graph2;
        }

        if (GraphUtils.containsBidirectedEdge(graph)) {
            throw new IllegalArgumentException("Contains bidirected edge.");
        }

        this.graph = graph;
        this.dataSet = data;
        this.scorer = new DagScorer(getDataSet());
    }

    public HbsmsBeam(Graph graph, CovarianceMatrix cov, IKnowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(cov.getVariables());

        this.knowledge = knowledge;

        boolean allowArbitraryOrientations = false;
        boolean allowNewColliders = true;
        DagInPatternIterator iterator = new DagInPatternIterator(graph, getKnowledge(), allowArbitraryOrientations,
                allowNewColliders);
        Graph graph2 = iterator.next();

        if (graph2 == null) {
            DagIterator iterator2 = new DagIterator(graph);
            graph = iterator2.next();
        } else {
            graph = graph2;
        }

        if (GraphUtils.containsBidirectedEdge(graph)) {
            throw new IllegalArgumentException("Contains bidirected edge.");
        }

        this.graph = graph;
        this.cov = cov;
        this.scorer = new DagScorer(cov);
    }

    public Graph search() {
        EdgeListGraph _graph = new EdgeListGraph(getGraph());
        addRequiredEdges(_graph);
        Graph bestGraph = SearchGraphUtils.dagFromPattern(_graph);
        Score score0 = scoreGraph(bestGraph);
        double bestScore = score0.getScore();
        this.originalSemIm = score0.getEstimatedSem();

        System.out.println("Graph from search = " + bestGraph);

        if (trueModel != null) {
            trueModel = GraphUtils.replaceNodes(trueModel, bestGraph.getNodes());
            trueModel = SearchGraphUtils.patternForDag(trueModel);
        }

        System.out.println("Initial Score = " + nf.format(bestScore));
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());

        {
            bestGraph = increaseScoreLoop(bestGraph, getAlpha());
            bestGraph = increaseDfLoop(bestGraph, getAlpha());
            removeZeroEdges(bestGraph);
        }

        Score score = scoreGraph(bestGraph);
        SemIm estSem = score.getEstimatedSem();

        this.newSemIm = estSem;
        this.newDag = bestGraph;

        return bestGraph;
    }


    private Graph increaseScoreLoop(Graph bestGraph, double alpha) {
        System.out.println("Increase score loop2");

        double initialDof = scoreGraph(getGraph()).getScore();

        Map<Graph, Double> S = new LinkedHashMap<Graph, Double>();
        S.put(bestGraph, initialDof);
        boolean changed = true;
        Graph graph;

        LOOP:
        while (changed) {
            changed = false;

            Map<Graph, Double> SPrime = new LinkedHashMap<Graph, Double>(S);

            for (Graph s : SPrime.keySet()) {
                List<Move> moves = new ArrayList<Move>();
                moves.addAll(getAddMoves(s));
                moves.addAll(getRedirectMoves(s));

                if (shuffleMoves) {
                    Collections.shuffle(moves);
                }

                boolean found = false;

                for (Move move : moves) {
                    graph = new EdgeListGraph(s);
                    makeMove(graph, move, false);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.existsDirectedCycle()) {
                        continue;
                    }

                    if (S.keySet().contains(graph)) {
                        continue;
                    }

                    Score _score = scoreDag(graph);
                    double score = _score.getScore();

//                    System.out.println("Score " + _score.getPValue() + " DOF = " + _score.getDof());

                    if (S.keySet().size() < this.beamWidth) {
                        S.put(new EdgeListGraph(graph), score);
                        changed = true;
                    } else if (increasesScore(S, score)) {
                        System.out.println("==INSERT== " + score + " > " + minimalScore(S));
                        removeMinimalScore(S);
                        S.put(new EdgeListGraph(graph), score);
                        changed = true;

                        if (_score.getPValue() > alpha) {
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

        Map<Graph, Integer> S = new LinkedHashMap<Graph, Integer>();
        S.put(bestGraph, initialDof);
        boolean changed = true;

        while (changed) {
            changed = false;

            Map<Graph, Integer> SPrime = new LinkedHashMap<Graph, Integer>(S);

            for (Graph s : SPrime.keySet()) {
                List<Move> moves = new ArrayList<Move>();
                moves.addAll(getRemoveMoves(s));
                moves.addAll(getRedirectMoves(s));

                if (shuffleMoves) {
                    Collections.shuffle(moves);
                }

                for (Move move : moves) {
                    Graph graph = new EdgeListGraph(s);
                    makeMove(graph, move, false);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.existsDirectedCycle()) {
                        continue;
                    }

                    Score _score = scoreDag(graph);

                    int dof = _score.getDof();
//                    System.out.println("DOF = " + dof);

                    if (_score.getPValue() < alpha) {
                        continue;
                    }

                    if (S.keySet().contains(graph)) {
                        continue;
                    }

                    if (S.keySet().size() < this.beamWidth) {
                        S.put(new EdgeListGraph(graph), dof);
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


    public HbsmsBeam() {
        super();
    }

    public Graph removeZeroEdges(Graph bestGraph) {
        boolean changed = true;

        while (changed) {
            changed = false;
            Score score = scoreGraph(bestGraph);
            SemIm estSem = score.getEstimatedSem();

            for (Parameter param : estSem.getSemPm().getParameters()) {
                if (param.getType() != ParamType.COEF) {
                    continue;
                }

                double p = estSem.getPValue(param, 10000);

                Edge edge = bestGraph.getEdge(param.getNodeA(), param.getNodeB());

                if (getKnowledge().isRequired(edge.getNode1().getName(), edge.getNode2().getName())) {
                    System.out.println("Not removing " + edge + " because it is required.");
                    TetradLogger.getInstance().log("details", "Not removing " + edge + " because it is required.");
                    continue;
                }

//                System.out.println("P value for edge " + edge + " = " + p);

                if (p > getHighPValueAlpha()) {
                    System.out.println("Removing edge " + edge + " because it has p = " + p);
                    TetradLogger.getInstance().log("details", "Removing edge " + edge + " because it has p = " + p);
                    bestGraph.removeEdge(edge);
                    changed = true;
                }
            }
        }

        return bestGraph;
    }

    private Edge makeMove(Graph graph, Move move, boolean finalMove) {
        Edge firstEdge = move.getFirstEdge();
        Edge secondEdge = move.getSecondEdge();

        if (firstEdge != null && move.getType() == Move.Type.ADD) {
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


        } else if (firstEdge != null && move.getType() == Move.Type.REMOVE) {
            graph.removeEdge(firstEdge);
        } else if (firstEdge != null && move.getType() == Move.Type.DOUBLE_REMOVE) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && move.getType() == Move.Type.REDIRECT) {
            graph.removeEdge(graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2()));
            graph.addEdge(firstEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == Move.Type.ADD_COLLIDER) {
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
        } else if (firstEdge != null && secondEdge != null && move.getType() == Move.Type.REMOVE_COLLIDER) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == Move.Type.SWAP) {
            graph.removeEdge(firstEdge);
            Edge secondEdgeStar = graph.getEdge(secondEdge.getNode1(), secondEdge.getNode2());

            if (secondEdgeStar != null) {
                graph.removeEdge(secondEdgeStar);
            }

            graph.addEdge(secondEdge);
        }

        return firstEdge;
    }

    private List<Move> getAddMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

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
                    moves.add(new Move(edge, Move.Type.ADD));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

        // Remove moves:
        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        Collections.sort(edges);

        for (Edge edge : edges) {
            Node i = edge.getNode1();
            Node j = edge.getNode2();

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            moves.add(new Move(edge, Move.Type.REMOVE));
        }

        return moves;
    }

    private List<Move> getRedirectMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

        // Reverse moves:
        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        Collections.sort(edges);

        for (Edge edge : edges) {
            Node i = edge.getNode1();
            Node j = edge.getNode2();
            if (knowledge.isForbidden(j.getName(), i.getName())) {
                continue;
            }

            if (getKnowledge().isRequired(i.getName(), j.getName())) {
                continue;
            }

            if (graph.isAncestorOf(j, i)) {
                continue;
            }

            moves.add(new Move(Edges.directedEdge(j, i), Move.Type.REDIRECT));
        }

        return moves;
    }

    private List<Move> getAddColliderMoves(Graph graph) {
//         Make collider moves:

        List<Move> moves = new ArrayList<Move>();

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

                        moves.add(new Move(edge1, edge2, Move.Type.ADD_COLLIDER));
                    }
                }
            }
        }

        return moves;
    }

    private List<Move> getSwapMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

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
                    moves.add(new Move(Edges.directedEdge(a, b), Edges.directedEdge(b, c), Move.Type.SWAP));
                } else if (graph.getEdge(b, a) != null && graph.getEdge(a, c) != null &&
                        graph.getEdge(b, a).pointsTowards(a) && graph.getEdge(a, c).pointsTowards(c)) {
                    moves.add(new Move(Edges.directedEdge(b, a), Edges.directedEdge(a, c), Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveTriangleMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

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
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(c, a), Move.Type.SWAP));
                } else if (edge1 != null && edge2 != null && edge3 != null &&
                        edge3.pointsTowards(a) && edge1.pointsTowards(b) &&
                        edge2.pointsTowards(b)) {
                    moves.add(new Move(Edges.directedEdge(b, c), Edges.directedEdge(b, a), Move.Type.SWAP));
                }
            }
        }

        return moves;
    }

    private List<Move> getRemoveColliderMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();

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

                    moves.add(new Move(edge1, edge2, Move.Type.REMOVE_COLLIDER));
                }
            }
        }

        return moves;
    }

    private List<Move> getDoubleRemoveMoves(Graph graph) {
        List<Move> moves = new ArrayList<Move>();
        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());

        // Remove moves:
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                moves.add(new Move(edges.get(i), edges.get(j), Move.Type.DOUBLE_REMOVE));
            }
        }

        return moves;
    }

    public Graph getGraph() {
        return graph;
    }

    public SemIm getOriginalSemIm() {
        return originalSemIm;
    }

    public SemIm getNewSemIm() {
        return newSemIm;
    }

    public double getHighPValueAlpha() {
        return highPValueAlpha;
    }

    public void setHighPValueAlpha(double highPValueAlpha) {
        this.highPValueAlpha = highPValueAlpha;
    }

    public boolean isCheckingCycles() {
        return checkingCycles;
    }

    public void setCheckingCycles(boolean checkingCycles) {
        this.checkingCycles = checkingCycles;
    }

    public Graph getNewDag() {
        return newDag;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public void setShuffleMoves(boolean shuffleMoves) {
        this.shuffleMoves = shuffleMoves;
    }

    private static class Move {
        public enum Type {
            ADD, REMOVE, REDIRECT, ADD_COLLIDER, REMOVE_COLLIDER, SWAP, DOUBLE_REMOVE;
        }

        private Edge edge;
        private Edge secondEdge;
        private Type type;

        public Move(Edge edge, Type type) {
            this.edge = edge;
            this.type = type;
        }

        public Move(Edge edge, Edge secondEdge, Type type) {
            this.edge = edge;
            this.secondEdge = secondEdge;
            this.type = type;
        }

        public Edge getFirstEdge() {
            return this.edge;
        }

        public Edge getSecondEdge() {
            return secondEdge;
        }

        public Type getType() {
            return this.type;
        }

        public String toString() {
            String s = (secondEdge != null) ? (secondEdge + ", ") : "";
            return "<" + edge + ", " + s + type + ">";

        }
    }


    private void saveModelIfSignificant(Graph graph) {
        double pValue = scoreGraph(graph).getPValue();

        if (pValue > getAlpha()) {
            getSignificantModels().add(new GraphWithPValue(graph, pValue));
        }
    }

    public static class GraphWithPValue {
        private Graph graph;
        private double pValue;

        public GraphWithPValue(Graph graph, double pValue) {
            this.graph = graph;
            this.pValue = pValue;
        }

        public Graph getGraph() {
            return graph;
        }

        public double getPValue() {
            return pValue;
        }

        public int hashCode() {
            return 17 * graph.hashCode();
        }

        public boolean equals(Object o) {
            if (o == null) return false;
            GraphWithPValue p = (GraphWithPValue) o;
            return (p.graph.equals(graph));
        }
    }

    public Score scoreGraph(Graph graph) {
        Graph dag = graph; //SearchGraphUtils.dagFromPattern(graph, getKnowledge());

        if (dag == null) {
            return Score.negativeInfinity();
        }

        scorer.score(dag);
        return new Score(scorer);
    }

    public Score scoreDag(Graph dag) {
//        SemPm semPm = new SemPm(dag);
//        SemEstimator semEstimator = new SemEstimator(dataSet, semPm, new SemOptimizerEm());
//        semEstimator.estimate();
//        SemIm estimatedSem = semEstimator.getEstimatedSem();
//        return new Score(estimatedSem);

        scorer.score(dag);

        return new Score(scorer);
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;

        if (knowledge.isViolatedBy(graph)) {
            throw new IllegalArgumentException("Graph violates knowledge.");
        }
    }

    public Graph getTrueModel() {
        return trueModel;
    }

    public void setTrueModel(Graph trueModel) {
        this.trueModel = trueModel;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setBeamWidth(int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException();
        this.beamWidth = beamWidth;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public Set<GraphWithPValue> getSignificantModels() {
        return significantModels;
    }

    private void addRequiredEdges(Graph graph) {
        for (Iterator<KnowledgeEdge> it =
                this.getKnowledge().requiredEdgesIterator(); it.hasNext();) {
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
                getKnowledge().forbiddenEdgesIterator(); it.hasNext();) {
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
        private Scorer scorer;
        private double pValue;
        private double fml;
        private double chisq;
        private double bic;
        private double aic;
        private double kic;
        private int dof;

        public Score(Scorer scorer) {
            this.scorer = scorer;

            this.pValue = scorer.getPValue();
            this.fml = scorer.getFml();
            this.chisq = scorer.getChiSquare();
            this.bic = scorer.getBicScore();
            this.aic = scorer.getAicScore();
            this.kic = scorer.getKicScore();
            this.dof = scorer.getDof();
        }

        private Score() {
            this.scorer = null;
            this.pValue = 0.0;
            this.fml = Double.POSITIVE_INFINITY;
            this.chisq = 0.0;
        }

        public SemIm getEstimatedSem() {
            return scorer.getEstSem();
        }

        public double getPValue() {
            return pValue;
        }

        public double getScore() {
//            return -fml;
//            return -chisq;
//            return -bic;
            return -aic;
//            return -kic;
        }

        public double getFml() {
            return fml;
        }

        public static Score negativeInfinity() {
            return new Score();
        }

        public int getDof() {
            return dof;
        }

        public double getChiSquare() {
            return chisq;
        }

        public double getBic() {
            return bic;
        }
    }

    /**
     * This method straightforwardly applies the standard definition of the numerical estimates of the second order
     * partial derivatives.  See for example Section 5.7 of Numerical Recipes in C.
     */
    public double secondPartialDerivative(FittingFunction f, int i, int j,
                                          double[] p, double delt) {
        double[] arg = new double[p.length];
        System.arraycopy(p, 0, arg, 0, p.length);

        arg[i] += delt;
        arg[j] += delt;
        double ff1 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff2 = f.evaluate(arg);

        arg[i] -= 2 * delt;
        arg[j] += 2 * delt;
        double ff3 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff4 = f.evaluate(arg);

        double fsSum = ff1 - ff2 - ff3 + ff4;

        return fsSum / (4.0 * delt * delt);
    }

    /**
     * Evaluates a fitting function for an array of parameters.
     *
     * @author Joseph Ramsey
     */
    static interface FittingFunction {

        /**
         * Returns the value of the function for the given array of parameter values.
         */
        double evaluate(double[] argument);

        /**
         * Returns the number of parameters.
         */
        int getNumParameters();
    }

    /**
     * Wraps a Sem for purposes of calculating its fitting function for given parameter values.
     *
     * @author Joseph Ramsey
     */
    static class SemFittingFunction implements FittingFunction {

        /**
         * The wrapped Sem.
         */
        private final SemIm sem;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public SemFittingFunction(SemIm sem) {
            this.sem = sem;
        }

        /**
         * Computes the maximum likelihood function value for the given parameters values as given by the optimizer.
         * These values are mapped to parameter values.
         */
        public double evaluate(double[] parameters) {
            sem.setFreeParamValues(parameters);

            // This needs to be FML-- see Bollen p. 109.
            return sem.getScore();
        }

        /**
         * Returns the number of arguments. Required by the MultivariateFunction interface.
         */
        public int getNumParameters() {
            return this.sem.getNumFreeParams();
        }
    }
}


