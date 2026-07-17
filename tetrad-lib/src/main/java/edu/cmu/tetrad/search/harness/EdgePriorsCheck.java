// Standalone sanity check for EdgePriors, EdgePriorScore, EdgePriorTest.
package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.EdgePriorScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.*;
import edu.cmu.tetrad.search.utils.EdgePriors;
import java.util.*;

public class EdgePriorsCheck {
    static int fails = 0;
    static void ok(String what, boolean cond) {
        System.out.printf("  [%s] %s%n", cond ? "PASS" : "FAIL", what);
        if (!cond) fails++;
    }

    // Deterministic fake: localScore(y, S) = 100*y + sum(S) ; no prior inside.
    static class FakeScore implements Score {
        final List<Node> vars;
        FakeScore(List<Node> vars) { this.vars = vars; }
        public double localScore(int node, int... parents) {
            double s = 100.0 * node;
            for (int p : parents) s += p;
            return s;
        }
        public List<Node> getVariables() { return vars; }
        public int getSampleSize() { return 50000; }
        public String toString() { return "FakeScore"; }
    }

    static class FakeTest implements IndependenceTest {
        final List<Node> vars; final double p; boolean verbose;
        FakeTest(List<Node> vars, double p) { this.vars = vars; this.p = p; }
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            return new IndependenceResult(new IndependenceFact(x,y,z), p > 0.01, p, 0.01 - p);
        }
        public List<Node> getVariables() { return vars; }
        public DataModel getData() { return null; }
        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean v) { verbose = v; }
        public String toString() { return "FakeTest"; }
    }

    public static void main(String[] a) throws Exception {
        Node A = new GraphNode("A"), B = new GraphNode("B"), C = new GraphNode("C");
        List<Node> vars = List.of(A, B, C);            // score order: A=0, B=1, C=2
        FakeScore base = new FakeScore(vars);

        System.out.println("1. Neutral prior is an exact no-op on the score");
        Score neut = new EdgePriorScore(base, EdgePriors.neutral(EdgePriors.Semantics.LOG_ODDS));
        boolean same = true;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == y) continue;
                if (neut.localScore(y, x) != base.localScore(y, x)) same = false;
                if (neut.localScoreDiff(x, y, new int[0]) != base.localScoreDiff(x, y, new int[0])) same = false;
            }
        }
        ok("localScore and localScoreDiff identical to delegate", same);

        System.out.println("\n2. localScoreDiff picks up exactly 2*beta_xy");
        // prior built in a DIFFERENT variable order than the score: C, A, B
        List<String> priorOrder = List.of("C", "A", "B");
        double[][] beta = new double[3][3];
        beta[1][2] = beta[2][1] = 0.75;   // in prior order this is (A,B)
        EdgePriors lo = EdgePriors.fromMatrix(priorOrder, beta, EdgePriors.Semantics.LOG_ODDS);
        ok("prior resolves (A,B) regardless of its own ordering", lo.get("A","B") == 0.75);
        ok("prior is symmetric", lo.get("B","A") == 0.75);
        ok("unset pair defaults to 0", lo.get("A","C") == 0.0);

        Score ps = new EdgePriorScore(base, lo);
        double d = ps.localScoreDiff(0, 1, new int[0]);      // add A(0) to parents of B(1)
        double d0 = base.localScoreDiff(0, 1, new int[0]);
        ok("diff for the primed pair = base + 2*0.75", Math.abs(d - (d0 + 1.5)) < 1e-12);
        double e = ps.localScoreDiff(0, 2, new int[0]);      // add A(0) to parents of C(2): unprimed
        double e0 = base.localScoreDiff(0, 2, new int[0]);
        ok("diff for an unprimed pair is unchanged", Math.abs(e - e0) < 1e-12);

        System.out.println("\n3. Beta terms for conditioning parents cancel in the diff");
        double f  = ps.localScoreDiff(0, 1, new int[]{2});   // z = {C}
        double f0 = base.localScoreDiff(0, 1, new int[]{2});
        ok("diff given z={C} still = base + 2*beta_AB", Math.abs(f - (f0 + 1.5)) < 1e-12);

        System.out.println("\n4. Score equivalence: prior sum depends only on the skeleton");
        // A->B and B->A charge beta_AB to different children, but to the same total.
        double ab = ps.localScore(1, 0) - base.localScore(1, 0);   // beta charged to B
        double ba = ps.localScore(0, 1) - base.localScore(0, 1);   // beta charged to A
        ok("either orientation contributes the same 2*beta", Math.abs(ab - ba) < 1e-12 && Math.abs(ab - 1.5) < 1e-12);

        System.out.println("\n5. Guards fire");
        try { EdgePriors.fromMatrix(List.of("A","B"), new double[][]{{0,1},{2,0}}, EdgePriors.Semantics.LOG_ODDS); ok("asymmetric matrix rejected", false); }
        catch (IllegalArgumentException ex) { ok("asymmetric matrix rejected", ex.getMessage().contains("symmetric")); }
        try { new EdgePriorScore(base, EdgePriors.neutral(EdgePriors.Semantics.WEIGHTS)); ok("weights-into-score rejected", false); }
        catch (IllegalStateException ex) { ok("weights-into-score rejected", true); }
        try {
            double[][] bad = new double[2][2]; bad[0][1] = bad[1][0] = 1.0;
            new EdgePriorScore(base, EdgePriors.fromMatrix(List.of("A","ZZZ"), bad, EdgePriors.Semantics.LOG_ODDS));
            ok("unknown variable name rejected", false);
        } catch (IllegalArgumentException ex) { ok("unknown variable name rejected", ex.getMessage().contains("ZZZ")); }

        System.out.println("\n6. GRW mean-one normalisation");
        double[][] w = EdgePriors.neutralMatrix(3, EdgePriors.Semantics.WEIGHTS);
        w[0][1] = w[1][0] = 4.0; w[0][2] = w[2][0] = 2.0;
        EdgePriors ws = EdgePriors.fromMatrix(List.of("A","B","C"), w, EdgePriors.Semantics.WEIGHTS).normalizedToMeanOne();
        double mean = (ws.get("A","B") + ws.get("A","C")) / 2.0;
        ok("stored weights average one", Math.abs(mean - 1.0) < 1e-12);
        ok("ratios preserved (4:2 -> 4/3:2/3)", Math.abs(ws.get("A","B") / ws.get("A","C") - 2.0) < 1e-12);
        ok("unset pair still 1", ws.get("B","C") == 1.0);

        System.out.println("\n6b. The zero-default footgun is caught");
        try { EdgePriors.fromMatrix(List.of("A","B","C"), new double[3][3], EdgePriors.Semantics.WEIGHTS);
            ok("raw new double[p][p] rejected for WEIGHTS", false); }
        catch (IllegalArgumentException ex) { ok("raw new double[p][p] rejected for WEIGHTS", ex.getMessage().contains("neutralMatrix")); }
        ok("neutralMatrix(3, WEIGHTS) is all ones", EdgePriors.neutralMatrix(3, EdgePriors.Semantics.WEIGHTS)[0][1] == 1.0);
        ok("neutralMatrix(3, LOG_ODDS) is all zeros", EdgePriors.neutralMatrix(3, EdgePriors.Semantics.LOG_ODDS)[0][1] == 0.0);
        ok("zeros allowed with explicit opt-in", EdgePriors.fromMatrix(List.of("A","B","C"), new double[3][3], EdgePriors.Semantics.WEIGHTS, true).get("A","B") == 0.0);

        System.out.println("\n7. Test: p~ = p/w, independent iff p~ > alpha  (w>1 protects the edge)");
        double[][] wt = EdgePriors.neutralMatrix(3, EdgePriors.Semantics.WEIGHTS);
        wt[0][1] = wt[1][0] = 5.0;                     // (A,B) upweighted
        EdgePriors tw = EdgePriors.fromMatrix(List.of("A","B","C"), wt, EdgePriors.Semantics.WEIGHTS);
        // p = 0.03; alpha = 0.01. Unweighted: 0.03 > 0.01 -> INDEPENDENT -> edge deleted.
        EdgePriorTest t = new EdgePriorTest(new FakeTest(vars, 0.03), tw, 0.01);
        ok("w=5 -> effective alpha 0.05 for (A,B)", Math.abs(t.getAlpha(A,B) - 0.05) < 1e-12);
        ok("unprimed pair keeps alpha 0.01", Math.abs(t.getAlpha(A,C) - 0.01) < 1e-12);
        IndependenceResult rAB = t.checkIndependence(A, B, Set.of());
        IndependenceResult rAC = t.checkIndependence(A, C, Set.of());
        // p~_AB = 0.03/5 = 0.006, NOT > 0.01 -> dependent -> edge KEPT (protected)
        ok("upweighted pair: p~=0.006 -> dependent -> edge kept", !rAB.isIndependent());
        // p~_AC = 0.03/1 = 0.03 > 0.01 -> independent -> edge deleted
        ok("unweighted pair: p~=0.03 -> independent -> edge deleted", rAC.isIndependent());

        System.out.println("\n8. Neutral weights are a no-op on the test");
        EdgePriorTest tn = new EdgePriorTest(new FakeTest(vars, 0.03), EdgePriors.neutral(EdgePriors.Semantics.WEIGHTS), 0.01);
        ok("neutral: p~ = p, independent as unwrapped", tn.checkIndependence(A,B,Set.of()).isIndependent());

        System.out.println(fails == 0 ? "\nALL PASS" : "\n" + fails + " FAILURES");
        if (fails > 0) System.exit(1);
    }
}
