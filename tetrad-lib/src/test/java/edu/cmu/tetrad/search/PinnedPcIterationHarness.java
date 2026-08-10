package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.PinnedIndependenceTest;
import edu.cmu.tetrad.search.test.PinnedIndependenceTest.QueryRecord;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.*;

/**
 * Hand-run harness: can out-of-sample validation of PC's own conditional-independence decisions improve PC?
 * <p>
 * The naive version of Markov-check feedback — pin the estimated graph's rejected <i>implied</i> CIs — has no
 * traction on PC: FAS removes an edge at the first separating subset it finds and never queries the implied local
 * Markov fact (parents as conditioner), so exact-match pins are never consulted (verified: zero pin hits). This
 * harness instead validates the CI decisions the search <i>actually made</i>, taken from the query trace of
 * {@link PinnedIndependenceTest}:
 * <ul>
 * <li><b>Separation decisions</b>: queries judged independent for pairs non-adjacent in the output — these licensed
 * the removals. Each is retested on a held-out fold; those rejected there with material effect size (|partial r|
 * recovered from the Fisher Z p-value, gated by delta, not by p-value alone) are pinned dependent, blocking that
 * removal path on the next run. FAS then either finds another separator (a new candidate for validation next round)
 * or keeps the edge. For a truly adjacent pair no valid separator exists, so candidate separators are peeled away
 * one by one, given power on the held-out fold; for a truly non-adjacent pair the true separator survives
 * validation and no pin is placed.</li>
 * <li><b>Retention decisions</b>: queries judged dependent for pairs adjacent in the output. Those clearly
 * independent on the held-out fold (p large AND effect below a small delta) are pinned independent, licensing
 * removal — the symmetric direction that prevents a one-way ratchet toward density.</li>
 * </ul>
 * A graph-level Markov check on the held-out fold (ordered local Markov, same effect-size gating) is the external
 * yardstick: an iteration is accepted only if the gated failure count strictly decreases; otherwise the previous
 * graph is kept and the procedure stops. It also stops at a fixed point (no new pins) or after maxIters.
 * <p>
 * Usage: java ... PinnedPcIterationHarness [reps] [numNodes] [numEdges] [n] [alpha] [deltaDep] [kPerRound]
 * <p>
 * Set -DpinDebug=true for per-iteration diagnostics.
 * <p>
 * <b>Findings from initial runs</b> (20 reps, 20 nodes, 40 edges, linear-Gaussian, correctly specified test).
 * At n = 1000 (500/500 split): split-PC SHD 24.80 &rarr; 23.15 over iterations; arrow precision .679 &rarr; .721;
 * arrow recall .396 &rarr; .441; 10/20 reps improved, 1/20 worsened (by 2). At n = 4000: SHD 20.75 &rarr; 19.65,
 * with gains again concentrated in orientations. In both regimes the iteration closes most of the gap to full-data
 * PC (SHD 22.75 and 19.40 respectively) but does not beat it, and a final full-data run with the accumulated pins
 * is statistically a wash with plain full-data PC. Interpretation: with a correctly specified test on i.i.d. data,
 * full-sample PC's CI decisions are already nearly as good as cross-fold-validated ones; the procedure buys
 * robustness (it fixes unstable decisions, especially collider orientations) rather than extra signal. The expected
 * payoff domains are (a) misspecified tests / non-i.i.d. data, where held-out validation with a robust test can
 * catch systematic errors the search test makes, and (b) diagnosis: the pin list is exactly the set of CI decisions
 * the graph rests on that fail to replicate out-of-sample — a far more actionable Markov-check output than a count
 * of rejected implied CIs. Also verified here: pinning the graph's <i>implied</i> local Markov facts (the naive
 * feedback scheme) has zero traction on PC — no pinned fact was ever re-queried across 20 reps — which is why this
 * harness validates the query trace instead.
 */
public final class PinnedPcIterationHarness {

    private static final NormalDistribution NORMAL = new NormalDistribution(0, 1);

    private PinnedPcIterationHarness() {
    }

    public static void main(String[] args) throws Exception {
        int reps = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int numNodes = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int numEdges = args.length > 2 ? Integer.parseInt(args[2]) : 40;
        int n = args.length > 3 ? Integer.parseInt(args[3]) : 1000;
        double alpha = args.length > 4 ? Double.parseDouble(args[4]) : 0.01;
        double deltaDep = args.length > 5 ? Double.parseDouble(args[5]) : 0.10;   // min |r| to pin dependent
        int kPerRound = args.length > 6 ? Integer.parseInt(args[6]) : 10;

        double deltaInd = 0.03;    // max |r| to pin independent
        double pIndMin = 0.50;     // min held-out p to pin independent
        int maxIters = 10;
        int patience = 3;      // non-improving rounds tolerated before stopping

        System.out.printf("reps=%d nodes=%d edges=%d n=%d (split %d/%d) alpha=%s deltaDep=%s deltaInd=%s k=%d maxIters=%d%n%n",
                reps, numNodes, numEdges, n, n / 2, n - n / 2, alpha, deltaDep, deltaInd, kPerRound, maxIters);

        int cols = maxIters + 1;
        double[][] adjP = new double[reps][cols], adjR = new double[reps][cols], adjF1 = new double[reps][cols];
        double[][] arrP = new double[reps][cols], arrR = new double[reps][cols], shd = new double[reps][cols];
        double[][] gated = new double[reps][cols];
        int[] stoppedAt = new int[reps];
        double[] fullShd = new double[reps];
        double[] fullAdjF1 = new double[reps];
        double[] finalShd = new double[reps];
        double[] finalAdjF1 = new double[reps];
        double[] finalArrP = new double[reps];
        double[] finalArrR = new double[reps];
        int[] finalPinHits = new int[reps];
        int[] improved = new int[reps], worsened = new int[reps];

        for (int rep = 0; rep < reps; rep++) {
            long seed = 1_000_003L * (rep + 1);
            RandomUtil.getInstance().setSeed(seed);

            Graph trueDag = RandomGraph.randomDag(numNodes, 0, numEdges, 100, 100, 100, false);
            SemPm pm = new SemPm(trueDag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(n, false);

            List<Integer> evens = new ArrayList<>(), odds = new ArrayList<>();
            for (int i = 0; i < data.getNumRows(); i++) (i % 2 == 0 ? evens : odds).add(i);
            DataSet foldA = data.subsetRows(evens);
            DataSet foldB = data.subsetRows(odds);
            int nB = foldB.getNumRows();

            Graph trueCpdag = GraphTransforms.dagToCpdag(trueDag);

            // Honest baseline: PC on the full (unsplit) sample.
            Pc pcFull = new Pc(new IndTestFisherZ(data, alpha));
            double[] mFull = metrics(pcFull.search(), trueCpdag);
            fullShd[rep] = mFull[5];
            fullAdjF1[rep] = mFull[2];

            IndTestFisherZ testA = new IndTestFisherZ(foldA, alpha);
            PinnedIndependenceTest pinned = new PinnedIndependenceTest(testA);
            pinned.setRecordQueries(true);

            IndTestFisherZ testB = new IndTestFisherZ(foldB, alpha);
            Map<String, Node> bNodes = new HashMap<>();
            for (Node node : testB.getVariables()) bNodes.put(node.getName(), node);

            Set<String> alreadyPinned = new HashSet<>();
            List<IndependenceFact> pinnedDepFacts = new ArrayList<>();
            List<IndependenceFact> pinnedIndFacts = new ArrayList<>();
            double prevGated = Double.POSITIVE_INFINITY;
            int sinceImprove = 0;
            int lastRecorded = 0;
            double[] lastM = null;
            int lastDepHits = 0, lastIndHits = 0;
            int iter;

            for (iter = 0; iter <= maxIters; iter++) {
                pinned.resetHitCounts();
                pinned.clearQueryLog();
                Pc pc = new Pc(pinned);
                Graph est = pc.search();
                List<QueryRecord> log = pinned.getQueryLog();

                double[] m = metrics(est, trueCpdag);

                if (Boolean.getBoolean("pinDebug")) {
                    System.out.printf("  [dbg] rep %d iter %d: est edges=%d trueCpdag edges=%d shd=%.0f%n",
                            rep, iter, est.getNumEdges(), trueCpdag.getNumEdges(), m[5]);
                }

                // ---- External yardstick: gated Markov failures of the graph on fold B. ----
                MarkovCheck mc = new MarkovCheck(est, testB, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
                mc.generateResults(true, true);
                double g = 0;
                if (Boolean.getBoolean("pinDebug")) {
                    System.out.printf("  [dbg]   markov results (indep side): %d%n", mc.getResults(true).size());
                }
                for (IndependenceResult r : mc.getResults(true)) {
                    if (!r.isValid()) continue;
                    double eff = effectFromP(r.getPValue(), r.getFact().getZ().size(), nB);
                    if (r.getPValue() < alpha && eff >= deltaDep) g++;
                }

                // Accept as new best only on strict decrease of the gated yardstick; but keep
                // pinning for up to `patience` non-improving rounds, since peeling false separators
                // for a single pair can take several rounds before the edge finally sticks.
                if (iter == 0 || g < prevGated) {
                    lastM = m;
                    prevGated = g;
                    lastDepHits = pinned.getDependentPinHits();
                    lastIndHits = pinned.getIndependentPinHits();
                    sinceImprove = 0;
                } else {
                    sinceImprove++;
                }
                record(adjP, adjR, adjF1, arrP, arrR, shd, gated, rep, iter, lastM, prevGated);
                lastRecorded = iter;

                if (sinceImprove >= patience) {
                    iter++;
                    break;
                }

                // ---- Candidate pins from the query trace, validated on fold B. ----
                Set<String> estAdj = new HashSet<>();
                for (Edge e : est.getEdges()) estAdj.add(pairKey(e.getNode1().getName(), e.getNode2().getName()));

                // Deduped trace decisions (skip pin-answered queries; they are already fold-B-derived).
                Map<String, QueryRecord> sepDecisions = new LinkedHashMap<>();   // independent, pair non-adjacent
                Map<String, QueryRecord> keepDecisions = new LinkedHashMap<>();  // dependent, pair adjacent
                for (QueryRecord q : log) {
                    if (q.fromPin()) continue;
                    IndependenceFact f = q.fact();
                    String pk = pairKey(f.getX().getName(), f.getY().getName());
                    String fk = factKey(f);
                    if (alreadyPinned.contains(fk)) continue;
                    if (q.independent() && !estAdj.contains(pk)) sepDecisions.putIfAbsent(fk, q);
                    if (!q.independent() && estAdj.contains(pk)) keepDecisions.putIfAbsent(fk, q);
                }

                List<Object[]> depCandidates = new ArrayList<>();   // invalidated separations
                for (QueryRecord q : sepDecisions.values()) {
                    IndependenceResult rB = checkOnB(testB, bNodes, q.fact());
                    if (rB == null || !rB.isValid()) continue;
                    double eff = effectFromP(rB.getPValue(), q.fact().getZ().size(), nB);
                    if (rB.getPValue() < alpha && eff >= deltaDep) depCandidates.add(new Object[]{q.fact(), eff});
                }
                depCandidates.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));

                List<Object[]> indCandidates = new ArrayList<>();   // invalidated retentions
                for (QueryRecord q : keepDecisions.values()) {
                    IndependenceResult rB = checkOnB(testB, bNodes, q.fact());
                    if (rB == null || !rB.isValid()) continue;
                    double eff = effectFromP(rB.getPValue(), q.fact().getZ().size(), nB);
                    if (rB.getPValue() > pIndMin && eff <= deltaInd) indCandidates.add(new Object[]{q.fact(), eff});
                }
                indCandidates.sort(Comparator.comparingDouble(a -> (double) a[1]));

                int newPins = 0;
                for (int i = 0; i < Math.min(kPerRound, depCandidates.size()); i++) {
                    IndependenceFact f = (IndependenceFact) depCandidates.get(i)[0];
                    pinned.pinDependent(f);
                    pinnedDepFacts.add(f);
                    alreadyPinned.add(factKey(f));
                    newPins++;
                }
                for (int i = 0; i < Math.min(kPerRound, indCandidates.size()); i++) {
                    IndependenceFact f = (IndependenceFact) indCandidates.get(i)[0];
                    pinned.pinIndependent(f);
                    pinnedIndFacts.add(f);
                    alreadyPinned.add(factKey(f));
                    newPins++;
                }

                if (newPins == 0) {
                    iter++;
                    break;   // fixed point
                }
            }

            stoppedAt[rep] = lastRecorded;
            for (int t = stoppedAt[rep] + 1; t <= maxIters; t++) {
                adjP[rep][t] = adjP[rep][t - 1];
                adjR[rep][t] = adjR[rep][t - 1];
                adjF1[rep][t] = adjF1[rep][t - 1];
                arrP[rep][t] = arrP[rep][t - 1];
                arrR[rep][t] = arrR[rep][t - 1];
                shd[rep][t] = shd[rep][t - 1];
                gated[rep][t] = gated[rep][t - 1];
            }

            // Final variant: one PC run on the FULL data with the accumulated cross-fold-validated
            // pins. Uses all n for the marginal decisions while retaining the validated constraints.
            PinnedIndependenceTest pinnedFull = new PinnedIndependenceTest(new IndTestFisherZ(data, alpha));
            for (IndependenceFact f : pinnedDepFacts) pinnedFull.pinDependent(f);
            for (IndependenceFact f : pinnedIndFacts) pinnedFull.pinIndependent(f);
            Pc pcFinal = new Pc(pinnedFull);
            double[] mFinal = metrics(pcFinal.search(), trueCpdag);
            finalShd[rep] = mFinal[5];
            finalAdjF1[rep] = mFinal[2];
            finalArrP[rep] = mFinal[3];
            finalArrR[rep] = mFinal[4];
            finalPinHits[rep] = pinnedFull.getDependentPinHits() + pinnedFull.getIndependentPinHits();

            if (shd[rep][maxIters] < shd[rep][0]) improved[rep] = 1;
            if (shd[rep][maxIters] > shd[rep][0]) worsened[rep] = 1;

            System.out.printf("rep %2d: SHD %5.1f -> %5.1f | adjF1 %.3f -> %.3f | gated %3.0f -> %3.0f | stopped iter %d | pins %d | hits(dep/ind) %d/%d%n",
                    rep, shd[rep][0], shd[rep][maxIters], adjF1[rep][0], adjF1[rep][maxIters],
                    gated[rep][0], gated[rep][maxIters], stoppedAt[rep], alreadyPinned.size(),
                    lastDepHits, lastIndHits);
        }

        System.out.println();
        System.out.println("iter |  adjP   adjR  adjF1 |  arrP   arrR |   SHD | gatedFails");
        for (int t = 0; t <= maxIters; t++) {
            System.out.printf("%4d | %.3f  %.3f  %.3f | %.3f  %.3f | %5.2f | %6.2f%n",
                    t, mean(adjP, t), mean(adjR, t), mean(adjF1, t),
                    mean(arrP, t), mean(arrR, t), mean(shd, t), mean(gated, t));
        }
        System.out.printf("%nreps improved (final SHD < split baseline): %d/%d; worsened: %d/%d; unchanged: %d/%d%n",
                sum(improved), reps, sum(worsened), reps, reps - sum(improved) - sum(worsened), reps);

        double fs = 0, ff = 0;
        for (int r = 0; r < reps; r++) {
            fs += fullShd[r];
            ff += fullAdjF1[r];
        }
        System.out.printf("full-data PC baseline (n unsplit): mean SHD %.2f, mean adjF1 %.3f%n", fs / reps, ff / reps);

        double gs = 0, gf = 0, gp = 0, gr = 0;
        int fw = 0, fl = 0, hits = 0;
        for (int r = 0; r < reps; r++) {
            gs += finalShd[r];
            gf += finalAdjF1[r];
            gp += finalArrP[r];
            gr += finalArrR[r];
            hits += finalPinHits[r];
            if (finalShd[r] < fullShd[r]) fw++;
            if (finalShd[r] > fullShd[r]) fl++;
        }
        System.out.printf("full-data PC + validated pins: mean SHD %.2f, mean adjF1 %.3f, arrP %.3f, arrR %.3f (pin hits total %d)%n",
                gs / reps, gf / reps, gp / reps, gr / reps, hits);
        System.out.printf("vs full-data baseline: better SHD in %d/%d reps, worse in %d/%d%n", fw, reps, fl, reps);
    }

    private static IndependenceResult checkOnB(IndTestFisherZ testB, Map<String, Node> bNodes,
                                               IndependenceFact fact) throws InterruptedException {
        Node x = bNodes.get(fact.getX().getName());
        Node y = bNodes.get(fact.getY().getName());
        if (x == null || y == null) return null;
        Set<Node> z = new HashSet<>();
        for (Node node : fact.getZ()) {
            Node b = bNodes.get(node.getName());
            if (b == null) return null;
            z.add(b);
        }
        return testB.checkIndependence(x, y, z);
    }

    private static void record(double[][] adjP, double[][] adjR, double[][] adjF1, double[][] arrP,
                               double[][] arrR, double[][] shd, double[][] gated,
                               int rep, int iter, double[] m, double g) {
        adjP[rep][iter] = m[0];
        adjR[rep][iter] = m[1];
        adjF1[rep][iter] = m[2];
        arrP[rep][iter] = m[3];
        arrR[rep][iter] = m[4];
        shd[rep][iter] = m[5];
        gated[rep][iter] = g;
    }

    /**
     * |partial r| recovered from a Fisher Z p-value: z = Phi^{-1}(1 - p/2), r = tanh(z / sqrt(n - |Z| - 3)).
     * Saturated p-values are clamped, bounding the recovered effect rather than sending it to 1.
     */
    private static double effectFromP(double p, int zSize, int n) {
        double pc = Math.max(1e-15, Math.min(1 - 1e-15, p));
        double z = NORMAL.inverseCumulativeProbability(1.0 - pc / 2.0);
        double df = Math.max(1, n - zSize - 3);
        return Math.tanh(Math.abs(z) / Math.sqrt(df));
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String factKey(IndependenceFact f) {
        List<String> zs = new ArrayList<>();
        for (Node node : f.getZ()) zs.add(node.getName());
        Collections.sort(zs);
        return pairKey(f.getX().getName(), f.getY().getName()) + "||" + String.join(",", zs);
    }

    /**
     * Returns {adjP, adjR, adjF1, arrowP, arrowR, shd} of est vs. true CPDAG, matched by variable name.
     */
    private static double[] metrics(Graph est, Graph trueCpdag) {
        Map<String, Character> estMap = edgeMap(est);
        Map<String, Character> truMap = edgeMap(trueCpdag);

        int estAdj = estMap.size(), truAdj = truMap.size(), tpAdj = 0;
        for (String k : estMap.keySet()) if (truMap.containsKey(k)) tpAdj++;

        int estDir = 0, truDir = 0, tpDir = 0;
        for (Map.Entry<String, Character> e : estMap.entrySet()) if (e.getValue() != 'u') estDir++;
        for (Map.Entry<String, Character> e : truMap.entrySet()) if (e.getValue() != 'u') truDir++;
        for (Map.Entry<String, Character> e : estMap.entrySet()) {
            if (e.getValue() != 'u' && Objects.equals(truMap.get(e.getKey()), e.getValue())) tpDir++;
        }

        int shd = 0;
        Set<String> all = new HashSet<>(estMap.keySet());
        all.addAll(truMap.keySet());
        for (String k : all) {
            Character a = estMap.get(k), b = truMap.get(k);
            if (a == null || b == null) shd++;
            else if (!a.equals(b)) shd++;
        }

        double p = estAdj == 0 ? 0 : (double) tpAdj / estAdj;
        double r = truAdj == 0 ? 0 : (double) tpAdj / truAdj;
        double f1 = (p + r) == 0 ? 0 : 2 * p * r / (p + r);
        double ap = estDir == 0 ? 0 : (double) tpDir / estDir;
        double ar = truDir == 0 ? 0 : (double) tpDir / truDir;
        return new double[]{p, r, f1, ap, ar, shd};
    }

    /**
     * Unordered pair key -> 'u' (undirected), '>' (lexicographically-second node is head), '<' (first is head).
     */
    private static Map<String, Character> edgeMap(Graph g) {
        Map<String, Character> map = new HashMap<>();
        for (Edge e : g.getEdges()) {
            String n1 = e.getNode1().getName(), n2 = e.getNode2().getName();
            String a = n1.compareTo(n2) <= 0 ? n1 : n2;
            String b = n1.compareTo(n2) <= 0 ? n2 : n1;
            char c;
            if (Edges.isDirectedEdge(e)) {
                Node head = Edges.getDirectedEdgeHead(e);
                c = head.getName().equals(b) ? '>' : '<';
            } else {
                c = 'u';
            }
            map.put(a + "|" + b, c);
        }
        return map;
    }

    private static double mean(double[][] a, int col) {
        double s = 0;
        for (double[] row : a) s += row[col];
        return s / a.length;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int x : a) s += x;
        return s;
    }
}
