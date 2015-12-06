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

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.graph.*;

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Holds some utility methods for Purify, Build Clusters, and Mimbuild.
 *
 * @author Joseph Ramsey
 */
public final class MimUtils {

    public static Clusters convertToClusters(Graph clusterGraph) {
        List<Node> measuredVariables = new ArrayList<Node>();

        for (Node node : clusterGraph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                measuredVariables.add(node);
            }
        }

        return convertToClusters(clusterGraph, measuredVariables);
    }

    public static List<List<Node>> convertToClusters2(Graph clusterGraph) {
        Clusters clusters = convertToClusters(clusterGraph);

        List<List<Node>> _clusters = new ArrayList<List<Node>>();

        for (int i = 0; i < clusters.getNumClusters(); i++) {
            List<Node> cluster = new ArrayList<Node>();
            List<String> cluster1 = clusters.getCluster(i);

            for (int j = 0; j < cluster1.size(); j++) {
                cluster.add(clusterGraph.getNode(cluster1.get(j)));
            }

            _clusters.add(cluster);
        }

        return _clusters;
    }

    /**
     * Converts a disconnected multiple indicator model into a set of clusters. Assumes the given graph contains a
     * number of latents Li, i = 0,...,n-1, for each of which there is a list of indicators Wj, j = 0,...,m_i-1, such
     * that , Li-->Wj. Returns a Clusters object mapping i to Wj. The name for cluster i is set to Li.
     */
    public static Clusters convertToClusters(Graph clusterGraph, List<Node> measuredVariables) {
        List<String> latents = new ArrayList<String>();
        Clusters clusters = new Clusters();
        clusterGraph = GraphUtils.replaceNodes(clusterGraph, measuredVariables);

        for (Node node : clusterGraph.getNodes()) {
            if (!measuredVariables.contains(node)) {
                latents.add(node.getName());
            }
        }

        Collections.sort(latents);

        for (int i = 0; i < latents.size(); i++) {
            String name = latents.get(i);
            clusters.setClusterName(i, name);
            Node latent = clusterGraph.getNode(name);
            List<Node> measured =
                    clusterGraph.getNodesOutTo(latent, Endpoint.ARROW);

            for (Node _node : measured) {
                if (measuredVariables.contains(_node)) {
                    clusters.addToCluster(i, _node.getName());
                }
            }
        }

        return clusters;
    }

    /**
     * @throws Exception if the graph cannot be cloned properly due to a serialization problem.
     */
    public static Graph extractStructureGraph(Graph clusterGraph)
            throws Exception {
        Set<Edge> edges = clusterGraph.getEdges();
        Graph structureGraph = new EdgeListGraph();

        for (Edge edge : edges) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (node1.getNodeType() == NodeType.LATENT) {
                if (!structureGraph.containsNode(node1)) {
                    structureGraph.addNode(node1);
                }
            }

            if (node2.getNodeType() == NodeType.LATENT) {
                if (!structureGraph.containsNode(node2)) {
                    structureGraph.addNode(node2);
                }
            }

            if (node1.getNodeType() == NodeType.LATENT &&
                    node2.getNodeType() == NodeType.LATENT) {
                structureGraph.addEdge(edge);
            }
        }

        Graph clone = (Graph) new MarshalledObject(structureGraph).get();
        GraphUtils.circleLayout(clone, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(clone);
        return clone;
    }
}





