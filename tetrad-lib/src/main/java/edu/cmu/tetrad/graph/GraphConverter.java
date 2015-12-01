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

import java.util.StringTokenizer;

/**
 * Converts graphs used in the search package from one type to another.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 */
public final class GraphConverter {

    /**
     * Converts a string spec of a graph--for example, "X1-->X2, X1---X3,
     * X2o->X4, X3<->X4" to a Graph. The spec consists of a comma separated list
     * of edge specs of the forms just used in the previous sentence.
     * Unconnected nodes may be listed separately--example: "X,Y->Z". To specify
     * a node as latent, use "Latent()." Example: "Latent(L1),Y->L1".
     */
    public static Graph convert(String spec) {
        Graph graph = new EdgeListGraph();
        StringTokenizer st1, st2;

        for (st1 = new StringTokenizer(spec, ", "); st1.hasMoreTokens(); ) {
            String edgeSpec = st1.nextToken();

            st2 = new StringTokenizer(edgeSpec, "<>-o ");

            String var1 = st2.nextToken();

            if (var1.startsWith("Latent(")) {
                String latentName =
                        (String) var1.subSequence(7, var1.length() - 1);
                GraphNode node = new GraphNode(latentName);
                node.setNodeType(NodeType.LATENT);
                graph.addNode(node);
                continue;
            }

            if (!st2.hasMoreTokens()) {
                graph.addNode(new GraphNode(var1));
                continue;
            }

            String var2 = st2.nextToken();

            if (graph.getNode(var1) == null) {
                graph.addNode(new GraphNode(var1));
            }

            if (graph.getNode(var2) == null) {
                graph.addNode(new GraphNode(var2));
            }

            Node nodeA = graph.getNode(var1);
            Node nodeB = graph.getNode(var2);
            Edge edge = graph.getEdge(nodeA, nodeB);

            if (edge != null) {
                throw new IllegalArgumentException(
                        "Multiple edges connecting " +
                                "nodes is not supported.");
            }

            if (edgeSpec.lastIndexOf("-->") != -1) {
                graph.addDirectedEdge(nodeA, nodeB);
            }

            if (edgeSpec.lastIndexOf("<--") != -1) {
                graph.addDirectedEdge(nodeB, nodeA);
            } else if (edgeSpec.lastIndexOf("---") != -1) {
                graph.addUndirectedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("<->") != -1) {
                graph.addBidirectedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("o->") != -1) {
                graph.addPartiallyOrientedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("<-o") != -1) {
                graph.addPartiallyOrientedEdge(nodeB, nodeA);
            } else if (edgeSpec.lastIndexOf("o-o") != -1) {
                graph.addNondirectedEdge(nodeB, nodeA);
            }
        }

        return graph;
    }
}





