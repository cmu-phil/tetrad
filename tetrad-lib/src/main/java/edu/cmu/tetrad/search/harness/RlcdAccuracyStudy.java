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

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Rlcd;
import edu.cmu.tetrad.search.rlcd.TestwiseDeletionRankTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A watch-the-output accuracy study of RLCD on linear non-Gaussian data with latent variables, comparing the plain
 * search with the two recent additions: {@link Rlcd#setResolveTwoIndicatorLatents(boolean)} and temporal-tier
 * knowledge, which RLCD now reads as constraining ancestry through latents.
 * <p>
 * <b>Structures.</b>
 * <ul>
 *     <li><b>mixed30</b> (as in the October 9 large-graph study): latents L1 --&gt; L2 --&gt; L3 --&gt; L5 &lt;--
 *     L4 &lt;-- L1, four pure indicators each; O1 --&gt; L1 with O1 --&gt; O5 --&gt; O6; L5 --&gt; O2 --&gt; O3
 *     --&gt; O4; X1 --&gt; O7 --&gt; O8; O9 --&gt; O10. No impurities.</li>
 *     <li><b>mixed30i5</b>: mixed30 with L1 given a fifth indicator, a direct edge X2 --&gt; X3 inside L1's cluster,
 *     and X9 cross-loading on L2 and L3. The fifth indicator matters: after RLCD splits off {X2, X3}, three pure
 *     indicators of L1 remain to serve as references, which the two-indicator step needs.</li>
 *     <li><b>chainplus</b> (14 variables): two pure latents with three indicators each, plus an eight-variable
 *     first-order chain of observed variables hanging off the second latent. Any latent over the chain is
 *     spurious. <b>chain2plus</b> adds weak skip edges two steps along the chain, which is what lets RLCD's
 *     partition reach it; this is the structure for the observed-explanation check (condition token
 *     {@code explain}).</li>
 *     <li><b>twolatents</b> (6 variables) and <b>pure12</b> (12 variables): chains of pure latents, L1 -&gt; L2
 *     (3 indicators each) and L1 -&gt; L2 -&gt; L3 (4 each), with no impurities. These exist for the missing-value
 *     arms, where the variable count is decisive.</li>
 *     <li><b>rand6</b>: drawn afresh each replication. Six latents in a random DAG (each forward pair with
 *     probability 0.4), five indicators each; three direct edges between two indicators of one latent (distinct
 *     latents); one indicator cross-loading on a second latent; two observed causes of latents, each with one
 *     observed child.</li>
 * </ul>
 * <b>Data.</b> Linear; coefficients uniform in 0.5 to 1.5 with random sign; independent errors with variance in 0.5
 * to 1: <i>skew</i> (centered exponential, random sign per variable, skewness 2), <i>mildskew</i> (Gamma with shape
 * 4, skewness 1), <i>gauss</i> (control), and <i>ceil</i>: skew data with every observed variable then clipped at its
 * 85th percentile, as grades at a ceiling are. Latents are dropped.
 * <p>
 * <b>Conditions.</b> <i>plain</i>; <i>resolve</i> (rlcdResolveTwoIndicatorLatents); <i>tiers</i> (true tier
 * knowledge: each observed variable is put in the tier given by its depth, the longest directed path to it from a
 * root in the true graph with latents, so a later tier is never an ancestor of an earlier one); and
 * <i>both</i>. All four run on the same data.
 * <p>
 * <b>Scores</b> (means over replications). An estimated latent is matched to the true latent whose observed children
 * it best overlaps (Jaccard of observed-child sets), if that overlap is at least 0.5; otherwise it is spurious.
 * <ul>
 *     <li><b>lat</b>: latents found, mean. <b>clus</b>: share of true latents with some estimated latent having
 *     exactly its observed children. <b>jac</b>: for each true latent, the best Jaccard overlap of its observed
 *     children with an estimated latent's, averaged (softer than clus, which an impurity alone can spoil).
 *     <b>spur</b>: spurious latents per run.</li>
 *     <li><b>LL P/R</b>: precision and recall of latent-latent adjacencies, through the matching (an edge at a
 *     spurious latent counts against precision; an edge between two estimated latents matched to the same true
 *     latent, a split cluster, is left out of both).</li>
 *     <li><b>OO P/R</b>: precision and recall of observed-observed adjacencies. <b>dir</b>: among the true
 *     observed-observed adjacencies found and directed, the share pointing the true way (direction accuracy;
 *     false adjacencies play no part). <b>dir'd</b>: the share of the true adjacencies found that are directed at
 *     all, the rest being undirected.</li>
 *     <li><b>imp</b>: share of the true within-cluster edges found as adjacencies; <b>impDir</b>: as directed edges
 *     pointing the true way; <b>impLat</b>: rendered instead as a latent whose observed children are exactly the
 *     pair, RLCD's rank-equivalent representation of such an edge. Such a latent is matched to the pair's true
 *     latent and is not counted as spurious. Blank for structures without impurities.</li>
 *     <li><b>rev</b>: ordered pairs of observed variables per run with X an ancestor of Y in the output but Y an
 *     ancestor of X in the truth (a reversed ancestral relation, the thing tiers rule out).</li>
 * </ul>
 * Edges between observed and latent variables are not scored separately; their effect shows in clus and spur.
 * <p>
 * <b>Arguments</b>, all optional, as key=value: reps=10, ns=2000, structures=mixed30,mixed30i5,rand6,
 * dists=skew,mildskew,gauss, conditions=plain,resolve,tiers,both (a condition may also be a '+'-joined set of the
 * tokens plain, resolve, tiers, explain, e.g. {@code tiers+explain}; explain switches on the observed-explanation
 * check), seed=20261022, and the two swept
 * parameters:
 * <ul>
 *     <li><b>alphas</b>=0.01, a comma-separated list of rank-test alphas (RLCD's default is 0.01);</li>
 *     <li><b>penalties</b>=1, a comma-separated list of stage-1 BOSS penalty discounts (RLCD's default of 1
 *     matches causal-learn's GES setting). The older singular {@code penalty=} is still accepted as a synonym
 *     for a one-element list, so earlier command lines keep working.</li>
 * </ul>
 * Every alpha, penalty, and condition cell is run on the <i>same</i> simulated dataset within a replication, so
 * differences between cells are paired and not sampling noise between them. Add {@code csv=path} to also write
 * one row per cell to a file, for plotting a sweep. For example,
 * {@code reps=20 ns=2000,5000 structures=mixed30,rand6 dists=skew conditions=plain alphas=0.001,0.01,0.05,0.1
 * penalties=0.5,1,2,4 csv=sweep.csv}.
 * <p>
 * <b>Missing values.</b> With {@code missing=} other than none, each replication's data have holes punched in
 * them and the arms named by {@code handling=} are all run on the same holed data:
 * <ul>
 *     <li><b>complete</b>: the data before the holes, the ceiling for the cell;</li>
 *     <li><b>pooled</b>: {@code imputations=} MVN multiple imputations through {@link MvnImputer}, searched as one
 *     by {@link Rlcd#Rlcd(List)}, which pools the rank tests by the D2 rule;</li>
 *     <li><b>testwise</b>: {@link Rlcd#setTestwiseDeletion(boolean)}, each rank query answered from the rows
 *     complete on that query's columns;</li>
 *     <li><b>em</b> (also emMean), <b>emFull</b>, <b>emMin</b>: one EM covariance matrix from all the incomplete
 *     rows, every rank test run on it with a per-query sample size by the mean, full-N, or minimum pairwise rule
 *     ({@link edu.cmu.tetrad.search.rlcd.EmCovarianceRankTest});</li>
 *     <li><b>pairwise</b>: as em with the available-case covariance matrix, mean pairwise rule;</li>
 *     <li><b>censored</b>: for mnar only -- a removed value is read as right-censored at about the largest value
 *     that survived in its column, and the covariance is estimated by
 *     {@link CensoredMvnCovarianceEstimator}; under mcar or mar this reading is false and the arm says nothing;</li>
 *     <li><b>listwise</b>: both stages on the rows complete in every column.</li>
 * </ul>
 * Mechanisms, with {@code missingRate=} the share of values removed from an affected variable:
 * <ul>
 *     <li><b>mcar</b>: every observed variable but the first loses values at random;</li>
 *     <li><b>mar</b>: the first observed variable is kept whole and drives the rest -- a value is removed with
 *     probability rising with the driver's percentile in that row, so missingness depends on observed data but not
 *     on the missing value itself;</li>
 *     <li><b>mnar</b>: self-masking. A variable loses its own largest values, which is what a ceiling-truncated
 *     grade looks like, and is the case the chem data raised.</li>
 * </ul>
 * MCAR is where testwise deletion should be sound; MAR and MNAR are where its selection problem should bite, so a
 * comparison that only runs MCAR proves little.
 * <p>
 * <b>Stage 1 is a confound and {@code sharedStage1=true} (the default under missing values) removes it.</b> The arms
 * differ in their stage-2 rank test, which is the thing of interest, but they also differ in the covariance stage 1
 * gets: pooled averages the imputations' matrices over all rows, testwise uses the listwise-complete rows. With
 * sharedStage1, one stage-1 graph is searched per replication from the complete data and handed to every arm
 * through {@link Rlcd#setStage1Graph(Graph)}, so the columns differ only in the rank test. Set it false to see the
 * two effects together, which is what a user would actually get.
 * <p>
 * The <b>skip</b> column is the mean number of rank queries per run that the testwise guards resolved as rejections
 * rather than answering (too few complete rows, or a non-positive small-sample correction). It is blank for the
 * other arms. A large value means that arm's result rests on the guard.
 * <p>
 * <b>Scoring other implementations with the same scorer.</b> {@code exportDir=path} writes, for every
 * replication, the data the arms see ({@code <tag>.data.txt}, tab-delimited, {@code *} for missing) and the truth
 * ({@code <tag>.truth.txt}), and appends a line to {@code manifest.txt}. An outside program -- the reference RLCD
 * in causal-learn or scm-identify, through the Python driver {@code rlcd_external_eval.py} -- reads the data, runs,
 * and writes its graph as Tetrad graph text to {@code <tag>.<label>.graph.txt}, latents in parentheses. Then
 * {@code scoreDir=path labels=cl,scm} reads the manifest, the truths and those graphs, scores them with exactly the
 * scorer used for the Tetrad arms, and prints the same table with the label in the hand column. Nothing is
 * simulated in that mode, so the structures, dists and ns arguments are ignored; cells with no graph file are
 * counted and reported, not scored.
 * <p>
 * Timing, from a smoke run at N = 5000, per RLCD run: mixed30 about 2 s, mixed30i5 about 3 s, rand6 about 7 s.
 * The number of runs per replication is alphas x penalties x conditions, so a sweep grid multiplies quickly:
 * the example above is 8 runs per replication per N, about 20 minutes at reps=20. The ceil data are much slower,
 * 25 to 60 s per run, because clipped variables give stage 2 many spurious rank deficiencies to enumerate, so
 * ceil is not in the default list; a large alpha has the same effect, so expect alpha=0.1 cells to run several
 * times longer than alpha=0.01 ones. At reps=10 the standard error of a share is up to 0.16, so only large
 * differences count. Start with one structure and one dist before widening the grid.
 *
 * @author josephramsey
 */
public final class RlcdAccuracyStudy {

    private RlcdAccuracyStudy() {
    }

    /**
     * Runs the study.
     *
     * @param args key=value options; see the class description.
     * @throws InterruptedException if interrupted.
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> opt = new HashMap<>();
        for (String a : args) {
            int eq = a.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("Expected key=value, got " + a);
            opt.put(a.substring(0, eq).trim(), a.substring(eq + 1).trim());
        }
        int reps = Integer.parseInt(opt.getOrDefault("reps", "10"));
        int[] ns = Arrays.stream(opt.getOrDefault("ns", "2000").split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        String[] structures = opt.getOrDefault("structures", "mixed30,mixed30i5,rand6").split(",");
        String[] dists = opt.getOrDefault("dists", "skew,mildskew,gauss").split(",");
        String[] conditions = opt.getOrDefault("conditions", "plain,resolve,tiers,both").split(",");
        long seed = Long.parseLong(opt.getOrDefault("seed", "20261022"));
        double[] alphas = Arrays.stream(opt.getOrDefault("alphas", "0.01").split(","))
                .mapToDouble(s -> Double.parseDouble(s.trim())).toArray();
        // The singular penalty= of the October 22 harness remains valid as a one-element penalties= list.
        double[] penalties = Arrays.stream(opt.getOrDefault("penalties", opt.getOrDefault("penalty", "1")).split(","))
                .mapToDouble(s -> Double.parseDouble(s.trim())).toArray();
        String csv = opt.get("csv");
        String exportDir = opt.get("exportDir");
        String scoreDir = opt.get("scoreDir");
        if (scoreDir != null) {
            scoreExternal(new java.io.File(scoreDir), opt.getOrDefault("labels", "cl").split(","), csv);
            return;
        }
        if (exportDir != null) {
            java.io.File dir = new java.io.File(exportDir);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create " + dir);
            new java.io.FileWriter(new java.io.File(dir, "manifest.txt"), false).close();   // start afresh
        }
        String missing = opt.getOrDefault("missing", "none");
        double missingRate = Double.parseDouble(opt.getOrDefault("missingRate", "0.2"));
        int imputationCount = Integer.parseInt(opt.getOrDefault("imputations", "5"));
        String[] handling = opt.getOrDefault("handling",
                missing.equals("none") ? "complete" : "complete,pooled,testwise").split(",");
        boolean sharedStage1 = Boolean.parseBoolean(opt.getOrDefault("sharedStage1",
                missing.equals("none") ? "false" : "true"));
        if (!List.of("none", "mcar", "mar", "mnar").contains(missing)) {
            throw new IllegalArgumentException("Unknown missing mechanism: " + missing);
        }
        if (missing.equals("none") && !(handling.length == 1 && handling[0].equals("complete"))) {
            throw new IllegalArgumentException("handling= applies only with missing= other than none.");
        }
        Random random = new Random(seed);

        System.out.println("RLCD accuracy on linear non-Gaussian data with latents. reps " + reps
                           + "; alphas " + Arrays.toString(alphas)
                           + "; stage-1 penalty discounts " + Arrays.toString(penalties)
                           + "; RLCD defaults otherwise (max k 3, BOSS stage 1, latent gate on).");
        if (!missing.equals("none")) {
            System.out.println("Missing values: " + missing + " at rate " + missingRate + "; arms "
                               + Arrays.toString(handling) + " on the same holed data; " + imputationCount
                               + " imputations for the pooled arm; stage-1 graph "
                               + (sharedStage1 ? "shared across arms (searched on the complete data)"
                                      : "searched per arm (so stage 1 and the rank test differ together)") + ".");
        }
        System.out.println("Runs per replication per N: " + (alphas.length * penalties.length * conditions.length)
                           + ". All cells of a replication share one simulated dataset, so cells are paired.");

        try (java.io.PrintWriter out = csv == null ? null : new java.io.PrintWriter(csv)) {
            if (out != null) {
                out.println("structure,dist,n,alpha,penalty,cond,handling,reps,lat,trueLat,clus,jac,spur,llP,llR,"
                            + "ooP,ooR,dir,dirPct,imp,impDir,impLat,rev,skip,secs");
            }
            for (String structure : structures) {
                for (String dist : dists) {
                    System.out.println();
                    System.out.println("=== " + structure + ", errors " + dist + " ===");
                    System.out.printf("%6s %6s %4s %-8s %-8s %7s %5s %5s %5s  %5s %5s  %5s %5s  %5s %5s  %5s %6s "
                                      + "%6s  %5s %5s %6s%n",
                            "N", "alpha", "pd", "cond", "hand", "lat", "clus", "jac", "spur", "LL P", "LL R", "OO P",
                            "OO R", "dir", "dir'd", "imp", "impDir", "impLat", "rev", "skip", "secs");
                    for (int n : ns) {
                        Map<Cell, Totals> totals = new LinkedHashMap<>();
                        for (double alpha : alphas) {
                            for (double penalty : penalties) {
                                for (String c : conditions) {
                                    for (String h : handling) {
                                        totals.put(new Cell(alpha, penalty, c, h), new Totals());
                                    }
                                }
                            }
                        }
                        int trueLatents = 0;
                        System.out.print("  N=" + n + " ");
                        for (int r = 0; r < reps; r++) {
                            System.out.print(".");
                            System.out.flush();
                            Truth truth = build(structure, random);
                            trueLatents = truth.latents.size();
                            DataSet data = truth.simulate(dist, n, random);
                            long runSeed = Math.floorMod(random.nextLong(), 1_000_000L);

                            DataSet holes = missing.equals("none") ? data
                                    : punchHoles(data, missing, missingRate, random);
                            if (exportDir != null) {
                                exportReplication(new java.io.File(exportDir), structure, dist, n, r, holes, truth);
                            }
                            List<DataSet> imputed = null;
                            Map<Double, Graph> sharedStage1Graphs = new HashMap<>();

                            for (Cell cell : totals.keySet()) {
                                Graph stage1 = null;
                                if (sharedStage1) {
                                    Double key = cell.penalty();
                                    if (!sharedStage1Graphs.containsKey(key)) {
                                        Rlcd probe = new Rlcd(data);
                                        probe.setSeed(runSeed);
                                        probe.setAlpha(cell.alpha());
                                        probe.setPenaltyDiscount(cell.penalty());
                                        probe.search();
                                        sharedStage1Graphs.put(key, probe.getStage1Graph());
                                    }
                                    stage1 = sharedStage1Graphs.get(key);
                                }
                                if (cell.handling().equals("pooled") && imputed == null) {
                                    imputed = new MvnImputer(MissingDataSpec.multipleImputation(imputationCount))
                                            .impute(holes, imputationCount, runSeed);
                                }
                                long t0 = System.nanoTime();
                                Run res = run(data, holes, imputed, truth, cell, runSeed, stage1);
                                totals.get(cell).add(score(truth, res.graph()), (System.nanoTime() - t0) / 1e9,
                                        res.skipped());
                            }
                        }
                        System.out.println();
                        for (Map.Entry<Cell, Totals> e : totals.entrySet()) {
                            e.getValue().print(n, e.getKey(), trueLatents);
                            if (out != null) e.getValue().csv(out, structure, dist, n, e.getKey(), trueLatents);
                        }
                    }
                }
            }
        }
        System.out.println();
        System.out.println("lat: latents found (true count in parentheses). clus: true latents recovered with "
                           + "exactly their indicators. jac: mean best overlap (Jaccard) of a true latent's "
                           + "indicators with an estimated latent's. spur: unmatched latents per run.");
        System.out.println("LL, OO: precision and recall of latent-latent and observed-observed adjacencies. dir: "
                           + "direction accuracy among true observed adjacencies found and directed; dir'd: share "
                           + "of those found that are directed.");
        System.out.println("imp, impDir, impLat: within-cluster direct edges found as adjacencies, found pointing "
                           + "the right way, and rendered as a two-indicator latent. rev: reversed ancestral "
                           + "relations among observed pairs per run.");
    }

    /**
     * One cell of the sweep: a rank-test alpha, a stage-1 penalty discount, and a condition.
     */
    private record Cell(double alpha, double penalty, String condition, String handling) {
    }

    /**
     * The result of one cell: the graph, and how many rank queries a testwise guard resolved rather than answered.
     */
    private record Run(Graph graph, int skipped) {
    }

    private static Run run(DataSet data, DataSet holes, List<DataSet> imputed, Truth truth, Cell cell, long seed,
                           Graph stage1) throws InterruptedException {
        TestwiseDeletionRankTest testwise = null;
        Rlcd rlcd;

        switch (cell.handling()) {
            case "complete" -> rlcd = new Rlcd(data);
            case "pooled" -> rlcd = new Rlcd(imputed);
            case "testwise" -> {
                rlcd = new Rlcd(holes);
                // The tester is held so its guard count can be read afterwards; it is the same test
                // setTestwiseDeletion installs, which also sets the listwise stage-1 covariance that matters only
                // when no stage-1 graph is supplied below.
                rlcd.setTestwiseDeletion(true);
                testwise = new TestwiseDeletionRankTest(holes);
                rlcd.setRankTester(testwise);
            }
            case "listwise" -> {
                rlcd = new Rlcd(holes);
                rlcd.setMissingDataSpec(MissingDataSpec.listwise());
            }
            case "em", "emMean" -> {
                rlcd = new Rlcd(holes);
                rlcd.setMissingDataSpec(MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));
            }
            case "emFull" -> {
                rlcd = new Rlcd(holes);
                rlcd.setMissingDataSpec(MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N));
            }
            case "emMin" -> {
                rlcd = new Rlcd(holes);
                rlcd.setMissingDataSpec(MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE));
            }
            case "censored" -> {
                // For mnar holes only: a removed value was above its column's cut, so it is right-censored at about
                // the largest value that survived. Under mcar or mar this reading is false and the arm is meaningless.
                rlcd = new Rlcd(holes);
                CensoredMvnCovarianceEstimator est = new CensoredMvnCovarianceEstimator(holes,
                        CensoredMvnCovarianceEstimator.upperBoundsFromObservedMaxima(holes), null);
                est.setSeed(seed);
                est.setMissingAsCensored(true);     // the holes are the censored values, removed
                rlcd.setCensoredCovariance(est, MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE);
            }
            case "pairwise" -> {
                rlcd = new Rlcd(holes);
                rlcd.setMissingDataSpec(MissingDataSpec.pairwiseCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));
            }
            default -> throw new IllegalArgumentException("Unknown handling: " + cell.handling());
        }

        rlcd.setSeed(seed);
        rlcd.setAlpha(cell.alpha());
        rlcd.setPenaltyDiscount(cell.penalty());
        // A condition is a '+'-joined set of tokens: plain, resolve, tiers, explain; "both" is resolve+tiers.
        String cond = cell.condition().equals("both") ? "resolve+tiers" : cell.condition();
        Set<String> tokens = new HashSet<>(Arrays.asList(cond.split("\\+")));
        rlcd.setResolveTwoIndicatorLatents(tokens.contains("resolve"));
        if (tokens.contains("tiers")) rlcd.setKnowledge(truth.depthTiers());
        if (tokens.contains("explain")) rlcd.setObservedExplanationCheck(true, 0.05);
        if (stage1 != null) rlcd.setStage1Graph(stage1);

        return new Run(rlcd.search(), testwise == null ? -1 : testwise.getSkippedQueries());
    }

    /**
     * Removes values from a copy of the data by the named mechanism. The first observed variable is never touched:
     * under MAR it is the driver, and under the other mechanisms keeping one column whole keeps the cells
     * comparable with the MAR one.
     *
     * @param data    the complete data.
     * @param missing mcar, mar, or mnar.
     * @param rate    the share of values removed from an affected variable.
     * @param random  the generator.
     * @return a copy with NaN in place of the removed values.
     */
    private static DataSet punchHoles(DataSet data, String missing, double rate, Random random) {
        DataSet holes = data.copy();
        int n = holes.getNumRows(), p = holes.getNumColumns();

        if (missing.equals("mnar")) {
            // Self-masking: each affected variable loses its own largest values, as a ceiling-truncated grade does.
            for (int j = 1; j < p; j++) {
                double[] col = new double[n];
                for (int i = 0; i < n; i++) col[i] = data.getDouble(i, j);
                double[] sorted = col.clone();
                Arrays.sort(sorted);
                double cut = sorted[(int) Math.min(n - 1, Math.floor((1 - rate) * (n - 1)))];
                for (int i = 0; i < n; i++) if (col[i] >= cut) holes.setDouble(i, j, Double.NaN);
            }
            return holes;
        }

        double[] driverPercentile = new double[n];
        if (missing.equals("mar")) {
            double[] d = new double[n];
            for (int i = 0; i < n; i++) d[i] = data.getDouble(i, 0);
            Integer[] boxed = new Integer[n];
            for (int i = 0; i < n; i++) boxed[i] = i;
            Arrays.sort(boxed, (x, y) -> Double.compare(d[x], d[y]));
            for (int k = 0; k < n; k++) driverPercentile[boxed[k]] = k / (double) (n - 1);
        }

        for (int i = 0; i < n; i++) {
            // MCAR: a flat rate. MAR: the rate rises linearly with the driver's percentile, from 0 to twice the
            // nominal rate, so the mean rate matches MCAR while missingness depends on observed data alone.
            double pi = missing.equals("mar") ? 2 * rate * driverPercentile[i] : rate;
            for (int j = 1; j < p; j++) {
                if (random.nextDouble() < pi) holes.setDouble(i, j, Double.NaN);
            }
        }
        return holes;
    }

    // ---------------------------------------------------------------- export and external scoring

    private static String tag(String structure, String dist, int n, int rep) {
        return structure + "_" + dist + "_" + n + "_rep" + rep;
    }

    private static void exportReplication(java.io.File dir, String structure, String dist, int n, int rep,
                                          DataSet data, Truth truth) throws java.io.IOException {
        String tag = tag(structure, dist, n, rep);
        try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.File(dir, tag + ".data.txt"))) {
            List<String> names = data.getVariableNames();
            w.println(String.join("\t", names));
            for (int i = 0; i < data.getNumRows(); i++) {
                StringBuilder line = new StringBuilder();
                for (int j = 0; j < names.size(); j++) {
                    if (j > 0) line.append('\t');
                    double v = data.getDouble(i, j);
                    line.append(Double.isNaN(v) ? "*" : Double.toString(v));
                }
                w.println(line);
            }
        }
        try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.File(dir, tag + ".truth.txt"))) {
            w.println("# node <name> latent|observed <parents...>; impurity <from> <to>; nodes in causal order");
            for (String v : truth.names) {
                w.print("node " + v + " " + (truth.latents.contains(v) ? "latent" : "observed"));
                for (String pa : truth.parents.get(v)) w.print(" " + pa);
                w.println();
            }
            for (String[] imp : truth.impurities) w.println("impurity " + imp[0] + " " + imp[1]);
        }
        try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(
                new java.io.File(dir, "manifest.txt"), true))) {
            w.println(tag + "\t" + structure + "\t" + dist + "\t" + n + "\t" + rep);
        }
    }

    private static Truth readTruth(java.io.File file) throws java.io.IOException {
        Truth t = new Truth();
        for (String line : java.nio.file.Files.readAllLines(file.toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] tok = line.split("\\s+");
            if (tok[0].equals("node")) {
                t.node(tok[1], tok[2].equals("latent"), Arrays.copyOfRange(tok, 3, tok.length));
            } else if (tok[0].equals("impurity")) {
                t.impurities.add(new String[]{tok[1], tok[2]});
            }
        }
        return t;
    }

    /**
     * Scores graphs produced outside Tetrad against the exported truths, with the same scorer as the Tetrad arms.
     */
    private static void scoreExternal(java.io.File dir, String[] labels, String csv) throws Exception {
        java.io.File manifest = new java.io.File(dir, "manifest.txt");
        if (!manifest.isFile()) throw new java.io.FileNotFoundException("No manifest.txt in " + dir);
        List<String[]> entries = new ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(manifest.toPath())) {
            if (!line.isBlank()) entries.add(line.split("\t"));
        }
        System.out.println("Scoring external graphs in " + dir + " for labels " + Arrays.toString(labels)
                           + "; " + entries.size() + " replications in the manifest. Same scorer as the Tetrad arms; "
                           + "alpha and penalty are whatever the external run used and are shown as -.");

        // Group by structure, dist, n in manifest order.
        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (String[] e : entries) groups.computeIfAbsent(e[1] + "\t" + e[2] + "\t" + e[3], k -> new ArrayList<>()).add(e);

        try (java.io.PrintWriter out = csv == null ? null : new java.io.PrintWriter(csv)) {
            if (out != null) {
                out.println("structure,dist,n,alpha,penalty,cond,handling,reps,lat,trueLat,clus,jac,spur,llP,llR,"
                            + "ooP,ooR,dir,dirPct,imp,impDir,impLat,rev,skip,secs");
            }
            for (Map.Entry<String, List<String[]>> g : groups.entrySet()) {
                String[] key = g.getKey().split("\t");
                String structure = key[0], dist = key[1];
                int n = Integer.parseInt(key[2]);
                System.out.println();
                System.out.println("=== " + structure + ", errors " + dist + " ===");
                System.out.printf("%6s %6s %4s %-8s %-8s %7s %5s %5s %5s  %5s %5s  %5s %5s  %5s %5s  %5s %6s %6s"
                                  + "  %5s %5s %6s%n",
                        "N", "alpha", "pd", "cond", "hand", "lat", "clus", "jac", "spur", "LL P", "LL R", "OO P",
                        "OO R", "dir", "dir'd", "imp", "impDir", "impLat", "rev", "skip", "secs");
                for (String label : labels) {
                    Totals totals = new Totals();
                    int trueLatents = 0, missingFiles = 0;
                    for (String[] e : g.getValue()) {
                        Truth truth = readTruth(new java.io.File(dir, e[0] + ".truth.txt"));
                        trueLatents = truth.latents.size();
                        java.io.File gf = new java.io.File(dir, e[0] + "." + label + ".graph.txt");
                        if (!gf.isFile()) {
                            missingFiles++;
                            continue;
                        }
                        Graph graph = GraphSaveLoadUtils.loadGraphTxt(gf);
                        totals.add(score(truth, graph), 0.0, -1);
                    }
                    Cell cell = new Cell(Double.NaN, Double.NaN, "plain", label);
                    if (totals.runs > 0) {
                        totals.print(n, cell, trueLatents);
                        if (out != null) totals.csv(out, structure, dist, n, cell, trueLatents);
                    }
                    if (missingFiles > 0) {
                        System.out.println("         (" + label + ": " + missingFiles + " of " + g.getValue().size()
                                           + " replications have no graph file)");
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- truth

    /**
     * A linear model with nodes in causal order.
     */
    private static final class Truth {
        final List<String> names = new ArrayList<>();
        final Map<String, List<String>> parents = new HashMap<>();
        final Set<String> latents = new HashSet<>();
        final List<String[]> impurities = new ArrayList<>();   // within-cluster direct edges, {from, to}
        final Set<String> weak = new HashSet<>();                  // "child<-parent" pairs drawn with a small coefficient

        void node(String name, boolean latent, String... pa) {
            if (parents.containsKey(name)) throw new IllegalArgumentException(name);
            for (String p : pa) if (!parents.containsKey(p)) throw new IllegalArgumentException(p + " before " + name);
            names.add(name);
            parents.put(name, new ArrayList<>(List.of(pa)));
            if (latent) latents.add(name);
        }

        List<String> observed() {
            List<String> out = new ArrayList<>();
            for (String v : names) if (!latents.contains(v)) out.add(v);
            return out;
        }

        Graph graph() {
            Graph g = new EdgeListGraph();
            Map<String, Node> nodes = new HashMap<>();
            for (String v : names) {
                Node node = new GraphNode(v);
                if (latents.contains(v)) node.setNodeType(NodeType.LATENT);
                nodes.put(v, node);
                g.addNode(node);
            }
            for (String v : names) for (String p : parents.get(v)) g.addDirectedEdge(nodes.get(p), nodes.get(v));
            return g;
        }

        /**
         * Observed variables in tiers by depth in the true graph (latents included in the paths).
         */
        Knowledge depthTiers() {
            Map<String, Integer> depth = new HashMap<>();
            for (String v : names) {
                int d = 0;
                for (String p : parents.get(v)) d = Math.max(d, depth.get(p) + 1);
                depth.put(v, d);
            }
            List<Integer> levels = new ArrayList<>();
            for (String v : observed()) if (!levels.contains(depth.get(v))) levels.add(depth.get(v));
            levels.sort(null);
            Knowledge k = new Knowledge();
            for (String v : observed()) k.addToTier(levels.indexOf(depth.get(v)), v);
            return k;
        }

        DataSet simulate(String dist, int n, Random random) {
            String base = dist.equals("ceil") ? "skew" : dist;
            Map<String, double[]> values = new HashMap<>();
            for (String v : names) {
                double[] x = error(base, n, random);
                for (String p : parents.get(v)) {
                    double c = weak.contains(v + "<-" + p)
                            ? (0.2 + 0.2 * random.nextDouble()) * (random.nextBoolean() ? 1 : -1)
                            : (0.5 + random.nextDouble()) * (random.nextBoolean() ? 1 : -1);
                    double[] xp = values.get(p);
                    for (int i = 0; i < n; i++) x[i] += c * xp[i];
                }
                values.put(v, x);
            }
            List<String> obs = observed();
            List<Node> vars = new ArrayList<>();
            for (String v : obs) vars.add(new ContinuousVariable(v));
            DoubleDataBox box = new DoubleDataBox(n, obs.size());
            for (int j = 0; j < obs.size(); j++) {
                double[] x = values.get(obs.get(j));
                if (dist.equals("ceil")) {
                    double[] sorted = x.clone();
                    Arrays.sort(sorted);
                    double cap = sorted[(int) Math.floor(0.85 * (n - 1))];
                    for (int i = 0; i < n; i++) x[i] = Math.min(x[i], cap);
                }
                for (int i = 0; i < n; i++) box.set(i, j, x[i]);
            }
            return new BoxDataSet(box, vars);
        }
    }

    private static double[] error(String dist, int n, Random random) {
        double sd = Math.sqrt(0.5 + 0.5 * random.nextDouble());
        double sign = random.nextBoolean() ? 1 : -1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double z = switch (dist) {
                case "skew" -> sign * (-Math.log(1 - random.nextDouble()) - 1.0);
                case "mildskew" -> {
                    double s = 0;
                    for (int k = 0; k < 4; k++) s += -Math.log(1 - random.nextDouble());
                    yield sign * (s - 4.0) / 2.0;
                }
                case "gauss" -> random.nextGaussian();
                default -> throw new IllegalArgumentException("Unknown error distribution: " + dist);
            };
            out[i] = sd * z;
        }
        return out;
    }

    private static Truth build(String structure, Random random) {
        return switch (structure) {
            case "twolatents" -> pureChain(2, 3);
            case "pure12" -> pureChain(3, 4);
            case "chainplus" -> chainPlus();
            case "chain2plus" -> chainPlus(true);
            case "mixed30" -> mixed30(false);
            case "mixed30i5" -> mixed30(true);
            case "rand6" -> rand6(random);
            default -> throw new IllegalArgumentException("Unknown structure: " + structure);
        };
    }

    /**
     * A chain of pure latents, L1 -&gt; L2 -&gt; ... , each with the given number of pure indicators and no
     * impurities. Small structures matter for the missing-value arms: the number of columns a rank query mentions
     * decides how many rows are complete on all of them, so testwise deletion lives or dies by the variable count.
     *
     * @param latents    the number of latents.
     * @param indicators the indicators per latent.
     * @return the truth.
     */
    private static Truth pureChain(int latents, int indicators) {
        Truth t = new Truth();
        for (int i = 0; i < latents; i++) {
            if (i == 0) t.node("L1", true);
            else t.node("L" + (i + 1), true, "L" + i);
        }
        int x = 1;
        for (int i = 0; i < latents; i++) {
            for (int j = 0; j < indicators; j++) t.node("X" + (x++), false, "L" + (i + 1));
        }
        return t;
    }

    /**
     * Two pure latents (L1 -&gt; L2, three indicators each) and an eight-variable first-order chain of observed
     * variables C1 -&gt; C2 -&gt; ... -&gt; C8 with L2 -&gt; C1. The chain is the structure RLCD misreads as a
     * measurement model, since any cut through a chain is rank one; the latents are there so that a remedy for
     * that can be seen to leave true clusters alone. Any latent reported over the C variables is spurious.
     */
    private static Truth chainPlus() {
        return chainPlus(false);
    }

    /**
     * As {@link #chainPlus()}; with {@code secondOrder} each chain variable also has a weak edge (coefficient 0.2 to
     * 0.4) from the variable two before it. A first-order chain forms no stage-1 clique of three, so RLCD's
     * partition never reaches it and it is never misread; the skip edges make triangles, the chain enters the
     * partition, and any cut through it is rank two, which is what the unit-progress block of the chem data
     * showed. Structure name {@code chain2plus}.
     */
    private static Truth chainPlus(boolean secondOrder) {
        Truth t = new Truth();
        t.node("L1", true);
        t.node("L2", true, "L1");
        for (int j = 1; j <= 3; j++) t.node("X" + j, false, "L1");
        for (int j = 4; j <= 6; j++) t.node("X" + j, false, "L2");
        t.node("C1", false, "L2");
        t.node("C2", false, "C1");
        for (int j = 3; j <= 8; j++) {
            if (secondOrder) {
                t.node("C" + j, false, "C" + (j - 1), "C" + (j - 2));
                t.weak.add("C" + j + "<-C" + (j - 2));
            } else {
                t.node("C" + j, false, "C" + (j - 1));
            }
        }
        return t;
    }

    private static Truth mixed30(boolean impure) {
        Truth t = new Truth();
        t.node("O1", false);
        t.node("O9", false);
        t.node("O10", false, "O9");
        t.node("O5", false, "O1");
        t.node("O6", false, "O5");
        t.node("L1", true, "O1");
        t.node("L2", true, "L1");
        t.node("L4", true, "L1");
        t.node("L3", true, "L2");
        t.node("L5", true, "L3", "L4");
        t.node("X1", false, "L1");
        t.node("X2", false, "L1");
        if (impure) {
            t.node("X3", false, "L1", "X2");
            t.impurities.add(new String[]{"X2", "X3"});
        } else {
            t.node("X3", false, "L1");
        }
        t.node("X4", false, "L1");
        if (impure) t.node("X21", false, "L1");
        for (int i = 5; i <= 8; i++) t.node("X" + i, false, "L2");
        for (int i = 9; i <= 12; i++) {
            if (impure && i == 9) t.node("X9", false, "L3", "L2");
            else t.node("X" + i, false, "L3");
        }
        for (int i = 13; i <= 16; i++) t.node("X" + i, false, "L4");
        for (int i = 17; i <= 20; i++) t.node("X" + i, false, "L5");
        t.node("O2", false, "L5");
        t.node("O3", false, "O2");
        t.node("O4", false, "O3");
        t.node("O7", false, "X1");
        t.node("O8", false, "O7");
        return t;
    }

    private static Truth rand6(Random random) {
        Truth t = new Truth();
        int k = 6, per = 5;
        // Observed causes of latents first.
        List<Integer> causeTargets = new ArrayList<>();
        for (int c = 1; c <= 2; c++) {
            t.node("O" + c, false);
            causeTargets.add(random.nextInt(k));
        }
        for (int i = 0; i < k; i++) {
            List<String> pa = new ArrayList<>();
            for (int j = 0; j < i; j++) if (random.nextDouble() < 0.4) pa.add("L" + (j + 1));
            for (int c = 0; c < 2; c++) if (causeTargets.get(c) == i) pa.add("O" + (c + 1));
            t.node("L" + (i + 1), true, pa.toArray(new String[0]));
        }
        // Impurities in three distinct latents; one cross-loading on a fourth.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < k; i++) order.add(i);
        java.util.Collections.shuffle(order, random);
        Set<Integer> impure = new HashSet<>(order.subList(0, 3));
        int crossFrom = order.get(3), crossTo = order.get(4);
        int x = 1;
        for (int i = 0; i < k; i++) {
            String l = "L" + (i + 1);
            int a = random.nextInt(per - 1);                      // edge from indicator a to a + 1
            for (int j = 0; j < per; j++) {
                String name = "X" + (x++);
                List<String> pa = new ArrayList<>(List.of(l));
                if (impure.contains(i) && j == a + 1) {
                    String from = "X" + (x - 2);
                    pa.add(from);
                    t.impurities.add(new String[]{from, name});
                }
                if (i == crossFrom && j == per - 1) pa.add("L" + (crossTo + 1));   // all latents already exist
                t.node(name, false, pa.toArray(new String[0]));
            }
        }
        for (int c = 1; c <= 2; c++) t.node("O" + (c + 2), false, "O" + c);
        return t;
    }

    // ---------------------------------------------------------------- scoring

    private record Score(int latents, int exactClusters, double jaccardSum, int spurious, int llTp, int llEst, int llTrue,
                         int ooTp, int ooEst, int ooTrue, int dirRight, int dirWrong, int impAdj, int impDir,
                         int impLat, int impTrue, int reversed) {
    }

    private static Score score(Truth truth, Graph out) {
        Graph tg = truth.graph();

        // Observed children of each latent, true and estimated.
        Map<String, Set<String>> trueKids = new HashMap<>();
        for (String l : truth.latents) trueKids.put(l, observedChildren(tg, tg.getNode(l)));
        Map<Node, Set<String>> estKids = new LinkedHashMap<>();
        for (Node v : out.getNodes()) if (v.getNodeType() == NodeType.LATENT) estKids.put(v, observedChildren(out, v));

        // A latent whose observed children are exactly a true impure pair is that edge in RLCD's rank-equivalent
        // form; it is matched to the pair's latent and not counted as spurious.
        Map<Set<String>, String> impurityPairs = new HashMap<>();
        for (String[] imp : truth.impurities) {
            for (Map.Entry<String, Set<String>> t : trueKids.entrySet()) {
                if (t.getValue().contains(imp[0]) && t.getValue().contains(imp[1])) {
                    impurityPairs.put(Set.of(imp[0], imp[1]), t.getKey());
                }
            }
        }

        Map<Node, String> match = new HashMap<>();
        int spurious = 0, impLat = 0;
        for (Map.Entry<Node, Set<String>> e : estKids.entrySet()) {
            if (impurityPairs.containsKey(e.getValue())) {
                match.put(e.getKey(), impurityPairs.get(e.getValue()));
                impLat++;
                continue;
            }
            String best = null;
            double bestJ = 0;
            for (Map.Entry<String, Set<String>> t : trueKids.entrySet()) {
                double j = jaccard(e.getValue(), t.getValue());
                if (j > bestJ) {
                    bestJ = j;
                    best = t.getKey();
                }
            }
            if (bestJ >= 0.5) match.put(e.getKey(), best);
            else spurious++;
        }
        int exact = 0;
        double jaccardSum = 0;
        for (Set<String> kids : trueKids.values()) {
            if (estKids.containsValue(kids)) exact++;
            double best = 0;
            for (Set<String> est : estKids.values()) best = Math.max(best, jaccard(kids, est));
            jaccardSum += best;
        }

        // Latent-latent adjacencies.
        Set<Set<String>> trueLL = new HashSet<>();
        for (Edge e : tg.getEdges()) {
            if (truth.latents.contains(e.getNode1().getName()) && truth.latents.contains(e.getNode2().getName())) {
                trueLL.add(Set.of(e.getNode1().getName(), e.getNode2().getName()));
            }
        }
        int llEst = 0;
        Set<Set<String>> foundLL = new HashSet<>();
        for (Edge e : out.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() != NodeType.LATENT || b.getNodeType() != NodeType.LATENT) continue;
            String ma = match.get(a), mb = match.get(b);
            if (ma != null && ma.equals(mb)) continue;     // two pieces of one true latent: not a latent-latent edge
            llEst++;
            if (ma != null && mb != null && trueLL.contains(Set.of(ma, mb))) {
                foundLL.add(Set.of(ma, mb));
            }
        }
        int llTp = foundLL.size();

        // Observed-observed edges.
        Set<String> obs = new HashSet<>(truth.observed());
        Set<Set<String>> trueOO = new HashSet<>();
        Set<List<String>> trueArr = new HashSet<>();
        for (Edge e : tg.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            if (obs.contains(a) && obs.contains(b)) {
                trueOO.add(Set.of(a, b));
                Node tail = Edges.getDirectedEdgeTail(e), head = Edges.getDirectedEdgeHead(e);
                trueArr.add(List.of(tail.getName(), head.getName()));
            }
        }
        int ooEst = 0, ooTp = 0, dirRight = 0, dirWrong = 0;
        Set<Set<String>> estOO = new HashSet<>();
        Set<List<String>> estArr = new HashSet<>();
        for (Edge e : out.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            if (!obs.contains(a) || !obs.contains(b)) continue;
            ooEst++;
            estOO.add(Set.of(a, b));
            boolean trueAdj = trueOO.contains(Set.of(a, b));
            if (trueAdj) ooTp++;
            if (Edges.isDirectedEdge(e)) {
                List<String> arr = List.of(Edges.getDirectedEdgeTail(e).getName(),
                        Edges.getDirectedEdgeHead(e).getName());
                estArr.add(arr);
                if (trueAdj) {
                    if (trueArr.contains(arr)) dirRight++;
                    else dirWrong++;
                }
            }
        }

        int impAdj = 0, impDir = 0;
        for (String[] imp : truth.impurities) {
            if (estOO.contains(Set.of(imp[0], imp[1]))) impAdj++;
            if (estArr.contains(List.of(imp[0], imp[1]))) impDir++;
        }

        // Reversed ancestral relations among observed pairs.
        Map<String, Set<String>> trueAnc = ancestors(tg, truth.observed());
        Map<String, Set<String>> estAnc = ancestors(out, truth.observed());
        int reversed = 0;
        for (String y : truth.observed()) {
            for (String x : estAnc.get(y)) {
                // x is an ancestor of y in the output; reversed if y is an ancestor of x in the truth.
                if (trueAnc.get(x).contains(y)) reversed++;
            }
        }

        return new Score(estKids.size(), exact, jaccardSum, spurious, llTp, llEst, trueLL.size(), ooTp, ooEst, trueOO.size(),
                dirRight, dirWrong, impAdj, impDir, impLat, truth.impurities.size(), reversed);
    }

    private static Set<String> observedChildren(Graph g, Node l) {
        Set<String> out = new HashSet<>();
        for (Node c : g.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) out.add(c.getName());
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return inter.size() / (double) union.size();
    }

    /**
     * For each named observed variable, its observed proper ancestors along directed paths (latents included).
     */
    private static Map<String, Set<String>> ancestors(Graph g, List<String> observed) {
        Map<String, Set<String>> out = new HashMap<>();
        Set<String> obs = new HashSet<>(observed);
        for (String v : observed) {
            Node node = g.getNode(v);
            Set<String> anc = new HashSet<>();
            if (node != null) {
                for (Node a : g.paths().getAncestors(node)) {
                    if (a != node && obs.contains(a.getName())) anc.add(a.getName());
                }
            }
            out.put(v, anc);
        }
        return out;
    }

    private static final class Totals {
        int runs;
        double latents, exact, jaccard, spurious, secs;
        long llTp, llEst, llTrue, ooTp, ooEst, ooTrue, dirRight, dirWrong, impAdj, impDir, impLat, impTrue, reversed;
        long skipped;
        int skippedRuns;

        void add(Score s, double seconds, int skippedQueries) {
            runs++;
            if (skippedQueries >= 0) {
                skipped += skippedQueries;
                skippedRuns++;
            }
            latents += s.latents();
            exact += s.exactClusters();
            jaccard += s.jaccardSum();
            spurious += s.spurious();
            llTp += s.llTp();
            llEst += s.llEst();
            llTrue += s.llTrue();
            ooTp += s.ooTp();
            ooEst += s.ooEst();
            ooTrue += s.ooTrue();
            dirRight += s.dirRight();
            dirWrong += s.dirWrong();
            impAdj += s.impAdj();
            impDir += s.impDir();
            impLat += s.impLat();
            impTrue += s.impTrue();
            reversed += s.reversed();
            secs += seconds;
        }

        void print(int n, Cell cell, int trueLatents) {
            System.out.printf("%6d %6s %4s %-8s %-8s %7s %5.2f %5.2f %5.2f  %5s %5s  %5s %5s  %5s %5s  %5s %6s %6s"
                              + "  %5.1f %5s %6.2f%n",
                    n, trim(cell.alpha()), trim(cell.penalty()), cell.condition(), cell.handling(),
                    String.format("%.1f(%d)", latents / runs, trueLatents),
                    exact / (runs * (double) trueLatents),
                    jaccard / (runs * (double) trueLatents),
                    spurious / runs,
                    ratio(llTp, llEst), ratio(llTp, llTrue),
                    ratio(ooTp, ooEst), ratio(ooTp, ooTrue),
                    ratio(dirRight, dirRight + dirWrong), ratio(dirRight + dirWrong, ooTp),
                    impTrue == 0 ? "" : ratio(impAdj, impTrue), impTrue == 0 ? "" : ratio(impDir, impTrue),
                    impTrue == 0 ? "" : ratio(impLat, impTrue),
                    reversed / (double) runs,
                    skippedRuns == 0 ? "" : String.format("%.1f", skipped / (double) skippedRuns),
                    secs / runs);
        }

        void csv(java.io.PrintWriter out, String structure, String dist, int n, Cell cell, int trueLatents) {
            out.printf("%s,%s,%d,%s,%s,%s,%s,%d,%.3f,%d,%.4f,%.4f,%.4f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.3f,%s,%.3f%n",
                    structure, dist, n, trim(cell.alpha()), trim(cell.penalty()), cell.condition(),
                    cell.handling(), runs,
                    latents / runs, trueLatents,
                    exact / (runs * (double) trueLatents),
                    jaccard / (runs * (double) trueLatents),
                    spurious / runs,
                    plain(llTp, llEst), plain(llTp, llTrue),
                    plain(ooTp, ooEst), plain(ooTp, ooTrue),
                    plain(dirRight, dirRight + dirWrong), plain(dirRight + dirWrong, ooTp),
                    impTrue == 0 ? "" : plain(impAdj, impTrue), impTrue == 0 ? "" : plain(impDir, impTrue),
                    impTrue == 0 ? "" : plain(impLat, impTrue),
                    reversed / (double) runs,
                    skippedRuns == 0 ? "" : String.format("%.3f", skipped / (double) skippedRuns),
                    secs / runs);
        }

        private static String ratio(long num, long den) {
            return den == 0 ? "  -  " : String.format("%.2f", num / (double) den);
        }

        private static String plain(long num, long den) {
            return den == 0 ? "" : String.format("%.4f", num / (double) den);
        }

        /**
         * A parameter value for a column heading: 0.01 rather than 0.010000, 2 rather than 2.0.
         */
        private static String trim(double x) {
            if (Double.isNaN(x)) return "-";
            String s = String.format("%.4f", x);
            s = s.replaceAll("0+$", "");
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }
    }
}
