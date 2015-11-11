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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

/**
 * Nonlinear orientation, Peter's idea.
 */
public class Nlo {


    private final TetradMatrix data;
    private final DataSet dataSet;
    private final double alpha;

    private Cci cci;

    public Nlo(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.data = dataSet.getDoubleData();
        List<String> varNames = dataSet.getVariableNames();
        cci = new Cci(dataSet.getDoubleData().getRealMatrix(), varNames, alpha);
        this.alpha = alpha;
    }

    public Graph pairwiseOrient1(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node node : graph.getNodes()) {
            List<Node> adjNodes = graph.getAdjacentNodes(node);

            for (Node adj : adjNodes) {

                List<Node> z = new ArrayList<Node>();
                z.add(adj);

                double[] r = cci.residuals(name(node), names(z));

                double[] p = column(adj);

                boolean indep = cci.independent(r, p);

                if (indep) {
                    graph.setEndpoint(adj, node, Endpoint.ARROW);
                }
            }
        }

        return graph;
    }

    private String name(Node node) {
        return node.getName();
    }

    public Graph pairwiseOrient2(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Edge edge : new EdgeListGraph(graph).getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> _x = Collections.singletonList(x);

            double[] r1 = cci.residuals(name(y), names(_x));

            double[] cx = column(x);

            boolean indep1 = cci.independent(r1, cx);
            double py = cci.getQ();

            List<Node> _y = Collections.singletonList(y);

            double[] r2 = cci.residuals(name(x), names(_y));

            double[] cy = column(y);

            boolean indep2 = cci.independent(r2, cy);

            if (indep1 && !indep2) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
            }
            if (indep2 && !indep1) {
                graph.setEndpoint(y, x, Endpoint.ARROW);
            }
//            if (indep1 && indep2) {
//                if (px > py) {
//                    graph.setEndpoint(y, x, Endpoint.ARROW);
//                }
//                else {
//                    graph.setEndpoint(x, y, Endpoint.ARROW);
//                }
//
////                graph.removeEdge(edge);
//            }
//            if (!indep1 && !indep2) {
//                if (px > py) {
//                    graph.setEndpoint(y, x, Endpoint.ARROW);
//                }
//                else {
//                    graph.setEndpoint(x, y, Endpoint.ARROW);
//                }
//
////                graph.removeEdge(edge);
////                graph.addDirectedEdge(x, y);
////                graph.addDirectedEdge(y, x);
//            }

        }

        return graph;
    }

    public Graph pairwiseOrient3(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Edge edge : new EdgeListGraph(graph).getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double[] r1 = cci.residuals(name(y), names(Collections.singletonList(x)));
            double[] cx = column(x);

            cci.independent(r1, cx);
            double px = cci.getQ();

            double[] r2 = cci.residuals(x.toString(), names(Collections.singletonList(y)));
            double[] cy = column(y);
            cci.independent(r2, cy);

            double py = cci.getQ();

            if (py > px) {
                graph.setEndpoint(y, x, Endpoint.ARROW);
            } else if (px > py) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
            }

            System.out.println("px = " + px + " py = " + py + " avg = " + (px + py) / 2.0);
        }

        return graph;
    }

    private double[] column(Node x) {
        return data.getColumn(dataSet.getColumn(x)).toArray();
    }

    public Graph fullOrient(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node node : graph.getNodes()) {
            List<Node> adjNodes = graph.getAdjacentNodes(node);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adjNodes.size(), -1);
            int[] choice;

            List<Node> bestParents = null;
            double bestAvgP = -1;

            while ((choice = gen.next()) != null) {
                if (choice.length < 1) continue;

                List<Node> parents = GraphUtils.asList(choice, adjNodes);

                double[] r = cci.residuals(name(node), names(parents));

                boolean allIndep = true;
                double sumP = 0.0;

                for (Node parent : parents) {
                    double[] p = column(parent);
                    boolean indep = cci.independent(r, p);

                    if (!indep) {
                        allIndep = false;
                        break;
                    }

                    double _p = cci.getQ();
                    sumP += _p;
                }

                if (allIndep) {
                    bestParents = parents;
                    break;
                }

//                double avgP = sumP / parents.size();
//
//                if (avgP > bestAvgP) {
//                    bestAvgP = avgP;
//                    bestParents = parents;
//                }
            }

            if (bestParents != null) {
                for (Node _node : bestParents) {
                    graph.setEndpoint(_node, node, Endpoint.ARROW);
                }
            }
        }

        return graph;
    }

    public Graph fullOrient2(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node node : graph.getNodes()) {
            List<Node> adjNodes = graph.getAdjacentNodes(node);

            for (Node a : adjNodes) {
                List<Node> adj2 = new ArrayList<Node>(adjNodes);
                adj2.remove(a);

                double[] r = cci.residuals(name(node), names(adj2));

                double[] p = column(a);
                boolean indep = cci.independent(r, p);

                if (indep) {
                    graph.setEndpoint(a, node, Endpoint.ARROW);
                }
            }
        }

        return graph;
    }

    public Graph fullOrient3(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node Y : graph.getNodes()) {
            System.out.println("Y = " + Y);

            List<Node> adjNodes = graph.getAdjacentNodes(Y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adjNodes.size(), 4);
            int[] choice;

            List<Node> bestParents = null;

            while ((choice = gen.next()) != null) {
                List<Node> W = GraphUtils.asList(choice, adjNodes);

                double[] r = cci.residuals(name(Y), names(W));

                boolean indep = testIndependence(W, r);

                System.out.println("W = " + W + " indep = " + indep + " p = " + q);

                if (indep) {
                    bestParents = W;
                }
            }

            for (Node X : bestParents) {
                List<Node> _Y = Collections.singletonList(Y);
                double[] r = cci.residuals(name(X), names(_Y));
                boolean indep = testIndependence(_Y, r);

                if (!indep) {
                    graph.setEndpoint(X, Y, Endpoint.ARROW);
                }
            }
        }

//        graph = DataGraphUtils.bidirectedToUndirected(graph);

        return graph;
    }

    public Graph fullOrient4(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        Map<Node, List<Node>> cond = new HashMap<Node, List<Node>>();

        for (Node Y : graph.getNodes()) {
            System.out.println("Y = " + Y);

            double maxQ = 0.0;

            List<Node> adjNodes = graph.getAdjacentNodes(Y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adjNodes.size(), 6);
            int[] choice;

            List<Node> parents = new ArrayList<Node>();

            while ((choice = gen.next()) != null) {
                List<Node> W = GraphUtils.asList(choice, adjNodes);
                if (W.isEmpty()) continue;

                double[] r = new double[0];
                try {
                    r = cci.residuals(name(Y), names(W));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                boolean indep = testIndependence(W, r);

                System.out.println("\tW = " + W + " indep = " + (indep) + " q = " + q);

//                if (indep && q > maxQ) {
//                    maxQ = q;
//                    parents = W;
//                }

                if (indep) {
                    for (Node x : W) {
                        if (!parents.contains(x)) {
                            parents.add(x);
                        }
                    }
                }
            }

            cond.put(Y, parents);
        }

        // Don't orient bidirected edges.
        for (Node Y : graph.getNodes()) {
            System.out.println("Y = " + Y + " cond = " + cond.get(Y));

            for (Node X : cond.get(Y)) {
                System.out.println("\tcond " + X + " = " + cond.get(X));

                boolean contains = cond.get(X).contains(Y);

                if (contains) {
                    System.out.println("\t\t" + cond.get(X) + " contains " + Y + " ... not orienting.");
                }

                if (!contains) {
                    graph.setEndpoint(X, Y, Endpoint.ARROW);
                }
            }
        }

        return graph;
    }

    public Graph fullOrient5(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Edge edge : graph.getEdges()) {
            Node X = edge.getNode1();
            Node Y = edge.getNode2();
            Node C = null;
            double maxQ = 0.0;

            List<Node> adjX = graph.getAdjacentNodes(X);
            DepthChoiceGenerator gen = new DepthChoiceGenerator(adjX.size(), 4);
            int[] choice;

            List<Node> parents = new ArrayList<Node>();

            while ((choice = gen.next()) != null) {
                List<Node> W = GraphUtils.asList(choice, adjX);
                if (W.isEmpty()) continue;

                double[] r = cci.residuals(name(X), names(W));

                boolean indep = testIndependence(W, r);

                double q = cci.getQ();

                System.out.println("\tW = " + W + " indep = " + (indep) + " q = " + q);

                if (indep && q > maxQ) {
                    parents = W;
                    C = X;
                    maxQ = q;
                }
            }

            List<Node> adjY = graph.getAdjacentNodes(Y);
            DepthChoiceGenerator gen2 = new DepthChoiceGenerator(adjY.size(), 4);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                List<Node> W = GraphUtils.asList(choice2, adjY);
                if (W.isEmpty()) continue;

                double[] r = cci.residuals(name(Y), names(W));

                boolean indep = testIndependence(W, r);

                double q = cci.getQ();

                System.out.println("\tW = " + W + " indep = " + (indep) + " q = " + q);

                if (indep && q > maxQ) {
                    parents = W;
                    C = Y;
                    maxQ = q;
                }
            }

            if (C != null) {
                for (Node P : parents) {
                    if (C == P) {
                        throw new IllegalArgumentException();
                    }
//                    if (!(P == C)) continue;
                    System.out.println("Orienting " + P + "-->" + C);
                    graph.setEndpoint(P, C, Endpoint.ARROW);
                }
            }
        }

        return graph;
    }

    public Graph fullOrient6(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        Map<Node, List<Node>> cond = new HashMap<Node, List<Node>>();
        Map<Node, List<Double>> pVal = new HashMap<Node, List<Double>>();

        for (Node Y : graph.getNodes()) {
            System.out.println("Y = " + Y);

            List<Node> adjNodes = graph.getAdjacentNodes(Y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adjNodes.size(), 4);
            int[] choice;

            List<Node> bestParents = null;

            while ((choice = gen.next()) != null) {
                List<Node> W = GraphUtils.asList(choice, adjNodes);

                double[] r = cci.residuals(name(Y), names(W));

                boolean indep = testIndependence(W, r);

                System.out.println("\tW = " + W + " indep = " + (indep) + " p = " + q);

                if (indep) {
                    bestParents = W;
                }
            }

            cond.put(Y, bestParents);
        }

        for (Node Y : graph.getNodes()) {
            System.out.println("Y = " + Y + " cont = " + cond.get(Y));

            for (Node X : cond.get(Y)) {
                System.out.println("\tcond " + X + " = " + cond.get(X));

                if (!cond.get(X).contains(Y)) {
                    graph.setEndpoint(X, Y, Endpoint.ARROW);
                }
            }
        }

//        graph = DataGraphUtils.bidirectedToUndirected(graph);

        return graph;
    }

    public Graph mooij(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);
//        graph.fullyConnect(Endpoint.TAIL);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        List<Node> X = graph.getNodes();
        int d = X.size();

        Set<Integer> S = new HashSet<Integer>();
        List<Integer> sigma = new ArrayList<Integer>();
        for (int i = 0; i < d; i++) S.add(i);
        for (int i = 0; i < d; i++) sigma.add(-1);

        for (int j = d - 1; j >= 0; j--) {
            double pMax = Double.NEGATIVE_INFINITY;
            int iMax = -1;

            for (int i : S) {
                Node x = X.get(i);

                System.out.println("Node " + x);

//                if (!graph.isAdjacentTo(x, X.get(j))) continue;

                List<Node> T = graph.getAdjacentNodes(x);

                for (int m = 0; m < d; m++) {
                    if (sigma.get(m) != -1) {
                        T.remove(X.get(sigma.get(m)));
                    }
                }

//                List<Node> T = new ArrayList<Node>();
//
//                for (int t : S) {
//                    if (t != i) continue;
//                    T.add(X.get(t));
//                }

                double[] r = cci.residuals(name(x), names(T));
                boolean indep = testIndependence(T, r);
//                System.out.println("indep = " + indep);

                System.out.println(q + " " + indep);

                if (!indep) continue;

                if (q > pMax) {
                    pMax = q;
                    iMax = i;
                }

                System.out.println(X.get(i) + " q = " + q + " iMax = " + iMax);
                System.out.println("sigma = " + sigma);
            }

            sigma.set(j, iMax);
            S.remove(new Integer(iMax));
        }

        System.out.println("Sigma = " + sigma);
//        graph.fullyConnect(Endpoint.TAIL);

        Map<Node, List<Node>> parents = new HashMap<Node, List<Node>>();

        for (int j = d - 1; j >= 0; j--) {
            int i = sigma.get(j);

            if (i == -1) continue;

            List<Integer> pa = new ArrayList<Integer>();

            for (int k = 0; k < j; k++) {
                int e = sigma.get(k);

                if (e == -1) continue;

                pa.add(e);
            }

            for (int _pa : new ArrayList<Integer>(pa)) {
                if (!graph.isAdjacentTo(X.get(_pa), X.get(i))) pa.remove(new Integer(_pa));
            }

//            for (int k = 0; k < j - 1; k++) {
//                List<Node> T = new ArrayList<Node>();
//
//                for (int t : pa) {
//                    if (t != sigma.get(k)) T.add(X.get(t));
//                }
//
//
//                double[] r = cci.residuals(X.get(i), T);
//
//                if (testIndependence(T, r)) {
//                    pa.remove(sigma.get(k));
//                }
//            }

            List<Node> _parents = new ArrayList<Node>();

            for (int h : pa) {
                _parents.add(X.get(h));
            }

            parents.put(X.get(i), _parents);
        }

        Graph _graph = new EdgeListGraph(X);

        for (Node x : parents.keySet()) {
            for (Node y : parents.get(x)) {
                _graph.addDirectedEdge(y, x);
            }
        }

        return _graph;
    }

    double q;

    private boolean testIndependence(List<Node> X, double[] y) {
        double minQ = 1.0;
        boolean _indep = true;

        for (Node x : X) {
            double[] _x = column(x);
            boolean indep = cci.independent(_x, y);
            double q = cci.getQ();

//            boolean indep = q > alpha;

            if (q < minQ) minQ = q;
            if (!indep) {
                _indep = false;
                break;
            }
        }

        this.q = minQ;

        return _indep;
    }

    public Graph fullHoyer(Graph graph) {
        graph = GraphUtils.undirectedGraph(graph);

        DagIterator iterator = new DagIterator(graph);
        Graph lastGraph = null;

        while (iterator.hasNext()) {
            Graph g = iterator.next();

            List<double[]> residuals = new ArrayList<double[]>();

            for (Node node : g.getNodes()) {
                double[] r = cci.residuals(name(node), names(g.getParents(node)));
                residuals.add(r);
            }

            boolean allIndep = true;

            LOOP:
            for (int i = 0; i < residuals.size(); i++) {
                for (int j = i + 1; j < residuals.size(); j++) {
                    if (!cci.independent(residuals.get(i), residuals.get(j))) {
                        allIndep = false;
                        break LOOP;
                    }
                }
            }

            if (allIndep) {
                System.out.println(g);
                lastGraph = g;
            }
        }

        return lastGraph;
    }

    private List<String> names(List<Node> nodes) {
        List<String> names = new ArrayList<String>();
        for (Node node : nodes) names.add(name(node));
        return names;
    }
}



