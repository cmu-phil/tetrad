package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.MissingnessInjector.Mechanism;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Stage 2 of the missing-data experiments: does each missing-data policy make the linear Gaussian BIC score rank
 * the true DAG above its single-edge neighbours?
 *
 * <p>This tests the score, not the search. For each simulated DAG, every legal single-edge addition, deletion and
 * reversal is scored against the truth, and a policy counts as right on a move when the truth scores strictly
 * higher. Only the families whose parents change enter the difference, so each move is one or two local score
 * comparisons. A search can only be as good as the rankings its score gives, so a policy that mis-ranks here
 * cannot be rescued by a better search; one that ranks well here may still lose in search, which is Stage 3's
 * question.</p>
 *
 * <h2>How to read the output</h2>
 *
 * <ul>
 *   <li><b>ORACLE</b> scores the complete data before deletion. Its error rate is the finite-sample floor at this
 *   n and penalty; a policy is judged by how far it sits above that floor, not by its raw rate.</li>
 *   <li><b>induced</b> counts moves the oracle ranks right and the policy ranks wrong, per repetition. This is the
 *   error the missing-data handling itself is responsible for.</li>
 *   <li><b>|cov rev|</b> is the mean absolute score change on reversing a covered edge. Such a reversal stays in
 *   the Markov equivalence class, so a score-equivalent score gives zero, as the oracle does up to rounding. A
 *   nonzero value means the policy can orient edges inside an equivalence class from the missingness alone.</li>
 *   <li><b>NaN</b> counts local scores that could not be computed, usually too few complete rows.</li>
 * </ul>
 *
 * <p><b>TESTWISE COMMON ROWS</b> is not a policy Tetrad offers; it is a control. Under TESTWISE each local score
 * uses the rows complete on its own family, so the two sides of a comparison can rest on different cases. This row
 * instead scores both sides of each move with ordinary BIC on the rows complete on the union of the families the
 * move touches. If TESTWISE's errors come from comparing fits on different rows, they should vanish here, and
 * covered reversals should score exactly zero; whatever remains is selection, which no choice of rows removes.</p>
 *
 * <h2>Mechanisms and what to expect</h2>
 *
 * <p>Every mechanism deletes from the same variables, leaving two fully observed: the root with the most
 * descendants and the sink with the most ancestors, so that MAR mechanisms have a driver to use. The expectations
 * below are conjectures, from the regression form of the local score; checking them is the point of the run.</p>
 *
 * <ul>
 *   <li><b>MCAR</b>: all policies consistent; differences are efficiency only.</li>
 *   <li><b>MAR (root)</b>: deletion driven by the root. A true family regresses a child on its parents, which
 *   separate it from the root, a non-descendant, so selection on the root should leave true-family fits
 *   unbiased. Expect deletion policies to stay near the floor. EM is consistent under MAR.</li>
 *   <li><b>MAR (sink)</b>: deletion driven by the sink, a descendant. Selecting rows on an effect biases
 *   regressions of its causes, so expect induced errors for LISTWISE and TESTWISE, and EM near the floor.</li>
 *   <li><b>ATTRITION</b>: monotone dropout along the causal order, first with no drivers (MCAR), then with the
 *   hazard at each step driven by the variable just before it (sequential MAR). Under testwise deletion a family
 *   keeps the cases still present at its latest member, so selection is on earlier variables only.</li>
 *   <li><b>SELF-MASKING</b>: each variable's deletion depends on its own value. MNAR; expect every policy above the
 *   floor, EM included.</li>
 *   <li><b>COLLUDER</b>: each variable's deletion depends on the value and the indicator of the one before it in
 *   causal order. MNAR, and not identified in the sense of Nabi, Bhattacharya and Shpitser.</li>
 * </ul>
 *
 * <p>Run {@link #main(String[])} from IntelliJ. With the defaults it takes well under a minute.</p>
 *
 * @author josephramsey
 */
public final class MissingDataScoreDiagnostics {

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 12;

    /** Edges in the simulated graph. */
    private static final int NUM_EDGES = 18;

    /** Rows simulated before deletion. Large, so that bias shows through noise. */
    private static final int SAMPLE_SIZE = 2000;

    /** Repetitions; each draws a fresh graph, SEM and dataset, shared by every mechanism within it. */
    private static final int NUM_REPS = 10;

    /** BIC penalty discount for every policy. 1 is standard BIC, which is consistent. */
    private static final double PENALTY_DISCOUNT = 1.0;

    /** Marginal missingness rate for every deleted variable under the per-cell mechanisms. */
    private static final double RATE = 0.3;

    /** Row propensity for every mechanism. 0 isolates the mechanism term. */
    private static final double ROW_PROPENSITY = 0.0;

    /** Logit-scale weight on a standardized driver value, for every value effect. */
    private static final double VALUE_WEIGHT = 1.5;

    /** Logit-scale weight on a driver's indicator, for the colluder mechanism. */
    private static final double INDICATOR_WEIGHT = 2.0;

    /** Cumulative dropout rate at the first and the last attrition step, linear in between. */
    private static final double ATTRITION_FIRST = 0.05, ATTRITION_LAST = 0.5;

    private MissingDataScoreDiagnostics() {
    }

    // ---------------------------------------------------------------- policies

    private static Map<String, BiFunction<DataSet, DataSet, SemBicScore>> policies() {
        Map<String, BiFunction<DataSet, DataSet, SemBicScore>> policies = new LinkedHashMap<>();
        policies.put("ORACLE", (complete, missing) -> score(complete, null));
        policies.put("LISTWISE", (complete, missing) -> score(missing, MissingDataSpec.listwise()));
        policies.put("TESTWISE", (complete, missing) -> score(missing, MissingDataSpec.testwise()));
        policies.put(COMMON_ROWS, null);
        for (MissingDataSpec.EffectiveSampleSizeMode mode : MissingDataSpec.EffectiveSampleSizeMode.values()) {
            policies.put("EM_COV " + mode, (complete, missing) ->
                    score(missing, MissingDataSpec.emCovariance().withEssMode(mode)));
        }
        return policies;
    }

    /** The control policy, scored per move on common rows; see the class comment. */
    private static final String COMMON_ROWS = "TESTWISE COMMON ROWS";

    private static SemBicScore score(DataSet data, MissingDataSpec spec) {
        SemBicScore score = new SemBicScore(data, true, spec);
        // Set after construction: the test-wise path sets its own discount in the constructor.
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    // ---------------------------------------------------------------- mechanisms

    /** A mechanism built for a particular DAG, given its structure in column indices. */
    private interface MechanismFactory {
        Injection build(Structure s);
    }

    private record Injection(double[] rates, Mechanism mechanism) {
    }

    private static Map<String, MechanismFactory> mechanisms() {
        Map<String, MechanismFactory> m = new LinkedHashMap<>();

        m.put("MCAR", s -> new Injection(perCellRates(s), Mechanism.none()));

        m.put("MAR (root)", s -> {
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withValueEffect(j, s.root, VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });

        m.put("MAR (sink)", s -> {
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withValueEffect(j, s.sink, VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });

        m.put("ATTRITION (MCAR)", s -> new Injection(attritionRates(s), Mechanism.none().withAttrition(s.deleted)));

        m.put("ATTRITION (seq. MAR)", s -> {
            // Low values of the previous variable drive dropout, as a poor score might.
            Mechanism mech = Mechanism.none().withAttrition(s.deleted);
            for (int t = 1; t < s.deleted.length; t++) {
                mech = mech.withValueEffect(s.deleted[t], s.deleted[t - 1], -VALUE_WEIGHT);
            }
            return new Injection(attritionRates(s), mech);
        });

        m.put("SELF-MASKING", s -> {
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withSelfMasking(j, -VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });

        m.put("COLLUDER", s -> {
            Mechanism mech = Mechanism.none();
            for (int t = 1; t < s.deleted.length; t++) {
                mech = mech.withValueEffect(s.deleted[t], s.deleted[t - 1], -VALUE_WEIGHT)
                        .withIndicatorEffect(s.deleted[t], s.deleted[t - 1], INDICATOR_WEIGHT);
            }
            return new Injection(perCellRates(s), mech);
        });

        return m;
    }

    private static double[] perCellRates(Structure s) {
        double[] rates = new double[s.p];
        for (int j : s.deleted) rates[j] = RATE;
        return rates;
    }

    private static double[] attritionRates(Structure s) {
        double[] rates = new double[s.p];
        int k = s.deleted.length;
        for (int t = 0; t < k; t++) {
            rates[s.deleted[t]] = k == 1 ? ATTRITION_FIRST
                    : ATTRITION_FIRST + (ATTRITION_LAST - ATTRITION_FIRST) * t / (k - 1.0);
        }
        return rates;
    }

    // ---------------------------------------------------------------- main

    /**
     * Runs the diagnostics and prints one table per mechanism.
     *
     * @param args Not used.
     * @throws Exception If simulation fails.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Missing-data score diagnostics: p=%d, edges=%d, n=%d, reps=%d, penalty=%.1f, rate=%.2f,"
                          + " value weight=%.1f, row propensity=%.1f%n%n",
                NUM_VARS, NUM_EDGES, SAMPLE_SIZE, NUM_REPS, PENALTY_DISCOUNT, RATE, VALUE_WEIGHT, ROW_PROPENSITY);

        Map<String, MechanismFactory> mechanisms = mechanisms();
        Map<String, BiFunction<DataSet, DataSet, SemBicScore>> policies = policies();

        // tallies[mechanism][policy]
        Map<String, Map<String, Tally>> tallies = new LinkedHashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        Map<String, double[]> completeRows = new LinkedHashMap<>();
        for (String mech : mechanisms.keySet()) {
            Map<String, Tally> row = new LinkedHashMap<>();
            for (String pol : policies.keySet()) row.put(pol, new Tally());
            tallies.put(mech, row);
            completeRows.put(mech, new double[1]);
        }

        for (int rep = 0; rep < NUM_REPS; rep++) {
            RandomUtil.getInstance().setSeed(5000L + rep);
            Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
            DataSet complete = new SemIm(new SemPm(dag)).simulateData(SAMPLE_SIZE, false);
            Structure s = Structure.of(dag, complete);
            List<Move> moves = s.moves();

            for (Map.Entry<String, MechanismFactory> me : mechanisms.entrySet()) {
                RandomUtil.getInstance().setSeed(9000L + rep);
                Injection inj = me.getValue().build(s);
                MissingnessInjector.Result injected = MissingnessInjector.inject(
                        complete, new MissingnessInjector.Spec(inj.rates, ROW_PROPENSITY), inj.mechanism);
                classes.putIfAbsent(me.getKey(), injected.mechanism().overall().toString());
                completeRows.get(me.getKey())[0] += injected.report().completeRows();

                boolean[] oracleRight = null;
                for (Map.Entry<String, BiFunction<DataSet, DataSet, SemBicScore>> pe : policies.entrySet()) {
                    Tally tally = tallies.get(me.getKey()).get(pe.getKey());
                    boolean commonRows = pe.getValue() == null;
                    SemBicScore score = null;
                    int[] col = null;
                    if (!commonRows) {
                        try {
                            score = pe.getValue().apply(complete, injected.data());
                        } catch (RuntimeException e) {
                            tally.failures++;
                            System.out.printf("  [%s / %s] rep %d: score could not be built: %s%n",
                                    me.getKey(), pe.getKey(), rep, e.getMessage());
                            if (oracleRight == null) oracleRight = new boolean[moves.size()];
                            continue;
                        }
                        col = s.columnsIn(score);
                    }
                    boolean[] right = new boolean[moves.size()];
                    for (int k = 0; k < moves.size(); k++) {
                        Move mv = moves.get(k);
                        double delta = commonRows ? commonRowsDelta(mv, injected.data(), s)
                                : mv.delta(score, col);
                        if (Double.isNaN(delta)) {
                            tally.nan++;
                            continue;
                        }
                        if (mv.type == MoveType.COVERED_REVERSAL) {
                            tally.coveredSum += Math.abs(delta);
                            tally.coveredCount++;
                            continue;
                        }
                        right[k] = delta > 0.0;
                        tally.count(mv.type, right[k]);
                        if (oracleRight != null && oracleRight[k] && !right[k]) tally.induced[mv.type.ordinal()]++;
                    }
                    if (oracleRight == null) oracleRight = right;
                }
            }
            System.out.printf("rep %d done: %d moves (%s)%n", rep, moves.size(), Move.summary(moves));
        }

        System.out.println();
        for (String mech : mechanisms.keySet()) {
            System.out.printf("=== %s  (class %s; mean complete rows %.0f of %d) ===%n", mech, classes.get(mech),
                    completeRows.get(mech)[0] / NUM_REPS, SAMPLE_SIZE);
            System.out.printf("%-22s %8s %8s %8s %8s %8s %8s %10s %6s%n", "policy", "add ok", "del ok",
                    "rev ok", "induced", "ind/add", "ind/del", "|cov rev|", "NaN");
            for (Map.Entry<String, Tally> e : tallies.get(mech).entrySet()) {
                Tally t = e.getValue();
                System.out.printf("%-22s %8s %8s %8s %8.1f %8.1f %8.1f %10s %6d%n", e.getKey(),
                        pct(t, MoveType.ADD), pct(t, MoveType.DELETE), pct(t, MoveType.REVERSAL),
                        t.totalInduced() / (double) NUM_REPS,
                        t.induced[MoveType.ADD.ordinal()] / (double) NUM_REPS,
                        t.induced[MoveType.DELETE.ordinal()] / (double) NUM_REPS,
                        t.coveredCount == 0 ? "-" : String.format("%.3g", t.coveredSum / t.coveredCount),
                        t.nan);
            }
            System.out.println();
        }
        System.out.println("ok columns: share of moves where the truth scores strictly higher. induced: per rep, moves");
        System.out.println("the oracle ranks right and the policy wrong. |cov rev|: mean |score change| on reversing a");
        System.out.println("covered edge; zero for a score-equivalent score.");
    }

    /**
     * The move's score difference by ordinary BIC on the rows complete on every variable in the families it
     * touches, so that both sides rest on the same cases. NaN if too few rows remain to fit them.
     */
    private static double commonRowsDelta(Move mv, DataSet data, Structure s) {
        java.util.LinkedHashSet<Integer> union = new java.util.LinkedHashSet<>();
        union.add(mv.y);
        union.add(mv.x);
        for (int j : mv.parentsOfY) union.add(j);
        if (mv.type == MoveType.REVERSAL || mv.type == MoveType.COVERED_REVERSAL) {
            for (int j : mv.parentsOfX) union.add(j);
        }
        int[] cols = union.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) {
            boolean complete = true;
            for (int j : cols) {
                if (Double.isNaN(data.getDouble(i, j))) {
                    complete = false;
                    break;
                }
            }
            if (complete) rows.add(i);
        }
        if (rows.size() <= cols.length + 1) return Double.NaN;

        SemBicScore score = score(data.subsetRowsColumns(rows.stream().mapToInt(Integer::intValue).toArray(), cols),
                null);
        int[] col = new int[s.p];
        Arrays.fill(col, -1);
        for (int c = 0; c < cols.length; c++) col[cols[c]] = c;
        return mv.delta(score, col);
    }

    private static String pct(Tally t, MoveType type) {
        int n = t.total[type.ordinal()];
        return n == 0 ? "-" : String.format("%.3f", t.right[type.ordinal()] / (double) n);
    }

    // ---------------------------------------------------------------- moves

    private enum MoveType { ADD, DELETE, REVERSAL, COVERED_REVERSAL }

    /**
     * A single-edge change to the true DAG, x -> y added, deleted or reversed, in the DAG's own indices.
     */
    private record Move(MoveType type, int x, int y, int[] parentsOfY, int[] parentsOfX) {

        /** Score of the truth minus score of the neighbour, in the score's column indices. NaN if undefined. */
        double delta(SemBicScore score, int[] col) {
            int[] py = map(parentsOfY, col);
            int cy = col[y], cx = col[x];
            switch (type) {
                case ADD:
                    return score.localScore(cy, py) - score.localScore(cy, with(py, cx));
                case DELETE:
                    return score.localScore(cy, py) - score.localScore(cy, without(py, cx));
                default: {
                    int[] px = map(parentsOfX, col);
                    double truth = score.localScore(cy, py) + score.localScore(cx, px);
                    double neighbour = score.localScore(cy, without(py, cx)) + score.localScore(cx, with(px, cy));
                    return truth - neighbour;
                }
            }
        }

        static String summary(List<Move> moves) {
            int[] c = new int[MoveType.values().length];
            for (Move m : moves) c[m.type.ordinal()]++;
            return String.format("%d add, %d delete, %d reversal, %d covered reversal", c[0], c[1], c[2], c[3]);
        }
    }

    private static int[] map(int[] idx, int[] col) {
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = col[idx[i]];
        return out;
    }

    private static int[] with(int[] a, int v) {
        int[] out = Arrays.copyOf(a, a.length + 1);
        out[a.length] = v;
        return out;
    }

    private static int[] without(int[] a, int v) {
        return Arrays.stream(a).filter(e -> e != v).toArray();
    }

    // ---------------------------------------------------------------- structure

    /**
     * The true DAG in index form: parents, a topological order, the fully observed root and sink, and the deleted
     * variables in topological order.
     */
    private static final class Structure {
        final int p;
        final List<String> names = new ArrayList<>();
        final int[][] parents;
        final int[] order;
        final int root, sink;
        final int[] deleted;

        private Structure(int p, List<String> names, int[][] parents) {
            this.p = p;
            this.names.addAll(names);
            this.parents = parents;
            this.order = topologicalOrder();

            boolean[][] anc = ancestry();
            int bestRoot = -1, bestSink = -1, mostDesc = -1, mostAnc = -1;
            for (int j : order) {
                int desc = 0, ancs = 0;
                for (int k = 0; k < p; k++) {
                    if (anc[j][k]) desc++;
                    if (anc[k][j]) ancs++;
                }
                boolean isRoot = parents[j].length == 0;
                boolean isSink = true;
                for (int k = 0; k < p; k++) if (contains(parents[k], j)) isSink = false;
                if (isRoot && desc > mostDesc) { mostDesc = desc; bestRoot = j; }
                if (isSink && ancs > mostAnc) { mostAnc = ancs; bestSink = j; }
            }
            this.root = bestRoot;
            this.sink = bestSink == bestRoot ? -1 : bestSink;

            List<Integer> del = new ArrayList<>();
            for (int j : order) if (j != root && j != sink) del.add(j);
            this.deleted = del.stream().mapToInt(Integer::intValue).toArray();
        }

        /** Builds the structure with indices equal to the data's columns. */
        static Structure of(Graph dag, DataSet data) {
            int p = data.getNumColumns();
            List<String> names = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            for (int j = 0; j < p; j++) {
                names.add(data.getVariable(j).getName());
                index.put(names.get(j), j);
            }
            int[][] parents = new int[p][];
            for (Node node : dag.getNodes()) {
                Integer j = index.get(node.getName());
                if (j == null) continue;
                parents[j] = dag.getParents(node).stream().map(Node::getName).map(index::get)
                        .mapToInt(Integer::intValue).toArray();
            }
            for (int j = 0; j < p; j++) {
                if (parents[j] == null) throw new IllegalStateException("No graph node for " + names.get(j));
            }
            return new Structure(p, names, parents);
        }

        /** For each of the structure's indices, the score's column for the same variable. */
        int[] columnsIn(SemBicScore score) {
            Map<String, Integer> index = new HashMap<>();
            List<Node> vars = score.getVariables();
            for (int j = 0; j < vars.size(); j++) index.put(vars.get(j).getName(), j);
            int[] col = new int[p];
            for (int j = 0; j < p; j++) col[j] = index.get(names.get(j));
            return col;
        }

        /** Every legal single-edge neighbour of the true DAG. */
        List<Move> moves() {
            boolean[][] anc = ancestry();
            List<Move> moves = new ArrayList<>();
            for (int x = 0; x < p; x++) {
                for (int y = 0; y < p; y++) {
                    if (x == y) continue;
                    if (contains(parents[y], x)) {
                        moves.add(new Move(MoveType.DELETE, x, y, parents[y], parents[x]));
                        // Reversing x -> y is legal unless another directed path runs from x to y.
                        boolean otherPath = false;
                        for (int z : parents[y]) if (z != x && anc[x][z]) otherPath = true;
                        if (!otherPath) {
                            MoveType t = covered(x, y) ? MoveType.COVERED_REVERSAL : MoveType.REVERSAL;
                            moves.add(new Move(t, x, y, parents[y], parents[x]));
                        }
                    } else if (!contains(parents[x], y) && !anc[y][x]) {
                        // Adding x -> y is legal unless y is already an ancestor of x.
                        moves.add(new Move(MoveType.ADD, x, y, parents[y], parents[x]));
                    }
                }
            }
            return moves;
        }

        /** x -> y is covered if the parents of y are exactly the parents of x together with x. */
        private boolean covered(int x, int y) {
            if (parents[y].length != parents[x].length + 1) return false;
            for (int z : parents[x]) if (!contains(parents[y], z)) return false;
            return true;
        }

        /** anc[a][b] is true when a is a proper ancestor of b. */
        private boolean[][] ancestry() {
            boolean[][] anc = new boolean[p][p];
            for (int j : order) {
                for (int q : parents[j]) {
                    anc[q][j] = true;
                    for (int a = 0; a < p; a++) if (anc[a][q]) anc[a][j] = true;
                }
            }
            return anc;
        }

        private int[] topologicalOrder() {
            int[] indeg = new int[p];
            for (int j = 0; j < p; j++) indeg[j] = parents[j].length;
            List<Integer> out = new ArrayList<>();
            boolean[] done = new boolean[p];
            while (out.size() < p) {
                int next = -1;
                for (int j = 0; j < p; j++) if (!done[j] && indeg[j] == 0) { next = j; break; }
                if (next < 0) throw new IllegalStateException("The graph has a cycle.");
                done[next] = true;
                out.add(next);
                for (int k = 0; k < p; k++) if (contains(parents[k], next)) indeg[k]--;
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }

        private static boolean contains(int[] a, int v) {
            for (int e : a) if (e == v) return true;
            return false;
        }
    }

    // ---------------------------------------------------------------- tallies

    private static final class Tally {
        final int[] right = new int[MoveType.values().length];
        final int[] total = new int[MoveType.values().length];
        final int[] induced = new int[MoveType.values().length];
        double coveredSum;
        int coveredCount;
        int nan;
        int failures;

        void count(MoveType type, boolean ok) {
            total[type.ordinal()]++;
            if (ok) right[type.ordinal()]++;
        }

        int totalInduced() {
            int s = 0;
            for (int v : induced) s += v;
            return s;
        }
    }
}
