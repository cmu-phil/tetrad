///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TextTable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Some utilities for generating misclassification tables for graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MisclassificationUtils {

    /**
     * <p>Constructor for MisclassificationUtils.</p>
     */
    private MisclassificationUtils() {
    }

    /**
     * <p>getIndex.</p>
     *
     * @param endpoint a {@link edu.cmu.tetrad.graph.Endpoint} object
     * @return a int
     */
    public static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
    }

    /**
     * <p>convertNodes.</p>
     *
     * @param edges        a {@link java.util.Set} object
     * @param newVariables a {@link java.util.List} object
     * @return a {@link java.util.Set} object
     */
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

            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();
            Edge newEdge = new Edge(node1, node2, endpoint1, endpoint2);
            newEdges.add(newEdge);
        }

        return newEdges;
    }

    /**
     * <p>endpointMisclassification.</p>
     *
     * @param estGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param refGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
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

                int index1 = MisclassificationUtils.getIndex(endpoint1);
                int index2 = MisclassificationUtils.getIndex(endpoint2);

                counts[index1][index2]++;
            }
        }

        TextTable table2 = new TextTable(5, 5);

        table2.setToken(0, 1, "-o");
        table2.setToken(0, 2, "->");
        table2.setToken(0, 3, "--");
        table2.setToken(0, 4, "no endpoint");
        table2.setToken(1, 0, "-o");
        table2.setToken(2, 0, "->");
        table2.setToken(3, 0, "--");
        table2.setToken(4, 0, "no endpoint");

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) table2.setToken(3 + 1, 3 + 1, "*");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();
    }

    /**
     * <p>edgeMisclassifications.</p>
     *
     * @param estGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param refGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
    public static String edgeMisclassifications(Graph estGraph, Graph refGraph) {
        estGraph = GraphUtils.replaceNodes(estGraph, refGraph.getNodes());

        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "no edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "no edge");

        int[][] counts = new int[8][6];

        for (Edge est1 : estGraph.getEdges()) {
            Node x = est1.getNode1();
            Node y = est1.getNode2();

            Edge true1 = refGraph.getEdge(x, y);

            if (true1 == null) {
                true1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);
            }

            Edge trueConvert = new Edge(x, y, true1.getEndpoint(x), true1.getEndpoint(y));

            int m = MisclassificationUtils.getTypeLeft(trueConvert, est1);
            int n = MisclassificationUtils.getTypeTop(est1);

            counts[m][n]++;
        }

        for (Edge true1 : refGraph.getEdges()) {
            Node x = true1.getNode1();
            Node y = true1.getNode2();

            Edge est1 = estGraph.getEdge(x, y);

            if (est1 == null) {
                est1 = new Edge(x, y, Endpoint.NULL, Endpoint.NULL);
            }

            Edge estConvert = new Edge(x, y, est1.getEndpoint(x), est1.getEndpoint(y));

            int m = MisclassificationUtils.getTypeLeft(true1, estConvert);
            int n = MisclassificationUtils.getTypeTop(estConvert);

            if (n == 5) {
                counts[m][5]++;
            }
        }

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) table2.setToken(7 + 1, 5 + 1, "*");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        builder.append("\n").append(table2);

        return builder.toString();
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

        return 5;

//        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
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

        if (e1 == Endpoint.NULL && e2 == Endpoint.NULL) {
            return 7;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
    }

}


