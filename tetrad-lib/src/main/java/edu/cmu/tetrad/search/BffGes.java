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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.DagScorer;
import edu.cmu.tetrad.sem.Scorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Best Fit Finder using the GES algorithm.
 * </p>
 * Improves the P value of a SEM IM by adding, removing, or reversing single edges.
 *
 * @author Joseph Ramsey
 */

public final class BffGes implements Bff {
    private IKnowledge knowledge = new Knowledge2();
    private Graph graph;
    private double alpha = 0.05;
    private double highPValueAlpha = 0.05;
    private final NumberFormat nf = new DecimalFormat("0.0#########");
    private Set<GraphWithPValue> significantModels = new HashSet<GraphWithPValue>();
    private SemIm originalSemIm;
    private SemIm newSemIm;
    private Scorer scorer;
    private Graph newDag;

    public BffGes(Graph graph, DataSet data) {
        if (graph == null) throw new NullPointerException("Graph not specified.");

        boolean allowArbitraryOrientations = true;
        boolean allowNewColliders = true;
        DagInPatternIterator iterator = new DagInPatternIterator(graph, getKnowledge(), allowArbitraryOrientations,
                allowNewColliders);
        graph = iterator.next();
        graph = SearchGraphUtils.patternForDag(graph);

        if (GraphUtils.containsBidirectedEdge(graph)) {
            throw new IllegalArgumentException("Contains bidirected edge.");
        }

        this.graph = graph;
        this.scorer = new DagScorer(data);
    }

    private void saveModelIfSignificant(Graph graph) {
        double pValue = scoreGraph(graph).getPValue();

        if (pValue > alpha) {
            getSignificantModels().add(new GraphWithPValue(graph, pValue));
        }
    }

    public void setNewDag(Graph newDag) {
        this.newDag = newDag;
    }

    public Graph getNewDag() {
        return newDag;
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
            if (!(o instanceof GraphWithPValue)) return false;
            GraphWithPValue p = (GraphWithPValue) o;
            return (p.graph.equals(graph));
        }
    }

    public Score scoreGraph(Graph graph) {
        Graph dag = SearchGraphUtils.dagFromPattern(graph, getKnowledge());

        if (dag == null) {
            return Score.negativeInfinity();
        }

        scorer.score(dag);
        return new Score(scorer);
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

    public void setHighPValueAlpha(double highPValueAlpha) {
        this.highPValueAlpha = highPValueAlpha;
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

//    private void removeHighPValueEdges(Graph bestGraph) {
//        boolean changed = true;
//
//        while (changed) {
//            changed = false;
//            Score score = scoreGraph(bestGraph);
//            SemIm estSem = score.getEstimatedSem();
//
//            for (Parameter param : estSem.getSemPm().getParameters()) {
//                if (param.getType() != ParamType.COEF) {
//                    continue;
//                }
//
//                double p = estSem.getPValue(param, 10000);
//                Edge edge = bestGraph.getEdge(param.getNodeA(), param.getNodeB());
//
//                if (p > getHighPValueAlpha()) {
//                    System.out.println("Removing edge " + edge + " because it has p = " + p);
//                    bestGraph.removeEdge(edge);
//                    changed = true;
//                }
//            }
//        }
//    }

    public Graph search() {
        Score score1 = scoreGraph(getGraph());
        double score = score1.getScore();
        System.out.println(getGraph());
        System.out.println(score);

        originalSemIm = score1.getEstimatedSem();

        saveModelIfSignificant(getGraph());

//        removeHighPValueEdges(getGraph());

        // Do forward search.
        score = fes(getGraph(), score);

        // Do backward search.
        bes(getGraph(), score);

//        removeHighPValueEdges(getGraph());

        setNewDag(SearchGraphUtils.dagFromPattern(getGraph()));

        Score _score = scoreGraph(getGraph());
        newSemIm = _score.getEstimatedSem();

        return new EdgeListGraph(getGraph());
    }

    private double fes(Graph graph, double score) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");
        double bestScore = score;
        TetradLogger.getInstance().log("info", "Initial Score = " + nf.format(bestScore));

        Node x, y;
        Set<Node> t = new HashSet<Node>();

        do {
            x = y = null;
            List<Node> nodes = graph.getNodes();
            Collections.shuffle(nodes);

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

                    List<Node> tNeighbors = getTNeighbors(_x, _y, graph);
                    List<Set<Node>> tSubsets = powerSet(tNeighbors);

                    for (Set<Node> tSubset : tSubsets) {

                        if (!validSetByKnowledge(_x, _y, tSubset, true)) {
                            continue;
                        }

                        Graph graph2 = new EdgeListGraph(graph);

                        tryInsert(_x, _y, tSubset, graph2, true);

                        if (graph2.existsDirectedCycle()) {
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
                insert(x, y, t, graph, true);
                rebuildPattern(graph);

                saveModelIfSignificant(graph);

                if (scoreGraph(graph).getPValue() > alpha) {
                    return score;
                }
            }
        } while (x != null);
        return score;
    }

    private double bes(Graph graph, double initialScore) {
        TetradLogger.getInstance().log("info", "** BACKWARD ELIMINATION SEARCH");
        TetradLogger.getInstance().log("info", "Initial Score = " + nf.format(initialScore));
        double score = initialScore;
        double bestScore = score;
        Node x, y;
        Set<Node> t = new HashSet<Node>();
        do {
            x = y = null;
            List<Edge> graphEdges = new ArrayList<Edge>(graph.getEdges());
            Collections.shuffle(graphEdges);

            for (Edge edge : graphEdges) {
                Node _x, _y;

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

                List<Node> hNeighbors = getHNeighbors(_x, _y, graph);
                List<Set<Node>> hSubsets = powerSet(hNeighbors);

                for (Set<Node> hSubset : hSubsets) {
                    if (!validSetByKnowledge(_x, _y, hSubset, false)) {
                        continue;
                    }

                    Graph graph2 = new EdgeListGraph(graph);

                    tryDelete(_x, _y, hSubset, graph2, true);

                    double evalScore = scoreGraph(graph2).getScore();

                    if (!(evalScore > bestScore)) {
                        continue;
                    }

                    if (!validDelete(_x, _y, hSubset, graph)) {
                        continue;
                    }

                    bestScore = evalScore;
                    x = _x;
                    y = _y;
                    t = hSubset;
                }
            }
            if (x != null) {
//                if (scoreGraph(graph).getPValue() < alpha) {
//                    return score;
//                }

                if (!graph.isAdjacentTo(x, y)) {
                    throw new IllegalArgumentException("trying to delete a nonexistent edge! " + x + "---" + y);
                }

                score = bestScore;
                delete(x, y, t, graph, true);


                rebuildPattern(graph);

                saveModelIfSignificant(graph);
            }
        } while (x != null);

        return score;
    }


    /*
    * Do an actual insertion
    * (Definition 12 from Chickering, 2002).
    **/

    private void tryInsert(Node x, Node y, Set<Node> subset, Graph graph, boolean log) {
        graph.addDirectedEdge(x, y);

        for (Node t : subset) {
            Edge oldEdge = graph.getEdge(t, y);

            if (!Edges.isUndirectedEdge(oldEdge)) {
                throw new IllegalArgumentException("Should be undirected: " + oldEdge);
            }

            graph.removeEdge(t, y);
            graph.addDirectedEdge(t, y);

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(t, y));
            }
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void tryDelete(Node x, Node y, Set<Node> subset, Graph graph, boolean log) {
        graph.removeEdge(x, y);

        for (Node h : subset) {
            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                if (log) {
                    Edge oldEdge = graph.getEdge(x, h);
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(x, h));
                }
            }

            if (Edges.isUndirectedEdge(graph.getEdge(y, h))) {
                graph.removeEdge(y, h);
                graph.addDirectedEdge(y, h);

                if (log) {
                    Edge oldEdge = graph.getEdge(y, h);
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(y, h));
                }
            }
        }
    }

    private void insert(Node x, Node y, Set<Node> subset, Graph graph, boolean log) {
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

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(t, y));
            }
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void delete(Node x, Node y, Set<Node> subset, Graph graph, boolean log) {

        if (log) {
            Edge oldEdge = graph.getEdge(x, y);
            System.out.println(graph.getNumEdges() + ". DELETE " + oldEdge +
                    " " + subset +
                    " (" + nf.format(scoreGraph(graph).getPValue()) + ")");
        }

        graph.removeEdge(x, y);

        for (Node h : subset) {
            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                if (log) {
                    Edge oldEdge = graph.getEdge(x, h);
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(x, h));
                }
            }

            if (Edges.isUndirectedEdge(graph.getEdge(y, h))) {
                graph.removeEdge(y, h);
                graph.addDirectedEdge(y, h);

                if (log) {
                    Edge oldEdge = graph.getEdge(y, h);
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(y, h));
                }
            }
        }
    }

    /*
    * Test if the candidate insertion is a valid operation
    * (Theorem 15 from Chickering, 2002).
    **/

    private boolean validInsert(Node x, Node y, Set<Node> subset, Graph graph) {
        List<Node> naYXT = new LinkedList<Node>(subset);
        naYXT.addAll(findNaYX(x, y, graph));

        return isClique(naYXT, graph) && isSemiDirectedBlocked(x, y, naYXT, graph, new HashSet<Node>());

    }

    /**
     * Test if the candidate deletion is a valid operation (Theorem 17 from Chickering, 2002).
     */
    private static boolean validDelete(Node x, Node y, Set<Node> h,
                                       Graph graph) {
        List<Node> naYXH = findNaYX(x, y, graph);
        naYXH.removeAll(h);
        return isClique(naYXH, graph);
    }

    /**
     * Get all nodes that are connected to Y by an undirected edge and not adjacent to X.
     */
    private static List<Node> getTNeighbors(Node x, Node y, Graph graph) {
        List<Node> tNeighbors = new LinkedList<Node>(graph.getAdjacentNodes(y));
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
        List<Node> hNeighbors = new LinkedList<Node>(graph.getAdjacentNodes(y));
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
        List<Node> naYX = new LinkedList<Node>(graph.getAdjacentNodes(y));
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

    private boolean validSetByKnowledge(Node x, Node y, Set<Node> subset,
                                        boolean insertMode) {
        if (insertMode) {
            for (Node aSubset : subset) {
                if (getKnowledge().isForbidden(aSubset.getName(),
                        y.getName())) {
                    return false;
                }
            }
        } else {
            for (Node nextElement : subset) {
                if (getKnowledge().isForbidden(x.getName(),
                        nextElement.getName())) {
                    return false;
                }
                if (getKnowledge().isForbidden(y.getName(),
                        nextElement.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

//    private double scoreGraphChange(Node y, Set<Node> parents1,
//                                    Set<Node> parents2, Graph graph) {
//        graph = SearchGraphUtils.dagFromPattern(graph);
//
//        List<Node> currentParents = graph.getParents(y);
//        List<Node> currentChildren = graph.getChildren(y);
//
//        for (Node node : currentParents) {
//            graph.removeEdge(node, y);
//        }
//
//        for (Node node : currentChildren) {
//            graph.removeEdge(y, node);
//        }
//
//        for (Node node : parents1) {
//            graph.addDirectedEdge(node, y);
//        }
//
//        double score1 = scoreGraph(graph).getScore();
//
//        saveModelIfSignificant(graph);
//
//        for (Node node : parents1) {
//            graph.removeEdge(node, y);
//        }
//
//        for (Node node : parents2) {
//            graph.addDirectedEdge(node, y);
//        }
//
//        double score2 = scoreGraph(graph).getScore();
//
//        saveModelIfSignificant(graph);
//
//        for (Node node : parents2) {
//            graph.removeEdge(node, y);
//        }
//
//        for (Node node : currentParents) {
//            graph.addDirectedEdge(node, y);
//        }
//
//        for (Node node : currentChildren) {
//            graph.addDirectedEdge(y, node);
//        }
//
//        return score1 - score2;
//    }

    /**
     * @return true iif the given set forms a clique in the given graph.
     */
    private static boolean isClique(List<Node> set, Graph graph) {
        List<Node> setv = new LinkedList<Node>(set);
        for (int i = 0; i < setv.size() - 1; i++) {
            for (int j = i + 1; j < setv.size(); j++) {
                if (!graph.isAdjacentTo(setv.get(i), setv.get(j))) {
                    return false;
                }
            }
        }
        return true;
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

    private static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<Set<Node>>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<Node>();
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


    /**
     * Completes a pattern that was modified by an insertion/deletion operator Based on the algorithm described on
     * Appendix C of (Chickering, 2002).
     */
    private void rebuildPattern(Graph graph) {
        SearchGraphUtils.basicPattern(graph, false);
        addRequiredEdges(graph);
        pdagWithBk(graph, getKnowledge());

        TetradLogger.getInstance().log("rebuiltPatterns", "Rebuilt pattern = " + graph);
    }

    /**
     * Fully direct a graph with background knowledge. I am not sure how to adapt Chickering's suggested algorithm above
     * (dagToPdag) to incorporate background knowledge, so I am also implementing this algorithm based on Meek's 1995
     * UAI paper. Notice it is the same implemented in PcSearch. </p> *IMPORTANT!* *It assumes all colliders are
     * oriented, as well as arrows dictated by time order.*
     */
    private void pdagWithBk(Graph graph, IKnowledge knowledge) {
        MeekRules rules = new MeekRules();
//        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
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

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setBeamWidth(int beamWidth) {
//        if (beamWidth < 1) throw new IllegalArgumentException();
        // Do nothing. We don't care about beam width.
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public Set<GraphWithPValue> getSignificantModels() {
        return significantModels;
    }

    public static class Score {
        private Scorer scorer;
        private double pValue;
        private double fml;
        private double chisq;
        private double bic;
//        private double aic;
        private int dof;

        public Score(Scorer scorer) {
            this.scorer = scorer;
            this.pValue = scorer.getPValue();
            this.fml = scorer.getFml();
            this.chisq = scorer.getChiSquare();
            this.bic = scorer.getBicScore();
//            this.aic = scorer.getAicScore();
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
//            double fml = estimatedSem.getFml();
//            int freeParams = estimatedSem.getNumFreeParams();
//            int sampleSize = estimatedSem.getSampleSize();
//            return -(sampleSize - 1) * fml - (freeParams * Math.log(sampleSize));
//            return -getChisq();

//            if (getMaxEdgeP() > 0.05) {
//                return Double.NEGATIVE_INFINITY;
//            }

//            return -fml;
//            return -chisq;
            return -bic;
//            return -aic;
        }

        public double getFml() {
            return fml;
        }

//        public double getChisq() {
//            return chisq;
//        }

//        public double getMaxEdgeP() {
//            double maxP = Double.NEGATIVE_INFINITY;
//
//            for (Parameter param : estimatedSem.getSemPm().getParameters()) {
//                if (param.getType() != ParamType.COEF) {
//                    continue;
//                }
//                double p = this.estimatedSem.getPValue(param, 10000);
//                if (p > maxP) maxP = p;
//            }
//
//            return maxP;
//        }

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

//    /**
//     * This method straightforwardly applies the standard definition of the numerical estimates of the second order
//     * partial derivatives.  See for example Section 5.7 of Numerical Recipes in C.
//     */
//    public double secondPartialDerivative(FittingFunction f, int i, int j,
//                                          double[] p, double delt) {
//        double[] arg = new double[p.length];
//        System.arraycopy(p, 0, arg, 0, p.length);
//
//        arg[i] += delt;
//        arg[j] += delt;
//        double ff1 = f.evaluate(arg);
//
//        arg[j] -= 2 * delt;
//        double ff2 = f.evaluate(arg);
//
//        arg[i] -= 2 * delt;
//        arg[j] += 2 * delt;
//        double ff3 = f.evaluate(arg);
//
//        arg[j] -= 2 * delt;
//        double ff4 = f.evaluate(arg);
//
//        double fsSum = ff1 - ff2 - ff3 + ff4;
//
//        return fsSum / (4.0 * delt * delt);
//    }

//    /**
//     * Evaluates a fitting function for an array of parameters.
//     *
//     * @author Joseph Ramsey
//     */
//    interface FittingFunction {
//
//        /**
//         * @return the value of the function for the given array of parameter values.
//         */
//        double evaluate(double[] argument);
//
//        /**
//         * @return the number of parameters.
//         */
//        int getNumParameters();
//    }

//    /**
//     * Wraps a Sem for purposes of calculating its fitting function for given parameter values.
//     *
//     * @author Joseph Ramsey
//     */
//    static class SemFittingFunction implements FittingFunction {
//
//        /**
//         * The wrapped Sem.
//         */
//        private final SemIm sem;
//
//        /**
//         * Constructs a new CoefFittingFunction for the given Sem.
//         */
//        public SemFittingFunction(SemIm sem) {
//            this.sem = sem;
//        }
//
//        /**
//         * Computes the maximum likelihood function value for the given parameters values as given by the optimizer.
//         * These values are mapped to parameter values.
//         */
//        public double evaluate(double[] parameters) {
//            sem.setFreeParamValues(parameters);
//
//            // This needs to be FML-- see Bollen p. 109.
//            return sem.getScore();
//        }
//
//        /**
//         * @return the number of arguments. Required by the MultivariateFunction interface.
//         */
//        public int getNumParameters() {
//            return this.sem.getNumFreeParams();
//        }
//    }
}


