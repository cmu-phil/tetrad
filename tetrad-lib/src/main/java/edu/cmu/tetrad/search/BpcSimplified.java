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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;


/**
 * Implements a modification of BuildPureClusters-Simplified from Silva, Scheines, Glymour, and Spirtes,
 * Learning the Structure of Linear Latent Variable Models, JMLR 7 (2006), 191-246.
 * <p>
 * References:
 * <p>
 * Silva, R.; Scheines, R.; Spirtes, P.; Glymour, C. (2003). "Learning measurement models". Technical report
 * CMU-CALD-03-100, Center for Automated Learning and Discovery, Carnegie Mellon University.
 * <p>
 * Bollen, K. (1990). "Outlier screening and distribution-free test for vanishing tetrads." Sociological Methods and
 * Research 19, 80-92.
 * <p>
 * Wishart, J. (1928). "Sampling errors in the theory of two factors". British Journal of Psychology 19, 180-187.
 * <p>
 * Bron, C. and Kerbosch, J. (1973) "Algorithm 457: Finding all cliques of an undirected graph". Communications of ACM
 * 16, 575-577.
 *
 * @author Joseph Ramsey
 * @deprecated
 */
public class BpcSimplified {
    private DataSet dataSet;
    private ICovarianceMatrix cov;
    private List<Node> variables;
    private TetradTest test;
    private double alpha;
    private static final int MAX_CLIQUE_TRIALS = 50;
    private IndependenceTest indTest;
    private boolean depthOne = false;
    private Graph depthOneGraph;
    private HashMap<Node, Integer> variablesMap;
    private TestType testType;
    private DeltaTetradTest deltaTest;

    public BpcSimplified(ICovarianceMatrix cov, TestType testType, double alpha) {
        this.cov = cov;
        this.variables = cov.getVariables();
        this.test = new ContinuousTetradTest(cov, testType, alpha);
        this.alpha = alpha;
        this.testType = testType;
        this.indTest = new IndTestFisherZ(cov, alpha);
        deltaTest = new DeltaTetradTest(cov);
    }

    public BpcSimplified(DataSet dataSet, TestType testType, double alpha) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.test = new ContinuousTetradTest(dataSet, testType, alpha);
        this.alpha = alpha;
        this.testType = testType;
        this.indTest = new IndTestFisherZ(dataSet, alpha);
        deltaTest = new DeltaTetradTest(dataSet);
    }

    public Graph search() {
        return buildPureClustersSimplified();
    }

    private Graph findPattern() {
        // 1. Start with a complete undirected graph G over the observed variables.
        // 2. Remove edges for pairs that are marginally uncorrelated or uncorrelated conditioned on a
        // third observed variable.
        Graph G = fas(variables, isDepthOne() ? 3 : 0);
        this.depthOneGraph = new EdgeListGraph(G);
//        Graph G = new EdgeListGraph(variables);
//        G.fullyConnect(Endpoint.TAIL);

        // 3. For every pair of nodes linked by an edge in G, test if some rule CS1, CS2 or CS3 applies.
        // Remove an edge between every pair corresponding to a rule that applies.

//        for (int iteration = 0; iteration < 1; iteration++) {
//            int max = 1000;
//
//            EDGES:
//            for (Edge edge : G.getEdges()) {
////                System.out.println("Considering edge " + edge);
//
//                Node x1 = edge.getNode1();
//                Node y1 = edge.getNode2();
//
//                List<Node> _variables;
//
////                _variables = reachable(G, x1, y1);
//                _variables = new ArrayList<Node>(variables);
//
////                if (iteration == 0) {
////                    _variables = new ArrayList<Node>(variables);
////                }
////                else {
////                    _variables = reachable(G, x1, y1);
////                }
//
//                _variables.remove(x1);
//                _variables.remove(y1);
//
//                Collections.shuffle(_variables);
//
////                if (_variables.size() > 12) max = 495;
////                if (_variables.size() > 15) max = 1365;
////                if (_variables.size() > 8) max = 70;
//
////                System.out.println("variables = " + _variables);
//
//                if (_variables.size() < 4) continue;
//
//                ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 4);
//                int[] choice;
//                int count = 0;
//
//                while ((choice = gen.next()) != null && ++count < max) {
//
////                    Node x2 = _variables.get(choice[0]);
////                    Node x3 = _variables.get(choice[1]);
////                    Node y2 = _variables.get(choice[2]);
////                    Node y3 = _variables.get(choice[3]);
////
////                    // CS1.
////                    if (cs1Condition(x1, x2, x3, y1, y2, y3) || cs1Condition(x1, x2, x3, y1, y3, y2)) {
////                        G.removeEdge(edge);
////                        System.out.println("Removing " + edge + " CS1");
////                        continue EDGES;
////                    }
//
//                    PermutationGenerator gen2 = new PermutationGenerator(4);
//                    int[] perm;
//
//                    while ((perm = gen2.next()) != null) {
//                        Node x2 = _variables.get(choice[perm[0]]);
//                        Node x3 = _variables.get(choice[perm[1]]);
//                        Node y2 = _variables.get(choice[perm[2]]);
//                        Node y3 = _variables.get(choice[perm[3]]);
//
//                        // CS1.
//                        if (cs1Condition(x1, x2, x3, y1, y2, y3)) {
//                            G.removeEdge(edge);
//                            System.out.println("Removing " + edge + " CS1");
//                            continue EDGES;
//                        }
//
//                    }
//
//                }
//            }
//
//        }


        int max = 1000;

        for (int round = 0; round < 2; round++) {

            Set<Edge> edges = G.getEdges();
//            Collections.shuffle(edges);

            EDGES:
            for (Edge edge : edges) {
                Node x1 = edge.getNode1();
                Node y1 = edge.getNode2();

                List<Node> variablesX1 = reachable(G, x1, 2);
                List<Node> variablesY1 = reachable(G, y1, 2);

                variablesX1.remove(x1);
                variablesX1.remove(y1);
                variablesY1.remove(y1);
                variablesY1.remove(x1);

                if (variablesX1.size() < 2) continue;

                ChoiceGenerator gen = new ChoiceGenerator(variablesX1.size(), 2);
                int[] choice;
                int count1 = 0;

                while ((choice = gen.next()) != null && ++count1 < max) {
                    Node x2 = variablesX1.get(choice[0]);
                    Node x3 = variablesX1.get(choice[1]);

                    List<Node> _variablesY1 = new ArrayList<Node>(variablesY1);
                    _variablesY1.remove(x2);
                    _variablesY1.remove(x3);

                    if (_variablesY1.size() < 2) continue;

                    ChoiceGenerator gen2 = new ChoiceGenerator(_variablesY1.size(), 2);
                    int[] choice2;
                    int count2 = 0;

                    while ((choice2 = gen2.next()) != null && ++count2 < max) {
                        Node y2 = _variablesY1.get(choice2[0]);
                        Node y3 = _variablesY1.get(choice2[1]);

//                        if (!depthOneGraph.isAdjacentTo(x2, x3) || !depthOneGraph.isAdjacentTo(y2, y3)) {
//                            continue;
//                        }

                        // CS1.
                        if (cs1Condition(x1, x2, x3, y1, y2, y3) || cs1Condition(x1, x2, x3, y1, y3, y2)) {
                            G.removeEdge(edge);
                            System.out.println("Removing " + edge + " CS1, round " + round);
                            continue EDGES;
                        }
                    }

                }
            }

        }

        List components = findComponents(G);
        Graph H = convertSearchGraph(components);

        return H;
    }

    private boolean cs1Condition(Node x1, Node x2, Node x3, Node y1, Node y2, Node y3) {
        if (testType == TestType.TETRAD_DELTA) {
            Tetrad t1 = new Tetrad(x1, y1, x2, x3);
            Tetrad t2 = new Tetrad(x1, y1, x3, x2);
            Tetrad t3 = new Tetrad(y1, x1, y2, y3);
            Tetrad t4 = new Tetrad(y1, x1, y3, y2);
            Tetrad t5 = new Tetrad(x1, x2, y2, y1);

            deltaTest.calcChiSquare(t1, t2, t3, t4);
            boolean s1 = deltaTest.getPValue() > alpha;

            deltaTest.calcChiSquare(t5);
            boolean s2 = deltaTest.getPValue() > alpha;

            return s1 && !s2;
        }

        return t(x1, y1, x2, x3) && t(x1, y1, x3, x2) && t(y1, x1, y2, y3) && t(y1, x1, y3, y2) && !t(x1, x2, y2, y1);
    }

    public boolean isDepthOne() {
        return depthOne;
    }

    public void setDepthOne(boolean depthOne) {
        this.depthOne = depthOne;
    }

    private List<Node> reachable(Graph g, Node x1, Node y1) {
//        Set<Node> reachable = reachable(g, x1, 2);
//        reachable.addAll(reachable(g, y1, 2));

        int depth = 4;

        Set<Node> reachable2 = new TreeSet<Node>(reachable(g, x1, depth));
        reachable2.addAll(reachable(g, y1, depth));
        return new ArrayList<Node>(reachable2);

//        reachable2.retainAll(reachable);
//        return new ArrayList<Node>(reachable2);


    }

    private List<Node> reachable(Graph g, Node x, int depth) {
        Set<Node> reachable = new TreeSet<Node>();
        reachable.add(x);

        for (int count = 0; count < depth; count++) {
            for (Node node : new TreeSet<Node>(reachable)) {
                reachable.addAll(g.getAdjacentNodes(node));
            }
        }

        return new ArrayList<Node>(reachable);

//        Set<Node> reachable2 = new HashSet<Node>();
//        reachable2.add(x);
//
//        for (int count = 0; count < depth; count++) {
//            for (Node node : new HashSet<Node>(reachable2)) {
//                reachable2.addAll(depthOneGraph.getAdjacentNodes(node));
//            }
//        }
//
//        reachable.retainAll(reachable2);
//
//        return reachable;
    }

    public Graph buildPureClustersSimplified() {
        Graph G = buildPureClustersFirstFiveSteps();

//        List<List<Node>> clusters = findMaximalCliquesNodes(G);
//        Graph H = convertSearchGraphNodes(clusters);
//        ((ContinuousTetradTest) test).setBollenTest(deltaTest);

        IPurify purify = new PurifyTetradBased3(test);
        List<List<Node>> _clustering = extractClusters(G);
        _clustering = purify.purify(_clustering);
        G = convertSearchGraphNodes(_clustering);

// 6. Remove all latents with less than three children, and their respective measures;
        for (Node node : G.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                List<Node> children = G.getChildren(node);
                if (children.size() < 3) {
                    G.removeNode(node);
                    G.removeNodes(children);
                }
            }
        }


        // 7. if G has at least four observed variables, return G. Otherwise, return an empty model.

        List<Node> measured = new ArrayList<Node>();

        for (Node node : G.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        if (measured.size() > 4) {
            return G;
        } else {
            return new EdgeListGraph();
        }
    }

    private Graph buildPureClustersFirstFiveSteps() {
        // 1. G FINDPATTERN(S).
        Graph G = findPattern();

// 2. Choose a set of latents in G. Remove all other latents and all observed nodes that are not
// children of the remaining latents and all clusters of size 1.

// NOTE: I choose all the latents in G! All observed nodes are children of these latents! Easy!

        for (Node node : G.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                List<Node> children = G.getChildren(node);

                if (children.size() == 1) {
                    G.removeNode(node);
                    G.removeNodes(children);
                }
            }
        }

        System.out.println("BPC step 2" + G);

// 3. Remove all nodes that have more than one latent parent in G.

        for (Node node : G.getNodes()) {
            if (node.getNodeType() != NodeType.MEASURED) {
                continue;
            }

            List<Node> parents = G.getParents(node);

            if (parents.size() > 1) {
                G.removeNode(node);
            }
        }

        System.out.println("BPC step 3" + G);

//  4. For all pairs of nodes linked by an undirected edge, choose one element of each pair to be
// removed.

        for (Edge edge : G.getEdges()) {
            if (!G.containsEdge(edge)) continue;
            if (!Edges.isUndirectedEdge(edge)) continue;
            G.removeNode(edge.getNode1());
        }

        System.out.println("BPC step 4" + G);
//
//        // If I'm interpreting this right, Purify does a better job of this step.
////        // 5. If for some set of nodes {A,B,C}, all children of the same latent, there is a fourth node D in
////        // G such that sABsCD = sACsBD = sADsBC is not true, remove one of these four nodes.
////
////        List<Node> latents = new ArrayList<Node>();
////
////        for (Node node : G.getNodes()) {
////            if (node.getNodeType() == NodeType.LATENT) {
////                latents.add(node);
////            }
////        }
////
////        for (Node latent : latents) {
////            AGAIN:
////            while (true) {
////                List<Node> children = G.getChildren(latent);
////                if (children.size() < 3) continue;
////
////                ChoiceGenerator gen = new ChoiceGenerator(children.size(), 3);
////                int[] choice;
////
////                while ((choice = gen.next()) != null) {
////                    Node A = children.get(choice[0]);
////                    Node B = children.get(choice[1]);
////                    Node C = children.get(choice[2]);
////
////                    List<Node> others = new ArrayList<Node>(children);
////                    others.remove(A);
////                    others.remove(B);
////                    others.remove(C);
////
////                    for (Node D : new ArrayList<Node>(others)) {
////                        if (!(t(A, B, C, D) && t(A, B, D, C) && t(A, C, D, B))) {
////                            G.removeNode(D);
////                            continue AGAIN;
////                        }
////                    }
////                }
////
////                break;
////            }
////        }
//
//        System.out.println("BPC step 5" + G);


        return G;
    }

//    public Graph buildPureClusters() {
//
//        Graph G = buildPureClustersFirstFiveSteps();
//
//
//        // 6. Remove all latents with less than three children, and their respective measures;
//        for (Node node : G.getNodes()) {
//            if (node.getNodeType() == NodeType.LATENT) {
//                List<Node> children = G.getChildren(node);
//                if (children.size() < 3) {
//                    G.removeNode(node);
//                    G.removeNodes(children);
//                }
//            }
//        }
//
//        // 7. if G has at least four observed variables, return G. Otherwise, return an empty model.
//
//        List<Node> measured = new ArrayList<Node>();
//
//        for (Node node : G.getNodes()) {
//            if (node.getNodeType() == NodeType.MEASURED) {
//                measured.add(node);
//            }
//        }
//
////        IPurify purify = new PurifyTetradBased3(test);
////        List<List<Node>> _clustering = extractClusters(G);
////        _clustering = purify.purify(_clustering);
////        G = convertSearchGraphNodes(_clustering);
//
//        if (measured.size() > 4) {
//            return G;
//        } else {
//            return new EdgeListGraph();
//        }
//    }

    private List<List<Node>> extractClusters(Graph g) {
        List<List<Node>> clustering = new ArrayList<List<Node>>();

        for (Node node : g.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                List<Node> children = g.getChildren(node);
                clustering.add(children);
            }
        }

        return clustering;
    }


//    private List<List<Node>> findMaximalCliquesNodes(Graph g) {
//        List<List<Node>> clusters = new ArrayList<List<Node>>();
//
//        for (Node node : g.getNodes()) {
//
//            List<Node> cluster = new ArrayList<Node>();
//            cluster.add(node);
//
//            E:
//            for (Node e : g.getNodes()) {
//                if (cluster.contains(e)) continue;
//
//                for (Node f : cluster) {
//                    if (!g.isAdjacentTo(e, f)) continue E;
//                }
//
//                cluster.add(e);
//            }
//
//            clusters.add(cluster);
//        }
//
//        CLUSTER:
//        for (List<Node> cluster1 : new ArrayList<List<Node>>(clusters)) {
//            for (List<Node> cluster2 : new ArrayList<List<Node>>(clusters)) {
//                if (cluster1 == cluster2) continue;
//
//                if (cluster1.containsAll(cluster2)) {
//                    clusters.remove(cluster2);
//                    continue CLUSTER;
//                }
//            }
//        }
//
//        return clusters;
//    }


    /**
     * @return the converted search graph, or null if there is no model.
     */
    private Graph convertSearchGraph(List<int[]> clusters) {
        Graph graph = new EdgeListGraph(variables);

        List<Node> latents = new ArrayList<Node>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(MimBuild.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        for (int i = 0; i < latents.size(); i++) {
            for (int j : clusters.get(i)) {
                graph.addDirectedEdge(latents.get(i), variables.get(j));
            }
        }

        return graph;
    }

    private Graph convertSearchGraphNodes(List<List<Node>> clusters) {
        Graph graph = new EdgeListGraph(variables);

        List<Node> latents = new ArrayList<Node>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(MimBuild.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        for (int i = 0; i < latents.size(); i++) {
            for (Node node : clusters.get(i)) {
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }


    private Graph fas(List<Node> variables, int depth) {
        Graph G = new EdgeListGraph(variables);
        G.fullyConnect(Endpoint.TAIL);

        IndependenceTest test;

        double _alpha = alpha; //.01;

        if (dataSet != null) {
            test = new IndTestFisherZ(dataSet, _alpha);
        } else {
            test = new IndTestFisherZ(cov, _alpha);
        }

        Fas fas = new Fas(G, test);
        fas.setDepth(depth);

        G = fas.search();
        return G;
    }

//    private boolean factor(Node x, Node y, Graph graph) {
//        List<Node> _variables = new ArrayList<Node>(variables);
//        _variables.remove(x);
//        _variables.remove(y);
//
//        ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 2);
//        int[] choice;
//
//        while ((choice = gen.next()) != null) {
//            Node w = _variables.get(choice[0]);
//            Node z = _variables.get(choice[1]);
//
//            if (!indTest.isDependent(x, y)) {
//                return false;
//            }
//
//            if (!indTest.isDependent(x, w)) {
//                return false;
//            }
//
//            if (!indTest.isDependent(x, z)) {
//                return false;
//            }
//
//            if (!indTest.isDependent(y, w)) {
//                return false;
//            }
//
//            if (!indTest.isDependent(y, z)) {
//                return false;
//            }
//
//            if (!indTest.isDependent(w, z)) {
//                return false;
//            }
//
//            if (t(w, x, y, z) && t(w, x, z, y)) {
//                return true;
//
////                List<Node> nodes = new ArrayList<Node>();
////                nodes.add(x);
////                nodes.add(y);
////                nodes.add(z);
////                nodes.add(w);
////
////                List<Edge> edges = fas(nodes, 1).getEdges();
////
////                if (edges.size() > 3) {
////                    return true;
////                }
//            }
//        }
//
//        return false;
//    }

    /**
     * Find components of a graph. Note: naive implementation, but it works. After all, it will still run much faster
     * than Stage 2 of the FindMeasurementPattern algorithm.
     */

    private List<int[]> findComponents(Graph graph) {
        boolean marked[] = new boolean[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            marked[i] = false;
        }
        int numMarked = 0;
        List output = new ArrayList();

        int tempComponent[] = new int[variables.size()];
        while (numMarked != variables.size()) {
            int sizeTemp = 0;
            boolean noChange;
            do {
                noChange = true;
                for (int i = 0; i < variables.size(); i++) {
                    if (marked[i]) {
                        continue;
                    }
                    boolean inComponent = false;
                    for (int j = 0; j < sizeTemp && !inComponent; j++) {
                        if (graph.isAdjacentTo(variables.get(i), variables.get(tempComponent[j]))) {
                            inComponent = true;
                        }
//                        if (graph[i][tempComponent[j]] == color) {
//                            inComponent = true;
//                        }
                    }
                    if (sizeTemp == 0 || inComponent) {
                        tempComponent[sizeTemp++] = i;
                        marked[i] = true;
                        noChange = false;
                        numMarked++;
                    }
                }
            } while (!noChange);
            if (sizeTemp > 1) {
                int newPartition[] = new int[sizeTemp];
                for (int i = 0; i < sizeTemp; i++) {
                    newPartition[i] = tempComponent[i];
                }
                output.add(newPartition);
            }
        }
        return output;
    }

    private List findMaximalCliques(int elements[], Graph G) {
        boolean connected[][] = new boolean[variables.size()][variables.size()];
        for (int i = 0; i < connected.length; i++) {
            for (int j = i; j < connected.length; j++) {
                if (i != j) {
                    boolean adjacent = G.isAdjacentTo(variables.get(i), variables.get(j));
                    connected[i][j] = connected[j][i] = adjacent;
                } else {
                    connected[i][j] = true;
                }
            }
        }
        int numCalls[] = new int[1];
        numCalls[0] = 0;
        int c[] = new int[1];
        c[0] = 0;
        List<int[]> output = new ArrayList<int[]>();
        int compsub[] = new int[elements.length];
        int old[] = new int[elements.length];
        for (int i = 0; i < elements.length; i++) {
            old[i] = elements[i];
        }
        findMaximalCliquesOperator(numCalls, output, connected,
                compsub, c, old, 0, elements.length);
        return output;
    }

    private void findMaximalCliquesOperator(int numCalls[], List<int[]> output, boolean connected[][], int compsub[], int c[],
                                            int old[], int ne, int ce) {
        if (numCalls[0] > MAX_CLIQUE_TRIALS) {
            return;
        }
        int newA[] = new int[ce];
        int nod, fixp = -1;
        int newne, newce, i, j, count, pos = -1, p, s = -1, sel, minnod;
        minnod = ce;
        nod = 0;
        for (i = 0; i < ce && minnod != 0; i++) {
            p = old[i];
            count = 0;
            for (j = ne; j < ce && count < minnod; j++) {
                if (!connected[p][old[j]]) {
                    count++;
                    pos = j;
                }
            }
            if (count < minnod) {
                fixp = p;
                minnod = count;
                if (i < ne) {
                    s = pos;
                } else {
                    s = i;
                    nod = 1;
                }
            }
        }
        for (nod = minnod + nod; nod >= 1; nod--) {
            p = old[s];
            old[s] = old[ne];
            sel = old[ne] = p;
            newne = 0;
            for (i = 0; i < ne; i++) {
                if (connected[sel][old[i]]) {
                    newA[newne++] = old[i];
                }
            }
            newce = newne;
            for (i = ne + 1; i < ce; i++) {
                if (connected[sel][old[i]]) {
                    newA[newce++] = old[i];
                }
            }
            compsub[c[0]++] = sel;
            if (newce == 0) {
                int clique[] = new int[c[0]];
                System.arraycopy(compsub, 0, clique, 0, c[0]);
                output.add(clique);
            } else if (newne < newce) {
                numCalls[0]++;
                findMaximalCliquesOperator(numCalls, output,
                        connected, compsub, c, newA, newne, newce);
            }
            c[0]--;
            ne++;
            if (nod > 1) {
                s = ne;
                while (connected[fixp][old[s]]) {
                    s++;
                }
            }
        }
    }

//    /**
//     * Remove cliques that are contained into another ones in cliqueList.
//     */
//    private List trimCliqueList(List cliqueList) {
//        List trimmed = new ArrayList();
//        List cliqueCopy = new ArrayList();
//        cliqueCopy.addAll(cliqueList);
//
//        Iterator it = cliqueList.iterator();
//        while (it.hasNext()) {
//            int cluster[] = (int[]) it.next();
//            cliqueCopy.remove(cluster);
//            if (!cliqueContained(cluster, cluster.length, cliqueCopy)) {
//                trimmed.add(cluster);
//            }
//            cliqueCopy.add(cluster);
//        }
//        return trimmed;
//    }

//    /**
//     * @return true iff "newClique" is contained in some element of "clustering".
//     */
//
//    private boolean cliqueContained(int newClique[], int size, List clustering) {
//        Iterator it = clustering.iterator();
//        while (it.hasNext()) {
//            int next[] = (int[]) it.next();
//            if (size > next.length) {
//                continue;
//            }
//            boolean found = true;
//            for (int i = 0; i < size && found; i++) {
//                found = false;
//                for (int j = 0; j < next.length && !found; j++) {
//                    if (newClique[i] == next[j]) {
//                        found = true;
//                        break;
//                    }
//                }
//            }
//            if (found) {
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean t(Node n1, Node n2, Node n3, Node n4) {
        if (variablesMap == null) {
            variablesMap = new HashMap<Node, Integer>();

            for (int i = 0; i < variables.size(); i++) {
                variablesMap.put(variables.get(i), i);
            }
        }

        if (testType == TestType.TETRAD_DELTA) {
            Tetrad t1 = new Tetrad(n1, n2, n3, n4);
            deltaTest.calcChiSquare(t1);
            return deltaTest.getPValue() > alpha;
        }

        return test.tetradHolds(variablesMap.get(n1), variablesMap.get(n2), variablesMap.get(n3), variablesMap.get(n4));
    }

}




