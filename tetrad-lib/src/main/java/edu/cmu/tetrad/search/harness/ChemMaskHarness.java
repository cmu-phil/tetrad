package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.algcomparison.statistic.StructuralHammingDistance;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stage 5 of the missing-data experiments: simulated data shaped like a real dataset, with the real dataset's own
 * pattern of missing cells.
 *
 * <p>The harness reads a real data file only for its shape, never fitting anything to it: the variable names, the
 * row count, which variables are discrete and their category proportions, which cells are missing, and, optionally,
 * how much of each continuous variable sits at its maximum (the pile-up at 100 in CHEM). It then simulates a linear
 * model on variables of the same names and types, respecting an optional tier order, deletes cells using the real
 * missingness pattern, and compares DG-BIC searches under each way of handling missing values against the
 * complete-data search.</p>
 *
 * <h2>How the real pattern is applied</h2>
 *
 * <ul>
 *   <li><b>permuted</b> (default): the real rows' missingness patterns are assigned to simulated rows in random
 *   order. The pattern is exactly CHEM's -- which cells go missing together -- but missingness is independent of the
 *   simulated values, so MCAR by construction. This isolates the effect of the pattern's shape.</li>
 *   <li><b>matched</b>: the real rows are ranked by the target variable's real value and assigned to simulated rows
 *   ranked by the simulated target, so a student near the bottom of the real final-grade distribution lends their
 *   pattern to a simulated student near the bottom. Whatever association the real data have between the final grade
 *   and missingness is carried over; the mechanism is MAR driven by the target if the target is fully observed.
 *   Rows whose real target is missing are assigned at random.</li>
 * </ul>
 *
 * <h2>Ceiling</h2>
 *
 * <p>With {@code ceiling=true}, each continuous variable other than the target has the same share of its simulated
 * values set to its simulated quantile as the real variable has at its maximum, reproducing the pile-up. The oracle
 * sees the ceilinged complete data, so the comparison isolates missingness; run once with and once without to see
 * what the ceiling itself costs.</p>
 *
 * <h2>Simulation</h2>
 *
 * <p>Variables are ordered by tier (tiers file, as for the Python monotonicity script: one tier per line,
 * comma-separated names; lines starting with '#' are ignored), randomly within a tier; with no tiers file, by column
 * order. A random DAG with the requested average degree is drawn consistent with that order. Coefficients are
 * positive, uniform in [coefLo, coefHi]; errors are Gaussian. A discrete variable is the thresholded value of its
 * latent linear predictor, with thresholds that reproduce the real category proportions, and its children see the
 * category index, as they would in the data.</p>
 *
 * <h2>Settings</h2>
 *
 * <p>{@code key=value} arguments: {@code data} (required: path to the real file; tab-, comma- or space-delimited,
 * with a header; '*', blank, NA and NaN read as missing; a column with any non-numeric entry is read as
 * categorical), {@code tiers} (optional), {@code target} (default
 * "final" if present, otherwise the last variable in the order), {@code match} (permuted or matched), {@code ceiling}
 * (true or false), {@code reps}, {@code avgDegree}, {@code coefLo}, {@code coefHi}, {@code penalty},
 * {@code maxCategories} (a column with at most this many distinct values is treated as discrete; default 4),
 * {@code targetParents} (the target's number of parents, drawn from the variables before it; default 3, since a
 * random draw at CHEM's density often leaves the last variable with none).</p>
 *
 * <pre>
 *     Program arguments:
 *     data=/Users/josephramsey/IdeaProjects/BryanStuff/educational/example1/data/Joined1_24.txt tiers=/Users/josephramsey/IdeaProjects/BryanStuff/educational/example1/data/tiers.csv
 * </pre>
 *
 * @author josephramsey
 */
public final class ChemMaskHarness {

    // ---------------------------------------------------------------- settings

    private static String dataPath = null, tiersPath = null, targetName = null, match = "permuted";
    private static boolean ceiling = false;
    private static int reps = 10, maxCategories = 4, targetParents = 3;
    private static double avgDegree = 3.0, coefLo = 0.2, coefHi = 0.6, penaltyDiscount = 2.0;

    /** Seconds any single search may take before it is abandoned; 0 disables the limit. */
    private static final int ARM_TIMEOUT_SECONDS = 180;

    /** Per-repetition results, written to the working directory. */
    private static final String CSV_FILE = "chem_mask.csv";

    private ChemMaskHarness() {
    }

    // ---------------------------------------------------------------- arms

    private record Arm(String name, String policy, String essMode, boolean oracle) {
    }

    private static List<Arm> arms() {
        List<Arm> arms = new ArrayList<>();
        arms.add(new Arm("DG ORACLE", null, null, true));
        arms.add(new Arm("DG LISTWISE", "listwise", "fullN", false));
        for (String policy : new String[]{"testwise", "em", "pairwise"}) {
            for (String ess : new String[]{"fullN", "minPairwise"}) {
                arms.add(new Arm("DG " + policy.toUpperCase() + " " + ess, policy, ess, false));
            }
        }
        return arms;
    }

    private static Graph search(Arm arm, DataSet complete, DataSet missing) throws InterruptedException {
        Parameters p = new Parameters();
        p.set(Params.PENALTY_DISCOUNT, penaltyDiscount);
        p.set(Params.STRUCTURE_PRIOR, 0.0);
        p.set(Params.SINGULARITY_LAMBDA, 0.0);
        p.set(Params.PRECOMPUTE_COVARIANCES, true);
        p.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        if (arm.policy != null) {
            p.set(Params.MISSING_DATA_POLICY, arm.policy);
            p.set(Params.MISSING_ESS_MODE, arm.essMode);
        }
        Score score = new DegenerateGaussianBicScore().getScore(arm.oracle ? complete : missing, p);
        Boss boss = new Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }

    // ---------------------------------------------------------------- main

    private static final String[] STATS = {"adjP", "adjR", "arrP", "arrR", "SHD", "tgtR", "tgtP", "sec"};

    /**
     * Runs the comparison.
     *
     * @param args {@code key=value} settings; see the class comment. {@code data} is required.
     * @throws Exception If the file cannot be read or a simulation fails.
     */
    public static void main(String[] args) throws Exception {
        parseArgs(args);
        if (dataPath == null) {
            throw new IllegalArgumentException("Give the real data file as data=/path/to/file.");
        }

        Shape shape = Shape.read(dataPath, maxCategories);
        int[] order = shape.order(tiersPath);
        int target = shape.targetIndex(targetName, order);

        System.out.printf("Real data %s: %d rows, %d variables (%d discrete), %.1f%% of cells missing, %d complete"
                          + " rows.%n", dataPath, shape.n, shape.p, shape.numDiscrete(), 100 * shape.missingShare(),
                shape.completeRows());
        List<String> discreteList = new ArrayList<>();
        for (int j = 0; j < shape.p; j++) {
            if (shape.discrete[j]) {
                discreteList.add(shape.names.get(j) + " (" + shape.categoryShares[j].length + ")");
            }
        }
        System.out.println("Discrete, with category counts: " + (discreteList.isEmpty() ? "none" : discreteList));
        System.out.printf("Target %s; patterns %s; ceiling %s; avg degree %.1f; coefficients [%.2f, %.2f];"
                          + " penalty %.1f; reps %d.%n%n", shape.names.get(target), match, ceiling ? "on" : "off",
                avgDegree, coefLo, coefHi, penaltyDiscount, reps);
        if (ceiling) {
            System.out.println("Share at maximum used for the ceiling, continuous variables:");
            for (int j = 0; j < shape.p; j++) {
                if (!shape.discrete[j] && j != target && shape.shareAtMax[j] > 0.02) {
                    System.out.printf("  %-40s %.3f%n", shape.names.get(j), shape.shareAtMax[j]);
                }
            }
            System.out.println();
        }

        List<Arm> arms = arms();
        Map<String, Acc[]> stats = new LinkedHashMap<>();
        for (Arm arm : arms) stats.put(arm.name, Acc.array(STATS.length));

        try (PrintWriter csv = new PrintWriter(new FileWriter(CSV_FILE))) {
            csv.println("rep,arm," + String.join(",", STATS));

            for (int rep = 0; rep < reps; rep++) {
                RandomUtil.getInstance().setSeed(41000L + rep);
                Sim s = Sim.draw(shape, order, target);
                DataSet missing = applyMasks(shape, s, target);
                Graph trueCpdag = GraphTransforms.dagToCpdag(s.dag);

                for (Arm arm : arms) {
                    long t0 = System.currentTimeMillis();
                    Graph g;
                    try {
                        g = runWithTimeout(arm, s.data, missing);
                    } catch (TimeoutException e) {
                        System.out.printf("  [%s] rep %d timed out after %ds%n", arm.name, rep, ARM_TIMEOUT_SECONDS);
                        continue;
                    } catch (Exception e) {
                        System.out.printf("  [%s] rep %d failed: %s%n", arm.name, rep,
                                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        continue;
                    }
                    double seconds = (System.currentTimeMillis() - t0) / 1000.0;
                    g = GraphUtils.replaceNodes(g, trueCpdag.getNodes());

                    double[] v = searchStats(trueCpdag, g, s, target, missing, seconds);
                    Acc[] a = stats.get(arm.name);
                    StringBuilder line = new StringBuilder();
                    for (int k = 0; k < v.length; k++) {
                        if (!Double.isNaN(v[k])) a[k].add(v[k]);
                        line.append(',').append(Double.isNaN(v[k]) ? "" : String.format("%.4f", v[k]));
                    }
                    csv.printf("%d,\"%s\"%s%n", rep, arm.name, line);
                }
                csv.flush();
                System.out.printf("rep %d done (target has %d parents)%n", rep, s.parents[target].length);
            }
        }

        System.out.printf("%n%-26s", "arm");
        for (String h : STATS) System.out.printf(" %15s", h);
        System.out.printf(" %5s%n", "runs");
        for (Map.Entry<String, Acc[]> e : stats.entrySet()) {
            System.out.printf("%-26s", e.getKey());
            for (Acc a : e.getValue()) System.out.printf(" %15s", a.cell());
            System.out.printf(" %5d%n", e.getValue()[STATS.length - 1].n);
        }
        System.out.println();
        System.out.println("adj/arr = adjacency/arrowhead precision (P) and recall (R) against the true CPDAG; SHD");
        System.out.println("blank where a graph is not a legal PDAG; tgtR/tgtP = recall/precision of the target's");
        System.out.println("adjacencies. Mean (standard error) over repetitions; runs = repetitions that produced a graph.");
        System.out.println("Per-repetition rows written to " + new java.io.File(CSV_FILE).getAbsolutePath());
    }

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            String[] kv = arg.split("=", 2);
            if (kv.length != 2) throw new IllegalArgumentException("Expected key=value, got: " + arg);
            switch (kv[0].trim()) {
                case "data" -> dataPath = kv[1];
                case "tiers" -> tiersPath = kv[1];
                case "target" -> targetName = kv[1];
                case "match" -> {
                    match = kv[1].trim().toLowerCase();
                    if (!match.equals("permuted") && !match.equals("matched")) {
                        throw new IllegalArgumentException("match must be permuted or matched.");
                    }
                }
                case "ceiling" -> ceiling = Boolean.parseBoolean(kv[1]);
                case "reps" -> reps = Integer.parseInt(kv[1]);
                case "avgDegree" -> avgDegree = Double.parseDouble(kv[1]);
                case "coefLo" -> coefLo = Double.parseDouble(kv[1]);
                case "coefHi" -> coefHi = Double.parseDouble(kv[1]);
                case "penalty" -> penaltyDiscount = Double.parseDouble(kv[1]);
                case "maxCategories" -> maxCategories = Integer.parseInt(kv[1]);
                case "targetParents" -> targetParents = Integer.parseInt(kv[1]);
                default -> throw new IllegalArgumentException("Unknown setting: " + kv[0]);
            }
        }
    }

    /**
     * The simulated data with the real missingness pattern applied: real row r's pattern goes to simulated row
     * assignment[r].
     */
    private static DataSet applyMasks(Shape shape, Sim s, int target) {
        int n = shape.n;
        RandomUtil r = RandomUtil.getInstance();

        // Simulated rows in random order, then, if matching, re-paired by target rank.
        Integer[] simRows = new Integer[n];
        for (int i = 0; i < n; i++) simRows[i] = i;
        shuffle(simRows, r);

        int[] assignment = new int[n]; // real row -> simulated row
        if (match.equals("matched")) {
            List<Integer> observed = new ArrayList<>(), unobserved = new ArrayList<>();
            for (int i = 0; i < n; i++) (Double.isNaN(shape.values[i][target]) ? unobserved : observed).add(i);
            observed.sort(Comparator.comparingDouble(i -> shape.values[i][target]));

            // Simulated rows sorted by the simulated target; observed real rows take evenly spaced ranks, the rest
            // fill the gaps at random.
            Integer[] bySim = simRows.clone();
            Arrays.sort(bySim, Comparator.comparingDouble(i -> s.data.getDouble(i, target)));
            boolean[] used = new boolean[n];
            int m = observed.size();
            for (int k = 0; k < m; k++) {
                int rank = m == 1 ? n / 2 : (int) Math.round(k * (n - 1) / (double) (m - 1));
                while (used[rank]) rank = (rank + 1) % n;
                used[rank] = true;
                assignment[observed.get(k)] = bySim[rank];
            }
            int next = 0;
            for (int i : unobserved) {
                while (used[next]) next++;
                used[next] = true;
                assignment[i] = bySim[next];
            }
        } else {
            for (int i = 0; i < n; i++) assignment[i] = simRows[i];
        }

        DataSet out = s.data.copy();
        for (int i = 0; i < n; i++) {
            int row = assignment[i];
            for (int j = 0; j < shape.p; j++) {
                if (!Double.isNaN(shape.values[i][j])) continue;
                if (shape.discrete[j]) out.setInt(row, j, DiscreteVariable.MISSING_VALUE);
                else out.setDouble(row, j, Double.NaN);
            }
        }
        return out;
    }

    private static void shuffle(Integer[] a, RandomUtil r) {
        for (int i = a.length - 1; i > 0; i--) {
            int k = (int) Math.floor(r.nextDouble() * (i + 1));
            Integer t = a[i];
            a[i] = a[k];
            a[k] = t;
        }
    }

    private static double[] searchStats(Graph trueCpdag, Graph g, Sim s, int target, DataSet data, double seconds) {
        double shd = new StructuralHammingDistance().getValue(trueCpdag, g, data);
        Node gTarget = g.getNode(s.names.get(target));
        Node tTarget = trueCpdag.getNode(s.names.get(target));
        Set<String> trueAdj = new HashSet<>();
        for (Node n : trueCpdag.getAdjacentNodes(tTarget)) trueAdj.add(n.getName());
        int found = 0;
        List<Node> estAdj = g.getAdjacentNodes(gTarget);
        for (Node n : estAdj) if (trueAdj.contains(n.getName())) found++;

        return new double[]{
                new AdjacencyPrecision().getValue(trueCpdag, g, data),
                new AdjacencyRecall().getValue(trueCpdag, g, data),
                new ArrowheadPrecision().getValue(trueCpdag, g, data),
                new ArrowheadRecall().getValue(trueCpdag, g, data),
                shd < 0 ? Double.NaN : shd,
                trueAdj.isEmpty() ? Double.NaN : found / (double) trueAdj.size(),
                estAdj.isEmpty() ? Double.NaN : found / (double) estAdj.size(),
                seconds};
    }

    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet missing) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return search(arm, complete, missing);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chem-mask-arm");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Graph> future = executor.submit(() -> search(arm, complete, missing));
            try {
                return future.get(ARM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- the real data's shape

    /**
     * What is read from the real file: names, values (NaN for missing), which columns are discrete and their
     * category proportions, and each continuous column's share of observed values at its maximum.
     */
    private static final class Shape {
        int n, p;
        List<String> names = new ArrayList<>();
        double[][] values;
        boolean[] discrete;
        double[][] categoryShares; // per discrete column, cumulative-free shares in category order
        double[] shareAtMax;

        static Shape read(String path, int maxCategories) throws IOException {
            List<String[]> rows = new ArrayList<>();
            String delimiter;
            List<String> header;
            try (BufferedReader in = new BufferedReader(new FileReader(path))) {
                String first = in.readLine();
                if (first == null) throw new IOException("Empty file: " + path);
                delimiter = first.contains("\t") ? "\t" : first.contains(",") ? "," : "\\s+";
                header = new ArrayList<>();
                for (String h : first.trim().split(delimiter, -1)) header.add(unquote(h.trim()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank()) continue;
                    rows.add(line.split(delimiter, -1));
                }
            }

            Shape s = new Shape();
            s.names.addAll(header);
            s.p = header.size();
            s.n = rows.size();
            s.values = new double[s.n][s.p];
            for (int i = 0; i < s.n; i++) {
                String[] r = rows.get(i);
                if (r.length != s.p) {
                    throw new IOException("Row " + (i + 2) + " has " + r.length + " fields; the header has " + s.p
                                          + ".");
                }
            }

            // A column with any non-numeric entry is categorical: its distinct strings, sorted, are coded 0, 1, ...
            // and it is treated as discrete whatever the number of categories. Other columns are parsed as numbers.
            boolean[] text = new boolean[s.p];
            for (int j = 0; j < s.p; j++) {
                TreeSet<String> labels = new TreeSet<>();
                for (String[] r : rows) {
                    String t = cell(r[j]);
                    if (t == null) continue;
                    labels.add(t);
                    if (!isNumber(t)) text[j] = true;
                }
                List<String> codes = new ArrayList<>(labels);
                for (int i = 0; i < s.n; i++) {
                    String t = cell(rows.get(i)[j]);
                    s.values[i][j] = t == null ? Double.NaN : text[j] ? codes.indexOf(t) : Double.parseDouble(t);
                }
            }

            s.discrete = new boolean[s.p];
            s.categoryShares = new double[s.p][];
            s.shareAtMax = new double[s.p];
            for (int j = 0; j < s.p; j++) {
                TreeSet<Double> distinct = new TreeSet<>();
                int m = 0;
                double max = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < s.n; i++) {
                    double v = s.values[i][j];
                    if (Double.isNaN(v)) continue;
                    distinct.add(v);
                    m++;
                    max = Math.max(max, v);
                }
                if (m == 0) throw new IOException("Column " + header.get(j) + " has no observed values.");
                if (text[j] || distinct.size() <= maxCategories) {
                    s.discrete[j] = true;
                    List<Double> cats = new ArrayList<>(distinct);
                    double[] shares = new double[cats.size()];
                    for (int i = 0; i < s.n; i++) {
                        double v = s.values[i][j];
                        if (!Double.isNaN(v)) shares[cats.indexOf(v)] += 1.0 / m;
                    }
                    s.categoryShares[j] = shares;
                } else {
                    int atMax = 0;
                    for (int i = 0; i < s.n; i++) if (s.values[i][j] == max) atMax++;
                    s.shareAtMax[j] = atMax / (double) m;
                }
            }
            return s;
        }

        private static String unquote(String s) {
            return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
        }

        /** A cell's trimmed, unquoted text, or null if it marks a missing value. */
        private static String cell(String raw) {
            String t = unquote(raw.trim());
            if (t.isEmpty() || t.equals("*") || t.equalsIgnoreCase("NA") || t.equalsIgnoreCase("NaN")
                || t.equals("?")) {
                return null;
            }
            return t;
        }

        private static boolean isNumber(String t) {
            try {
                Double.parseDouble(t);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        int numDiscrete() {
            int k = 0;
            for (boolean d : discrete) if (d) k++;
            return k;
        }

        double missingShare() {
            int miss = 0;
            for (double[] r : values) for (double v : r) if (Double.isNaN(v)) miss++;
            return miss / (double) (n * p);
        }

        int completeRows() {
            int c = 0;
            R:
            for (double[] r : values) {
                for (double v : r) if (Double.isNaN(v)) continue R;
                c++;
            }
            return c;
        }

        /** Column indices in causal order: by tier from the tiers file, randomly within a tier; else column order. */
        int[] order(String tiersPath) throws IOException {
            if (tiersPath == null) {
                int[] o = new int[p];
                for (int j = 0; j < p; j++) o[j] = j;
                return o;
            }
            Map<String, Integer> index = new HashMap<>();
            for (int j = 0; j < p; j++) index.put(names.get(j), j);
            List<List<Integer>> tiers = new ArrayList<>();
            Set<Integer> placed = new HashSet<>();
            try (BufferedReader in = new BufferedReader(new FileReader(tiersPath))) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    List<Integer> tier = new ArrayList<>();
                    for (String name : line.split(",")) {
                        String nm = unquote(name.trim());
                        if (nm.isEmpty()) continue;
                        Integer j = index.get(nm);
                        if (j == null) {
                            System.out.println("Tiers file names " + nm + ", which is not in the data; ignored.");
                            continue;
                        }
                        if (placed.add(j)) tier.add(j);
                    }
                    if (!tier.isEmpty()) tiers.add(tier);
                }
            }
            List<Integer> rest = new ArrayList<>();
            for (int j = 0; j < p; j++) if (!placed.contains(j)) rest.add(j);
            if (!rest.isEmpty()) {
                System.out.println("Not in any tier, placed first: "
                                   + rest.stream().map(names::get).toList());
                tiers.add(0, rest);
            }
            this.tiers = tiers;
            int[] o = new int[p];
            int k = 0;
            for (List<Integer> t : tiers) for (int j : t) o[k++] = j;
            return o;
        }

        /** The tiers, in order, or null if there is no tiers file; within-tier order is drawn per repetition. */
        List<List<Integer>> tiers = null;

        int targetIndex(String name, int[] order) {
            String want = name != null ? name : names.contains("final") ? "final" : null;
            if (want != null) {
                int j = names.indexOf(want);
                if (j < 0) throw new IllegalArgumentException("No variable named " + want + " in the data.");
                return j;
            }
            return order[order.length - 1];
        }
    }

    // ---------------------------------------------------------------- simulation

    private static final class Sim {
        List<String> names;
        Graph dag;
        DataSet data;
        int[][] parents;

        static Sim draw(Shape shape, int[] baseOrder, int target) {
            RandomUtil r = RandomUtil.getInstance();
            int p = shape.p, n = shape.n;

            // Causal order: tiers in order, shuffled within each tier.
            int[] order;
            if (shape.tiers == null) {
                order = baseOrder.clone();
            } else {
                List<Integer> o = new ArrayList<>();
                for (List<Integer> t : shape.tiers) {
                    Integer[] tier = t.toArray(new Integer[0]);
                    shuffle(tier, r);
                    o.addAll(Arrays.asList(tier));
                }
                order = o.stream().mapToInt(Integer::intValue).toArray();
            }
            // The target goes last: nothing in the data is caused by the final grade.
            int[] o2 = new int[p];
            int k = 0;
            for (int j : order) if (j != target) o2[k++] = j;
            o2[k] = target;
            order = o2;

            // Random DAG consistent with the order, with the requested expected average degree.
            int numPairs = p * (p - 1) / 2;
            double prob = Math.min(1.0, avgDegree * p / 2.0 / numPairs);
            List<List<Integer>> par = new ArrayList<>();
            for (int j = 0; j < p; j++) par.add(new ArrayList<>());
            for (int a = 0; a < p; a++) {
                for (int b = a + 1; b < p - 1; b++) {
                    if (r.nextDouble() < prob) par.get(order[b]).add(order[a]);
                }
            }
            // The target, last in the order, gets exactly targetParents parents drawn from the rest.
            Integer[] candidates = new Integer[p - 1];
            for (int a = 0; a < p - 1; a++) candidates[a] = order[a];
            shuffle(candidates, r);
            for (int a = 0; a < Math.min(targetParents, p - 1); a++) par.get(target).add(candidates[a]);

            Sim s = new Sim();
            s.names = new ArrayList<>(shape.names);
            List<Node> vars = new ArrayList<>();
            for (int j = 0; j < p; j++) {
                vars.add(shape.discrete[j]
                        ? new DiscreteVariable(shape.names.get(j), shape.categoryShares[j].length)
                        : new ContinuousVariable(shape.names.get(j)));
            }
            s.parents = new int[p][];
            for (int j = 0; j < p; j++) s.parents[j] = par.get(j).stream().mapToInt(Integer::intValue).toArray();

            Graph dag = new EdgeListGraph(vars);
            for (int j = 0; j < p; j++) for (int q : s.parents[j]) dag.addDirectedEdge(vars.get(q), vars.get(j));
            s.dag = dag;

            double[][] coef = new double[p][p];
            for (int j = 0; j < p; j++) {
                for (int q : s.parents[j]) coef[j][q] = coefLo + (coefHi - coefLo) * r.nextDouble();
            }

            // Values: continuous columns directly; discrete columns from thresholds on a standardized latent,
            // with children seeing the category index standardized.
            // raw holds the values the data will carry; x holds them standardized, as the parents' inputs.
            double[][] x = new double[n][p];
            double[][] raw = new double[n][p];
            for (int j : order) {
                double[] latent = new double[n];
                for (int i = 0; i < n; i++) {
                    double v = r.nextGaussian(0, 1);
                    for (int q : s.parents[j]) v += coef[j][q] * x[i][q];
                    latent[i] = v;
                }
                if (shape.discrete[j]) {
                    double[] sorted = latent.clone();
                    Arrays.sort(sorted);
                    double[] shares = shape.categoryShares[j];
                    double[] cuts = new double[shares.length - 1];
                    double cum = 0;
                    for (int c = 0; c < cuts.length; c++) {
                        cum += shares[c];
                        cuts[c] = sorted[Math.min(n - 1, (int) Math.floor(cum * n))];
                    }
                    for (int i = 0; i < n; i++) {
                        int c = 0;
                        while (c < cuts.length && latent[i] >= cuts[c]) c++;
                        x[i][j] = c;
                    }
                } else {
                    for (int i = 0; i < n; i++) x[i][j] = latent[i];
                    if (ceiling && j != target && shape.shareAtMax[j] > 0) {
                        double[] sorted = latent.clone();
                        Arrays.sort(sorted);
                        int idx = Math.max(0, (int) Math.floor((1.0 - shape.shareAtMax[j]) * n) - 1);
                        double cap = sorted[idx];
                        for (int i = 0; i < n; i++) x[i][j] = Math.min(x[i][j], cap);
                    }
                }
                for (int i = 0; i < n; i++) raw[i][j] = x[i][j];
                standardize(x, j, n);
            }

            DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    if (shape.discrete[j]) data.setInt(i, j, (int) Math.round(raw[i][j]));
                    else data.setDouble(i, j, raw[i][j]);
                }
            }
            s.data = data;
            return s;
        }

        /** Standardizes column j in place, so every parent enters its children on the same scale. */
        private static void standardize(double[][] x, int j, int n) {
            double mean = 0, ss = 0;
            for (int i = 0; i < n; i++) mean += x[i][j] / n;
            for (int i = 0; i < n; i++) ss += (x[i][j] - mean) * (x[i][j] - mean);
            double sd = Math.sqrt(ss / Math.max(1, n - 1));
            for (int i = 0; i < n; i++) x[i][j] = sd > 0 ? (x[i][j] - mean) / sd : 0.0;
        }
    }

    /** A running mean and standard error. */
    private static final class Acc {
        private double sum, sumSq;
        private int n;

        static Acc[] array(int k) {
            Acc[] a = new Acc[k];
            for (int i = 0; i < k; i++) a[i] = new Acc();
            return a;
        }

        void add(double v) {
            sum += v;
            sumSq += v * v;
            n++;
        }

        String cell() {
            if (n == 0) return "-";
            double m = sum / n;
            if (n == 1) return String.format("%.3f", m);
            double var = Math.max(0.0, (sumSq - n * m * m) / (n - 1));
            return String.format("%.3f (%.3f)", m, Math.sqrt(var / n));
        }
    }
}
