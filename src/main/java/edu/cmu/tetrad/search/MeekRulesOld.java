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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background knowledge").
 * <p/>
 * For now, the fourth rule is always performed.
 *
 * @author Joseph Ramsey
 */
public class MeekRulesOld implements ImpliedOrientation {
    private IKnowledge knowledge = new Knowledge2();

    public MeekRulesOld() {
    }

    public void orientImplied(Graph graph) {
        orientUsingMeekRules(knowledge, graph);
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * <pre>
     * Meek-Orient(G, t)
     * 1.while orientations can be made, for arbitrary a, b, c, and d:
     * 2.    If a --> b, b --> c, a not in adj(c), and Is-Noncollider(a, b, c) then orient b --> c.
     * 3.    If a --> b, b --> c, a --- c, then orient a --> c.
     * 4.    If a --- b, a --- c, a --- d, c --> b, d --> b, then orient a --> b.
     * 5.    If a --> b, b in adj(d) a in adj(c), a --- d, b --> c, c --> d, then orient a --> d.
     * </pre>
     */
    private void orientUsingMeekRules(IKnowledge knowledge, Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = meekR1(graph, knowledge) || meekR2(graph, knowledge) ||
                    meekR3(graph, knowledge) || meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

//    /**
//     * Meek's rule R1: if b-->a, a---c, and a not adj to c, then a-->c
//     */
//    private boolean meekR1(Graph graph, IKnowledge knowledge) {
//        List<Node> nodes = graph.getNodes();
//        boolean changed = true;
//
//        while (changed) {
//            changed = false;
//
//            for (Node a : nodes) {
//                List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//                if (adjacentNodes.size() < 2) {
//                    continue;
//                }
//
//                ChoiceGenerator cg =
//                        new ChoiceGenerator(adjacentNodes.size(), 2);
//                int[] combination;
//
//                while ((combination = cg.next()) != null) {
//                    Node b = adjacentNodes.get(combination[0]);
//                    Node c = adjacentNodes.get(combination[1]);
//
//                    // Skip triples that are shielded.
//                    if (graph.isAdjacentTo(b, c)) {
//                        continue;
//                    }
//
//                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, c) && !graph.isAncestorOf(c, a)) {
//                        if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge))
//                        {
//                            graph.setEndpoint(a, c, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, c));
//                            changed = true;
//                        }
//                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, b) && !graph.isAncestorOf(b, a)) {
//                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge))
//                        {
//                            graph.setEndpoint(a, b, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, b));
//                            changed = true;
//                        }
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * Meek's rule R1: if b-->a, a---c, and a not adj to c, then a-->c
     */
    private boolean meekR1(Graph graph, IKnowledge knowledge) {
        List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node a : nodes) {
                List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                ChoiceGenerator cg =
                        new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = adjacentNodes.get(combination[0]);
                    Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, c)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, b)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

//    /**
//     * If b-->a-->c, b--c, then b-->c.
//     */
//    private boolean meekR2(Graph graph, IKnowledge knowledge) {
//        List<Node> nodes = graph.getNodes();
//        boolean changed = false;
//
//        for (Node a : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//            for (Node c : adjacentNodes) {
//                if (graph.isUndirectedFromTo(a, c) && graph.isAncestorOf(a, c)) {
//                    if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge)) {
//                        graph.setEndpoint(a, c, Endpoint.ARROW);
//                        SearchLogUtils.logEdgeOriented("Nonlocal R2", graph.getEdge(a, c));
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * If b-->a-->c, b--c, then b-->c.
     */
    private boolean meekR2(Graph graph, IKnowledge knowledge) {
        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isDirectedFromTo(b, a) &&
                        graph.isDirectedFromTo(a, c) &&
                        graph.isUndirectedFromTo(b, c)) {
                    if (SearchGraphUtils.isArrowpointAllowed(b, c, knowledge)) {
                        graph.setEndpoint(b, c, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                    }
                } else if (graph.isDirectedFromTo(c, a) &&
                        graph.isDirectedFromTo(a, b) &&
                        graph.isUndirectedFromTo(c, b)) {
                    if (SearchGraphUtils.isArrowpointAllowed(c, b, knowledge)) {
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, d-->b, then orient a-->b.
     */
    private boolean meekR3(Graph graph, IKnowledge knowledge) {

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node b : adjacentNodes) {
                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
                otherAdjacents.remove(b);

                if (!graph.isUndirectedFromTo(a, b)) {
                    continue;
                }

                ChoiceGenerator cg =
                        new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node c = otherAdjacents.get(combination[0]);
                    Node d = otherAdjacents.get(combination[1]);

                    if (graph.isAdjacentTo(c, d)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, d)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(c, b) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

    private boolean meekR4(Graph graph, IKnowledge knowledge) {
        if (knowledge == null) {
            return false;
        }

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node d : adjacentNodes) {
                if (!graph.isAdjacentTo(a, d)) {
                    continue;
                }

                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
                otherAdjacents.remove(d);

                ChoiceGenerator cg =
                        new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = otherAdjacents.get(combination[0]);
                    Node c = otherAdjacents.get(combination[1]);

                    if (!graph.isUndirectedFromTo(a, b)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

//                    if (!isUnshieldedNoncollider(c, a, b, graph)) {
//                        continue;
//                    }

                    if (graph.isDirectedFromTo(b, c) &&
                            graph.isDirectedFromTo(d, c)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            changed = true;
                            break;
                        }
                    } else if (graph.isDirectedFromTo(c, d) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }
}



