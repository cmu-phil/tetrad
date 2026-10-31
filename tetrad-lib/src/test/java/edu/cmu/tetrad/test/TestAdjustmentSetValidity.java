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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Oracle tests for adjustment-set search: every set that {@link RecursiveAdjustment} returns must be a valid
 * adjustment set for the DAG that generated the graph it was given.
 * <p>
 * The oracle. Under a linear SEM, a valid adjustment set Z for (X, Y) makes the coefficient on X in the population
 * regression of Y on X and Z equal to the total effect of X on Y, for every DAG represented by the graph. So, with
 * the generating DAG in hand, validity can be <i>checked</i> rather than argued: compute the population covariance
 * of the measured variables from the SEM, regress, and compare to the total effect read off the coefficient matrix.
 * The check needs no premise about the graph class, the search, or the criterion; it is the definition of validity,
 * instantiated. It is cheap, and it is general -- any adjustment-set procedure can be run against it.
 * <p>
 * Why it exists. In October 2026 this oracle, run inside {@code PagSigmaHatHarness}, found that about fifteen
 * percent of the sets returned for amenable pairs in random PAGs with latent confounders did not recover the true
 * effect. The search itself implements the generalized adjustment criterion correctly; the defect was one level
 * down, in {@code EdgeListGraph.isDefNoncollider}, whose circle case was inverted: it called an unshielded
 * {@code a *-o b o-* c} triple undetermined and a shielded one a definite noncollider, which is backwards. On a
 * path such as {@code X <-o A o-o B o-o C --> Y}, with A and C non-adjacent, B is in fact a definite noncollider,
 * so the path is of definite status and must be blocked; the search was told it need not be, and returned sets
 * leaving it open. The class had been reviewed against the criterion more than once and passed, because it does
 * follow the criterion given its primitives. Only an oracle that ignores the reasoning and checks the answer
 * could have caught it, which is the argument for keeping one in the suite.
 *
 * @author josephramsey
 * @see RecursiveAdjustment
 * @see TestEdgeListGraph#testIsDefNoncolliderCircleCase()
 */
public class TestAdjustmentSetValidity {

    /**
     * Agreement tolerance for the population regression against the true effect.
     */
    private static final double TOL = 1e-8;

    /**
     * The minimal construction that reproduces the October 2026 failure, with the roles labelled. True DAG:
     * {@code L -> B, L -> C, B -> A, A -> X, C -> D, D -> X, X -> Y, C -> Y}, with L latent. Two back-door paths
     * run from X to Y: {@code X <- D <- C -> Y} and {@code X <- A <- B <- L -> C -> Y}. The set {D} blocks the first
     * and not the second, and is therefore not a valid adjustment set; {C} blocks both. In the PAG the second path
     * appears as {@code X <-o A o-o B o-o C --> Y} with A and C non-adjacent, so B is a definite noncollider and
     * the path is of definite status: the generalized adjustment criterion requires it blocked. Under the inverted
     * {@code isDefNoncollider} the search believed B's status undetermined, treated the path as needing no
     * blocking, and returned {D}.
     */
    @Test
    public void testUnshieldedCirclePathMustBeBlocked() {
        Node l = new GraphNode("L");
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node c = new GraphNode("C");
        Node d = new GraphNode("D");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        l.setNodeType(NodeType.LATENT);

        Graph dag = new EdgeListGraph(List.of(l, a, b, c, d, x, y));
        dag.addDirectedEdge(l, b);
        dag.addDirectedEdge(l, c);
        dag.addDirectedEdge(b, a);
        dag.addDirectedEdge(a, x);
        dag.addDirectedEdge(c, d);
        dag.addDirectedEdge(d, x);
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(c, y);

        SemIm im = new SemIm(new SemPm(dag));

        // Strong, fixed coefficients so that an unblocked path produces a bias far above tolerance.
        im.setEdgeCoef(l, b, 0.8);
        im.setEdgeCoef(l, c, 0.8);
        im.setEdgeCoef(b, a, 0.9);
        im.setEdgeCoef(a, x, 0.9);
        im.setEdgeCoef(c, d, 0.9);
        im.setEdgeCoef(d, x, 0.9);
        im.setEdgeCoef(x, y, 0.5);
        im.setEdgeCoef(c, y, 0.8);

        Graph pag = GraphTransforms.dagToPag(dag, false);
        Node px = pag.getNode("X");
        Node py = pag.getNode("Y");
        Node pa = pag.getNode("A");
        Node pb = pag.getNode("B");
        Node pc = pag.getNode("C");
        Node pd = pag.getNode("D");

        // Guard: the test is only meaningful if the PAG has the shape the defect depended on. If dagToPag ever
        // changes so that this no longer holds, the test should fail loudly rather than pass vacuously.
        assertTrue("expected A o-o B in the PAG, got " + pag.getEdge(pa, pb),
                Edges.isNondirectedEdge(pag.getEdge(pa, pb)));
        assertTrue("expected B o-o C in the PAG, got " + pag.getEdge(pb, pc),
                Edges.isNondirectedEdge(pag.getEdge(pb, pc)));
        assertFalse("A and C must be non-adjacent for B to be a definite noncollider", pag.isAdjacentTo(pa, pc));
        assertTrue("B must be a definite noncollider on A o-o B o-o C", pag.isDefNoncollider(pa, pb, pc));
        assertTrue("the pair must be amenable, else there is nothing to test",
                pag.paths().isGraphAmenable(px, py, "PAG", -1, Set.of()));

        Oracle oracle = new Oracle(im);
        int checked = 0;

        for (RecursiveAdjustment.ColliderPolicy policy : RecursiveAdjustment.ColliderPolicy.values()) {
            List<Set<Node>> sets = adjustmentSets(pag, px, py, "PAG", 20, policy);
            assertFalse("no adjustment sets returned under " + policy, sets.isEmpty());

            for (Set<Node> z : sets) {
                // Structural statement of the same fact, for readability: both back-door paths must be blocked.
                boolean blocksViaD = z.contains(pd) || z.contains(pc);
                boolean blocksViaA = z.contains(pa) || z.contains(pb) || z.contains(pc);
                assertTrue("set " + names(z) + " under " + policy + " leaves X <- D <- C -> Y open", blocksViaD);
                assertTrue("set " + names(z) + " under " + policy + " leaves X <-o A o-o B o-o C -> Y open",
                        blocksViaA);

                // And the oracle, which is what actually certifies it.
                oracle.assertValid(dag, "X", "Y", z, policy.toString());
                checked++;
            }
        }

        assertTrue(checked > 0);
    }

    /**
     * The oracle over random PAGs with latent confounders: every set returned for every amenable pair must recover
     * the true effect at the population covariance. This is the audit from {@code PagSigmaHatHarness} as a unit
     * test; before the {@code isDefNoncollider} fix it failed on roughly one set in seven.
     */
    @Test
    public void testReturnedSetsAreValidOnRandomPags() {
        RandomUtil.getInstance().setSeed(1848291L);
        int pairs = 0;
        int sets = 0;

        // Amenable pairs are rare in these graphs -- about two per three replications -- so this needs many
        // replications to clear the informativeness floors below. The work per replication is a dagToPag and a
        // few adjustment searches, no data, so this is still quick.
        for (int rep = 0; rep < 150; rep++) {
            Graph dag = RandomGraph.randomDag(12, 3, 20, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            Graph pag = GraphTransforms.dagToPag(dag, false);
            Oracle oracle = new Oracle(im);

            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y)) continue;
                    if (!pag.paths().possibleAncestor(x, y)) continue;
                    if (!pag.paths().isGraphAmenable(x, y, "PAG", -1, Set.of())) continue;

                    for (RecursiveAdjustment.ColliderPolicy policy : RecursiveAdjustment.ColliderPolicy.values()) {
                        List<Set<Node>> found = adjustmentSets(pag, x, y, "PAG", 5, policy);
                        for (Set<Node> z : found) {
                            oracle.assertValid(dag, x.getName(), y.getName(), z,
                                    "rep " + rep + " " + policy);
                            sets++;
                        }
                    }

                    pairs++;
                }
            }
        }

        assertTrue("too few amenable pairs drawn to be informative: " + pairs, pairs >= 30);
        assertTrue("too few sets checked to be informative: " + sets, sets >= 60);
    }

    /**
     * The same oracle with no latent variables, so the graph is a CPDAG and the graph type is PDAG. There the
     * circle case never arises, so this is a control: it should pass before and after the fix, and failing here
     * would indicate something unrelated.
     */
    @Test
    public void testReturnedSetsAreValidOnRandomCpdags() {
        RandomUtil.getInstance().setSeed(55512L);
        int pairs = 0;

        for (int rep = 0; rep < 40; rep++) {
            Graph dag = RandomGraph.randomDag(10, 0, 16, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            Oracle oracle = new Oracle(im);

            for (Node x : cpdag.getNodes()) {
                for (Node y : cpdag.getNodes()) {
                    if (x.equals(y)) continue;
                    if (!cpdag.paths().possibleAncestor(x, y)) continue;
                    if (!cpdag.paths().isGraphAmenable(x, y, "PDAG", -1, Set.of())) continue;

                    for (RecursiveAdjustment.ColliderPolicy policy : RecursiveAdjustment.ColliderPolicy.values()) {
                        for (Set<Node> z : adjustmentSets(cpdag, x, y, "PDAG", 5, policy)) {
                            oracle.assertValid(dag, x.getName(), y.getName(), z, "rep " + rep + " " + policy);
                        }
                    }

                    pairs++;
                }
            }
        }

        assertTrue("too few amenable pairs drawn to be informative: " + pairs, pairs >= 30);
    }

    // -----------------------------------------------------------------------------------------------------------

    /**
     * Adjustment sets with the settings the Linear Adjustment Effects tool and PdagPagIda use: full radius, hugging
     * both endpoints, no path-length cap, default RaMode.VALID.
     */
    private static List<Set<Node>> adjustmentSets(Graph graph, Node x, Node y, String graphType, int maxNumSets,
                                                  RecursiveAdjustment.ColliderPolicy policy) {
        return new RecursiveAdjustment(graph).adjustmentSets(x, y, graphType, maxNumSets, graph.getNumNodes(), 3,
                -1, policy, true, null, null, Set.of());
    }

    private static String names(Set<Node> nodes) {
        List<String> out = new ArrayList<>();
        for (Node n : nodes) out.add(n.getName());
        java.util.Collections.sort(out);
        return String.valueOf(out);
    }

    /**
     * The population oracle for one SEM: the measured-variable covariance it implies, and the total-effect matrix
     * read off its coefficients.
     */
    private static final class Oracle {

        private final Matrix popSigma;
        private final Map<String, Integer> idx = new HashMap<>();
        private final Matrix totalEffects;
        private final List<Node> all;

        Oracle(SemIm im) {
            List<Node> measured = im.getMeasuredNodes();
            this.popSigma = im.getImplCovar(measured);
            for (int i = 0; i < measured.size(); i++) idx.put(measured.get(i).getName(), i);

            // Over ALL variables, latents included: (I - Gamma)^{-1}[x][y] is the sum over directed paths from x
            // to y of the products of coefficients, i.e. the total effect, and getEdgeCoef is indexed by
            // getVariableNodes in exactly that (tail, head) convention.
            this.all = im.getVariableNodes();
            this.totalEffects = Matrix.identity(all.size()).minus(new Matrix(im.getEdgeCoef())).inverse();
        }

        double trueEffect(Graph dag, String x, String y) {
            return totalEffects.get(all.indexOf(dag.getNode(x)), all.indexOf(dag.getNode(y)));
        }

        /**
         * The coefficient on x in the population regression of y on {x} union z.
         */
        double populationEstimate(String x, Set<Node> z, String y) {
            List<String> w = new ArrayList<>();
            w.add(x);
            Set<String> seen = new LinkedHashSet<>(List.of(x, y));
            for (Node node : z) if (seen.add(node.getName())) w.add(node.getName());

            int m = w.size();
            Matrix sww = new Matrix(m, m);
            Matrix swy = new Matrix(m, 1);

            for (int i = 0; i < m; i++) {
                int wi = idx.get(w.get(i));
                for (int j = 0; j < m; j++) sww.set(i, j, popSigma.get(wi, idx.get(w.get(j))));
                swy.set(i, 0, popSigma.get(wi, idx.get(y)));
            }

            return sww.solve(swy).get(0, 0);
        }

        void assertValid(Graph dag, String x, String y, Set<Node> z, String context) {
            double truth = trueEffect(dag, x, y);
            double estimate = populationEstimate(x, z, y);
            assertEquals(context + ": adjustment set " + names(z) + " for (" + x + ", " + y
                         + ") does not recover the true total effect at the population covariance, so it is not a"
                         + " valid adjustment set for the generating DAG", truth, estimate, TOL);
        }
    }
}
