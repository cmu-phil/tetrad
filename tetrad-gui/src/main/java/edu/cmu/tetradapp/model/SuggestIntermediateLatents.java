/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;

import java.io.Serial;
import java.util.*;

/**
 * Suggests intermediate latents for cartesian edge products of actually or potentially directed edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SuggestIntermediateLatents extends GraphWrapper implements DoNotAddOldModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for MagInPagWrapper.</p>
     *
     * @param source     a {@link GraphSource} object
     * @param parameters a {@link Parameters} object
     */
    public SuggestIntermediateLatents(GraphSource source, Parameters parameters) {
        this(source.getGraph());
    }

    /**
     * <p>Constructor for MagInPagWrapper.</p>
     *
     * @param graph a {@link Graph} object
     */
    public SuggestIntermediateLatents(Graph graph) {
        super(SuggestIntermediateLatents.getGraph(graph), "Choose Zhang MAG in PAG.");
        String message = getGraph() + "";
        TetradLogger.getInstance().log(message);
    }

    private static Graph getGraph(Graph graph) {
        graph = new EdgeListGraph(graph);

        Map<Set<Node>, Set<Node>> cartesianProducts = new HashMap<>();

        Graph possiblyDirected = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (edge.pointsTowards(edge.getNode2())) {
                possiblyDirected.addDirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        for (Node x : possiblyDirected.getNodes()) {
            Set<Node> possibleChildren = new HashSet<>(possiblyDirected.getChildren(x));
            Set<Node> possibleParents = new HashSet<>();

            for (Node p : possibleChildren) {
                possibleParents.addAll(possiblyDirected.getParents(p));
            }

            List<Node> _possibleParents = new ArrayList<>(possibleParents);
            List<Node> _possibleChildren = new ArrayList<>(possibleChildren);

            SublistGenerator gen1 = new SublistGenerator(_possibleParents.size(), _possibleParents.size());
            int[] choice1;

            W:
            while ((choice1 = gen1.next()) != null) {
                List<Node> a1 = GraphUtils.asList(choice1, _possibleParents);
                List<Node> comp1 = new ArrayList<>(_possibleParents);
                comp1.removeAll(a1);
                if (comp1.size() < 2) {
                    continue;
                }

                SublistGenerator gen2 = new SublistGenerator(_possibleChildren.size(), _possibleChildren.size());
                int[] choice2;

                C:
                while ((choice2 = gen2.next()) != null) {
                    List<Node> a2 = GraphUtils.asList(choice2, _possibleChildren);
                    List<Node> comp2 = new ArrayList<>(_possibleChildren);
                    comp2.removeAll(a2);
                    if (comp2.size() < 2) {
                        continue;
                    }

                    for (Node p : comp1) {
                        for (Node c : comp2) {
                            Edge e = possiblyDirected.getEdge(p, c);

                            if (e == null) {
                                continue C;
                            }
                        }
                    }

                    cartesianProducts.put(new HashSet<>(comp1), new HashSet<>(comp2));
                    break W;
                }
            }
        }

        int latentCounter = 1;
        
        for (Set<Node> parents : cartesianProducts.keySet()) {
            Set<Node> children = cartesianProducts.get(parents);

            if (parents.size() > 1 && children.size() > 1) {
                GraphNode newNode = new GraphNode("L" + latentCounter++);
                newNode.setNodeType(NodeType.LATENT);
                graph.addNode(newNode);

                for (Node p : parents) {
                    for (Node c : children) {
                        graph.removeEdge(p, c);

                        graph.addDirectedEdge(p, newNode);
                        graph.addDirectedEdge(newNode, c);
                    }
                }

                float avgx = 0f;
                float avgy = 0f;
                int count = 0;

                for (Node p : parents) {
                    avgx += p.getCenterX();
                    avgy += p.getCenterY();
                    count++;
                }

                for (Node c : children) {
                    avgx += c.getCenterX();
                    avgy += c.getCenterY();
                    count++;
                }

                avgx /= count;
                avgy /= count;

                newNode.setCenter((int) avgx, (int) avgy);
            }
        }

        return graph;
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link SuggestIntermediateLatents} object
     */
    public static SuggestIntermediateLatents serializableInstance() {
        return new SuggestIntermediateLatents(EdgeListGraph.serializableInstance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}




