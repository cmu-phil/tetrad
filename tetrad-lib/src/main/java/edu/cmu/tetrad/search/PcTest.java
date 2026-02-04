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

import edu.cmu.tetrad.algcomparison.statistic.LegalCpdag;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * The PcTest class implements the IGraphSearch interface to perform causal graph
 * discovery using the PC algorithm. The algorithm uses conditional independence
 * tests to iteratively remove edges and orient the graph structure.
 */
public class PcTest implements IGraphSearch {

    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;

    /**
     * Constructs a new PcTest instance using the given independence test.
     *
     * @param test The {@link IndependenceTest} instance to be used for performing
     *             independence testing in the Pc algorithm.
     */
    public PcTest(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Sets the knowledge to be used in the PC algorithm.
     *
     * @param knowledge The {@link Knowledge} instance containing background information
     *                  to guide the structure learning process in the PC algorithm.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the depth parameter for the PC algorithm. This parameter determines the
     * maximum length of conditioning sets to be considered during the structure
     * discovery process.
     *
     * @param depth The maximum depth to use for conditional independence testing.
     *              A value of -1 indicates no limit on the depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Executes the PC algorithm to perform a causal structure learning search
     * using the variables provided by the associated independence test.
     *
     * @return A {@link Graph} representing the learned causal structure.
     *         The nodes of the graph correspond to the variables, and the
     *         edges represent causal or associational relationships inferred
     *         from the data.
     * @throws InterruptedException If the thread executing the method is interrupted
     *                              during the search process.
     */
    @Override
    public Graph search() throws InterruptedException {
        return search(this.test.getVariables());
    }

    /**
     * Performs structure discovery on the provided list of nodes using the PC algorithm.
     * The method constructs an initial graph, applies Meek rules for edge orientation,
     * and iteratively searches for independence relationships to refine the structure.
     *
     * @param nodes A list of {@link Node} objects representing the variables
     *              to be included in the causal graph.
     * @return A {@link Graph} representing the causal relationships inferred from
     *         the provided nodes. The graph structure is refined based on the
     *         conditional independence tests and edge orientation rules.
     * @throws InterruptedException If the execution is interrupted during the search process.
     */
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

                            LegalCpdag check = new LegalCpdag();
                            double legal = check.getValue(null, g);
                            if (legal == 1) {
                                found = true;
                                k = 0;
                                break;
                            }

                            g = new EdgeListGraph(nodes);
                            for (Edge edge : bookmark) g.addEdge(edge);
                        }
                    }

                    adj.addAll(pa);
                    adj.add(b);
                }
            }

            if (!found) k += 1;
        }

        System.out.println(g.toString());

        return g;
    }

    /**
     * Retrieves the independence test associated with this PcTest instance.
     * The independence test is used to evaluate conditional independence
     * relationships among variables during the causal structure learning process.
     *
     * @return The {@link IndependenceTest} instance currently in use.
     */
    public IndependenceTest getTest() { return this.test; }

    /**
     * Sets the independence test to be used in the PC algorithm. The newly provided test
     * must contain the same set of variables as the existing test; otherwise, an
     * {@link IllegalArgumentException} is thrown. This ensures consistency in the algorithm's
     * structure learning process.
     *
     * @param test The {@link IndependenceTest} instance to be set. This test is used to
     *             evaluate conditional independence relationships among variables involved
     *             in the causal structure learning process.
     * @throws IllegalArgumentException If the variables in the provided test do not match
     *                                  those in the current independence test.
     */
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