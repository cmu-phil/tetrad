package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;

/**
 * Small-model precision study for FCI-style latent variable searches from sample: which search, at which settings,
 * gives high adjacency and orientation precision consistently over a handful of runs.
 *
 * <p>Hand-run harness with a main. Arguments, all optional, in order:
 * {@code seedBase runs sampleSize numMeasured numLatents numEdges timeoutSeconds}. Defaults: seeds from 101, 5
 * runs, N = 1000, 15 measured variables plus 3 latent common causes, 20 edges, 180 seconds per search. Keep the
 * models small; these searches can be very slow on large ones.</p>
 *
 * <p>Truth is a random forward DAG; data are linear Gaussian at the registered simulation defaults, with the columns
 * shuffled (forward DAGs name nodes in causal order and several of these searches start from the data order, so
 * unshuffled columns would hand them the answer). Every configuration sees the same data at a given seed.</p>
 *
 * <p>Marks are scored one endpoint at a time. Against the true PAG: an estimated arrowhead (tail) is right iff the
 * true PAG has that adjacency with an arrowhead (tail) at that endpoint; marks on false adjacencies are wrong.
 * Against the true DAG, the causal reading with no selection bias: an arrowhead at y on x *-> y is right iff y is
 * not an ancestor of x, and a tail at x on x --> y is right iff x is an ancestor of y.</p>
 *
 * <p>Besides plain configurations there are three post-processed kinds, all done here and not in Tetrad proper:</p>
 * <ul>
 * <li>{@code notails:Alg} - every tail demoted to a circle.</li>
 * <li>{@code consensus:Alg} with override lists separated by semicolons - one run per list; an edge is reported iff
 * every run has the adjacency, and an endpoint keeps its arrowhead or tail iff every run has that mark there,
 * otherwise it is a circle.</li>
 * <li>{@code bootew:Alg} with {@code overrides ; tAdj ; tMark} - bootstrap, then endpoint by endpoint: an edge is
 * kept iff it is present in at least tAdj of the resamples, and an arrowhead or tail is kept iff that mark sits at
 * that endpoint in at least tMark of all the resamples (resamples lacking the edge count against it), otherwise it
 * is a circle. {@code bootmaj:Alg} is Tetrad's own Majority composite, for comparison.</li>
 * </ul>
 *
 * <p>The consensus and endpoint-wise graphs are mark-by-mark summaries. They are generally not legal PAGs, and
 * the legal column says how often they happen to be.</p>
 *
 * @author Claude
 */
public class LvSamplePrecisionStudy {

    private static final String PAG_PACKAGE = "edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.";

    /** name, kind:AlgClass, spec. Everything not named in a spec is at its registered default. */
    private static final String[][] CONFIGS = {
            {"FCI default", "Fci", ""},
            {"GFCI default", "Gfci", ""},
            {"FCIT default", "Fcit", ""},
            {"FCIT pd=4", "Fcit", "penaltyDiscount=4"},
            {"FCIT pd=4 no tails", "notails:Fcit", "penaltyDiscount=4"},
            {"FCIT consensus pd 4,2,1", "consensus:Fcit", "penaltyDiscount=4 ; penaltyDiscount=2 ; penaltyDiscount=1"},
            {"FCIT pd=4 boot50 majority", "bootmaj:Fcit", "numberResampling=50,penaltyDiscount=4"},
            {"FCIT pd=4 boot50 endpointwise .5/.9", "bootew:Fcit", "numberResampling=50,penaltyDiscount=4 ; 0.5 ; 0.9"},
            {"BFCI default", "Bfci", ""},
            {"BFCI pd=4", "Bfci", "penaltyDiscount=4"},
            {"BFCI consensus pd 4,2,1", "consensus:Bfci", "penaltyDiscount=4 ; penaltyDiscount=2 ; penaltyDiscount=1"},
            {"BFCI pd=4 boot50 endpointwise .5/.9", "bootew:Bfci", "numberResampling=50,penaltyDiscount=4 ; 0.5 ; 0.9"},
            {"GRaSP-FCI default", "GraspFci", ""},
            {"GRaSP-FCI pd=4", "GraspFci", "penaltyDiscount=4"},
            {"GRaSP-FCI consensus pd 4,2,1", "consensus:GraspFci", "penaltyDiscount=4 ; penaltyDiscount=2 ; penaltyDiscount=1"},
            {"GRaSP-FCI pd=4 boot50 endpointwise .5/.9", "bootew:GraspFci", "numberResampling=50,penaltyDiscount=4 ; 0.5 ; 0.9"},
    };

    public static void main(String[] args) throws Exception {
        long seedBase = args.length > 0 ? Long.parseLong(args[0]) : 101L;
        int runs = arg(args, 1, 5);
        int n = arg(args, 2, 1000);
        int numMeasured = arg(args, 3, 15);
        int numLatents = arg(args, 4, 3);
        int numEdges = arg(args, 5, 20);
        int timeoutSec = arg(args, 6, 180);

        // The searches print diagnostics to System.out whatever verbose says; keep the tables readable.
        final PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        out.printf("LV sample precision study: %d measured + %d latent, %d edges, N = %d, seeds %d..%d%n%n",
                numMeasured, numLatents, numEdges, n, seedBase, seedBase + runs - 1);

        Map<String, List<int[]>> results = new LinkedHashMap<>();
        Map<String, List<String>> failures = new LinkedHashMap<>();
        for (String[] c : CONFIGS) {
            results.put(c[0], new ArrayList<>());
            failures.put(c[0], new ArrayList<>());
        }

        for (int r = 0; r < runs; r++) {
            final long seed = seedBase + r;

            RandomUtil.getInstance().setSeed(seed);
            Graph dag = RandomGraph.randomGraphRandomForwardEdges(numMeasured + numLatents, numLatents, numEdges,
                    100, 100, 100, false, seed);
            SemIm im = new SemIm(new SemPm(dag), new Parameters());
            DataSet full = im.simulateData(n, false);
            List<Node> vars = new ArrayList<>(full.getVariables());
            Collections.shuffle(vars, new Random(seed * 7919 + 13));
            final DataSet data = full.subsetColumns(vars);
            Graph truePag = GraphTransforms.dagToPag(dag, true);

            for (String[] c : CONFIGS) {
                ExecutorService ex = Executors.newSingleThreadExecutor(task -> {
                    Thread th = new Thread(task, "lv-precision-search");
                    th.setDaemon(true);
                    return th;
                });
                Future<Graph> fut = ex.submit(() -> runConfig(c[1], c[2], data, seed));
                try {
                    Graph est = GraphUtils.replaceNodes(fut.get(timeoutSec, TimeUnit.SECONDS), truePag.getNodes());
                    int[] m = metrics(dag, truePag, est);
                    results.get(c[0]).add(m);
                    out.printf("seed %d  %-42s adj %2d/%2d  arrow %2d/%2d  tail %2d/%2d%n", seed, c[0],
                            m[ADJ_TP], m[ADJ_TP] + m[ADJ_FP], m[AH_TP], m[AH_TP] + m[AH_FP], m[T_TP], m[T_TP] + m[T_FP]);
                } catch (TimeoutException e) {
                    fut.cancel(true);
                    failures.get(c[0]).add("seed " + seed + " timed out");
                    out.printf("seed %d  %-42s TIMED OUT after %d s%n", seed, c[0], timeoutSec);
                } catch (ExecutionException e) {
                    failures.get(c[0]).add("seed " + seed + " " + e.getCause());
                    out.printf("seed %d  %-42s FAILED: %s%n", seed, c[0], e.getCause());
                }
                ex.shutdownNow();
            }
        }

        out.printf("%nPooled over runs: marks right / marks claimed. Worst = lowest single-run precision.%n");
        out.printf("%-42s %4s %9s %9s %7s %6s %6s %6s %8s %8s %6s %6s %6s%n", "config", "runs", "adj", "arrow", "tail",
                "AP", "AHP", "TP", "worstAP", "worstAHP", "AR", "AHR", "legal");
        for (String[] c : CONFIGS) {
            List<int[]> ms = results.get(c[0]);
            int[] s = new int[N_FIELDS];
            double worstAp = 1.0, worstAhp = 1.0;
            for (int[] m : ms) {
                for (int i = 0; i < N_FIELDS; i++) s[i] += m[i];
                // A run that claims no edges, or no arrowheads, has no precision to be worst at.
                double ap = ratio(m[ADJ_TP], m[ADJ_FP]), ahp = ratio(m[AH_TP], m[AH_FP]);
                if (!Double.isNaN(ap)) worstAp = Math.min(worstAp, ap);
                if (!Double.isNaN(ahp)) worstAhp = Math.min(worstAhp, ahp);
            }
            out.printf("%-42s %4d %9s %9s %7s %6s %6s %6s %8s %8s %6s %6s %6s%n", c[0], ms.size(),
                    s[ADJ_TP] + "/" + (s[ADJ_TP] + s[ADJ_FP]), s[AH_TP] + "/" + (s[AH_TP] + s[AH_FP]),
                    s[T_TP] + "/" + (s[T_TP] + s[T_FP]),
                    f(ratio(s[ADJ_TP], s[ADJ_FP])), f(ratio(s[AH_TP], s[AH_FP])), f(ratio(s[T_TP], s[T_FP])),
                    f(worstAp), f(worstAhp), f(ratio(s[ADJ_TP], s[ADJ_FN])), f(ratio(s[AH_TP], s[AH_FN])),
                    ms.isEmpty() ? "--" : s[LEGAL] + "/" + ms.size());
        }

        out.printf("%nCausal reading against the true DAG: marks right / marks claimed.%n");
        out.printf("%-42s %9s %9s%n", "config", "arrow", "tail");
        for (String[] c : CONFIGS) {
            int[] s = new int[N_FIELDS];
            for (int[] m : results.get(c[0])) for (int i = 0; i < N_FIELDS; i++) s[i] += m[i];
            out.printf("%-42s %9s %9s%n", c[0], s[SA_TP] + "/" + (s[SA_TP] + s[SA_FP]), s[ST_TP] + "/" + (s[ST_TP] + s[ST_FP]));
        }

        for (String[] c : CONFIGS) {
            if (!failures.get(c[0]).isEmpty()) out.println(c[0] + ": " + failures.get(c[0]));
        }

        System.exit(0);
    }

    private static final int ADJ_TP = 0, ADJ_FP = 1, ADJ_FN = 2, AH_TP = 3, AH_FP = 4, AH_FN = 5, T_TP = 6, T_FP = 7,
            T_FN = 8, SA_TP = 9, SA_FP = 10, ST_TP = 11, ST_FP = 12, LEGAL = 13, N_FIELDS = 14;

    static int[] metrics(Graph dag, Graph truePag, Graph est) {
        int[] m = new int[N_FIELDS];

        for (Edge e : est.getEdges()) {
            Node x = e.getNode1(), y = e.getNode2();
            Edge te = truePag.getEdge(x, y);
            m[te != null ? ADJ_TP : ADJ_FP]++;
            for (int side = 0; side < 2; side++) {
                Node from = side == 0 ? x : y, to = side == 0 ? y : x;   // the mark at 'to'
                Endpoint mark = est.getEndpoint(from, to);
                Endpoint trueMark = te == null ? null : truePag.getEndpoint(from, to);
                if (mark == Endpoint.ARROW) {
                    m[trueMark == Endpoint.ARROW ? AH_TP : AH_FP]++;
                    m[!dag.paths().isAncestorOf(to, from) ? SA_TP : SA_FP]++;
                } else if (mark == Endpoint.TAIL) {
                    m[trueMark == Endpoint.TAIL ? T_TP : T_FP]++;
                    m[dag.paths().isAncestorOf(to, from) ? ST_TP : ST_FP]++;
                }
            }
        }

        for (Edge te : truePag.getEdges()) {
            Node x = te.getNode1(), y = te.getNode2();
            boolean adj = est.isAdjacentTo(x, y);
            if (!adj) m[ADJ_FN]++;
            for (int side = 0; side < 2; side++) {
                Node from = side == 0 ? x : y, to = side == 0 ? y : x;
                Endpoint trueMark = truePag.getEndpoint(from, to);
                Endpoint mark = adj ? est.getEndpoint(from, to) : null;
                if (trueMark == Endpoint.ARROW && mark != Endpoint.ARROW) m[AH_FN]++;
                if (trueMark == Endpoint.TAIL && mark != Endpoint.TAIL) m[T_FN]++;
            }
        }

        try {
            m[LEGAL] = est.paths().isLegalPag() ? 1 : 0;
        } catch (Exception ex) {
            m[LEGAL] = 0;
        }

        return m;
    }

    static Graph runConfig(String cls, String spec, DataSet data, long seed) throws Exception {
        if (!cls.contains(":")) return runPlain(cls, spec, data, seed);
        String kind = cls.substring(0, cls.indexOf(':')), alg = cls.substring(cls.indexOf(':') + 1);
        String[] parts = spec.split(";");

        switch (kind) {
            case "notails": {
                Graph g = new EdgeListGraph(runPlain(alg, spec, data, seed));
                for (Edge e : new ArrayList<>(g.getEdges())) {
                    Node x = e.getNode1(), y = e.getNode2();
                    if (g.getEndpoint(y, x) == Endpoint.TAIL) g.setEndpoint(y, x, Endpoint.CIRCLE);
                    if (g.getEndpoint(x, y) == Endpoint.TAIL) g.setEndpoint(x, y, Endpoint.CIRCLE);
                }
                return g;
            }
            case "consensus": {
                List<Graph> gs = new ArrayList<>();
                for (String p : parts) gs.add(runPlain(alg, p.trim(), data, seed));
                return consensus(gs);
            }
            case "bootew":
            case "bootmaj": {
                Graph r = runPlain(alg, parts[0].trim(), data, seed);
                Graph sampling = ((EdgeListGraph) r).getAncillaryGraph("samplingGraph");
                if (sampling == null) throw new IllegalStateException("The bootstrap returned no sampling graph.");
                if (kind.equals("bootmaj")) {
                    return GraphSampling.createDisplayGraph(sampling, ResamplingEdgeEnsemble.Majority);
                }
                return endpointwise(sampling, Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
            }
            default:
                throw new IllegalArgumentException("Unknown kind: " + kind);
        }
    }

    private static Graph runPlain(String cls, String spec, DataSet data, long seed) throws Exception {
        Parameters params = new Parameters();
        applyOverrides(params, spec);
        RandomUtil.getInstance().setSeed(seed + 1000003L);
        return make(cls).search(data.copy(), params);
    }

    /** The first graph supplies the candidate edges; every other graph can only weaken what it says. */
    static Graph consensus(List<Graph> gs) {
        Graph g1 = gs.getFirst();
        Graph out = new EdgeListGraph(g1.getNodes());

        for (Edge e : g1.getEdges()) {
            Node x = e.getNode1(), y = e.getNode2();
            Endpoint markAtX = g1.getEndpoint(y, x), markAtY = g1.getEndpoint(x, y);
            boolean adjacentInAll = true;

            for (Graph g : gs.subList(1, gs.size())) {
                Node gx = g.getNode(x.getName()), gy = g.getNode(y.getName());
                if (gx == null || gy == null || !g.isAdjacentTo(gx, gy)) {
                    adjacentInAll = false;
                    break;
                }
                if (markAtX != g.getEndpoint(gy, gx)) markAtX = Endpoint.CIRCLE;
                if (markAtY != g.getEndpoint(gx, gy)) markAtY = Endpoint.CIRCLE;
            }

            if (adjacentInAll) out.addEdge(new Edge(x, y, markAtX, markAtY));
        }

        return out;
    }

    /**
     * Endpoint-wise bootstrap composite from a sampling graph. In an edge-type name the first letter is the mark at
     * node 1 and the second the mark at node 2.
     */
    static Graph endpointwise(Graph sampling, double tAdj, double tMark) {
        Graph out = new EdgeListGraph(sampling.getNodes());

        for (Edge e : sampling.getEdges()) {
            List<EdgeTypeProbability> etps = e.getEdgeTypeProbabilities();
            if (etps == null || etps.isEmpty()) continue;
            double nil = 0, arrow1 = 0, tail1 = 0, arrow2 = 0, tail2 = 0, total = 0;

            for (EdgeTypeProbability etp : etps) {
                double p = etp.getProbability();
                total += p;
                switch (etp.getEdgeType()) {
                    case nil -> nil += p;
                    case ta -> {
                        tail1 += p;
                        arrow2 += p;
                    }
                    case at -> {
                        arrow1 += p;
                        tail2 += p;
                    }
                    case ca -> arrow2 += p;
                    case ac -> arrow1 += p;
                    case aa -> {
                        arrow1 += p;
                        arrow2 += p;
                    }
                    case tt -> {
                        tail1 += p;
                        tail2 += p;
                    }
                    default -> {
                    }
                }
            }

            if (total - nil < tAdj) continue;
            Endpoint m1 = arrow1 >= tMark ? Endpoint.ARROW : tail1 >= tMark ? Endpoint.TAIL : Endpoint.CIRCLE;
            Endpoint m2 = arrow2 >= tMark ? Endpoint.ARROW : tail2 >= tMark ? Endpoint.TAIL : Endpoint.CIRCLE;
            out.addEdge(new Edge(e.getNode1(), e.getNode2(), m1, m2));
        }

        return out;
    }

    private static Algorithm make(String cls) throws Exception {
        Class<?> k = Class.forName(PAG_PACKAGE + cls);
        IndependenceWrapper test = new FisherZ();
        ScoreWrapper score = new SemBicScore();

        for (Constructor<?> ct : k.getConstructors()) {
            Class<?>[] p = ct.getParameterTypes();
            if (p.length == 2 && p[0] == IndependenceWrapper.class && p[1] == ScoreWrapper.class) {
                return (Algorithm) ct.newInstance(test, score);
            }
        }
        for (Constructor<?> ct : k.getConstructors()) {
            Class<?>[] p = ct.getParameterTypes();
            if (p.length == 1 && p[0] == IndependenceWrapper.class) return (Algorithm) ct.newInstance(test);
            if (p.length == 1 && p[0] == ScoreWrapper.class) return (Algorithm) ct.newInstance(score);
        }

        throw new IllegalArgumentException("No usable constructor for " + cls);
    }

    private static void applyOverrides(Parameters params, String spec) {
        if (spec == null || spec.isBlank()) return;
        for (String kv : spec.split(",")) {
            String[] p = kv.trim().split("=");
            String v = p[1].trim();
            Object val;
            if (v.equals("true") || v.equals("false")) val = Boolean.parseBoolean(v);
            else if (v.matches("-?\\d+")) val = Integer.parseInt(v);
            else if (v.matches("-?\\d*\\.\\d+")) val = Double.parseDouble(v);
            else val = v;
            params.set(p[0].trim(), val);
        }
    }

    private static double ratio(int a, int b) {
        return a + b == 0 ? Double.NaN : a / (double) (a + b);
    }

    private static String f(double v) {
        return Double.isNaN(v) ? "--" : String.format("%.2f", v);
    }

    private static int arg(String[] args, int i, int dflt) {
        return args.length > i ? Integer.parseInt(args[i]) : dflt;
    }
}
