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
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

public class PcTestJoe implements IGraphSearch {

    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;

    public PcTestJoe(IndependenceTest test) {
//        this.test = test;
        this.test = new CachedIndependenceQueries(test);
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setVerbose(boolean verbose) {
        this.test.setVerbose(verbose);
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

        int maxDepth = (this.depth == -1) ? nodes.size() : (this.depth + 1);

        int k = 0;
        while (k < maxDepth) {
            boolean found = false;

            // (2) Cache markov blankets for THIS graph state / k-iteration.
            // Rebuild on every while-iteration; since we restart k=0 on accepted moves,
            // this is the right granularity and avoids invalid caches.
            Map<Node, List<Node>> mbCache = new HashMap<>(nodes.size());
            for (Node v : nodes) {
                mbCache.put(v, new ArrayList<>(g.paths().markovBlanket(v)));
            }

            for (Node a : nodes) {
                if (found) break;

                List<Node> mbA = mbCache.get(a);
                if (mbA == null || mbA.isEmpty()) continue;

                // (1) Only consider b that are BOTH in MB(a) and adjacent to a.
                // Build candidates once per a.
                Set<Node> candB = new HashSet<>(g.getAdjacentNodes(a));
                candB.retainAll(mbA);
                if (candB.isEmpty()) continue;

                for (Node b : candB) {
                    if (found) break;

                    // Defensive: MB can include non-neighbors; candB should already be adjacent.
                    if (!g.isAdjacentTo(a, b)) continue;

                    List<Node> mbB = mbCache.get(b);
                    if (mbB == null) mbB = List.of();

                    // Pool starts from MB(a); we remove pa and b from it.
                    List<Node> pool = new ArrayList<>(mbA);

                    // pa = parents(a) that are also in MB(b), and remove them from pool.
                    List<Node> pa = new ArrayList<>();
                    for (Node c : g.getParents(a)) {
                        if (c == b) continue;
                        if (mbB.contains(c)) {
                            pool.remove(c);
                            pa.add(c);
                        }
                    }

                    pool.remove(b);

                    // Still using "exact size k" enumeration (Bryan-style)
                    if (k < pa.size()) continue;
                    int choose = k - pa.size();
                    if (pool.size() < choose) continue;

                    // (3) Precompute the part of "children" that depends only on (a,b) and graph,
                    // not on C; then inside the C-loop we only subtract C.
                    Set<Node> commonAdj = new HashSet<>(g.getAdjacentNodes(a));
                    commonAdj.retainAll(g.getAdjacentNodes(b));
                    commonAdj.removeAll(g.getParents(a));
                    commonAdj.removeAll(g.getParents(b));

                    ChoiceGenerator gen = new ChoiceGenerator(pool.size(), choose);
                    int[] idx;

                    while ((idx = gen.next()) != null) {
                        Set<Node> C = new HashSet<>(pa);
                        for (int j : idx) C.add(pool.get(j));

                        IndependenceResult result = this.test.checkIndependence(a, b, C);
                        if (!result.isIndependent()) continue;

                        // Snapshot PRE-move for rollback
                        Set<Edge> bookmark = new HashSet<>(g.getEdges());

                        // Remove the edge (guaranteed to exist)
                        Edge eAB = g.getEdge(a, b);
                        if (eAB == null) continue; // paranoia
                        g.removeEdge(eAB);

                        // Use precomputed commonAdj then subtract C
                        Set<Node> children = new HashSet<>(commonAdj);
                        children.removeAll(C);

                        for (Node c : children) {
                            g.removeEdge(a, c);
                            g.removeEdge(b, c);
                            g.addDirectedEdge(a, c);
                            g.addDirectedEdge(b, c);
                        }

                        rules.orientImplied(g);

                        if (g.paths().isLegalCpdag()) {
                            found = true;
                            k = 0;      // restart from small conditioning size (CPDAG -> CPDAG walk)
                            break;
                        }

                        // Roll back
                        g.removeEdges(new HashSet<>(g.getEdges()));
                        for (Edge e : bookmark) g.addEdge(e);
                    }
                }
            }

            if (!found) k++;
        }

        return g;
    }

    public IndependenceTest getTest() {
        return this.test;
    }

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