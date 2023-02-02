package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

import static java.lang.Math.min;
import static java.util.Collections.shuffle;

public class RandomGraph {
    public static Graph randomDag(int numNodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomDag(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Dag randomDag(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return new Dag(randomGraph(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected));
    }

    public static Graph randomGraph(int numNodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraph(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {

        // It is still unclear whether we should use the random forward edges method or the
        // random uniform method to create random DAGs, hence this method.
        // jdramsey 12/8/2015
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
//        return randomGraphUniform(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphUniform(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        int numNodes = nodes.size();

        if (numNodes == 0) {
            throw new IllegalArgumentException("NumNodes most be > 0: " + numNodes);
        }

        if (maxNumEdges < 0 || maxNumEdges > numNodes * (numNodes - 1)) {
            throw new IllegalArgumentException("NumEdges must be " + "at least 0 and at most (#nodes)(#nodes - 1) / 2: " + maxNumEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > numNodes) {
            throw new IllegalArgumentException("Max # latent confounders must be " + "at least 0 and at most the number of nodes: " + numLatentConfounders);
        }

        for (Node node : nodes) {
            node.setNodeType(NodeType.MEASURED);
        }

        UniformGraphGenerator generator;

        if (connected) {
            generator = new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        } else {
            generator = new UniformGraphGenerator(UniformGraphGenerator.ANY_DAG);
        }

        generator.setNumNodes(numNodes);
        generator.setMaxEdges(maxNumEdges);
        generator.setMaxDegree(maxDegree);
        generator.setMaxInDegree(maxIndegree);
        generator.setMaxOutDegree(maxOutdegree);
        generator.generate();
        Graph dag = generator.getDag(nodes);

        // Create a list of nodes. Add the nodes in the list to the
        // dag. Arrange the nodes in a circle.
        fixLatents1(numLatentConfounders, dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    static List<Node> getCommonCauses(Graph dag) {
        List<Node> commonCauses = new ArrayList<>();
        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> children = dag.getChildren(node);

            if (children.size() >= 2) {
                commonCauses.add(node);
            }
        }

        return commonCauses;
    }

    public static Graph randomGraphRandomForwardEdges(int numNodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected, boolean layoutAsCircle) {
        if (nodes.size() == 0) {
            throw new IllegalArgumentException("NumNodes most be > 0");
        }

        // Believe it or not ths is needed.
        long size = nodes.size();

        if (numEdges < 0 || numEdges > size * (size - 1)) {
            throw new IllegalArgumentException("numEdges must be " + "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " + numEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > nodes.size()) {
            throw new IllegalArgumentException("MaxNumLatents must be " + "greater than 0 and less than the number of nodes: " + numLatentConfounders);
        }

        LinkedList<List<Integer>> allEdges = new LinkedList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                List<Integer> pair = new ArrayList<>(2);
                pair.add(i);
                pair.add(j);
                allEdges.add(pair);
            }
        }

        Graph dag;

        int numTriesForGraph = 0;

        do {
            dag = new EdgeListGraph(nodes);

            shuffle(allEdges);

            while (!allEdges.isEmpty() && dag.getNumEdges() < numEdges) {
                List<Integer> e = allEdges.removeFirst();

                Node n1 = nodes.get(e.get(0));
                Node n2 = nodes.get(e.get(1));

                if (dag.getIndegree(n2) >= maxIndegree) {
                    continue;
                }

                if (dag.getOutdegree(n1) >= maxOutdegree) {
                    continue;
                }

                if (dag.getIndegree(n1) + dag.getOutdegree(n1) >= maxDegree) {
                    continue;
                }

                if (dag.getIndegree(n2) + dag.getOutdegree(n2) >= maxDegree) {
                    continue;
                }

                dag.addDirectedEdge(n1, n2);
            }
        } while (++numTriesForGraph < 1000 && connected && (new Paths(dag).connectedComponents().size() != 1));

        fixLatents4(numLatentConfounders, dag);

        if (layoutAsCircle) {
            GraphUtils.circleLayout(dag, 200, 200, 150);
        }

        return dag;
    }

    public static Graph randomScaleFreeGraph(int numNodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomScaleFreeGraph(nodes, numLatentConfounders, alpha, beta, delta_in, delta_out);
    }

    private static Graph randomScaleFreeGraph(List<Node> _nodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {

        if (alpha + beta >= 1) {
            throw new IllegalArgumentException("For the Bollobas et al. algorithm," + "\napha + beta + gamma = 1, so alpha + beta must be < 1.");
        }

        shuffle(_nodes);

        LinkedList<Node> nodes = new LinkedList<>();
        nodes.add(_nodes.get(0));

        Graph G = new EdgeListGraph(_nodes);

        if (alpha <= 0) {
            throw new IllegalArgumentException("alpha must be > 0.");
        }
        if (beta <= 0) {
            throw new IllegalArgumentException("beta must be > 0.");
        }

        double gamma = 1.0 - alpha - beta;

        if (gamma <= 0) {
            throw new IllegalArgumentException("alpha + beta must be < 1.");
        }

        if (delta_in <= 0) {
            throw new IllegalArgumentException("delta_in must be > 0.");
        }
        if (delta_out <= 0) {
            throw new IllegalArgumentException("delta_out must be > 0.");
        }

        Map<Node, Set<Node>> parents = new HashMap<>();
        Map<Node, Set<Node>> children = new HashMap<>();
        parents.put(_nodes.get(0), new HashSet<>());
        children.put(_nodes.get(0), new HashSet<>());

        while (nodes.size() < _nodes.size()) {
            double r = RandomUtil.getInstance().nextDouble();
            int v, w;

            if (r < alpha) {
                v = nodes.size();
                w = chooseNode(indegrees(nodes, parents), delta_in);
                Node m = _nodes.get(v);
                nodes.addFirst(m);
                parents.put(m, new HashSet<>());
                children.put(m, new HashSet<>());
                v = 0;
                w++;
            } else if (r < alpha + beta) {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = chooseNode(indegrees(nodes, parents), delta_in);
                if (!(w > v)) {
                    continue;
                }
            } else {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = nodes.size();
                Node m = _nodes.get(w);
                nodes.addLast(m);
                parents.put(m, new HashSet<>());
                children.put(m, new HashSet<>());
            }

            if (G.isAdjacentTo(nodes.get(v), nodes.get(w))) {
                continue;
            }

            G.addDirectedEdge(nodes.get(v), nodes.get(w));

            parents.get(nodes.get(w)).add(nodes.get(v));
            children.get(nodes.get(v)).add(nodes.get(w));
        }

        fixLatents1(numLatentConfounders, G);

        GraphUtils.circleLayout(G, 200, 200, 150);

        return G;
    }

    private static int chooseNode(int[] distribution, double delta) {
        double cumsum = 0.0;
        double psum = sum(distribution) + delta * distribution.length;
        double r = RandomUtil.getInstance().nextDouble();

        for (int i = 0; i < distribution.length; i++) {
            cumsum += (distribution[i] + delta) / psum;

            if (r < cumsum) {
                return i;
            }
        }

        throw new IllegalArgumentException("Didn't pick a node.");
    }

    private static int sum(int[] distribution) {
        int sum = 0;
        for (int w : distribution) {
            sum += w;
        }
        return sum;
    }

    private static int[] indegrees(List<Node> nodes, Map<Node, Set<Node>> parents) {
        int[] indegrees = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            indegrees[i] = parents.get(nodes.get(i)).size();
        }

        return indegrees;
    }

    private static int[] outdegrees(List<Node> nodes, Map<Node, Set<Node>> children) {
        int[] outdegrees = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            outdegrees[i] = children.get(nodes.get(i)).size();
        }

        return outdegrees;
    }

    public static void fixLatents1(int numLatentConfounders, Graph graph) {
        List<Node> commonCauses = getCommonCauses(graph);
        int index = 0;

        while (index++ < numLatentConfounders) {
            if (commonCauses.size() == 0) {
                break;
            }
            int i = RandomUtil.getInstance().nextInt(commonCauses.size());
            Node node = commonCauses.get(i);
            node.setNodeType(NodeType.LATENT);
            commonCauses.remove(i);
        }
    }

    // JMO's method for fixing latents
    public static void fixLatents4(int numLatentConfounders, Graph graph) {
        if (numLatentConfounders == 0) {
            return;
        }

        List<Node> commonCausesAndEffects = getCommonCausesAndEffects(graph);
        int index = 0;

        while (index++ < numLatentConfounders) {
            if (commonCausesAndEffects.size() == 0) {
                index--;
                break;
            }
            int i = RandomUtil.getInstance().nextInt(commonCausesAndEffects.size());
            Node node = commonCausesAndEffects.get(i);
            node.setNodeType(NodeType.LATENT);
            commonCausesAndEffects.remove(i);
        }

        List<Node> nodes = graph.getNodes();
        while (index++ < numLatentConfounders) {
            int r = RandomUtil.getInstance().nextInt(nodes.size());
            if (nodes.get(r).getNodeType() == NodeType.LATENT) {
                index--;
            } else {
                nodes.get(r).setNodeType(NodeType.LATENT);
            }
        }
    }

    //Helper method for fixLatents4
    //Common effects refers to common effects with at least one child
    private static List<Node> getCommonCausesAndEffects(Graph dag) {
        List<Node> commonCausesAndEffects = new ArrayList<>();
        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> children = dag.getChildren(node);

            if (children.size() >= 2) {
                commonCausesAndEffects.add(node);
            } else {
                List<Node> parents = dag.getParents(node);
                if (parents.size() >= 2 && children.size() >= 1) {
                    commonCausesAndEffects.add(node);
                }
            }
        }

        return commonCausesAndEffects;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5
     * to the graph, then finally adding two cycles.
     */
    public static Graph randomCyclicGraph2(int numNodes, int numEdges, int maxDegree) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);

        LOOP:
        while (graph.getEdges().size() < numEdges /*&& ++count1 < 100*/) {
//            int cycleSize = RandomUtil.getInstance().nextInt(2) + 4;
            int cycleSize = RandomUtil.getInstance().nextInt(3) + 3;

            // Pick that many nodes randomly
            List<Node> cycleNodes = new ArrayList<>();
            int count2 = -1;

            for (int i = 0; i < cycleSize; i++) {
                Node node = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));

                if (cycleNodes.contains(node)) {
                    i--;
                    ++count2;
                    if (count2 < 10) {
                        continue;
                    }
                }

                cycleNodes.add(node);
            }

            for (Node cycleNode : cycleNodes) {
                if (graph.getDegree(cycleNode) >= maxDegree) {
                    continue LOOP;
                }
            }

            Edge edge;

            // Make sure you won't create any two cycles (this will be done later, explicitly)
            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i + 1), cycleNodes.get(i));

                if (graph.containsEdge(edge)) {
                    continue LOOP;
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));

            if (graph.containsEdge(edge)) {
                continue;
            }

            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i), cycleNodes.get(i + 1));

                if (!graph.containsEdge(edge)) {
                    graph.addEdge(edge);

                    if (graph.getNumEdges() == numEdges) {
                        break LOOP;
                    }
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(cycleNodes.size() - 1), cycleNodes.get(0));

            if (!graph.containsEdge(edge)) {
                graph.addEdge(edge);

                if (graph.getNumEdges() == numEdges) {
                    break;
                }
            }
        }

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5
     * to the graph, then finally adding two cycles.
     */
    public static Graph randomCyclicGraph3(int numNodes, int numEdges, int maxDegree, double probCycle, double probTwoCycle) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);

        LOOP:
        while (graph.getEdges().size() < numEdges /*&& ++count1 < 100*/) {
//            int cycleSize = RandomUtil.getInstance().nextInt(2) + 4;
            int cycleSize = RandomUtil.getInstance().nextInt(3) + 3;

            // Pick that many nodes randomly
            List<Node> cycleNodes = new ArrayList<>();
            int count2 = -1;

            for (int i = 0; i < cycleSize; i++) {
                Node node = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));

                if (cycleNodes.contains(node)) {
                    i--;
                    ++count2;
                    if (count2 < 10) {
                        continue;
                    }
                }

                cycleNodes.add(node);
            }

            for (Node cycleNode : cycleNodes) {
                if (graph.getDegree(cycleNode) >= maxDegree) {
                    continue LOOP;
                }
            }

            Edge edge;

            // Make sure you won't create any two cycles (this will be done later, explicitly)
            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i + 1), cycleNodes.get(i));

                if (graph.containsEdge(edge)) {
                    continue LOOP;
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));

            if (graph.containsEdge(edge)) {
                continue;
            }

            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i), cycleNodes.get(i + 1));

                if (!graph.containsEdge(edge)) {
                    graph.addEdge(edge);

                    if (graph.getNumEdges() == numEdges) {
                        break LOOP;
                    }
                }
            }

            if (RandomUtil.getInstance().nextDouble() < probCycle) {
                edge = Edges.directedEdge(cycleNodes.get(cycleNodes.size() - 1), cycleNodes.get(0));
            } else {
                edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));
            }

            if (!graph.containsEdge(edge)) {
                graph.addEdge(edge);

                if (graph.getNumEdges() == numEdges) {
                    break;
                }
            }
        }

        Set<Edge> edges = graph.getEdges();

        for (Edge edge : edges) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            if (RandomUtil.getInstance().nextDouble() < probTwoCycle) {
                graph.removeEdges(a, b);
                graph.addEdge(Edges.directedEdge(a, b));
                graph.addEdge(Edges.directedEdge(b, a));
            }
        }

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    public static void addTwoCycles(Graph graph, int numTwoCycles) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        shuffle(edges);

        for (int i = 0; i < min(numTwoCycles, edges.size()); i++) {
            Edge edge = edges.get(i);
            Edge reversed = Edges.directedEdge(edge.getNode2(), edge.getNode1());

            if (graph.containsEdge(reversed)) {
                i--;
                continue;
            }

            graph.addEdge(reversed);
        }
    }
}
