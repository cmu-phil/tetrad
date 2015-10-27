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

import java.util.*;

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
    public static boolean graphInPagStep1(Graph pag, Graph dag) {
        System.out.println("graphInPag entered!");
        System.out.println("PAG = " + pag);
        System.out.println("DAG = " + dag);

        //(1) Every vertex in gamma is in O (measured nodes in dag)
        List<Node> pagNodes = pag.getNodes();

        //If there is a node in pag that's not a measured node in dag return false.
        for (Node pagNode : pagNodes) {
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

    public static boolean graphInPagStep2(Graph pag, Graph dag) {
        //If A and B are in O, there is an edge between A and B in gamma
        //iff for every W subset of O minus {A, B}, A and B are d-connected
        //given W union S in [every graph] in delta (dag).
        List<Node> dagNodes = new LinkedList<Node>(dag.getNodes());
        List<Node> dagONodes = new LinkedList<Node>();

        IndTestDSep test = new IndTestDSep(pag);

        for (Node dagNode : dagNodes) {
            if (dagNode.getNodeType() == NodeType.MEASURED) {
                dagONodes.add(dagNode);
            }
        }

        //For each pair of nodes A and B in dag
        boolean forAllAB = true;
        for (int j = 0; j < dagONodes.size(); j++) {
            for (int k = j + 1; k < dagONodes.size(); k++) {
                Node Ad = dagONodes.get(j);
                Node Bd = dagONodes.get(k);

                //System.out.println("A = " + Ad.getName() + " B = " + Bd.getName());

                List<Node> OMinusAB = new ArrayList<Node>(dagONodes);
                OMinusAB.remove(Ad);
                OMinusAB.remove(Bd);

                //System.out.println("OMinusAB:  ");
                //for(Iterator it = OMinusAB.iterator(); it.hasNext(); )
                //    System.out.println(((Node) it.next()).getName() + " ");

                int nOMinusAB = OMinusAB.size();


                Node Ap = pag.getNode(Ad.getName());
                Node Bp = pag.getNode(Bd.getName());

                //Is there an edge between A and B in gamma?
                List<Edge> edgesABgamma = pag.getEdges(Ap, Bp);
                boolean existsEdgeABgamma = (edgesABgamma.size() > 0);
                //if(existsEdgeABgamma) System.out.println("There are edges between " +
                //        Ap.getName() + " and " + Bp.getName() + " in gamma.");

                //For every subset W of O
                //int maxDepth = 3;

                //if (depth() != -1 && _depth > depth()) {
                //    _depth = depth();
                //}

                boolean forEveryW = true;

//                Node[] arrayOMinusAB = OMinusAB.toArray(new Node[0]);
                for (int i = 0; i <= nOMinusAB; i++) {
                    //subsets of size i
                    ChoiceGenerator cg = new ChoiceGenerator(nOMinusAB, i);

                    int[] indSet;
                    while ((indSet = cg.next()) != null) {
                        List<Node> condSetW = GraphUtils.asList(indSet, OMinusAB);

                        //System.out.println("Trying conditioning set:  ");
                        //for(Iterator it = condSetW.iterator(); it.hasNext(); )
                        //    System.out.println(((Node) it.next()).getName() + " ");


                        if (test.isIndependent(Ad, Bd, condSetW)) {
                            //A and B are d-separated in dag given conditioning set W.
                            forEveryW = false;
                            break;
                        }
                    }
                    if (!forEveryW) {
                        break;
                    }
                }

                if (forEveryW != existsEdgeABgamma) {
                    forAllAB = false;
                    break;
                }

            }
        }

        return forAllAB;
    }

    public static boolean graphInPagStep3(Graph pag, Graph dag) {
        Set<Edge> pagEdges = pag.getEdges();

        for (Edge edge : pagEdges) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                Node A = edge.getNode1();
                Node B = edge.getNode2();

                Node Ad = dag.getNode(A.getName());
                Node Bd = dag.getNode(B.getName());

                List<Node> singletonB = new ArrayList<Node>();
                singletonB.add(Bd);
                List<Node> ancestorsOfB = dag.getAncestors(singletonB);
                if (!ancestorsOfB.contains(Ad)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean graphInPagStep4(Graph pag, Graph dag) {
        Set<Edge> pagEdges = pag.getEdges();

        for (Edge edge : pagEdges) {
            if (edge.getEndpoint2() == Endpoint.ARROW) {
                Node A = edge.getNode1();
                Node B = edge.getNode2();

                Node Ad = dag.getNode(A.getName());
                Node Bd = dag.getNode(B.getName());

                List<Node> singletonA = new ArrayList<Node>();
                singletonA.add(Ad);
                List<Node> ancestorsOfA = dag.getAncestors(singletonA);
                if (ancestorsOfA.contains(Bd)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean graphInPagStep5(Graph pag, Graph dag) {
        Set<Triple> pagUnderLines = pag.getUnderLines();

        for (Triple underline : pagUnderLines) {
            Node A = underline.getX();
            Node B = underline.getY();
            Node C = underline.getZ();

            Node Ad = dag.getNode(A.getName());
            Node Bd = dag.getNode(B.getName());
            Node Cd = dag.getNode(C.getName());

            List<Node> singletonA = new ArrayList<Node>();
            singletonA.add(Ad);
            List<Node> singletonC = new ArrayList<Node>();
            singletonC.add(Cd);

            List<Node> ancestorsOfA = dag.getAncestors(singletonA);
            List<Node> ancestorsOfC = dag.getAncestors(singletonC);

            if (!ancestorsOfA.contains(Bd) && !ancestorsOfC.contains(Bd)) {
                return false;
            }
        }

        return true;
    }

    public static boolean graphInPagStep6(Graph pag, Graph dag) {
        List<Node> pagNodes = pag.getNodes();
        List<Node> dagNodes = dag.getNodes();

        Set<Triple> pagDottedUnderlines = pag.getDottedUnderlines();

        for (Node B : pagNodes) {
            List<Node> parentsOfB = pag.getParents(B);

            //Hack to make sure each parent occurs only once:
            Set<Node> bParents = new HashSet<Node>(parentsOfB);
            Node[] parents = new Node[bParents.size()];
            for (int i = 0; i < parents.length; i++) {
                parents[i] = (Node) (bParents.toArray()[i]);
            }
            //parents = (Node[]) bParents.toArray();

            for (int i = 0; i < parents.length; i++) {
                for (int j = i + 1; j < parents.length; j++) {
                    Node A = parents[i];
                    Node C = parents[j];

                    for (Triple underline : pagDottedUnderlines) {

                        //Does it matter if the triple is ABC versus CBA?
                        //if(A == underLine.getFirst() && B == underLine.getSecond() &&
                        //        C == underLine.getThird()) {
                        if (B == underline.getY() && ((
                                A == underline.getX() &&
                                        C == underline.getZ()) || (
                                C == underline.getX() &&
                                        A == underline.getZ()))) {
                            //if B is a descendant of a common child of A & C return false
                            Node Ad = dag.getNode(A.getName());
                            Node Cd = dag.getNode(C.getName());

                            List<Node> childrenOfA = dag.getChildren(Ad);
                            List<Node> childrenOfC = dag.getChildren(Cd);

                            List<Node> commonChildrenAC = new ArrayList<Node>();

                            for (Node childAC : dagNodes) {
                                if (childrenOfA.contains(childAC) &&
                                        childrenOfC.contains(childAC)) {
                                    commonChildrenAC.add(childAC);
                                }
                            }

                            //If b desdencant of any node in commonChildrenAC return false
                            for (Node commonChild : commonChildrenAC) {
                                if (dag.isDescendentOf(B, commonChild)) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}




