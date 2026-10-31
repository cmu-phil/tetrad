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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.ejml.data.DMatrixRMaj;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides, empirically, whether evaluating a covariate-adjustment functional at the RICF-fitted covariance matrix
 * beats evaluating it at the sample covariance matrix, when the causal structure is known as a PAG.
 * <p>
 * The question this answers. Covariate adjustment in a PAG uses one adjustment set and one regression, and so
 * discards the other constraints the PAG imposes on the covariance matrix. The alternative is to fit the Gaussian
 * ancestral graph model by RICF, obtaining the maximum likelihood estimate Sigma-hat, and to evaluate the same
 * adjustment functional there. That is the only route by which the idea behind G-regression -- use the whole
 * model-implied covariance rather than one marginal regression (Guo and Perković, 2022) -- can be carried to the
 * PAG case, since the bucket decomposition their estimator relies on has no PAG analogue. Under Gaussian errors
 * the fitted estimator should be at least as efficient; the point of this harness is to find out whether the
 * margin is large enough to be worth having. If the ratio comes out near one, there is no case for it.
 * <p>
 * What is held fixed, so that only the estimator is being compared. Both estimators are given the <i>same</i>
 * adjustment set, found once per pair in the true PAG, and the same data. The true PAG is used rather than the
 * output of a search, so that search error does not enter; errors are Gaussian, which is where the fitted
 * estimator's claim is strongest. Both are therefore consistent, and the comparison is purely one of variance.
 * Only pairs amenable with respect to the PAG are used, since neither estimator is defined otherwise.
 * <p>
 * What is reported, and how to read it. For each stratum, the root mean squared error of both estimators and
 * their ratio, plus -- following Table 1 of Guo and Perković -- the geometric mean and median of the per-pair
 * ratio of squared errors, and the number of pairs on which the two estimators returned the same number.
 * <p>
 * The stratification matters. Where the adjustment functional touches only entries of Sigma that the model
 * leaves unconstrained, the fit reproduces the sample covariance there and the two estimators are the same
 * arithmetic, so the pair is a tie carrying no information. Averaging those in dilutes any real effect toward
 * one, which is why the headline figure is the ratio on pairs with two or more valid adjustment sets. The RMSE
 * ratio is a function of sums of squares and so tail-sensitive; a percentile bootstrap over pairs gives an
 * interval for it.
 * <p>
 * The invariance check is the other thing to read. The spread of the estimate across several valid adjustment
 * sets should be zero at Sigma-hat -- every valid set agrees at a point of the model -- and is generally not
 * zero at S. A nonzero spread at Sigma-hat has three possible causes which a mean cannot separate, so the
 * maximum is reported, the worst pairs are named, and the per-set estimates for the worst are printed: one set
 * far from the others suggests that set does not satisfy the generalized adjustment criterion, which would be a
 * defect in the adjustment machinery rather than in this estimator; estimates merely scattered suggest the
 * functional is ill-conditioned at the fit; and a large value at one pair only suggests an outlier. A spread
 * near zero at S as well means the pairs drawn are not discriminating, and the simulation needs denser graphs
 * or smaller samples.
 * <p>
 * Finally the bookkeeping: pairs skipped as not amenable, pairs with no adjustment set found, replications with
 * no bidirected edge in the PAG, and RICF failures to converge.
 * <p>
 * This class is a {@code main}; it fits no model into Tetrad's search interfaces and nothing calls it.
 * <pre>
 *   java edu.cmu.tetrad.search.PagSigmaHatHarness --reps 20 --n 500 --nodes 14 --latents 3 --edges 24
 * </pre>
 * Options: {@code --reps}, {@code --n} (sample size), {@code --nodes} (total, including latents),
 * {@code --latents}, {@code --edges}, {@code --seed}, {@code --maxSets} (adjustment sets for the spread
 * diagnostic), {@code --tol} and {@code --maxIters} (RICF), {@code --allowNoArcs} to keep replications whose
 * PAG has no bidirected edge (by default those are skipped, since they are not the case of interest),
 * {@code --bootstraps} (resamples for the interval, 0 to skip), {@code --worst} (how many high-spread pairs to
 * list), {@code --tieTol} (relative tolerance for calling two estimates identical), {@code --validTol} (absolute
 * tolerance for the population audit), {@code --colliderPolicy} (OFF, PREFER_NONCOLLIDERS or NONCOLLIDER_FIRST),
 * and {@code --dump rep:x:y}, repeatable, which prints a full case file for that pair when the run reaches it --
 * the graphs, the paths, and a check of every returned adjustment set against the true DAG. Same seed, same
 * case: {@code --seed 1848291 --reps 200 --dump 189:X9:X12 --dump 71:X10:X11}.
 *
 * @author josephramsey
 * @see RicfEjml
 * @see RecursiveAdjustment
 */
public final class PagSigmaHatHarness {

    /**
     * Private constructor; this class is a main.
     */
    private PagSigmaHatHarness() {
    }

    /**
     * Runs the comparison.
     *
     * @param args see the class documentation.
     * @throws ParseException if {@link SemIm#simulateData(int, boolean)} does, which it declares.
     */
    public static void main(String[] args) throws ParseException {
        int reps = 20;
        int n = 500;
        int nodes = 14;
        int latents = 3;
        int edges = 24;
        long seed = 1848291L;
        int maxSets = 5;
        double tol = RicfEjml.DEFAULT_TOL;
        int maxIters = RicfEjml.DEFAULT_MAX_ITERS;
        boolean allowNoArcs = false;
        int bootstraps = 2000;
        int worst = 5;
        double tieTol = 1e-12;
        double validTol = 1e-6;
        RecursiveAdjustment.ColliderPolicy colliderPolicy = RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST;
        Set<String> dumpKeys = new HashSet<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--reps" -> reps = Integer.parseInt(args[++i]);
                case "--n" -> n = Integer.parseInt(args[++i]);
                case "--nodes" -> nodes = Integer.parseInt(args[++i]);
                case "--latents" -> latents = Integer.parseInt(args[++i]);
                case "--edges" -> edges = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--maxSets" -> maxSets = Integer.parseInt(args[++i]);
                case "--tol" -> tol = Double.parseDouble(args[++i]);
                case "--maxIters" -> maxIters = Integer.parseInt(args[++i]);
                case "--allowNoArcs" -> allowNoArcs = true;
                case "--bootstraps" -> bootstraps = Integer.parseInt(args[++i]);
                case "--worst" -> worst = Integer.parseInt(args[++i]);
                case "--tieTol" -> tieTol = Double.parseDouble(args[++i]);
                case "--validTol" -> validTol = Double.parseDouble(args[++i]);
                case "--colliderPolicy" ->
                        colliderPolicy = RecursiveAdjustment.ColliderPolicy.valueOf(args[++i]);
                case "--dump" -> dumpKeys.add(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        RandomUtil.getInstance().setSeed(seed);

        System.out.printf(Locale.ROOT,
                "PAG Sigma-hat vs S, same adjustment set.%n"
                + "reps=%d n=%d nodes=%d (latents=%d) edges=%d seed=%d maxSets=%d colliderPolicy=%s%n%n",
                reps, n, nodes, latents, edges, seed, maxSets, colliderPolicy);
        System.out.println("Each pair: regress y on {x} + Z, once with S and once with the RICF fit Sigma-hat.");
        System.out.println("Truth is the total effect in the generating DAG. Gaussian errors throughout.");
        System.out.println();

        // Every comparable pair is kept, so the summary can stratify by the number of adjustment sets, count
        // the pairs where the two estimators agree arithmetically, and bootstrap the ratio over pairs.
        List<PairResult> results = new ArrayList<>();

        int skippedNoArcs = 0;
        int skippedBadMag = 0;
        int notAmenable = 0;
        int noAdjustmentSet = 0;
        int ricfNotConverged = 0;
        int roundTripFailures = 0;
        int repsUsed = 0;
        int setsChecked = 0;
        int setsInvalid = 0;
        double maxFitMovement = 0.0;

        for (int rep = 1; rep <= reps; rep++) {
            Graph dag = RandomGraph.randomDag(nodes, latents, edges, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            List<Node> all = im.getVariableNodes();
            Matrix truth = Matrix.identity(all.size()).minus(new Matrix(im.getEdgeCoef())).inverse();

            Graph pag = GraphTransforms.dagToPag(dag, false);

            int arcs = 0;
            for (Edge edge : pag.getEdges()) if (Edges.isBidirectedEdge(edge)) arcs++;

            if (arcs == 0 && !allowNoArcs) {
                skippedNoArcs++;
                System.out.printf(Locale.ROOT, "rep %3d: skipped, no bidirected edge in the PAG%n", rep);
                continue;
            }

            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            boolean magOk = true;
            for (Edge edge : mag.getEdges()) {
                if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge))) magOk = false;
            }

            if (!magOk) {
                skippedBadMag++;
                System.out.printf(Locale.ROOT, "rep %3d: skipped, MAG from PAG has an unusable edge%n", rep);
                continue;
            }

            // Does the fitted MAG actually lie in the input PAG's equivalence class? If not, Sigma-hat is the
            // MLE of the wrong model and every claim here fails at the first step. zhangMagFromPag has a
            // recorded history of producing graphs outside the class on circle-rich PAGs, so this is checked
            // rather than assumed.
            boolean roundTrip = sameMarks(pag, GraphTransforms.magToPag(mag, false, 15));
            if (!roundTrip) {
                roundTripFailures++;
                System.out.printf(Locale.ROOT, "rep %3d: WARNING, MAG does not round-trip to the input PAG%n",
                        rep);
            }

            repsUsed++;

            DataSet data = im.simulateData(n, false);
            ICovarianceMatrix cov = new CovarianceMatrix(data);
            Matrix s = cov.getMatrix();

            Map<String, Integer> idx = new HashMap<>();
            List<String> names = cov.getVariableNames();
            for (int i = 0; i < names.size(); i++) idx.put(names.get(i), i);

            // The population covariance of the measured variables, in the same order as the sample one, so that
            // every adjustment set handed back can be audited: a valid set must recover the true total effect
            // exactly here. This is the definition of validity instantiated for this DAG, and it settles the
            // question without any appeal to what the fitted Sigma-hat is or is not a point of.
            Map<String, Node> measuredByName = new HashMap<>();
            for (Node node : im.getMeasuredNodes()) measuredByName.put(node.getName(), node);

            List<Node> ordered = new ArrayList<>();
            for (String name : names) ordered.add(measuredByName.get(name));
            Matrix popSigma = im.getImplCovar(ordered);

            RicfEjml.RicfResult fit = new RicfEjml().ricf(mag, cov, tol, maxIters);
            if (fit.getDiff() > tol) ricfNotConverged++;

            // RICF stops on the parameter change over one sweep, which can be small while the fit is still far
            // from stationary. Refitting much more tightly and measuring how far Sigma-hat moves is a surrogate
            // for stationarity: if it moves, the first fit was not a point of the model, which would by itself
            // explain a nonzero spread across adjustment sets.
            RicfEjml.RicfResult tightFit = new RicfEjml().ricf(mag, cov, tol * 1e-4, maxIters * 10);

            DMatrixRMaj fitted = fit.getSigmaHat();
            int p = names.size();
            Matrix sigmaHat = new Matrix(p, p);
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < p; j++) sigmaHat.set(i, j, fitted.get(i, j));
            }

            DMatrixRMaj fittedTight = tightFit.getSigmaHat();
            Matrix sigmaHatTight = new Matrix(p, p);
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < p; j++) sigmaHatTight.set(i, j, fittedTight.get(i, j));
            }

            double movement = 0.0;
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < p; j++) {
                    movement = Math.max(movement, Math.abs(sigmaHat.get(i, j) - sigmaHatTight.get(i, j)));
                }
            }
            maxFitMovement = Math.max(maxFitMovement, movement);

            double repSseS = 0.0;
            double repSseSigma = 0.0;
            int repPairs = 0;

            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y)) continue;
                    if (!pag.paths().possibleAncestor(x, y)) continue;

                    if (!pag.paths().isGraphAmenable(x, y, "PAG", -1, Set.of())) {
                        notAmenable++;
                        continue;
                    }

                    List<Set<Node>> sets = adjustmentSets(pag, x, y, maxSets, colliderPolicy);

                    if (sets.isEmpty()) {
                        noAdjustmentSet++;
                        continue;
                    }

                    Set<Node> z = sets.get(0);
                    double tauS = effect(s, idx, x, z, y);
                    double tauSigma = effect(sigmaHat, idx, x, z, y);
                    double t = truth.get(all.indexOf(dag.getNode(x.getName())),
                            all.indexOf(dag.getNode(y.getName())));

                    // Audit every returned set against the population covariance.
                    int invalidSets = 0;
                    boolean firstSetValid = true;

                    for (int k = 0; k < sets.size(); k++) {
                        double tauPop = effect(popSigma, idx, x, sets.get(k), y);
                        boolean ok = Math.abs(tauPop - t) <= validTol;
                        if (!ok) {
                            invalidSets++;
                            if (k == 0) firstSetValid = false;
                        }
                    }

                    setsChecked += sets.size();
                    setsInvalid += invalidSets;

                    if (dumpKeys.contains(rep + ":" + x.getName() + ":" + y.getName())) {
                        dumpCase(rep, dag, im, all, pag, mag, x, y, sets, popSigma, idx, t, validTol);
                    }

                    double spreadS = 0.0;
                    double spreadSigma = 0.0;
                    double spreadSigmaTight = 0.0;
                    String detail = null;

                    if (sets.size() >= 2) {
                        spreadS = spread(s, idx, x, sets, y);
                        spreadSigma = spread(sigmaHat, idx, x, sets, y);
                        spreadSigmaTight = spread(sigmaHatTight, idx, x, sets, y);
                    }

                    if (sets.size() >= 2 || invalidSets > 0) {
                        detail = perSetDetail(sigmaHat, sigmaHatTight, s, popSigma, idx, x, sets, y, t, validTol);
                    }

                    PairResult result = new PairResult(rep,
                            "rep " + rep + " " + x.getName() + " -> " + y.getName(),
                            tauS, tauSigma, t, sets.size(), spreadS, spreadSigma, spreadSigmaTight,
                            roundTrip, invalidSets, firstSetValid, detail);

                    results.add(result);
                    repSseS += result.errS();
                    repSseSigma += result.errSigma();
                    repPairs++;
                }
            }

            if (repPairs == 0) {
                System.out.printf(Locale.ROOT, "rep %3d: arcs=%2d, no usable pairs%n", rep, arcs);
            } else {
                System.out.printf(Locale.ROOT,
                        "rep %3d: arcs=%2d pairs=%3d  rmse(S)=%.4f  rmse(Sigma-hat)=%.4f  ratio=%.3f  "
                        + "ricf iters=%d%n",
                        rep, arcs, repPairs, Math.sqrt(repSseS / repPairs),
                        Math.sqrt(repSseSigma / repPairs),
                        Math.sqrt(repSseSigma / Math.max(repSseS, 1e-300)), fit.getIterations());
            }
        }

        System.out.println();
        System.out.println("---- summary ----");

        if (results.isEmpty()) {
            System.out.println("No usable pairs. Try more replications, denser graphs, or --allowNoArcs.");
            return;
        }

        report("all pairs", results, tieTol);

        // The stratification that matters is tied versus untied. A pair where the two estimators return the
        // same number carries no information about either, and averaging those in pulls every ratio toward one.
        // (An earlier version stratified on the number of adjustment sets, on the theory that the gain came
        // from invariance across sets; the data did not support that, so the sets split is kept below only as
        // a secondary cut.)
        List<PairResult> untied = new ArrayList<>();
        List<PairResult> tied = new ArrayList<>();
        for (PairResult r : results) (r.tie(tieTol) ? tied : untied).add(r);

        System.out.println();
        report("untied pairs (the informative ones)", untied, tieTol);
        System.out.println();
        report("tied pairs", tied, tieTol);

        List<PairResult> multi = new ArrayList<>();
        List<PairResult> single = new ArrayList<>();
        for (PairResult r : results) (r.numSets() >= 2 ? multi : single).add(r);

        System.out.println();
        report("secondary cut: pairs with 2+ adjustment sets", multi, tieTol);
        System.out.println();
        report("secondary cut: pairs with exactly 1 adjustment set", single, tieTol);

        // And a cut on whether the replication's MAG was in the input PAG's class at all. If the gain lives in
        // the replications that failed the round trip, it is an artifact of fitting the wrong model.
        List<PairResult> roundTripOk = new ArrayList<>();
        List<PairResult> roundTripBad = new ArrayList<>();
        for (PairResult r : results) (r.roundTrip() ? roundTripOk : roundTripBad).add(r);

        if (!roundTripBad.isEmpty()) {
            System.out.println();
            report("pairs whose MAG round-tripped", roundTripOk, tieTol);
            System.out.println();
            report("pairs whose MAG did NOT round-trip", roundTripBad, tieTol);
        }

        // The audit. A pair with an invalid set among those returned tells us nothing about efficiency, since
        // both estimators are then estimating the wrong quantity; keep those out of the headline.
        List<PairResult> cleanSets = new ArrayList<>();
        List<PairResult> dirtySets = new ArrayList<>();
        for (PairResult r : results) (r.numInvalidSets() == 0 ? cleanSets : dirtySets).add(r);

        if (!dirtySets.isEmpty()) {
            System.out.println();
            report("pairs where every returned set passed the audit", cleanSets, tieTol);
            System.out.println();
            report("pairs with at least one INVALID set", dirtySets, tieTol);
        }

        // Bootstrap the RMSE ratio, resampling replications rather than pairs: pairs within a replication share
        // a graph, a data set and one RICF fit, so treating them as independent gives an interval that is too
        // narrow. Untied pairs only, since the tied ones contribute equal terms to both sums.
        if (bootstraps >= 2 && untied.size() >= 2) {
            double[] ci = bootstrapRatioCi(untied, bootstraps);
            System.out.println();
            System.out.printf(Locale.ROOT,
                    "RMSE ratio on untied pairs, cluster bootstrap over replications: "
                    + "95%% interval [%.4f, %.4f] from %d resamples%n", ci[0], ci[1], bootstraps);
            if (ci[0] <= 1.0 && ci[1] >= 1.0) {
                System.out.println("  The interval covers 1, so this run does not establish a difference.");
            }
        }

        // The invariance check. A nonzero spread at Sigma-hat means either RICF has not really reached a point
        // of the model, or the functional is ill-conditioned there, or one of the sets is not GAC-valid -- and
        // the last of those would be a defect in the adjustment machinery, not in this estimator. A mean cannot
        // tell these apart, so report the maximum and name the pairs.
        List<PairResult> spreadPairs = new ArrayList<>(multi);
        System.out.println();

        if (spreadPairs.isEmpty()) {
            System.out.println("No pair had two or more adjustment sets, so the spread diagnostic is empty.");
        } else {
            double sumS = 0.0;
            double sumSigma = 0.0;
            double sumSigmaTight = 0.0;
            for (PairResult r : spreadPairs) {
                sumS += r.spreadS();
                sumSigma += r.spreadSigma();
                sumSigmaTight += r.spreadSigmaTight();
            }

            spreadPairs.sort(Comparator.comparingDouble(PairResult::spreadSigma).reversed());

            System.out.printf(Locale.ROOT,
                    "spread over valid adjustment sets (%d pairs): mean at S %.5f, at Sigma-hat %.3g, "
                    + "at tight Sigma-hat %.3g%n",
                    spreadPairs.size(), sumS / spreadPairs.size(), sumSigma / spreadPairs.size(),
                    sumSigmaTight / spreadPairs.size());
            System.out.printf(Locale.ROOT,
                    "  max spread at Sigma-hat: %.3g, at tight Sigma-hat: %.3g   (both should be ~0)%n",
                    spreadPairs.getFirst().spreadSigma(),
                    spreadPairs.stream().mapToDouble(PairResult::spreadSigmaTight).max().orElse(0.0));

            int show = Math.min(worst, spreadPairs.size());
            System.out.printf(Locale.ROOT, "  worst %d by spread at Sigma-hat:%n", show);

            for (int i = 0; i < show; i++) {
                PairResult r = spreadPairs.get(i);
                System.out.printf(Locale.ROOT,
                        "    %-28s sets=%d  Sigma %.3g  tight %.3g  S %.3g  roundTrip=%b%n",
                        r.label(), r.numSets(), r.spreadSigma(), r.spreadSigmaTight(), r.spreadS(),
                        r.roundTrip());
            }

            PairResult top = spreadPairs.getFirst();
            if (top.detail() != null) {
                System.out.println("  per-set estimates for the worst pair, " + top.label() + ":");
                System.out.print(top.detail());
                System.out.println("    Diagnosis, in order of what to rule out: if roundTrip is false the MAG");
                System.out.println("    was outside the PAG's class and Sigma-hat is the MLE of the wrong");
                System.out.println("    model; else if the tight column collapses toward agreement, the looser");
                System.out.println("    fit had not converged and the stopping rule is the problem; else one");
                System.out.println("    set disagreeing with the rest at a converged fit means that set does");
                System.out.println("    not satisfy the generalized adjustment criterion, which would be a");
                System.out.println("    defect in RecursiveAdjustment and would affect the shipped adjustment");
                System.out.println("    tool as well, not just this estimator.");
            }
        }

        System.out.println();
        System.out.printf(Locale.ROOT,
                "skipped: %d reps with no bidirected edge, %d with an unusable MAG%n",
                skippedNoArcs, skippedBadMag);
        System.out.printf(Locale.ROOT,
                "pairs dropped: %d not amenable, %d with no adjustment set found%n",
                notAmenable, noAdjustmentSet);
        System.out.printf(Locale.ROOT, "RICF did not reach tolerance in %d replication(s)%n", ricfNotConverged);
        System.out.printf(Locale.ROOT,
                "MAG did not round-trip to the input PAG in %d of %d replication(s) used%n",
                roundTripFailures, repsUsed);
        System.out.printf(Locale.ROOT,
                "largest movement of Sigma-hat between the two tolerances: %.3g   (a stationarity surrogate)%n",
                maxFitMovement);
        System.out.printf(Locale.ROOT,
                "ADJUSTMENT SET AUDIT: %d of %d returned sets failed at the population covariance (%.1f%%)%n",
                setsInvalid, setsChecked, setsChecked == 0 ? 0.0 : 100.0 * setsInvalid / setsChecked);

        int firstSetBad = 0;
        for (PairResult r : results) if (!r.firstSetValid()) firstSetBad++;

        System.out.printf(Locale.ROOT,
                "                      %d of %d pairs used an invalid set for the reported estimate%n",
                firstSetBad, results.size());

        if (setsInvalid > 0) {
            List<PairResult> offenders = new ArrayList<>();
            for (PairResult r : results) if (r.numInvalidSets() > 0) offenders.add(r);
            offenders.sort(Comparator.comparingInt(PairResult::numInvalidSets).reversed());

            System.out.printf(Locale.ROOT, "  first %d offending pair(s):%n",
                    Math.min(worst, offenders.size()));

            for (int i = 0; i < Math.min(worst, offenders.size()); i++) {
                PairResult r = offenders.get(i);
                System.out.printf(Locale.ROOT, "    %-28s %d of %d set(s) invalid, used-set valid=%b%n",
                        r.label(), r.numInvalidSets(), r.numSets(), r.firstSetValid());
            }

            PairResult first = offenders.getFirst();
            if (first.detail() != null) {
                System.out.println("  per-set detail for " + first.label() + ":");
                System.out.print(first.detail());
            }

            System.out.println("  A set that does not recover the true effect at the POPULATION covariance is");
            System.out.println("  not a valid adjustment set for this DAG, hence not for the PAG. With the MAG");
            System.out.println("  round-tripping and the fit stationary, that is the remaining explanation for a");
            System.out.println("  nonzero spread at Sigma-hat -- and it is a defect in the adjustment search,");
            System.out.println("  not in this estimator. RecursiveAdjustment is left at its default RaMode.VALID");
            System.out.println("  here, the same as the Linear Adjustment Effects tool uses, so if sets fail");
            System.out.println("  this audit the shipped tool can return them too. The one setting that differs");
            System.out.println("  is the ColliderPolicy argument: NONCOLLIDER_FIRST here, copied from");
            System.out.println("  PdagPagIda, against OFF in the GUI tool. Rerun with --colliderPolicy OFF to");
            System.out.println("  see whether that is what makes the difference.");
        }
        System.out.println();
        System.out.println("Read this as: the untied stratum carries the answer, since a tied pair is the same");
        System.out.println("arithmetic twice and tells you nothing. A ratio near 1 there means the fitted");
        System.out.println("covariance buys nothing and the existing Linear Adjustment Effects tool is all that");
        System.out.println("is needed. Settle the two lines above before reading any ratio, though: a round-trip");
        System.out.println("failure means the wrong model was fitted, and a large movement between tolerances");
        System.out.println("means the fit was not a point of any model. Note also that these are Gaussian");
        System.out.println("errors, the case most favorable to Sigma-hat, and the true PAG rather than a search");
        System.out.println("result; under non-Gaussian errors the fitted estimator stays consistent but loses");
        System.out.println("its efficiency argument, and with an estimated PAG structure error enters as well.");
    }

    /**
     * Prints the error summary for one stratum: the two root mean squared errors and their ratio, the
     * geometric mean and median of the per-pair squared-error ratio, and the count of pairs where the two
     * estimators agreed to within the tie tolerance -- for which the comparison carries no information.
     */
    private static void report(String name, List<PairResult> results, double tieTol) {
        System.out.printf(Locale.ROOT, "%s: %d pair(s)%n", name, results.size());

        if (results.isEmpty()) return;

        double sseS = 0.0;
        double sseSigma = 0.0;
        int ties = 0;
        List<Double> ratios = new ArrayList<>();

        for (PairResult r : results) {
            sseS += r.errS();
            sseSigma += r.errSigma();
            if (r.tie(tieTol)) ties++;
            if (r.errS() > 0.0) ratios.add(r.errSigma() / r.errS());
        }

        double rmseS = Math.sqrt(sseS / results.size());
        double rmseSigma = Math.sqrt(sseSigma / results.size());

        System.out.printf(Locale.ROOT, "  RMSE at S %.5f, at Sigma-hat %.5f, ratio %.4f  (< 1 favors Sigma)%n",
                rmseS, rmseSigma, rmseSigma / Math.max(rmseS, 1e-300));

        if (!ratios.isEmpty()) {
            List<Double> sorted = new ArrayList<>(ratios);
            Collections.sort(sorted);
            double logSum = 0.0;
            for (double r : ratios) logSum += Math.log(Math.max(r, 1e-300));
            System.out.printf(Locale.ROOT,
                    "  per-pair squared-error ratio: geometric mean %.4f, median %.4f (n=%d)%n",
                    Math.exp(logSum / ratios.size()), sorted.get(sorted.size() / 2), ratios.size());
        }

        System.out.printf(Locale.ROOT,
                "  identical estimates: %d of %d (%.1f%%) -- no information in those%n",
                ties, results.size(), 100.0 * ties / results.size());
    }

    /**
     * A percentile bootstrap interval for the RMSE ratio, resampling whole replications with replacement. The
     * cluster is the replication because pairs drawn from one replication share a graph, a data set and a
     * single RICF fit, and so are not independent.
     *
     * @return the 2.5 and 97.5 percentiles.
     */
    private static double[] bootstrapRatioCi(List<PairResult> results, int bootstraps) {
        Map<Integer, List<PairResult>> byRep = new LinkedHashMap<>();
        for (PairResult r : results) byRep.computeIfAbsent(r.rep(), k -> new ArrayList<>()).add(r);

        List<List<PairResult>> clusters = new ArrayList<>(byRep.values());
        RandomUtil random = RandomUtil.getInstance();
        int m = clusters.size();
        double[] draws = new double[bootstraps];

        for (int b = 0; b < bootstraps; b++) {
            double sseS = 0.0;
            double sseSigma = 0.0;

            for (int i = 0; i < m; i++) {
                for (PairResult r : clusters.get(random.nextInt(m))) {
                    sseS += r.errS();
                    sseSigma += r.errSigma();
                }
            }

            draws[b] = Math.sqrt(sseSigma / Math.max(sseS, 1e-300));
        }

        java.util.Arrays.sort(draws);
        return new double[]{draws[(int) (0.025 * bootstraps)], draws[(int) (0.975 * bootstraps)]};
    }

    /**
     * True if the two graphs have the same nodes by name and the same endpoint marks on every pair, i.e. are the
     * same partial ancestral graph. Used to check that a MAG maps back to the PAG it came from.
     */
    private static boolean sameMarks(Graph a, Graph b) {
        if (a.getNumNodes() != b.getNumNodes()) return false;

        List<Node> nodes = a.getNodes();

        for (Node x : nodes) {
            if (b.getNode(x.getName()) == null) return false;
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x1 = nodes.get(i);
                Node y1 = nodes.get(j);
                Node x2 = b.getNode(x1.getName());
                Node y2 = b.getNode(y1.getName());

                Edge e1 = a.getEdge(x1, y1);
                Edge e2 = b.getEdge(x2, y2);

                if (e1 == null && e2 == null) continue;
                if (e1 == null || e2 == null) return false;

                if (a.getEndpoint(x1, y1) != b.getEndpoint(x2, y2)) return false;
                if (a.getEndpoint(y1, x1) != b.getEndpoint(y2, x2)) return false;
            }
        }

        return true;
    }

    /**
     * One comparable pair: the two estimates, the truth, and the spread over the adjustment sets found.
     *
     * @param rep              the replication, so the bootstrap can resample replications rather than pairs.
     * @param label            rep number and the pair, for naming an outlier.
     * @param tauS             the estimate from the sample covariance.
     * @param tauSigma         the estimate from the RICF fit.
     * @param truth            the total effect in the generating DAG.
     * @param numSets          how many valid adjustment sets were found.
     * @param spreadS          spread of the estimate over those sets at S; 0 if fewer than two.
     * @param spreadSigma      spread at Sigma-hat; should be ~0.
     * @param spreadSigmaTight spread at the tightly refitted Sigma-hat; if much smaller, the looser fit had
     *                         simply not converged.
     * @param roundTrip        whether this replication's MAG mapped back to the input PAG.
     * @param numInvalidSets   how many returned sets failed the population audit, i.e. did not recover the true
     *                         total effect at the population covariance and so are not valid adjustment sets.
     * @param firstSetValid    whether the set actually used for the estimate passed that audit.
     * @param detail           per-set estimates, for diagnosis; null when there is nothing to diagnose.
     */
    private record PairResult(int rep, String label, double tauS, double tauSigma, double truth, int numSets,
                              double spreadS, double spreadSigma, double spreadSigmaTight, boolean roundTrip,
                              int numInvalidSets, boolean firstSetValid, String detail) {

        /**
         * @return the squared error of the sample-covariance estimate.
         */
        private double errS() {
            return (tauS - truth) * (tauS - truth);
        }

        /**
         * @return the squared error of the fitted estimate.
         */
        private double errSigma() {
            return (tauSigma - truth) * (tauSigma - truth);
        }

        /**
         * @param tol relative tolerance.
         * @return true if the two estimators returned the same number, so the pair is uninformative.
         */
        private boolean tie(double tol) {
            return Math.abs(tauSigma - tauS) <= tol * Math.max(1.0, Math.abs(tauS));
        }
    }

    /**
     * Prints a full case file for one pair, for checking by hand. Everything here is computed against the
     * <i>true DAG</i>, latents included, so it adjudicates rather than describes: for each adjustment set
     * returned it reports (a) whether the set contains a node that is forbidden in the true DAG -- a node on a
     * directed path from X to Y, or a descendant of one -- and (b) whether X and Y are d-separated given the set
     * in the true DAG with the edges out of X removed, which is the back-door condition. A set that passes both
     * is valid for this DAG; if such a set still fails the population audit, the audit itself is wrong and the
     * output says so. The directed paths from X to Y are listed with their coefficient products and sum, so the
     * true total effect used throughout can be verified line by line.
     */
    private static void dumpCase(int rep, Graph dag, SemIm im, List<Node> all, Graph pag, Graph mag,
                                 Node x, Node y, List<Set<Node>> sets, Matrix popSigma,
                                 Map<String, Integer> idx, double truth, double validTol) {
        String tag = "rep " + rep + " " + x.getName() + " -> " + y.getName();
        System.out.println();
        System.out.println("================ CASE FILE: " + tag + " ================");

        Node dx = dag.getNode(x.getName());
        Node dy = dag.getNode(y.getName());
        Matrix gamma = im.getEdgeCoef();

        // Latents.
        List<String> latents = new ArrayList<>();
        Set<Node> measured = new HashSet<>(im.getMeasuredNodes());
        for (Node v : all) if (!measured.contains(v)) latents.add(v.getName());
        System.out.println("latent nodes: " + latents);

        // The DAG, with coefficients.
        System.out.println("true DAG (tail -> head : coefficient):");
        List<String> dagLines = new ArrayList<>();
        for (Edge e : dag.getEdges()) {
            Node tail = Edges.getDirectedEdgeTail(e);
            Node head = Edges.getDirectedEdgeHead(e);
            double c = gamma.get(all.indexOf(tail), all.indexOf(head));
            dagLines.add(String.format(Locale.ROOT, "  %s -> %s : %+.4f%s", tail.getName(), head.getName(), c,
                    measured.contains(tail) ? "" : "   (latent tail)"));
        }
        Collections.sort(dagLines);
        dagLines.forEach(System.out::println);

        System.out.println("PAG:");
        sortedEdgeLines(pag).forEach(System.out::println);
        System.out.println("MAG fitted (Zhang):");
        sortedEdgeLines(mag).forEach(System.out::println);

        // Amenability and the visibility of each edge out of x.
        boolean amenable = pag.paths().isGraphAmenable(x, y, "PAG", -1, Set.of());
        System.out.println("PAG amenable for (x, y), no forced visibility: " + amenable);
        for (Node w : pag.getAdjacentNodes(x)) {
            Edge e = pag.getEdge(x, w);
            if (e != null && e.pointsTowards(w)) {
                System.out.printf(Locale.ROOT, "  edge %s out of x: definitely visible = %b%n",
                        e, pag.paths().defVisiblePag(x, w));
            }
        }

        Set<List<Node>> pdPaths = pag.paths().potentiallyDirectedPaths(x, y, -1);
        System.out.println("possibly directed paths x ~> y in the PAG (" + pdPaths.size() + "):");
        int shown = 0;
        for (List<Node> path : pdPaths) {
            if (shown++ >= 20) {
                System.out.println("  ...");
                break;
            }
            System.out.println("  " + names(path));
        }

        // The true total effect, path by path.
        Set<List<Node>> dPaths = dag.paths().directedPaths(dx, dy, -1);
        System.out.println("directed paths x -> y in the true DAG, with coefficient products:");
        double sum = 0.0;
        Set<Node> mediators = new HashSet<>();
        for (List<Node> path : dPaths) {
            double prod = 1.0;
            for (int i = 0; i + 1 < path.size(); i++) {
                prod *= gamma.get(all.indexOf(path.get(i)), all.indexOf(path.get(i + 1)));
            }
            sum += prod;
            for (int i = 1; i < path.size(); i++) mediators.add(path.get(i));
            System.out.printf(Locale.ROOT, "  %-40s %+.6f%n", names(path), prod);
        }
        System.out.printf(Locale.ROOT, "  sum of path products %+.6f ; truth used by the audit %+.6f ; %s%n",
                sum, truth, Math.abs(sum - truth) < 1e-9 ? "agree" : "DISAGREE -- truth computation suspect");

        // Forbidden nodes in the true DAG: mediators (including y) and their descendants, minus y itself.
        Set<Node> forbDag = new HashSet<>(mediators);
        forbDag.addAll(dag.paths().getDescendants(new ArrayList<>(mediators)));
        forbDag.remove(dy);

        // The proper back-door graph: the true DAG with every edge out of x removed.
        Graph backdoor = new EdgeListGraph(dag);
        for (Edge e : new ArrayList<>(backdoor.getEdges(backdoor.getNode(x.getName())))) {
            if (Edges.getDirectedEdgeTail(e).getName().equals(x.getName())) backdoor.removeEdge(e);
        }
        Node bx = backdoor.getNode(x.getName());
        Node by = backdoor.getNode(y.getName());

        System.out.println("adjustment sets returned, each checked against the true DAG:");
        for (Set<Node> z : sets) {
            double tauPop = effect(popSigma, idx, x, z, y);
            boolean auditOk = Math.abs(tauPop - truth) <= validTol;

            Set<Node> zDag = new HashSet<>();
            for (Node v : z) zDag.add(dag.getNode(v.getName()));
            Set<Node> badForb = new HashSet<>(zDag);
            badForb.retainAll(forbDag);

            Set<Node> zBack = new HashSet<>();
            for (Node v : z) zBack.add(backdoor.getNode(v.getName()));
            boolean dSep = backdoor.paths().isMSeparatedFrom(bx, by, zBack, false);

            boolean validInDag = badForb.isEmpty() && dSep;

            System.out.printf(Locale.ROOT, "  Z=%s%n", names(new ArrayList<>(z)));
            System.out.printf(Locale.ROOT, "    population estimate %+.6f vs truth %+.6f : audit says %s%n",
                    tauPop, truth, auditOk ? "VALID" : "INVALID");
            System.out.printf(Locale.ROOT, "    forbidden nodes in Z (on or below a causal path): %s%n",
                    badForb.isEmpty() ? "none" : names(new ArrayList<>(badForb)));
            System.out.printf(Locale.ROOT, "    x d-separated from y given Z, edges out of x removed: %b%n", dSep);
            System.out.printf(Locale.ROOT, "    => by the true DAG this set is %s%n",
                    validInDag ? "VALID" : "INVALID");

            if (validInDag != auditOk) {
                System.out.println("    !! the DAG verdict and the population audit DISAGREE on this set; the audit"
                                   + " or its truth is wrong here, not the adjustment search");
            } else if (!validInDag) {
                System.out.println("    !! confirmed invalid by the true DAG: " + (badForb.isEmpty()
                        ? "an open back-door path survives this Z" : "Z contains a forbidden node"));
            }
        }

        System.out.println("================ end case file ================");
        System.out.println();
    }

    private static List<String> sortedEdgeLines(Graph g) {
        List<String> lines = new ArrayList<>();
        for (Edge e : g.getEdges()) lines.add("  " + e);
        Collections.sort(lines);
        return lines;
    }

    private static String names(List<Node> nodes) {
        List<String> out = new ArrayList<>();
        for (Node n : nodes) out.add(n.getName());
        return String.valueOf(out);
    }

    /**
     * Up to maxNumSets valid adjustment sets for (x, y) in the PAG, with the settings PdagPagIda uses.
     */
    private static List<Set<Node>> adjustmentSets(Graph pag, Node x, Node y, int maxNumSets,
                                                  RecursiveAdjustment.ColliderPolicy colliderPolicy) {
        try {
            return new RecursiveAdjustment(pag).adjustmentSets(x, y, "PAG", maxNumSets,
                    pag.getNumNodes(), 3, -1,
                    colliderPolicy, true, null, null, Set.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * The coefficient of x in the least squares regression of y on {x} union z, computed from the given
     * covariance matrix.
     */
    private static double effect(Matrix sigma, Map<String, Integer> idx, Node x, Set<Node> z, Node y) {
        List<String> w = new ArrayList<>();
        w.add(x.getName());

        Set<String> seen = new LinkedHashSet<>();
        seen.add(x.getName());
        seen.add(y.getName());

        for (Node node : z) {
            if (seen.add(node.getName())) w.add(node.getName());
        }

        int m = w.size();
        Matrix sww = new Matrix(m, m);
        Matrix swy = new Matrix(m, 1);

        for (int i = 0; i < m; i++) {
            int wi = idx.get(w.get(i));
            for (int j = 0; j < m; j++) sww.set(i, j, sigma.get(wi, idx.get(w.get(j))));
            swy.set(i, 0, sigma.get(wi, idx.get(y.getName())));
        }

        return sww.solve(swy).get(0, 0);
    }

    /**
     * The estimate from each adjustment set, at both covariance matrices, one set per line. Printed for the
     * pair with the largest spread at Sigma-hat, where the shape of the disagreement is the diagnosis: one set
     * far from the rest suggests that set is not GAC-valid, whereas estimates merely scattered suggest the
     * functional is ill-conditioned at the fit.
     */
    private static String perSetDetail(Matrix sigma, Matrix sigmaTight, Matrix s, Matrix popSigma,
                                       Map<String, Integer> idx, Node x, List<Set<Node>> sets, Node y,
                                       double truth, double validTol) {
        StringBuilder sb = new StringBuilder();

        for (Set<Node> z : sets) {
            List<String> names = new ArrayList<>();
            for (Node node : z) names.add(node.getName());
            Collections.sort(names);

            double tauPop = effect(popSigma, idx, x, z, y);
            boolean valid = Math.abs(tauPop - truth) <= validTol;

            sb.append(String.format(Locale.ROOT,
                    "    Sigma %+.6f  tight %+.6f  S %+.6f  pop %+.6f  %-7s Z=%s%n",
                    effect(sigma, idx, x, z, y), effect(sigmaTight, idx, x, z, y),
                    effect(s, idx, x, z, y), tauPop, valid ? "VALID" : "INVALID", names));
        }

        sb.append(String.format(Locale.ROOT, "    true total effect %+.6f%n", truth));
        return sb.toString();
    }

    /**
     * The spread (maximum minus minimum) of the estimate over the given adjustment sets, at one covariance
     * matrix.
     */
    private static double spread(Matrix sigma, Map<String, Integer> idx, Node x, List<Set<Node>> sets, Node y) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;

        for (Set<Node> z : sets) {
            double tau = effect(sigma, idx, x, z, y);
            lo = Math.min(lo, tau);
            hi = Math.max(hi, tau);
        }

        return hi - lo;
    }
}
