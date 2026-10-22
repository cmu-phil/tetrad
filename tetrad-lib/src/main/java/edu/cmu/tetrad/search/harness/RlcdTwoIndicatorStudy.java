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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Rlcd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A watch-the-output study of {@link Rlcd#setResolveTwoIndicatorLatents(boolean)}: RLCD is run with the step off and
 * on, on the same data, and the output is scored for how it represents the pair X1, X2.
 * <p>
 * Truths (linear; latents dropped from the data):
 * <ul>
 *     <li>EDGE: L1 --&gt; X1..X5, X1 --&gt; X2; L2 --&gt; X6..X9; L1 --&gt; L2. RLCD should split off a latent over
 *     {X1, X2}; the step should turn it into X1 --&gt; X2.</li>
 *     <li>LATENT: L1 --&gt; Lp --&gt; {X1, X2}; L1 --&gt; X3..X5; L2 --&gt; X6..X9; L1 --&gt; L2. The latent over
 *     {X1, X2} is real; the step should leave it.</li>
 *     <li>PURE: L1 --&gt; X1..X5; L2 --&gt; X6..X9; L1 --&gt; L2. Nothing to find.</li>
 * </ul>
 * Errors: strongly skewed (centered exponential, random sign), mildly skewed (Gamma with shape 4, skewness 1), and
 * Gaussian (a control: the step should change nothing). Coefficients are uniform in 0.5 to 1.5 with random sign;
 * error variances in 0.5 to 1.
 * <p>
 * Scores for the pair, as shares of runs: X1 --&gt; X2, X2 --&gt; X1, another edge between them, "2-latent" (some
 * latent whose observed children are exactly X1 and X2), and "other" (anything else, e.g. both under one latent with
 * other indicators). The right answers are X1 --&gt; X2 for EDGE, 2-latent for LATENT, and other for PURE. The last
 * column counts how often the step changed the pair at all.
 * <p>
 * Arguments (all optional): reps (default 50), then sample sizes (default 1000 2000 5000). Each RLCD run on ten
 * variables takes well under a second; the bootstrap in the step adds a little where it applies.
 *
 * @author josephramsey
 */
public final class RlcdTwoIndicatorStudy {

    private static final String[] TRUTHS = {"EDGE", "LATENT", "PURE"};
    private static final String[] DISTS = {"skew", "mildskew", "gauss"};
    private static final String[] LABELS = {"X1->X2", "X2->X1", "edge?", "2-latent", "other"};

    private RlcdTwoIndicatorStudy() {
    }

    /**
     * Runs the study.
     *
     * @param args reps, then sample sizes.
     * @throws InterruptedException if interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        int reps = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int[] ns = {1000, 2000, 5000};
        if (args.length > 1) {
            ns = new int[args.length - 1];
            for (int i = 1; i < args.length; i++) ns[i - 1] = Integer.parseInt(args[i]);
        }
        Random random = new Random(20261022L);

        System.out.println("RLCD two-indicator resolution study: reps " + reps + "; off = plain RLCD, on = with "
                           + "rlcdResolveTwoIndicatorLatents. Shares of runs; * marks the right answer.");

        for (String dist : DISTS) {
            System.out.println();
            System.out.println("=== errors: " + dist + " ===");
            System.out.printf("%-7s %6s %4s  %8s %8s %8s %8s %8s   %7s%n", "truth", "N", "step",
                    LABELS[0], LABELS[1], LABELS[2], LABELS[3], LABELS[4], "changed");
            for (String truth : TRUTHS) {
                for (int n : ns) {
                    int[] off = new int[LABELS.length], on = new int[LABELS.length];
                    int changed = 0;
                    long t0 = System.currentTimeMillis();
                    for (int r = 0; r < reps; r++) {
                        DataSet data = simulate(truth, dist, n, random);
                        long seed = random.nextLong();
                        String a = score(run(data, false, seed));
                        String b = score(run(data, true, seed));
                        off[indexOf(a)]++;
                        on[indexOf(b)]++;
                        if (!a.equals(b)) changed++;
                    }
                    double secs = (System.currentTimeMillis() - t0) / 1000.0;
                    print(truth, n, "off", off, reps, -1, -1);
                    print(truth, n, "on", on, reps, changed, secs);
                }
            }
        }
        System.out.println();
        System.out.println("Right answers: EDGE -> X1->X2, LATENT -> 2-latent, PURE -> other. Under gauss the step "
                           + "should change nothing.");
    }

    private static Graph run(DataSet data, boolean resolve, long seed) throws InterruptedException {
        Rlcd rlcd = new Rlcd(data);
        rlcd.setSeed(Math.abs(seed) % 1_000_000L);
        rlcd.setResolveTwoIndicatorLatents(resolve);
        return rlcd.search();
    }

    private static String right(String truth) {
        return switch (truth) {
            case "EDGE" -> "X1->X2";
            case "LATENT" -> "2-latent";
            default -> "other";
        };
    }

    private static void print(String truth, int n, String step, int[] counts, int reps, int changed, double secs) {
        StringBuilder sb = new StringBuilder(String.format("%-7s %6d %4s ", truth, n, step));
        for (int i = 0; i < LABELS.length; i++) {
            String mark = LABELS[i].equals(right(truth)) ? "*" : " ";
            sb.append(String.format(" %7.2f%s", counts[i] / (double) reps, mark));
        }
        if (changed >= 0) sb.append(String.format("   %7.2f   (%.1fs)", changed / (double) reps, secs));
        System.out.println(sb);
    }

    private static int indexOf(String label) {
        for (int i = 0; i < LABELS.length; i++) if (LABELS[i].equals(label)) return i;
        throw new IllegalArgumentException(label);
    }

    /**
     * How the output represents the pair X1, X2.
     */
    private static String score(Graph g) {
        Node x1 = g.getNode("X1"), x2 = g.getNode("X2");
        if (g.isAdjacentTo(x1, x2)) {
            if (g.isParentOf(x1, x2)) return "X1->X2";
            if (g.isParentOf(x2, x1)) return "X2->X1";
            return "edge?";
        }
        for (Node l : g.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            Set<String> obs = new HashSet<>();
            for (Node c : g.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) obs.add(c.getName());
            if (obs.equals(Set.of("X1", "X2"))) return "2-latent";
        }
        return "other";
    }

    // ---------------------------------------------------------------- simulation

    private static DataSet simulate(String truth, String dist, int n, Random random) {
        double[] l1 = error(dist, n, random), l2 = new double[n], lp = new double[n];
        double c12 = coef(random);
        double[] e = error(dist, n, random);
        for (int i = 0; i < n; i++) l2[i] = c12 * l1[i] + e[i];
        if (truth.equals("LATENT")) {
            double c = coef(random);
            double[] u = error(dist, n, random);
            for (int i = 0; i < n; i++) lp[i] = c * l1[i] + u[i];
        }

        double[][] x = new double[9][];
        for (int j = 0; j < 9; j++) {
            double[] parent = j < 5 ? (truth.equals("LATENT") && j < 2 ? lp : l1) : l2;
            double c = coef(random);
            double[] err = error(dist, n, random);
            x[j] = new double[n];
            for (int i = 0; i < n; i++) x[j][i] = c * parent[i] + err[i];
        }
        if (truth.equals("EDGE")) {
            double c = coef(random);
            for (int i = 0; i < n; i++) x[1][i] += c * x[0][i];
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 9; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        DoubleDataBox box = new DoubleDataBox(n, 9);
        for (int i = 0; i < n; i++) for (int j = 0; j < 9; j++) box.set(i, j, x[j][i]);
        return new BoxDataSet(box, vars);
    }

    private static double coef(Random random) {
        return (0.5 + random.nextDouble()) * (random.nextBoolean() ? 1 : -1);
    }

    /**
     * Independent errors with variance in 0.5 to 1.
     */
    private static double[] error(String dist, int n, Random random) {
        double sd = Math.sqrt(0.5 + 0.5 * random.nextDouble());
        double sign = random.nextBoolean() ? 1 : -1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double z = switch (dist) {
                case "skew" -> sign * (-Math.log(1 - random.nextDouble()) - 1.0);
                case "mildskew" -> sign * (gamma4(random) - 4.0) / 2.0;
                default -> random.nextGaussian();
            };
            out[i] = sd * z;
        }
        return out;
    }

    /**
     * Gamma with shape 4 and scale 1, as a sum of four unit exponentials.
     */
    private static double gamma4(Random random) {
        double s = 0;
        for (int k = 0; k < 4; k++) s += -Math.log(1 - random.nextDouble());
        return s;
    }
}
