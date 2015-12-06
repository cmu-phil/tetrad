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


import edu.cmu.tetrad.util.ChoiceGenerator;
import org.apache.commons.collections4.map.MultiKeyMap;

import java.util.*;

/**
 * Lays out a graph by placing springs between the nodes and letting the system
 * settle (one node at a time).
 *
 * @author Joseph Ramsey
 */
public final class LayeredDrawing {

    /**
     * The graph being laid out.
     */
    private Graph graph;

    //==============================CONSTRUCTORS===========================//

    public LayeredDrawing(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.graph = graph;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        //                List tiers = DataGraphUtils.getTiers(graph);
        List<List<Node>> tiers = placeInTiers(graph);

        int y = 0;

        for (List<Node> tier1 : tiers) {
            y += 60;
            int x = 0;

            for (Node aTier : tier1) {
                x += 90;
                aTier.setCenterX(x);
                aTier.setCenterY(y);
            }
        }
    }

    //============================PRIVATE METHODS=========================//

    private List<List<Node>> placeInTiers(Graph graph) {
        List<List<Node>> connectedComponents =
                GraphUtils.connectedComponents(graph);
        List<List<Node>> tiers = new ArrayList<>();

        for (List<Node> component : connectedComponents) {

            // Recursively map each node to its tier inside the component,
            // starting with the first node. These tiers are relative and
            // can be negative.
            Node firstNode = component.get(0);
            Map<Node, Integer> componentTiers = new HashMap<>();
            placeNodes(firstNode, componentTiers, graph);

            // Reverse the map. The domain of this map is now possibly negative
            // tiers.
            Map reversedMap = new MultiKeyMap();

            for (Node _node : component) {
                Integer _tier = componentTiers.get(_node);
                reversedMap.put(_tier, _node);
            }

            List<Integer> indices = new ArrayList<>(reversedMap.keySet());
            Collections.sort(indices);

            // Add these tiers low to high to the list of all tiers. Note that
            // connected components are appended top to bottom in the list of
            // tiers.
//            int start = tiers.size();

            for (int i : indices) {
                Collection<Node> collection = (Collection<Node>) reversedMap.get(i);
                tiers.add(new ArrayList<>(collection));
            }

            // Do some heuristic uncrossing of edges in successive tiers.
            for (int i = 0; i < tiers.size() - 1; i++) {
                List<Node> tier1 = tiers.get(i);
                List<Node> tier2 = tiers.get(i + 1);

                List<Node> reorderTier2 = new ArrayList<>();

                for (Node node : tier1) {
                    List<Node> adj = graph.getAdjacentNodes(node);
                    adj.retainAll(tier2);
                    adj.removeAll(reorderTier2);
                    reorderTier2.addAll(adj);
                }

//                List<Node> saveArray = new ArrayList<Node>();
//                int saveCrossings = Integer.MAX_VALUE;
//
//                for (int j = 0; j < 4 * tier2.size(); j++) {
//                    Collections.shuffle(tier2);
//                    int numCrossings = numCrossings(tier1, tier2, graph);
//
//                    if (numCrossings < saveCrossings) {
//                        saveArray = new ArrayList<Node>(tier2);
//                        saveCrossings = numCrossings;
//                    }
//                }

                tiers.set(i + 1, reorderTier2);
//                tiers.set(i + 1, saveArray);
            }
        }

        return tiers;
    }

    private int numCrossings(List<Node> tier1, List<Node> tier2, Graph graph) {
        if (tier2.size() < 2) {
            return 0;
        }

        ChoiceGenerator cg = new ChoiceGenerator(tier2.size(), 2);
        int[] choice;
        int numCrossings = 0;

        while ((choice = cg.next()) != null) {
            List<Node> list1 = graph.getAdjacentNodes(tier2.get(choice[0]));
            List<Node> list2 = graph.getAdjacentNodes(tier2.get(choice[1]));

            list1.retainAll(tier1);
            list2.retainAll(tier1);

            for (Node node0 : list1) {
                for (Node node1 : list2) {
                    if (list1.indexOf(node0) > list1.indexOf(node1)) {
                        numCrossings++;
                    }
                }
            }
        }

        return numCrossings;
    }

    private void placeNodes(Node node, Map<Node, Integer> tiers, Graph graph) {
        if (tiers.keySet().contains(node)) {
            return;
        }

        Set<Node> keySet = tiers.keySet();
        List<Node> parents = graph.getParents(node);
        parents.retainAll(keySet);

        List<Node> children = graph.getChildren(node);
        children.retainAll(keySet);

        if (parents.isEmpty() && children.isEmpty()) {
            tiers.put(node, 0);
        } else if (parents.isEmpty()) {
            int cMin = getCMin(children, tiers);
            tiers.put(node, cMin - 1);
            placeChildren(node, tiers, graph);
            return;
        } else {
            int pMax = getPMax(parents, tiers);
            int cMin = getCMin(children, tiers);
            tiers.put(node, pMax + 1);

            if (!children.isEmpty() && cMin < pMax + 2) {
                int diff = (pMax + 2) - cMin;
                List<Node> descendants =
                        graph.getDescendants(Collections.singletonList(node));
                descendants.retainAll(keySet);
                descendants.remove(node);

                for (Node descendant : descendants) {
                    Integer index = tiers.get(descendant);
                    tiers.put(descendant, index + diff);
                }
            }
        }

        placeChildren(node, tiers, graph);
    }

    private void placeChildren(Node node, Map<Node, Integer> tiers,
                               Graph graph) {
        // Recurse.
        List<Node> adj = graph.getAdjacentNodes(node);

        for (Node _node : adj) {
            placeNodes(_node, tiers, graph);
        }
    }

    private int getPMax(List<Node> parents, Map<Node, Integer> tiers) {
        int pMax = Integer.MIN_VALUE;

        for (Node parent : parents) {
            Integer index = tiers.get(parent);
            if (index > pMax) {
                pMax = index;
            }
        }
        return pMax;
    }

    private int getCMin(List<Node> children, Map<Node, Integer> tiers) {
        int cMin = Integer.MAX_VALUE;

        for (Node child : children) {
            Integer index = tiers.get(child);
            if (index < cMin) {
                cMin = index;
            }
        }
        return cMin;
    }
}





