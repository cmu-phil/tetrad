///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds some utility methods for Purify, Build Clusters, and Mimbuild.
 *
 * @author Joseph Ramsey
 */
public final class MimUtils {

    public static Clusters convertToClusters(Graph clusterGraph) {
        List<Node> measuredVariables = new ArrayList<>();

        for (Node node : clusterGraph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                measuredVariables.add(node);
            }
        }

        return MimUtils.convertToClusters(clusterGraph, measuredVariables);
    }

    public static List<List<Node>> convertToClusters2(Graph clusterGraph) {
        Clusters clusters = MimUtils.convertToClusters(clusterGraph);

        List<List<Node>> _clusters = new ArrayList<>();

        for (int i = 0; i < clusters.getNumClusters(); i++) {
            List<Node> cluster = new ArrayList<>();
            List<String> cluster1 = clusters.getCluster(i);

            for (String s : cluster1) {
                cluster.add(clusterGraph.getNode(s));
            }

            _clusters.add(cluster);
        }

        return _clusters;
    }

    /**
     * Converts a disconnected multiple indicator model into a set of clusters. Assumes the given graph contains a
     * number of latents Li, i = 0,...,n-1, for each of which there is a list of indicators Wj, j = 0,...,m_i-1, such
     * that , Li--&gt;Wj. Returns a Clusters object mapping i to Wj. The name for cluster i is set to Li.
     */
    public static Clusters convertToClusters(Graph clusterGraph, List<Node> measuredVariables) {
        List<String> latents = new ArrayList<>();
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
}





