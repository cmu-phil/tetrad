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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Rlcd;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * A watch-the-output study of whether RLCD puts an edge between two children of the same latent (an impure indicator
 * pair X1 --&gt; X2 with L --&gt; X1 and L --&gt; X2 in the output). The question came up because RLCD's output never
 * seemed to contain one; see the project note <code>claude/rlcd-indicator-edges.md</code>.
 * <p>
 * What the code says. In a partition where stage 2 introduces a latent, every observed-to-observed edge is written
 * from a cover: an observed non-sink that is a member of a cover points to the cover's children. A variable that has
 * become a cluster child is a leaf and is never again admitted as a non-sink. And each child cover has one parent
 * cover, so when X1 --&gt; X2 and L --&gt; X2 are both output, L and X1 are co-members of one cover such as {L, X1}.
 * For the plain impure pair (L --&gt; X1..Xn, X1 --&gt; X2, nothing else) the rows of X1 and X2 against the other
 * indicators are proportional whatever the edge strength, so {X1, X2} is a rank-1 set at k = 1, both become leaves
 * in the first round, and the pair comes out as a two-indicator latent, always. The pattern is reachable only when
 * something breaks that rank-1 relation, most simply a second child of X1 inside the same partition, and even then
 * X1 is attached to L only if the controls at that point do not all hang off L.
 * <p>
 * Truths (linear Gaussian, unit error variance, latents dropped from the data). Names starting with L are latent.
 * <ul>
 *     <li>PAIR: L1 --&gt; X1..X5, X1 --&gt; X2; L1 --&gt; L2 --&gt; X6..X9. Expect a two-indicator latent over {X1, X2}.</li>
 *     <li>PAIR_PARENT_CHILD: PAIR plus X1 --&gt; X7. Expect X1 --&gt; X2 and X1 --&gt; X7 with X1 attached to no
 *     latent: once L2 is a child of L1 every remaining control hangs off L1 and the test that would put X1 under L1
 *     is skipped as vacuous.</li>
 *     <li>PAIR_CHILD_CHILD: PAIR plus X2 --&gt; X7. The mirror image: X2 --&gt; X1 and X2 --&gt; X7, X2 unattached. The
 *     data do not orient the pair.</li>
 *     <li>SECOND_CHILD: L1 --&gt; X1..X4, L2 --&gt; X5..X8, L3 --&gt; X9..X11, all three latents connected, X7 --&gt; X8
 *     and X7 --&gt; X10. Expect the pattern, correctly: L --&gt; X5..X8, X7 --&gt; X8, X7 --&gt; X10 (random seed 204
 *     of the Nov 12 scan).</li>
 *     <li>SHARED_CAUSE: X1 --&gt; X2, X3, X4, X8, X9, X10; L1 --&gt; X2, X3, X4; L1 --&gt; L2 --&gt; X8, X9, X10. The
 *     covers {L1, X1} and {L2, X1} share X1 and are chained by the finish step. causal-learn writes that chain as
 *     directed edges and so fabricates L --&gt; X1; Tetrad writes it undirected (X1 --- L1, X1 --- L2).</li>
 * </ul>
 * In random mode the truths are drawn as in the Python scan of Nov 12: one to three latents with three to five
 * indicators each, latent edges with probability 0.6, one to three within-cluster indicator edges, sometimes a
 * cross-cluster indicator edge, a cross-loading, or an observed exogenous cause of several indicators.
 * <p>
 * For each run the output edges are printed, then the hits: STRICT means L --&gt; Xa, L --&gt; Xb and Xa adjacent to
 * Xb; LOOSE means L adjacent to Xa and Xb with Xa adjacent to Xb but not both arrows into the X's (the undirected
 * finish-step chain shows up here). Each within-cluster impure pair of the truth is also classified: edge (the pair is
 * adjacent and both are children of a common latent), 2-latent (some latent has exactly the pair as observed
 * children), unattached (the pair is adjacent but the parent end has no latent parent), or other.
 * <p>
 * Arguments (all optional, in any order): <code>models</code> (default) or <code>random</code>; <code>reps=</code>
 * (default 1 for models, 50 for random); <code>n=</code> (default 20000 for models, 5000 for random);
 * <code>seed=</code> (default 20261112); <code>resolve</code> to turn on rlcdResolveTwoIndicatorLatents;
 * <code>dump=DIR</code> to write each data set as CSV with its truth beside it, for a cross-check against
 * causal-learn's RLCD on the same data. Each run takes a few seconds at most.
 *
 * @author josephramsey
 */
public final class RlcdIndicatorEdgeStudy {

    private static final String[] MODELS = {"PAIR", "PAIR_PARENT_CHILD", "PAIR_CHILD_CHILD", "SECOND_CHILD",
            "SHARED_CAUSE"};

    private RlcdIndicatorEdgeStudy() {
    }

    /**
     * Runs the study.
     *
     * @param args see the class Javadoc.
     * @throws Exception on I/O or interruption.
     */
    public static void main(String[] args) throws Exception {
        boolean randomMode = false, resolve = false;
        int reps = -1, n = -1;
        long seed = 20261112L;
        Path dump = null;
        for (String a : args) {
            if (a.equals("random")) randomMode = true;
            else if (a.equals("models")) randomMode = false;
            else if (a.equals("resolve")) resolve = true;
            else if (a.startsWith("reps=")) reps = Integer.parseInt(a.substring(5));
            else if (a.startsWith("n=")) n = Integer.parseInt(a.substring(2));
            else if (a.startsWith("seed=")) seed = Long.parseLong(a.substring(5));
            else if (a.startsWith("dump=")) dump = Paths.get(a.substring(5));
            else throw new IllegalArgumentException("Unknown argument: " + a);
        }
        if (reps < 0) reps = randomMode ? 50 : 1;
        if (n < 0) n = randomMode ? 5000 : 20000;
        if (dump != null) Files.createDirectories(dump);
        Random random = new Random(seed);

        System.out.println("RLCD indicator-edge study: " + (randomMode ? "random truths" : "named truths")
                           + ", reps " + reps + ", N " + n + ", resolveTwoIndicatorLatents " + resolve);
        System.out.println("STRICT = L->Xa, L->Xb, Xa adjacent Xb.  LOOSE = L adjacent to both, not both arrows into the X's.");

        int runs = 0, strictRuns = 0, looseRuns = 0;
        Map<String, Integer> pairOutcomes = new LinkedHashMap<>();
        for (String k : new String[]{"edge", "2-latent", "unattached", "other"}) pairOutcomes.put(k, 0);

        List<String> names = randomMode ? Collections.nCopies(reps, "random") : List.of(MODELS);
        int index = 0;
        for (String name : names) {
            for (int r = 0; r < (randomMode ? 1 : reps); r++, index++) {
                Truth truth = randomMode ? randomTruth(random) : namedTruth(name);
                DataSet data = simulate(truth, n, random);
                if (dump != null) write(dump, index, truth, data);

                Rlcd rlcd = new Rlcd(data);
                rlcd.setResolveTwoIndicatorLatents(resolve);
                rlcd.setSeed(Math.abs(random.nextLong()) % 1_000_000L);
                Graph g;
                long t0 = System.currentTimeMillis();
                try {
                    g = rlcd.search();
                } catch (Exception e) {
                    System.out.println("--- run " + index + " (" + name + "): ERROR " + e);
                    continue;
                }
                runs++;
                double secs = (System.currentTimeMillis() - t0) / 1000.0;

                List<String> strict = strictHits(g), loose = looseHits(g);
                if (!strict.isEmpty()) strictRuns++;
                if (!loose.isEmpty()) looseRuns++;

                System.out.println();
                System.out.printf("--- run %d (%s), %.1fs%n", index, name, secs);
                System.out.println("TRUTH: " + truth);
                System.out.println("OUT:   " + edges(g));
                System.out.println("STRICT: " + strict + "   LOOSE: " + loose);
                for (String[] pair : truth.impurePairs()) {
                    String outcome = classify(g, pair[0], pair[1]);
                    pairOutcomes.merge(outcome, 1, Integer::sum);
                    System.out.println("   pair " + pair[0] + "->" + pair[1] + " (both under " + pair[2] + "): " + outcome);
                }
            }
        }

        System.out.println();
        System.out.println("Runs " + runs + "; with a STRICT hit " + strictRuns + "; with a LOOSE hit " + looseRuns);
        System.out.println("Within-cluster impure pairs in the truths, by outcome: " + pairOutcomes);
    }

    // ---------------------------------------------------------------- scoring

    private static List<String> strictHits(Graph g) {
        List<String> hits = new ArrayList<>();
        for (Node l : g.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            List<Node> ch = new ArrayList<>();
            for (Node c : g.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) ch.add(c);
            for (int i = 0; i < ch.size(); i++) {
                for (int j = i + 1; j < ch.size(); j++) {
                    if (g.isAdjacentTo(ch.get(i), ch.get(j))) {
                        hits.add(l + ": " + compact(g.getEdge(ch.get(i), ch.get(j))));
                    }
                }
            }
        }
        return hits;
    }

    private static List<String> looseHits(Graph g) {
        List<String> hits = new ArrayList<>();
        for (Node l : g.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            List<Node> adj = new ArrayList<>();
            for (Node c : g.getAdjacentNodes(l)) if (c.getNodeType() != NodeType.LATENT) adj.add(c);
            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node a = adj.get(i), b = adj.get(j);
                    if (!g.isAdjacentTo(a, b)) continue;
                    if (g.isChildOf(a, l) && g.isChildOf(b, l)) continue;   // a STRICT hit
                    hits.add(compact(g.getEdge(l, a)) + ", " + compact(g.getEdge(l, b)) + ", " + compact(g.getEdge(a, b)));
                }
            }
        }
        return hits;
    }

    /**
     * How the output represents a truth pair a --&gt; b whose members share a latent parent in the truth.
     */
    private static String classify(Graph g, String a, String b) {
        Node x = g.getNode(a), y = g.getNode(b);
        if (g.isAdjacentTo(x, y)) {
            for (Node l : g.getNodes()) {
                if (l.getNodeType() == NodeType.LATENT && g.isChildOf(x, l) && g.isChildOf(y, l)) return "edge";
            }
            Node parent = g.isParentOf(y, x) ? y : x;
            boolean parentHasLatentParent = false;
            for (Node p : g.getParents(parent)) if (p.getNodeType() == NodeType.LATENT) parentHasLatentParent = true;
            return parentHasLatentParent ? "other" : "unattached";
        }
        for (Node l : g.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            TreeSet<String> obs = new TreeSet<>();
            for (Node c : g.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) obs.add(c.getName());
            if (obs.equals(new TreeSet<>(List.of(a, b)))) return "2-latent";
        }
        return "other";
    }

    private static String edges(Graph g) {
        List<String> out = new ArrayList<>();
        for (Edge e : g.getEdges()) out.add(compact(e));
        Collections.sort(out);
        return String.join("; ", out);
    }

    private static String compact(Edge e) {
        return e.toString().replace(" ", "");
    }

    // ---------------------------------------------------------------- truths

    /**
     * A linear structure: node names (latent if starting with L) and weighted edges.
     */
    private record Truth(List<String> nodes, List<String[]> edges, List<Double> coefs) {
        void add(String p, String c, double w) {
            edges.add(new String[]{p, c});
            coefs.add(w);
        }

        List<String> observed() {
            List<String> out = new ArrayList<>();
            for (String v : nodes) if (!v.startsWith("L")) out.add(v);
            return out;
        }

        /**
         * Observed pairs a --&gt; b whose members share a latent parent; the third entry is that latent.
         */
        List<String[]> impurePairs() {
            List<String[]> out = new ArrayList<>();
            for (String[] e : edges) {
                if (e[0].startsWith("L") || e[1].startsWith("L")) continue;
                for (String l : nodes) {
                    if (!l.startsWith("L")) continue;
                    if (has(l, e[0]) && has(l, e[1])) {
                        out.add(new String[]{e[0], e[1], l});
                        break;
                    }
                }
            }
            return out;
        }

        boolean has(String p, String c) {
            for (String[] e : edges) if (e[0].equals(p) && e[1].equals(c)) return true;
            return false;
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < edges.size(); i++) {
                parts.add(edges.get(i)[0] + "->" + edges.get(i)[1] + String.format("(%.2f)", coefs.get(i)));
            }
            return String.join("; ", parts);
        }
    }

    private static Truth truth(String... nodes) {
        return new Truth(new ArrayList<>(List.of(nodes)), new ArrayList<>(), new ArrayList<>());
    }

    private static Truth namedTruth(String name) {
        switch (name) {
            case "PAIR", "PAIR_PARENT_CHILD", "PAIR_CHILD_CHILD" -> {
                Truth t = truth("L1", "L2", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8", "X9");
                t.add("L1", "X1", 1.0);
                t.add("L1", "X2", 0.8);
                t.add("L1", "X3", 1.0);
                t.add("L1", "X4", 1.1);
                t.add("L1", "X5", 0.9);
                t.add("X1", "X2", 0.9);
                t.add("L1", "L2", 0.7);
                t.add("L2", "X6", 1.0);
                t.add("L2", "X7", 0.9);
                t.add("L2", "X8", 1.1);
                t.add("L2", "X9", 1.0);
                if (name.equals("PAIR_PARENT_CHILD")) t.add("X1", "X7", 0.8);
                if (name.equals("PAIR_CHILD_CHILD")) t.add("X2", "X7", 0.8);
                return t;
            }
            case "SECOND_CHILD" -> {
                Truth t = truth("L1", "L2", "L3", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8", "X9", "X10", "X11");
                double[] w = {-1.25, 0.78, 0.61, -0.65, -1.38, 1.21, -0.64, -0.69, 0.76, -1.03, -0.74};
                for (int i = 0; i < 11; i++) t.add(i < 4 ? "L1" : i < 8 ? "L2" : "L3", "X" + (i + 1), w[i]);
                t.add("L1", "L2", 0.64);
                t.add("L1", "L3", -0.74);
                t.add("L2", "L3", 0.81);
                t.add("X7", "X8", -1.02);
                t.add("X7", "X10", -1.0);
                return t;
            }
            case "SHARED_CAUSE" -> {
                Truth t = truth("L1", "L2", "X1", "X2", "X3", "X4", "X8", "X9", "X10");
                t.add("X1", "X2", 0.7);
                t.add("X1", "X3", 0.8);
                t.add("X1", "X4", 0.6);
                t.add("X1", "X8", 0.7);
                t.add("X1", "X9", 0.9);
                t.add("X1", "X10", 0.6);
                t.add("L1", "X2", 1.0);
                t.add("L1", "X3", 0.9);
                t.add("L1", "X4", 1.1);
                t.add("L1", "L2", 0.8);
                t.add("L2", "X8", 1.0);
                t.add("L2", "X9", 1.1);
                t.add("L2", "X10", 0.9);
                return t;
            }
            default -> throw new IllegalArgumentException(name);
        }
    }

    private static Truth randomTruth(Random random) {
        int nl = 1 + random.nextInt(3);
        List<String> nodes = new ArrayList<>();
        for (int i = 1; i <= nl; i++) nodes.add("L" + i);
        Truth t = new Truth(nodes, new ArrayList<>(), new ArrayList<>());
        Map<String, List<String>> inds = new LinkedHashMap<>();
        int x = 1;
        for (int i = 1; i <= nl; i++) {
            String l = "L" + i;
            inds.put(l, new ArrayList<>());
            int k = 3 + random.nextInt(3);
            for (int j = 0; j < k; j++) {
                String v = "X" + x++;
                nodes.add(v);
                inds.get(l).add(v);
                t.add(l, v, uniform(random, 0.6, 1.4) * sign(random));
            }
        }
        for (int i = 1; i <= nl; i++) {
            for (int j = i + 1; j <= nl; j++) {
                if (random.nextDouble() < 0.6) t.add("L" + i, "L" + j, uniform(random, 0.4, 0.9) * sign(random));
            }
        }
        int impurities = 1 + random.nextInt(3);
        for (int m = 0; m < impurities; m++) {
            String l = "L" + (1 + random.nextInt(nl));
            List<String> pool = new ArrayList<>(inds.get(l));
            Collections.shuffle(pool, random);
            String a = pool.get(0), b = pool.get(1);
            if (number(a) > number(b)) {
                String tmp = a;
                a = b;
                b = tmp;
            }
            if (!t.has(a, b)) t.add(a, b, uniform(random, 0.5, 1.2) * sign(random));
        }
        if (nl > 1 && random.nextDouble() < 0.5) {
            List<String> ls = new ArrayList<>(inds.keySet());
            Collections.shuffle(ls, random);
            String a = pick(inds.get(ls.get(0)), random), b = pick(inds.get(ls.get(1)), random);
            if (number(a) > number(b)) {
                String tmp = a;
                a = b;
                b = tmp;
            }
            t.add(a, b, uniform(random, 0.5, 1.0) * sign(random));
        }
        if (nl > 1 && random.nextDouble() < 0.4) {
            List<String> ls = new ArrayList<>(inds.keySet());
            Collections.shuffle(ls, random);
            t.add(ls.get(0), pick(inds.get(ls.get(1)), random), uniform(random, 0.5, 1.0));
        }
        if (random.nextDouble() < 0.4) {
            String v = "X" + x;
            nodes.add(v);
            for (List<String> group : inds.values()) {
                for (String ind : group) {
                    if (random.nextDouble() < 0.5) t.add(v, ind, uniform(random, 0.4, 0.9));
                }
            }
        }
        return t;
    }

    private static int number(String x) {
        return Integer.parseInt(x.substring(1));
    }

    private static String pick(List<String> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    private static double uniform(Random random, double lo, double hi) {
        return lo + (hi - lo) * random.nextDouble();
    }

    private static double sign(Random random) {
        return random.nextBoolean() ? 1 : -1;
    }

    // ---------------------------------------------------------------- simulation

    /**
     * Simulates the structure in topological order with unit-variance Gaussian errors and returns the observed
     * variables, in the order the truth lists them.
     */
    private static DataSet simulate(Truth truth, int n, Random random) {
        Map<String, double[]> values = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>();
        for (String v : truth.nodes()) visit(v, truth, order, seen);
        for (String v : order) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = random.nextGaussian();
            for (int e = 0; e < truth.edges().size(); e++) {
                String[] edge = truth.edges().get(e);
                if (!edge[1].equals(v)) continue;
                double[] parent = values.get(edge[0]);
                double w = truth.coefs().get(e);
                for (int i = 0; i < n; i++) col[i] += w * parent[i];
            }
            values.put(v, col);
        }
        List<String> observed = truth.observed();
        List<Node> vars = new ArrayList<>();
        for (String v : observed) vars.add(new ContinuousVariable(v));
        DoubleDataBox box = new DoubleDataBox(n, observed.size());
        for (int j = 0; j < observed.size(); j++) {
            double[] col = values.get(observed.get(j));
            for (int i = 0; i < n; i++) box.set(i, j, col[i]);
        }
        return new BoxDataSet(box, vars);
    }

    private static void visit(String v, Truth truth, List<String> order, TreeSet<String> seen) {
        if (!seen.add(v)) return;
        for (String[] e : truth.edges()) if (e[1].equals(v)) visit(e[0], truth, order, seen);
        order.add(v);
    }

    private static void write(Path dir, int index, Truth truth, DataSet data) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve("data_" + index + ".csv")))) {
            List<String> header = new ArrayList<>();
            for (Node v : data.getVariables()) header.add(v.getName());
            out.println(String.join(",", header));
            for (int i = 0; i < data.getNumRows(); i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < data.getNumColumns(); j++) {
                    if (j > 0) sb.append(',');
                    sb.append(String.format("%.6f", data.getDouble(i, j)));
                }
                out.println(sb);
            }
        }
        Files.writeString(dir.resolve("truth_" + index + ".txt"), truth + System.lineSeparator());
    }
}
