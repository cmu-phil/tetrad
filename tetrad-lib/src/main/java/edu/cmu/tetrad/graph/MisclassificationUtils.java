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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MisclassificationUtils {
    private static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
    }

    public static Set<Edge> convertNodes(Set<Edge> edges, List<Node> newVariables) {
        Set<Edge> newEdges = new HashSet<>();
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

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) table2.setToken(i + 1, j + 1, "*");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();
    }

    public static String edgeMisclassifications(Graph estGraph, Graph trueGraph) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder builder = new StringBuilder();

        Node a = new GraphNode("a");
        Node b = new GraphNode("b");

        List<Edge> trueEdgeTypes = new ArrayList<>();

        trueEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.TAIL));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.CIRCLE));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.TAIL));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.ARROW, Endpoint.ARROW));
        trueEdgeTypes.add(new Edge(a, b, Endpoint.NULL, Endpoint.NULL));

        List<Edge> estEdgeTypes = new ArrayList<>();

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

        builder.append("\n").append(table2.toString());

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
        builder.append("\n").append(table3);

        return builder.toString();
    }
}

