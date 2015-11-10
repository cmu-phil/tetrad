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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.LayoutEditable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Lays out a graph by placing springs between the nodes and letting the system
 * settle (one node at a time).
 *
 * @author Joseph Ramsey
 */
public final class CausalOrder {

    /**
     * The graph being laid out.
     */
    private final Graph graph;

    /**
     * Has information about nodes on screen.
     */
    private LayoutEditable layoutEditable;

    //==============================CONSTRUCTORS===========================//

    public CausalOrder(LayoutEditable layoutEditable) {
        this.graph = layoutEditable.getGraph();

        this.layoutEditable = layoutEditable;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        List<List<Node>> tiers = getTiers();

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

    /**
     * Finds the set of nodes which have no children, followed by the set of
     * their parents, then the set of the parents' parents, and so on.  The
     * result is returned as a List of Lists.
     *
     * @return the tiers of this digraph.
     */
    public List<List<Node>> getTiers() {
        Set<Node> found = new HashSet<Node>();
        Set<Node> notFound = new HashSet<Node>();
        List<List<Node>> tiers = new LinkedList<List<Node>>();

        // first copy all the nodes into 'notFound'.
        for (Node node1 : graph.getNodes()) {
            notFound.add(node1);
        }

        // repeatedly run through the nodes left in 'notFound'.  If any node
        // has all of its parents already in 'found', then add it to the
        // getModel tier.
        while (!notFound.isEmpty()) {
            List<Node> thisTier = new LinkedList<Node>();

            for (Node node : notFound) {
                if (found.containsAll(graph.getParents(node))) {
                    thisTier.add(node);
                }
            }

            if (thisTier.isEmpty()) {
                tiers.add(new ArrayList<Node>(notFound));
                break;
            }

            // shift all the nodes in this tier from 'notFound' to 'found'.
            notFound.removeAll(thisTier);
            found.addAll(thisTier);

            // add the getModel tier to the list of tiers.
            tiers.add(thisTier);
        }

        return tiers;
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





