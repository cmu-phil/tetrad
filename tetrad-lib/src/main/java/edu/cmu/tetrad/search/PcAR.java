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
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * Implements the PC (Peter–Clark) causal discovery algorithm for learning a
 * causal graph from conditional independence information.
 * <p>
 * The PC algorithm is a constraint-based method that estimates the Markov
 * equivalence class of a causal directed acyclic graph (DAG) under the
 * assumptions of causal sufficiency (no latent confounders), acyclicity,
 * and faithfulness. The output is typically a partially directed acyclic
 * graph (CPDAG) representing the equivalence class of DAGs consistent with
 * the observed conditional independence relations.
 * <p>
 * This implementation follows the standard three-phase structure described
 * in Causation, Prediction, and Search:
 * <ol>
 *   <li><b>Skeleton discovery</b> using Fast Adjacency Search (FAS), with an
 *       optional PC-Stable variant to ensure order independence.</li>
 *   <li><b>Collider orientation</b> on unshielded triples, using one of several
 *       supported strategies:
 *       <ul>
 *         <li>Sepset-based orientation (original PC)</li>
 *         <li>Conservative PC (CPC)</li>
 *         <li>MAX-P orientation, including optional global and depth-stratified
 *             variants</li>
 *       </ul>
 *   </li>
 *   <li><b>Orientation propagation</b> via Meek’s orientation rules, applied
 *       to closure while respecting background knowledge constraints.</li>
 * </ol>
 * <p>
 * The algorithm relies on an external {@link IndependenceTest} to evaluate
 * conditional independence relations and supports the use of background
 * knowledge to forbid or require specific edge orientations. Optional guards
 * are included to prevent the introduction of directed cycles during collider
 * orientation.
 * <p>
 * This class is deterministic given a fixed independence test, variable
 * ordering, and configuration.
 * <p>
 * <b>References:</b>
 * <ul>
 *   <li>Spirtes, P., Glymour, C. N., &amp; Scheines, R. (2000).
 *       <i>Causation, Prediction, and Search</i>. MIT Press.</li>
 *   <li>Colombo, D., &amp; Maathuis, M. H. (2014).
 *       Order-independent constraint-based causal structure learning.
 *       <i>Journal of Machine Learning Research</i>, 15, 3741–3782.</li>
 *   <li>Ramsey, J. D., Spirtes, P., &amp; Zhang, J. (2012).
 *       Adjacency-faithfulness and conservative causal inference.
 *       <i>Proceedings of UAI</i>.</li>
 * </ul>
 * <p>
 * <b>PcAR (Adjacency-Rescue) extension.</b> This variant adds detection, and
 * optionally repair, of cancelled unfaithful-triangle edges ("Unfaithful
 * Triangles and Where to Find Them", Ramsey/Glymour draft). Two of the
 * paper's three detection tiers are implemented as automatic, general
 * machinery here:
 * <ol>
 *   <li><b>Orientation-clash pass (Sec. 14, tier 1).</b> Every unshielded
 *       triple's deletion is tested against all candidate sepsets (CPC-style)
 *       rather than the first found; both a within-triple CPC ambiguity and a
 *       cross-triple would-create-bidirected conflict are recorded as
 *       {@link ContestedDeletion}s against the deleted pair, regardless of
 *       which {@link ColliderOrientationStyle} the caller has selected for
 *       the graph itself.</li>
 *   <li><b>Determinism guard (Sec. 14).</b> Pluggable via
 *       {@link #setDeterminismGuard}; skips a pair from clash consideration
 *       when the conditioning set functionally determines one side
 *       (Var(V|S)=0), since that is a distinct, rank-dropping faithfulness
 *       failure and not a cancellation. No default implementation is wired,
 *       because that check depends on the concrete {@link IndependenceTest}
 *       exposing a covariance/residual-variance API that this generic class
 *       cannot assume; supply one for tests where it applies (e.g. Fisher Z).</li>
 * </ol>
 * The paper's third tier, the <b>Markov audit</b> of the finished graph's
 * implied independencies, is <i>not</i> reimplemented here: tracing an audit
 * failure back to a specific recoverable edge is locus-specific (Sec. 12) and
 * not soundly generalizable to arbitrary graphs from the triangle argument
 * alone. A minimal, deterministic sanity pass over non-collider unshielded
 * triples is included for diagnostics only (see {@link #getMarkovAuditFailures()});
 * wire {@link #setMarkovAuditor} to a real implication-enumeration pass (e.g.
 * an existing MarkovCheck-style utility) if automatic tracing is wanted.
 * <p>
 * On a tier-1 positive, the action taken is governed by {@link #setRescueAction}:
 * {@code MARK} (default) leaves the skeleton untouched and only records the
 * {@link ContestedDeletion}; {@code RECOVER} reinstates the edge and re-runs
 * orientation, but only when {@link #setRecoveryOddsEstimator} supplies
 * posterior odds at or above {@link #setRecoveryOddsThreshold} for that pair.
 * No default odds estimator is wired: the paper's base rate q(n) is derived
 * from a specific random linear-Gaussian generative model (Secs. 7, 10) and
 * does not transfer to arbitrary data without recalibration. Absent an
 * estimator, RECOVER behaves exactly like MARK.
 */
public class PcAR implements IGraphSearch {

    /**
     * The independence test used to evaluate the statistical independence of variables during the structure learning
     * process in the PC algorithm. This test is a critical component of the algorithm as it dictates the conditional
     * independence relationships to be used for constructing the causal graph.
     */
    private IndependenceTest test;
    /**
     * Represents a {@link Knowledge} object that contains constraints and domain-specific information for use in search
     * algorithms within the Pc class. This variable is used to impose restrictions, such as which edges are allowed or
     * disallowed, and to guide the orientation of structures in the graph.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Represents the depth configuration for a search algorithm.
     * <p>
     * The variable controls the maximum depth to be considered in certain algorithmic operations. A value of -1
     * indicates that there is no depth limit.
     */
    private int depth = -1;                  // -1 => no cap
    /**
     * Indicates whether the PC-Stable variant of the PC algorithm is enabled.
     * <p>
     * When `fasStable` is set to `true`, the skeleton learning phase of the PC algorithm adheres to the PC-Stable
     * rules, which guarantee order independence by fixing the separation sets (sepsets) before running the collider
     * orientation phase.
     * <p>
     * This option is typically used when order independence in constraint-based structure learning is desirable. If set
     * to `false`, the standard PC algorithm is used, which does not enforce such order independence.
     */
    private boolean fasStable = true;        // PC-Stable skeleton
    /**
     * Represents the strategy or style used for orienting colliders within the causal discovery process. Determines how
     * unshielded triples are analyzed and how causal edges are oriented based on available statistical or structural
     * information.
     * <p>
     * The possible values of {@code ColliderOrientationStyle} are: - {@code SEPSETS}: Uses separation sets to orient
     * colliders. - {@code CONSERVATIVE}: Employs a conservative approach to avoid premature orientations. -
     * {@code MAX_P}: Uses statistical measures with potentially global or depth-stratified considerations.
     * <p>
     * This variable is initialized to {@code ColliderOrientationStyle.SEPSETS} by default.
     */
    private ColliderOrientationStyle colliderOrientationStyle = ColliderOrientationStyle.SEPSETS;
    /**
     * Determines whether bidirected edges are allowed in the graph. By default, bidirected edges are disallowed, which
     * means the algorithm will not consider such edges during its operations.
     * <p>
     * This variable can be configured using the {@code setAllowBidirected} method, enabling the user to allow or
     * disallow bidirected edges as needed.
     */
    private AllowBidirected allowBidirected = AllowBidirected.DISALLOW;
    /**
     * Indicates whether verbose logging is enabled for this instance. When set to true, additional detailed information
     * about the internal operations and processes is logged, aiding in debugging and analysis. When false, minimal or
     * no logging is performed.
     */
    private boolean verbose = false;
    /**
     * The timeout in milliseconds for search operations. This value determines the maximum duration allowed for an
     * operation to complete before a timeout is triggered. A value less than 0 indicates that no timeout is applied.
     * <p>
     * This configuration can be used to control the runtime of lengthy computations, ensuring that the process does not
     * exceed a specified limit. If no timeout is desired, set the value to a negative number (e.g., -1).
     */
    private long timeoutMs = -1;             // <0 => no timeout
    /**
     * The start time in milliseconds, used to measure or reference elapsed time for specific operations or processes
     * within the class.
     */
    private long startTimeMs = 0;

    // MAX-P options
    /**
     * Indicates whether to apply global, order-independent MAX-P collider orientation.
     * <p>
     * When set to true, the procedure for orienting colliders within the graph operates independently of the order in
     * which nodes are considered. This can provide more robust results in certain scenarios but may alter the
     * algorithm's behavior based on priorities within the graph search process.
     * <p>
     * Default value is false.
     */
    private boolean maxPGlobalOrder = false;     // if true, apply global order
    /**
     * A boolean flag that, when enabled, configures the MAX-P collider orientation process to operate in a
     * depth-stratified manner, meaning it processes by incrementally increasing the size of separating sets (|S|). This
     * setting is applied only when global order-independent MAX-P processing is active.
     */
    private boolean maxPDepthStratified = true;  // when global is on, process by increasing |S|
    /**
     * The `maxPMargin` variable serves as a threshold or margin guard that determines whether the difference in
     * p-values between potential separation sets on opposite sides of a causal structure is significant enough to
     * resolve as definite instead of ambiguous. Specifically, during detailed MAX-P collider orientation, if both
     * candidate sides share similar best p-values within the range defined by `maxPMargin`, the relationship is marked
     * as ambiguous, avoiding a decisive orientation.
     * <p>
     * By default, `maxPMargin` is set to 0.0, meaning this margin guard is turned off and decisions are made solely
     * based on p-value rankings without further constraints.
     */
    private double maxPMargin = 0.0;            // margin guard; 0 => off

    // Optional tie logging for MAX_P
    /**
     * A flag indicating whether to log details about ties in p-values during the MAX-P collider orientation process.
     * Ties occur when multiple separation sets result in the same best p-value for a collider determination.
     * <p>
     * When set to {@code true}, additional information about these ties will be logged, helping users to debug or
     * analyze scenarios where MAX-P decisions are influenced by such ties.
     * <p>
     * This flag is primarily relevant for debugging or detailed analysis of the MAX-P orientation process and has no
     * effect if such information is not required.
     */
    private boolean logMaxPTies = false;
    /**
     * The output stream used for logging operations within the class. By default, this is set to the standard output
     * stream (System.out). This stream can be redirected to a different output stream as needed.
     */
    private java.io.PrintStream logStream = System.out;
    /**
     * The `fas` variable holds an instance of the Fast Adjacency Search (FAS) algorithm used to construct the skeleton
     * of a graphical model.
     * <p>
     * This field is initialized to `null` and can be accessed via the `getFas()` method. It is typically configured and
     * utilized within the context of the causal discovery process.
     */
    private Fas fas = null; // expose via getFas()

    /**
     * Indicates whether the graph replication process is currently active.
     * This variable is used to track the state of graph replication,
     * which may be required in scenarios involving data duplication,
     * synchronization, or fault tolerance mechanisms.
     */
    private boolean replicatingGraph = false;

    // ---------------- NEW: cycle-safety knobs ----------------
    /** If true, do not allow any orientation that creates a directed cycle. */
    private boolean forbidDirectedCycles = true;

    // ---------------- NEW: adjacency-rescue (PcAR) knobs ----------------

    /**
     * What to do with a tier-1 (orientation-clash) positive.
     */
    public enum RescueAction {
        /** Run detection but take no action; {@link #getContestedDeletions()} stays empty. */
        OFF,
        /** Annotate the deletion as contested; skeleton is left exactly as FAS produced it. */
        MARK,
        /** Reinstate the edge and re-run orientation, subject to {@link #recoveryOddsThreshold}. */
        RECOVER,
        /**
         * Reinstate a contested deletion only when the Markov audit independently flags the SAME
         * pair -- i.e. tier one says the deletion's orientation demands don't cohere AND tier two
         * says an independence the resulting graph entails for that pair is rejected by the data.
         * On the first ground-truth run (20 nodes, avg degree 4, n=1000), the intersection
         * contained two pairs: one a truly missing edge (X3-X8), one not (X12-X18, whose audit
         * failure was actually caused by a DIFFERENT missing edge, X8-X12, that made the graph
         * entail a false independence for the X12-X18 pair). So this mode's precision was 1/2
         * there, against 1/14 for the raw tier-1 list -- much better, not clean. The residual
         * failure mode is inherited from the audit's localization limit: an audit failure names
         * the pair whose entailment fails, not necessarily the pair whose edge is missing, so
         * corroborated recovery repairs correctly only when those coincide. Requires a
         * {@link MarkovAuditor} to be wired; without one the audit is empty, nothing is ever
         * corroborated, and this behaves exactly like MARK. The {@link #recoveryOddsThreshold} is
         * NOT consulted in this mode: the corroboration itself is the evidence gate.
         */
        RECOVER_CORROBORATED
    }

    /**
     * A candidate rescuer: the deleted pair, the sepset that removed it, which clash test caught
     * it, and whether a RECOVER action was actually taken for this deletion.
     *
     * @param x        one endpoint of the deleted pair
     * @param y        the other endpoint of the deleted pair
     * @param z        the pivot (mediator/sink/source) of the triple that produced the flag
     * @param sepset   the sepset FAS recorded for (x, y)
     * @param locus    "bidirected-clash" or "collider-noncollider-clash", the two contradiction
     *                 types of CLASH-PASS (Clark's note Sec. 5)
     * @param recovered whether a RECOVER action was actually taken for this deletion
     */
    public record ContestedDeletion(Node x, Node y, Node z, Set<Node> sepset, String locus, boolean recovered) {
    }

    /**
     * A bidirected orientation clash: the edge {@code u}-{@code z}, present in the graph, that two
     * different unshielded triples demand carry an arrowhead in opposite directions (CLASH-PASS
     * lines 6-7). Localizes to an <i>adjacency that cannot be consistently oriented</i>, not to a
     * deleted pair -- u and z are adjacent, so there is no separating set for them and nothing to
     * reinstate. Detection-only: {@link RescueAction} never acts on these. A cluster of them
     * indicates the accepted independence facts in that neighborhood do not cohere, but tracing
     * that back to a specific recoverable deletion is not mechanical and is not attempted here.
     *
     * @param u one endpoint of the unorientable edge (lexicographically first)
     * @param z the other endpoint
     * @param witnesses the pivot vertices of the triples that raised the conflicting demands
     */
    public record OrientationClash(Node u, Node z, Set<Node> witnesses) {
    }

    /**
     * A diagnostic Markov-audit flag: an implied independence x _||_ y | conditioningSet that did
     * not hold under a fresh test. Carries the full conditioning set rather than a single pivot
     * Node -- the built-in fallback audit only ever populates it with one element, but that is a
     * known limitation of the fallback (see {@link #runMarkovAudit}'s javadoc), not a property of
     * Markov violations in general: the separator implied by a graph is frequently more than one
     * vertex, and a record that could only name one would force a real {@link MarkovAuditor}
     * (e.g. one delegating to a proper graph-implication enumeration) to either drop information
     * or abuse the field. Not traced back to a specific recoverable edge automatically; see the
     * class javadoc.
     *
     * @param x first endpoint
     * @param y second endpoint
     * @param conditioningSet the conditioning set the fresh test used
     * @param pValue the p-value of the fresh test
     */
    public record MarkovAuditFailure(Node x, Node y, Set<Node> conditioningSet, double pValue) {
    }

    /**
     * PcAR reuses {@link Fas.DeterminismGuard} rather than defining its own: the same guard is now
     * forwarded to the internal {@link Fas} instance (see {@link #search(List)}), so a single guard
     * wired once via {@link #setDeterminismGuard} covers both places determinism matters -- the
     * adjacency phase, where it can decline a contaminated deletion outright (Clark's note, "Two
     * Detection Tiers...", Aug 15 2026: "the determinism guard is placed where it can only add
     * edges"), and this class's own clash pass, where it still gates which flagged pairs get
     * treated as candidate cancellations. Previously this class defined its own structurally
     * identical interface and only ever applied it downstream of FAS, which the note correctly
     * identified as unable to prevent a contaminated deletion FAS had already made before this
     * class's search() even runs.
     */
    /**
     * Functional hook supplying posterior odds that a flagged deletion is a genuine edge, for the
     * RECOVER decision. No default is wired; see class javadoc.
     */
    @FunctionalInterface
    public interface RecoveryOddsEstimator {
        double posteriorOdds(Node x, Node y, Node z, Set<Node> sepset) throws InterruptedException;
    }

    /**
     * Boole upper bound on the per-triangle probability that a true triangle is silently
     * collapsed by genuine cancellation (not a weak-edge miss), summed over the three
     * cancellation loci (Secs. 7-8): {@code Sigma_canc = f_XZ(0) + f_YZ(0) + f_XY.Z(0) ~= 1.774},
     * {@code c0_cancel = 2*z* * Sigma_canc ~= 6.95}. Table 1 shows the true rate settling a few
     * percent below this as n grows (6.87 at n=1e5 vs. the 6.95 bound), so it over- rather than
     * under-states the base rate.
     * <p>
     * DERIVED UNDER (A1)-(A3): Erdos-Renyi skeleton, coefficients uniform on (-1,1), disturbance
     * variances uniform on (1/2, 2), an <i>isolated</i> triangle (no exogenous parent on any of
     * the three vertices -- Sec. 2's closing paragraph), Fisher z at alpha=0.05. It is the right
     * default only to the extent your data matches that generative story; recalibrate for
     * anything else rather than trusting the number on its face.
     * <p>
     * Does NOT distinguish which of the three loci is in play (mediator-hidden f_XZ(0)~=0.598,
     * source-hidden f_YZ(0)~=0.505 -- never detects via the clash pass, per the Sec. 12 Lemma --
     * shielded-collider f_XY.Z(0)~=0.671, detectable only via a Markov audit, not this class's
     * clash pass). If you want a locus-specific rate rather than the pooled one, that's the split
     * to make; {@link #getContestedDeletions()}'s {@code locus} field doesn't currently map
     * cleanly onto these three surfaces (it records which clash mechanism fired, not which
     * cancellation surface produced it), so that mapping would need doing by hand per flag.
     */
    public static final double PAPER_CANCELLATION_C0 = 6.95;

    /**
     * {@code q_cancel(n) ~= PAPER_CANCELLATION_C0 / sqrt(n - 3)}, per Secs. 7-8. Returns NaN for
     * n &lt;= 3 (linearization doesn't apply) and also for n small enough that the bound exceeds 1
     * (below n~=51: 6.95/sqrt(48)~=1.003) -- past that point the union bound is vacuous, not "cancellation
     * is certain," and clamping it to 1 would silently assert a confidence the bound doesn't
     * support. Callers (including {@link #paperCancellationBaseRateOdds}) should treat NaN as "this
     * estimator doesn't apply at this n," not as a large or small odds value.
     */
    public static double paperCancellationBaseRate(int n) {
        if (n <= 3) return Double.NaN;
        double q = PAPER_CANCELLATION_C0 / Math.sqrt(n - 3);
        return q < 1.0 ? q : Double.NaN;
    }

    /**
     * The base rate above, expressed as odds ({@code q/(1-q)}) rather than a probability -- i.e.
     * exactly the "base rate" term of the Sec. 14 posterior-odds factorization, and nothing more:
     * it does NOT include the likelihood-ratio term (the rescue test's own power/alpha against the
     * specific discriminating statistic at this triangle), which the paper treats as a separate,
     * per-test factor and which nothing in this class computes for you. Using this value alone as
     * a {@link RecoveryOddsEstimator} implicitly sets that likelihood ratio to 1 -- i.e. treats a
     * clash detection as carrying no evidentiary weight of its own beyond the base rate, which is
     * conservative for real detections and overstates recovery odds for weak/coincidental ones.
     * It is a real default in the sense that it isn't fabricated, but it is not the paper's actual
     * calibrated odds, and shouldn't be presented as such.
     * <p>
     * Returns NaN wherever {@link #paperCancellationBaseRate} does (n too small either way); a NaN
     * compared against any {@link #recoveryOddsThreshold} in {@code x >= threshold} form is always
     * false in Java, so this correctly falls back to MARK behavior rather than throwing or
     * producing a nonsensical negative "odds" from {@code q/(1-q)} with q &gt;= 1.
     */
    public static double paperCancellationBaseRateOdds(int n) {
        double q = paperCancellationBaseRate(n);
        return Double.isNaN(q) ? Double.NaN : q / (1 - q);
    }

    /**
     * A {@link RecoveryOddsEstimator} implementing Sec. 14's full posterior-odds factorization,
     * base rate TIMES likelihood ratio, replacing the earlier base-rate-only default whose
     * pair-blindness made every flag score identically (0.282... at n=1000, all 14 flags on one
     * logged run byte-identical).
     * <p>
     * <b>The discriminating test.</b> Per the Sec. 14 worked trace (step 6: clash on Y-Z implicates
     * deleted X-Z with sepset {}; re-test rho_XZ.Y; "conditioning on Y blocks the indirect path"),
     * the re-test of a flagged pair (x, y) with sepset S and pivot z is the pair under S with the
     * pivot's membership TOGGLED: {@code S u {z}} when z is not in S (the trace's case -- adding
     * the pivot blocks the indirect path so the direct edge shows through, proportional to c), and
     * {@code S \ {z}} when z is in S (the shielded-collider locus -- removing the demanded-collider
     * pivot closes the collider-opened path the cancellation was cancelling against; the graph's
     * entailed marginal dependence of the pair is exactly what Clark's note Sec. 3 identifies as
     * the fact the shielded-collider case leaves testable). Every currently recovery-eligible flag
     * (collider-noncollider-clash) has z in S, so the removal branch is the live one; the union
     * branch is implemented for fidelity to the trace and for any future locus that flags with
     * z outside S.
     * <p>
     * <b>The likelihood ratio.</b> Sec. 14's LR is power/alpha against the unknown effect size vc,
     * which is not computable per-pair. This class substitutes the Vovk-Sellke bound on the Bayes
     * factor implied by the discriminating test's p-value: {@code LR &lt;= 1 / (-e * p * ln p)} for
     * p &lt; 1/e, and 1 otherwise (Sellke, Bayarri &amp; Berger 2001, Am. Stat. 55(1)). This is an
     * UPPER bound -- it credits the rejection with the most evidence any alternative could claim
     * -- so it is anti-conservative on the LR term; the small base rate is what keeps the product
     * in check (at n=1000, base odds 0.282, the threshold-1.0 recovery bar works out to roughly
     * p &lt; 0.02 on the discriminating test).
     * <p>
     * <b>Two honest limits, stated up front.</b> (1) The paper's claim that the discriminating
     * test has size alpha under a genuine non-edge holds WITHIN its model, where the clash's
     * collider demands are genuine. On real data a clash can itself be a test error: if the pivot
     * z is in truth a plain common cause of the pair (not a collider), then removing z from the
     * conditioning set opens the path THROUGH z, the discriminating test rejects through that
     * confounding, and the "size alpha" guarantee does not hold -- the rejection is real
     * dependence, wrongly attributed to a direct edge. Expect this estimator to over-recover
     * exactly where the clash pass over-flags. (2) Sec. 14 explicitly owes a multiplicity
     * correction (BH or Bonferroni over one discriminating test per flag); this class scores each
     * flag in isolation and applies none, because the family size isn't known until all flags are
     * in. Raising the threshold is the crude compensation available today; a proper family-wise
     * pass is future work.
     */
    public static class DiscriminatingTestOddsEstimator implements RecoveryOddsEstimator {
        private final IndependenceTest test;
        private final int sampleSize;

        /**
         * @param test the test to run the discriminating re-test with; sample size for the
         *             base-rate term is taken from {@code test.getSampleSize()}
         */
        public DiscriminatingTestOddsEstimator(IndependenceTest test) {
            this.test = test;
            this.sampleSize = test.getSampleSize();
        }

        @Override
        public double posteriorOdds(Node x, Node y, Node z, Set<Node> sepset) throws InterruptedException {
            double baseOdds = paperCancellationBaseRateOdds(sampleSize);
            if (Double.isNaN(baseOdds)) return Double.NaN; // n too small for the bound; falls to MARK

            // Toggle the pivot's membership in the sepset (Sec. 14 worked trace, step 6).
            Set<Node> discriminating = new LinkedHashSet<>(sepset);
            if (!discriminating.remove(z)) {
                discriminating.add(z);
            }

            IndependenceResult r = test.checkIndependence(x, y, discriminating);
            double p = r.getPValue();

            // Vovk-Sellke bound on the LR from the discriminating p-value. Clamp p away from 0 so
            // p=0.0 (which real runs produce) yields a large finite LR rather than NaN from
            // 0 * -Infinity.
            double lr;
            if (Double.isNaN(p) || p >= 1.0 / Math.E) {
                lr = 1.0; // no evidence credited beyond the base rate
            } else {
                double pc = Math.max(p, 1e-16);
                lr = 1.0 / (-Math.E * pc * Math.log(pc));
            }

            return baseOdds * lr;
        }
    }

    /**
     * Functional hook for a real Markov-audit implication pass, given the finished graph and the
     * test. No default is wired; the built-in fallback only re-checks the non-collider unshielded
     * triples PC already enumerated.
     */
    @FunctionalInterface
    public interface MarkovAuditor {
        List<MarkovAuditFailure> audit(Graph g, IndependenceTest test) throws InterruptedException;
    }

    private RescueAction rescueAction = RescueAction.MARK;
    private double recoveryOddsThreshold = Double.POSITIVE_INFINITY; // never met => RECOVER acts like MARK until an estimator is set
    private int maxRescuePasses = 1; // R; default 1 preserves this class's pre-loop single-pass behavior exactly
    private Fas.DeterminismGuard determinismGuard = null;
    private RecoveryOddsEstimator recoveryOddsEstimator = null;
    private MarkovAuditor markovAuditor = null;
    private boolean warnedNoAuditor = false; // one-shot guard for the "tier two inactive" warning
    private final List<ContestedDeletion> contestedDeletions = new ArrayList<>();
    private final List<OrientationClash> orientationClashes = new ArrayList<>();
    private final List<MarkovAuditFailure> markovAuditFailures = new ArrayList<>();

    /**
     * Sets what to do with a tier-1 clash positive. Default {@link RescueAction#MARK}.
     */
    public void setRescueAction(RescueAction action) {
        this.rescueAction = action;
    }

    /**
     * Sets the posterior-odds threshold at or above which a clash triggers RECOVER rather than
     * MARK. Meaningless unless a {@link RecoveryOddsEstimator} is also set.
     */
    public void setRecoveryOddsThreshold(double threshold) {
        this.recoveryOddsThreshold = threshold;
    }

    /**
     * Sets R, the maximum number of clash-detect-then-recover passes {@link #search(List)} will
     * run (AUGMENTED-SEARCH's bounded iteration, Clark's note Sec. 5-6). Values below 1 are
     * treated as 1. Default 1: exactly the single-pass behavior this class had before this loop
     * existed. Only matters when {@link #rescueAction} is {@link RescueAction#RECOVER} and at
     * least one pass actually recovers something -- under MARK or OFF, or under RECOVER with no
     * successful recovery, the loop always stops after its first pass regardless of this value,
     * since nothing changes for a later pass to act on. There is no guarantee of monotone
     * convergence in R, which is exactly why this is a bounded loop rather than a while-until-fixed-point
     * one; a larger R costs more search time for potentially no additional detections.
     */
    public void setMaxRescuePasses(int r) {
        this.maxRescuePasses = r;
    }

    /**
     * Supplies the Var(v|S)=0 determinism check. Forwarded to the internal {@link Fas} instance at
     * the start of {@link #search(List)} (so it can decline a contaminated deletion before it ever
     * reaches this class's own passes) AND applied again in this class's own clash pass (so a
     * pair that already made it into the skeleton some other way -- e.g. from a differently
     * configured upstream search whose graph was handed in some other way -- is still checked here
     * too). See class javadoc.
     */
    public void setDeterminismGuard(Fas.DeterminismGuard guard) {
        this.determinismGuard = guard;
    }

    /**
     * Supplies the posterior-odds calculation (base rate x likelihood ratio, Sec. 14) used for
     * the MARK/RECOVER decision. See class javadoc.
     */
    public void setRecoveryOddsEstimator(RecoveryOddsEstimator estimator) {
        this.recoveryOddsEstimator = estimator;
    }

    /**
     * Supplies a real Markov-audit implication pass over the finished graph. Without this only
     * the built-in non-collider-triple sanity check runs.
     */
    public void setMarkovAuditor(MarkovAuditor auditor) {
        this.markovAuditor = auditor;
    }

    /**
     * Returns every tier-1 positive recorded during the last {@link #search()} call, in the order
     * detected. Empty if {@link RescueAction#OFF} or before search() has run.
     */
    public List<ContestedDeletion> getContestedDeletions() {
        return Collections.unmodifiableList(contestedDeletions);
    }

    /**
     * Returns every bidirected orientation clash found during the last {@link #search()} call --
     * edges that cannot be consistently oriented given the accepted separating sets. Distinct from
     * {@link #getContestedDeletions()}: these name present adjacencies, not deleted pairs, and are
     * never acted on by {@link RescueAction}. Empty if {@link RescueAction#OFF} or before search()
     * has run.
     */
    public List<OrientationClash> getOrientationClashes() {
        return Collections.unmodifiableList(orientationClashes);
    }

    /**
     * Returns the diagnostic Markov-audit flags from the last {@link #search()} call. See class
     * javadoc: these are not auto-traced to a recoverable edge.
     */
    public List<MarkovAuditFailure> getMarkovAuditFailures() {
        return Collections.unmodifiableList(markovAuditFailures);
    }

    /**
     * Passthrough to the internal {@link Fas} instance's {@link Fas#getBlocked()}: every deletion
     * the adjacency phase declined to make because the only accepting separating sets it found
     * were contaminated by determinism. Distinct from both {@link #getContestedDeletions()} (a
     * deletion FAS DID make, that this class's own passes flag after the fact) and
     * {@link #getMarkovAuditFailures()} (an implied independence the finished graph entails that
     * the data rejected). Always empty if no {@link #setDeterminismGuard} was set, or before
     * search() has run.
     */
    public List<Fas.BlockedDeletion> getBlockedDeletions() {
        return fas == null ? Collections.emptyList() : fas.getBlocked();
    }

    /**
     * Constructs a new instance of the PcAR algorithm with the specified independence test.
     *
     * @param test the independence test to be used by the algorithm
     */
    public PcAR(IndependenceTest test) {
        this.test = test;
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        String f = from.getName();
        String t = to.getName();
        return !knowledge.isRequired(t, f)   // disallow f->t if t->f is required
                && !knowledge.isForbidden(f, t); // disallow f->t if f->t is forbidden
    }

    // ----- Configuration setters -----

    /**
     * Sets the knowledge object for this instance by creating a new instance based on the provided knowledge.
     *
     * @param knowledge the Knowledge object to be set, representing constraints or prior information about the graph
     *                  structure
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the depth parameter for the instance. The depth controls the maximum number of conditioning variables used
     * in conditional independence tests.
     *
     * @param depth the maximum number of conditioning variables; a value of -1 typically indicates no limit.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether the Fast Adjacency Search (FAS) algorithm will use the "stable" modification. The stable version
     * ensures that edge removals during execution do not affect the search process.
     *
     * @param fasStable true to enable the stable FAS modification, false to disable it
     */
    public void setFasStable(boolean fasStable) {
        this.fasStable = fasStable;
    }

    /**
     * Sets the orientation style for handling colliders in the graph. The orientation style determines the method used
     * to decide whether a triple forms a collider. Valid styles are defined in the {@link ColliderOrientationStyle}
     * enum, such as SEPSETS, CONSERVATIVE, or MAX_P.
     *
     * @param rule the {@link ColliderOrientationStyle} that specifies the method used for collider orientation
     */
    public void setColliderOrientationStyle(ColliderOrientationStyle rule) {
        this.colliderOrientationStyle = rule;
    }

    /**
     * Sets the allowance for bidirected edges in the structure being analyzed. This method determines whether
     * bidirected edges are permitted based on the specified option.
     *
     * @param allow an instance of {@link AllowBidirected} that specifies whether bidirected edges are allowed (e.g.,
     *              ALLOW or DISALLOW)
     */
    public void setAllowBidirected(AllowBidirected allow) {
        this.allowBidirected = allow;
    }

    /**
     * Sets whether this instance and its associated test will print verbose output.
     *
     * @param verbose true to enable verbose output; false to disable it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the timeout duration for this instance, specifying the maximum time (in milliseconds) the algorithm or
     * operation is permitted to run.
     *
     * @param timeoutMs the timeout duration in milliseconds. A value of 0 or a negative number may indicate no timeout
     *                  (depending on implementation).
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Sets whether ties in the MAX-P conditional independence tests are logged during execution. If enabled, details
     * about ties that impact decision-making in the algorithm are recorded in the logs.
     *
     * @param enabled true to enable logging for MAX-P ties; false to disable it
     */
    public void setLogMaxPTies(boolean enabled) {
        this.logMaxPTies = enabled;
    }

    /**
     * Sets the output stream for logging messages. The specified PrintStream will be used to capture log outputs
     * generated during the execution of this instance.
     *
     * @param out the {@link java.io.PrintStream} object where the log messages will be directed. Passing {@code null}
     *            disables logging.
     */
    public void setLogStream(java.io.PrintStream out) {
        this.logStream = out;
    }

    /**
     * Sets the global order-independent MAX-P collider orientation option. This determines whether the MAX-P algorithm
     * will apply a global ordering that is independent of the sequence of operations.
     *
     * @param enabled true to enable global order-independent orientation, false to disable it
     */
    public void setMaxPGlobalOrder(boolean enabled) {
        this.maxPGlobalOrder = enabled;
    }

    /**
     * Sets whether the MAX-P depth stratification procedure is enabled or disabled. Depth stratification can be used to
     * adjust the way depth constraints are applied during the MAX-P algorithm, based on specific requirements.
     *
     * @param enabled true to enable depth stratification in MAX-P, false to disable it
     */
    public void setMaxPDepthStratified(boolean enabled) {
        this.maxPDepthStratified = enabled;
    }

    /**
     * Configures whether directed cycles are forbidden in the graph.
     *
     * @param enabled a boolean indicating if directed cycles should be forbidden.
     *                If true, directed cycles are not allowed. If false, directed
     *                cycles are permitted.
     */
    public void setForbidDirectedCycles(boolean enabled) { this.forbidDirectedCycles = enabled; }

    // ----- Entry points -----

    /**
     * Performs a search operation based on the test variables associated with the instance. Delegates the search to the
     * method that accepts a list of nodes.
     *
     * @return the resulting graph structure after the search operation is completed
     * @throws InterruptedException if the thread executing the search is interrupted
     */
    @Override
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    /**
     * Performs a search to generate a graph structure based on the provided list of nodes. Executes a three-step
     * process: skeleton construction using the Fast Adjacency Search (FAS), orientation of unshielded triples as
     * colliders, and application of Meek rules to ensure proper edge orientations.
     *
     * @param nodes the list of nodes to be used as input for the search algorithm
     * @return the resulting graph structure after the search process is completed
     * @throws InterruptedException if the thread executing the search is interrupted
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();
        this.contestedDeletions.clear();
        this.orientationClashes.clear();
        this.markovAuditFailures.clear();

        // Phase 1: skeleton
        this.fas = new Fas(test);
        fas.setReplicatingGraph(replicatingGraph);
        fas.setKnowledge(knowledge);
        fas.setDepth(depth);
        fas.setStable(fasStable);
        fas.setVerbose(verbose);
        fas.setDeterminismGuard(determinismGuard); // null is safe: Fas treats null as "off", same as before this hook existed
        Graph g = fas.search(nodes);
        SepsetMap sepsets = fas.getSepsets();

        // Phase 2: orient v-structures
        orientUnshieldedTriples(g, sepsets);

        // Phase 2.5 (PcAR): tier-1 clash detection and, if configured, recovery, iterated up to
        // maxRescuePasses times (Clark's note, "Two Detection Tiers...", Aug 15 2026, Sec. 6,
        // sixth reason: "the iteration ... is bounded at R passes because a recovery changes the
        // skeleton and so changes the entailed facts, and there is no guarantee of monotone
        // convergence"). Each pass runs on the skeleton/sepsets FAS produced, independently of the
        // collider orientation style chosen for the graph itself (Sec. 14: "the procedure sees the
        // same masquerades no matter where the knob is"). A pass that recovers nothing ends the
        // loop early -- in particular, under RescueAction.MARK or OFF, nothing ever mutates g, so
        // exactly one pass runs regardless of maxRescuePasses, matching this class's pre-loop
        // behavior exactly. flagAndAct dedupes by pair, so later passes don't duplicate
        // ContestedDeletion entries for a still-unresolved clash.
        //
        // Under RECOVER_CORROBORATED, the recovery decision is made after clash detection by
        // intersecting with a fresh Markov audit of the current oriented graph (which is why it
        // runs here, on the oriented-but-pre-Meek graph, rather than inside flagAndAct): only
        // pairs both tiers independently point at are reinstated. Note this consumes audit calls
        // inside the loop, in addition to the final diagnostic audit below.
        int passes = Math.max(1, maxRescuePasses);
        if (rescueAction != RescueAction.OFF) {
            // Config echo: the MARK log lines alone cannot distinguish "MARK selected" from
            // "RECOVER selected but odds never clear the threshold" -- they print identically.
            // This line makes the effective configuration verifiable from the log.
            if (verbose) {
                TetradLogger.getInstance().log(
                        "[PcAR config] rescueAction=" + rescueAction
                                + ", recoveryOddsThreshold=" + recoveryOddsThreshold
                                + ", oddsEstimator=" + (recoveryOddsEstimator != null ? "wired" : "ABSENT")
                                + ", markovAuditor=" + (markovAuditor != null ? "wired" : "ABSENT")
                                + ", determinismGuard=" + (determinismGuard != null ? "wired" : "absent")
                                + ", maxRescuePasses=" + passes);
            }

            for (int pass = 0; pass < passes; pass++) {
                boolean anyRecovered = runClashDetection(g, sepsets);

                if (rescueAction == RescueAction.RECOVER_CORROBORATED) {
                    // Clash detection above only records under CORROBORATED; the actual
                    // reinstatements happen here, gated on tier-2 agreement.
                    anyRecovered = recoverCorroborated(g, sepsets);
                }

                if (!anyRecovered) break;

                // Recovered pairs were spliced back into g as plain undirected edges (Sec. 14:
                // "reinstate the edge and re-run orientation"). Re-running the same collider pass
                // over the updated skeleton is idempotent for every triple recovery didn't touch
                // -- their sepset lookup and outcome are unchanged -- and orients the
                // newly-adjacent pairs consistently with the rest of the graph, exposing whatever
                // new unshielded triples they create for the next pass to check. Ambiguous-triple
                // bookkeeping is fully recomputed on this pass, which is correct since it derives
                // from current adjacency, not accumulated across passes.
                orientUnshieldedTriples(g, sepsets);
            }
        }

        // Phase 3: Meek R1-R4 to closure
        applyMeekRules(g);

        // Phase 3.5 (PcAR): diagnostic-only Markov audit of the finished graph. A single pass on
        // the final graph, not part of the recovery loop above -- see class javadoc on why tier-2
        // stays diagnostic-only (auto-tracing an audit failure to a specific recoverable edge is
        // locus-specific and not soundly generalizable, per Clark's note Sec. 3-4).
        markovAuditFailures.addAll(runMarkovAudit(g));

        return g;
    }

    // ------------------------------------------------------------------------------------
    // PcAR: tier-1 orientation-clash detection and mark/recover
    // ------------------------------------------------------------------------------------

    /**
     * Tier-one orientation-clash pass, implementing CLASH-PASS from Clark's note ("Two Detection
     * Tiers...", Aug 15 2026, Sec. 5). Builds, from the accepted separating sets alone, the set of
     * arrowhead demands (each unshielded triple whose sepset excludes its pivot demands a collider
     * there) and non-collider demands (each triple whose sepset includes its pivot), then reports
     * two structurally different kinds of contradiction:
     * <ul>
     *   <li><b>Bidirected clash</b> (CLASH-PASS lines 6-7): an edge U-Z that two different triples
     *       demand carry an arrowhead in opposite directions. This localizes to an <i>adjacency
     *       that cannot be consistently oriented</i>, NOT to a deleted pair -- U and Z are adjacent
     *       in g, so there is no sepset for them and nothing to reinstate. Reported through
     *       {@link #getOrientationClashes()} as {@link OrientationClash} records and is
     *       <b>detection-only</b>: {@link #rescueAction} never acts on it. An earlier revision
     *       reported these against the deleted pair of whichever triple raised the demand, which
     *       silently converted an orientation contradiction into an adjacency claim about a
     *       different pair of variables; that was wrong and produced badly inflated counts (43
     *       flags on a 20-variable n=1000 run).</li>
     *   <li><b>Collider/non-collider clash</b> (CLASH-PASS lines 8-9): a triple (X,Z,Y) whose own
     *       sepset demands Z NOT be a collider, where two other triples independently demand
     *       arrowheads (X,Z) and (Y,Z) into that same Z. This one genuinely localizes to the
     *       deleted pair X,Y (unshielded triple means X,Y non-adjacent), so it is reported as a
     *       {@link ContestedDeletion} and IS recovery-eligible. This is what a clean cause of the
     *       sink produces at the shielded-collider locus.</li>
     * </ul>
     * Both are logical contradictions among facts the search has already accepted, so this pass
     * runs no independence tests of its own and has no threshold of its own: per Sec. 2 of the
     * note, its false-alarm rate is that of the tests that produced the sepsets. SCALING CAVEAT:
     * the note's measured 0.03 false-alarm rate is from four-variable blocks. The demand set grows
     * with the number of triples, so at twenty-plus variables both counts run higher; a large
     * count is a real statement about how many accepted facts fail to cohere, not necessarily a
     * count of recoverable cancellations.
     * <p>
     * Each contradiction is reported once per pair (bidirected) or once per deleted pair
     * (collider/non-collider), not once per witnessing pivot; the witnessing pivots are carried on
     * the record instead.
     * <p>
     * HISTORICAL NOTE: an earlier revision also flagged a {@code cpc-ambiguous} case,
     * re-enumerating every candidate sepset per triple and flagging disagreement about the pivot's
     * collider status. That was not part of CLASH-PASS and has been removed: it was the CPC
     * ambiguity heuristic rather than an exactness check, and it swamped the output (35 of 41
     * flags on the same run). Use {@link ColliderOrientationStyle#CONSERVATIVE} for CPC-style
     * ambiguity handling; per Sec. 2 of the note that is also the collider style this pass
     * performs best under.
     * <p>
     * Pairs the {@link #determinismGuard} flags as functionally determined are skipped.
     *
     * @return true if at least one deletion was actually recovered (edge spliced back into g)
     */
    private boolean runClashDetection(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        List<Triple> triples = collectUnshieldedTriples(g);

        // Pass 1: collect demands from accepted sepsets (CLASH-PASS lines 1-4). For each demanded
        // arrowhead (from -> into), remember which pivots witnessed it, for reporting.
        Map<List<Node>, Set<Node>> arrowWitnesses = new LinkedHashMap<>();
        List<Triple> nonColliderDemand = new ArrayList<>();

        for (Triple t : triples) {
            checkTimeout();
            if (g.isAdjacentTo(t.x, t.y)) continue; // recovered earlier this pass; sepset no longer applies
            Set<Node> s = fasSepsets.get(t.x, t.y);
            if (s == null) continue;
            if (isDetermined(t.x, s) || isDetermined(t.y, s)) continue;

            if (!s.contains(t.z)) {
                arrowWitnesses.computeIfAbsent(List.of(t.x, t.z), k -> new LinkedHashSet<>()).add(t.y);
                arrowWitnesses.computeIfAbsent(List.of(t.y, t.z), k -> new LinkedHashSet<>()).add(t.x);
            } else {
                nonColliderDemand.add(t);
            }
        }

        // Pass 2a: bidirected clashes (CLASH-PASS lines 6-7). Edge-localized, deduped, and
        // detection-only -- see this method's javadoc for why these are not recovery candidates.
        Set<List<Node>> seenEdges = new LinkedHashSet<>();
        for (List<Node> demand : arrowWitnesses.keySet()) {
            checkTimeout();
            Node u = demand.get(0), z = demand.get(1);
            if (!arrowWitnesses.containsKey(List.of(z, u))) continue; // not demanded both ways

            // Canonical unordered key so U-Z and Z-U report once.
            List<Node> key = u.getName().compareTo(z.getName()) <= 0 ? List.of(u, z) : List.of(z, u);
            if (!seenEdges.add(key)) continue;

            Set<Node> witnesses = new LinkedHashSet<>(arrowWitnesses.get(demand));
            witnesses.addAll(arrowWitnesses.get(List.of(z, u)));
            orientationClashes.add(new OrientationClash(key.get(0), key.get(1), witnesses));
        }

        // Pass 2b: collider vs. non-collider clash at a shared vertex (CLASH-PASS lines 8-9).
        // Deduped by deleted pair; the first witnessing pivot is the one recorded on the record.
        boolean anyRecovered = false;
        Set<List<Node>> seenPairs = new LinkedHashSet<>();
        for (Triple t : nonColliderDemand) {
            checkTimeout();
            if (g.isAdjacentTo(t.x, t.y)) continue;
            if (!arrowWitnesses.containsKey(List.of(t.x, t.z))
                    || !arrowWitnesses.containsKey(List.of(t.y, t.z))) continue;

            List<Node> key = t.x.getName().compareTo(t.y.getName()) <= 0
                    ? List.of(t.x, t.y) : List.of(t.y, t.x);
            if (!seenPairs.add(key)) continue;

            Set<Node> s = fasSepsets.get(t.x, t.y);
            if (s == null) continue;
            anyRecovered |= flagAndAct(g, t.x, t.y, t.z, s, "collider-noncollider-clash");
        }

        // Detection-only logging for bidirected clashes, capped: at twenty-plus variables these
        // can run to dozens, and dumping every one buries the recovery-eligible flags below them.
        if (verbose && !orientationClashes.isEmpty()) {
            int cap = 20;
            int shown = 0;
            for (OrientationClash oc : orientationClashes) {
                if (shown++ >= cap) break;
                TetradLogger.getInstance().log(
                        "[PcAR bidirected-clash] " + oc.u().getName() + " -- " + oc.z().getName()
                                + " cannot be consistently oriented (witnesses " + oc.witnesses()
                                + "); detection-only, not a recovery candidate");
            }
            if (orientationClashes.size() > cap) {
                TetradLogger.getInstance().log(
                        "[PcAR bidirected-clash] ... " + (orientationClashes.size() - cap)
                                + " more (total " + orientationClashes.size()
                                + "). See getOrientationClashes().");
            }
        }

        return anyRecovered;
    }

    private boolean isDetermined(Node v, Set<Node> S) throws InterruptedException {
        return determinismGuard != null && determinismGuard.determines(v, S);
    }

    /**
     * Applies {@link #rescueAction} to a tier-1 positive. On RECOVER with odds clearing
     * {@link #recoveryOddsThreshold}, splices the edge back into {@code g} as a plain undirected
     * edge (Sec. 14's "reinstate the edge"); the caller is responsible for re-running orientation
     * afterward. The paper's "re-test under an enlarged conditioning set" step is the
     * {@link RecoveryOddsEstimator}'s job (it's what should have produced the odds in the first
     * place, using the discriminating rescuer statistic at that enlarged set) -- this method does
     * not re-test independently, to avoid running the discriminating test twice with two different
     * notions of what the enlarged set is.
     * <p>
     * Under {@link RescueAction#RECOVER_CORROBORATED} this records only (like MARK); the recovery
     * decision is deferred to {@link #recoverCorroborated}, which needs the Markov audit of the
     * whole oriented graph and therefore cannot run per-flag from here.
     * <p>
     * Duplicate protection: a pair already present in {@link #contestedDeletions} is not recorded
     * again (later rescue passes re-run detection on a changed skeleton, and a still-unresolved
     * clash would otherwise re-flag every pass).
     *
     * @return true if the edge is now present in g (either just added, or already added earlier
     * in the same pass by a different pivot)
     */
    private boolean flagAndAct(Graph g, Node x, Node y, Node z, Set<Node> sepset, String locus) throws InterruptedException {
        for (ContestedDeletion cd : contestedDeletions) {
            if ((cd.x() == x && cd.y() == y) || (cd.x() == y && cd.y() == x)) {
                return false; // already flagged on an earlier pass; don't duplicate
            }
        }

        boolean recovered = false;
        double odds = Double.NaN; // only meaningful under RECOVER; carried into the log line below

        if (rescueAction == RescueAction.RECOVER) {
            odds = (recoveryOddsEstimator != null)
                    ? recoveryOddsEstimator.posteriorOdds(x, y, z, sepset)
                    : Double.NEGATIVE_INFINITY; // no estimator => never clears the threshold => acts like MARK
            if (odds >= recoveryOddsThreshold) {
                recovered = reinstateEdge(g, x, y);
                if (verbose) {
                    TetradLogger.getInstance().log(
                            "[PcAR RECOVER] " + x.getName() + " - " + y.getName()
                                    + " (pivot " + z.getName() + ", locus " + locus + ", odds=" + odds + ")");
                }
            }
        }

        if (verbose && !recovered) {
            // Say WHY this is a mark, so a declined recovery is distinguishable in the log from
            // plain MARK mode -- previously both printed identically.
            String reason = switch (rescueAction) {
                case RECOVER -> (recoveryOddsEstimator == null)
                        ? "; RECOVER selected but no odds estimator wired"
                        : "; RECOVER selected, odds=" + odds + " < threshold=" + recoveryOddsThreshold;
                case RECOVER_CORROBORATED -> "; corroboration decision deferred to audit intersection";
                default -> "";
            };
            TetradLogger.getInstance().log(
                    "[PcAR MARK] " + x.getName() + " - " + y.getName()
                            + " (pivot " + z.getName() + ", locus " + locus + reason + ")");
        }

        contestedDeletions.add(new ContestedDeletion(x, y, z, sepset, locus, recovered));
        return recovered;
    }

    /**
     * The RECOVER_CORROBORATED decision step: intersects the not-yet-recovered contested deletions
     * with the pairs the Markov audit of the current oriented graph flags, reinstates exactly the
     * pairs appearing in both, and rewrites their {@link ContestedDeletion} records with
     * {@code recovered=true}. Corroboration is by unordered pair identity -- a tier-2 failure
     * x _||_ y | S corroborates a tier-1 flag on the same {x, y} regardless of S or pivot, since
     * the two tiers arrive at the pair by structurally different routes and agreement on the pair
     * is the whole signal. Runs the audit fresh on the current graph (not a cached one), because
     * the graph may have changed since the last audit if this is a later rescue pass.
     *
     * @return true if at least one pair was recovered
     */
    private boolean recoverCorroborated(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        List<MarkovAuditFailure> audit = runMarkovAudit(g);
        if (audit.isEmpty()) return false; // includes the no-auditor-wired case: behaves like MARK

        Set<List<Node>> auditPairs = new LinkedHashSet<>();
        for (MarkovAuditFailure maf : audit) {
            Node a = maf.x(), b = maf.y();
            auditPairs.add(a.getName().compareTo(b.getName()) <= 0 ? List.of(a, b) : List.of(b, a));
        }

        boolean any = false;
        for (int i = 0; i < contestedDeletions.size(); i++) {
            ContestedDeletion cd = contestedDeletions.get(i);
            if (cd.recovered()) continue;
            if (g.isAdjacentTo(cd.x(), cd.y())) continue;

            List<Node> key = cd.x().getName().compareTo(cd.y().getName()) <= 0
                    ? List.of(cd.x(), cd.y()) : List.of(cd.y(), cd.x());
            if (!auditPairs.contains(key)) continue;

            reinstateEdge(g, cd.x(), cd.y());
            contestedDeletions.set(i, new ContestedDeletion(
                    cd.x(), cd.y(), cd.z(), cd.sepset(), cd.locus(), true));
            any = true;

            if (verbose) {
                TetradLogger.getInstance().log(
                        "[PcAR RECOVER-CORROBORATED] " + cd.x().getName() + " - " + cd.y().getName()
                                + " (pivot " + cd.z().getName() + ", locus " + cd.locus()
                                + "; tier-1 clash corroborated by tier-2 audit failure on the same pair)");
            }
        }
        return any;
    }

    /**
     * Splices (x, y) back into g as a plain undirected edge, i.e. exactly the state every skeleton
     * edge is in immediately after FAS and before collider orientation -- so the subsequent
     * re-run of {@link #orientUnshieldedTriples} treats it like any other freshly-discovered edge.
     * Idempotent: if the pair is already adjacent (e.g. recovered earlier in the same pass by a
     * different pivot), this is a no-op that still reports the edge as present.
     *
     * ASSUMPTION FLAGGED: uses {@code Edges.undirectedEdge}, which I'm inferring is available
     * from the same {@code edu.cmu.tetrad.graph} package as {@code GraphUtils.orientCollider}
     * (already used elsewhere in this class) and {@code EdgeListGraph}/{@code Graph} (already
     * imported via the wildcard import). If your version names it differently, this is the one
     * line to change.
     */
    private boolean reinstateEdge(Graph g, Node x, Node y) {
        if (!g.isAdjacentTo(x, y)) {
            g.addEdge(Edges.undirectedEdge(x, y));
        }
        return true;
    }

    // ------------------------------------------------------------------------------------
    // PcAR: diagnostic Markov audit (see class javadoc for scope)
    // ------------------------------------------------------------------------------------

    private List<MarkovAuditFailure> runMarkovAudit(Graph g) throws InterruptedException {
        List<MarkovAuditFailure> out;

        if (markovAuditor != null) {
            out = markovAuditor.audit(g, test);
        } else {
            // No built-in fallback. The previous one re-tested x _||_ y | {z} using only the
            // triple's own pivot as the conditioning set, which silently assumed z alone separates
            // x and y in the WHOLE graph. That is wrong in both directions and was confirmed wrong
            // on real output (Clark's note, "Two Detection Tiers...", Aug 15 2026, Sec. 3; and a
            // 20-variable n=1000 run where 11 of 12 checkable flags conditioned on something other
            // than what the graph entailed -- six of them testing x _||_ y | {z} where the graph
            // entailed plain MARGINAL independence, so the rejection was guaranteed and meant
            // nothing). It also missed the shielded-collider case tier two exists for, by skipping
            // collider triples entirely.
            //
            // Returning nothing is strictly better than returning that: a silent empty tier two is
            // a known gap, whereas a confidently wrong tier two invites acting on flags that are
            // artifacts of the checker. Wire setMarkovAuditor(...) -- see the PcAr algcomparison
            // wrapper for a MarkovCheck-delegating implementation -- to get a real tier two.
            if (verbose && !warnedNoAuditor) {
                warnedNoAuditor = true;
                TetradLogger.getInstance().log(
                        "[PcAR markov-audit] No MarkovAuditor wired; tier two is INACTIVE and "
                                + "getMarkovAuditFailures() will be empty. This is not a clean bill of "
                                + "health -- it means no Markov audit ran. Use setMarkovAuditor(...).");
            }
            out = List.of();
        }

        // Live per-flag logging, matching flagAndAct's convention for tier-1 clashes -- previously
        // this was silent from inside PcAR itself, so a direct caller (or a wired MarkovAuditor)
        // got no visibility unless something downstream (e.g. the algcomparison wrapper) chose to
        // print getMarkovAuditFailures() afterward. Covers both branches above, not just the
        // fallback, so a real MarkovAuditor's findings get the same live visibility.
        if (verbose) {
            for (MarkovAuditFailure maf : out) {
                TetradLogger.getInstance().log(
                        "[PcAR markov-audit] " + maf.x().getName() + " _||_ " + maf.y().getName()
                                + " | " + maf.conditioningSet() + " failed (p=" + maf.pValue() + ")");
            }
        }

        return out;
    }

    public IndependenceTest getTest() { return test; }


    // ------------------------------------------------------------------------------------
    // Triple classification APIs (unshielded only), deterministic order
    // ------------------------------------------------------------------------------------

    /**
     * Sets the independence test for this instance. The provided test must have the same list of variables as the
     * current test to ensure consistency. Otherwise, an exception will be thrown.
     *
     * @param test the new {@link IndependenceTest} to be set. This test must have the same list of variables as the
     *             current test.
     * @throws IllegalArgumentException if the node lists of the current test and the provided test are not equal.
     */
    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();
        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(
                    "The nodes of the proposed new test are not equal list-wise to the nodes of the existing test."
            );
        }
        this.test = test;
    }

    /**
     * Retrieves the Fas object.
     *
     * @return the Fas object associated with this instance.
     */
    public Fas getFas() { return fas; }

    /**
     * Retrieves all colliders from the provided graph based on specific criteria. A collider is an unshielded triple
     * where both parent nodes x and y point to a common child z (i.e., x -&gt; z &lt;- y). The method identifies all
     * such triples in the graph and returns them.
     *
     * @param g the graph from which colliders are identified
     * @return a list of triples representing the colliders found in the graph
     */
    public List<Triple> getColliderTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Sets the value that determines whether the graph is in a replicating state.
     *
     * @param replicatingGraph a boolean indicating whether the graph is replicating.
     */
    public void setReplicatingGraph(boolean replicatingGraph) { this.replicatingGraph = replicatingGraph; }

    // ------------------------------------------------------------------------------------
    // Triple helpers
    // ------------------------------------------------------------------------------------

    private List<Triple> collectUnshieldedTriples(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        List<Triple> triples = new ArrayList<>();
        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                Node xi = adj.get(i);
                for (int j = i + 1; j < m; j++) {
                    Node yj = adj.get(j);
                    if (!g.isAdjacentTo(xi, yj)) {
                        Node x = xi, y = yj;
                        if (x.getName().compareTo(y.getName()) > 0) {
                            Node tmp = x; x = y; y = tmp;
                        }
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator.comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));
        return triples;
    }

    private static String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        names.sort(NaturalSort.naturalComparator());
        return "{" + String.join(",", names) + "}";
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation (cycle-safe)
    // ------------------------------------------------------------------------------------

    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets)
            throws InterruptedException {
        List<Triple> triples = collectUnshieldedTriples(g);

        if (colliderOrientationStyle == ColliderOrientationStyle.MAX_P
                && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples);
            return;
        }

        List<Triple> ambiguousTriples = new ArrayList<>();

        // First pass: decide outcomes without mutating the graph
        List<Triple> toOrient = new ArrayList<>();

        for (Triple t : triples) {
            checkTimeout();

            ColliderOutcome outcome = switch (colliderOrientationStyle) {
                case SEPSETS -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z)
                            ? ColliderOutcome.DEPENDENT
                            : ColliderOutcome.INDEPENDENT;
                }
                case CONSERVATIVE -> judgeConservative(t, g);
                case MAX_P -> judgeMaxP(t, g);
            };

            switch (outcome) {
                case INDEPENDENT -> toOrient.add(t);
                case DEPENDENT, NO_SEPSET -> { }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW)
                        ambiguousTriples.add(t);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "Ambiguous triple: " + t.x.getName()
                                        + " - " + t.z.getName()
                                        + " - " + t.y.getName());
                    }
                }
            }
        }

        // Second pass: apply all collider orientations
        for (Triple t : toOrient) {
            if (canOrientCollider(g, t.x, t.z, t.y)) {
                GraphUtils.orientCollider(g, t.x, t.z, t.y);
                if (verbose) {
                    TetradLogger.getInstance().log(
                            "Collider oriented: " + t.x.getName()
                                    + " -> " + t.z.getName()
                                    + " <- " + t.y.getName());
                }
            }
        }

        Set<edu.cmu.tetrad.graph.Triple> _ambiguousTriples = new HashSet<>();
        for (Triple t : ambiguousTriples)
            _ambiguousTriples.add(
                    new edu.cmu.tetrad.graph.Triple(t.x, t.z, t.y));
        g.setAmbiguousTriples(_ambiguousTriples);
    }

    /**
     * Cycle/knowledge/bidirected-safe collider orientation gate.
     *
     * Forbids:
     *  - violating knowledge arrowhead constraints
     *  - creating bidirected (unless allowed)
     *  - creating a directed cycle (if enabled)
     */
    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // knowledge: require arrowheads into z allowed
        if (!isArrowheadAllowed(x, z, knowledge) || !isArrowheadAllowed(y, z, knowledge)) return false;

        // if bidirected not allowed, disallow if z already points to x or y (would create <->)
        if (allowBidirected != AllowBidirected.ALLOW && (g.isParentOf(z, x) || g.isParentOf(z, y))) return false;

        if (forbidDirectedCycles) {
            // If there is already a directed path z -> ... -> x, then x -> z would create a cycle.
            if (g.paths().existsDirectedPath(z, x)) return false;
            if (g.paths().existsDirectedPath(z, y)) return false;
        }

        return true;
    }

    // ------------------------------------------------------------------------------------
    // MAX-P / Conservative logic (same as your code, but left intact)
    // ------------------------------------------------------------------------------------

    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.INDEPENDENT) winners.add(d);
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);

            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator.comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.x.getName())
                        .thenComparing(m -> m.t.z.getName())
                        .thenComparing(m -> m.t.y.getName())
                        .thenComparing(m -> stringifySet(m.bestS)));

                for (MaxPDecision d : level) {
                    if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                    if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                        if (verbose) {
                            TetradLogger.getInstance().log(
                                    "[MAX-P global(d=" + d.bestS.size() + ")] " +
                                            d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                            " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                        }
                    }
                }
            }
        } else {
            winners.sort(Comparator.comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.x.getName())
                    .thenComparing(d -> d.t.z.getName())
                    .thenComparing(d -> d.t.y.getName())
                    .thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "[MAX-P global] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                        " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        }
    }

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        boolean sawIncludesZ = false, sawExcludesZ = false, sawAny = false;

        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncludesZ = true;
            else sawExcludesZ = true;
            if (sawIncludesZ && sawExcludesZ) return ColliderOutcome.AMBIGUOUS;
        }

        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcludesZ && !sawIncludesZ) return ColliderOutcome.INDEPENDENT;
        if (sawIncludesZ && !sawExcludesZ) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    private ColliderOutcome judgeMaxP(Triple t, Graph g) throws InterruptedException {
        return decideMaxPDetail(t, g).outcome;
    }

    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

//        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = TMath.max(bestIncl, c.p);
            else bestExcl = TMath.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && logStream != null) {
                logStream.println("[MAX-P ambiguous] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName()
                        + " bestExcl=" + bestExcl + " bestIncl=" + bestIncl + " margin=" + maxPMargin);
            }
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, TMath.max(bestExcl, bestIncl), Collections.emptySet());
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
            return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
        } else {
            return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    private Set<Node> firstTieMatchingContainsZ(List<SepCandidate> indep, Node z, boolean containsZ, double targetP) {
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == targetP && c.S.contains(z) == containsZ) ties.add(c);
        ties.sort(Comparator.comparing(c -> stringifySet(c.S)));
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        Map<String, SepCandidate> uniq = new LinkedHashMap<>();

        List<Node> adjx = new ArrayList<>(g.getAdjacentNodes(x));
        List<Node> adjy = new ArrayList<>(g.getAdjacentNodes(y));
        adjx.remove(y);
        adjy.remove(x);

        adjx.sort(Comparator.comparing(Node::getName));
        adjy.sort(Comparator.comparing(Node::getName));

        final int depthCap = (depth < 0) ? Integer.MAX_VALUE : depth;
        int maxAdj = TMath.max(adjx.size(), adjy.size());

        for (int d = 0; d <= TMath.min(depthCap, maxAdj); d++) {
            for (List<Node> adj : new List[]{adjx, adjy}) {
                if (d > adj.size()) continue;

                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String sKey = setKey(S);
                    if (uniq.containsKey(sKey)) continue;

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(sKey, new SepCandidate(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        names.sort(NaturalSort.naturalComparator());;
        return String.join("\u0001", names);
    }

    private void applyMeekRules(Graph g) {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(knowledge);
        meekRules.setMeekPreventCycles(forbidDirectedCycles);
        meekRules.setRevertToUnshieldedColliders(false);
        meekRules.orientImplied(g);
    }

    // ------------------------------------------------------------------------------------
    // Checks / timeouts
    // ------------------------------------------------------------------------------------

    private void checkVars(List<Node> nodes) {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("All nodes must be contained in the test's variables.");
        }
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs)
                throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------

    /**
     * An enumeration that defines the styles of collider orientation in a graph or network.
     * This enumeration can be used in algorithms or systems that deal with graph structures,
     * providing different approaches to orienting colliders.
     */
    public enum ColliderOrientationStyle {

        /**
         * Represents the collider orientation style that utilizes separating sets (sepsets).
         * This style is typically employed in graph or network algorithms where colliders
         * are oriented based on separation properties derived from conditional independencies.
         */
        SEPSETS,

        /**
         * Represents the collider orientation style for the Conservative PC algorithm.
         */
        CONSERVATIVE,

        /**
         * Represents the collider orientation style for the PC-Max algorithm.
         */
        MAX_P }

    /**
     * Enum representing whether bidirected edges are allowed in the graph.
     */
    public enum AllowBidirected {

        /**
         * Represents the option to allow bidirected edges in a graph.
         * Used as part of the AllowBidirected enumeration to specify
         * whether such edges are permissible in the graph structure.
         */
        ALLOW,

        /**
         * Represents the option to disallow bidirected edges in a graph.
         * Used as part of the AllowBidirected enumeration to specify that
         * bidirected edges are not permissible in the graph structure.
         */
        DISALLOW }

    /**
     * Represents the possible outcomes for a collider relationship in a causal
     * inference or graph-based algorithm. This enumeration is used to classify
     * the nature of the relationship between variables in terms of their
     * dependency or causal structure.
     */
    private enum ColliderOutcome {

        /**
         * Represents a state where two variables are determined to be independent
         * in the context of collider relationship analysis within a causal inference
         * or graph-based algorithm.
         */
        INDEPENDENT,

        /**
         * Represents a state where two variables are determined to be dependent
         * in the context of collider relationship analysis within a causal inference
         * or graph-based algorithm. This outcome indicates that the variables
         * exhibit a dependency in their relationship in the examined context.
         */
        DEPENDENT,

        /**
         * Represents a state where the relationship between two variables cannot be
         * conclusively determined as independent or dependent in the context of collider
         * relationship analysis within a causal inference or graph-based algorithm.
         * This outcome indicates uncertainty or lack of sufficient information to classify
         * the nature of the relationship.
         */
        AMBIGUOUS,

        /**
         * Represents a state where there is no separating set (sepset) between two variables
         * in the context of collider relationship analysis within a causal inference or
         * graph-based algorithm. This outcome indicates the absence of a conditioning set
         * that can render the variables independent.
         */
        NO_SEPSET }

    /**
     * A utility class that encapsulates a triplet of nodes.
     * This class is immutable and thread-safe as its fields are final.
     */
    public static final class Triple {

        /**
         * The first node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node x;

        /**
         * The second node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node z;

        /**
         * The third node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node y;

        /**
         * Constructs a Triple object with the specified nodes.
         *
         * @param x the first node in the triplet
         * @param z the second node in the triplet
         * @param y the third node in the triplet
         */
        public Triple(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
    }

    private static final class SepCandidate {
        final Set<Node> S;
        final boolean independent;
        final double p;

        SepCandidate(Set<Node> S, boolean independent, double p) {
            List<Node> sorted = new ArrayList<>(S);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.independent = independent;
            this.p = p;
        }
    }

    private static final class MaxPDecision {
        final Triple t;
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;

        MaxPDecision(Triple t, ColliderOutcome outcome, double bestP, Set<Node> bestS) {
            this.t = t;
            this.outcome = outcome;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}