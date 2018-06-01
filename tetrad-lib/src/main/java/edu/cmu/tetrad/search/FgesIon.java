package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FgesIon {
    private Graph augmented;
    private List<Edge> reusableEdges;
    private List<List<Edge>> edgesRemoved = new ArrayList<>();
    private IKnowledge knowledge = new Knowledge2();

    public FgesIon() {
    }

    public static void printModels(List<Graph> models, List<List<Edge>> edgesRemoved) {
        System.out.println();
        System.out.println("All models (some extra oriention may be required:");
        System.out.println();

        for (int i = 0; i < models.size(); i++) {
            System.out.println((i + 1) + ". Removing" + edgesRemoved.get(i) + " \n" + models.get(i) + "\n");
        }
    }

    public Graph search(DataSet dataSet) {
        final ISemBicScore score = new SemBicScore2(new CovarianceMatrixOnTheFly2(dataSet));
        score.setPenaltyDiscount(2);
        Fges3 fges = new Fges3(score);
//        fges.setVerbose(true);
        fges.setKnowledge(knowledge);
        final Graph graph = fges.search();

//        Pc pc = new Pc(new IndTestScore(score));
//        pc.setKnowledge(knowledge);
//        Graph graph = pc.search();

        this.augmented = getAugmentedGraph(score, graph);
        this.reusableEdges = getRemoveableEdges(this.augmented, score);
        return graph;


    }

//    private Graph getAugmentedGraph(SemBicScore score, Graph p) {
//        final List<Node> nodes = p.getNodes();
//
//        final Graph augmented = new EdgeListGraph(p);
//
//        for (int i = 0; i < nodes.size(); i++) {
//
//            NODES:
//            for (int j = 0; j < nodes.size(); j++) {
//                if (i == j) continue;
//
//                List<List<Node>> paths = GraphUtils.semidirectedPathsFromTo(p, nodes.get(i), nodes.get(j), 5);
//
//                for (List<Node> path : paths) {
//                    Node x = path.get(0);
//                    Node y = path.get(path.size() - 1);
//
//                    List<Node> intermediaries = new ArrayList<>();
//
//                    for (int k = 1; k < path.size() - 1; k++) {
//                        intermediaries.add(path.get(k));
//                    }
//
//                    List<Node> adjx = augmented.getAdjacentNodes(x);
//                    adjx.remove(y);
//
//                    List<Node> adjy = augmented.getAdjacentNodes(y);
//                    adjy.remove(x);
//
//                    if (adjx.size() < 1) continue;
//
//                    ChoiceGenerator gen = new ChoiceGenerator(adjx.size(), 1);
//                    int[] choice;
//
//                    while ((choice = gen.next()) != null) {
//                        List<Node> n = GraphUtils.asList(choice, adjx);
//                        double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(n, nodes));
//
//                        if (!Double.isNaN(v) && v < 0) {
//                            continue NODES;
//                        }
//                    }
//
//                    if (adjy.size() < 1) continue;
//
//                    gen = new ChoiceGenerator(adjy.size(), adjy.size());
//
//                    while ((choice = gen.next()) != null) {
//                        List<Node> n = GraphUtils.asList(choice, adjy);
//                        double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(n, nodes));
//
//                        if (!Double.isNaN(v) && v < 0) {
//                            continue NODES;
//                        }
//                    }
//
//                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(intermediaries, nodes));
//
//                    if (Double.isNaN(v)) {
//                        final Edge augmentedEdge = getAugmentedEdge(path, intermediaries, p);
//                        augmented.addEdge(augmentedEdge);
//                        continue NODES;
//                    }
//                }
//            }
//        }
//
//        return augmented;
//    }

    private Graph getAugmentedGraph(ISemBicScore score, Graph p) {
        final List<Node> nodes = p.getNodes();

        final Graph augmented = new EdgeListGraph(p);

        for (int i = 0; i < nodes.size(); i++) {

            NODES:
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                List<List<Node>> paths = GraphUtils.semidirectedPathsFromTo(p, nodes.get(i), nodes.get(j), 5);

                if (paths.isEmpty()) continue;
                List<Node> path = paths.get(0);

//                System.out.println(GraphUtils.pathString(paths.get(0), p));

                Node x = path.get(0);
                Node y = path.get(path.size() - 1);

                List<Node> intermediaries = new ArrayList<>();

                for (int k = 1; k < path.size() - 1; k++) {
                    intermediaries.add(path.get(k));
                }

                List<Node> adjx = augmented.getAdjacentNodes(x);
                adjx.remove(y);

                List<Node> adjy = augmented.getAdjacentNodes(y);
                adjy.remove(x);

                if (adjx.size() < 1) continue;

                ChoiceGenerator gen = new ChoiceGenerator(adjx.size(), 1);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> n = GraphUtils.asList(choice, adjx);
                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(n, nodes));
//                    System.out.println("v = " + v + " n = " + n);

                    if (!Double.isNaN(v) && v < 0) {
                        augmented.removeEdge(x, y);
                        continue NODES;
                    }
                }

                if (adjy.size() < 1) continue;

                gen = new ChoiceGenerator(adjy.size(), adjy.size());

                while ((choice = gen.next()) != null) {
                    List<Node> n = GraphUtils.asList(choice, adjy);
                    double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(n, nodes));
//                    System.out.println("vv = " + v + " n = " + n);

                    if (!Double.isNaN(v) && v < 0) {
                        augmented.removeEdge(x, y);
                        continue NODES;
                    }
                }

                double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(intermediaries, nodes));

                if (Double.isNaN(v)) {
//                    System.out.println("###");

                    final Edge augmentedEdge = getAugmentedEdge(path, intermediaries, p);

                    if (augmentedEdge != null) {
//                        System.out.println("Adding edge: " + augmentedEdge);
                        augmented.addEdge(augmentedEdge);
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

    public List<Graph> allModels(Graph fgesGraph, Graph augmented, List<Edge> removeableEdges) {
        List<Graph> models = new ArrayList<>();
        edgesRemoved.clear();

        DepthChoiceGenerator gen = new DepthChoiceGenerator(removeableEdges.size(), removeableEdges.size());
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            List<Edge> toRemove = new ArrayList<>();
            for (int aChoice : choice) toRemove.add(removeableEdges.get(aChoice));

            Graph Q = new EdgeListGraph(augmented);
            List<Node> theseNodes = new ArrayList<>();

            for (Edge edge : toRemove) {
                Q.removeEdge(edge);
                theseNodes.add(edge.getNode1());
                theseNodes.add(edge.getNode2());
            }

            final MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            SearchGraphUtils.basicPattern(Q, false);
            meekRules.orientImplied(Q);

            for (Node x : theseNodes) {
                for (Node y : theseNodes) {
                    if (x == y) continue;

                    boolean yes = true;

                    if (fgesGraph.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                        if (!Q.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
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

    public Graph getAugmented() {
        return augmented;
    }

    public List<Edge> getRemoveableEdges() {
        return reusableEdges;
    }

    public List<List<Edge>> getEdgesRemoved() {
        return edgesRemoved;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
