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
 * @version $Id: $Id
 */
public final class HbsmsBeam implements Hbsms {

    /**
     * The covariance matrix.
     */
    private final CovarianceMatrix cov;
    /**
     * The external graph.
     */
    private final Graph externalGraph;
    /**
     * The number format.
     */
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    /**
     * The scorer.
     */
    private final Scorer scorer;
    /**
     * The knowledge.
     */
    private Knowledge knowledge;
    /**
     * The graph.
     */
    private Graph graph;
    /**
     * The alpha.
     */
    private double alpha = 0.05;
    /**
     * The high p value alpha.
     */
    private double highPValueAlpha = 0.05;
    /**
     * The true model.
     */
    private Graph trueModel;
    /**
     * The original sem im.
     */
    private SemIm originalSemIm;
    /**
     * The new sem im.
     */
    private SemIm newSemIm;
    /**
     * The beam width.
     */
    private int beamWidth = 1;

    /**
     * <p>Constructor for HbsmsBeam.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param data      a {@link edu.cmu.tetrad.data.DataSet} object
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public HbsmsBeam(Graph graph, DataSet data, Knowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(data.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = new CovarianceMatrix(data);
        this.scorer = new DagScorer(this.cov);
    }

    /**
     * <p>Constructor for HbsmsBeam.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param cov       a {@link edu.cmu.tetrad.data.CovarianceMatrix} object
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public HbsmsBeam(Graph graph, CovarianceMatrix cov, Knowledge knowledge) {
        if (graph == null) graph = new EdgeListGraph(cov.getVariables());

        this.knowledge = knowledge;
        this.graph = graph;
        this.externalGraph = new EdgeListGraph(graph);
        this.cov = cov;
        this.scorer = new DagScorer(cov);
    }

    /**
     * <p>search.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
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
            this.trueModel = GraphTransforms.dagToCpdag(this.trueModel);
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

    /**
     * <p>removeZeroEdges.</p>
     *
     * @param bestGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
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
                        TetradLogger.getInstance().forceLogMessage("Not removing " + edge + " because it is required.");
                        continue;
                    }

                    System.out.println("Removing edge " + edge + " because it has p = " + p);
                    TetradLogger.getInstance().forceLogMessage("Removing edge " + edge + " because it has p = " + p);
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

    /**
     * <p>Getter for the field <code>graph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * <p>Getter for the field <code>originalSemIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getOriginalSemIm() {
        return this.originalSemIm;
    }

    /**
     * <p>Getter for the field <code>newSemIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getNewSemIm() {
        return this.newSemIm;
    }

    /**
     * <p>Getter for the field <code>highPValueAlpha</code>.</p>
     *
     * @return a double
     */
    public double getHighPValueAlpha() {
        return this.highPValueAlpha;
    }

    /**
     * {@inheritDoc}
     */
    public void setHighPValueAlpha(double highPValueAlpha) {
        this.highPValueAlpha = highPValueAlpha;
    }

    /**
     * <p>isCheckingCycles.</p>
     *
     * @return a boolean
     */
    public boolean isCheckingCycles() {
        return true;
    }

    /**
     * <p>scoreGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.HbsmsBeam.Score} object
     */
    public Score scoreGraph(Graph graph) {
        if (graph == null) {
            return Score.negativeInfinity();
        }

        this.scorer.score(graph);
        return new Score(this.scorer);
    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return a double
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * {@inheritDoc}
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * {@inheritDoc}
     */
    public void setBeamWidth(int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException();
        this.beamWidth = beamWidth;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
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
                String message = "Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB);
                TetradLogger.getInstance().forceLogMessage(message);
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
                    String message = "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA);
                    TetradLogger.getInstance().forceLogMessage(message);
                }
            }
        }
    }

    /**
     * A move.
     */
    public static class Move {

        /**
         * The edge.
         */
        private final Edge edge;

        /**
         * The type.
         */
        private final HbsmsBeam.Move.Type type;

        /**
         * The second edge.
         */
        private Edge secondEdge;

        /**
         * <p>Constructor for Move.</p>
         *
         * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
         * @param type a {@link edu.cmu.tetrad.search.work_in_progress.HbsmsBeam.Move.Type} object
         */
        public Move(Edge edge, HbsmsBeam.Move.Type type) {
            this.edge = edge;
            this.type = type;
        }

        /**
         * <p>Constructor for Move.</p>
         *
         * @param edge       a {@link edu.cmu.tetrad.graph.Edge} object
         * @param secondEdge a {@link edu.cmu.tetrad.graph.Edge} object
         * @param type       a {@link edu.cmu.tetrad.search.work_in_progress.HbsmsBeam.Move.Type} object
         */
        public Move(Edge edge, Edge secondEdge, HbsmsBeam.Move.Type type) {
            this.edge = edge;
            this.secondEdge = secondEdge;
            this.type = type;
        }

        /**
         * <p>Getter for the field <code>edge</code>.</p>
         *
         * @return a {@link edu.cmu.tetrad.graph.Edge} object
         */
        public Edge getFirstEdge() {
            return this.edge;
        }

        /**
         * <p>Getter for the field <code>secondEdge</code>.</p>
         *
         * @return a {@link edu.cmu.tetrad.graph.Edge} object
         */
        public Edge getSecondEdge() {
            return this.secondEdge;
        }

        /**
         * <p>getType.</p>
         *
         * @return a {@link edu.cmu.tetrad.search.work_in_progress.HbsmsBeam.Move.Type} object
         */
        public HbsmsBeam.Move.Type getType() {
            return this.type;
        }

        /**
         * Returns a string representation of this move.
         *
         * @return a string representation of this move.
         */
        public String toString() {
            String s = (this.secondEdge != null) ? (this.secondEdge + ", ") : "";
            return "<" + this.edge + ", " + s + this.type + ">";

        }

        /**
         * Types of moves the algorithm can make.
         */
        public enum Type {
            /**
             * Add an edge.
             */
            ADD,

            /**
             * Remove an edge.
             */
            REMOVE,

            /**
             * Redirect an edge.
             */
            REDIRECT,

            /**
             * Add a collider.
             */
            ADD_COLLIDER,

            /**
             * Remove a collider.
             */
            REMOVE_COLLIDER,

            /**
             * Swap two edges.
             */
            SWAP,

            /**
             * Remove two edges.
             */
            DOUBLE_REMOVE
        }
    }

    /**
     * The score.
     */
    public static class Score {
        /**
         * The fml.
         */
        private final double fml;
        /**
         * The chisq.
         */
        private final double chisq;
        /**
         * The bic.
         */
        private final double bic;
        /**
         * The scorer.
         */
        private Scorer scorer = null;
        /**
         * The dof.
         */
        private int dof;

        /**
         * <p>Constructor for Score.</p>
         *
         * @param scorer a {@link Scorer} object
         */
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

        /**
         * <p>negativeInfinity.</p>
         *
         * @return a {@link edu.cmu.tetrad.search.work_in_progress.HbsmsBeam.Score} object
         */
        public static Score negativeInfinity() {
            return new Score();
        }

        /**
         * <p>getEstimatedSem.</p>
         *
         * @return a {@link edu.cmu.tetrad.sem.SemIm} object
         */
        public SemIm getEstimatedSem() {
            return this.scorer.getEstSem();
        }

        /**
         * <p>getPValue.</p>
         *
         * @return a double
         */
        public double getPValue() {
            return this.scorer.getPValue();
        }

        /**
         * <p>getScore.</p>
         *
         * @return a double
         */
        public double getScore() {
            return -this.bic;
        }

        /**
         * <p>getFml.</p>
         *
         * @return a double
         */
        public double getFml() {
            return this.scorer.getFml();
        }

        /**
         * <p>getDof.</p>
         *
         * @return a int
         */
        public int getDof() {
            return this.dof;
        }

        /**
         * <p>getChiSquare.</p>
         *
         * @return a double
         */
        public double getChiSquare() {
            return this.chisq;
        }

        /**
         * <p>getBic.</p>
         *
         * @return a double
         */
        public double getBic() {
            return this.bic;
        }
    }

}


