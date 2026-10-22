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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Rlcd;

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
 *     spurious latent counts against precision).</li>
 *     <li><b>OO P/R</b>: precision and recall of observed-observed adjacencies. <b>Arr P/R</b>: the same for
 *     directed observed-observed edges, counting only those pointing the true way.</li>
 *     <li><b>imp</b>: share of the true within-cluster edges found as adjacencies; <b>impDir</b>: as directed edges
 *     pointing the true way. Blank for structures without them.</li>
 *     <li><b>rev</b>: ordered pairs of observed variables per run with X an ancestor of Y in the output but Y an
 *     ancestor of X in the truth (a reversed ancestral relation, the thing tiers rule out).</li>
 * </ul>
 * Edges between observed and latent variables are not scored separately; their effect shows in clus and spur.
 * <p>
 * <b>Arguments</b>, all optional, as key=value: reps=10, ns=2000, structures=mixed30,mixed30i5,rand6,
 * dists=skew,mildskew,gauss, conditions=plain,resolve,tiers,both, seed=20261022. For example,
 * {@code reps=30 ns=2000,5000 structures=mixed30i5 dists=skew,ceil}. A dot is printed per replication.
 * <p>
 * Timing, from a smoke run at N = 5000, per RLCD run: mixed30 about 2 s, mixed30i5 about 3 s, rand6 about 7 s
 * (four runs per replication, one per condition). The default grid takes roughly a quarter of an hour. The ceil
 * data are much slower, 25 to 60 s per run, because clipped variables give stage 2 many spurious rank
 * deficiencies to enumerate, so ceil is not in the default list. At reps=10 the standard error of a share is up
 * to 0.16, so only large differences count.
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
    public static void main(String[] args) throws InterruptedException {
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
        Random random = new Random(seed);

        System.out.println("RLCD accuracy on linear non-Gaussian data with latents. reps " + reps
                           + "; RLCD defaults otherwise (alpha 0.01, max k 3, BOSS stage 1, latent gate on).");

        for (String structure : structures) {
            for (String dist : dists) {
                System.out.println();
                System.out.println("=== " + structure + ", errors " + dist + " ===");
                System.out.printf("%6s %-8s %7s %5s %5s %5s  %5s %5s  %5s %5s  %5s %5s  %5s %6s  %5s %6s%n",
                        "N", "cond", "lat", "clus", "jac", "spur", "LL P", "LL R", "OO P", "OO R", "Arr P", "Arr R",
                        "imp", "impDir", "rev", "secs");
                for (int n : ns) {
                    Map<String, Totals> totals = new LinkedHashMap<>();
                    for (String c : conditions) totals.put(c, new Totals());
                    int trueLatents = 0;
                    System.out.print("  N=" + n + " ");
                    for (int r = 0; r < reps; r++) {
                        System.out.print(".");
                        System.out.flush();
                        Truth truth = build(structure, random);
                        trueLatents = truth.latents.size();
                        DataSet data = truth.simulate(dist, n, random);
                        long runSeed = Math.floorMod(random.nextLong(), 1_000_000L);
                        for (String c : conditions) {
                            long t0 = System.nanoTime();
                            Graph out = run(data, truth, c, runSeed);
                            totals.get(c).add(score(truth, out), (System.nanoTime() - t0) / 1e9);
                        }
                    }
                    System.out.println();
                    for (String c : conditions) totals.get(c).print(n, c, trueLatents);
                }
            }
        }
        System.out.println();
        System.out.println("lat: latents found (true count in parentheses). clus: true latents recovered with "
                           + "exactly their indicators. jac: mean best overlap (Jaccard) of a true latent's "
                           + "indicators with an estimated latent's. spur: unmatched latents per run.");
        System.out.println("LL, OO, Arr: precision and recall of latent-latent adjacencies, observed-observed "
                           + "adjacencies, and observed-observed arrows.");
        System.out.println("imp, impDir: within-cluster direct edges found, and found pointing the right way. "
                           + "rev: reversed ancestral relations among observed pairs per run.");
    }

    private static Graph run(DataSet data, Truth truth, String condition, long seed) throws InterruptedException {
        Rlcd rlcd = new Rlcd(data);
        rlcd.setSeed(seed);
        rlcd.setResolveTwoIndicatorLatents(condition.equals("resolve") || condition.equals("both"));
        if (condition.equals("tiers") || condition.equals("both")) rlcd.setKnowledge(truth.depthTiers());
        return rlcd.search();
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
                    double c = (0.5 + random.nextDouble()) * (random.nextBoolean() ? 1 : -1);
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
            case "mixed30" -> mixed30(false);
            case "mixed30i5" -> mixed30(true);
            case "rand6" -> rand6(random);
            default -> throw new IllegalArgumentException("Unknown structure: " + structure);
        };
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
                         int ooTp, int ooEst, int ooTrue, int arrTp, int arrEst, int impAdj, int impDir,
                         int impTrue, int reversed) {
    }

    private static Score score(Truth truth, Graph out) {
        Graph tg = truth.graph();

        // Observed children of each latent, true and estimated.
        Map<String, Set<String>> trueKids = new HashMap<>();
        for (String l : truth.latents) trueKids.put(l, observedChildren(tg, tg.getNode(l)));
        Map<Node, Set<String>> estKids = new LinkedHashMap<>();
        for (Node v : out.getNodes()) if (v.getNodeType() == NodeType.LATENT) estKids.put(v, observedChildren(out, v));

        Map<Node, String> match = new HashMap<>();
        int spurious = 0;
        for (Map.Entry<Node, Set<String>> e : estKids.entrySet()) {
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
            llEst++;
            String ma = match.get(a), mb = match.get(b);
            if (ma != null && mb != null && !ma.equals(mb) && trueLL.contains(Set.of(ma, mb))) {
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
        int ooEst = 0, ooTp = 0, arrEst = 0, arrTp = 0;
        Set<Set<String>> estOO = new HashSet<>();
        Set<List<String>> estArr = new HashSet<>();
        for (Edge e : out.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            if (!obs.contains(a) || !obs.contains(b)) continue;
            ooEst++;
            estOO.add(Set.of(a, b));
            if (trueOO.contains(Set.of(a, b))) ooTp++;
            if (Edges.isDirectedEdge(e)) {
                arrEst++;
                List<String> arr = List.of(Edges.getDirectedEdgeTail(e).getName(),
                        Edges.getDirectedEdgeHead(e).getName());
                estArr.add(arr);
                if (trueArr.contains(arr)) arrTp++;
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
                arrTp, arrEst, impAdj, impDir, truth.impurities.size(), reversed);
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
        long llTp, llEst, llTrue, ooTp, ooEst, ooTrue, arrTp, arrEst, impAdj, impDir, impTrue, reversed;
        int trueLatentsSeen;

        void add(Score s, double seconds) {
            runs++;
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
            arrTp += s.arrTp();
            arrEst += s.arrEst();
            impAdj += s.impAdj();
            impDir += s.impDir();
            impTrue += s.impTrue();
            reversed += s.reversed();
            secs += seconds;
        }

        void print(int n, String condition, int trueLatents) {
            System.out.printf("%6d %-8s %7s %5.2f %5.2f %5.2f  %5s %5s  %5s %5s  %5s %5s  %5s %6s  %5.1f %6.2f%n",
                    n, condition,
                    String.format("%.1f(%d)", latents / runs, trueLatents),
                    exact / (runs * (double) trueLatents),
                    jaccard / (runs * (double) trueLatents),
                    spurious / runs,
                    ratio(llTp, llEst), ratio(llTp, llTrue),
                    ratio(ooTp, ooEst), ratio(ooTp, ooTrue),
                    ratio(arrTp, arrEst), ratio(arrTp, ooTrue),
                    impTrue == 0 ? "" : ratio(impAdj, impTrue), impTrue == 0 ? "" : ratio(impDir, impTrue),
                    reversed / (double) runs, secs / runs);
        }

        private static String ratio(long num, long den) {
            return den == 0 ? "  -  " : String.format("%.2f", num / (double) den);
        }
    }
}
