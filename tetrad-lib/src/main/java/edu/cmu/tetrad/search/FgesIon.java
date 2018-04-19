package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FgesIon {
    private Graph augmented;
    private List<Edge> reusableEdges;
    private List<List<Edge>> edgesRemoved = new ArrayList<>();

    public FgesIon() {
    }

    public static void printModels(List<Graph> models, List<List<Edge>> edgesRemoved) {
        System.out.println();
        System.out.println("All models (some extra oriention may be required:");
        System.out.println();

        for (int i = 0; i < models.size(); i++) {
            System.out.println((i + 1) + ", Removing" + edgesRemoved.get(i) + " \n" + models.get(i) + "\n");
        }
    }

    public Graph search(DataSet dataSet) {
        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        Fges fges = new Fges(score);
        fges.setVerbose(true);
        final Graph p = fges.search();
        this.augmented = getAugmentedGraph(score, p);
        this.reusableEdges = getRemoveableEdges(this.augmented, p, score);

        return p;
    }

    private Graph getAugmentedGraph(SemBicScore score, Graph p) {
        final List<Node> nodes = p.getNodes();

        final Graph augmented = new EdgeListGraph(p);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                List<List<Node>> paths = GraphUtils.semidirectedPathsFromTo(p, nodes.get(i), nodes.get(j), 5);

                for (List<Node> path : paths) {
                    Node x = path.get(0);
                    Node y = path.get(path.size() - 1);

                    List<Node> intermediaries = new ArrayList<>();

                    for (int k = 1; k < path.size() - 1; k++) {
                        intermediaries.add(path.get(k));
                    }

                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(intermediaries, nodes));

                    if (Double.isNaN(v)) {
                        augmented.addEdge(getAugmentedEdge(path, intermediaries, p));
                    }
                }
            }
        }

        return augmented;
    }

    private Edge getAugmentedEdge(List<Node> path, List<Node> these, Graph P) {
        if (these.size() == 0) return null;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (Node node : these) {
            final int i = path.indexOf(node);
            if (i < min) min = i;
            if (i > max) max = i;
        }

        Node n1 = path.get(min - 1);
        Node n2 = path.get(min);
        Node n3 = path.get(max);
        Node n4 = path.get(max + 1);

        Edge edge1 = P.getEdge(n1, n2);
        Edge edge2 = P.getEdge(n3, n4);

        return new Edge(n1, n4, edge1.getProximalEndpoint(n1), edge2.getProximalEndpoint(n4));
    }

    public List<Graph> allModels(Graph p, Graph augmented, List<Edge> removeableEdges, Score score) {
        List<Graph> models = new ArrayList<>();
        edgesRemoved.clear();

        DepthChoiceGenerator gen = new DepthChoiceGenerator(removeableEdges.size(), removeableEdges.size());
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            List<Edge> toRemove = new ArrayList<>();
            for (int aChoice : choice) toRemove.add(removeableEdges.get(aChoice));

            Graph Q = new EdgeListGraph(augmented);

            for (Edge edge : toRemove) {
                Q.removeEdge(edge);
            }

//            IKnowledge knowledge = new Knowledge2();

//            for (Edge edge : toRemove) {
//                knowledge.setForbidden(edge.getNode1().toString(), edge.getNode2().toString());
//                knowledge.setForbidden(edge.getNode2().toString(), edge.getNode1().toString());
//            }

//            Fges fges = new Fges(score);
//            fges.setKnowledge(knowledge);
//            Q = fges.search();

            List<Node> nodes = augmented.getNodes();

            for (int r = 0; r < nodes.size(); r++) {
                for (int s = r + 1; s < nodes.size(); s++) {
                    Node x = nodes.get(r);
                    Node y = nodes.get(s);

                    boolean yes = true;

                    if (p.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                        if (!Q.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                            yes = false;
                        }
                    }

                    if (p.existsSemiDirectedPathFromTo(y, Collections.singleton(x))) {
                        if (!Q.existsSemiDirectedPathFromTo(y, Collections.singleton(x))) {
                            yes = false;
                        }
                    }

                    if (!yes) continue CHOICE;
                }
            }

            if (!models.contains(Q)) {
                edgesRemoved.add(toRemove);
                models.add(Q);
            }
        }

        return models;
    }

    private List<Edge> getRemoveableEdges(Graph augmented, Graph fgesGraph, Score score) {
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

            Graph p2 = new EdgeListGraph(augmented);
            p2.removeEdge(edge);

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

    public Graph getAugmented() {
        return augmented;
    }

    public List<Edge> getRemoveableEdges() {
        return reusableEdges;
    }

    public List<List<Edge>> getEdgesRemoved() {
        return edgesRemoved;
    }
}
