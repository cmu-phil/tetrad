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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.DagInCpcagIterator;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.DagScorer;
import edu.cmu.tetrad.sem.Scorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * <p>Heuristic Best Significant Model Search using the GES algorithm.</p>
 * <p>Improves the P value of a SEM IM by adding, removing, or reversing single edges.</p>
 *
 * @author josephramsey
 */
public final class HbsmsGes implements Hbsms {
    private final Graph graph;
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    private final Set<GraphWithPValue> significantModels = new HashSet<>();
    private final Scorer scorer;
    private Knowledge knowledge = new Knowledge();
    private double alpha = 0.05;
    private SemIm originalSemIm;
    private SemIm newSemIm;

    public HbsmsGes(Graph graph, DataSet data) {
        if (graph == null) throw new NullPointerException("Graph not specified.");

        final boolean allowArbitraryOrientations = true;
        final boolean allowNewColliders = true;
        DagInCpcagIterator iterator = new DagInCpcagIterator(graph, getKnowledge(), allowArbitraryOrientations,
                allowNewColliders);
        graph = iterator.next();
        graph = GraphTransforms.cpdagForDag(graph);

        if (GraphUtils.containsBidirectedEdge(graph)) {
            throw new IllegalArgumentException("Contains bidirected edge.");
        }

        this.graph = graph;
        this.scorer = new DagScorer(data);
    }

    /**
     * Test if the candidate deletion is a valid operation (Theorem 17 from Chickering, 2002).
     */
    private static boolean validDelete(Node x, Node y, Set<Node> h,
                                       Graph graph) {
        List<Node> naYXH = HbsmsGes.findNaYX(x, y, graph);
        naYXH.removeAll(h);
        return GraphUtils.isClique(naYXH, graph);
    }

    /**
     * Get all nodes that are connected to Y by an undirected edge and not adjacent to X.
     */
    private static List<Node> getTNeighbors(Node x, Node y, Graph graph) {
        List<Node> tNeighbors = new LinkedList<>(graph.getAdjacentNodes(y));
        tNeighbors.removeAll(graph.getAdjacentNodes(x));

        for (int i = tNeighbors.size() - 1; i >= 0; i--) {
            Node z = tNeighbors.get(i);
            Edge edge = graph.getEdge(y, z);

            if (!Edges.isUndirectedEdge(edge)) {
                tNeighbors.remove(z);
            }
        }

        return tNeighbors;
    }

    /**
     * Get all nodes that are connected to Y by an undirected edge and adjacent to X
     */
    private static List<Node> getHNeighbors(Node x, Node y, Graph graph) {
        List<Node> hNeighbors = new LinkedList<>(graph.getAdjacentNodes(y));
        hNeighbors.retainAll(graph.getAdjacentNodes(x));

        for (int i = hNeighbors.size() - 1; i >= 0; i--) {
            Node z = hNeighbors.get(i);
            Edge edge = graph.getEdge(y, z);

            if (!Edges.isUndirectedEdge(edge)) {
                hNeighbors.remove(z);
            }
        }

        return hNeighbors;
    }

    /**
     * Find all nodes that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
     * directed edge) NOTE: very inefficient implementation, since the getModel library does not allow access to the
     * adjacency list/matrix of the graph.
     */
    private static List<Node> findNaYX(Node x, Node y, Graph graph) {
        List<Node> naYX = new LinkedList<>(graph.getAdjacentNodes(y));
        naYX.retainAll(graph.getAdjacentNodes(x));

        for (int i = 0; i < naYX.size(); i++) {
            Node z = naYX.get(i);
            Edge edge = graph.getEdge(y, z);

            if (!Edges.isUndirectedEdge(edge)) {
                naYX.remove(z);
            }
        }

        return naYX;
    }

    private static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<>();
        int total = (int) FastMath.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<>();
            String selection = Integer.toBinaryString(i);
            for (int j = selection.length() - 1; j >= 0; j--) {
                if (selection.charAt(j) == '1') {
                    newSet.add(nodes.get(selection.length() - j - 1));
                }
            }
            subsets.add(newSet);
        }
        return subsets;
    }

    private void saveModelIfSignificant(Graph graph) {
        double pValue = scoreGraph(graph).getPValue();

        if (pValue > this.alpha) {
            getSignificantModels().add(new GraphWithPValue(graph, pValue));
        }
    }

    public Score scoreGraph(Graph graph) {
        Graph dag = GraphTransforms.dagFromCpdag(graph, getKnowledge());

        this.scorer.score(dag);
        return new Score(this.scorer);
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

    public void setHighPValueAlpha(double highPValueAlpha) {
    }


    /*
     * Do an actual insertion
     * (Definition 12 from Chickering, 2002).
     **/

    public Score scoreDag(Graph dag) {

        this.scorer.score(dag);
        return new Score(this.scorer);
    }

    public Graph search() {
        Score score1 = scoreGraph(getGraph());
        double score = score1.getScore();
        System.out.println(getGraph());
        System.out.println(score);

        this.originalSemIm = score1.getEstimatedSem();

        saveModelIfSignificant(getGraph());

        // Do forward search.
        score = fes(getGraph(), score);

        // Do backward search.
        bes(getGraph(), score);

        Score _score = scoreGraph(getGraph());
        this.newSemIm = _score.getEstimatedSem();

        return new EdgeListGraph(getGraph());
    }

    private double fes(Graph graph, double score) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");
        double bestScore = score;
        TetradLogger.getInstance().log("info", "Initial Score = " + this.nf.format(bestScore));

        Node x, y;
        Set<Node> t = new HashSet<>();

        do {
            x = y = null;
            List<Node> nodes = graph.getNodes();
            RandomUtil.shuffle(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                Node _x = nodes.get(i);

                for (Node _y : nodes) {
                    if (_x == _y) {
                        continue;
                    }

                    if (graph.isAdjacentTo(_x, _y)) {
                        continue;
                    }

                    if (getKnowledge().isForbidden(_x.getName(),
                            _y.getName())) {
                        continue;
                    }

                    List<Node> tNeighbors = HbsmsGes.getTNeighbors(_x, _y, graph);
                    List<Set<Node>> tSubsets = HbsmsGes.powerSet(tNeighbors);

                    for (Set<Node> tSubset : tSubsets) {

                        if (invalidSetByKnowledge(_x, _y, tSubset, true)) {
                            continue;
                        }

                        Graph graph2 = new EdgeListGraph(graph);

                        tryInsert(_x, _y, tSubset, graph2);

                        if (graph2.paths().existsDirectedCycle()) {
                            continue;
                        }

                        double evalScore = scoreGraph(graph2).getScore();

                        TetradLogger.getInstance().log("edgeEvaluations", "Trying to add " + _x + "-->" + _y + " evalScore = " +
                                evalScore);

                        if (!(evalScore > bestScore && evalScore > score)) {
                            continue;
                        }

                        if (!validInsert(_x, _y, tSubset, graph)) {
                            continue;
                        }

                        bestScore = evalScore;
                        x = _x;
                        y = _y;
                        t = tSubset;
                    }
                }
            }

            if (x != null) {
                score = bestScore;
                insert(x, y, t, graph);
                rebuildCPDAG(graph);

                saveModelIfSignificant(graph);

                if (scoreGraph(graph).getPValue() > this.alpha) {
                    return score;
                }
            }
        } while (x != null);
        return score;
    }

    private void bes(Graph graph, double initialScore) {
        TetradLogger.getInstance().log("info", "** BACKWARD ELIMINATION SEARCH");
        TetradLogger.getInstance().log("info", "Initial Score = " + this.nf.format(initialScore));
        double bestScore = initialScore;
        Node x, y;
        Set<Node> t = new HashSet<>();
        do {
            x = y = null;
            List<Edge> graphEdges = new ArrayList<>(graph.getEdges());
            RandomUtil.shuffle(graphEdges);

            for (Edge edge : graphEdges) {
                Node _x;
                Node _y;

                if (Edges.isUndirectedEdge(edge)) {
                    _x = edge.getNode1();
                    _y = edge.getNode2();
                } else {
                    _x = Edges.getDirectedEdgeTail(edge);
                    _y = Edges.getDirectedEdgeHead(edge);
                }

                if (!getKnowledge().noEdgeRequired(_x.getName(), _y.getName())) {
                    continue;
                }

                List<Node> hNeighbors = HbsmsGes.getHNeighbors(_x, _y, graph);
                List<Set<Node>> hSubsets = HbsmsGes.powerSet(hNeighbors);

                for (Set<Node> hSubset : hSubsets) {
                    if (invalidSetByKnowledge(_x, _y, hSubset, false)) {
                        continue;
                    }

                    Graph graph2 = new EdgeListGraph(graph);

                    tryDelete(_x, _y, hSubset, graph2);

                    double evalScore = scoreGraph(graph2).getScore();

                    if (!(evalScore > bestScore)) {
                        continue;
                    }

                    if (!HbsmsGes.validDelete(_x, _y, hSubset, graph)) {
                        continue;
                    }

                    bestScore = evalScore;
                    x = _x;
                    y = _y;
                    t = hSubset;
                }
            }
            if (x != null) {

                if (!graph.isAdjacentTo(x, y)) {
                    throw new IllegalArgumentException("trying to delete a nonexistent edge! " + x + "---" + y);
                }

                delete(x, y, t, graph);


                rebuildCPDAG(graph);

                saveModelIfSignificant(graph);
            }
        } while (x != null);

    }

    /*
     * Test if the candidate insertion is a valid operation
     * (Theorem 15 from Chickering, 2002).
     **/

    private void tryInsert(Node x, Node y, Set<Node> subset, Graph graph) {
        graph.addDirectedEdge(x, y);

        for (Node t : subset) {
            Edge oldEdge = graph.getEdge(t, y);

            if (!Edges.isUndirectedEdge(oldEdge)) {
                throw new IllegalArgumentException("Should be undirected: " + oldEdge);
            }

            graph.removeEdge(t, y);
            graph.addDirectedEdge(t, y);

            TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                    graph.getEdge(t, y));
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void tryDelete(Node x, Node y, Set<Node> subset, Graph graph) {
        graph.removeEdge(x, y);

        for (Node h : subset) {
            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                Edge oldEdge = graph.getEdge(x, h);
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(x, h));
            }

            if (Edges.isUndirectedEdge(graph.getEdge(y, h))) {
                graph.removeEdge(y, h);
                graph.addDirectedEdge(y, h);

                Edge oldEdge = graph.getEdge(y, h);
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }
        }
    }

    private void insert(Node x, Node y, Set<Node> subset, Graph graph) {
        if (graph.isAdjacentTo(x, y)) {
            return;
        }

        graph.addDirectedEdge(x, y);

        for (Node t : subset) {
            Edge oldEdge = graph.getEdge(t, y);

            if (!Edges.isUndirectedEdge(oldEdge)) {
                throw new IllegalArgumentException("Should be undirected: " + oldEdge);
            }

            graph.removeEdge(t, y);
            graph.addDirectedEdge(t, y);

            TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                    graph.getEdge(t, y));
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void delete(Node x, Node y, Set<Node> subset, Graph graph) {

        {
            Edge oldEdge = graph.getEdge(x, y);
            System.out.println(graph.getNumEdges() + ". DELETE " + oldEdge +
                    " " + subset +
                    " (" + this.nf.format(scoreGraph(graph).getPValue()) + ")");
        }

        graph.removeEdge(x, y);

        for (Node h : subset) {
            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                Edge oldEdge = graph.getEdge(x, h);
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(x, h));
            }

            if (Edges.isUndirectedEdge(graph.getEdge(y, h))) {
                graph.removeEdge(y, h);
                graph.addDirectedEdge(y, h);

                Edge oldEdge = graph.getEdge(y, h);
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }
        }
    }

    private boolean validInsert(Node x, Node y, Set<Node> subset, Graph graph) {
        List<Node> naYXT = new LinkedList<>(subset);
        naYXT.addAll(HbsmsGes.findNaYX(x, y, graph));

        return GraphUtils.isClique(naYXT, graph) && isSemiDirectedBlocked(x, y, naYXT, graph, new HashSet<>());

    }

    private boolean invalidSetByKnowledge(Node x, Node y, Set<Node> subset,
                                          boolean insertMode) {
        if (insertMode) {
            for (Node aSubset : subset) {
                if (getKnowledge().isForbidden(aSubset.getName(),
                        y.getName())) {
                    return true;
                }
            }
        } else {
            for (Node nextElement : subset) {
                if (getKnowledge().isForbidden(x.getName(),
                        nextElement.getName())) {
                    return true;
                }
                if (getKnowledge().isForbidden(y.getName(),
                        nextElement.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies if every semidirected path from y to x contains a node in naYXT.
     */
    private boolean isSemiDirectedBlocked(Node x, Node y, List<Node> naYXT,
                                          Graph graph, Set<Node> marked) {
        if (naYXT.contains(y)) {
            return true;
        }

        if (y == x) {
            return false;
        }

        for (Node node1 : graph.getNodes()) {
            if (node1 == y || marked.contains(node1)) {
                continue;
            }

            if (graph.isAdjacentTo(y, node1) && !graph.isParentOf(node1, y)) {
                marked.add(node1);

                if (!isSemiDirectedBlocked(x, node1, naYXT, graph, marked)) {
                    return false;
                }

                marked.remove(node1);
            }
        }

        return true;
    }

    /**
     * Completes a CPDAG that was modified by an insertion/deletion operator Based on the algorithm described on
     * Appendix C of (Chickering, 2002).
     */
    private void rebuildCPDAG(Graph graph) {
        GraphSearchUtils.basicCpdag(graph);
        addRequiredEdges(graph);
        pdagWithBk(graph, getKnowledge());

        TetradLogger.getInstance().log("rebuiltCPDAGs", "Rebuilt CPDAG = " + graph);
    }

    /**
     * Fully direct a graph with background knowledge. I am not sure how to adapt Chickering's suggested algorithm above
     * (dagToPdag) to incorporate background knowledge, so I am also implementing this algorithm based on Meek's 1995
     * UAI paper. Notice it is the same implemented in PcSearch. *IMPORTANT!* *It assumes all colliders are oriented, as
     * well as arrows dictated by time order.*
     */
    private void pdagWithBk(Graph graph, Knowledge knowledge) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);
    }

    private void addRequiredEdges(Graph graph) {
        // Add required edges.
        List<Node> nodes = graph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                if (getKnowledge().isRequired(nodes.get(i).getName(), nodes.get(j).getName())) {
                    if (!graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                        graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                    }
                }
            }
        }
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setBeamWidth(int beamWidth) {
//        if (beamWidth < 1) throw new IllegalArgumentException();
        // Do nothing. We don't care about beam width.
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    public Set<GraphWithPValue> getSignificantModels() {
        return this.significantModels;
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
            if (!(o instanceof GraphWithPValue)) return false;
            GraphWithPValue p = (GraphWithPValue) o;
            return (p.graph.equals(this.graph));
        }
    }

    public static class Score {
        private final Scorer scorer;
        private final double pValue;
        private final double fml;
        private final double chisq;
        private final double bic;
        //        private double aic;
        private final int dof;

        public Score(Scorer scorer) {
            this.scorer = scorer;
            this.pValue = scorer.getPValue();
            this.fml = scorer.getFml();
            this.chisq = scorer.getChiSquare();
            this.bic = scorer.getBicScore();
//            this.aic = scorer.getAicScore();
            this.dof = scorer.getDof();
        }

        public SemIm getEstimatedSem() {
            return this.scorer.getEstSem();
        }

        public double getPValue() {
            return this.pValue;
        }

        public double getScore() {

            return -this.bic;
//            return -aic;
        }

        public double getFml() {
            return this.fml;
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


