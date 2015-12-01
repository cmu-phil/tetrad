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
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background IKnowledge"), modified for Conservative PC to check noncolliders against recorded noncolliders before
 * orienting.
 * <p>
 * For now, the fourth rule is always performed.
 *
 * @author Joseph Ramsey
 */
public class MeekRulesPattern implements ImpliedOrientation {

    private IKnowledge IKnowledge;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;


    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();


    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRulesPattern() {

    }

    //======================== Public Methods ========================//


    public void orientImplied(Graph graph) {
        orientUsingMeekRulesLocally(IKnowledge, graph);
    }

    public void setKnowledge(IKnowledge IKnowledge) {
        this.IKnowledge = IKnowledge;
    }

    //============================== Private Methods ===================================//

    public void orientUsingMeekRulesLocally(IKnowledge IKnowledge, Graph graph) {

        this.logger.log("info", "Starting Orientation Step D.");

        boolean changed;

        do {
            changed = meekR2(graph, IKnowledge) ||
                    meekR1Locally(graph, IKnowledge) || meekR3(graph, IKnowledge) ||
                    meekR4(graph, IKnowledge);
        } while (changed);


        this.logger.log("info", "Finishing Orientation Step D.");
    }

    public boolean meekR1Locally(Graph graph, IKnowledge IKnowledge) {
        List<Node> nodes = graph.getNodes();
        boolean changed = false;

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
                    if (!isUnshieldedNoncollider(b, a, c, graph)) {
                        continue;
                    }

                    if (isArrowpointAllowed(a, c, IKnowledge) && !createsCycle(a, c, graph)) {
                        graph.setEndpoint(a, c, Endpoint.ARROW);

                        this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg(
                                "Meek R1 triangle (" + b + "-->" + a + "---" + c + ")", graph.getEdge(a, c)));
                        changed = true;

                        meekR2(graph, IKnowledge);
                    }
                } else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
                        graph.isUndirectedFromTo(a, b)) {
                    if (!isUnshieldedNoncollider(b, a, c, graph)) {
                        continue;
                    }

                    if (isArrowpointAllowed(a, b, IKnowledge) && !createsCycle(a, b, graph)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);

                        this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg(
                                "Meek R1 triangle (" + c + "-->" + a + "---" + b + ")", graph.getEdge(a, b)));
                        changed = true;

                        meekR2(graph, IKnowledge);
                    }
                }
            }
        }

        return changed;
    }

    public boolean meekR2(Graph graph, IKnowledge IKnowledge) {
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
                    if (isArrowpointAllowed(b, c, IKnowledge) && !createsCycle(b, c, graph)) {
                        graph.setEndpoint(b, c, Endpoint.ARROW);
                        this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                        meekR2(graph, IKnowledge);
                    }
                } else if (graph.isDirectedFromTo(c, a) &&
                        graph.isDirectedFromTo(a, b) &&
                        graph.isUndirectedFromTo(c, b)) {
                    if (isArrowpointAllowed(c, b, IKnowledge) && !createsCycle(c, b, graph)) {
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                        meekR2(graph, IKnowledge);
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, d-->b, then orient a-->b.
     */
    public boolean meekR3(Graph graph, IKnowledge IKnowledge) {

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

                    if (!isUnshieldedNoncollider(c, a, d, graph)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(c, b) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, IKnowledge) && !createsCycle(a, b, graph)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);

                            this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
                            changed = true;
                            meekR2(graph, IKnowledge);
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

    public boolean meekR4(Graph graph, IKnowledge IKnowledge) {
        if (IKnowledge == null) {
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

                    if (!isUnshieldedNoncollider(c, a, b, graph)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(b, d) &&
                            graph.isDirectedFromTo(d, c)) {
                        if (isArrowpointAllowed(a, c, IKnowledge) && !createsCycle(a, c, graph)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);

                            this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            changed = true;
                            meekR2(graph, IKnowledge);
                            break;
                        }
                    } else if (graph.isDirectedFromTo(c, d) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, IKnowledge) && !createsCycle(a, b, graph)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);

                            this.logger.log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, b)));
                            changed = true;
                            meekR2(graph, IKnowledge);
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

    private static boolean isUnshieldedNoncollider(Node a, Node b, Node c,
                                                   Graph graph) {
        if (graph.isAmbiguousTriple(a, b, c)) {
            return false;
        }

        if (!graph.isAdjacentTo(a, b)) {
            return false;
        }

        if (!graph.isAdjacentTo(c, b)) {
            return false;
        }

        if (graph.isAdjacentTo(a, c)) {
            return false;
        }

        return !(graph.getEndpoint(a, b) == Endpoint.ARROW &&
                graph.getEndpoint(c, b) == Endpoint.ARROW);

    }

    private static boolean isArrowpointAllowed(Object from, Object to,
                                               IKnowledge IKnowledge) {
        if (IKnowledge == null) return true;
        return !IKnowledge.isRequired(to.toString(), from.toString()) &&
                !IKnowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * @return true if orienting x-->y would create a cycle.
     */
    private boolean createsCycle(Node x, Node y, Graph graph) {
        return graph.isAncestorOf(y, x);
    }

    public boolean isAggressivelyPreventCycles() {
        return aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }
}



