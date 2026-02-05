/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

public class PcTest implements IGraphSearch {

    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;

    public PcTest(IndependenceTest test) {
        this.test = test;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public Graph search() throws InterruptedException {
        return search(this.test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {

        Graph g = new EdgeListGraph(nodes);
        g.fullyConnect(Endpoint.TAIL);

        MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.setVerbose(false);

        int depth = this.depth == -1 ? nodes.size() : this.depth + 1;

        boolean found;

        int k = 0;
        while (k < depth) {
            found = false;

            for (Node a : nodes) {
                if (found) break;

                List<Node> adj = new ArrayList();
                adj.addAll(g.getAdjacentNodes(a));
                adj.removeAll(g.getChildren(a));

                if (adj.size() < k + 1) continue;

                for (Node b : nodes) {
                    if (found) break;

                    if (!adj.contains(b)) continue;

                    List<Node> ne = new ArrayList<>();
                    ne.addAll(g.getAdjacentNodes(b));
                    ne.removeAll(g.getParents(b));
                    ne.removeAll(g.getChildren(b));

                    List<Node> pa = new ArrayList<>();
                    for (Node c : g.getParents(a)) {
                        if (b == c) continue;
                        if (ne.contains(c)) {
                            adj.remove(c);
                            pa.add(c);
                        }
                    }

                    if (k < pa.size()) continue;

                    adj.remove(b);

                    int[] indices;
                    ChoiceGenerator gen = new ChoiceGenerator(adj.size(), k - pa.size());

                    while ((indices = gen.next()) != null) {
                        Set<Node> C = new HashSet();
                        C.addAll(pa);
                        for (int index : indices) C.add(adj.get(index));
                        IndependenceResult test = this.test.checkIndependence(a, b, C);

                        // System.out.println(a.toString() + " _|_ " + b.toString() + " | " + C.toString());
                        // System.out.println(test.getPValue());
                        // System.out.println("");

                        if (test.isIndependent()) {
                            g.removeEdge(a, b);

                            Set<Node> children = new HashSet();
                            children.addAll(g.getAdjacentNodes(a));
                            children.retainAll(g.getAdjacentNodes(b));
                            children.removeAll(g.getParents(a));
                            children.removeAll(g.getParents(b));
                            children.removeAll(C);

                            for (Node c : children) {
                                g.removeEdge(a, c);
                                g.removeEdge(b, c);
                                g.addDirectedEdge(a, c);
                                g.addDirectedEdge(b, c);
                            }

                            Set<Edge> bookmark = g.getEdges();
                            rules.orientImplied(g);

                            if (g.paths().isLegalCpdag()) {
                                found = true;
                                k = 0;
                                break;
                            }

                            g.removeEdges(g.getEdges());
                            // g = new EdgeListGraph(nodes);
                            for (Edge edge : bookmark) g.addEdge(edge);
                        }
                    }

                    adj.addAll(pa);
                    adj.add(b);
                }
            }

            if (!found) k += 1;
        }

        return g;
    }

    public IndependenceTest getTest() { return this.test; }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();
        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException("The nodes of the proposed new test are not equal list-wise to the nodes of the existing test."
            );
        }
        this.test = test;
    }
}