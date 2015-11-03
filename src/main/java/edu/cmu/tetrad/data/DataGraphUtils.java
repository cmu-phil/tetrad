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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Sundry graph utils that need to be located in the data package to
 * avoid package cycles.
 */
public class DataGraphUtils {
    public static Graph randomSingleFactorModel(int numStructuralNodes,
                                                int numStructuralEdges, int numMeasurementsPerLatent,
                                                int numLatentMeasuredImpureParents,
                                                int numMeasuredMeasuredImpureParents,
                                                int numMeasuredMeasuredImpureAssociations) {

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numStructuralNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag;

        do {
//            dag = DataGraphUtils.randomGraphUniform(numStructuralNodes, numStructuralNodes, numStructuralEdges, 4, 3, 3, false);
            dag = edu.cmu.tetrad.graph.GraphUtils.randomGraphRandomForwardEdges(vars, 0, numStructuralEdges);
        } while (dag.getNumEdges() != numStructuralEdges);

        Graph graph = new EdgeListGraph(dag);

        return randomMim(graph, numMeasurementsPerLatent,
                numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents, numMeasuredMeasuredImpureAssociations,
                true);

    }

    public static Graph randomMim(Graph graph, int numMeasurementsPerLatent,
                                  int numLatentMeasuredImpureParents,
                                  int numMeasuredMeasuredImpureParents,
                                  int numMeasuredMeasuredImpureAssociations, boolean arrangeGraph) {
        Graph graph1 = new EdgeListGraph(graph);
//        Graph graph1 = graph;

        List<Node> latents = graph1.getNodes();

        for (int i = 0; i < latents.size(); i++) {
            Node latent = latents.get(i);
            latent.setNodeType(NodeType.LATENT);

            if (!(latent.getNodeType() == NodeType.LATENT)) {
                throw new IllegalArgumentException("Expected latent.");
            }

            String newName = "L." + latent.getName();
            ((EdgeListGraph) graph1).changeName(latent.getName(), newName);
//            latent.setName(newName);
        }

        int measureIndex = 0;

        for (Object latent1 : latents) {
            Node latent = (Node) latent1;

            for (int j = 0; j < numMeasurementsPerLatent; j++) {
                Node measurement = new GraphNode("X" + (++measureIndex));
                graph1.addNode(measurement);
                graph1.addDirectedEdge(latent, measurement);
            }
        }

        // Latent-->measured.
        int misses = 0;

        for (int i = 0; i < numLatentMeasuredImpureParents; i++) {
            if (misses > 10) {
                break;
            }

            int j = RandomUtil.getInstance().nextInt(latents.size());
            Node latent = latents.get(j);
            List<Node> nodes = graph1.getNodes();
            List<Node> measures = graph1.getNodesOutTo(latent, Endpoint.ARROW);
            measures.removeAll(latents);
            nodes.removeAll(latents);
            nodes.removeAll(measures);

            if (nodes.isEmpty()) {
                i--;
                misses++;
                continue;
            }

            int k = RandomUtil.getInstance().nextInt(nodes.size());
            Node measure = nodes.get(k);

            if (graph1.getEdge(latent, measure) != null ||
                    graph1.isAncestorOf(measure, latent)) {
                i--;
                misses++;
                continue;
            }

            // These can't create cycles.
            graph1.addDirectedEdge(latent, measure);

//            System.out.println("Latent to  measured: " + graph.getEdge(latent,  measure));
        }

        // Measured-->measured.
        misses = 0;

        for (int i = 0; i < numMeasuredMeasuredImpureParents; i++) {
            if (misses > 10) {
                break;
            }

            int j = RandomUtil.getInstance().nextInt(latents.size());
            Node latent = latents.get(j);
            List<Node> nodes = graph1.getNodes();
            List<Node> measures = graph1.getNodesOutTo(latent, Endpoint.ARROW);
            measures.removeAll(latents);

            if (measures.isEmpty()) {
                i--;
                misses++;
                continue;
            }

            int m = RandomUtil.getInstance().nextInt(measures.size());
            Node measure1 = measures.get(m);

            nodes.removeAll(latents);
            nodes.removeAll(measures);

            if (nodes.isEmpty()) {
                i--;
                misses++;
                continue;
            }

            int k = RandomUtil.getInstance().nextInt(nodes.size());
            Node measure2 = nodes.get(k);

            if (graph1.getEdge(measure1, measure2) != null ||
                    graph1.isAncestorOf(measure2, measure1)) {
                i--;
                misses++;
                continue;
            }

            graph1.addDirectedEdge(measure1, measure2);
//            System.out.println("Measure to  measure: " + graph.getEdge(measure1,  measure2));
        }

        // Measured<->measured.
        misses = 0;

        for (int i = 0; i < numMeasuredMeasuredImpureAssociations; i++) {
            if (misses > 10) {
                break;
            }

            int j = RandomUtil.getInstance().nextInt(latents.size());
            Node latent = latents.get(j);
            List<Node> nodes = graph1.getNodes();
            List<Node> measures = graph1.getNodesOutTo(latent, Endpoint.ARROW);
            measures.removeAll(latents);

            if (measures.isEmpty()) {
                i--;
                misses++;
                continue;
            }

            int m = RandomUtil.getInstance().nextInt(measures.size());
            Node measure1 = measures.get(m);

            nodes.removeAll(latents);
            nodes.removeAll(measures);

            if (nodes.isEmpty()) {
                i--;
                misses++;
                continue;
            }

            int k = RandomUtil.getInstance().nextInt(nodes.size());
            Node measure2 = nodes.get(k);

            if (graph1.getEdge(measure1, measure2) != null) {
                i--;
                misses++;
                continue;
            }

            graph1.addBidirectedEdge(measure1, measure2);
//            System.out.println("Bidirected: " + graph.getEdge(measure1, measure2));
        }

        if (arrangeGraph) {
            edu.cmu.tetrad.graph.GraphUtils.circleLayout(graph1, 200, 200, 150);
            edu.cmu.tetrad.graph.GraphUtils.fruchtermanReingoldLayout(graph1);
        }

        return graph1;
    }

    /**
     * First a random single factor model is created with the specified number of latent nodes and latent
     * edges, and impurity structure. Then this is converted to a bifactor model by adding new latents and
     * edges.
     */
    public static Graph randomBifactorModel(int numStructuralNodes,
                                            int numStructuralEdges, int numMeasurementsPerLatent,
                                            int numLatentMeasuredImpureParents,
                                            int numMeasuredMeasuredImpureParents,
                                            int numMeasuredMeasuredImpureAssociations) {
        Graph mim = randomSingleFactorModel(numStructuralNodes, numStructuralEdges,
                numMeasurementsPerLatent, numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations);

        List<Node> latents = new ArrayList<Node>();
        List<Node> latents2 = new ArrayList<Node>();

        for (Node node : mim.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
                GraphNode node2 = new GraphNode(node.getName() + "B");
                node2.setNodeType(NodeType.LATENT);
                latents2.add(node2);
                mim.addNode(node2);
            }
        }

        for (int i = 0; i < latents.size(); i++) {
            Node latent = latents.get(i);

            for (Node child : mim.getChildren(latent)) {
                if (child.getNodeType() == NodeType.MEASURED) {
                    mim.addDirectedEdge(latents2.get(i), child);
                } else {
                    int j = latents.indexOf(child);
                    mim.addDirectedEdge(latents2.get(i), latents2.get(j));
                }
            }
        }

        // Connect up all the latents.
//        List<Node> allLatents = new ArrayList<Node>(latents);
//        allLatents.addAll(latents2);
//
//        for (int i = 0; i < allLatents.size(); i++) {
//            for (int j = i + 1; j < allLatents.size(); j++) {
//                if (mim.isAdjacentTo(allLatents.get(i), allLatents.get(j))) {
//                    mim.removeEdge(allLatents.get(i), allLatents.get(j));
//                }
//
//                if (j == i + latents.size()) continue;
//
//                mim.addDirectedEdge(allLatents.get(i), allLatents.get(j));
//            }
//        }

        return mim;
    }
}

