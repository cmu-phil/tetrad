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

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Created by josephramsey on 6/4/15.
 */
public class MisclassificationUtils {
    public static String edgeMisclassificationsJustCounts(Graph dag, Graph estGraph, Graph trueGraph) {
        StringBuilder builder = new StringBuilder();

        int[][] counts = edgeMisclassificationCountsB(trueGraph, estGraph);
        builder.append(edgeMisclassifications(counts));

//        int shd = DataGraphUtils.structuralHammingDistance(trueGraph, estGraph);
//        builder.append("\nSHD = " + shd);

        if (dag != null) {
            builder.append(printCorrectArrows(dag, estGraph, trueGraph));
            builder.append(printCorrectTails(dag, estGraph, trueGraph));
        }

        return builder.toString();
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

    private static int[][] edgeMisclassificationCountsB(Graph leftGraph, Graph topGraph) {
        if (false) {
            return edgeMisclassificationCounts1(leftGraph, topGraph);
        }

        leftGraph = GraphUtils.replaceNodes(leftGraph, topGraph.getNodes());

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

    private static int[][] edgeMisclassificationCounts1(Graph leftGraph, Graph topGraph) {
        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

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

        System.out.println("# edges in true graph = " + leftGraph.getNumEdges());
        System.out.println("# edges in est graph = " + topGraph.getNumEdges());

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

    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        Endpoint e1 = edgeTop.getEndpoint1();
        Endpoint e2 = edgeTop.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edgeTop type : " + e1 + " " + e2);
    }

    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        Endpoint e1 = edgeLeft.getEndpoint1();
        Endpoint e2 = edgeLeft.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 3;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 5;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 4;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
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

    private static String printCorrectArrows(Graph dag, Graph estGraph, Graph truePag) {
        StringBuilder b = new StringBuilder();

        truePag = GraphUtils.replaceNodes(truePag, dag.getNodes());
        estGraph = GraphUtils.replaceNodes(estGraph, dag.getNodes());

        int correctArrows = 0;
        int totalEstimatedArrows = 0;
        int totalTrueArrows = 0;
        int correctNonAncestorRelationships = 0;

        double[] stats = new double[6];

        for (Edge edge : estGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.ARROW) {
                if (!dag.isAncestorOf(x, y)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }

            if (ey == Endpoint.ARROW) {
                if (!dag.isAncestorOf(y, x)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.ARROW) {
                totalTrueArrows++;
            }

            if (ey == Endpoint.ARROW) {
                totalTrueArrows++;
            }
        }

        b.append("\n");
        b.append("\n# correct arrows = " + correctArrows);
        b.append("\n# total estimated arrows = " + totalEstimatedArrows);
        b.append("\n# correct arrow nonancestor relationships = " + correctNonAncestorRelationships);
        b.append("\n# total true arrows = " + totalTrueArrows);

        b.append("\n");
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctArrows / (double) totalEstimatedArrows;
        b.append("\nArrow precision = " + nf.format(precision));
        final double recall = correctArrows / (double) totalTrueArrows;
        b.append("\nArrow recall = " + nf.format(recall));
        final double proportionCorrectNonAncestorRelationships = correctNonAncestorRelationships /
                (double) totalEstimatedArrows;
        b.append("\nProportion correct arrow nonancestor relationships " + nf.format(proportionCorrectNonAncestorRelationships));

        return b.toString();
    }

    private static String printCorrectTails(Graph dag, Graph estGraph, Graph truePag) {
        StringBuilder b = new StringBuilder();

        truePag = GraphUtils.replaceNodes(truePag, dag.getNodes());
        estGraph = GraphUtils.replaceNodes(estGraph, dag.getNodes());

        int correctTails = 0;
        int correctAncestorRelationships = 0;
        int totalEstimatedTails = 0;
        int totalTrueTails = 0;

        double[] stats = new double[6];

        for (Edge edge : estGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.TAIL) {
                if (dag.isAncestorOf(x, y)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }

            if (ey == Endpoint.TAIL) {
                if (dag.isAncestorOf(y, x)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.TAIL) {
                totalTrueTails++;
            }

            if (ey == Endpoint.TAIL) {
                totalTrueTails++;
            }
        }

        b.append("\n");
        b.append("\n# correct tails = " + correctTails);
        b.append("\n# total estimated tails = " + totalEstimatedTails);
        b.append("\n# correct tail ancestor relationships = " + correctAncestorRelationships);
        b.append("\n# total true tails = " + totalTrueTails);

        b.append("\n");
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctTails / (double) totalEstimatedTails;
        b.append("\nTail precision = " + nf.format(precision));
        final double recall = correctTails / (double) totalTrueTails;
        b.append("\nTail recall = " + nf.format(recall));
        final double proportionCorrectAncestorRelationships = correctAncestorRelationships /
                (double) totalEstimatedTails;
        b.append("\nProportion correct tail ancestor relationships " + nf.format(proportionCorrectAncestorRelationships));

        return b.toString();
    }

    public static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
    }

    //This removes the latent nodes in G and connects nodes that were formerly
    //adjacent to the latent node with an undirected edge (edge type doesnt matter).
    private static Graph removeLatent(Graph g) {
        Graph result = new EdgeListGraph(g);
        result.setGraphConstraintsChecked(false);

        List<Node> allNodes = g.getNodes();
        LinkedList<Node> toBeRemoved = new LinkedList<Node>();

        for (Node curr : allNodes) {
            if (curr.getNodeType() == NodeType.LATENT) {
                List<Node> adj = result.getAdjacentNodes(curr);

                for (int i = 0; i < adj.size(); i++) {
                    Node a = adj.get(i);
                    for (int j = i + 1; j < adj.size(); j++) {
                        Node b = adj.get(j);

                        if (!result.isAdjacentTo(a, b)) {
                            result.addEdge(Edges.undirectedEdge(a, b));
                        }
                    }
                }

                toBeRemoved.add(curr);
            }
        }

        result.removeNodes(toBeRemoved);
        return result;
    }

    public static Set<Edge> convertNodes(Set<Edge> edges, List<Node> newVariables) {
        Set<Edge> newEdges = new HashSet<Edge>();
        Graph convertedGraph = new EdgeListGraph(newVariables);

        for (Edge edge : edges) {
            Node node1 = convertedGraph.getNode(edge.getNode1().getName());
            Node node2 = convertedGraph.getNode(edge.getNode2().getName());

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
            newEdges.add(newEdge);
        }

        return newEdges;
    }

    public static String endpointMisclassification(Graph estGraph, Graph refGraph) {
        List<Node> _nodes = refGraph.getNodes();
        estGraph = GraphUtils.replaceNodes(estGraph, _nodes);
        refGraph = GraphUtils.replaceNodes(refGraph, _nodes);

        _nodes = estGraph.getNodes();

        int[][] counts = new int[4][4];

        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = 0; j < _nodes.size(); j++) {
                if (i == j) continue;

                Endpoint endpoint1 = refGraph.getEndpoint(_nodes.get(i), _nodes.get(j));
                Endpoint endpoint2 = estGraph.getEndpoint(_nodes.get(i), _nodes.get(j));

                int index1 = getIndex(endpoint1);
                int index2 = getIndex(endpoint2);

                counts[index1][index2]++;
            }
        }

        TextTable table2 = new TextTable(5, 5);

        table2.setToken(0, 1, "-o");
        table2.setToken(0, 2, "->");
        table2.setToken(0, 3, "--");
        table2.setToken(0, 4, "NULL");
        table2.setToken(1, 0, "-o");
        table2.setToken(2, 0, "->");
        table2.setToken(3, 0, "--");
        table2.setToken(4, 0, "NULL");

        int sum = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) continue;
                else sum += counts[i][j];
            }
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) table2.setToken(i + 1, j + 1, "*");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();

        //        println("\n" + name);
        //        println(table2.toString());
        //        println("");
    }

    public static String edgeMisclassifications(Graph dag, Graph estGraph, Graph trueGraph) {
        if (true) {
            return edgeMisclassificationsJustCounts(dag, estGraph, trueGraph);
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder builder = new StringBuilder();

        Node a = new GraphNode("a");
        Node b = new GraphNode("b");

        List<Edge> trueEdgeTypes = new ArrayList<Edge>();

        trueEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.TAIL));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.CIRCLE));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.TAIL));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.NULL, Endpoint.NULL));

        List<Edge> estEdgeTypes = new ArrayList<Edge>();

        estEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.TAIL));
        estEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
        estEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.ARROW));
        estEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
        estEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.ARROW));
        estEdgeTypes.add(new Edge(a, b, Endpoint.NULL, Endpoint.NULL));

        int[][] counts = new int[8][6];
        Graph graph = new EdgeListGraph(trueGraph.getNodes());
        graph.fullyConnect(Endpoint.TAIL);

        for (int m = 0; m < 8; m++) {
            for (int n = 0; n < 6; n++) {
                for (Edge fullEdge : graph.getEdges()) {
                    if (m == 3 || m == 5) {
                        Node x = fullEdge.getNode1();
                        Node y = fullEdge.getNode2();

                        Edge true1 = trueGraph.getEdge(x, y);
                        if (true1 == null) true1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);
                        true1 = true1.reverse();

                        Edge est1 = estGraph.getEdge(x, y);
                        if (est1 == null) est1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);

                        Edge trueEdgeType = trueEdgeTypes.get(m);
                        Edge estEdgeType = estEdgeTypes.get(n);

                        Edge trueConvert = new Edge(x, y, trueEdgeType.getEndpoint1(), trueEdgeType.getEndpoint2());
                        Edge estConvert = new Edge(x, y, estEdgeType.getEndpoint1(), estEdgeType.getEndpoint2());

                        boolean equals = true1.equals(trueConvert) && est1.equals(estConvert);// && true1.equals(est1);
                        if (equals) counts[m][n]++;
                    } else {
                        Node x = fullEdge.getNode1();
                        Node y = fullEdge.getNode2();

                        Edge true1 = trueGraph.getEdge(x, y);
                        if (true1 == null) true1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);

                        Edge est1 = estGraph.getEdge(x, y);
                        if (est1 == null) est1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);

                        Edge trueEdgeType = trueEdgeTypes.get(m);
                        Edge estEdgeType = estEdgeTypes.get(n);

                        Edge trueConvert = new Edge(x, y, trueEdgeType.getEndpoint1(), trueEdgeType.getEndpoint2());
                        Edge estConvert = new Edge(x, y, estEdgeType.getEndpoint1(), estEdgeType.getEndpoint2());

                        boolean equals = true1.equals(trueConvert) && est1.equals(estConvert);// && true1.equals(est1);
                        if (equals) counts[m][n]++;
                    }
                }
            }
        }

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "null");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "null");

        // Need the sum of cells except the null-null cell.
        int sum = 0;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) continue;
                sum += counts[i][j];
            }
        }

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else table2.setToken(i + 1, j + 1, nf.format(counts[i][j] / (double) sum));
            }
        }

        builder.append("\n" + table2.toString());

        //        println("\n" + name);
        //        println(table2.toString());
        //        println("");

        TextTable table3 = new TextTable(3, 3);

        table3.setToken(1, 0, "Non-Null");
        table3.setToken(2, 0, "Null");
        table3.setToken(0, 1, "Non-Null");
        table3.setToken(0, 2, "Null");

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 5; j++) {
                if (i == 6 && j == 4) table2.setToken(i + 1, j + 1, "*");
                else table2.setToken(i + 1, j + 1, nf.format(counts[i][j] / (double) sum));
            }
        }


        int[][] _counts = new int[2][2];
        int _sum = 0;

        for (int i = 0; i < 7; i++) {
            _sum += counts[i][0];
        }

        _counts[1][0] = _sum;
        _sum = 0;

        for (int i = 0; i < 5; i++) {
            _sum += counts[0][i];
        }

        _counts[0][1] = _sum;
        _sum = 0;

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) {
                _sum += counts[i][j];
            }
        }

        _counts[0][0] = _sum;

        _counts[1][1] = counts[7][5];

        // Now we need the sum of all cells.
        sum = 0;

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                sum += _counts[i][j];
            }
        }

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                table3.setToken(i + 1, j + 1, nf.format(_counts[i][j] / (double) sum));
            }
        }

        //        out.println("Null\n");
        //
        builder.append("\n" + table3);

        return builder.toString();
    }
}

