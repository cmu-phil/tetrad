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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Search-agnostic Markov-audit utilities: given ANY graph and an independence test, find the
 * conditional independencies the graph entails that the data reject, with the family-wise false
 * discovery rate controlled, and derive from those failures the vertex set a targeted repair
 * should be seeded at.
 * <p>
 * Nothing here depends on how the graph was produced. The audit's question -- does the data reject
 * independencies this graph entails? -- is well-posed for the output of any search (PC-family,
 * BOSS, FGES, ...), for a hand-drawn graph, or for a published model. The implied facts and their
 * conditioning sets come from separation in the graph itself, via {@link MarkovCheck}; the only
 * inputs are the graph, a test, a conditioning-set type, and the FDR level.
 * <p>
 * <b>Why FDR here is not optional.</b> The audit is a FAMILY of tests, one per implied fact, and
 * the family grows with graph size and (through power) effectively with sample size: at a per-test
 * alpha the raw failure count rises with n even on a fixed-quality graph, which both overstates
 * the violation count and -- when failures seed a repair region -- inflates the seed set toward
 * the whole graph, observed in practice as a 20-node seeded repair excluding zero vertices at
 * n=5000. Benjamini-Hochberg over the implied-fact p-values is the correction the MARKOV-AUDIT
 * routine specifies (its line 6); this class applies it via
 * {@link StatUtils#fdrCutoff(double, List, boolean, boolean)}. The {@code negativelyCorrelated}
 * flag selects the more conservative Benjamini-Yekutieli-style level for use under arbitrary
 * dependence among the tests; the implied facts of one graph are certainly dependent, so BY is
 * the defensible strict choice, but plain BH ({@code false}) is the routine's stated criterion
 * and the default here.
 * <p>
 * The vertex-set derivation ({@link #implicatedVertices}) takes only the ENDPOINTS of rejected
 * facts, not their conditioning-set members: the conditioning variables locate the evidence, not
 * the suspect region, and including them empirically pulls in most of the graph. The seed
 * attribute written by {@link #stampSeedAttribute} is what
 * {@code VertexRepairPanelGlobalRepair} looks for on its incoming graph to offer seeded repair;
 * any search wrapper can stamp it, not just PC-AR.
 */
public final class MarkovAuditUtils {

    /**
     * The graph attribute under which a seed vertex set travels between boxes, as a
     * comma-separated list of node names. Search-agnostic successor to the PC-AR-specific
     * "PcAR.implicatedVertices" key (which readers should continue to accept for compatibility).
     */
    public static final String SEED_ATTRIBUTE = "markovRepair.seedVertices";

    private MarkovAuditUtils() {
    }

    /**
     * Runs a Markov audit of {@code graph} against {@code test} and returns the implied
     * independence facts rejected under Benjamini-Hochberg at level {@code q} -- NOT the raw
     * per-test rejections. Empty list means "no fact survived FDR-corrected rejection," which is
     * evidence of Markov consistency relative to this family and level, not a certificate of
     * faithfulness (configurations exist that no audit of entailed independencies can expose).
     *
     * @param graph                the graph whose entailed independencies are audited; any graph
     *                             type {@link MarkovCheck} accepts
     * @param test                 the independence test to audit against
     * @param setType              the conditioning-set type (e.g.
     *                             {@link ConditioningSetType#LOCAL_MARKOV})
     * @param q                    the FDR level for the family of implied facts
     * @param negativelyCorrelated pass true for the more conservative correction valid under
     *                             arbitrary dependence (Benjamini-Yekutieli-style level); false
     *                             for plain BH
     * @return the FDR-rejected implied facts, in {@link MarkovCheck}'s result order
     */
    public static List<IndependenceResult> auditFailures(Graph graph, IndependenceTest test,
                                                         ConditioningSetType setType, double q,
                                                         boolean negativelyCorrelated) {
        return auditFailures(graph, test, setType, q, negativelyCorrelated, null);
    }

    /**
     * As {@link #auditFailures(Graph, IndependenceTest, ConditioningSetType, double, boolean)},
     * with a determinism screen: an implied fact x _||_ y | S is EXCLUDED from the audited family
     * (before FDR) when {@code guard} reports S functionally determines x or y, or some member of
     * S is determined by the rest of S. This is the soundness condition Sec. 14 places on both
     * detection tiers: a variable functionally fixed by the conditioning set makes the fact's
     * test statistic degenerate (0/0 partial correlation), and the resulting p-value fires the
     * audit falsely -- observed concretely on the Figure 4 harness, where C = A + B produced four
     * artifact failures of the form C _||_ . | [A, B] at p = 0, all of which are TRUE (vacuously:
     * given A and B, C is a constant) and all of which then drove a downstream seeded repair to
     * add two spurious edges. Screened facts are simply not part of the family; they neither fire
     * nor count toward the BH correction's m.
     * <p>
     * A guard that throws {@link InterruptedException} marks the fact screened (fail-safe: an
     * unevaluated fact is not reported) and re-asserts the thread's interrupt status, matching
     * {@code Fas}'s handling. Pass {@code null} for no screening (the other overload's behavior).
     *
     * @param guard the determinism check, or null for none
     */
    public static List<IndependenceResult> auditFailures(Graph graph, IndependenceTest test,
                                                         ConditioningSetType setType, double q,
                                                         boolean negativelyCorrelated,
                                                         Fas.DeterminismGuard guard) {
        MarkovCheck mc = new MarkovCheck(graph, test, setType);
        mc.generateResults(true, true);
        List<IndependenceResult> all = mc.getResults(true);
        if (all.isEmpty()) return new ArrayList<>();

        List<IndependenceResult> family = new ArrayList<>(all.size());
        for (IndependenceResult r : all) {
            if (guard != null && isDegenerate(guard,
                    r.getFact().getX(), r.getFact().getY(), r.getFact().getZ())) {
                continue;
            }
            family.add(r);
        }
        if (family.isEmpty()) return new ArrayList<>();

        List<Double> pValues = new ArrayList<>(family.size());
        for (IndependenceResult r : family) {
            double p = r.getPValue();
            pValues.add(Double.isNaN(p) ? 1.0 : p); // NaN = no evidence against the fact
        }

        double cutoff = StatUtils.fdrCutoff(q, pValues, negativelyCorrelated, false);

        List<IndependenceResult> rejected = new ArrayList<>();
        for (IndependenceResult r : family) {
            double p = r.getPValue();
            if (!Double.isNaN(p) && p <= cutoff) {
                rejected.add(r);
            }
        }
        return rejected;
    }

    /**
     * True if the fact x _||_ y | S is degenerate per the guard: S determines x or y, or some
     * member of S is determined by the rest of S. Interruption from within the guard reports
     * degenerate (screened) and re-asserts interrupt status.
     */
    private static boolean isDegenerate(Fas.DeterminismGuard guard, Node x, Node y, Set<Node> S) {
        try {
            if (guard.determines(x, S)) return true;
            if (guard.determines(y, S)) return true;
            for (Node v : S) {
                Set<Node> rest = new LinkedHashSet<>(S);
                rest.remove(v);
                if (guard.determines(v, rest)) return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /**
     * The endpoints of the given audit failures: the x and y of each rejected implied fact,
     * deduplicated, in first-appearance order. Conditioning-set members are deliberately
     * excluded; see the class javadoc.
     *
     * @param failures audit failures, e.g. from {@link #auditFailures}
     * @return the implicated vertex set
     */
    public static Set<Node> implicatedVertices(Collection<IndependenceResult> failures) {
        Set<Node> out = new LinkedHashSet<>();
        for (IndependenceResult r : failures) {
            out.add(r.getFact().getX());
            out.add(r.getFact().getY());
        }
        return out;
    }

    /**
     * Convenience composition: audit, correct, take endpoints.
     *
     * @see #auditFailures
     * @see #implicatedVertices(Collection)
     */
    public static Set<Node> implicatedVertices(Graph graph, IndependenceTest test,
                                               ConditioningSetType setType, double q,
                                               boolean negativelyCorrelated) {
        return implicatedVertices(auditFailures(graph, test, setType, q, negativelyCorrelated));
    }

    /**
     * Writes {@code seeds} onto {@code graph} under {@link #SEED_ATTRIBUTE} as a comma-separated
     * name list, the channel the Vertex Repair GUI reads to offer seeded repair. No attribute is
     * written for an empty or null seed set (an empty restriction would seed nothing, which is
     * never what's wanted; absence correctly means "no restriction offered"). Node NAMES travel,
     * not Node references, because graphs are copied between boxes and nodes re-resolved by name.
     *
     * @param graph the graph to stamp (typically a search's output, just before returning it)
     * @param seeds the seed vertices, e.g. from {@link #implicatedVertices}
     */
    public static void stampSeedAttribute(Graph graph, Collection<Node> seeds) {
        if (seeds == null || seeds.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (Node n : seeds) {
            if (n != null && n.getName() != null) names.add(n.getName());
        }
        if (!names.isEmpty()) {
            graph.addAttribute(SEED_ATTRIBUTE, String.join(",", names));
        }
    }
}
