package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly2;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;

import java.util.*;

public class FgesIon {
    public enum PatternAlgorithm {PC, FGES}
    private PatternAlgorithm patternAlgorithm = PatternAlgorithm.FGES;
    private Graph augmented;
    private List<Edge> removableEdges;
    private ISemBicScore score;
    private double penaltyDiscount = 2;

    public FgesIon() {
    }

    public List<Graph> search(DataSet dataSet) {
        final ISemBicScore score = new SemBicScore2(new CovarianceMatrixOnTheFly2(dataSet), dataSet);
        this.score = score;
        score.setPenaltyDiscount(penaltyDiscount);

        final Graph pattern;

        if (patternAlgorithm == PatternAlgorithm.FGES) {
            Fges fges = new Fges(score);
            pattern = fges.search();
        } else if (patternAlgorithm == PatternAlgorithm.PC) {
            Pc pc = new Pc(new IndTestScore(score));
            pattern = pc.search();
        } else {
            throw new IllegalArgumentException("Unrecognized pattern algorithm.");
        }

        System.out.println("Pattern = " + pattern);

        this.augmented = getAugmentedGraph(pattern);

        System.out.println("\nAugemented pattern = " + augmented);

        this.removableEdges = getRemoveableEdges(this.augmented, score);

        return allModels(getAugmented(), getRemoveableEdges());
    }

    public static List<Triple> asList(int[] indices, List<Triple> triple) {
        List<Triple> list = new LinkedList<>();

        for (int i : indices) {
            list.add(triple.get(i));
        }

        return list;
    }

    public Graph getAugmented() {
        return augmented;
    }

    private List<Edge> getRemoveableEdges() {
        return removableEdges;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setPatternAlgorithm(PatternAlgorithm patternAlgorithm) {
        this.patternAlgorithm = patternAlgorithm;
    }

    //====================================PRIVATE METHODS=================================//

    private boolean impliedFactsCheckOut(Graph model, Score score) {
        if (model == null) throw new NullPointerException();

        final List<Node> variables = score.getVariables();
        model = GraphUtils.replaceNodes(model, variables);

        if (model == null) throw new NullPointerException("Null models");

        final List<Node> nodes = model.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                List<Node> adjx = model.getAdjacentNodes(x);
                adjx.remove(y);

                DepthChoiceGenerator gen2 = new DepthChoiceGenerator(adjx.size(), adjx.size());
                int[] choice2;

                while ((choice2 = gen2.next()) != null) {
                    List<Node> t = GraphUtils.asList(choice2, adjx);

                    int indx = variables.indexOf(x);
                    int indy = variables.indexOf(y);

                    int[] indz = new int[t.size()];
                    for (int g = 0; g < t.size(); g++) indz[g] = variables.indexOf(t.get(g));

                    final double s = score.localScoreDiff(indx, indy, indz);

                    if (Double.isNaN(s)) continue;

                    final boolean dSeparated = !GraphUtils.isDConnectedTo(x, y, t, model);

                    if (!((dSeparated && s < 0) || (!dSeparated && s > 0))) {
                        return false;
                    }
                }

                List<Node> adjy = model.getAdjacentNodes(y);
                adjy.remove(x);

                gen2 = new DepthChoiceGenerator(adjy.size(), adjy.size());

                while ((choice2 = gen2.next()) != null) {
                    List<Node> t = GraphUtils.asList(choice2, adjy);

                    int indx = variables.indexOf(x);
                    int indy = variables.indexOf(y);

                    int[] indz = new int[t.size()];
                    for (int g = 0; g < t.size(); g++) indz[g] = variables.indexOf(t.get(g));

                    final double s = score.localScoreDiff(indx, indy, indz);

                    if (Double.isNaN(s)) continue;

                    final boolean dSeparated = !GraphUtils.isDConnectedTo(x, y, t, model);

                    if (!Double.isNaN(s) && !((dSeparated && s < 0) || (!dSeparated && s > 0))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private Graph getAugmentedGraph(Graph p) {
        final List<Node> nodes = p.getNodes();

        final Graph augmented = new EdgeListGraph(p);

        for (int i = 0; i < nodes.size(); i++) {

            NODES:
            for (int j = i + 1; j < nodes.size(); j++) {
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                List<Node> adjx = augmented.getAdjacentNodes(x);
                adjx.remove(y);

                List<Node> adjy = augmented.getAdjacentNodes(y);
                adjy.remove(x);

                DepthChoiceGenerator gen = new DepthChoiceGenerator(adjx.size(), adjx.size());
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> _adjx = GraphUtils.asList(choice, adjx);
                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(_adjx, nodes));

                    if (!Double.isNaN(v) && v < 0) {
                        augmented.removeEdge(x, y);
                        continue NODES;
                    }
                }

                gen = new DepthChoiceGenerator(adjy.size(), adjy.size());

                while ((choice = gen.next()) != null) {
                    List<Node> _adjy = GraphUtils.asList(choice, adjy);
                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(_adjy, nodes));

                    if (!Double.isNaN(v) && v < 0) {
                        augmented.removeEdge(x, y);
                        continue NODES;
                    }
                }

                List<List<Node>> paths = GraphUtils.semidirectedTreks(p, x, y, 5);

                if (paths.isEmpty()) continue;
                List<Node> path = paths.get(0);

                List<Node> intermediaries = new ArrayList<>();

                for (int k = 1; k < path.size() - 1; k++) {
                    intermediaries.add(path.get(k));
                }

                double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(intermediaries, nodes));

                if (Double.isNaN(v)) {
                    final Edge augmentedEdge = getAugmentedEdge(path, intermediaries);

                    if (augmentedEdge != null) {
                        if (!augmented.isAdjacentTo(augmentedEdge.getNode1(), augmentedEdge.getNode2())) {
                            augmented.addEdge(augmentedEdge);
                        }
                    }
                }
            }
        }

        return augmented;
    }

    private Edge getAugmentedEdge(List<Node> path, List<Node> these) {
        if (these.size() == 0) return null;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (Node node : these) {
            final int i = path.indexOf(node);
            if (i < min) min = i;
            if (i > max) max = i;
        }

        Node n1 = path.get(min - 1);
        Node n4 = path.get(max + 1);

        return Edges.undirectedEdge(n1, n4);
    }

    private List<Graph> allModels(Graph augmented, List<Edge> removeableEdges) {
        List<Graph> models = new ArrayList<>();

        DepthChoiceGenerator gen = new DepthChoiceGenerator(removeableEdges.size(), removeableEdges.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            models.addAll(listModels(augmented, removeableEdges, choice));
        }

        return models;
    }

    private List<Graph> listModels(Graph augmented, List<Edge> removeableEdges, int[] choice) {
        List<Graph> models = new ArrayList<>();

        List<Edge> toRemove = new ArrayList<>();
        for (int aChoice : choice) toRemove.add(removeableEdges.get(aChoice));

        Graph Q = new EdgeListGraph(augmented);

        for (Edge edge : toRemove) {
            Q.removeEdge(edge);
        }

        SearchGraphUtils.basicPattern(Q, false);

        final List<Node> nodes = Q.getNodes();

        List<Triple> unshieldedNoncolliders = new ArrayList<>();

        for (Node y : nodes) {
            List<Node> adj = new ArrayList<>();

            for (Node z : Q.getAdjacentNodes(y)) {
                if (!Q.getEdge(y, z).pointsTowards(z)) {
                    adj.add(z);
                }
            }

            if (adj.size() < 2) continue;
            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice3;

            while ((choice3 = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice3, adj);
                if (Q.isAdjacentTo(_adj.get(0), _adj.get(1))) continue;
                unshieldedNoncolliders.add(new Triple(_adj.get(0), y, _adj.get(1)));
            }
        }

        DepthChoiceGenerator gen4 = new DepthChoiceGenerator(unshieldedNoncolliders.size(), unshieldedNoncolliders.size());
        int[] choice4;

        while ((choice4 = gen4.next()) != null) {
            List<Triple> tripleList = asList(choice4, unshieldedNoncolliders);

            Graph Q2 = new EdgeListGraph(Q);

            for (Triple triple : tripleList) {
                if (Q2.isAdjacentTo(triple.getX(), triple.getZ())) continue;

                Q2.removeEdge(triple.getX(), triple.getY());
                Q2.removeEdge(triple.getZ(), triple.getY());
                Q2.addDirectedEdge(triple.getX(), triple.getY());
                Q2.addDirectedEdge(triple.getZ(), triple.getY());
            }

            SearchGraphUtils.basicPattern(Q2, false);
            new MeekRules().orientImplied(Q2);

            boolean ok = impliedFactsCheckOut(Q2, score);

            if (ok && !models.contains(Q2)) {
                models.add(Q2);
            }
        }

        return models;
    }

    private List<Edge> getRemoveableEdges(Graph augmented, Score score) {
        List<Node> nodes = score.getVariables();
        List<Edge> removeableEdges = new ArrayList<>();

        for (Edge edge : augmented.getEdges()) {

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> commonChildren = augmented.getChildren(x);
            commonChildren.retainAll(augmented.getChildren(y));

            List<Node> adjx = augmented.getAdjacentNodes(x);
            adjx.removeAll(commonChildren);
            adjx.remove(y);

            List<Node> adjy = augmented.getAdjacentNodes(y);
            adjy.removeAll(commonChildren);
            adjy.remove(x);

            double vx = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(adjx, nodes));
            double vy = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(adjy, nodes));

            if ((Double.isNaN(vx) || Double.isNaN(vy))) {
                removeableEdges.add(edge);
            }
        }

        return removeableEdges;
    }

    private int[] varIndices(List<Node> z, List<Node> variables) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = variables.indexOf(z.get(i));
        }

        return indices;
    }
}
