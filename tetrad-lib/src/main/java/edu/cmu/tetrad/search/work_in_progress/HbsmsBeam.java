///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Heuristic Best Significant Model Search using a beam search.
 * <p>
 * Improves the P value of a SEM IM by adding, removing, or reversing single edges.
 *
 * @author josephramsey
 */

public final class HbsmsBeam implements Hbsms {
    private final CovarianceMatrix cov;
    private final Graph externalGraph;
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    private final Scorer scorer;
    private Knowledge knowledge;
    private Graph graph;
    private double alpha = 0.05;
    private double highPValueAlpha = 0.05;
    private Graph trueModel;
    private SemIm originalSemIm;
    private SemIm newSemIm;
    private int beamWidth = 1;

    public HbsmsBeam(Graph graph, DataSet data, Knowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(data.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = new CovarianceMatrix(data);
        this.scorer = new DagScorer(this.cov);
    }

    public HbsmsBeam(Graph graph, CovarianceMatrix cov, Knowledge knowledge) {
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
        Graph bestGraph = GraphTransforms.dagFromCpdag(_graph, null);

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
            this.trueModel = GraphTransforms.cpdagForDag(this.trueModel);
        }

        System.out.println("Initial Score = " + this.nf.format(bestScore));
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());

        {
            bestGraph = increaseScoreLoop(bestGraph, getAlpha());
            bestGraph = removeZeroEdges(bestGraph);
        }

        Score score = scoreGraph(bestGraph);

        this.newSemIm = score.getEstimatedSem();

        return bestGraph;
    }


    private Graph increaseScoreLoop(Graph bestGraph, double alpha) {
        System.out.println("Increase score loop2");

        double initialScore = scoreGraph(bestGraph).getScore();

        Map<Graph, Double> S = new HashMap<>();
        S.put(bestGraph, initialScore);
        boolean changed = true;

        LOOP:
        while (changed) {
            changed = false;

            for (Graph s : new HashMap<>(S).keySet()) {
                List<Move> moves = new ArrayList<>();
                moves.addAll(getAddMoves(s));
//                moves.addAll(getRemoveMoves(s));
                moves.addAll(getRedirectMoves(s));

                boolean found = false;

                for (Move move : moves) {
                    Graph graph = makeMove(s, move);

                    if (getKnowledge().isViolatedBy(graph)) {
                        continue;
                    }

                    if (isCheckingCycles() && graph.paths().existsDirectedCycle()) {
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
                List<Node> parents = new ArrayList<>(graph.getParents(child));
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

    private Graph makeMove(Graph graph, Move move) {
        graph = new EdgeListGraph(graph);
        Edge firstEdge = move.getFirstEdge();
        Edge secondEdge = move.getSecondEdge();

        if (firstEdge != null && move.getType() == HbsmsBeam.Move.Type.ADD) {
            graph.removeEdge(firstEdge.getNode1(), firstEdge.getNode2());
            graph.addEdge(firstEdge);
        } else if (firstEdge != null && move.getType() == HbsmsBeam.Move.Type.REMOVE) {
            graph.removeEdge(firstEdge);
        } else if (firstEdge != null && move.getType() == HbsmsBeam.Move.Type.DOUBLE_REMOVE) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && move.getType() == HbsmsBeam.Move.Type.REDIRECT) {
            graph.removeEdge(graph.getEdge(firstEdge.getNode1(), firstEdge.getNode2()));
            graph.addEdge(firstEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbsmsBeam.Move.Type.ADD_COLLIDER) {
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
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbsmsBeam.Move.Type.REMOVE_COLLIDER) {
            graph.removeEdge(firstEdge);
            graph.removeEdge(secondEdge);
        } else if (firstEdge != null && secondEdge != null && move.getType() == HbsmsBeam.Move.Type.SWAP) {
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

                if (!graph.paths().isAncestorOf(nodes.get(j), nodes.get(i))) {
                    Edge edge = Edges.directedEdge(nodes.get(i), nodes.get(j));
                    moves.add(new Move(edge, HbsmsBeam.Move.Type.ADD));
                }
            }
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

            if (graph.paths().isAncestorOf(j, i)) {
                continue;
            }

            moves.add(new Move(Edges.directedEdge(j, i), HbsmsBeam.Move.Type.REDIRECT));
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
        return true;
    }

    public Score scoreGraph(Graph graph) {
        if (graph == null) {
            return Score.negativeInfinity();
        }

        this.scorer.score(graph);
        return new Score(this.scorer);
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

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;

        if (knowledge.isViolatedBy(this.graph)) {
            throw new IllegalArgumentException("Graph violates knowledge.");
        }
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
            if (!graph.paths().isAncestorOf(nodeB, nodeA)) {
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
                if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                    graph.removeEdges(nodeA, nodeB);
                    graph.addDirectedEdge(nodeB, nodeA);
                    TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                }
            }
        }
    }

    public static class Move {
        private final Edge edge;
        private final HbsmsBeam.Move.Type type;
        private Edge secondEdge;

        public Move(Edge edge, HbsmsBeam.Move.Type type) {
            this.edge = edge;
            this.type = type;
        }

        public Move(Edge edge, Edge secondEdge, HbsmsBeam.Move.Type type) {
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

        public HbsmsBeam.Move.Type getType() {
            return this.type;
        }

        public String toString() {
            String s = (this.secondEdge != null) ? (this.secondEdge + ", ") : "";
            return "<" + this.edge + ", " + s + this.type + ">";

        }

        public enum Type {
            ADD, REMOVE, REDIRECT, ADD_COLLIDER, REMOVE_COLLIDER, SWAP, DOUBLE_REMOVE
        }
    }

    public static class Score {
        private final double fml;
        private final double chisq;
        private final double bic;
        private Scorer scorer = null;
        private int dof;

        public Score(Scorer scorer) {
            this.scorer = scorer;
            this.fml = scorer.getFml();
            this.dof = scorer.getDof();
            int sampleSize = scorer.getSampleSize();

            this.chisq = (sampleSize - 1) * getFml();
            this.bic = this.chisq - this.dof * FastMath.log(sampleSize);
        }

        private Score() {
            int sampleSize = 1000;
            this.fml = Double.POSITIVE_INFINITY;
            this.chisq = (sampleSize - 1) * this.fml;
            this.bic = this.chisq - this.dof * FastMath.log(sampleSize);
        }

        public static Score negativeInfinity() {
            return new Score();
        }

        public SemIm getEstimatedSem() {
            return this.scorer.getEstSem();
        }

        public double getPValue() {
            return this.scorer.getPValue();
        }

        public double getScore() {
            return -this.bic;
        }

        public double getFml() {
            return this.scorer.getFml();
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


