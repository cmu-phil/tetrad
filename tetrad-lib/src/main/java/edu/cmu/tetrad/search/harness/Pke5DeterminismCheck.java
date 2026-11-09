/// ////////////////////////////////////////////////////////////////////////////
// Pke5DeterminismCheck.java                                                   //
//                                                                             //
// PURPOSE.  PKE4 and PKE5 disagreed on counts that should be bit-identical    //
// (gated 17829 vs 17821, step-breaks 58 vs 56, illegal-H0 10358 vs 10368),    //
// while stalls (8) and distinct MAGs (2691) matched.  Before ANY N=7 count    //
// is quotable -- RESIDUE=2 included -- we must know whether the pipeline's    //
// verdicts are deterministic.  This harness answers that in about a minute    //
// by fingerprinting a sample of models repeatedly and diffing the verdicts.   //
//                                                                             //
// WHAT IT DOES.  For each selected model (mask, latent placement):            //
//   Phase 1 (consecutive repeats): compute the full decision fingerprint      //
//     REPEATS times back-to-back on one thread.  Each computation allocates   //
//     fresh Node/Graph objects, whose identity hash codes differ, so any      //
//     HashSet/HashMap iteration-order dependence inside FciOrient /           //
//     PagLegalityCheck / RecursiveBlocking (the DiscriminatingPath-hashCode   //
//     bug class) flips verdicts HERE, same thread, same order.                //
//   Phase 2 (delayed pass): after every key's repeats are done, compute one   //
//     more fingerprint per key and compare with the first.  A flip that       //
//     appears ONLY here (repeats stable) indicates static-state pollution     //
//     across models (a shared Tetrad cache), not per-call nondeterminism.     //
//                                                                             //
// FINGERPRINTS.  PhantomKernelEnumerator5.fingerprintModel produces two:      //
//   code   -- verdict codes only (H0: RBFAIL/ILLEGAL/NOTIMAP/GATED; per       //
//             deletion: NOSEP/LEGAL/ILLEGAL/BREAK-<bucket>/ABSENT; per stall: //
//             SWEEP=/ZM=).  Code instability = VERDICT flips = the thing      //
//             that must be pinned before quoting counts.                      //
//   strict -- code + reasons, false-CI witnesses, mechanism detail, sweep/ZM  //
//             counters.  Strict-only instability (code stable) = wording or   //
//             detail varies while verdicts hold; informative, not gating.     //
//                                                                             //
// INTERPRETATION.                                                             //
//   * Code flips in Phase 1  => live per-call nondeterminism.  Hunt iteration //
//     order (LinkedHashSet/LinkedHashMap sweep, or sort before iterating) in  //
//     the legality/orientation path; the flipping code line names the gate.   //
//     No PKE4/PKE5 count is stable-quotable until pinned.                     //
//   * All stable here, but full PKE5 reruns still disagree with each other    //
//     => nondeterminism lives in cases this sample missed; raise MAX_KEYS or  //
//     target the drifted cases directly via TARGETS.                          //
//   * All stable here AND full PKE5 reruns agree with each other (but not     //
//     with PKE4) => runs are internally deterministic; the PKE4-PKE5 delta is //
//     a code-difference effect; diff the shared pipeline, suspect a shared    //
//     static cache perturbed by the added magOfPag/isLegalMag calls.          //
//   * Phase-2-only flips => cross-model static-state pollution.               //
//                                                                             //
// TARGETING.  Default sweeps BLOCKS and fingerprints up to MAX_KEYS distinct  //
// canonical MAGs.  For the sharpest test, put the exemplars you actually care //
// about in TARGETS: the 2 RESIDUE entries (mask= and lat=[..] lines in        //
// pke5_stepbreak_mechanism.log) and the 8 stalls (exemplar dag mask / latent  //
// set in pke5_stall_counterexamples.log or pke5_stall_zm_rescue.log).         //
// NOTE: an exemplar (mask, latSet) re-derives the same canonical MAG, so its  //
// fingerprint covers the same H0/H1 population that produced the logged case. //
//                                                                             //
// COST.  Each fingerprint costs one per-key pipeline (~seconds of CPU).       //
// Keys run in parallel; repeats within a key are serial by design.  Defaults  //
// (BLOCKS={0,1}, MAX_KEYS=300, REPEATS=3, +1 delayed) are a few minutes on a  //
// multicore machine; a TARGETS-only run with ~10 entries is well under a      //
// minute.                                                                     //
//                                                                             //
// OUTPUT.  Console summary + pke5_determinism_diffs.log (full strict          //
// fingerprints for every unstable key, capped).                              //
//                                                                             //
// Same package as PhantomKernelEnumerator5; compiles against its              //
// package-private fingerprint entry points.  Run this class's main in         //
// IntelliJ.                                                                   //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.util.SublistGenerator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checks determinism for PhantomKernelEnumerator5.
 */
public final class Pke5DeterminismCheck {

    /**
     * Default constructor.
     */
    public Pke5DeterminismCheck() {

    }

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (all hard-coded; edit and re-run)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Specific exemplars to test, each {mask, latIdx1, latIdx2} with latent
     * indices 0-based as in PKE5's "latent set" log lines.  When non-empty,
     * BLOCKS is ignored.  RECOMMENDED: paste the 2 RESIDUE exemplars and the
     * 8 stall exemplars from the PKE5 logs, e.g.
     *     {123456L, 1, 4},
     */
    private static final long[][] TARGETS = {
            {314672, 0, 1},
            {400048, 0, 1},
            // {mask, lat1, lat2},
    };

    /** Blocks (of PhantomKernelEnumerator5.BLOCK_SIZE masks) to sweep when
     *  TARGETS is empty. */
    private static final long[] BLOCKS = {0L, 1L};

    /** Cap on distinct canonical MAGs fingerprinted (block mode). */
    private static final int MAX_KEYS = 300;

    /** Consecutive fingerprints per key (Phase 1).  >= 2. */
    private static final int REPEATS = 3;

    /** One extra fingerprint per key after all keys finish (Phase 2). */
    private static final boolean DELAYED_PASS = true;

    private static final String DIFF_LOG_PATH = "pke5_determinism_diffs.log";
    /** Max unstable keys dumped in full to the diff log. */
    private static final int DIFF_DUMP_CAP = 20;

    // ────────────────────────────────────────────────────────────────────────

    private static final class Unit {
        final long mask;
        final int[] latChoice;
        final String key;

        Unit(long mask, int[] latChoice, String key) {
            this.mask = mask;
            this.latChoice = latChoice;
            this.key = key;
        }
    }

    private static final class KeyReport {
        Unit unit;
        List<PhantomKernelEnumerator5.Fp> reps = new ArrayList<>();  // Phase 1
        PhantomKernelEnumerator5.Fp delayed;                          // Phase 2 (nullable)
        boolean codeStable = true;      // across Phase 1 repeats
        boolean strictStable = true;
        boolean delayedCodeStable = true;
        boolean delayedStrictStable = true;
        String firstCodeDiff;           // human-readable first divergence
    }

    /**
     * Runs the check.
     * @param args Arguments.
     * @throws IOException If any.
     */
    public static void main(String[] args) throws IOException {
        System.err.println(PhantomKernelEnumerator5.CONFIG_LINE);
        System.err.printf("Determinism check: %s | REPEATS=%d delayedPass=%b maxKeys=%d%n",
                TARGETS.length > 0 ? (TARGETS.length + " explicit TARGETS")
                        : ("blocks " + Arrays.toString(BLOCKS)),
                REPEATS, DELAYED_PASS, MAX_KEYS);

        // ---- Phase 0: discover work units (one exemplar per canonical key) ----
        List<Unit> units = discoverUnits();
        System.err.printf("distinct canonical MAGs to test: %d%n", units.size());
        if (units.isEmpty()) {
            System.err.println("Nothing to test -- check TARGETS/BLOCKS.");
            return;
        }

        // ---- Phase 1: consecutive repeats per key (parallel ACROSS keys, serial WITHIN) ----
        long t0 = System.currentTimeMillis();
        AtomicLong done = new AtomicLong();
        List<KeyReport> reports = units.parallelStream().map(u -> {
            KeyReport kr = new KeyReport();
            kr.unit = u;
            for (int i = 0; i < REPEATS; i++) {
                kr.reps.add(fingerprintSafe(u));
            }
            for (int i = 1; i < kr.reps.size(); i++) {
                if (!kr.reps.get(i).code.equals(kr.reps.get(0).code)) kr.codeStable = false;
                if (!kr.reps.get(i).strict.equals(kr.reps.get(0).strict)) kr.strictStable = false;
            }
            if (!kr.codeStable) {
                kr.firstCodeDiff = firstLineDiff(kr.reps.get(0).code, divergent(kr.reps).code);
            }
            long d = done.incrementAndGet();
            if ((d & 0x1F) == 0) {
                System.err.printf("  ...%d/%d keys, %.1f min%n", d, units.size(),
                        (System.currentTimeMillis() - t0) / 60000.0);
            }
            return kr;
        }).collect(java.util.stream.Collectors.toList());

        // ---- Phase 2: delayed pass ----
        if (DELAYED_PASS) {
            reports.parallelStream().forEach(kr -> {
                kr.delayed = fingerprintSafe(kr.unit);
                kr.delayedCodeStable = kr.delayed.code.equals(kr.reps.get(0).code);
                kr.delayedStrictStable = kr.delayed.strict.equals(kr.reps.get(0).strict);
            });
        }

        // ---- Report ----
        long codeUnstable = reports.stream().filter(k -> !k.codeStable).count();
        long strictOnly = reports.stream().filter(k -> k.codeStable && !k.strictStable).count();
        long delayedOnlyCode = reports.stream()
                .filter(k -> k.codeStable && !k.delayedCodeStable).count();
        long delayedOnlyStrict = reports.stream()
                .filter(k -> k.strictStable && k.delayedCodeStable && !k.delayedStrictStable).count();

        // Aggregate flip categories over Phase-1 code-unstable keys.
        Map<String, Integer> flipCats = new TreeMap<>();
        for (KeyReport kr : reports) {
            if (kr.codeStable) continue;
            categorizeFlips(kr.reps.get(0).code, divergent(kr.reps).code, flipCats);
        }

        StringBuilder sum = new StringBuilder();
        sum.append("==== PKE5 DETERMINISM CHECK SUMMARY ====\n");
        sum.append(PhantomKernelEnumerator5.CONFIG_LINE).append('\n');
        sum.append(String.format("units (distinct canonical MAGs)     : %d%n", reports.size()));
        sum.append(String.format("repeats per key (consecutive)       : %d%s%n",
                REPEATS, DELAYED_PASS ? "  + 1 delayed" : ""));
        sum.append(String.format("Phase 1 CODE-UNSTABLE keys          : %d%s%n", codeUnstable,
                codeUnstable == 0
                        ? "   (no verdict flips: per-call pipeline is deterministic on this sample)"
                        : "   *** VERDICT FLIPS: pin the nondeterminism before quoting ANY count ***"));
        sum.append(String.format("Phase 1 strict-only unstable        : %d%s%n", strictOnly,
                strictOnly == 0 ? "" : "   (verdicts stable; reason/detail wording varies)"));
        if (DELAYED_PASS) {
            sum.append(String.format("Phase 2 delayed-only CODE flips     : %d%s%n", delayedOnlyCode,
                    delayedOnlyCode == 0 ? ""
                            : "   *** repeats stable but delayed flips: cross-model static-state pollution ***"));
            sum.append(String.format("Phase 2 delayed-only strict flips   : %d%n", delayedOnlyStrict));
        }
        if (!flipCats.isEmpty()) {
            sum.append("flip categories (Phase 1, by gate):\n");
            for (Map.Entry<String, Integer> en : flipCats.entrySet()) {
                sum.append(String.format("  %-34s: %d%n", en.getKey(), en.getValue()));
            }
        }
        sum.append("==== END SUMMARY ====");
        System.out.println(sum);

        // ---- Diff log: full strict dumps for unstable keys ----
        try (PrintWriter w = new PrintWriter(new FileWriter(DIFF_LOG_PATH, false))) {
            w.println(sum);
            int dumped = 0;
            for (KeyReport kr : reports) {
                boolean unstable = !kr.codeStable || !kr.strictStable
                        || !kr.delayedCodeStable || !kr.delayedStrictStable;
                if (!unstable) continue;
                if (dumped++ >= DIFF_DUMP_CAP) {
                    w.println("==== further unstable keys suppressed (cap " + DIFF_DUMP_CAP + ") ====");
                    break;
                }
                w.println();
                w.println("==== UNSTABLE KEY ====");
                w.println("exemplar mask=" + kr.unit.mask
                        + " latChoice=" + Arrays.toString(kr.unit.latChoice));
                w.println("key=" + kr.unit.key);
                w.println("codeStable=" + kr.codeStable + " strictStable=" + kr.strictStable
                        + " delayedCodeStable=" + kr.delayedCodeStable
                        + " delayedStrictStable=" + kr.delayedStrictStable);
                if (kr.firstCodeDiff != null) {
                    w.println("first code divergence: " + kr.firstCodeDiff);
                }
                for (int i = 0; i < kr.reps.size(); i++) {
                    w.println("---- repeat " + (i + 1) + " (strict) ----");
                    w.println(kr.reps.get(i).strict);
                }
                if (kr.delayed != null) {
                    w.println("---- delayed pass (strict) ----");
                    w.println(kr.delayed.strict);
                }
            }
            if (dumped == 0) w.println("\n(no unstable keys)");
            w.flush();
        }
        System.out.println("diffs (if any) written to: " + DIFF_LOG_PATH);
        System.out.printf("elapsed: %.1f min%n", (System.currentTimeMillis() - t0) / 60000.0);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Work-unit discovery
    // ────────────────────────────────────────────────────────────────────────

    private static List<Unit> discoverUnits() {
        Map<String, Unit> byKey = new ConcurrentHashMap<>();

        if (TARGETS.length > 0) {
            for (long[] t : TARGETS) {
                long mask = t[0];
                int[] latChoice = new int[]{(int) t[1], (int) t[2]};
                Arrays.sort(latChoice);
                try {
                    String key = PhantomKernelEnumerator5.canonicalKeyForModel(mask, latChoice);
                    byKey.putIfAbsent(key, new Unit(mask, latChoice, key));
                } catch (Exception ex) {
                    System.err.println("TARGET mask=" + mask + " lat=" + Arrays.toString(latChoice)
                            + " failed key computation: " + ex);
                }
            }
        } else {
            outer:
            for (long b : BLOCKS) {
                long lo = b * PhantomKernelEnumerator5.BLOCK_SIZE;
                long hi = Math.min(PhantomKernelEnumerator5.TOTAL_DAGS,
                        lo + PhantomKernelEnumerator5.BLOCK_SIZE);
                for (long mask = lo; mask < hi; mask++) {
                    SublistGenerator latGen = new SublistGenerator(
                            PhantomKernelEnumerator5.N, PhantomKernelEnumerator5.NUM_LATENT);
                    int[] latChoice;
                    while ((latChoice = latGen.next()) != null) {
                        if (latChoice.length != PhantomKernelEnumerator5.NUM_LATENT) continue;
                        try {
                            String key = PhantomKernelEnumerator5.canonicalKeyForModel(
                                    mask, latChoice.clone());
                            byKey.putIfAbsent(key, new Unit(mask, latChoice.clone(), key));
                        } catch (Exception ignore) {
                            // mirrors accumulate's per-model skip; not a determinism question here
                        }
                        if (byKey.size() >= MAX_KEYS) break outer;
                    }
                }
            }
        }
        List<Unit> units = new ArrayList<>(byKey.values());
        units.sort(Comparator.comparing(u -> u.key));   // deterministic processing order
        return units;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    /** Fingerprint that never throws: an exception becomes the fingerprint, so an
     *  exception-vs-success flip is itself reported as instability. */
    private static PhantomKernelEnumerator5.Fp fingerprintSafe(Unit u) {
        try {
            return PhantomKernelEnumerator5.fingerprintModel(u.mask, u.latChoice);
        } catch (Exception ex) {
            PhantomKernelEnumerator5.Fp f = new PhantomKernelEnumerator5.Fp();
            f.key = u.key;
            f.code = "EXCEPTION " + ex.getClass().getSimpleName() + "\n";
            f.strict = "EXCEPTION " + ex + "\n";
            return f;
        }
    }

    /** First repeat whose code differs from repeat 0 (caller guarantees one exists). */
    private static PhantomKernelEnumerator5.Fp divergent(List<PhantomKernelEnumerator5.Fp> reps) {
        for (int i = 1; i < reps.size(); i++) {
            if (!reps.get(i).code.equals(reps.get(0).code)) return reps.get(i);
        }
        return reps.get(reps.size() - 1);
    }

    private static String firstLineDiff(String a, String b) {
        String[] la = a.split("\n", -1), lb = b.split("\n", -1);
        int n = Math.min(la.length, lb.length);
        for (int i = 0; i < n; i++) {
            if (!la[i].equals(lb[i])) {
                return "line " + (i + 1) + ": [" + la[i] + "]  vs  [" + lb[i] + "]";
            }
        }
        return "line-count mismatch: " + la.length + " vs " + lb.length
                + " (structural divergence: a verdict flip changed which lines exist downstream)";
    }

    /** Categorize which gate each differing line pair flips at. */
    private static void categorizeFlips(String a, String b, Map<String, Integer> cats) {
        String[] la = a.split("\n", -1), lb = b.split("\n", -1);
        int n = Math.min(la.length, lb.length);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            String x = la[i], y = lb[i];
            if (x.equals(y)) continue;
            any = true;
            String cat;
            if (x.contains(" STALL: ") || y.contains(" STALL: ")) cat = "stall sweep/ZM verdict";
            else if (x.contains("BREAK-") && y.contains("BREAK-")) cat = "break BUCKET flip";
            else if (x.contains("BREAK-") || y.contains("BREAK-")) cat = "break PRESENCE flip";
            else if (x.contains(" del ") || y.contains(" del ")) cat = "deletion legality/sepset";
            else if (x.startsWith("EXCEPTION") || y.startsWith("EXCEPTION")) cat = "exception flip";
            else cat = "H0 gate (legality/I-map/RB)";
            cats.merge(cat, 1, Integer::sum);
            break;   // first divergence only: downstream lines shift once structure changes
        }
        if (!any && la.length != lb.length) {
            cats.merge("structural (line-count) divergence", 1, Integer::sum);
        }
    }
}
