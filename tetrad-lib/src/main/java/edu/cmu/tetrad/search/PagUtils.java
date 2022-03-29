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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class contains methods which can be used to determine whether a directed graph is in the equivalence class
 * determined by the given PAG.  See p. 300 Def. 12.1.1 of CPS for a specification.
 *
 * @author Frank Wimberly
 */
public final class PagUtils {

    /**
     * This method implements step (1) of the definition.
     *
     * @return true if every vertex in gamma is in O.
     */
    public static boolean graphInPagStep0(final Graph pag, final Graph dag) {

        //(1) Every vertex in gamma is in O (measured nodes in dag)
        final List<Node> pagNodes = pag.getNodes();

        //If there is a node in pag that's not a measured node in dag return false.
        for (final Node pagNode : pagNodes) {
            if (dag.getNode(pagNode.getName()) == null) {
                return false;
            }
            if (dag.getNode(pagNode.getName()).getNodeType() != NodeType
                    .MEASURED) {
                return false;
            }
        }

        return true;
    }

    public static boolean graphInPagStep1(final Graph pag, final Graph dag) {
        //If A and B are in O, there is an edge between A and B in gamma
        //iff for every W subset of O minus {A, B}, A and B are d-connected
        //given W in [every graph] in delta (dag).
        final IndTestDSep test = new IndTestDSep(pag);

        final List<Node> V = new ArrayList<>(dag.getNodes());

        for (final Edge edge : pag.getEdges()) {
            final Node A = edge.getNode1();
            final Node B = edge.getNode2();

            final List<Node> W = new ArrayList<>(V);
            W.remove(A);
            W.remove(B);

            final ChoiceGenerator gen = new ChoiceGenerator(W.size(), W.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                final List<Node> S = GraphUtils.asList(choice, W);

                if (test.isDSeparated(A, B, S)) {
                    return false;
                }

            }
        }

        return true;
    }

    public static boolean graphInPagStep2(final Graph pag, final Graph dag) {
        final Set<Edge> pagEdges = pag.getEdges();

        for (final Edge edge : pagEdges) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                final Node A = edge.getNode1();
                final Node B = edge.getNode2();

                final Node Ad = dag.getNode(A.getName());
                final Node Bd = dag.getNode(B.getName());

                final List<Node> singletonB = new ArrayList<>();
                singletonB.add(Bd);
                final List<Node> ancestorsOfB = dag.getAncestors(singletonB);
                if (!ancestorsOfB.contains(Ad)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean graphInPagStep3(final Graph pag, final Graph dag) {
        final Set<Edge> pagEdges = pag.getEdges();

        for (final Edge edge : pagEdges) {
            if (edge.getEndpoint2() == Endpoint.ARROW) {
                final Node A = edge.getNode1();
                final Node B = edge.getNode2();

                final Node Ad = dag.getNode(A.getName());
                final Node Bd = dag.getNode(B.getName());

                if (dag.isAncestorOf(Bd, Ad)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean graphInPagStep4(final Graph pag, final Graph dag) {
        final Set<Triple> pagUnderLines = pag.getUnderLines();

        for (final Triple underline : pagUnderLines) {
            final Node A = underline.getX();
            final Node B = underline.getY();
            final Node C = underline.getZ();

            final Node Ad = dag.getNode(A.getName());
            final Node Bd = dag.getNode(B.getName());
            final Node Cd = dag.getNode(C.getName());

            if (!dag.isAncestorOf(Bd, Ad) && !dag.isAncestorOf(Bd, Cd)) {
                return false;
            }
        }

        return true;
    }

    public static boolean graphInPagStep5(final Graph pag, final Graph dag) {
        for (final Triple triple : pag.getDottedUnderlines()) {
            final Node A = triple.getX();
            final Node B = triple.getY();
            final Node C = triple.getZ();

            final Node Ad = dag.getNode(A.getName());
            final Node Bd = dag.getNode(B.getName());
            final Node Cd = dag.getNode(C.getName());

            if (pag.isParentOf(A, B) && pag.isParentOf(C, B)) {
                final List<Node> commonChildrenAC = new ArrayList<>(dag.getChildren(Ad));
                commonChildrenAC.retainAll(dag.getChildren(Cd));

                for (final Node Dd : commonChildrenAC) {
                    if (dag.isDescendentOf(Bd, Dd)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}




