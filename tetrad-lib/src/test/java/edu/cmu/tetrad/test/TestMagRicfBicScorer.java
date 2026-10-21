///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScorer;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.work_in_progress.MagRicfBicScorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link MagRicfBicScorer}: the RICF-based exact Gaussian MAG BIC with district decomposition.
 *
 * <p>The tests certify the properties the score must have before any search is built on it:
 * agreement with the DAG BIC on DAGs, agreement of the district decomposition with a whole-graph fit,
 * score equivalence across Markov equivalent MAGs, and preference for the true latent-projection MAG
 * over its non-equivalent single-edge neighbors at large sample size.</p>
 *
 * @author josephramsey
 */
public class TestMagRicfBicScorer {

    /**
     * On DAGs the score must agree with SemBicScorer.scoreDag exactly (same likelihood constants, same
     * edge-only penalty convention), at more than one penalty discount.
     */
    @Test
    public void dagAgreesWithSemBicScorer() throws Exception {
        RandomUtil.getInstance().setSeed(38482838L);

        for (int rep = 0; rep < 5; rep++) {
            Graph dag = RandomGraph.randomGraph(8, 0, 10, 100, 100, 100, false);
            DataSet data = new SemIm(new SemPm(dag)).simulateData(1000, false);
            ICovarianceMatrix cov = new CovarianceMatrix(data);

            for (double c : new double[]{1.0, 2.0}) {
                MagRicfBicScorer scorer = new MagRicfBicScorer(cov);
                scorer.setPenaltyDiscount(c);

                double magBic = scorer.score(dag);
                double dagBic = SemBicScorer.scoreDag(dag, cov, c, false);

                assertEquals("DAG rep " + rep + " c " + c, dagBic, magBic, 1e-6);
            }
        }
    }

    /**
     * The district decomposition must agree with a single whole-graph RICF fit, on latent-projection MAGs
     * with nontrivial districts.
     */
    @Test
    public void decomposedAgreesWithFullScore() throws Exception {
        RandomUtil.getInstance().setSeed(48271504L);

        int tested = 0;

        for (int rep = 0; rep < 8; rep++) {
            Graph dagWithLatents = RandomGraph.randomGraph(7, 2, 12, 100, 100, 100, false);
            Graph mag = GraphTransforms.dagToMag(dagWithLatents);

            if (mag.getEdges().stream().noneMatch(Edges::isBidirectedEdge)) continue;

            DataSet data = new SemIm(new SemPm(dagWithLatents)).simulateData(500, false);
            DataSet measured = keepMeasured(data, mag);
            MagRicfBicScorer scorer = new MagRicfBicScorer(measured);

            double decomposed = scorer.score(mag);
            double full = scorer.fullScore(mag);

            assertEquals("rep " + rep, full, decomposed, 1e-4);
            tested++;
        }

        assertTrue("No latent projection with a bidirected edge was generated.", tested >= 3);
    }

    /**
     * Score equivalence: every MAG of a PAG's Markov equivalence class gets the same score. MAGs are
     * enumerated from the PAG and each is certified equivalent by converting it back to a PAG and
     * comparing; only certified members are compared. This is the first oracle criterion for the LV-BOSS
     * score layer.
     */
    @Test
    public void markovEquivalentMagsScoreEqual() throws Exception {
        RandomUtil.getInstance().setSeed(59273948L);

        int classesWithSeveralMags = 0;

        for (int rep = 0; rep < 12 && classesWithSeveralMags < 3; rep++) {
            Graph dagWithLatents = RandomGraph.randomGraph(7, 2, 12, 100, 100, 100, false);
            Graph trueMag = GraphTransforms.dagToMag(dagWithLatents);
            Graph pag = new MagToPag(trueMag).convert(false, true);

            DataSet data = new SemIm(new SemPm(dagWithLatents)).simulateData(500, false);
            DataSet measured = keepMeasured(data, trueMag);
            MagRicfBicScorer scorer = new MagRicfBicScorer(measured);

            List<Double> scores = new ArrayList<>();
            int count = 0;

            for (Graph mag : GraphTransforms.zhangMagsFromPag(pag)) {
                if (++count > 15) break;
                if (!mag.paths().isLegalMag()) continue;

                Graph roundTrip = new MagToPag(mag).convert(false, true);
                roundTrip = GraphUtils.replaceNodes(roundTrip, pag.getNodes());
                if (!roundTrip.equals(pag)) continue; // not certified equivalent; skip

                scores.add(scorer.score(mag));
            }

            if (scores.size() < 2) continue;
            classesWithSeveralMags++;

            double first = scores.getFirst();
            for (int i = 1; i < scores.size(); i++) {
                assertEquals("rep " + rep + ", MAG " + i + " of the class scored differently",
                        first, scores.get(i), 1e-4);
            }
        }

        assertTrue("Fewer than 3 equivalence classes with several certified MAGs were found.",
                classesWithSeveralMags >= 3);
    }

    /**
     * Oracle preference: at a large sample size with strong coefficients, the true latent-projection MAG
     * outscores every legal single-edge mutant (deletion, addition, reversal, mark change) that is not
     * Markov equivalent to it. This is the second oracle criterion for the LV-BOSS score layer.
     */
    @Test
    public void trueMagBeatsNonEquivalentNeighbors() throws Exception {
        RandomUtil.getInstance().setSeed(69184727L);

        // L1 confounds X2, X3; L2 confounds X5, X6. Projection: X1 -> X2 <-> X3 <- X4, X3 -> X5 <-> X6,
        // two nontrivial districts.
        Node x1 = new GraphNode("X1"), x2 = new GraphNode("X2"), x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4"), x5 = new GraphNode("X5"), x6 = new GraphNode("X6");
        Node l1 = new GraphNode("L1"), l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);

        Graph dag = new EdgeListGraph(List.of(x1, x2, x3, x4, x5, x6, l1, l2));
        dag.addEdge(Edges.directedEdge(x1, x2));
        dag.addEdge(Edges.directedEdge(x4, x3));
        dag.addEdge(Edges.directedEdge(x3, x5));
        dag.addEdge(Edges.directedEdge(l1, x2));
        dag.addEdge(Edges.directedEdge(l1, x3));
        dag.addEdge(Edges.directedEdge(l2, x5));
        dag.addEdge(Edges.directedEdge(l2, x6));

        Graph trueMag = GraphTransforms.dagToMag(dag);
        assertTrue(trueMag.paths().isLegalMag());

        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, 0.5);
        params.set(Params.COEF_HIGH, 1.5);

        DataSet data = new SemIm(new SemPm(dag), params).simulateData(50000, false);
        DataSet measured = keepMeasured(data, trueMag);
        MagRicfBicScorer scorer = new MagRicfBicScorer(measured);

        double trueScore = scorer.score(trueMag);
        Graph truePag = new MagToPag(trueMag).convert(false, true);

        int mutants = 0;

        for (Graph mutant : singleEdgeMutants(trueMag)) {
            if (!mutant.paths().isLegalMag()) continue;

            Graph mutantPag = new MagToPag(mutant).convert(false, true);
            mutantPag = GraphUtils.replaceNodes(mutantPag, truePag.getNodes());
            if (mutantPag.equals(truePag)) continue; // equivalent; not a rival

            double mutantScore = scorer.score(mutant);
            assertTrue("Mutant outscored the truth: " + mutant.getEdges() + " by "
                       + (mutantScore - trueScore), trueScore > mutantScore);
            mutants++;
        }

        assertTrue("Too few legal non-equivalent mutants were generated: " + mutants, mutants >= 20);
    }

    /**
     * A complete DAG is the saturated model; its log-likelihood must match the closed form.
     */
    @Test
    public void saturatedModelMatchesClosedForm() throws Exception {
        RandomUtil.getInstance().setSeed(81623540L);

        Graph dag = RandomGraph.randomGraph(5, 0, 7, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(dag)).simulateData(800, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);
        int n = cov.getSampleSize();
        int p = cov.getDimension();

        List<Node> vars = cov.getVariables();
        Graph complete = new EdgeListGraph(vars);
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                complete.addEdge(Edges.directedEdge(vars.get(i), vars.get(j)));
            }
        }

        MagRicfBicScorer scorer = new MagRicfBicScorer(cov);
        double ll = scorer.logLikelihood(complete);

        org.ejml.simple.SimpleMatrix s = new org.ejml.simple.SimpleMatrix(cov.getMatrix().toArray());
        double logDet = Math.log(s.determinant());
        double closedForm = -0.5 * n * (logDet + p * (1.0 + Math.log(2.0 * Math.PI)));

        assertEquals(closedForm, ll, 1e-6);
    }

    /**
     * Districts untouched by an edit are answered from the cache, and the cached path gives the same
     * numbers as a fresh scorer.
     */
    @Test
    public void cacheReusesUnchangedDistricts() throws Exception {
        RandomUtil.getInstance().setSeed(92735162L);

        Node x1 = new GraphNode("X1"), x2 = new GraphNode("X2"), x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4"), x5 = new GraphNode("X5"), x6 = new GraphNode("X6");
        Node l1 = new GraphNode("L1"), l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);

        Graph dag = new EdgeListGraph(List.of(x1, x2, x3, x4, x5, x6, l1, l2));
        dag.addEdge(Edges.directedEdge(x1, x2));
        dag.addEdge(Edges.directedEdge(x4, x3));
        dag.addEdge(Edges.directedEdge(x3, x5));
        dag.addEdge(Edges.directedEdge(l1, x2));
        dag.addEdge(Edges.directedEdge(l1, x3));
        dag.addEdge(Edges.directedEdge(l2, x5));
        dag.addEdge(Edges.directedEdge(l2, x6));

        Graph mag = GraphTransforms.dagToMag(dag);
        DataSet data = new SemIm(new SemPm(dag)).simulateData(1000, false);
        DataSet measured = keepMeasured(data, mag);

        MagRicfBicScorer scorer = new MagRicfBicScorer(measured);
        double score1 = scorer.score(mag);

        // Re-score the identical graph: every district should come from the cache.
        long missesAfterFirst = scorer.getCacheMisses();
        double score2 = scorer.score(mag);
        assertEquals(score1, score2, 0.0);
        assertEquals(missesAfterFirst, scorer.getCacheMisses());
        assertTrue(scorer.getCacheHits() > 0);

        // Edit far from the X5 <-> X6 district: delete X1 -> X2. The {X5, X6} district's entry is reused.
        Graph edited = new EdgeListGraph(mag);
        Node m1 = edited.getNode("X1"), m2 = edited.getNode("X2");
        edited.removeEdge(edited.getEdge(m1, m2));

        long hitsBefore = scorer.getCacheHits();
        double editedScore = scorer.score(edited);
        assertTrue(scorer.getCacheHits() > hitsBefore);

        MagRicfBicScorer fresh = new MagRicfBicScorer(measured);
        assertEquals(fresh.score(edited), editedScore, 1e-10);
    }

    /**
     * Graphs this scorer is not defined for are refused with a clear message: edges that are neither
     * directed nor bidirected, and a cyclic directed part.
     */
    @Test
    public void rejectsIllegalInput() {
        Node a = new GraphNode("X1"), b = new GraphNode("X2");
        Graph und = new EdgeListGraph(List.of(a, b));
        und.addEdge(Edges.undirectedEdge(a, b));

        double[][] m = {{1.0, 0.3}, {0.3, 1.0}};
        ICovarianceMatrix cov = new CovarianceMatrix(
                List.of(new edu.cmu.tetrad.data.ContinuousVariable("X1"),
                        new edu.cmu.tetrad.data.ContinuousVariable("X2")),
                new edu.cmu.tetrad.util.Matrix(m), 100);

        MagRicfBicScorer scorer = new MagRicfBicScorer(cov);

        try {
            scorer.score(und);
            fail("Undirected edge should be refused.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        Graph cyc = new EdgeListGraph(List.of(a, b));
        cyc.addEdge(Edges.directedEdge(a, b));
        cyc.addEdge(Edges.directedEdge(b, a));

        try {
            scorer.score(cyc);
            fail("Cyclic directed part should be refused.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /**
     * The factorization subtraction must hold even when a parent of a district is a descendant of the
     * district, where the district's error vector is not independent of the parent and the contribution
     * is a likelihood factor rather than a conditional likelihood. Truth: L1 confounds D1 and M, L2
     * confounds M and D2, and D1 -> X -> D2, so the projection is D1 <-> M <-> D2 (one district) with
     * district parent X, a descendant of D1.
     */
    @Test
    public void districtParentMayBeDescendantOfDistrict() throws Exception {
        RandomUtil.getInstance().setSeed(13579246L);

        Node d1 = new GraphNode("D1"), m = new GraphNode("M"), d2 = new GraphNode("D2"), x = new GraphNode("X");
        Node l1 = new GraphNode("L1"), l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);

        Graph dag = new EdgeListGraph(List.of(d1, m, d2, x, l1, l2));
        dag.addEdge(Edges.directedEdge(d1, x));
        dag.addEdge(Edges.directedEdge(x, d2));
        dag.addEdge(Edges.directedEdge(l1, d1));
        dag.addEdge(Edges.directedEdge(l1, m));
        dag.addEdge(Edges.directedEdge(l2, m));
        dag.addEdge(Edges.directedEdge(l2, d2));

        Graph mag = GraphTransforms.dagToMag(dag);
        assertTrue(mag.paths().isLegalMag());

        // The structure this test is about: one district {D1, M, D2} whose parent X is a descendant of D1.
        Node md1 = mag.getNode("D1"), mm = mag.getNode("M"), md2 = mag.getNode("D2"), mx = mag.getNode("X");
        assertTrue(Edges.isBidirectedEdge(mag.getEdge(md1, mm)));
        assertTrue(Edges.isBidirectedEdge(mag.getEdge(mm, md2)));
        assertTrue(mag.getParents(md2).contains(mx));
        assertTrue(mag.paths().isAncestorOf(md1, mx));

        DataSet data = new SemIm(new SemPm(dag)).simulateData(2000, false);
        DataSet measured = keepMeasured(data, mag);
        MagRicfBicScorer scorer = new MagRicfBicScorer(measured);

        assertEquals(scorer.fullScore(mag), scorer.score(mag), 1e-4);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Restricts the simulated data to the MAG's (measured) variables, in the MAG's node order.
     */
    private static DataSet keepMeasured(DataSet data, Graph mag) {
        List<Node> keep = new ArrayList<>();
        for (Node node : mag.getNodes()) {
            keep.add(data.getVariable(node.getName()));
        }
        return data.subsetColumns(keep);
    }

    /**
     * All single-edge mutants of a graph: each edge deleted; each directed edge reversed and made
     * bidirected; each bidirected edge made directed both ways; each non-adjacent pair joined by a
     * directed edge either way or a bidirected edge. Legality is for the caller to filter.
     */
    private static List<Graph> singleEdgeMutants(Graph g) {
        List<Graph> out = new ArrayList<>();
        List<Node> nodes = g.getNodes();

        for (Edge e : g.getEdges()) {
            Graph del = new EdgeListGraph(g);
            del.removeEdge(del.getEdge(del.getNode(e.getNode1().getName()), del.getNode(e.getNode2().getName())));
            out.add(del);

            Node a = e.getNode1(), b = e.getNode2();

            if (Edges.isDirectedEdge(e)) {
                Node tail = Edges.getDirectedEdgeTail(e), head = Edges.getDirectedEdgeHead(e);
                out.add(replaceEdge(g, a, b, Edges.directedEdge(head, tail)));
                out.add(replaceEdge(g, a, b, Edges.bidirectedEdge(tail, head)));
            } else if (Edges.isBidirectedEdge(e)) {
                out.add(replaceEdge(g, a, b, Edges.directedEdge(a, b)));
                out.add(replaceEdge(g, a, b, Edges.directedEdge(b, a)));
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i), b = nodes.get(j);
                if (g.isAdjacentTo(a, b)) continue;

                out.add(withEdge(g, Edges.directedEdge(a, b)));
                out.add(withEdge(g, Edges.directedEdge(b, a)));
                out.add(withEdge(g, Edges.bidirectedEdge(a, b)));
            }
        }

        return out;
    }

    private static Graph replaceEdge(Graph g, Node a, Node b, Edge newEdge) {
        Graph copy = new EdgeListGraph(g);
        Node ca = copy.getNode(a.getName()), cb = copy.getNode(b.getName());
        copy.removeEdge(copy.getEdge(ca, cb));
        copy.addEdge(remapEdge(newEdge, copy));
        return copy;
    }

    private static Graph withEdge(Graph g, Edge newEdge) {
        Graph copy = new EdgeListGraph(g);
        copy.addEdge(remapEdge(newEdge, copy));
        return copy;
    }

    private static Edge remapEdge(Edge e, Graph target) {
        Node n1 = target.getNode(e.getNode1().getName());
        Node n2 = target.getNode(e.getNode2().getName());
        return new Edge(n1, n2, e.getEndpoint1(), e.getEndpoint2());
    }
}
