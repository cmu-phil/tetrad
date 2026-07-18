// Sanity check for the anchored conversion and the normalisation guard.
// Compiles against fakes; adapt or lift into JUnit.
package edu.cmu.tetrad.search.harness;
import edu.cmu.tetrad.search.utils.EdgePriors;
import java.util.List;

public class EdgePriorsAnchorCheck extends EdgePriorsCheck {
    static int fails = 0;
    static void ok(String w, boolean c) { System.out.printf("  [%s] %s%n", c?"PASS":"FAIL", w); if(!c) fails++; }

    static EdgePriors lo(double beta) {
        double[][] b = EdgePriors.neutralMatrix(3, EdgePriors.Semantics.LOG_ODDS);
        b[0][1] = b[1][0] = beta;
        return EdgePriors.fromMatrix(List.of("A","B","C"), b, EdgePriors.Semantics.LOG_ODDS);
    }

    public static void main(String[] a) {
        double alpha = 0.01;

        System.out.println("1. Anchored: beta = 0 reproduces the tuned alpha exactly");
        // beta=0 stores nothing (0 is neutral), so probe via a tiny beta and via the neutral store
        EdgePriors z = EdgePriors.neutral(EdgePriors.Semantics.LOG_ODDS).toWeightsAnchoredAtAlpha(alpha);
        ok("neutral log-odds -> all weights 1", z.get("A","B") == 1.0 && z.size() == 0);
        EdgePriors eps = lo(1e-12).toWeightsAnchoredAtAlpha(alpha);
        ok("beta->0 gives w->1", Math.abs(eps.get("A","B") - 1.0) < 1e-6);

        System.out.println("\n2. Anchored: alpha_ij tracks the note's table");
        double[][] want = {{-1.0, 0.0033}, {0.5, 0.0176}, {1.0, 0.0313}, {2.0, 0.1045}};
        for (double[] row : want) {
            double w = lo(row[0]).toWeightsAnchoredAtAlpha(alpha).get("A","B");
            double aij = w * alpha;
            ok(String.format("beta=%+.1f -> alpha_ij=%.4f (want %.4f)", row[0], aij, row[1]),
                    Math.abs(aij - row[1]) < 5e-4);
        }

        System.out.println("\n3. Anchored: prior saturates at beta = c0^2/2 = 3.317");
        ok("beta=3.32 -> alpha_ij ~ 1 (undeletable)", lo(3.32).toWeightsAnchoredAtAlpha(alpha).get("A","B")*alpha > 0.97);
        ok("beta=10 clamps, does not blow up", lo(10.0).toWeightsAnchoredAtAlpha(alpha).get("A","B")*alpha == 1.0);

        System.out.println("\n4. Anchored differs from the BIC bridge exactly as predicted");
        double wBridge = lo(0.0001).toWeightsViaBicBridge(1.0, 50000, alpha).get("A","B");
        ok("bridge at lambda=1, beta~0 -> alpha_ij ~ 0.001, NOT 0.01", Math.abs(wBridge*alpha - 0.001) < 1e-4);
        double wAnch = lo(0.0001).toWeightsAnchoredAtAlpha(alpha).get("A","B");
        ok("anchored at same beta -> alpha_ij = 0.01", Math.abs(wAnch*alpha - 0.01) < 1e-5);
        // lambda_equiv = c0^2/ln n makes the bridge coincide with the anchored form
        double lamEq = Math.pow(2.5758293, 2) / Math.log(50000);
        double wEq = lo(0.5).toWeightsViaBicBridge(lamEq, 50000, alpha).get("A","B");
        double wAn = lo(0.5).toWeightsAnchoredAtAlpha(alpha).get("A","B");
        ok(String.format("bridge at lambda_equiv=%.4f == anchored", lamEq), Math.abs(wEq - wAn) < 1e-6);

        System.out.println("\n5. The guard");
        try { lo(0.5).toWeightsViaBicBridge(20.0, 50000, alpha).normalizedToMeanOne();
            ok("bridge weights refuse normalisation", false); }
        catch (IllegalStateException ex) { ok("bridge weights refuse normalisation",
                ex.getMessage().contains("toWeightsAnchoredAtAlpha")); }
        try { EdgePriors n = lo(0.5).toWeightsAnchoredAtAlpha(alpha).normalizedToMeanOne();
            ok("anchored weights normalise fine", n.getOrigin() == EdgePriors.Origin.NORMALIZED); }
        catch (IllegalStateException ex) { ok("anchored weights normalise fine", false); }

        System.out.println("\n6. Origins are tracked");
        ok("declared", lo(0.5).getOrigin() == EdgePriors.Origin.DECLARED);
        ok("bridge",   lo(0.5).toWeightsViaBicBridge(1,50000,alpha).getOrigin() == EdgePriors.Origin.BIC_BRIDGE);
        ok("anchored", lo(0.5).toWeightsAnchoredAtAlpha(alpha).getOrigin() == EdgePriors.Origin.ANCHORED);

        System.out.println(fails == 0 ? "\nALL PASS" : "\n" + fails + " FAILURES");
        if (fails > 0) System.exit(1);
    }
}
