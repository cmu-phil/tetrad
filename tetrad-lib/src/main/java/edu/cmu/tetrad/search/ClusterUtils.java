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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Some general utilities for dealing with clustering input and output.
 *
 * @author Joseph Ramsey
 */
public class ClusterUtils {
    public static final String LATENT_PREFIX = "_L";


    public static List<int[]> convertListToInt(List<List<Node>> partition, List<Node> nodes) {
        List<int[]> _partition = new ArrayList<>();

        for (List<Node> cluster : partition) {
            int[] _cluster = new int[cluster.size()];

            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    if (nodes.get(k).getName().equals(cluster.get(j).getName())) {
                        _cluster[j] = k;
                    }
                }
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    public static List<List<Node>> convertIntToList(List<int[]> partition, List<Node> nodes) {
        List<List<Node>> _partition = new ArrayList<>();

        for (int[] cluster : partition) {
            List<Node> _cluster = new ArrayList<>();

            for (int aCluster : cluster) {
                _cluster.add(nodes.get(aCluster));
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    public static List<List<Node>> clustersToPartition(Clusters clusters, List<Node> variables) {
        List<List<Node>> inputPartition = new ArrayList<>();

        for (int i = 0; i < clusters.getNumClusters(); i++) {
            List<Node> cluster = new ArrayList<>();

            for (String nodeName : clusters.getCluster(i)) {
                for (Node variable : variables) {
                    if (variable.getName().equals(nodeName)) {
                        cluster.add(variable);
                    }
                }
            }

            inputPartition.add(cluster);
        }

        return inputPartition;
    }

    public static Clusters partitionToClusters(List<List<Node>> partition) {
        Clusters clusters = new Clusters();

        for (int i = 0; i < partition.size(); i++) {
            List<Node> cluster = partition.get(i);

            for (Node aCluster : cluster) {
                clusters.addToCluster(i, aCluster.getName());
            }
        }

        return clusters;
    }

    public static Graph convertSearchGraph(List<int[]> clusters, String[] varNames) {
        List<Node> nodes = new ArrayList<>();

        if (clusters == null) {
            nodes.add(new GraphNode("No_model."));
            return new EdgeListGraph(nodes);
        }

        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            nodes.add(latent);
        }
        for (int[] indicators : clusters) {
            for (int i : indicators) {
                String indicatorName;
                indicatorName = varNames[i];
                Node indicator = new GraphNode(indicatorName);
                nodes.add(indicator);
            }
        }
        Graph graph = new EdgeListGraph(nodes);
        int acc = clusters.size();
        for (int i = 0; i < clusters.size(); i++) {
            int[] indicators = clusters.get(i);
            for (int j = 0; j < indicators.length; j++) {
                graph.setEndpoint(nodes.get(i), nodes.get(acc),
                        Endpoint.ARROW);
                graph.setEndpoint(nodes.get(acc), nodes.get(i),
                        Endpoint.TAIL);
                acc++;
            }
            for (int j = i + 1; j < clusters.size(); j++) {
                graph.setEndpoint(nodes.get(i), nodes.get(j),
                        Endpoint.ARROW);
                graph.setEndpoint(nodes.get(j), nodes.get(i),
                        Endpoint.TAIL);
            }
        }

        return graph;
    }

    public static Clusters mimClusters(Graph mim) {
        List<Node> latents = new ArrayList<>();

        for (Node node : mim.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        Clusters clusters = new Clusters();

        for (int i = 0; i < latents.size(); i++) {
            Node _latent = latents.get(i);
            List<Node> adj = mim.getAdjacentNodes(_latent);
            adj.removeAll(latents);

            clusters.setClusterName(i, _latent.getName());

            for (Node n : adj) {
                clusters.addToCluster(i, n.getName());
            }
        }

        return clusters;

    }

    public static void logClusters(Set<Set<Integer>> clusters, List<Node> variables) {
        int num = 1;
        StringBuilder buf = new StringBuilder();
        buf.append("\nClusters:\n");

        for (Set<Integer> indices : clusters) {
            buf.append(num++).append(": ");

            List<Node> _c = new ArrayList<>();

            for (int i : indices) {
                _c.add(variables.get(i));
            }

            Collections.sort(_c);

            for (Node n : _c) {
                buf.append(n).append(" ");
            }

            buf.append("\n");
        }

        TetradLogger.getInstance().log("clusters", buf.toString());
    }

    public static List<String> generateLatentNames(int total) {
        List<String> output = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            output.add(ClusterUtils.LATENT_PREFIX + (i + 1));
        }
        return output;
    }
}



