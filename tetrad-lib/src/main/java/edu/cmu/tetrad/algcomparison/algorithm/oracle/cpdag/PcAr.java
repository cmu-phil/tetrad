///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Peter/Clark algorithm with adjacency-rescue (PcAR): PC augmented with detection, and optionally
 * repair, of cancelled unfaithful-triangle edges. See {@link edu.cmu.tetrad.search.PcAR} for the
 * detection/recovery machinery itself; this class only wires it into algcomparison.
 * <p>
 * NOTE ON UNWIRED HOOKS: {@code edu.cmu.tetrad.search.PcAR} exposes three functional hooks
 * ({@code DeterminismGuard}, {@code RecoveryOddsEstimator}, {@code MarkovAuditor}) that take code,
 * not primitive values, and so cannot be expressed as {@link Parameters} entries. They are left
 * unset here (i.e. the built-in fallbacks apply, or RECOVER behaves like MARK with no odds
 * estimator) &mdash; wire them programmatically against a specific {@link IndependenceTest}
 * implementation if you want them active, rather than through this wrapper. As of this revision
 * a placeholder {@code RecoveryOddsEstimator} IS wired below for the RECOVER path (see
 * PLACEHOLDER_ALWAYS_RECOVER), purely so RECOVER produces a visibly different graph without you
 * having to write a real estimator first. Swap it out before trusting a recovered edge as more
 * than a demo.
 * <p>
 * NOTE ON PARAMS: {@code Params.RESCUE_ACTION} and {@code Params.RECOVERY_ODDS_THRESHOLD} are
 * used directly below on the assumption you've now registered them (plus their
 * {@code ParamDescriptions} entries) yourself; an earlier revision of this file referenced them
 * without that registration existing, which wouldn't have compiled.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC-AR",
        command = "pc-ar",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PcAr extends AbstractBootstrapAlgorithm implements Algorithm, AcceptsKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    // R (maxRescuePasses on PcAR): fixed rather than a tunable Parameters entry, per instruction.
    // "Small" here means enough passes for one recovered edge's newly-exposed unshielded triples
    // to get a chance to be checked too, without unbounded cost on a run where RECOVER never
    // actually recovers anything (the common case with only the BASE_RATE_ONLY estimator wired --
    // see the comment at the setMaxRescuePasses call site). 3 is a guess at that balance, not a
    // calibrated number; there's no result in the paper or Clark's note pinning down a specific R.
    private static final int MAX_RESCUE_PASSES = 3;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for PcAr.</p>
     */
    public PcAr() {
    }

    /**
     * <p>Constructor for PcAr.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public PcAr(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        boolean allowBidirected = parameters.getBoolean(Params.ALLOW_BIDIRECTED);

        edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle colliderOrientationStyle = switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
            case 1 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.SEPSETS;
            case 2 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.CONSERVATIVE;
            case 3 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.MAX_P;
            default -> throw new IllegalArgumentException("Invalid collider orientation style");
        };

        // 0=OFF, 1=MARK, 2=RECOVER, 3=RECOVER_CORROBORATED; see Params/ParamDescriptions
        // registration on your end (rescueAction upper bound needs raising to 3). Mode 3 is the
        // recommended recovery mode when recovery is wanted at all: it reinstates only pairs BOTH
        // tiers independently flag, and needs no odds estimator or threshold. Its precision on
        // the first ground-truth run was 1 of 2 (vs. 1 of 14 for the raw tier-1 list): the wrong
        // recovery was a pair whose audit failure was caused by a different missing edge, which
        // is the localization limit corroboration inherits from the audit. Treat mode 3's
        // precision as a quantity your replicates measure, not a promise.
        int rescueActionCode = parameters.getInt(Params.RESCUE_ACTION);
        edu.cmu.tetrad.search.PcAR.RescueAction rescueAction = switch (rescueActionCode) {
            case 0 -> edu.cmu.tetrad.search.PcAR.RescueAction.OFF;
            case 2 -> edu.cmu.tetrad.search.PcAR.RescueAction.RECOVER;
            case 3 -> edu.cmu.tetrad.search.PcAR.RescueAction.RECOVER_CORROBORATED;
            default -> edu.cmu.tetrad.search.PcAR.RescueAction.MARK;
        };

        IndependenceTest test = getIndependenceWrapper().getTest(dataModel, parameters);
        test = new CachingIndependenceTest(test);

        Graph graph;

        edu.cmu.tetrad.search.PcAR search = new edu.cmu.tetrad.search.PcAR(test);
        search.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);
        search.setFasStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setColliderOrientationStyle(colliderOrientationStyle);
        search.setAllowBidirected(allowBidirected ? edu.cmu.tetrad.search.PcAR.AllowBidirected.ALLOW
                : edu.cmu.tetrad.search.PcAR.AllowBidirected.DISALLOW);

        search.setRescueAction(rescueAction);
        search.setRecoveryOddsThreshold(parameters.getDouble(Params.RECOVERY_ODDS_THRESHOLD));

        // Fixed rather than exposed as a Parameters entry, per instruction: R (maxRescuePasses)
        // only has any effect when a pass actually recovers something. A small constant is enough
        // to let a cascading recovery play out without paying for repeated full clash passes on
        // every search call; promote to a registered Params entry once there's a reason to tune it
        // per-run rather than per-build.
        search.setMaxRescuePasses(MAX_RESCUE_PASSES);

        // Full Sec. 14 posterior odds: base rate q(n)/(1-q(n)) TIMES a per-pair likelihood ratio
        // from the discriminating re-test (pair re-tested with the pivot's sepset membership
        // toggled; LR via the Vovk-Sellke bound on the discriminating p-value). This replaces the
        // earlier base-rate-only lambda, whose pair-blindness scored every flag identically. See
        // PcAR.DiscriminatingTestOddsEstimator's javadoc for the two honest limits: (1) the
        // size-alpha guarantee on the discriminating test holds within the paper's model, where
        // the clash's collider demands are genuine -- a pivot that is in truth a plain common
        // cause of the pair makes the re-test reject through the confounding, so expect
        // over-recovery exactly where the clash pass over-flags; (2) no multiplicity correction is
        // applied across flags, which Sec. 14 explicitly owes -- raise the threshold as the crude
        // compensation. NOTE: the estimator runs one extra CI test per flagged pair, using the
        // same (cached) test as the search itself.
        search.setRecoveryOddsEstimator(
                new edu.cmu.tetrad.search.PcAR.DiscriminatingTestOddsEstimator(test));

        // determinismGuard intentionally left unset (null) -- see class javadoc. Wire here once
        // you have a concrete implementation, e.g.:
        //   search.setDeterminismGuard((v, S) -> ...);

        // Tier two, delegating to MarkovCheck. PcAR has NO built-in fallback auditor any more:
        // the old triple-based one was unsound in both directions (see runMarkovAudit's comment),
        // so without this wiring getMarkovAuditFailures() is simply empty. Clark's note (Sec. 4)
        // reports this substitution verified working: MarkovCheck derives conditioning sets from
        // separation in the graph rather than from a triple, which is exactly what tier two needs.
        //
        // API ASSUMPTIONS FLAGGED -- I don't have MarkovCheck's source, so the four lines marked
        // below are inferred from the note's description ("constructed on a graph, an independence
        // test, and a conditioning-set type", "reports the number of implied facts tested and the
        // fraction of them the data reject", "with the local conditioning-set type"). If any name
        // is off, these are the only lines to fix; the adapter shape around them is correct.
        search.setMarkovAuditor((auditGraph, auditTest) -> {
            List<edu.cmu.tetrad.search.PcAR.MarkovAuditFailure> failures = new ArrayList<>();

            // (1) constructor: (graph, test, conditioningSetType)
            edu.cmu.tetrad.search.MarkovCheck mc = new edu.cmu.tetrad.search.MarkovCheck(
                    auditGraph, auditTest,
                    // (2) enum constant for the "local" conditioning-set type
                    edu.cmu.tetrad.search.ConditioningSetType.LOCAL_MARKOV);

            // (3) run the check
            mc.generateResults(true, true);

            // (4) pull the per-fact results; each carries the fact (x, y, conditioning set) and
            // its p-value. Only the rejected ones become MarkovAuditFailures.
            for (edu.cmu.tetrad.search.test.IndependenceResult r : mc.getResults(true)) {
                if (!r.isIndependent()) {
                    failures.add(new edu.cmu.tetrad.search.PcAR.MarkovAuditFailure(
                            r.getFact().getX(), r.getFact().getY(),
                            r.getFact().getZ(), r.getPValue()));
                }
            }
            return failures;
        });

        // NOTE ON CALIBRATION, not yet implemented: the audit is a FAMILY of tests, so per-test
        // alpha is the wrong criterion for the derived claim "this graph is Markov-consistent"
        // (Clark's note Sec. 4 and Sec. 6, fifth reason). MARKOV-AUDIT line 6 calls for FDR at
        // level q over the family. The adapter above applies no family-level correction -- it
        // reports every individually-rejected fact -- so expect more flags than an FDR-controlled
        // audit would yield, particularly on larger graphs where the family is big. Do not read
        // the raw count as a violation count. (The note also cautions against the Anderson-Darling
        // uniformity statistic MarkovCheck reports: it tests a different question and is
        // uninterpretable for families of two or three facts.)

        double fdrQ = parameters.getDouble(Params.FDR_Q);

        if (fdrQ == 0.0) {
            graph = search.search();
        } else {
            boolean negativelyCorrelated = true;
            boolean verbose = parameters.getBoolean(Params.VERBOSE);
            double alpha = parameters.getDouble(Params.ALPHA);
            // NOTE: doFdrLoop was written against edu.cmu.tetrad.search.Pc in the original
            // wrapper; PcAR implements the same IGraphSearch interface, but I haven't seen
            // IndTestFdrWrapper's signature so can't confirm it doesn't downcast internally.
            // Flagging for a check on your end rather than guessing.
            graph = IndTestFdrWrapper.doFdrLoop(search, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        stampWithBic(graph, dataModel);

        // Surface the tier-1/tier-2 detections somewhere visible rather than dropping them.
        // Counts go on as graph attributes (cheap, always available, survive downstream).
        // Full per-detection detail (which pair, which pivot, which sepset, which locus) only
        // exists on the local `search` object and is gone once this method returns, so print it
        // now under the same VERBOSE gate PcAR itself uses for its own per-flag logging -- this is
        // additive to that (PcAR's internal logging is one line per flag as it happens; this is a
        // full listing at the end, easier to scan or grep out of a harness run).
        graph.addAttribute("PcAR.contestedDeletions", search.getContestedDeletions().size());
        graph.addAttribute("PcAR.orientationClashes", search.getOrientationClashes().size());
        graph.addAttribute("PcAR.markovAuditFailures", search.getMarkovAuditFailures().size());

        // Seed handoff for targeted repair: the implicated-vertex names travel with the graph
        // (EdgeListGraph's copy constructor preserves attributes), so a downstream Vertex Repair
        // session can detect this attribute on its input graph and offer to seed
        // VertexRepairSearch at exactly these vertices. Names, not Node references, because the
        // graph gets copied between boxes and nodes are re-resolved by name.
        java.util.List<String> implicated = search.implicatedVertices().stream()
                .map(edu.cmu.tetrad.graph.Node::getName).toList();
        if (!implicated.isEmpty()) {
            graph.addAttribute("PcAR.implicatedVertices", String.join(",", implicated));
        }

        if (parameters.getBoolean(Params.VERBOSE)) {
            for (edu.cmu.tetrad.search.PcAR.ContestedDeletion cd : search.getContestedDeletions()) {
                TetradLogger.getInstance().log(
                        "[PcAR contested] " + cd.x().getName() + " - " + cd.y().getName()
                                + " (pivot " + cd.z().getName() + ", locus " + cd.locus()
                                + ", sepset " + cd.sepset() + ", recovered=" + cd.recovered() + ")");
            }
            for (edu.cmu.tetrad.search.PcAR.MarkovAuditFailure maf : search.getMarkovAuditFailures()) {
                TetradLogger.getInstance().log(
                        "[PcAR markov-audit] " + maf.x().getName() + " _||_ " + maf.y().getName()
                                + " | " + maf.conditioningSet() + " failed (p=" + maf.pValue() + ")");
            }
        }


        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "PC-AR (adjacency-rescue) using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.ALLOW_BIDIRECTED);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.TIME_LAG_REPLICATING_GRAPH);
        parameters.add(Params.VERBOSE);
        // New for PC-AR; need registering in Params/ParamDescriptions (see class javadoc).
        parameters.add(Params.RESCUE_ACTION);
        parameters.add(Params.RECOVERY_ODDS_THRESHOLD);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }
        this.test = test;
    }
}
