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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import nu.xom.*;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Basic graph utilities.
 *
 * @author Joseph Ramsey
 */
public final class GraphUtils {

    /**
     * Arranges the nodes in the graph in a circle.
     *
     * @param centerx
     * @param centery
     * @param radius  The radius of the circle in pixels; a good default is
     *                150.
     */
    public static void circleLayout(Graph graph, int centerx, int centery,
                                    int radius) {
        List<Node> nodes = graph.getNodes();

        Collections.sort(nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        double rad = 6.28 / nodes.size();
        double phi = .75 * 6.28;    // start from 12 o'clock.

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            int centerX = centerx + (int) (radius * Math.cos(phi));
            int centerY = centery + (int) (radius * Math.sin(phi));

            node.setCenterX(centerX);
            node.setCenterY(centerY);

            phi += rad;
        }
    }

    public static void arrangeByGraphTiers(Graph graph) {
        List<List<Node>> tiers = getTiers(graph);

        int y = 0;

        for (List<Node> tier1 : tiers) {
            y += 50;
            int x = 0;

            for (Object aTier : tier1) {
                x += 90;
                Node node = (Node) aTier;
                node.setCenterX(x);
                node.setCenterY(y);
            }
        }
    }

    public static void hierarchicalLayout(Graph graph) {
        LayeredDrawing layout = new LayeredDrawing(graph);
        layout.doLayout();
    }

    public static void kamadaKawaiLayout(Graph graph,
                                         boolean randomlyInitialized, double naturalEdgeLength,
                                         double springConstant, double stopEnergy) {
        KamadaKawaiLayout layout = new KamadaKawaiLayout(graph);
        layout.setRandomlyInitialized(randomlyInitialized);
        layout.setNaturalEdgeLength(naturalEdgeLength);
        layout.setSpringConstant(springConstant);
        layout.setStopEnergy(stopEnergy);
        layout.doLayout();
    }

    public static void fruchtermanReingoldLayout(Graph graph) {
        FruchtermanReingoldLayout layout = new FruchtermanReingoldLayout(graph);
        layout.doLayout();
    }

    /**
     * Finds the set of nodes which have no children, followed by the set of
     * their parents, then the set of the parents' parents, and so on.  The
     * result is returned as a List of Lists.
     *
     * @return the tiers of this digraph.
     */
    public static List<List<Node>> getTiers(Graph graph) {
        Set<Node> found = new HashSet<Node>();
        Set<Node> notFound = new HashSet<Node>();
        List<List<Node>> tiers = new LinkedList<List<Node>>();

        // first copy all the nodes into 'notFound'.
        notFound.addAll(graph.getNodes());

        // repeatedly run through the nodes left in 'notFound'.  If any node
        // has all of its parents already in 'found', then add it to the
        // getModel tier.
        int notFoundSize = 0;
        boolean jumpstart = false;

        while (!notFound.isEmpty()) {
            List<Node> thisTier = new LinkedList<Node>();

            for (Object aNotFound : notFound) {
                Node node = (Node) aNotFound;
                List<Node> adj = graph.getAdjacentNodes(node);
                List<Node> parents = new LinkedList<Node>();

                for (Object anAdj : adj) {
                    Node _node = (Node) anAdj;
                    Edge edge = graph.getEdge(node, _node);

                    //                    if (Edges.isDirectedEdge(edge) &&
                    //                            Edges.getDirectedEdgeHead(edge) == node) {
                    //                        parents.add(_node);
                    //                    }

                    if (edge.getProximalEndpoint(node) == Endpoint.ARROW &&
                            edge.getDistalEndpoint(node) == Endpoint.TAIL) {
                        parents.add(_node);
                    }
                }

                if (found.containsAll(parents)) {
                    thisTier.add(node);
                } else if (jumpstart) {
                    for (Object parent : parents) {
                        Node _node = (Node) parent;
                        if (!found.contains(_node)) {
                            thisTier.add(_node);
                        }
                    }

                    if (!found.contains(node)) {
                        thisTier.add(node);
                    }

                    jumpstart = false;
                }
            }

            // shift all the nodes in this tier from 'notFound' to 'found'.
            notFound.removeAll(thisTier);
            found.addAll(thisTier);
            if (notFoundSize == notFound.size()) {
                jumpstart = true;
            }

            notFoundSize = notFound.size();

            // add the getModel tier to the list of tiers.
            if (!thisTier.isEmpty()) {
                tiers.add(thisTier);
            }
        }

        return tiers;
    }


    /**
     * Arranges the nodes in the graph in a circle, organizing by cluster
     */
    public static void arrangeClustersInCircle(Graph graph) {
        List<Node> latents = new LinkedList<Node>();
        List<List<Node>> partition = new LinkedList<List<Node>>();
        int totalSize = getMeasurementModel(graph, latents, partition);
        boolean gaps[] = new boolean[totalSize];
        List<Node> nodes = new LinkedList<Node>();
        int count = 0;
        for (int i = latents.size() - 1; i >= 0; i--) {
            nodes.add(latents.get(i));
            gaps[count++] = (i == 0);
        }

        for (Object aPartition : partition) {
            List<Node> cluster = (List<Node>) aPartition;
            for (int i = 0; i < cluster.size(); i++) {
                nodes.add(cluster.get(i));
                gaps[count++] = (i == cluster.size() - 1);
            }
        }

        double rad = 6.28 / (nodes.size() + partition.size() + 1);
        double phi = .75 * 6.28;    // start from 12 o'clock.

        for (int i = 0; i < nodes.size(); i++) {
            Node n1 = nodes.get(i);
            int centerX = 200 + (int) (150 * Math.cos(phi));
            int centerY = 200 + (int) (150 * Math.sin(phi));

            n1.setCenterX(centerX);
            n1.setCenterY(centerY);

            if (gaps[i]) {
                phi += 2 * rad;
            } else {
                phi += rad;
            }
        }
    }

    /**
     * Arranges the nodes in the graph in a line, organizing by cluster
     */
    private static final int NODE_GAP = 50;

    public static void arrangeClustersInLine(Graph graph, boolean jitter) {
        List<Node> latents = new LinkedList<Node>();
        List<List<Node>> partition = new LinkedList<List<Node>>();
        getMeasurementModel(graph, latents, partition);
        List<Node> nodes = new LinkedList<Node>();
        double clusterWidth[] = new double[partition.size()];
        double indicatorWidth[][] = new double[partition.size()][];
        double latentWidth[] = new double[partition.size()];

        for (int i = 0; i < latents.size(); i++) {
            nodes.add(latents.get(i));
            latentWidth[i] = 60;
        }
        for (int k = 0; k < partition.size(); k++) {
            List<Node> cluster = partition.get(k);
            clusterWidth[k] = 0.;
            indicatorWidth[k] = new double[cluster.size()];
            for (int i = 0; i < cluster.size(); i++) {
                nodes.add(cluster.get(i));
                indicatorWidth[k][i] = 60;
                clusterWidth[k] += 60;
            }
            clusterWidth[k] += (cluster.size() - 1.) * NODE_GAP;
        }

        int currentPos = NODE_GAP;
        for (int k = 0; k < partition.size(); k++) {
            Node nl = latents.get(k);
            nl.setCenterX(currentPos + (int) (clusterWidth[k] / 2.));
            int noise = 0;
            if (jitter) {
                noise = RandomUtil.getInstance().nextInt(50) - 25;
            }
            nl.setCenterY(100 + noise);
            List<Node> cluster = partition.get(k);
            for (int i = 0; i < cluster.size(); i++) {
                Node ni = cluster.get(i);
                int centerX = currentPos + (int) (indicatorWidth[k][i] / 2.);
                ni.setCenterX(centerX);
                ni.setCenterY(200);
                currentPos += indicatorWidth[k][i] + NODE_GAP;
            }
            currentPos += 2. * NODE_GAP;
        }
    }

    /**
     * Decompose a latent variable graph into its measurement model
     */
    public static int getMeasurementModel(Graph graph, List<Node> latents,
                                          List<List<Node>> partition) {
        int totalSize = 0;

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                Collection<Node> children = graph.getChildren(node);
                List<Node> newCluster = new LinkedList<Node>();

                for (Object aChildren : children) {
                    Node child = (Node) aChildren;
                    if (child.getNodeType() == NodeType.MEASURED) {
                        newCluster.add(child);
                    }
                }

                latents.add(node);
                partition.add(newCluster);
                totalSize += 1 + newCluster.size();
            }
        }
        return totalSize;
    }

    public static Dag randomDag(List<Node> nodes, int numLatentConfounders,
                                int maxNumEdges, int maxDegree,
                                int maxIndegree, int maxOutdegree,
                                boolean connected) {
        return new Dag(randomGraph(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree,
                connected));
    }

    public static Graph randomGraph(List<Node> nodes, int numLatentConfounders,
                                    int maxNumEdges, int maxDegree,
                                    int maxIndegree, int maxOutdegree,
                                    boolean connected) {
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
//        return randomGraphUniform(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphUniform(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        int numNodes = nodes.size();

        if (numNodes <= 0) {
            throw new IllegalArgumentException(
                    "NumNodes most be > 0: " + numNodes);
        }

        if (maxNumEdges < 0 || maxNumEdges > numNodes * (numNodes - 1)) {
            throw new IllegalArgumentException("NumEdges must be " +
                    "at least 0 and at most (#nodes)(#nodes - 1) / 2: " +
                    maxNumEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > numNodes) {
            throw new IllegalArgumentException("Max # latent confounders must be " +
                    "at least 0 and at most the number of nodes: " +
                    numLatentConfounders);
        }

        for (Node node : nodes) {
            node.setNodeType(NodeType.MEASURED);
        }

        UniformGraphGenerator generator;

        if (connected) {
            generator = new UniformGraphGenerator(
                    UniformGraphGenerator.CONNECTED_DAG);
        } else {
            generator =
                    new UniformGraphGenerator(UniformGraphGenerator.ANY_DAG);
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

        return dag;
    }

    private static List<Node> getCommonCauses(Graph dag) {
        List<Node> commonCauses = new ArrayList<>();
        List<Node> nodes = dag.getNodes();

        NODES:
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            List<Node> children = dag.getChildren(node);

            if (children.size() >= 2) {
                commonCauses.add(node);
            }
        }

        return commonCauses;
    }

    public static Graph randomDagQuick(List<Node> nodes, int numLatentConfounders, int numEdges) {
        if (nodes.size() <= 0) {
            throw new IllegalArgumentException(
                    "NumNodes most be > 0: " + nodes.size());
        }

        // Believe it or not this is needed.
        long size = (long) nodes.size();

        if (numEdges < 0 || numEdges > size * (size - 1)) {
            throw new IllegalArgumentException("NumEdges must be " +
                    "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " +
                    numEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > nodes.size()) {
            throw new IllegalArgumentException("MaxNumLatents must be " +
                    "greater than 0 and less than the number of nodes: " +
                    numLatentConfounders);
        }

        Graph dag = new EdgeListGraph(nodes);

        List<Node> nodes2 = new ArrayList<Node>(nodes);
        Collections.shuffle(nodes2);

        for (int i = 0; i < numEdges; i++) {
            int c1 = RandomUtil.getInstance().nextInt(nodes2.size());
            int c2 = RandomUtil.getInstance().nextInt(nodes2.size());

            if (c1 < c2) {
                Node n1 = nodes2.get(c1);
                Node n2 = nodes2.get(c2);

                if (!dag.isAdjacentTo(n1, n2)) {
                    dag.addDirectedEdge(n1, n2);
                } else {
                    i--;
                }
            } else {
                i--;
            }
        }

        fixLatents1(numLatentConfounders, dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges) {
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, numEdges, 30, 15, 15, false);
    }


    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders,
                                                      int numEdges, int maxDegree,
                                                      int maxIndegree, int maxOutdegree, boolean connected) {
        if (nodes.size() <= 0) {
            throw new IllegalArgumentException(
                    "NumNodes most be > 0: " + nodes.size());
        }

        // Believe it or not this is needed.
        long size = (long) nodes.size();

        if (numEdges < 0 || numEdges > size * (size - 1)) {
            throw new IllegalArgumentException("NumEdges must be " +
                    "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " +
                    numEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > nodes.size()) {
            throw new IllegalArgumentException("MaxNumLatents must be " +
                    "greater than 0 and less than the number of nodes: " +
                    numLatentConfounders);
        }

        final Graph dag = new EdgeListGraphSingleConnections(nodes);

        final List<Node> nodes2 = dag.getNodes(); // new ArrayList<Node>(nodes);
//        Collections.shuffle(nodes2);

        for (int i = 0; i < numEdges; i++) {
            int c1 = RandomUtil.getInstance().nextInt(nodes2.size());
            int c2 = RandomUtil.getInstance().nextInt(nodes2.size());

            if (c1 < c2) {
                Node n1 = nodes2.get(c1);
                Node n2 = nodes2.get(c2);

//                if (dag.getAdjacentNodes(n1).size() > 5) continue;
//                if (dag.getAdjacentNodes(n2).size() > 5) continue;

                if (!dag.isAdjacentTo(n1, n2)) {
                    final int indegree = dag.getIndegree(n2);
                    final int outdegree = dag.getOutdegree(n1);

                    if (indegree >= maxIndegree) {
                        continue;
                    }

                    if (outdegree >= maxOutdegree) {
                        continue;
                    }

                    if (indegree + outdegree > maxDegree) {
                        continue;
                    }

                    if (connected && indegree == 0 && outdegree == 0) {
                        continue;
                    }

                    dag.addDirectedEdge(n1, n2);
                } else {
                    i--;
                }
            } else {
                i--;
            }
        }
//
        fixLatents4(numLatentConfounders, dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    //JMO's method that calls fixLatents4
    public static Graph randomGraphRandomForwardEdges1(List<Node> nodes, int numLatentConfounders, int numEdges) {
        return randomGraphRandomForwardEdges1(nodes, numLatentConfounders, numEdges, 30, 15, 15, false);
    }


    public static Graph randomGraphRandomForwardEdges1(List<Node> nodes, int numLatentConfounders,
                                                       int numEdges, int maxDegree,
                                                       int maxIndegree, int maxOutdegree, boolean connected) {
        if (nodes.size() <= 0) {
            throw new IllegalArgumentException(
                    "NumNodes most be > 0: " + nodes.size());
        }

        // Believe it or not this is needed.
        long size = (long) nodes.size();

        if (numEdges < 0 || numEdges > size * (size - 1)) {
            throw new IllegalArgumentException("NumEdges must be " +
                    "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " +
                    numEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > nodes.size()) {
            throw new IllegalArgumentException("MaxNumLatents must be " +
                    "greater than 0 and less than the number of nodes: " +
                    numLatentConfounders);
        }

        final Graph dag = new EdgeListGraphSingleConnections(nodes);

        final List<Node> nodes2 = dag.getNodes(); // new ArrayList<Node>(nodes);
//        Collections.shuffle(nodes2);

        for (int i = 0; i < numEdges; i++) {
            int c1 = RandomUtil.getInstance().nextInt(nodes2.size());
            int c2 = RandomUtil.getInstance().nextInt(nodes2.size());

            if (c1 < c2) {
                Node n1 = nodes2.get(c1);
                Node n2 = nodes2.get(c2);

//                if (dag.getAdjacentNodes(n1).size() > 5) continue;
//                if (dag.getAdjacentNodes(n2).size() > 5) continue;

                if (!dag.isAdjacentTo(n1, n2)) {
                    final int indegree = dag.getIndegree(n2);
                    final int outdegree = dag.getOutdegree(n1);

                    if (indegree >= maxIndegree) {
                        continue;
                    }

                    if (outdegree >= maxOutdegree) {
                        continue;
                    }

                    if (indegree + outdegree > maxDegree) {
                        continue;
                    }

                    if (connected && indegree == 0 && outdegree == 0) {
                        continue;
                    }

                    dag.addDirectedEdge(n1, n2);
                } else {
                    i--;
                }
            } else {
                i--;
            }
        }
//
        fixLatents4(numLatentConfounders, dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    public static Graph scaleFreeGraph(int numNodes, int numLatentConfounders,
                                       double alpha, double beta,
                                       double delta_in, double delta_out) {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return scaleFreeGraph(nodes, numLatentConfounders, alpha, beta, delta_in, delta_out);
    }


    public static Graph scaleFreeGraph(List<Node> _nodes, int numLatentConfounders,
                                       double alpha, double beta,
                                       double delta_in, double delta_out) {

        System.out.println("# nodes = " + _nodes.size() + " latents = " + numLatentConfounders +
                "  alpha = " + alpha + " beta = " + beta + " delta_in = " + delta_in + " delta_out = " + delta_out);

//        >>> print inspect.getsource(nx.scale_free_graph)
//        def scale_free_graph(n,
//                alpha=0.41,
//                beta=0.54,
//                gamma=0.05,
//                delta_in=0.2,
//                delta_out=0,
//                create_using=None,
//                seed=None):
//        """Return a scale free directed graph.
//
//        Parameters
//                ----------
//        n : integer
//        Number of nodes in graph
//        alpha : float
//        Probability for adding a new node connected to an existing node
//        chosen randomly according to the in-degree distribution.
//                beta : float
//        Probability for adding an edge between two existing nodes.
//                One existing node is chosen randomly according the in-degree
//        distribution and the other chosen randomly according to the out-degree
//        distribution.
//                gamma : float
//        Probability for adding a new node conecgted to an existing node
//        chosen randomly according to the out-degree distribution.
//                delta_in : float
//        Bias for choosing ndoes from in-degree distribution.
//                delta_out : float
//        Bias for choosing ndoes from out-degree distribution.
//                create_using : graph, optional (default MultiDiGraph)
//        Use this graph instance to start the process (default=3-cycle).
//        seed : integer, optional
//        Seed for random number generator
//
//                Examples
//        --------
//                >>> G=nx.scale_free_graph(100)
//
//        Notes
//                -----
//                The sum of alpha, beta, and gamma must be 1.
//
//        References
//                ----------
//        .. [1] B. Bollob{\'a}s, C. Borgs, J. Chayes, and O. Riordan,
//            Directed scale-free graphs,
//                    Proceedings of the fourteenth annual ACM-SIAM symposium on
//            Discrete algorithms, 132--139, 2003.
//            """
//
//            def _choose_node(G,distribution,delta):
//            cumsum=0.0
//            # normalization
//            psum=float(sum(distribution.values()))+float(delta)*len(distribution)
//            r=random.random()
//            for i in range(0,len(distribution)):
//            cumsum+=(distribution[i]+delta)/psum
//            if r < cumsum:
//            break
//            return i
//
//            if create_using is None:
//            # start with 3-cycle
//            G = nx.MultiDiGraph()
//            G.add_edges_from([(0,1),(1,2),(2,0)])
//            else:
//            # keep existing graph structure?
//            G = create_using
//            if not (G.is_directed() and G.is_multigraph()):
//            raise nx.NetworkXError(\
//                    "MultiDiGraph required in create_using")
//
//            if alpha <= 0:
//            raise ValueError('alpha must be >= 0.')
//            if beta <= 0:
//            raise ValueError('beta must be >= 0.')
//            if gamma <= 0:
//            raise ValueError('beta must be >= 0.')
//
//            if alpha+beta+gamma !=1.0:
//            raise ValueError('alpha+beta+gamma must equal 1.')
//
//            G.name="directed_scale_free_graph(%s,alpha=%s,beta=%s,gamma=%s,delta_in=%s,delta_out=%s)"%(n,alpha,beta,gamma,delta_in,delta_out)
//
//            # seed random number generated (uses None as default)
//                random.seed(seed)
//
//                while len(G)<n:
//                r = random.random()
//                # random choice in alpha,beta,gamma ranges
//                if r<alpha:
//                # alpha
//                # add new node v
//                        v = len(G)
//                # choose w according to in-degree and delta_in
//                w = _choose_node(G, G.in_degree(),delta_in)
//                elif r < alpha+beta:
//                # beta
//                # choose v according to out-degree and delta_out
//                v = _choose_node(G, G.out_degree(),delta_out)
//                # choose w according to in-degree and delta_in
//                w = _choose_node(G, G.in_degree(),delta_in)
//                else:
//                # gamma
//                # choose v according to out-degree and delta_out
//                v = _choose_node(G, G.out_degree(),delta_out)
//                # add new node w
//                        w = len(G)
//                G.add_edge(v,w)
//
//                return G
        Collections.shuffle(_nodes);

        LinkedList<Node> nodes = new LinkedList<>();
        nodes.add(_nodes.get(0));

        Graph G = new EdgeListGraphSingleConnections(_nodes);

        if (alpha <= 0) throw new IllegalArgumentException("alpha must be > 0.");
        if (beta <= 0) throw new IllegalArgumentException("beta must be > 0.");

        double gamma = 1.0 - alpha - beta;

        if (gamma <= 0) throw new IllegalArgumentException("alpha + beta must be < 1.");

        if (delta_in <= 0) throw new IllegalArgumentException("delta_in must be >= 0.");
        if (delta_out <= 0) throw new IllegalArgumentException("delta_out must be >= 0.");

        Map<Node, Set<Node>> parents = new HashMap<>();
        Map<Node, Set<Node>> children = new HashMap<>();
        parents.put(_nodes.get(0), new HashSet<Node>());
        children.put(_nodes.get(0), new HashSet<Node>());

        while (nodes.size() < _nodes.size()) {
            double r = RandomUtil.getInstance().nextDouble();
            int v, w;

            if (r < alpha) {
                v = nodes.size();
                w = chooseNode(indegrees(nodes, parents), delta_in);
                Node m = _nodes.get(v);
                nodes.addFirst(m);
                parents.put(m, new HashSet<Node>());
                children.put(m, new HashSet<Node>());
                v = 0;
                w++;
            } else if (r < alpha + beta) {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = chooseNode(indegrees(nodes, parents), delta_in);
                if (!(w > v)) continue;
            } else {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = nodes.size();
                Node m = _nodes.get(w);
                nodes.addLast(m);
                parents.put(m, new HashSet<Node>());
                children.put(m, new HashSet<Node>());
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

    /**
     * Implements the method in Melancon and Dutour, "Random Generation of
     * Directed Graphs," with optional biases added.
     */
    public static Graph randomGraph(int numNodes, int numLatentConfounders,
                                    int maxNumEdges, int maxDegree,
                                    int maxIndegree, int maxOutdegree,
                                    boolean connected) {
        if (numNodes <= 0) {
            throw new IllegalArgumentException(
                    "NumNodes most be > 0: " + numNodes);
        }

        if (maxNumEdges < 0 || maxNumEdges > numNodes * (numNodes - 1)) {
            throw new IllegalArgumentException("NumEdges must be " +
                    "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " +
                    maxNumEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > numNodes) {
            throw new IllegalArgumentException("MaxNumLatents must be " +
                    "greater than 0 and less than the number of nodes: " +
                    numLatentConfounders);
        }

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numNodes + numLatentConfounders; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomGraph(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraph(int numNodes, int numEdges, boolean connected) {
        return GraphUtils.randomGraph(numNodes, 0, numEdges, 30, 15, 15, connected);
    }

    public static void fixLatents1(int numLatentConfounders, Graph graph) {
        List<Node> commonCauses = getCommonCauses(graph);
        int index = 0;

        while (index++ < numLatentConfounders) {
            if (commonCauses.size() == 0) break;
            int i = RandomUtil.getInstance().nextInt(commonCauses.size());
            Node node = commonCauses.get(i);
            node.setNodeType(NodeType.LATENT);
            commonCauses.remove(i);
        }
    }

    public static void fixLatents2(int numLatentConfounders, Graph graph) {
        List<Node> commonCauses = getCommonCauses(graph);
        Collections.shuffle(commonCauses);

        List<Node> nodes = graph.getNodes();

        for (int i = 0; i < numLatentConfounders; i++) {
            int r = RandomUtil.getInstance().nextInt(nodes.size());
            nodes.get(r).setNodeType(NodeType.LATENT);
        }
    }

    public static void fixLatents3(int numLatentConfounders, Graph graph) {
        List<Node> latents = new ArrayList<Node>();
        List<Node> measures = graph.getNodes();

        for (int i = 0; i < numLatentConfounders; i++) {
            Node node = new GraphNode("L" + (i + 1));
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
            graph.addNode(node);
        }

        for (int i = 0; i < numLatentConfounders; i++) {
            Node n1 = measures.get(RandomUtil.getInstance().nextInt(measures.size()));
            Node n2 = measures.get(RandomUtil.getInstance().nextInt(measures.size()));
            if (n1 == n2) {
                i--;
                continue;
            }

            List<Node> parents1 = graph.getParents(n1);
            parents1.removeAll(latents);
            if (parents1.isEmpty()) continue;

            List<Node> parents2 = graph.getParents(n2);
            parents2.removeAll(latents);
            if (parents2.isEmpty()) continue;

            graph.addDirectedEdge(latents.get(i), n1);
            graph.addDirectedEdge(latents.get(i), n2);
        }
    }

    // JMO's method for fixing latents
    public static void fixLatents4(int numLatentConfounders, Graph graph) {
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
                continue;
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

        NODES:
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

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
     * This method builds on the randomDag methods by implementing a procedure
     * for adding cycles to a graph.
     *
     * @param dag            A Dag returned from any of the randomDag methods
     * @param maxNumCycles   Algorithm will add at most this many cyclic edges to the graph
     * @param minCycleLength The smallest number of edges allowed for creating cycles
     * @return
     */

    public static Graph addCycles(Graph dag, int maxNumCycles, int minCycleLength) {

        if (maxNumCycles <= 0) {
            throw new IllegalArgumentException(
                    "maxNumCycles most be > 0: " + maxNumCycles);
        }

        if (minCycleLength <= 0) {
            throw new IllegalArgumentException(
                    "minCycleLength most be > 0: " + minCycleLength);
        }

        // convert dag to EgdeListGraph
        Set<Edge> edges = dag.getEdges();
        EdgeListGraph graph = new EdgeListGraph(dag.getNodes());
        for (Edge e : edges) {
            graph.addEdge(e);
        }

        int cycles = maxNumCycles; // make up to this many cycles

        //get nodes in list
        List<Node> nodes = graph.getNodes();

        //go through list and get all possible cycles
        List<NodePair> cycleEdges = new ArrayList<NodePair>();
        for (Node i : nodes) {
            List<Node> c = findPotentialCycle(i, graph, -minCycleLength + 1);
            for (Node j : c) {
                NodePair p = new NodePair(i, j);
                if (!cycleEdges.contains(p))
                    cycleEdges.add(p);
            }
        }

        // with all edge possibilities, we pick from random and add to dag
        if (cycles > cycleEdges.size()) cycles = cycleEdges.size();
        for (int i = cycles; i > 0; i--) {
            int r = RandomUtil.getInstance().nextInt(i);
            NodePair p = cycleEdges.get(r);
            graph.addDirectedEdge(
                    graph.getNode(p.getFirst().getName()),
                    graph.getNode(p.getSecond().getName()));
            cycleEdges.remove(r);
        }

        return graph;
    }

    public static Graph addCycles2(Dag dag, int minNumCycles, int minlength, int maxLength) {
        if (minlength < 2) {
            throw new IllegalArgumentException("Cycle length must be at least 2.");
        }

        if (dag == null) throw new NullPointerException();

        Graph graph = new EdgeListGraph(dag);
        List<Node> nodes = graph.getNodes();

        Map<Edge, List<List<Node>>> edgePaths = new HashMap<Edge, List<List<Node>>>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);

                List<List<Node>> _directedPaths = GraphUtils.directedPathsFromTo(graph, node1, node2, maxLength);

                if (!_directedPaths.isEmpty()) {
                    Edge edge = Edges.directedEdge(node2, node1);
                    edgePaths.put(edge, _directedPaths);
                }
            }
        }

        for (Edge edge : new HashSet<Edge>(edgePaths.keySet())) {
            List<List<Node>> paths = edgePaths.get(edge);

            for (List<Node> path : new ArrayList<List<Node>>(paths)) {
                if (path.size() < minlength) {
                    edgePaths.remove(edge);
                    break;
                }
            }
        }

        int _numCycles = 0;
        int numTrials = -1;

        List<Edge> cyclicEdges = new ArrayList<Edge>(edgePaths.keySet());

        while (_numCycles < minNumCycles && ++numTrials < 4 * minNumCycles) {
            if (cyclicEdges.isEmpty()) {
                return graph;
            }

            int r = RandomUtil.getInstance().nextInt(cyclicEdges.size());
            Edge edge = cyclicEdges.get(r);

            if (graph.getAdjacentNodes(edge.getNode1()).size() > 4) {
                continue;
            }

            if (graph.getAdjacentNodes(edge.getNode2()).size() > 4) {
                continue;
            }

            cyclicEdges.remove(edge);
            graph.addEdge(edge);
            removeIdenticalPaths(edgePaths, edge, cyclicEdges);
            _numCycles += edgePaths.get(edge).size();

            System.out.println("Adding " + edgePaths.get(edge).size() + " cycles: " + edge);
        }

        graph = new EdgeListGraph(graph.getNodes());

        // kludge
//        graph = cyclicGraph3(dag.getNumNodes(), dag.getNumEdges(), 0);

        return graph;
    }

    public static Graph cyclicGraph3(int numNodes, int numEdges, int numTwoCycles) {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);

        for (int r = 0; r < numEdges; r++) {
            int i = RandomUtil.getInstance().nextInt(numNodes);
            int j = RandomUtil.getInstance().nextInt(numNodes);

            if (i == j) {
                r--;
                continue;
            }

            if (graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                r--;
                continue;
            }

            Edge edge = Edges.directedEdge(nodes.get(i), nodes.get(j));
            graph.addEdge(edge);
        }

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());

        for (int s = 0; s < numTwoCycles; s++) {
            Edge edge = edges.get(RandomUtil.getInstance().nextInt(edges.size()));
            Edge reversed = Edges.directedEdge(edge.getNode2(), edge.getNode1());

            if (graph.containsEdge(reversed)) {
                s--;
                continue;
            }

            graph.addEdge(reversed);
        }

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5 to the graph, then finally
     * adding two cycles.
     */
    public static Graph cyclicGraph4(int numNodes, int numEdges) {


        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);
        int count1 = -1;

        LOOP:
        while (graph.getEdges().size() < numEdges /*&& ++count1 < 100*/) {
//            int cycleSize = RandomUtil.getInstance().nextInt(2) + 4;
            int cycleSize = RandomUtil.getInstance().nextInt(3) + 3;

            // Pick that many nodes randomly
            List<Node> cycleNodes = new ArrayList<Node>();
            int count2 = -1;

            for (int i = 0; i < cycleSize; i++) {
                Node node = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));

                if (cycleNodes.contains(node)) {
                    i--;
                    ++count2;
                    if (count2 < 10) continue;
                }

                cycleNodes.add(node);
            }

            for (int i = 0; i < cycleSize; i++) {
                Node node = cycleNodes.get(i);

                if (graph.getAdjacentNodes(node).size() > 3) {
                    continue LOOP;
                }
            }

            Edge edge;

            // Make sure you won't created any two cycles (this will be done later, explicitly)
            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i + 1), cycleNodes.get(i));

                if (graph.containsEdge(edge)) {
                    continue LOOP;
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));

            if (graph.containsEdge(edge)) {
                continue LOOP;
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
                    break LOOP;
                }
            }
        }

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    public static void addTwoCycles(Graph graph, int numTwoCycles) {
        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        Collections.shuffle(edges);

        for (int i = 0; i < Math.min(numTwoCycles, edges.size()); i++) {
            Edge edge = edges.get(i);
            Edge reversed = Edges.directedEdge(edge.getNode2(), edge.getNode1());

            if (graph.containsEdge(reversed)) {
                i--;
                continue;
            }

            graph.addEdge(reversed);
        }
    }

    private static void removeIdenticalPaths(Map<Edge, List<List<Node>>> edgePaths, Edge edge,
                                             List<Edge> cyclicEdges) {
        for (Edge _edge : cyclicEdges) {
            for (List<Node> path : edgePaths.get(edge)) {
                for (List<Node> _path : new ArrayList<List<Node>>(edgePaths.get(_edge))) {
                    if (samePath(path, _path)) {
                        edgePaths.get(_edge).remove(_path);
                    }

                    if (edgePaths.get(_edge).isEmpty()) {
                        edgePaths.remove(_edge);
                    }
                }
            }
        }
    }

    /**
     * Assumes the nodes on path1 are unique and the nodes on path2 are unique.
     */
    private static boolean samePath(List<Node> path1, List<Node> path2) {
        if (path1.isEmpty() && path2.isEmpty()) {
            return true;
        }

        if (path1.size() != path2.size()) {
            return false;
        }

        int firstIndex = path2.indexOf(path1.get(0));

        if (firstIndex == -1) {
            return false;
        }

        for (int i = 0; i < path1.size(); i++) {
            int i2 = (i + firstIndex) % path2.size();
            Node node1 = path1.get(i);
            Node node2 = path2.get(i2);

            if (!(node1 == node2)) {
                return false;
            }
        }

        return true;
    }

    public static Graph addCycles3(Dag dag, int minNumCycles, int minlength, int maxLength) {
        if (minlength < 2) {
            throw new IllegalArgumentException("Cycle length must be at least 2.");
        }

        Graph graph = new EdgeListGraph(dag);
        List<Node> nodes = graph.getNodes();
        Map<Edge, List<List<Node>>> edgePaths = new HashMap<Edge, List<List<Node>>>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);

                if (!graph.isAdjacentTo(node1, node2)) {
                    continue;
                }

                List<List<Node>> _directedPaths = GraphUtils.directedPathsFromTo(graph, node1, node2, maxLength);

                if (!_directedPaths.isEmpty()) {
                    Edge edge = graph.getEdge(node1, node2);
                    edgePaths.put(edge, _directedPaths);
                }
            }
        }

//        for (Edge edge : new HashSet<Edge>(edgePaths.keySet())) {
//            List<List<Node>> paths = edgePaths.get(edge);
//
//            for (List<Node> path : new ArrayList<List<Node>>(paths)) {
//                if (path.size() < minlength) {
//                    edgePaths.remove(edge);
//                    System.out.println("Num edges = " + edgePaths.keySet().size());
//                    break;
//                }
//            }
//        }

        int _numCycles = 0;
        int trials = 0;

        List<Edge> cyclicEdges = new ArrayList<Edge>(edgePaths.keySet());

        while (_numCycles < minNumCycles && trials < minNumCycles) {
            if (cyclicEdges.isEmpty()) {
                return null;
            }

            int r = RandomUtil.getInstance().nextInt(cyclicEdges.size());
            Edge edge = cyclicEdges.get(r);
            cyclicEdges.remove(edge);

            for (List<Node> path : edgePaths.get(edge)) {
                for (int i = 0; i < path.size() - 2; i++) {
                    cyclicEdges.remove(graph.getEdge(path.get(i), path.get(i + 1)));
                }
            }

            graph.removeEdge(edge.getNode1(), edge.getNode2());
            graph.addEdge(Edges.directedEdge(edge.getNode2(), edge.getNode1()));
            _numCycles += edgePaths.get(edge).size();
        }

        return graph;
    }

    public static List<Node> findPotentialCycle(Node node, Graph dag, Integer depth) {

        List<Node> candidate = new ArrayList<Node>();
        List<Node> parent = dag.getParents(node);

        for (Node i : parent) {
            List<Node> c = findPotentialCycle(i, dag, depth + 1);
            for (Node n : c)
                candidate.add(n);
        }

        if (depth > 0 && parent.size() == 0) candidate.add(node);

        return candidate;

    }

    private static double multiplier(double bias, int numNodes) {
        if (bias > 0.0) {
            return numNodes * bias + 1.0;
        } else {
            return bias + 1.0;
        }
    }

    private static int getIndex(double[] weights) {
        double sum = 0.0;

        for (double weight : weights) {
            sum += weight;
        }

        double random = RandomUtil.getInstance().nextDouble() * sum;
        double partialSum = 0.0;

        for (int j = 0; j < weights.length; j++) {
            partialSum += weights[j];

            if (partialSum > random) {
                return j;
            }
        }

        throw new IllegalStateException();
    }

    /**
     * Arranges the nodes in the result graph according to their positions in
     * the source graph.
     *
     * @param resultGraph
     * @param sourceGraph
     * @return true if all of the nodes were arranged, false if not.
     */
    public static boolean arrangeBySourceGraph(Graph resultGraph,
                                               Graph sourceGraph) {
        if (resultGraph == null) {
            throw new IllegalArgumentException("Graph must not be null.");
        }

        if (sourceGraph == null) {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
            return true;
        }

        boolean arrangedAll = true;

        // There is a source graph. Position the nodes in the
        // result graph correspondingly.
        for (Object o : resultGraph.getNodes()) {
            Node node = (Node) o;
            String name = node.getName();
            Node sourceNode = sourceGraph.getNode(name);

            if (sourceNode == null) {
                arrangedAll = false;
                continue;
            }

            node.setCenterX(sourceNode.getCenterX());
            node.setCenterY(sourceNode.getCenterY());
        }

        return arrangedAll;
    }

    public static void arrangeByLayout(Graph graph, HashMap<String, PointXy> layout) {
        for (Node node : graph.getNodes()) {
            PointXy point = layout.get(node.getName());
            node.setCenter(point.getX(), point.getY());
        }
    }

    /**
     * @return the node associated with a given error node. This should be the
     * only child of the error node, E --> N.
     */
    public static Node getAssociatedNode(Node errorNode, Graph graph) {
        if (errorNode.getNodeType() != NodeType.ERROR) {
            throw new IllegalArgumentException(
                    "Can only get an associated node " + "for an error node: " +
                            errorNode);
        }

        List<Node> children = graph.getChildren(errorNode);

        if (children.size() != 1) {
            System.out.println("children of " + errorNode + " = " + children);
            System.out.println(graph);

            throw new IllegalArgumentException(
                    "An error node should have only " +
                            "one child, which is its associated node: " +
                            errorNode);
        }

        return children.get(0);
    }

    /**
     * @return true if <code>set</code> is a clique in <code>graph</code>. </p>
     * R. Silva, June 2004
     */

    public static boolean isClique(Set<Node> set, Graph graph) {
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
     * Calculates the Markov blanket of a target in a DAG. This includes the
     * target, the parents of the target, the children of the target, the
     * parents of the children of the target, edges from parents to target,
     * target to children, parents of children to children, and parent to
     * parents of children. (Edges among children are implied by the inclusion
     * of edges from parents of children to children.) Edges among parents and
     * among parents of children not explicitly included above are not included.
     * (Joseph Ramsey 8/6/04)
     *
     * @param target a node in the given DAG.
     * @param dag    the DAG with respect to which a Markov blanket DAG is to to
     *               be calculated. All of the nodes and edges of the Markov
     *               Blanket DAG are in this DAG.
     */
    public static Dag markovBlanketDag(Node target, Graph dag) {
        if (dag.getNode(target.getName()) == null) {
            throw new NullPointerException("Target node not in graph: " + target);
        }

        Graph blanket = new EdgeListGraph();
        blanket.addNode(target);

        // Add parents of target.
        List<Node> parents = dag.getParents(target);
        for (Object parent1 : parents) {
            Node parent = (Node) parent1;
            blanket.addNode(parent);

            blanket.addDirectedEdge(parent, target);
        }

        // Add children of target and parents of children of target.
        List<Node> children = dag.getChildren(target);
        List<Node> parentsOfChildren = new LinkedList<Node>();
        for (Object aChildren : children) {
            Node child = (Node) aChildren;

            if (!blanket.containsNode(child)) {
                blanket.addNode(child);
            }

            blanket.addDirectedEdge(target, child);

            List<Node> parentsOfChild = dag.getParents(child);
            parentsOfChild.remove(target);
            for (Object aParentsOfChild : parentsOfChild) {
                Node parentOfChild = (Node) aParentsOfChild;

                if (!parentsOfChildren.contains(parentOfChild)) {
                    parentsOfChildren.add(parentOfChild);
                }

                if (!blanket.containsNode(parentOfChild)) {
                    blanket.addNode(parentOfChild);
                }

                blanket.addDirectedEdge(parentOfChild, child);
            }
        }

        // Add in edges connecting parents and parents of children.
        parentsOfChildren.removeAll(parents);

        for (Object parent2 : parents) {
            Node parent = (Node) parent2;

            for (Object aParentsOfChildren : parentsOfChildren) {
                Node parentOfChild = (Node) aParentsOfChildren;
                Edge edge1 = dag.getEdge(parent, parentOfChild);
                Edge edge2 = blanket.getEdge(parent, parentOfChild);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(parent, parentOfChild,
                            edge1.getProximalEndpoint(parent),
                            edge1.getProximalEndpoint(parentOfChild));

                    blanket.addEdge(newEdge);
                }
            }
        }

        // Add in edges connecting children and parents of children.
        for (Object aChildren1 : children) {
            Node child = (Node) aChildren1;

            for (Object aParentsOfChildren : parentsOfChildren) {
                Node parentOfChild = (Node) aParentsOfChildren;
                Edge edge1 = dag.getEdge(child, parentOfChild);
                Edge edge2 = blanket.getEdge(child, parentOfChild);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(child, parentOfChild,
                            edge1.getProximalEndpoint(child),
                            edge1.getProximalEndpoint(parentOfChild));

                    blanket.addEdge(newEdge);
                }
            }
        }

        return new Dag(blanket);
    }

    /**
     * @return the connected components of the given graph, as a list of lists
     * of nodes.
     */
    public static List<List<Node>> connectedComponents(Graph graph) {
        List<List<Node>> components = new LinkedList<List<Node>>();
        List<Node> unsortedNodes = new ArrayList<Node>(graph.getNodes());

        while (!unsortedNodes.isEmpty()) {
            Node seed = unsortedNodes.get(0);
            Set<Node> component = new HashSet<Node>();
            collectComponentVisit(seed, component, graph, unsortedNodes);
            components.add(new ArrayList<Node>(component));
        }

        return components;
    }


    /**
     * Assumes node should be in component.
     */
    private static void collectComponentVisit(Node node, Set<Node> component,
                                              Graph graph, List<Node> unsortedNodes) {
        component.add(node);
        unsortedNodes.remove(node);
        List<Node> adj = graph.getAdjacentNodes(node);

        for (Object anAdj : adj) {
            Node _node = (Node) anAdj;

            if (!component.contains(_node)) {
                collectComponentVisit(_node, component, graph, unsortedNodes);
            }
        }
    }

    /**
     * @param graph The graph in which a directed cycle is sought.
     * @return the first directed cycle encountered in <code>graph</code>.
     */
    public static List<Node> directedCycle(Graph graph) {
        for (Node node : graph.getNodes()) {
            List<Node> path = directedPathFromTo(graph, node, node);

            if (path != null) {
                return path;
            }
        }

        return null;
    }

    /**
     * @param graph The graph in which a directed path is sought.
     * @param node1 The 'from' node.
     * @param node2 The 'to'node.
     * @return A path from <code>node1</code> to <code>node2</code>, or null
     * if there is no path.
     */
    public static List<Node> directedPathFromTo(Graph graph, Node node1, Node node2) {
        return directedPathVisit(graph, node1, node2, new LinkedList<Node>());
    }

    /**
     * @return the path of the first directed path found from node1 to node2,
     * if any.
     */
    private static List<Node> directedPathVisit(Graph graph, Node node1, Node node2,
                                                LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return path;
            }

            if (path.contains(child)) {
                continue;
            }

            if (directedPathVisit(graph, child, node2, path) != null) {
                return path;
            }
        }

        path.removeLast();
        return null;
    }

    //all adjancencies are directed <=> there is no uncertainty about who the parents of 'node' are.
    public static boolean allAdjacenciesAreDirected(Node node, Graph graph) {
        List<Edge> nodeEdges = graph.getEdges(node);
        for (Edge edge : nodeEdges) {
            if (!edge.isDirected())
                return false;
        }
        return true;
    }

    public static Graph removeBidirectedOrientations(Graph estPattern) {
        estPattern = new EdgeListGraph(estPattern);

        // Make bidirected edges undirected.
        for (Edge edge : estPattern.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                estPattern.removeEdge(edge);
                estPattern.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return estPattern;
    }

    public static Graph removeBidirectedEdges(Graph estPattern) {
        estPattern = new EdgeListGraph(estPattern);

        // Remove bidirected edges altogether.
        for (Edge edge : new ArrayList<Edge>(estPattern.getEdges())) {
            if (Edges.isBidirectedEdge(edge)) {
                estPattern.removeEdge(edge);
            }
        }

        return estPattern;
    }

    public static Graph undirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return graph2;
    }

    public static Graph undirectedMoralizedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        // copy skeleton from graph 1
        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        // for every unshielded collider in graph 1, connect the parents
        // with an undirected edge
        LinkedList<Triple> colliders = listColliderTriples(graph);
        for (Triple triple : colliders) {
            Node X = triple.getX();
            Node Z = triple.getZ();

            if (!graph2.isAdjacentTo(X, Z)) {
                graph2.addUndirectedEdge(X, Z);
            }
        }

        return graph2;
    }

    public static Graph nondirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Edge nondirected = Edges.nondirectedEdge(edge.getNode1(), edge.getNode2());

            if (!graph2.containsEdge(nondirected)) {
                graph2.addEdge(nondirected);
            }
        }

        return graph2;
    }

    public static Graph completeGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        graph2.removeEdges(new ArrayList<Edge>(graph2.getEdges()));

        List<Node> nodes = graph2.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);
                graph2.addUndirectedEdge(node1, node2);
            }
        }

        return graph2;
    }

    public static List<List<Node>> directedPathsFromTo(Graph graph, Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        directedPathsFromToVisit(graph, node1, node2, new LinkedList<Node>(), paths, maxLength);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    public static void directedPathsFromToVisit(Graph graph, Node node1, Node node2,
                                                LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) return;

        int witnessed = 0;

        for (Node node : path) {
            if (node == node1) {
                witnessed++;
            }
        }

        if (witnessed > 1) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            directedPathsFromToVisit(graph, child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> semidirectedPathsFromTo(Graph graph, Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        semidirectedPathsFromToVisit(graph, node1, node2, new LinkedList<Node>(), paths, maxLength);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    public static void semidirectedPathsFromToVisit(Graph graph, Node node1, Node node2,
                                                    LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        int witnessed = 0;

        for (Node node : path) {
            if (node == node1) {
                witnessed++;
            }
        }

        if (witnessed > 1) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            semidirectedPathsFromToVisit(graph, child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> allPathsFromTo(Graph graph, Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        allPathsFromToVisit(graph, node1, node2, new LinkedList<Node>(), paths, maxLength);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    public static void allPathsFromToVisit(Graph graph, Node node1, Node node2,
                                           LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        path.addLast(node1);

        if (path.size() > (maxLength == -1 ? 1000 : maxLength)) {
            return;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            if (child == node2) {
                List<Node> _path = new ArrayList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            allPathsFromToVisit(graph, child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> allDirectedPathsFromTo(Graph graph, Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        allDirectedPathsFromToVisit(graph, node1, node2, new LinkedList<Node>(), paths, maxLength);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    public static void allDirectedPathsFromToVisit(Graph graph, Node node1, Node node2,
                                                   LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        path.addLast(node1);

        if (path.size() > (maxLength == -1 ? 1000 : maxLength)) {
            return;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            if (child == node2) {
                List<Node> _path = new ArrayList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            allDirectedPathsFromToVisit(graph, child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> allPathsFromToExcluding(Graph graph, Node node1, Node node2, List<Node> excludes, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        allPathsFromToExcludingVisit(graph, node1, node2, new LinkedList<Node>(), paths, excludes, maxLength);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    public static void allPathsFromToExcludingVisit(Graph graph, Node node1, Node node2,
                                                    LinkedList<Node> path, List<List<Node>> paths, List<Node> excludes, int maxLength) {
        if (excludes.contains(node1)) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            allPathsFromToVisit(graph, child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> treks(Graph graph, Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        treks(graph, node1, node2, new LinkedList<Node>(), paths, maxLength);
        return paths;
    }

    private static void treks(Graph graph, Node node1, Node node2,
                              LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (path.size() > (maxLength == -1 ? 1000 : maxLength - 2)) {
            return;
        }

        if (path.contains(node1)) {
            return;
        }

        if (node1 == node2) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge.
            if (!edge.isDirected()) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            // Found a path.
            if (next == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treks(graph, next, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public static List<List<Node>> treksIncludingBidirected(SemGraph graph, Node node1, Node node2) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        treksIncludingBidirected(graph, node1, node2, new LinkedList<Node>(), paths);
        return paths;
    }

    private static void treksIncludingBidirected(SemGraph graph, Node node1, Node node2,
                                                 LinkedList<Node> path, List<List<Node>> paths) {
        if (!graph.isShowErrorTerms()) {
            throw new IllegalArgumentException("The SEM Graph must be showing its error terms; this method " +
                    "doesn't traverse two edges between the same nodes well.");
        }

        if (path.contains(node1)) {
            return;
        }

        if (node1 == node2) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge or a bidirected edge.
            if (!(edge.isDirected() || Edges.isBidirectedEdge(edge))) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            // Found a path.
            if (next == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treksIncludingBidirected(graph, next, node2, path, paths);
        }

        path.removeLast();
    }

    public static List<List<Node>> dConnectingPaths(Graph graph, Node node1, Node node2,
                                                    List<Node> conditioningNodes) {

        List<List<Node>> paths = new LinkedList<List<Node>>();

        Set<Node> conditioningNodesClosure = new HashSet<Node>();

        for (Object conditioningNode : conditioningNodes) {
            doParentClosureVisit(graph, (Node) (conditioningNode),
                    conditioningNodesClosure);
        }

        // Calls the recursive method to discover a d-connecting path from node1
        // to node2, if one exists.  If such a path is found, true is returned;
        // otherwise, false is returned.
        Endpoint incomingEndpoint = null;
        isDConnectedToVisit(graph, node1, incomingEndpoint, node2, new LinkedList<Node>(), paths,
                conditioningNodes, conditioningNodesClosure);

        return paths;
    }

    private static void doParentClosureVisit(Graph graph, Node node, Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (Edge edge1 : graph.getEdges(node)) {
                Node sub = Edges.traverseReverseDirected(node, edge1);

                if (sub == null) {
                    continue;
                }

                doParentClosureVisit(graph, sub, closure);
            }
        }
    }

    private static void isDConnectedToVisit(Graph graph, Node currentNode,
                                            Endpoint inEdgeEndpoint, Node node2, LinkedList<Node> path, List<List<Node>> paths,
                                            List<Node> conditioningNodes, Set<Node> conditioningNodesClosure) {
//        System.out.println("Visiting " + currentNode);

        if (currentNode == node2) {
            LinkedList<Node> _path = new LinkedList<Node>(path);
            _path.add(currentNode);
            paths.add(_path);
            return;
        }

//        if (path.size() >= 2) {
//            return;
//        }

//        if (currentNode == node2) {
//            return true;
//        }

        if (path.contains(currentNode)) {
            return;
        }

        path.addLast(currentNode);

        for (Edge edge1 : graph.getEdges(currentNode)) {
            Endpoint outEdgeEndpoint = edge1.getProximalEndpoint(currentNode);

            // Apply the definition of d-connection to determine whether
            // we can pass through on a path from this incoming edge to
            // this outgoing edge through this node.  it all depends
            // on whether this path through the node is a collider or
            // not--that is, whether the incoming endpoint and the outgoing
            // endpoint are both arrow endpoints.
            boolean isCollider = (inEdgeEndpoint == Endpoint.ARROW) &&
                    (outEdgeEndpoint == Endpoint.ARROW);
            boolean passAsCollider = isCollider &&
                    conditioningNodesClosure.contains(currentNode);
            boolean passAsNonCollider =
                    !isCollider && !conditioningNodes.contains(currentNode);

            if (passAsCollider || passAsNonCollider) {
                Node nextNode = Edges.traverse(currentNode, edge1);
                //if (nextNode != null) {
                Endpoint previousEndpoint = edge1.getProximalEndpoint(nextNode);
                isDConnectedToVisit(graph, nextNode, previousEndpoint, node2,
                        path, paths, conditioningNodes, conditioningNodesClosure);
            }
        }

        path.removeLast();
    }

    /**
     * @param graph1 An arbitrary graph.
     * @param graph2 Another arbitrary graph with the same number of nodes
     *               and node names.
     * @return Ibid.
     */
    public static List<Edge> edgesComplement(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<Edge>();

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            Edge edge2 = graph2.getEdge(node21, node22);

            if (edge2 == null || !edge1.equals(edge2)) {
                edges.add(edge1);
            }
        }

        return edges;
    }

    public static List<Edge> edgesComplementDirected(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<Edge>();

        for (Edge edge1 : graph1.getEdges()) {
            if (!edge1.isDirected()) continue;

            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            Edge edge2 = graph2.getEdge(node21, node22);

            if (edge2 == null || !edge1.equals(edge2)) {
                edges.add(edge1);
            }
        }

        return edges;
    }

    /**
     * @param graph1 An arbitrary graph.
     * @param graph2 Another arbitrary graph with the same number of nodes
     *               and node names.
     * @return Ibid.
     */
    public static List<Edge> edgesComplementUndirected(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<Edge>();

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            Edge edge2 = graph2.getEdge(node21, node22);

            if (edge2 == null) {
                edges.add(edge1);
            }
        }

        return edges;
    }

    /**
     * @return the edges that are in <code>graph1</code> but not in
     * <code>graph2</code>, as a list of undirected edges..
     */
    public static List<Edge> adjacenciesComplement(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<Edge>();

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

//            if (node21 == null) {
//                continue;
////                throw new IllegalArgumentException("There was no node by that name in the reference graph: " + name1);
//            }
//
//            if (node22 == null) {
//                continue;
////                throw new IllegalArgumentException("There was no node by that name in the reference graph: " + name2);
//            }

            if (node21 == null || node22 == null || !graph2.isAdjacentTo(node21, node22)) {
                edges.add(Edges.nondirectedEdge(edge1.getNode1(), edge1.getNode2()));
            }
        }

        return edges;
    }

    public static List<Edge> adjacenciesComplement2(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<Edge>();

        List<Node> graph1Nodes = graph1.getNodes();

        for (int i = 0; i < graph1Nodes.size(); i++) {
            for (int j = i + 1; j < graph1Nodes.size(); j++) {
                Node node11 = graph1Nodes.get(i);
                Node node12 = graph1Nodes.get(j);

                if (!graph1.isAdjacentTo(node11, node12)) continue;

                String name1 = node11.getName();
                String name2 = node12.getName();

                Node node21 = graph2.getNode(name1);
                Node node22 = graph2.getNode(name2);

//            if (node21 == null) {
//                continue;
////                throw new IllegalArgumentException("There was no node by that name in the reference graph: " + name1);
//            }
//
//            if (node22 == null) {
//                continue;
////                throw new IllegalArgumentException("There was no node by that name in the reference graph: " + name2);
//            }

                if (node21 == null || node22 == null || !graph2.isAdjacentTo(node21, node22)) {
                    edges.add(Edges.nondirectedEdge(node11, node12));
                }
            }
        }

        return edges;
    }

    public static int arrowEndpointComplement(Graph graph1, Graph graph2) {
        int count = 0;

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            Edge edge2 = graph2.getEdge(node21, node22);
//
//            if (edge1.getEndpoint1() == Endpoint.ARROW) {
//                if (edge2 == null) {
//                    count++;
//                } else if (edge2.getProximalEndpoint(node21) != Endpoint.ARROW) {
//                    count++;
//                }
//            }
//
//            if (edge1.getEndpoint2() == Endpoint.ARROW) {
//                if (edge2 == null) {
//                    count++;
//                } else if (edge2.getProximalEndpoint(node22) != Endpoint.ARROW) {
//                    count++;
//                }
//            }


            if (edge2 != null) {
                if (edge1.getEndpoint1() == Endpoint.ARROW && edge2.getProximalEndpoint(node21) != Endpoint.ARROW) {
                    count++;
                }

                if (edge1.getEndpoint2() == Endpoint.ARROW && edge2.getProximalEndpoint(node22) != Endpoint.ARROW) {
                    count++;
                }
            } else {
                if (Edges.isBidirectedEdge(edge1)) {
                    count += 2;
                } else if (Edges.isDirectedEdge(edge1)) {
                    count++;
                }
            }
        }


        return count;
    }

    /**
     * @return the number of directed edges in graph 1 whose orientations are different from the
     * corresponding edges in graph2, when the corresponding edges exist.
     */
    public static int numDifferentOrientationsDirected(Graph graph1, Graph graph2) {
        int errors = 0;

//        for (Edge edge : graph1.getEdges()) {
//            if (!Edges.isDirectedEdge(edge)) {
//                continue;
//            }
//
//            Node node2a = graph2.getNode(edge.getNode1().getName());
//            Node node2b = graph2.getNode(edge.getNode2().getName());
//            Edge edge2 = graph2.getEdge(node2a, node2b);
//
//            Edge edge1Translated = new Edge(node2a, node2b, edge.getEndpoint1(), edge.getEndpoint2());
//
//            if (edge2 != null && !edge2.equals(edge1Translated)) {
//                errors++;
//            }
//        }

        for (Edge edge1 : graph1.getEdges()) {
            Node node21 = graph2.getNode(edge1.getNode1().getName());
            Node node22 = graph2.getNode(edge1.getNode2().getName());

            if (edge1.isDirected() && graph2.isDirectedFromTo(node22, node21)) {
                errors++;
            }
        }

        return errors;
    }

    /**
     * @return the total number of edges in graph 1 whose orientations are different from the
     * corresponding edges in graph2, when the corresponding edges exist.
     */
    public static int numDifferentOrientations(Graph graph1, Graph graph2) {
        int errors = 0;

        for (Edge edge : graph1.getEdges()) {
            Node node2a = graph2.getNode(edge.getNode1().getName());
            Node node2b = graph2.getNode(edge.getNode2().getName());
            Edge edge2 = graph2.getEdge(node2a, node2b);

            Edge edge1Translated = new Edge(node2a, node2b, edge.getEndpoint1(), edge.getEndpoint2());

            if (edge2 != null && !edge2.equals(edge1Translated)) {
                errors++;
            }
        }

        return errors;
    }

    /**
     * @return a new graph in which the bidirectred edges of the given
     * graph have been changed to undirected edges.
     */
    public static Graph bidirectedToUndirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    /**
     * @return a new graph in which the undirectred edges of the given
     * graph have been changed to bidirected edges.
     */
    public static Graph undirectedToBidirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addBidirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    public static Graph nondirectedToBidirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isNondirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addBidirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    /**
     * @return a new graph in which the bidirectred edges of the given
     * graph have been changed to bidirected edges.
     */
    public static Graph bidirectedToTwoCycle(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addDirectedEdge(edge.getNode1(), edge.getNode2());
                newGraph.addDirectedEdge(edge.getNode2(), edge.getNode1());
            }
        }

        return newGraph;
    }

    public static String pathString(List<Node> path, Graph graph) {
        return pathString(graph, path, new LinkedList<Node>());
    }

    public static String pathString(Graph graph, List<Node> path, List<Node> conditioningVars) {
        StringBuilder buf = new StringBuilder();

        if (path.size() < 2) return "";

        buf.append(path.get(0).toString());

        if (conditioningVars.contains(path.get(0))) {
            buf.append("(C)");
        }

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);

            Edge edge = graph.getEdge(n0, n1);

            if (edge == null) {
                buf.append("(-)");
            } else {
                Endpoint endpoint0 = edge.getProximalEndpoint(n0);
                Endpoint endpoint1 = edge.getProximalEndpoint(n1);

                if (endpoint0 == Endpoint.ARROW) {
                    buf.append("<");
                } else if (endpoint0 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint0 == Endpoint.CIRCLE) {
                    buf.append("o");
                }

                buf.append("-");

                if (endpoint1 == Endpoint.ARROW) {
                    buf.append(">");
                } else if (endpoint1 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint1 == Endpoint.CIRCLE) {
                    buf.append("o");
                }
            }

            buf.append(n1.toString());

            if (conditioningVars.contains(n1)) {
                buf.append("(C)");
            }
        }
        return buf.toString();
    }

    /**
     * Converts the given graph, <code>originalGraph</code>, to use the new
     * variables (with the same names as the old).
     *
     * @param originalGraph The graph to be converted.
     * @param newVariables  The new variables to use, with the same names as
     *                      the old ones.
     * @return A new, converted, graph.
     */
    public static Graph replaceNodes(Graph originalGraph, List<Node> newVariables) {
        Graph reference = new EdgeListGraph(newVariables);
        Graph convertedGraph = new EdgeListGraph(newVariables);

        for (Edge edge : originalGraph.getEdges()) {
            Node node1 = reference.getNode(edge.getNode1().getName());
            Node node2 = reference.getNode(edge.getNode2().getName());

            if (node1 == null) {
                node1 = edge.getNode1();
                if (!convertedGraph.containsNode(node1)) {
                    convertedGraph.addNode(node1);
                }
            }
            if (node2 == null) {
                node2 = edge.getNode2();
                if (!convertedGraph.containsNode(node2)) {
                    convertedGraph.addNode(node2);
                }
            }

            if (!convertedGraph.containsNode(node1)) {
                convertedGraph.addNode(node1);
            }

            if (!convertedGraph.containsNode(node2)) {
                convertedGraph.addNode(node2);
            }

            if (node1 == null) {
                throw new IllegalArgumentException("Couldn't find a node by the name " + edge.getNode1().getName()
                        + " among the new variables for the converted graph (" + newVariables + ").");
            }

            if (node2 == null) {
                throw new IllegalArgumentException("Couldn't find a node by the name " + edge.getNode2().getName()
                        + " among the new variables for the converted graph (" + newVariables + ").");
            }

            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();
            Edge newEdge = new Edge(node1, node2, endpoint1, endpoint2);
            convertedGraph.addEdge(newEdge);
        }

        return convertedGraph;
    }


    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the new
     * variables (with the same names as the old).
     *
     * @param originalNodes The list of nodes to be converted.
     * @param newNodes      A list of new nodes, containing as a subset nodes with
     *                      the same names as those in <code>originalNodes</code>.
     *                      the old ones.
     * @return The converted list of nodes.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, List<Node> newNodes) {
        List<Node> convertedNodes = new LinkedList<Node>();

        for (Node node : originalNodes) {
            for (Node _node : newNodes) {
                if (node.getName().equals(_node.getName())) {
                    convertedNodes.add(_node);
                    break;
                }
            }
        }

        return convertedNodes;
    }

    public static int countAdjCorrect(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph1 = GraphUtils.undirectedGraph(graph1);
        graph2 = GraphUtils.undirectedGraph(graph2);

        // The number of omission errors.
        int count = 0;

        // Construct parallel lists of nodes where nodes of the same
        // name in graph1 and workbench 2 occur in the same position in
        // the list.
        List<Node> graph1Nodes = graph1.getNodes();
        List<Node> graph2Nodes = graph2.getNodes();

        Comparator<Node> comparator = new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                String name1 = o1.getName();
                String name2 = o2.getName();
                return name1.compareTo(name2);
            }
        };

        Collections.sort(graph1Nodes, comparator);
        Collections.sort(graph2Nodes, comparator);

        Set<Edge> edges1 = graph1.getEdges();

        for (Edge edge : edges1) {
            Node node1 = graph2.getNode(edge.getNode1().getName());
            Node node2 = graph2.getNode(edge.getNode2().getName());

            if (graph2.isAdjacentTo(node1, node2)) {
                ++count;
            }
        }

        return count;
    }


    /**
     * Counts the adjacencies that are in graph1 but not in graph2.
     *
     * @throws IllegalArgumentException if graph1 and graph2 are not namewise
     *                                  isomorphic.
     */
    public static int countAdjErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        graph1 = GraphUtils.undirectedGraph(graph1);
        graph2 = GraphUtils.undirectedGraph(graph2);

        int count = 0;

        Set<Edge> edges1 = graph1.getEdges();

        for (Edge edge : edges1) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                ++count;
            }
        }

        return count;
    }

    /**
     * Counts the arrowpoints that are in graph1 but not in graph2.
     */
    public static int countArrowptErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int count = 0;

        for (Edge edge1 : graph1.getEdges()) {
            Node node1 = edge1.getNode1();
            Node node2 = edge1.getNode2();

            Edge edge2 = graph2.getEdge(node1, node2);

            if (edge1.getEndpoint1() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode1()) != Endpoint.ARROW) {
                    count++;
                }
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode2()) != Endpoint.ARROW) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Number directed edges in both graph1 and graph2 divided by the number of directed edges in graph1.
     */
    public static double orientationPrecision(Graph graph1, Graph graph2) {
        Graph _graph2 = replaceNodes(graph1, graph2.getNodes());

        if (!new HashSet<Node>(graph2.getNodes()).equals(new HashSet<Node>(graph1.getNodes()))) {
            throw new IllegalArgumentException("Variables in the two graphs must be the same.");
        }

        int intersection = 0;
        int numGraph2 = 0;

        for (Edge edge : graph2.getEdges()) {
            edge = new Edge(edge);
            if (Edges.isPartiallyOrientedEdge(edge)) edge.setEndpoint1(Endpoint.TAIL);

            if (!edge.isDirected()) continue;

//            Edge oppositeEdge = new Edge(edge.getNode1(), edge.getNode2(), edge.getEndpoint2(), edge.getEndpoint1());
//
//            Ignore cycles.
//            if (graph1.containsEdge(oppositeEdge)) {
//                continue;
//            }

            numGraph2++;

            if (_graph2.containsEdge(edge)) {
                intersection++;
            }
        }

        return intersection / (double) numGraph2;
    }

    public static double adjacencyPrecision(Graph graph1, Graph graph2) {
        List<Node> nodes = graph1.getNodes();
        Graph _graph2 = replaceNodes(graph2, nodes);

        if (!new HashSet(nodes).equals(new HashSet<Node>(graph2.getNodes()))) {
            throw new IllegalArgumentException("Variables in the two graphs must be the same.");
        }

        int intersection = 0;
        int numGraph1 = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);

                if (graph1.isAdjacentTo(node1, node2)) {
                    numGraph1++;

                    if (_graph2.isAdjacentTo(node1, node2)) {
                        intersection++;
                    }
                }
            }
        }

//        for (Edge edge : graph1.getEdges()) {
//            numGraph1++;
//
//            if (_graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
//                intersection++;
//            }
//        }

        return intersection / (double) numGraph1;
    }

    public static int getNumArrowpts(Graph graph) {
        Set<Edge> edges = graph.getEdges();
        int numArrowpts = 0;

        for (Edge edge : edges) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                numArrowpts++;
            }
            if (edge.getEndpoint2() == Endpoint.ARROW) {
                numArrowpts++;
            }
        }

//        System.out.println("Num arrowpoints = " + numArrowpts);

        return numArrowpts;
    }

    public static int getNumCorrectArrowpts(Graph correct, Graph estimated) {
        correct = replaceNodes(correct, estimated.getNodes());

        Set<Edge> edges = estimated.getEdges();
        int numCorrect = 0;

        for (Edge edge1 : edges) {
            Edge edge2 = correct.getEdge(edge1.getNode1(), edge1.getNode2());
            if (edge2 == null) continue;

            if (edge1.getEndpoint1() == Endpoint.ARROW && edge2.getProximalEndpoint(edge1.getNode1()) == Endpoint.ARROW) {
                numCorrect++;
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW && edge2.getProximalEndpoint(edge1.getNode2()) == Endpoint.ARROW) {
                numCorrect++;
            }
        }

        return numCorrect;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the
     * replacement nodes for them by the same name in the given <code>graph</code>.
     *
     * @param originalNodes The list of nodes to be converted.
     * @param graph         A graph to be used as a source of new nodes.
     * @return A new, converted, graph.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, Graph graph) {
        List<Node> convertedNodes = new LinkedList<Node>();

        for (Node node : originalNodes) {
            convertedNodes.add(graph.getNode(node.getName()));
        }

        return convertedNodes;
    }


    /**
     * Sorts a list of edges alphabetically by name.
     */
    public static void sortEdges(List<Edge> edges) {
        Collections.sort(edges, new Comparator<Edge>() {
            public int compare(Edge o1, Edge o2) {
                String name11 = o1.getNode1().getName();
                String name12 = o1.getNode2().getName();
                String name21 = o2.getNode1().getName();
                String name22 = o2.getNode2().getName();

                int major = name11.compareTo(name21);
                int minor = name12.compareTo(name22);

                if (major == 0) {
                    return minor;
                } else {
                    return major;
                }
            }
        });
    }

    /**
     * @return an empty graph with the given number of nodes.
     */
    public static Graph emptyGraph(int numNodes) {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + i));
        }

        return new EdgeListGraph(nodes);
    }

    /**
     * Converts a graph to a Graphviz .dot file
     */
    public static String graphToDot(Graph graph) {
        StringBuilder builder = new StringBuilder();

        builder.append("digraph g {\n");
        for (Edge edge : graph.getEdges()) {
            builder.append(" \"" + edge.getNode1() + "\" -> \"" + edge.getNode2() +
                    "\" [arrowtail=");
            if (edge.getEndpoint1() == Endpoint.ARROW)
                builder.append("normal");
            else if (edge.getEndpoint1() == Endpoint.TAIL)
                builder.append("none");
            else if (edge.getEndpoint1() == Endpoint.CIRCLE)
                builder.append("odot");
            builder.append(", arrowhead=");
            if (edge.getEndpoint2() == Endpoint.ARROW)
                builder.append("normal");
            else if (edge.getEndpoint2() == Endpoint.TAIL)
                builder.append("none");
            else if (edge.getEndpoint2() == Endpoint.CIRCLE)
                builder.append("odot");
            builder.append("]; \n");
        }

        builder.append("}");
        return builder.toString();
    }

    public static void graphToDot(Graph graph, File file) {
        try {
            Writer writer = new FileWriter(file);
            writer.write(graphToDot(graph));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return an XML element representing the given graph. (Well, only a
     * basic graph for now...)
     */
    public static Element convertToXml(Graph graph) {
        Element element = new Element("graph");

        Element variables = new Element("variables");
        element.appendChild(variables);

        for (Node node : graph.getNodes()) {
            Element variable = new Element("variable");
            Text text = new Text(node.getName());
            variable.appendChild(text);
            variables.appendChild(variable);
        }

        Element edges = new Element("edges");
        element.appendChild(edges);

        for (Edge edge : graph.getEdges()) {
            Element _edge = new Element("edge");
            Text text = new Text(edge.toString());
            _edge.appendChild(text);
            edges.appendChild(_edge);
        }

        Set<Triple> ambiguousTriples = graph.getAmbiguousTriples();

        if (!ambiguousTriples.isEmpty()) {
            Element underlinings = new Element("ambiguities");
            element.appendChild(underlinings);

            for (Triple triple : ambiguousTriples) {
                Element underlining = new Element("ambiguities");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> underlineTriples = graph.getUnderLines();

        if (!underlineTriples.isEmpty()) {
            Element underlinings = new Element("underlines");
            element.appendChild(underlinings);

            for (Triple triple : underlineTriples) {
                Element underlining = new Element("underline");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> dottedTriples = graph.getDottedUnderlines();

        if (!dottedTriples.isEmpty()) {
            Element dottedUnderlinings = new Element("dottedUnderlines");
            element.appendChild(dottedUnderlinings);

            for (Triple triple : dottedTriples) {
                Element dottedUnderlining = new Element("dottedUnderline");
                Text text = new Text(niceTripleString(triple));
                dottedUnderlining.appendChild(text);
                dottedUnderlinings.appendChild(dottedUnderlining);
            }
        }

        return element;
    }

    private static String niceTripleString(Triple triple) {
        return triple.getX() + ", " + triple.getY() + ", " + triple.getZ();
    }

    public static String graphToXml(Graph graph) {
        Document document = new Document(convertToXml(graph));
        OutputStream out = new ByteArrayOutputStream();
        Serializer serializer = new Serializer(out);
        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return out.toString();
    }

    public static Graph parseGraphXml(Element graphElement, Map<String, Node> nodes) throws ParsingException {
        if (!"graph".equals(graphElement.getLocalName())) {
            throw new IllegalArgumentException("Expecting graph element: " + graphElement.getLocalName());
        }

        Attribute noteAttribute = graphElement.getAttribute("note");

        if (!("variables".equals(graphElement.getChildElements().get(0).getLocalName()))) {
            throw new ParsingException("Expecting variables element: " +
                    graphElement.getChildElements().get(0).getLocalName());
        }

        Element variablesElement = graphElement.getChildElements().get(0);
        Elements variableElements = variablesElement.getChildElements();
        List<Node> variables = new ArrayList<Node>();

        for (int i = 0; i < variableElements.size(); i++) {
            Element variableElement = variableElements.get(i);

            if (!("variable".equals(variablesElement.getChildElements().get(i).getLocalName()))) {
                throw new ParsingException("Expecting variable element.");
            }

            String value = variableElement.getValue();

            if (nodes == null) {
                variables.add(new GraphNode(value));
            } else {
                variables.add(nodes.get(value));
            }
        }

        Graph graph = new EdgeListGraph(variables);

//        graphNotes.add(noteAttribute.getValue());

        if (!("edges".equals(graphElement.getChildElements().get(1).getLocalName()))) {
            throw new ParsingException("Expecting edges element.");
        }

        Element edgesElement = graphElement.getChildElements().get(1);
        Elements edgesElements = edgesElement.getChildElements();

        for (int i = 0; i < edgesElements.size(); i++) {
            Element edgeElement = edgesElements.get(i);

            if (!("edge".equals(edgeElement.getLocalName()))) {
                throw new ParsingException("Expecting edge element: " + edgeElement.getLocalName());
            }

            String value = edgeElement.getValue();

//            System.out.println("value = " + value);

//            String regex = "([A-Za-z0-9_-]*) ?(.)-(.) ?([A-Za-z0-9_-]*)";
            String regex = "([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*) ?(.)-(.) ?([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*)";
//            String regex = "([A-Za-z0-9_-]*) ?([<o])-([o>]) ?([A-Za-z0-9_-]*)";

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value);

            if (!matcher.matches()) {
                throw new ParsingException("Edge doesn't match pattern.");
            }

            String var1 = matcher.group(1);
            String leftEndpoint = matcher.group(2);
            String rightEndpoint = matcher.group(3);
            String var2 = matcher.group(4);

            Node node1 = graph.getNode(var1);
            Node node2 = graph.getNode(var2);
            Endpoint endpoint1;

            if (leftEndpoint.equals("<")) {
                endpoint1 = Endpoint.ARROW;
            } else if (leftEndpoint.equals("o")) {
                endpoint1 = Endpoint.CIRCLE;
            } else if (leftEndpoint.equals("-")) {
                endpoint1 = Endpoint.TAIL;
            } else {
                throw new IllegalStateException("Expecting an endpoint: " + leftEndpoint);
            }

            Endpoint endpoint2;

            if (rightEndpoint.equals(">")) {
                endpoint2 = Endpoint.ARROW;
            } else if (rightEndpoint.equals("o")) {
                endpoint2 = Endpoint.CIRCLE;
            } else if (rightEndpoint.equals("-")) {
                endpoint2 = Endpoint.TAIL;
            } else {
                throw new IllegalStateException("Expecting an endpoint: " + rightEndpoint);
            }

            Edge edge = new Edge(node1, node2, endpoint1, endpoint2);
            graph.addEdge(edge);
        }


        int size = graphElement.getChildElements().size();
        if (2 >= size) return graph;

        int p = 2;

        if ("ambiguities".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "ambiguity");
            graph.setAmbiguousTriples(triples);
            p++;
        }

        if (p >= size) return graph;

        if ("underlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "underline");
            graph.setUnderLineTriples(triples);
            p++;
        }

        if (p >= size) return graph;

        if ("dottedunderlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "dottedunderline");
            graph.setDottedUnderLineTriples(triples);
        }

        return graph;
    }

    /**
     * A triples element has a list of three (comman separated) nodes as text.
     */
    private static Set<Triple> parseTriples(List<Node> variables, Element triplesElement, String s) {
        Elements elements = triplesElement.getChildElements(s);

        Set<Triple> triples = new HashSet<Triple>();

        for (int q = 0; q < elements.size(); q++) {
            Element tripleElement = elements.get(q);
            String value = tripleElement.getValue();

            String[] tokens = value.split(",");

            if (tokens.length != 3) {
                throw new IllegalArgumentException("Expecting a triple: " + value);
            }

            String x = tokens[0].trim();
            String y = tokens[1].trim();
            String z = tokens[2].trim();

            Node _x = getNode(variables, x);
            Node _y = getNode(variables, y);
            Node _z = getNode(variables, z);

            Triple triple = new Triple(_x, _y, _z);
            triples.add(triple);
        }
        return triples;
    }

    private static Node getNode(List<Node> nodes, String x) {
        for (Node node : nodes) {
            if (x.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public static Element getRootElement(File file) throws ParsingException, IOException {
        Builder builder = new Builder();
        Document document = builder.build(file);
        return document.getRootElement();
    }

    /**
     * @param graph The graph to be saved.
     * @param file  The file to save it in.
     * @param xml   True if to be saved in XML, false if in text.
     * @return I have no idea whey I'm returning this; it's already closed...
     */
    public static PrintWriter saveGraph(Graph graph, File file, boolean xml) {
        PrintWriter out;

        try {
            out = new PrintWriter(new FileOutputStream(file));
//            out.print(graph);

            if (xml) {
                out.print(graphToXml(graph));
            } else {
                out.println(graph);
            }
            out.close();
        } catch (IOException e1) {
            throw new IllegalArgumentException(
                    "Output file could not " + "be opened: " + file);
        }
        return out;
    }

    public static Graph loadGraph(File file) {
//        if (!file.getName().endsWith(".xml")) {
//            throw new IllegalArgumentException("Not an XML file.");
//        }

        Element root;
        Graph graph = null;

        try {
            root = getRootElement(file);
            graph = parseGraphXml(root, null);
        } catch (ParsingException e1) {
            throw new IllegalArgumentException("Could not parse " + file, e1);
        } catch (IOException e1) {
            throw new IllegalArgumentException("Could not read " + file, e1);
        }

        if (graph == null) {
            throw new IllegalArgumentException("Expecting a graph in " + file);
        }
        return graph;
    }

    public static Graph loadGraphTxt(File file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));

            while (!in.readLine().trim().equals("Graph Nodes:")) ;

            String line;
            Graph graph = new EdgeListGraph();

            while (!(line = in.readLine().trim()).equals("")) {
                String[] tokens = line.split(" ");

                for (String token : tokens) {
                    graph.addNode(new GraphNode(token));
                }
            }

            while (!in.readLine().trim().equals("Graph Edges:")) ;

            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.equals("")) break;
//                System.out.println(line);

                String[] tokens = line.split(" ");

                String from = tokens[1];
                String edge = tokens[2];
                String to = tokens[3];

                Node _from = graph.getNode(from);
                Node _to = graph.getNode(to);

                char end1 = edge.charAt(0);
                char end2 = edge.charAt(2);

                Endpoint _end1, _end2;

                if (end1 == '<') {
                    _end1 = Endpoint.ARROW;
                } else if (end1 == 'o') {
                    _end1 = Endpoint.CIRCLE;
                } else if (end1 == '-') {
                    _end1 = Endpoint.TAIL;
                } else {
                    throw new IllegalArgumentException();
                }

                if (end2 == '>') {
                    _end2 = Endpoint.ARROW;
                } else if (end2 == 'o') {
                    _end2 = Endpoint.CIRCLE;
                } else if (end2 == '-') {
                    _end2 = Endpoint.TAIL;
                } else {
                    throw new IllegalArgumentException();
                }

                Edge _edge = new Edge(_from, _to, _end1, _end2);

                graph.addEdge(_edge);
            }

            return graph;

        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalStateException();
    }

    public static HashMap<String, PointXy> grabLayout(List<Node> nodes) {
        HashMap<String, PointXy> layout = new HashMap<String, PointXy>();

        for (Node node : nodes) {
            layout.put(node.getName(), new PointXy(node.getCenterX(), node.getCenterY()));
        }

        return layout;
    }

    /**
     * @return A list of triples of the form X*->Y<-*Z.
     */
    public static List<Triple> getCollidersFromGraph(Node node, Graph graph) {
        List<Triple> colliders = new ArrayList<Triple>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.ARROW) {
                colliders.add(new Triple(x, node, z));
            }
        }

        return colliders;
    }

    /**
     * @return A list of triples of the form X*->Y<-*Z.
     */
    public static List<Triple> getDefiniteCollidersFromGraph(Node node, Graph graph) {
        List<Triple> defColliders = new ArrayList<Triple>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.isDefCollider(x, node, z)) {
                defColliders.add(new Triple(x, node, z));
            }
        }

        return defColliders;
    }

    /**
     * @return A list of triples of the form <X, Y, Z>, where <X, Y, Z> is a definite noncollider
     * in the given graph.
     */
    public static List<Triple> getNoncollidersFromGraph(Node node, Graph graph) {
        List<Triple> noncolliders = new ArrayList<Triple>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.TAIL
                    || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.ARROW
                    || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.TAIL) {
                noncolliders.add(new Triple(x, node, z));
            }
        }

        return noncolliders;
    }

    /**
     * @return A list of triples of the form <X, Y, Z>, where <X, Y, Z> is a definite noncollider
     * in the given graph.
     */
    public static List<Triple> getDefiniteNoncollidersFromGraph(Node node, Graph graph) {
        List<Triple> defNoncolliders = new ArrayList<Triple>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.isDefNoncollider(x, node, z)) {
                defNoncolliders.add(new Triple(x, node, z));
            }
        }

        return defNoncolliders;
    }


    /**
     * @return A list of triples of the form <X, Y, Z>, where <X, Y, Z> is a definite noncollider
     * in the given graph.
     */
    public static List<Triple> getAmbiguousTriplesFromGraph(Node node, Graph graph) {
        List<Triple> ambiguousTriples = new ArrayList<Triple>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.isAmbiguousTriple(x, node, z)) {
                ambiguousTriples.add(new Triple(x, node, z));
            }
        }

        return ambiguousTriples;
    }

    /**
     * @return A list of triples of the form <X, Y, Z>, where <X, Y, Z> is a definite noncollider
     * in the given graph.
     */
    public static List<Triple> getUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> underlinedTriples = new ArrayList<Triple>();
        Set<Triple> allUnderlinedTriples = graph.getUnderLines();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allUnderlinedTriples.contains(new Triple(x, node, z))) {
                underlinedTriples.add(new Triple(x, node, z));
            }
        }

        return underlinedTriples;
    }

    /**
     * @return A list of triples of the form <X, Y, Z>, where <X, Y, Z> is a definite noncollider
     * in the given graph.
     */
    public static List<Triple> getDottedUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> dottedUnderlinedTriples = new ArrayList<Triple>();
        Set<Triple> allDottedUnderlinedTriples = graph.getDottedUnderlines();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) return new LinkedList<Triple>();

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allDottedUnderlinedTriples.contains(new Triple(x, node, z))) {
                dottedUnderlinedTriples.add(new Triple(x, node, z));
            }
        }

        return dottedUnderlinedTriples;
    }

    /**
     * Represents straight-out adjacencies for any graph. a[i][j] = 1 just in case there is an
     * edge from i to j in the graph.
     */
    public static int[][] adjacencyMatrix(Graph graph) {
        List<Node> nodes = graph.getNodes();
        int[][] m = new int[nodes.size()][nodes.size()];

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException("Incidence matrix is for directed graphs.");
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                Node x1 = nodes.get(i);
                Node x2 = nodes.get(j);
                Edge edge = graph.getEdge(x1, x2);

                if (edge == null) {
                    m[i][j] = 0;
                } else {
                    m[i][j] = 1;
                }
            }
        }

        return m;
    }

    /**
     * A standard matrix graph representation for directed graphs. a[i][j] = 1 is j-->i and -1 if i-->j.
     *
     * @throws IllegalArgumentException if <code>graph</code> is not a directed graph.
     */
    public static int[][] incidenceMatrix(Graph graph) throws IllegalArgumentException {
        List<Node> nodes = graph.getNodes();
        int[][] m = new int[nodes.size()][nodes.size()];

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException("Not a directed graph.");
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                Node x1 = nodes.get(i);
                Node x2 = nodes.get(j);
                Edge edge = graph.getEdge(x1, x2);

                if (edge == null) {
                    m[i][j] = 0;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.ARROW) {
                    m[i][j] = 1;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.TAIL) {
                    m[i][j] = -1;
                }
            }
        }

        return m;
    }

    /**
     * @param graph
     * @return
     */
    public static String rMatrix(Graph graph) throws IllegalArgumentException {
        int[][] m = incidenceMatrix(graph);

        TextTable table = new TextTable(m[0].length + 1, m.length + 1);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                table.setToken(i + 1, j + 1, m[i][j] + "");
            }
        }

        for (int i = 0; i < m.length; i++) {
            table.setToken(i + 1, 0, (i + 1) + "");
        }

        List<Node> nodes = graph.getNodes();

        for (int j = 0; j < m[0].length; j++) {
            table.setToken(0, j + 1, nodes.get(j).getName());
        }

        return table.toString();

    }

    public static boolean containsBidirectedEdge(Graph graph) {
        boolean containsBidirected = false;

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                containsBidirected = true;
                break;
            }
        }
        return containsBidirected;
    }

    public static boolean existsDirectedPathFromTo(Node node1, Node node2, Graph graph) {
        return node1 == node2 || existsDirectedPathFromToBreathFirst(node1, node2, graph);
//        return existsDirectedPathVisit(node1, node2, new LinkedList<Node>(), 1000, graph);
    }

    public static boolean existsDirectedPathFromTo(Node node1, Node node2, int depth, Graph graph) {
        return node1 == node2 || existsDirectedPathVisit(node1, node2, new LinkedList<Node>(), depth, graph);
    }

    public static boolean existsSemidirectedPathFromTo(Node node1, Node node2, Graph graph) {
        return existsSemiDirectedPathVisit(node1, node2, new LinkedList<Node>(), 1000, graph);
    }

    public static boolean existsSemidirectedPathFromTo(Node node1, Node node2, int maxDepth, Graph graph) {
        return existsSemiDirectedPathVisit(node1, node2, new LinkedList<Node>(), maxDepth, graph);
    }

    private static boolean existsDirectedPathVisit(Node node1, Node node2, LinkedList<Node> path, int depth, Graph graph) {
        path.addLast(node1);

        if (path.size() >= depth) return false;

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsDirectedPathVisit(child, node2, path, depth, graph)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * @return true just in case there is a nonempty path from one node to another. Because
     * the path needs to be non-empty, this can distinguish cycles. The case where from = to
     * but there is no cycle from from to to needs to be checked separately.
     */
    public static boolean existsDirectedPathFromToBreathFirst(Node from, Node to, Graph G) {
        Queue<Node> Q = new LinkedList<Node>();
        Set<Node> V = new HashSet<Node>();
        Q.offer(from);
        V.add(from);

        while (!Q.isEmpty()) {
            Node t = Q.remove();
//            if (t == to) return true;

            for (Node u : G.getAdjacentNodes(t)) {
                Edge edge = G.getEdge(t, u);
                Node c = Edges.traverseDirected(t, edge);

                if (c == null) continue;
                if (c == to) return true;
                if (V.contains(c)) continue;

                V.add(c);
                Q.offer(c);
            }
        }

        return false;
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    public static boolean existsSemiDirectedPathVisit(Node node1, Node node2, LinkedList<Node> path, int maxDepth,
                                                      Graph graph) {
        path.addLast(node1);
        Node previous = null;
//        if (path.size() > 1) previous = path.get(path.size() - 2);

        if (path.size() >= maxDepth) return false;

        for (Edge edge : graph.getEdges(graph.getNode(node1.getName()))) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

//            if (previous != null && graph.isAmbiguousTriple(previous, node1, child)) {
//                continue;
//            }

            if (existsSemiDirectedPathVisit(child, node2, path, maxDepth, graph)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    public static LinkedList<Triple> listTriples(Graph graph) {
        LinkedList<Triple> triples = new LinkedList<Triple>();

        for (Node node : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(node);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> others = asList(choice, adj);
                triples.add(new Triple(others.get(0), node, others.get(1)));
            }
        }
        return triples;
    }

    public static LinkedList<Triple> listColliderTriples(Graph graph) {
        LinkedList<Triple> colliders = new LinkedList<Triple>();

        for (Node node : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(node);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> others = asList(choice, adj);

                if (graph.isDefCollider(others.get(0), node, others.get(1))) {
                    colliders.add(new Triple(others.get(0), node, others.get(1)));
                }
            }
        }
        return colliders;
    }

    public static int getDegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getAdjacentNodes(node).size();
            }
        }

        return max;
    }

    /**
     * Constructs a list of nodes from the given <code>nodes</code> list at the
     * given indices in that list.
     *
     * @param indices The indices of the desired nodes in <code>nodes</code>.
     * @param nodes   The list of nodes from which we select a sublist.
     * @return the The sublist selected.
     */
    public static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<Node>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    public static Set<Node> asSet(int[] indices, List<Node> nodes) {
        Set<Node> set = new HashSet<Node>();

        for (int i : indices) {
            set.add(nodes.get(i));
        }

        return set;
    }

    public static List<Edge> asListEdge(int[] indices, List<Edge> nodes) {
        List<Edge> list = new LinkedList<Edge>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    public static int numDirectionalErrors(Graph result, Graph pattern) {
        int count = 0;

        for (Edge edge : result.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            Node _node1 = pattern.getNode(node1.getName());
            Node _node2 = pattern.getNode(node2.getName());

            Edge _edge = pattern.getEdge(_node1, _node2);

            if (_edge == null) continue;

            if (Edges.isDirectedEdge(edge)) {
                if (_edge.pointsTowards(_node1)) {
                    count++;
                } else if (Edges.isUndirectedEdge(_edge)) {
                    count++;
                }
            }

//            else if (Edges.isBidirectedEdge(edge)) {
//                count++;
//            }
        }

        return count;
    }

    public static int numBidirected(Graph result) {
        int numBidirected = 0;

        for (Edge edge : result.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) numBidirected++;
        }

        return numBidirected;
    }

    public static int degree(Graph graph) {
        int maxDegree = 0;

        for (Node node : graph.getNodes()) {
            int n = graph.getAdjacentNodes(node).size();
            if (n > maxDegree) maxDegree = n;
        }

        return maxDegree;
    }

    public static List<Node> getCausalOrdering(final Graph graph) {
        if (graph.existsDirectedCycle()) throw new IllegalArgumentException("Graph must be acyclic.");

        List<Node> found = new LinkedList<>();
        Collections.shuffle(found);
        List<Node> notFound = new ArrayList<>(graph.getNodes());

        for (Iterator<Node> i = notFound.iterator(); i.hasNext(); ) {
            if (i.next().getNodeType() == NodeType.ERROR) {
                i.remove();
            }
        }

        while (!notFound.isEmpty()) {
            for (Iterator<Node> it = notFound.iterator(); it.hasNext(); ) {
                Node node = it.next();

                if (found.containsAll(graph.getParents(node))) {
                    found.add(node);
                    it.remove();
                }
            }
        }

        return found;
    }

    public static List<Node> getLatents(Graph graph) {
        List<Node> latents = new ArrayList<Node>();
        for (Node node : graph.getNodes()) if (node.getNodeType() == NodeType.LATENT) latents.add(node);
        return latents;
    }

    public static String getIntersectionComparisonString(List<Graph> graphs) {
        if (graphs == null || graphs.isEmpty()) return "";

        StringBuilder b = undirectedEdges(graphs);

        b.append(directedEdges(graphs));

        return b.toString();
    }

    public static StringBuilder undirectedEdges(List<Graph> graphs) {
        List<Graph> undirectedGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            Graph graph2 = new EdgeListGraph(graph);
            graph2.reorientAllWith(Endpoint.TAIL);
            undirectedGraphs.add(graph2);
        }

        Map<String, Node> exemplars = new HashMap<String, Node>();

        for (Graph graph : undirectedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }
        List<Node> nodes = new ArrayList<>(nodeSet);
        List<Graph> undirectedGraphs2 = new ArrayList<>();

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = replaceNodes(undirectedGraphs.get(i),
                    nodes);
            undirectedGraphs2.add(graph);
        }

        Set<Edge> undirectedEdgesSet = new HashSet<>();

        for (Graph graph : undirectedGraphs2) {
            undirectedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> undirectedEdges = new ArrayList<Edge>(undirectedEdgesSet);

        Collections.sort(undirectedEdges, new Comparator<Edge>() {
            public int compare(Edge o1, Edge o2) {
                String name11 = o1.getNode1().getName();
                String name12 = o1.getNode2().getName();
                String name21 = o2.getNode1().getName();
                String name22 = o2.getNode2().getName();

                int major = name11.compareTo(name21);
                int minor = name12.compareTo(name22);

                if (major == 0) {
                    return minor;
                } else {
                    return major;
                }
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < graphs.size(); i++) groups.add(new ArrayList<Edge>());

        for (Edge edge : undirectedEdges) {
            int count = 0;

            for (Graph graph : undirectedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count++;
                }
            }

            if (count == 0) throw new IllegalArgumentException();

            groups.get(count - 1).add(edge);
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nIn " + (i + 1) + " graph" + ((i > 0) ? "s" : "") + "...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n" + (j + 1) + ". " + groups.get(i).get(j));
            }
        }

        return b;
    }

    public static StringBuilder directedEdges(List<Graph> directedGraphs) {
        Set<Edge> directedEdgesSet = new HashSet<>();

        Map<String, Node> exemplars = new HashMap<String, Node>();

        for (Graph graph : directedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }

        List<Node> nodes = new ArrayList<>(nodeSet);

        List<Graph> directedGraphs2 = new ArrayList<>();

        for (int i = 0; i < directedGraphs.size(); i++) {
            Graph graph = replaceNodes(directedGraphs.get(i),
                    nodes);
            directedGraphs2.add(graph);
        }

        for (Graph graph : directedGraphs2) {
            directedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> directedEdges = new ArrayList<Edge>(directedEdgesSet);

        Collections.sort(directedEdges, new Comparator<Edge>() {
            public int compare(Edge o1, Edge o2) {
                String name11 = o1.getNode1().getName();
                String name12 = o1.getNode2().getName();
                String name21 = o2.getNode1().getName();
                String name22 = o2.getNode2().getName();

                int major = name11.compareTo(name21);
                int minor = name12.compareTo(name22);

                if (major == 0) {
                    return minor;
                } else {
                    return major;
                }
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < directedGraphs2.size(); i++) groups.add(new ArrayList<Edge>());
        Set<Edge> contradicted = new HashSet<>();

        for (Edge edge : directedEdges) {
            if (!edge.isDirected()) continue;

            int count = 0;
            int count2 = 0;

            for (Graph graph : directedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count++;
                } else if (uncontradicted(edge, graph.getEdge(edge.getNode1(), edge.getNode2()))) {
                    count2++;
                } else if (!contradicted.contains(edge) && !contradicted.contains(edge.reverse())) {
                    contradicted.add(edge);
                }
            }

            if (count2 == 0) {
                groups.get(count - 1).add(edge);
            }
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nUncontradicted in " + (i + 1) + " graph" + ((i > 0) ? "s" : "") + "...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n" + (j + 1) + ". " + groups.get(i).get(j));
            }
        }

        b.append("\n\nContradicted:\n");
        int index = 1;

        for (Edge edge : contradicted) {
            b.append("\n" + (index++) + ". " + edge);
        }

        return b;
    }

    public static boolean uncontradicted(Edge edge1, Edge edge2) {
        if (edge1 == null || edge2 == null) return true;

        Node x = edge1.getNode1();
        Node y = edge1.getNode2();

        if (edge1.pointsTowards(x) && edge2.pointsTowards(y)) return false;
        else if (edge1.pointsTowards(y) && edge2.pointsTowards(x)) return false;
        return true;
    }

    public static String edgeMisclassifications1(int[][] counts) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
//        table2.setToken(4, 0, "<-o");
        table2.setToken(4, 0, "-->");
//        table2.setToken(6, 0, "<--");
        table2.setToken(5, 0, "<->");
        table2.setToken(6, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 5 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        builder.append(table2.toString());

        return builder.toString();
    }

    private static int[][] edgeMisclassificationCounts1(Graph leftGraph, Graph topGraph, PrintStream out) {
        topGraph = replaceNodes(topGraph, leftGraph.getNodes());

        int[][] counts = new int[6][6];

        for (Edge est : topGraph.getEdges()) {
            Node x = est.getNode1();
            Node y = est.getNode2();

            Edge left = leftGraph.getEdge(x, y);

            Edge top = topGraph.getEdge(x, y);

            int m = getType1(left);
            int n = getType1(top);

            counts[m][n]++;
        }

        out.println("# edges in true graph = " + leftGraph.getNumEdges());
        out.println("# edges in est graph = " + topGraph.getNumEdges());

        for (Edge edge : leftGraph.getEdges()) {
            if (topGraph.getEdge(edge.getNode1(), edge.getNode2()) == null) {
                int m = getType1(edge);
                counts[m][5]++;
            }
        }

        return counts;
    }

    private static int getType1(Edge edge) {
        if (edge == null) {
            return 5;
        }

        Endpoint e1 = edge.getEndpoint1();
        Endpoint e2 = edge.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.CIRCLE) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.TAIL) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
    }

    public static String edgeMisclassifications(int[][] counts) {
        if (false) {
            return edgeMisclassifications1(counts);
        }

        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        builder.append(table2.toString());

        int correctEdges = 0;
        int estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = " + nf.format((correctEdges / (double) estimatedEdges)));

        return builder.toString();
    }

    public static int[][] edgeMisclassificationCounts(Graph leftGraph, Graph topGraph, PrintStream out) {
        if (false) {
            return edgeMisclassificationCounts1(leftGraph, topGraph, out);
        }

        topGraph = replaceNodes(topGraph, leftGraph.getNodes());

        int[][] counts = new int[8][6];

        for (Edge est : topGraph.getEdges()) {
            Node x = est.getNode1();
            Node y = est.getNode2();

            Edge left = leftGraph.getEdge(x, y);

            Edge top = topGraph.getEdge(x, y);

            int m = getTypeLeft(left, top);
            int n = getTypeTop(top);

            counts[m][n]++;
        }

        System.out.println("# edges in true graph = " + leftGraph.getNumEdges());
        System.out.println("# edges in est graph = " + topGraph.getNumEdges());

        for (Edge edgeLeft : leftGraph.getEdges()) {
            final Edge edgeTop = topGraph.getEdge(edgeLeft.getNode1(), edgeLeft.getNode2());
            if (edgeTop == null) {
                int m = getTypeLeft(edgeLeft, edgeLeft);
                counts[m][5]++;
            }
        }

        return counts;
    }

    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        if (Edges.isUndirectedEdge(edgeTop)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeTop)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeTop)) {
            return 2;
        }

        if (Edges.isDirectedEdge(edgeTop)) {
            return 3;
        }

        if (Edges.isBidirectedEdge(edgeTop)) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edgeTop type : " + edgeTop);
    }

    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        Node x = edgeLeft.getNode1();
        Node y = edgeLeft.getNode2();

        if (Edges.isUndirectedEdge(edgeLeft)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeLeft)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) ||
                    edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x)) {
                return 3;
            } else {
                return 2;
            }

//            if (edgeTop.equals(edgeLeft.reverse())) {
//                return 3;
//            }
//            else {
//                return 2;
//            }
        }

        if (Edges.isDirectedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) ||
                    edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x)) {
                return 5;
            } else {
                return 4;
            }

//            if (edgeTop.equals(edgeLeft.reverse())) {
//                return 5;
//            }
//            else {
//                return 4;
//            }
        }

        if (Edges.isBidirectedEdge(edgeLeft)) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    public static class GraphComparison {
        private int adjFn;
        private int adjFp;
        private int adjCorrect;
        private int arrowptFn;
        private int arrowptFp;
        private int arrowptCorrect;
        private int shd;
        private int twoCycleFn;
        private int twoCycleFp;
        private int twoCycleCorrect;

        private List<Edge> edgesAdded;
        private List<Edge> edgesRemoved;
        private List<Edge> edgesReorientedFrom;
        private List<Edge> edgesReorientedTo;

        public GraphComparison(int adjFn, int adjFp, int adjCorrect,
                               int arrowptFn, int arrowptFp, int arrowptCorrect,
                               int shd,
                               int twoCycleCorrect, int twoCycleFn, int twoCycleFp,
                               List<Edge> edgesAdded, List<Edge> edgesRemoved,
                               List<Edge> edgesReorientedFrom,
                               List<Edge> edgesReorientedTo) {
            this.adjFn = adjFn;
            this.adjFp = adjFp;
            this.adjCorrect = adjCorrect;
            this.arrowptFn = arrowptFn;
            this.arrowptFp = arrowptFp;
            this.arrowptCorrect = arrowptCorrect;
            this.shd = shd;
            this.twoCycleCorrect = twoCycleCorrect;
            this.twoCycleFn = twoCycleFn;
            this.twoCycleFp = twoCycleFp;
            this.edgesAdded = edgesAdded;
            this.edgesRemoved = edgesRemoved;
            this.edgesReorientedFrom = edgesReorientedFrom;
            this.edgesReorientedTo = edgesReorientedTo;
        }

        public int getAdjFn() {
            return adjFn;
        }

        public int getAdjFp() {
            return adjFp;
        }

        public int getAdjCorrect() {
            return adjCorrect;
        }

        public int getArrowptFn() {
            return arrowptFn;
        }

        public int getArrowptFp() {
            return arrowptFp;
        }

        public int getArrowptCorrect() {
            return arrowptCorrect;
        }

        public int getShd() {
            return shd;
        }

        public int getTwoCycleFn() {
            return twoCycleFn;
        }

        public int getTwoCycleFp() {
            return twoCycleFp;
        }

        public int getTwoCycleCorrect() {
            return twoCycleCorrect;
        }

        public List<Edge> getEdgesAdded() {
            return edgesAdded;
        }

        public List<Edge> getEdgesRemoved() {
            return edgesRemoved;
        }

        public List<Edge> getEdgesReorientedFrom() {
            return edgesReorientedFrom;
        }

        public List<Edge> getEdgesReorientedTo() {
            return edgesReorientedTo;
        }
    }

    public static TwoCycleErrors getTwoCycleErrors(Graph trueGraph, Graph estGraph) {
        Set<Edge> trueEdges = trueGraph.getEdges();
        Set<Edge> trueTwoCycle = new HashSet<Edge>();

        for (Edge edge : trueEdges) {
            if (!edge.isDirected()) continue;

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (trueEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                trueTwoCycle.add(undirEdge);
            }
        }

        Set<Edge> estEdges = estGraph.getEdges();
        Set<Edge> estTwoCycle = new HashSet<Edge>();

        for (Edge edge : estEdges) {
            if (!edge.isDirected()) continue;

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (estEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                estTwoCycle.add(undirEdge);
            }
        }

        Graph trueTwoCycleGraph = new EdgeListGraph(trueGraph.getNodes());

        for (Edge edge : trueTwoCycle) {
            trueTwoCycleGraph.addEdge(edge);
        }

        Graph estTwoCycleGraph = new EdgeListGraph(estGraph.getNodes());

        for (Edge edge : estTwoCycle) {
            estTwoCycleGraph.addEdge(edge);
        }

        estTwoCycleGraph = GraphUtils.replaceNodes(estTwoCycleGraph, trueTwoCycleGraph.getNodes());

        int adjFn = GraphUtils.countAdjErrors(trueTwoCycleGraph, estTwoCycleGraph);
        int adjFp = GraphUtils.countAdjErrors(estTwoCycleGraph, trueTwoCycleGraph);

        Graph undirectedGraph = undirectedGraph(estTwoCycleGraph);
        int adjCorrect = undirectedGraph.getNumEdges() - adjFp;

        List<Edge> edgesAdded = new ArrayList<Edge>();
        List<Edge> edgesRemoved = new ArrayList<Edge>();

        for (Edge edge : trueTwoCycleGraph.getEdges()) {
            if (!estTwoCycleGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                edgesRemoved.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
            }
        }

        for (Edge edge : estTwoCycleGraph.getEdges()) {
            if (!trueTwoCycleGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                edgesAdded.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
            }
        }

        TwoCycleErrors twoCycleErrors = new TwoCycleErrors(
                adjCorrect,
                adjFn,
                adjFp
        );

        return twoCycleErrors;
    }

    public static class TwoCycleErrors {
        public int twoCycCor = 0;
        public int twoCycFn = 0;
        public int twoCycFp = 0;

        public TwoCycleErrors(int twoCycCor, int twoCycFn, int twoCycFp) {
            this.twoCycCor = twoCycCor;
            this.twoCycFn = twoCycFn;
            this.twoCycFp = twoCycFp;
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();

            buf.append("2c cor = " + twoCycCor + "\t");
            buf.append("2c fn = " + twoCycFn + "\t");
            buf.append("2c fp = " + twoCycFp);

            return buf.toString();
        }
    }


    public static boolean isDConnectedTo(Node x, Node y, List<Node> z, Graph graph) {
        return isDConnectedTo1(x, y, z, graph);
//        return isDConnectedTo2(x, y, z, graph);
//        return isDConnectedTo3(x, y, z, graph);
//        return isDConnectedTo4(x, y, z, graph);
    }

    // Breadth first.
    public static boolean isDConnectedTo1(Node x, Node y, List<Node> z, Graph graph) {
        Queue<OrderedPair<Node>> Q = new ArrayDeque<OrderedPair<Node>>();
        Set<OrderedPair<Node>> V = new HashSet<OrderedPair<Node>>();

        if (x == y) return true;

        for (Node node : graph.getAdjacentNodes(x)) {
            if (node == y) return true;
            OrderedPair<Node> edge = new OrderedPair<Node>(x, node);
            Q.offer(edge);
            V.add(edge);
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            Node a = t.getFirst();
            Node b = t.getSecond();

            for (Node c : graph.getAdjacentNodes(b)) {
                if (reachable(a, b, c, z, graph)) {
                    if (c == y) return true;

                    OrderedPair u = new OrderedPair<Node>(b, c);
                    if (V.contains(u)) continue;

                    V.add(u);
                    Q.offer(u);
                }
            }
        }

        return false;
    }

    // Depth first.
    public static boolean isDConnectedTo2(Node x, Node y, List<Node> z, Graph graph) {
        LinkedList<Node> path = new LinkedList<Node>();

        path.add(x);

        for (Node c : graph.getAdjacentNodes(x)) {
            if (isDConnectedToVisit2(x, c, y, path, z, graph)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isDConnectedTo2(Node x, Node y, List<Node> z, Graph graph, LinkedList<Node> path) {
        path.add(x);

        for (Node c : graph.getAdjacentNodes(x)) {
            if (isDConnectedToVisit2(x, c, y, path, z, graph)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDConnectedToVisit2(Node a, Node b, Node y, LinkedList<Node> path, List<Node> z, Graph graph) {
        if (b == y) {
            path.addLast(b);
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        for (Node c : graph.getAdjacentNodes(b)) {
            if (a == c) continue;

            if (reachable(a, b, c, z, graph)) {
                if (isDConnectedToVisit2(b, c, y, path, z, graph)) {
//                    path.removeLast();
                    return true;
                }
            }
        }

        path.removeLast();
        return false;
    }

    public static boolean isDConnectedTo3(Node x, Node y, List<Node> z, Graph graph) {
        return reachableDConnectedNodes(x, z, graph).contains(y);
    }

    private static Set<Node> reachableDConnectedNodes(Node x, List<Node> z, Graph graph) {
        Set<Node> R = new HashSet<Node>();
        R.add(x);

        Queue<OrderedPair<Node>> Q = new ArrayDeque<OrderedPair<Node>>();
        Set<OrderedPair<Node>> V = new HashSet<OrderedPair<Node>>();

        for (Node node : graph.getAdjacentNodes(x)) {
            OrderedPair<Node> edge = new OrderedPair<Node>(x, node);
            Q.offer(edge);
            V.add(edge);
            R.add(node);
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            Node a = t.getFirst();
            Node b = t.getSecond();

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;
                if (!reachable(a, b, c, z, graph, null)) continue;
                R.add(c);

                OrderedPair<Node> u = new OrderedPair<Node>(b, c);
                if (V.contains(u)) continue;

                V.add(u);
                Q.offer(u);
            }
        }

        return R;
    }

    public static boolean isDConnectedTo(List<Node> x, List<Node> y, List<Node> z, Graph graph) {
        Set<Node> zAncestors = zAncestors(z, graph);

        Queue<OrderedPair<Node>> Q = new ArrayDeque<OrderedPair<Node>>();
        Set<OrderedPair<Node>> V = new HashSet<OrderedPair<Node>>();

        for (Node _x : x) {
            for (Node node : graph.getAdjacentNodes(_x)) {
                if (y.contains(node)) return true;
                OrderedPair<Node> edge = new OrderedPair<Node>(_x, node);
                Q.offer(edge);
                V.add(edge);
            }
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            Node b = t.getFirst();
            Node a = t.getSecond();

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;

                boolean collider = graph.isDefCollider(a, b, c);
                if (!((collider && zAncestors.contains(b)) || (!collider && !z.contains(b)))) continue;

                if (y.contains(c)) return true;

                OrderedPair<Node> u = new OrderedPair<Node>(b, c);
                if (V.contains(u)) continue;

                V.add(u);
                Q.offer(u);
            }
        }

        return false;
    }

    // Finds a sepset for x and y, if there is one; otherwise, returns null.
    public static List<Node> getSepset(Node x, Node y, Graph graph) {
        final int bound = -1;
        List<Node> sepset = getSepsetVisit(x, y, graph, bound);
        if (sepset == null) sepset = getSepsetVisit(y, x, graph, bound);
        return sepset;
    }

    private static List<Node> getSepsetVisit(Node x, Node y, Graph graph, int bound) {
        if (x == y) {
            return null;
        }

        List<Node> z = new ArrayList<>();

        List<Node> _z;

        do {
            _z = new ArrayList<>(z);

            Set<Node> path = new HashSet<>();
            path.add(x);
            Set<Triple> colliders = new HashSet<>();

            for (Node b : graph.getAdjacentNodes(x)) {
//                if (b == y) {
//                    return null;
//                }

                if (sepsetPathFound(x, b, y, path, z, graph, colliders, bound)) {
                    return null;
                }
            }
        } while (!new HashSet<Node>(z).equals(new HashSet<Node>(_z)));

        return z;
    }

    private static boolean sepsetPathFound(Node a, Node b, Node y, Set<Node> path, List<Node> z, Graph graph,
                                           Set<Triple> colliders, int bound) {
        if (b == y) {
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        if (path.size() > (bound == -1 ? 1000 : bound)) {
            return false;
        }

        path.add(b);

        if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
            final List<Node> passNodes = getPassNodes(a, b, z, graph, null);

            for (Node c : passNodes) {
                if (sepsetPathFound(b, c, y, path, z, graph, colliders, bound)) {
                    path.remove(b);
                    return true;
                }
            }

            path.remove(b);
            return false;
        } else {
            boolean found1 = false;
            Set<Triple> _colliders1 = new HashSet<Triple>();

            for (Node c : getPassNodes(a, b, z, graph, _colliders1)) {
                if (sepsetPathFound(b, c, y, path, z, graph, _colliders1, bound)) {
                    found1 = true;
                    break;
                }
            }

            if (!found1) {
                path.remove(b);
                colliders.addAll(_colliders1);
                return false;
            }

            z.add(b);
            boolean found2 = false;
            Set<Triple> _colliders2 = new HashSet<Triple>();

            for (Node c : getPassNodes(a, b, z, graph, null)) {
                if (sepsetPathFound(b, c, y, path, z, graph, _colliders2, bound)) {
                    found2 = true;
                    break;
                }
            }

            if (!found2) {
                path.remove(b);
                colliders.addAll(_colliders2);
                return false;
            }

            z.remove(b);
            path.remove(b);
            return true;
        }
    }

    private static Set<Triple> colliders(Node b, Graph graph, Set<Triple> colliders) {
        Set<Triple> _colliders = new HashSet<Triple>();

        for (Triple collider : colliders) {
            if (graph.isAncestorOf(collider.getY(), b)) {
                _colliders.add(collider);
            }
        }

        return _colliders;
    }

    private static boolean reachable(Node a, Node b, Node c, List<Node> z, Graph graph) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z, graph);
        return collider && ancestor;
    }

    private static boolean reachable(Node a, Node b, Node c, List<Node> z, Graph graph, Set<Triple> colliders) {
        boolean collider = graph.isDefCollider(a, b, c);

        if (!collider && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z, graph);

        final boolean colliderReachable = collider && ancestor;

        if (colliders != null && collider && !ancestor) {
            colliders.add(new Triple(a, b, c));
        }

        return colliderReachable;
    }

    private static boolean isAncestor(Node b, List<Node> z, Graph graph) {
        boolean ancestor = false;

        for (Node n : z) {
            if (graph.isAncestorOf(b, n)) {
                ancestor = true;
                break;
            }
        }

        return ancestor;
    }

    private static List<Node> getPassNodes(Node a, Node b, List<Node> z, Graph graph, Set<Triple> colliders) {
        List<Node> passNodes = new ArrayList<Node>();

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;

            if (reachable(a, b, c, z, graph, colliders)) {
                passNodes.add(c);
            }
        }

        return passNodes;
    }

    public static Set<Node> zAncestors(List<Node> z, Graph graph) {
        Queue<Node> Q = new ArrayDeque<Node>();
        Set<Node> V = new HashSet<Node>();

        for (Node node : z) {
            Q.offer(node);
            V.add(node);
        }

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : graph.getParents(t)) {
                if (V.contains(c)) continue;
                V.add(c);
                Q.offer(c);
            }
        }

        return V;
    }

    public static Set<Node> zAncestors2(List<Node> z, Graph graph) {
        Set<Node> ancestors = new HashSet<Node>(z);

        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node n : new HashSet<Node>(ancestors)) {
                List<Node> parents = graph.getParents(n);

                if (!ancestors.containsAll(parents)) {
                    ancestors.addAll(parents);
                    changed = true;
                }
            }
        }

        return ancestors;
    }

    public Set<Node> zAncestors(Node z, Graph graph) {
        Queue<Node> Q = new ArrayDeque<Node>();
        Set<Node> V = new HashSet<Node>();

        Q.offer(z);
        V.add(z);

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : graph.getParents(t)) {
                if (V.contains(c)) continue;
                V.add(c);
                Q.offer(c);
            }
        }

        return V;
    }

    public static boolean existsInducingPath(Node x, Node y, Graph graph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<Node>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(graph, x, b, x, y, path)) {
                return true;
            }
        }

        return false;
    }

    // Needs to be public.
    public static boolean existsInducingPathVisit(Graph graph, Node a, Node b, Node x, Node y,
                                                  LinkedList<Node> path) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        if (b == y) return true;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) continue;

            }

            if (graph.isDefCollider(a, b, c)) {
                if (!(graph.isAncestorOf(b, x) || graph.isAncestorOf(b, y))) {
                    continue;
                }
            }

            if (existsInducingPathVisit(graph, b, c, x, y, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    public static List<Node> getInducingPath(Node x, Node y, Graph graph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<Node>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(graph, x, b, x, y, path)) {
                return path;
            }
        }

        return null;
    }

    public static Set<Node> possibleDsep(Node x, Node y, Graph graph, int maxPathLength) {
        Set<Node> dsep = new HashSet<>();

        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        Set<OrderedPair<Node>> V = new HashSet<>();

        Map<Node, List<Node>> previous = new HashMap<>();
        previous.put(x, null);

        OrderedPair e = null;
        int distance = 0;

        for (Node b : graph.getAdjacentNodes(x)) {
            if (b == y) continue;
            OrderedPair<Node> edge = new OrderedPair<>(x, b);
            if (e == null) e = edge;
            Q.offer(edge);
            V.add(edge);
            addToList(previous, b, x);
            dsep.add(b);
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            if (e == t) {
                e = null;
                distance++;
                if (distance > 0 && distance > (maxPathLength == -1 ? 1000 : maxPathLength)) break;
            }

            Node a = t.getFirst();
            Node b = t.getSecond();

            if (existOnePathWithPossibleParents(previous, b, x, b, graph)) {
                dsep.add(b);
            }

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;
                if (c == x) continue;
                if (c == y) continue;

                addToList(previous, b, c);

                if (graph.isDefCollider(a, b, c) || graph.isAdjacentTo(a, c)) {
                    OrderedPair<Node> u = new OrderedPair<>(a, c);
                    if (V.contains(u)) continue;

                    V.add(u);
                    Q.offer(u);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        dsep.remove(x);
        dsep.remove(y);
        return dsep;
    }

    private static boolean existOnePathWithPossibleParents(Map<Node, List<Node>> previous, Node w, Node x, Node b, Graph graph) {
        if (w == x) return true;
        final List<Node> p = previous.get(w);
        if (p == null) return false;

        for (Node r : p) {
            if (r == b || r == x) continue;

            if ((existsSemidirectedPath(r, x, graph)) ||
                    existsSemidirectedPath(r, b, graph)) {
                if (existOnePathWithPossibleParents(previous, r, x, b, graph)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void addToList(Map<Node, List<Node>> previous, Node b, Node c) {
        List<Node> list = previous.get(c);

        if (list == null) {
            list = new ArrayList<>();
        }

        list.add(b);
    }

    public static boolean existsSemidirectedPath(Node from, Node to, Graph G) {
        Queue<Node> Q = new LinkedList<Node>();
        Set<Node> V = new HashSet<Node>();
        Q.offer(from);
        V.add(from);

        while (!Q.isEmpty()) {
            Node t = Q.remove();
            if (t == to) return true;

            for (Node u : G.getAdjacentNodes(t)) {
                Edge edge = G.getEdge(t, u);
                Node c = Edges.traverseSemiDirected(t, edge);

                if (c == null) continue;
                if (V.contains(c)) continue;

                V.add(c);
                Q.offer(c);
            }
        }

        return false;
    }
}




