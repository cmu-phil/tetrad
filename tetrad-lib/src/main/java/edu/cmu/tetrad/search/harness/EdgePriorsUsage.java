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

    /** Stands in for IndTestFisherZ built on a (K + T) principal submatrix. */
    static class SubTest implements IndependenceTest {
        final List<Node> vars;
        boolean verbose;
        SubTest(List<Node> vars) { this.vars = vars; }
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.5, 0.0);
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
        double[] s = standardisedAnnotation(M, new Random(1));

        double[][] beta = EdgePriors.neutralMatrix(locusNames.size(), EdgePriors.Semantics.LOG_ODDS);
        int iY1 = M, iY2 = M + 1;

        for (int j = 0; j < M; j++) {
            double b = tau * s[j];
            beta[j][iY1] = beta[iY1][j] = b;     // SNP -- BMI
            beta[j][iY2] = beta[iY2][j] = b;     // SNP -- SBP
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
                + "  (of " + (locusVars.size() * (locusVars.size() - 1) / 2) + " total)");

        // === STEP 3, per repeat: restrict, wrap, search =====================
        //
        // locusWeights is immutable: share it across all R repeats, including
        // parallel ones. The only per-repeat work is restrictTo.

        Random rng = new Random(2);

        for (int r = 0; r < 3; r++) {                       // 3 of R, for show
            List<Node> sub = sampleSubset(locusVars, M, K, y1, y2, rng);

            IndependenceTest base = new SubTest(sub);       // IndTestFisherZ(subCov, ALPHA)
            IndependenceTest test = new EdgePriorTest(base, locusWeights.restrictTo(sub), ALPHA);

            // new Pc(test).search();  -- unchanged from here on

            EdgePriorTest ept = (EdgePriorTest) test;
            Node someSnp = sub.get(0);
            System.out.printf("repeat %d: %d nodes; alpha(%s, BMI) = %.4f; alpha(%s, %s) = %.4f%n",
                    r, sub.size(),
                    someSnp.getName(), ept.getAlpha(someSnp, y1),
                    someSnp.getName(), sub.get(1).getName(), ept.getAlpha(someSnp, sub.get(1)));
        }

        // === What goes wrong if you skip step 3 ============================

        List<Node> sub = sampleSubset(locusVars, M, K, y1, y2, rng);
        try {
            new EdgePriorTest(new SubTest(sub), locusWeights, ALPHA);   // un-restricted
            System.out.println("\n!! no throw -- guard is broken");
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage();
            System.out.println("\nun-restricted prior is rejected, as intended:");
            System.out.println("  " + msg.substring(0, Math.min(96, msg.length())) + " ...");
        }

        System.out.println("\nR would be " + R + " repeats for this locus (m/K * 50).");
    }

    /** Annotation score, standardised to mean zero and unit SD across SNPs. */
    private static double[] standardisedAnnotation(int m, Random rng) {
        double[] s = new double[m];
        for (int j = 0; j < m; j++) s[j] = rng.nextDouble();     // stand-in
        double mean = 0.0;
        for (double v : s) mean += v;
        mean /= m;
        double ss = 0.0;
        for (double v : s) ss += (v - mean) * (v - mean);
        double sd = Math.sqrt(ss / (m - 1));
        for (int j = 0; j < m; j++) s[j] = (s[j] - mean) / sd;
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
