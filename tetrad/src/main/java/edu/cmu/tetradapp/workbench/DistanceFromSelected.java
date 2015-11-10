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

package edu.cmu.tetradapp.workbench;


import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayeredDrawing;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.LayoutEditable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Lays out a graph by placing springs between the nodes and letting the system
 * settle (one node at a time).
 *
 * @author Joseph Ramsey
 */
public final class DistanceFromSelected {

    /**
     * The graph being laid out.
     */
    private final Graph graph;

    /**
     * The selected nodes.
     */
    private final List<Node> selected;

    /**
     * Has information about nodes on screen.
     */
    private LayoutEditable layoutEditable;

    //==============================CONSTRUCTORS===========================//

    public DistanceFromSelected(LayoutEditable layoutEditable) {
        this.graph = layoutEditable.getGraph();

        List<Node> selected = new ArrayList<Node>();

        for (Node node : graph.getNodes()) {
            Map modelToDisplay = layoutEditable.getModelNodesToDisplay();
            DisplayNode displayNode = (DisplayNode) modelToDisplay.get(node);
            if (displayNode != null && displayNode.isSelected()) {
                selected.add(node);
            }
        }

        if (selected.isEmpty()) {
            String names = JOptionPane.showInputDialog("Selected nodes (space delimited):");

            List<Node> nodes = new ArrayList<Node>();
            String[] _names = names.split(" ");

            for (String name : _names) {
                Node node = graph.getNode(name);

                if (node != null) {
                    nodes.add(node);
                }
            }

            if (nodes.isEmpty()) {
                nodes.add(graph.getNodes().get(0));
            }

            selected = nodes;
        }

        this.selected = selected;
        this.layoutEditable = layoutEditable;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        if (selected.isEmpty()) {
            new LayeredDrawing(graph).doLayout();
            return;
        }

        List<List<Node>> tiers = placeInTiers(graph, selected);

        int y = 0;

        for (List<Node> tier : tiers) {
            y += 60;

            if (tier.isEmpty()) continue;

            Node node = tier.get(0);

            DisplayNode displayNode = (DisplayNode) layoutEditable.getModelNodesToDisplay().get(node);
            Rectangle r = displayNode.getBounds();
            int x = r.width / 2 + 10;

            node.setCenterX(x);
            node.setCenterY(y);

            int lastHalf = r.width / 2;

            for (int i = 1; i < tier.size(); i++) {
                node = tier.get(i);
                displayNode = (DisplayNode) layoutEditable.getModelNodesToDisplay().get(node);
                r = displayNode.getBounds();
                int thisHalf = r.width / 2;
                x += lastHalf + thisHalf + 5;
                node.setCenterX(x);
                node.setCenterY(y);
                lastHalf = thisHalf;
            }
        }
    }

    private List<List<Node>> placeInTiers(Graph graph, List<Node> selected) {

        // We know selected is not empty.
        List<Node> nodes = graph.getNodes();

        List<List<Node>> tiers = new ArrayList<List<Node>>();

        tiers.add(selected);

        Set<Node> all = new HashSet<Node>();
        all.addAll(selected);

//        Set<Node> augmented = new HashSet<Node>();
//
//        while (true) {
//            for (Node node : all) {
//                augmented.addAll(graph.getAdjacentNodes(node));
//            }
//
//            augmented.removeAll(all);
//
//            if (augmented.isEmpty()) {
//                break;
//            } else {
//                ArrayList<Node> newNodes = new ArrayList<Node>(augmented);
//                Collections.sort(newNodes);
//
//                // Do some heuristic uncrossing of edges in successive tiers.
//                List<Node> tier1 = tiers.get(tiers.size() - 1);
//                List<Node> tier2 = new ArrayList<Node>(newNodes);
//
//                List<Node> saveArray = new ArrayList<Node>(tier2);
//                int saveCrossings = Integer.MAX_VALUE;
//
//                for (int j = 0; j < 40 /* * tier2.size()*/; j++) {
//                    Collections.shuffle(saveArray);
//                    int numCrossings = numCrossings(tier1, tier2, graph);
//
//                    if (numCrossings < saveCrossings) {
//                        saveArray = new ArrayList<Node>(tier2);
//                        saveCrossings = numCrossings;
//                    }
//                }
//
//                tiers.add(saveArray);
////                tiers.add(newNodes);
//                all.addAll(augmented);
//            }
//        }

        while (true) {
            List<Node> tier1 = tiers.get(tiers.size() - 1);
            List<Node> tier2 = new ArrayList<Node>();

            for (Node node : tier1) {
                List<Node> adj = graph.getAdjacentNodes(node);
                adj.removeAll(all);
                tier2.addAll(adj);
                all.addAll(adj);
            }

            if (tier2.isEmpty()) break;

            tiers.add(tier2);
        }

        List<Node> remainder = new ArrayList<Node>(nodes);
        remainder.removeAll(all);

        tiers.add(new ArrayList<Node>(remainder));

        return tiers;
    }

    //============================PRIVATE METHODS=========================//


    private int numCrossings(List<Node> tier1, List<Node> tier2, Graph graph) {
        if (tier2.size() < 2) {
            return 0;
        }

        int numCrossings = 0;

        for (int i = 0; i < tier1.size(); i++) {
            for (int j = i + 1; j < tier1.size(); j++) {
                Node n11 = tier1.get(i);
                Node n12 = tier1.get(j);

                List<Node> adj1 = graph.getAdjacentNodes(n11);
                List<Node> adj2 = graph.getAdjacentNodes(n12);

                for (Node n21 : adj1) {
                    for (Node n22 : adj2) {
                        int i1 = tier2.indexOf(n21);
                        int i2 = tier2.indexOf(n22);

                        if (i1 != -1 && i2 != -1 && i2 > i1) {
                            numCrossings++;
                        }
                    }
                }

//                for (int ii = 0; ii < tier2.size(); ii++) {
//                    for (int jj = ii + 1; jj < tier2.size(); jj++) {
//                        Node n21 = tier2.get(ii);
//                        Node n22 = tier2.get(jj);
//
//                        if (graph.isAdjacentTo(n11, n22) && graph.isAdjacentTo(n12, n21)) {
//                            numCrossings++;
//                        }
//                    }
//                }
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





