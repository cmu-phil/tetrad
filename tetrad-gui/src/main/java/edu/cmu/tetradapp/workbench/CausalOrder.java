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
import java.util.List;
import java.util.*;

/**
 * Lays out a graph by placing springs between the nodes and letting the system
 * settle (one node at a time).
 *
 * @author Joseph Ramsey
 */
final class CausalOrder {

    /**
     * The graph being laid out.
     */
    private final Graph graph;

    /**
     * Has information about nodes on screen.
     */
    private final LayoutEditable layoutEditable;

    //==============================CONSTRUCTORS===========================//

    public CausalOrder(final LayoutEditable layoutEditable) {
        this.graph = layoutEditable.getGraph();

        this.layoutEditable = layoutEditable;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        final List<List<Node>> tiers = getTiers();

        int y = 0;

        for (final List<Node> tier : tiers) {
            y += 60;

            if (tier.isEmpty()) continue;

            Node node = tier.get(0);

            DisplayNode displayNode = (DisplayNode) this.layoutEditable.getModelNodesToDisplay().get(node);
            Rectangle r = displayNode.getBounds();
            int x = r.width / 2 + 10;

            node.setCenterX(x);
            node.setCenterY(y);

            int lastHalf = r.width / 2;

            for (int i = 1; i < tier.size(); i++) {
                node = tier.get(i);
                displayNode = (DisplayNode) this.layoutEditable.getModelNodesToDisplay().get(node);
                r = displayNode.getBounds();
                final int thisHalf = r.width / 2;
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
    private List<List<Node>> getTiers() {
        final Set<Node> found = new HashSet<>();
        final Set<Node> notFound = new HashSet<>();
        final List<List<Node>> tiers = new LinkedList<>();

        // first copy all the nodes into 'notFound'.
        for (final Node node1 : this.graph.getNodes()) {
            notFound.add(node1);
        }

        // repeatedly run through the nodes left in 'notFound'.  If any node
        // has all of its parents already in 'found', then add it to the
        // getModel tier.
        while (!notFound.isEmpty()) {
            final List<Node> thisTier = new LinkedList<>();

            for (final Node node : notFound) {
                if (found.containsAll(this.graph.getParents(node))) {
                    thisTier.add(node);
                }
            }

            if (thisTier.isEmpty()) {
                tiers.add(new ArrayList<>(notFound));
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

    private void placeNodes(final Node node, final Map<Node, Integer> tiers, final Graph graph) {
        if (tiers.containsKey(node)) {
            return;
        }

        final Set<Node> keySet = tiers.keySet();
        final List<Node> parents = graph.getParents(node);
        parents.retainAll(keySet);

        final List<Node> children = graph.getChildren(node);
        children.retainAll(keySet);

        if (parents.isEmpty() && children.isEmpty()) {
            tiers.put(node, 0);
        } else if (parents.isEmpty()) {
            final int cMin = getCMin(children, tiers);
            tiers.put(node, cMin - 1);
            placeChildren(node, tiers, graph);
            return;
        } else {
            final int pMax = getPMax(parents, tiers);
            final int cMin = getCMin(children, tiers);
            tiers.put(node, pMax + 1);

            if (!children.isEmpty() && cMin < pMax + 2) {
                final int diff = (pMax + 2) - cMin;
                final List<Node> descendants =
                        graph.getDescendants(Collections.singletonList(node));
                descendants.retainAll(keySet);
                descendants.remove(node);

                for (final Node descendant : descendants) {
                    final Integer index = tiers.get(descendant);
                    tiers.put(descendant, index + diff);
                }
            }
        }

        placeChildren(node, tiers, graph);
    }

    private void placeChildren(final Node node, final Map<Node, Integer> tiers,
                               final Graph graph) {
        // Recurse.
        final List<Node> adj = graph.getAdjacentNodes(node);

        for (final Node _node : adj) {
            placeNodes(_node, tiers, graph);
        }
    }

    private int getPMax(final List<Node> parents, final Map<Node, Integer> tiers) {
        int pMax = Integer.MIN_VALUE;

        for (final Node parent : parents) {
            final Integer index = tiers.get(parent);
            if (index > pMax) {
                pMax = index;
            }
        }
        return pMax;
    }

    private int getCMin(final List<Node> children, final Map<Node, Integer> tiers) {
        int cMin = Integer.MAX_VALUE;

        for (final Node child : children) {
            final Integer index = tiers.get(child);
            if (index < cMin) {
                cMin = index;
            }
        }
        return cMin;
    }
}





