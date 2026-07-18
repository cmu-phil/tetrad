// Worked example: how the anchored edge prior is meant to be wired into a
// random-subspace ensemble over a GWAS locus.
//
// Compiles against the same fakes as the other checks in this directory, not
// against real Tetrad. The shape of the calls is the point, not the types.
//
// The three rules this illustrates:
//
//   1. Build the prior ONCE per locus, over every candidate SNP. Not per repeat.
//   2. Verify its names ONCE, against the full locus variable list.
//   3. restrictTo(...) per repeat. That is the only per-repeat prior work.
//
// Everything else -- the alpha, the test, the search -- is untouched.

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.EdgePriorTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class EdgePriorsUsage {

    // ---- stand-ins -------------------------------------------------------

    /**
     * Stands in for IndTestFisherZ on a (K + T) principal submatrix. Returns a FIXED p-value,
     * chosen to straddle the per-edge thresholds so that the prior's effect on the decision is
     * visible rather than merely its effect on the threshold.
     */
    static class SubTest implements IndependenceTest {
        final List<Node> vars;
        final double p;
        boolean verbose;
        SubTest(List<Node> vars, double p) { this.vars = vars; this.p = p; }
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            return new IndependenceResult(new IndependenceFact(x, y, z), p > 0.01, p, 0.01 - p);
        }
        public List<Node> getVariables() { return vars; }
        public DataModel getData() { return null; }
        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean v) { verbose = v; }
        public String toString() { return "IndTestFisherZ(sub)"; }
    }

    // ---- the flow --------------------------------------------------------

    public static void main(String[] args) {
        final double ALPHA = 0.01;     // the tuned value; unchanged
        final double P = 0.012;        // a p-value that straddles the primed thresholds
        final int M = 2000;            // candidate SNPs surviving the marginal filter
        final int K = 100;             // SNPs per repeat
        final int R = M / K * 50;      // repeats, for coverage 50

        // Full locus variable list: m SNPs plus the traits.
        List<Node> locusVars = new ArrayList<>();
        for (int j = 0; j < M; j++) locusVars.add(new GraphNode("rs" + j));
        Node y1 = new GraphNode("BMI"), y2 = new GraphNode("SBP");
        locusVars.add(y1);
        locusVars.add(y2);

        List<String> locusNames = new ArrayList<>();
        for (Node v : locusVars) locusNames.add(v.getName());

        // === STEP 1, once per locus: beta = tau * s, with s standardised ====
        //
        // s_ij is the exogenous annotation score for the SNP-trait pair (eQTL
        // support, functional annotation, distance to a plausible gene, ...).
        // Standardise it to MEAN ZERO across the SNP-trait pairs before scaling
        // by tau. Biological annotations are nearly all positive; feeding them
        // in raw pushes every alpha_ij up, which just gives a denser graph and
        // confounds "the prior helped" with "the threshold was loosened".
        //
        // With s centred, tau = 0 reproduces the tuned pipeline bit for bit,
        // and tau > 0 redistributes rather than loosens. Sweep tau.

        double tau = 0.5;
        int iY1 = M, iY2 = M + 1;

        // NOTE the shape: s is indexed by (SNP, TRAIT), not by SNP alone.
        //
        // This matters more than it looks. The three mechanisms are distinguished
        // by WHICH trait a SNP feeds: pleiotropy is one SNP into both traits,
        // linkage is different SNPs into different traits. A trait-agnostic prior
        // -- the same beta on SNP--BMI and SNP--SBP -- protects both edges of every
        // SNP equally, so at a true linkage locus it shields the spurious edge just
        // as hard as the real one, and the locus drifts toward looking pleiotropic.
        // Pleiotropy is already the hardest label to call. If the annotation really
        // is trait-agnostic, say so explicitly; it biases the very thing being
        // measured.
        double[][] s = standardisedAnnotation(M, 2, new Random(1));

        double[][] beta = EdgePriors.neutralMatrix(locusNames.size(), EdgePriors.Semantics.LOG_ODDS);

        for (int j = 0; j < M; j++) {
            beta[j][iY1] = beta[iY1][j] = tau * s[j][0];     // SNP -- BMI
            beta[j][iY2] = beta[iY2][j] = tau * s[j][1];     // SNP -- SBP
        }
        // SNP--SNP and trait--trait pairs stay at beta = 0, hence alpha_ij = ALPHA.
        // Nothing to decide about them; there is no normalisation.

        EdgePriors logOdds = EdgePriors.fromMatrix(locusNames, beta, EdgePriors.Semantics.LOG_ODDS);

        // === STEP 2, once per locus: verify names, then anchor ==============
        //
        // Check against the FULL locus list. This is where a typo'd rsID or a
        // prior built for the wrong locus gets caught -- do it here, not inside
        // a repeat, and not a thousand times.

        List<String> unmatched = logOdds.unmatchedNames(locusVars);
        if (!unmatched.isEmpty()) {
            throw new IllegalStateException("Prior names not in the locus: " + unmatched);
        }

        // Anchored at the tuned alpha: beta = 0 => alpha_ij = ALPHA exactly.
        // Note what this call does NOT take: n. That is the whole point --
        // toWeightsViaBicBridge(lambda, n, alpha) needs a trustworthy
        // lambda * log(n), which summary-statistic data cannot supply, whereas
        // the anchored form needs only the alpha you already tuned.
        EdgePriors locusWeights = logOdds.toWeightsAnchoredAtAlpha(ALPHA);

        System.out.println("locus prior: " + locusWeights);
        System.out.println("  pairs carrying a prior: " + locusWeights.size()
                + " of " + (locusVars.size() * (locusVars.size() - 1) / 2)
                + " locus pairs -- but the number that matters is per repeat,");
        System.out.println("  where " + (K * 2) + " of " + ((K + 2) * (K + 1) / 2)
                + " pairs carry a prior and the rest sit at alpha = " + ALPHA + ".");

        // === STEP 3, per repeat: restrict, wrap, search =====================
        //
        // locusWeights is immutable: share it across all R repeats, including
        // parallel ones. The only per-repeat work is restrictTo.

        // --- tau = 0 must reproduce the unwrapped pipeline exactly ---------
        //
        // This is the regression test, and it exercises the whole path: build,
        // anchor, restrict, wrap. Any indexing error shows up here as a diff
        // against the bare test. Run it on a real locus before any sweep.

        Random rng = new Random(2);
        List<Node> probe = sampleSubset(locusVars, M, K, y1, y2, rng);
        double[][] zeroBeta = EdgePriors.neutralMatrix(locusNames.size(), EdgePriors.Semantics.LOG_ODDS);
        EdgePriors tau0 = EdgePriors.fromMatrix(locusNames, zeroBeta, EdgePriors.Semantics.LOG_ODDS)
                .toWeightsAnchoredAtAlpha(ALPHA);
        IndependenceTest bare = new SubTest(probe, P);
        EdgePriorTest wrapped = new EdgePriorTest(bare, tau0.restrictTo(probe), ALPHA);
        boolean identical = true;
        for (Node x : probe) {
            for (Node y : probe) {
                if (x == y) continue;
                try {
                    if (bare.checkIndependence(x, y, java.util.Collections.emptySet()).isIndependent()
                            != wrapped.checkIndependence(x, y, java.util.Collections.emptySet()).isIndependent()) {
                        identical = false;
                    }
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            }
        }
        System.out.println("\ntau = 0 reproduces the bare test on all "
                + (probe.size() * (probe.size() - 1)) + " ordered pairs: " + identical);
        System.out.println("  (alpha_ij = " + wrapped.getAlpha(probe.get(0), y1) + " everywhere)\n");

        for (int r = 0; r < 3; r++) {                       // 3 of R, for show
            List<Node> sub = sampleSubset(locusVars, M, K, y1, y2, rng);

            IndependenceTest base = new SubTest(sub, P);       // IndTestFisherZ(subCov, ALPHA)
            IndependenceTest test = new EdgePriorTest(base, locusWeights.restrictTo(sub), ALPHA);

            // new Pc(test).search();  -- unchanged from here on

            EdgePriorTest ept = (EdgePriorTest) test;
            Node snp = sub.get(0);
            Node other = sub.get(1);

            // Same p on every pair. Only the prior differs. Watch the verdict move.
            System.out.printf("repeat %d (%d nodes), p = %.3f on every pair:%n", r, sub.size(), P);
            System.out.printf("    %-8s-- %-4s : alpha_ij = %.4f  -> %s%n",
                    snp.getName(), "BMI", ept.getAlpha(snp, y1), verdict(ept, snp, y1));
            System.out.printf("    %-8s-- %-4s : alpha_ij = %.4f  -> %s%n",
                    snp.getName(), "SBP", ept.getAlpha(snp, y2), verdict(ept, snp, y2));
            System.out.printf("    %-8s-- %-4s : alpha_ij = %.4f  -> %s   (no prior)%n",
                    snp.getName(), other.getName(), ept.getAlpha(snp, other), verdict(ept, snp, other));
        }

        // === What goes wrong if you skip step 3 ============================

        List<Node> sub = sampleSubset(locusVars, M, K, y1, y2, rng);
        try {
            new EdgePriorTest(new SubTest(sub, P), locusWeights, ALPHA);   // un-restricted
            System.out.println("\n!! no throw -- guard is broken");
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage();
            System.out.println("\nun-restricted prior is rejected, as intended:");
            System.out.println("  " + msg.substring(0, Math.min(96, msg.length())) + " ...");
        }

        System.out.println("\nR would be " + R + " repeats for this locus (m/K * 50).");
    }

    /** Reports what the test decides for a pair, and hence whether the edge survives. */
    private static String verdict(EdgePriorTest t, Node x, Node y) {
        try {
            return t.checkIndependence(x, y, java.util.Collections.emptySet()).isIndependent()
                    ? "independent -> edge DELETED"
                    : "dependent   -> edge KEPT";
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Annotation score per (SNP, trait) pair, standardised to mean zero and unit SD across all
     * m * t pairs -- which is the set the prior actually lives on, and therefore the set whose
     * mean has to be zero for tau = 0 to be a real baseline.
     */
    private static double[][] standardisedAnnotation(int m, int t, Random rng) {
        double[][] s = new double[m][t];
        for (int j = 0; j < m; j++)
            for (int k = 0; k < t; k++) s[j][k] = rng.nextDouble();     // stand-in

        double mean = 0.0;
        for (double[] row : s) for (double v : row) mean += v;
        mean /= (double) m * t;

        double ss = 0.0;
        for (double[] row : s) for (double v : row) ss += (v - mean) * (v - mean);
        double sd = Math.sqrt(ss / (m * t - 1));

        for (int j = 0; j < m; j++)
            for (int k = 0; k < t; k++) s[j][k] = (s[j][k] - mean) / sd;
        return s;
    }

    /** K SNPs drawn without replacement, plus every trait, as each repeat does. */
    private static List<Node> sampleSubset(List<Node> locusVars, int m, int k, Node y1, Node y2, Random rng) {
        Set<Integer> picked = new LinkedHashSet<>();
        while (picked.size() < k) picked.add(rng.nextInt(m));
        List<Node> sub = new ArrayList<>();
        for (int idx : picked) sub.add(locusVars.get(idx));
        sub.add(y1);
        sub.add(y2);
        return sub;
    }
}
