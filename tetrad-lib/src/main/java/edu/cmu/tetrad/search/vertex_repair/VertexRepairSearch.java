package edu.cmu.tetrad.search.vertex_repair;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.*;

public class VertexRepairSearch implements IGraphSearch {

    private static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // 0) Guards first (true before false)
        if (a.passesGuards() != b.passesGuards()) {
            return a.passesGuards() ? -1 : 1;
        }
        if (!a.passesGuards()) {
            return stableTieBreak(a, b);
        }

        // 1) Δ violations (more negative is better)
        int c = Integer.compare(a.delta(), b.delta()); // ASC
        if (c != 0) return c;

        // 2) Fewer edges preferred
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;

        // 3) Smaller edit size preferred (single-edge before multi-edge)
        c = Integer.compare(editSize(a), editSize(b));
        if (c != 0) return c;

        // 4) Node-P: FINITE first, then log-odds DESC
        c = finiteFirst(a.nodePAfter(), b.nodePAfter());
        if (c != 0) return c;

        double npA = nodeLogOdds(a);
        double npB = nodeLogOdds(b);
        c = -Double.compare(npA, npB);
        if (c != 0) return c;

        // 5) Model-P improvement over baseline (dMp DESC)
        // (Optional but recommended: finite improvement beats "unknown improvement")
        c = finiteFirst(modelDeltaValueOrNaN(a), modelDeltaValueOrNaN(b));
        if (c != 0) return c;

        double dMpA = modelDelta(a);
        double dMpB = modelDelta(b);
        c = -Double.compare(dMpA, dMpB);
        if (c != 0) return c;

        // 6) Move-type bias (your existing heuristic)
        c = -Integer.compare(moveBiasScore(a), moveBiasScore(b)); // DESC
        if (c != 0) return c;

        // 7) Absolute Model-P: FINITE first, then log-odds DESC
        c = finiteFirst(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;

        double mpA = modelLogOdds(a);
        double mpB = modelLogOdds(b);
        c = -Double.compare(mpA, mpB);
        if (c != 0) return c;

        // 8) Stable tie-break
        return stableTieBreak(a, b);
    };

    private static int stableTieBreak(ScoredCandidate a, ScoredCandidate b) {
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        int c = ka.compareTo(kb);
        if (c != 0) return c;

        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    }

    private static int editSize(ScoredCandidate s) {
        try {
            if (s.edit() != null && s.edit().getEdges() != null) {
                return Math.max(1, s.edit().getEdges().size());
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    private static int finiteFirst(double a, double b) {
        boolean fa = Double.isFinite(a);
        boolean fb = Double.isFinite(b);
        if (fa == fb) return 0;
        return fa ? -1 : 1; // finite first
    }

    static final double alpha = 0.01;

    private static double nodeLogOdds(ScoredCandidate s) {
        double p = s.nodePAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    private static double alphaLogOdds(double p, double alpha) {
        if (!Double.isFinite(p)) return -50.0;
        if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0)
            throw new IllegalArgumentException("alpha must be in (0,1)");

        final double eps = 1e-12;

        double q = Math.min(1.0 - eps, Math.max(eps, p));
        double a = Math.min(1.0 - eps, Math.max(eps, alpha));

        // log(p/(1-p)) - log(alpha/(1-alpha))
        return (Math.log(q) - Math.log(1.0 - q))
                - (Math.log(a) - Math.log(1.0 - a));
    }

    private static double modelDeltaValueOrNaN(ScoredCandidate s) {
        if (s == null) return Double.NaN;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        return (Double.isFinite(before) && Double.isFinite(after)) ? (after - before) : Double.NaN;
    }

    private static double modelDelta(ScoredCandidate s) {
        if (s == null) return 0.0;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        if (Double.isFinite(before) && Double.isFinite(after)) {
            return after - before;
        }
        return 0.0;
    }

    private static int moveBiasScore(ScoredCandidate s) {
        MoveType mt = moveType(s.edit());
        double dMp = modelDelta(s);

        if (Double.isFinite(dMp) && dMp > 0.0) {
            if (mt == MoveType.REORIENT_SIMPLE) return 2;
            if (mt == MoveType.COLLIDER_FIX) return -1;
        } else if (!Double.isFinite(s.modelPAfter())) {
            if (mt == MoveType.REORIENT_SIMPLE) return 1;
            if (mt == MoveType.COLLIDER_FIX) return -1;
        }

        return 0;
    }

    private enum MoveType {
        REORIENT_SIMPLE,   // single-edge replace/orient/flip (low-risk)
        COLLIDER_FIX,      // multi-edge "Orient collider..." / "Orient away..." (higher-risk)
        REMOVE_EDGE,
        ADD_EDGE,
        OTHER
    }

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;

        String k = safeLower(e.key());
        String d = safeLower(e.description());
        String s = (k + " " + d).trim();

        // Explicit add/remove first (unambiguous)
        if (containsAny(s, "rem:") || containsAny(s, "remove", "delete")) return MoveType.REMOVE_EDGE;
        if (containsAny(s, "add:") || containsAny(s, "add", "insert")) return MoveType.ADD_EDGE;

        // Collider fixes (usually MULTI:... and description starts with "Orient collider" / "Orient away from collider")
        if (containsAny(s, "orient collider", "orient away from collider")) {
            return MoveType.COLLIDER_FIX;
        }

        // Simple reorientation: typically REP:... and/or "replace" with same endpoints (orientation change)
        // We don’t try to prove it’s “orientation-only” here; we just prioritize these moves over collider moves.
        if (containsAny(s, "rep:") || containsAny(s, "replace", "reorient", "orient", "flip", "reverse", "endpoint")) {
            return MoveType.REORIENT_SIMPLE;
        }

        return MoveType.OTHER;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (n != null && !n.isEmpty() && s.contains(n)) return true;
        return false;
    }

    private static double modelLogOdds(ScoredCandidate s) {
        double p = s.modelPAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    private record ScoredCandidate(
            CandidateEdit edit,
            int violationsBaseline,
            int violationsAfter,
            double nodePAfter,
            double modelPBefore,
            double modelPAfter,
            int edgesAfter,
            boolean passesGuards
    ) {
        int delta() {
            return violationsAfter - violationsBaseline;
        }
    }

    public interface CandidateEdit {

        static CandidateEdit noOp() {
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "No change";
                }

                @Override
                public Graph applyTo(Graph g) {
                    return (g == null) ? null : new EdgeListGraph(g);
                }

                @Override
                public boolean isNoOp() {
                    return true;
                }

                @Override
                public String key() {
                    return "NO_OP";
                }

                @Override
                public Edge getEdge() {
                    return null;
                }
            };
        }

        static CandidateEdit addEdge(Edge edgeToAdd) {
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Add edge " + edgeToAdd;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound == null) return g2;

                    g2.addEdge(rebound);
                    return g2;
                }

                @Override
                public String key() {
                    return "ADD:" + stableEdgeKey(edgeToAdd);
                }

                @Override
                public Edge getEdge() {
                    return edgeToAdd;
                }
            };
        }

        private static Edge rebindEdgeToGraph(Graph g, Edge e) {
            if (g == null || e == null) return null;

            Node a0 = e.getNode1();
            Node b0 = e.getNode2();
            if (a0 == null || b0 == null) return null;

            String an = a0.getName();
            String bn = b0.getName();
            if (an == null || bn == null) return null;

            Node a = g.getNode(an);
            Node b = g.getNode(bn);
            if (a == null || b == null) return null;

            // Preserve endpoint-at-node semantics, regardless of node order
            Endpoint ea = e.getEndpoint(a0);
            Endpoint eb = e.getEndpoint(b0);
            return new Edge(a, b, ea, eb);
        }

        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Remove edge " + edgeToRemove;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge e = getEdgeByNames(g2, edgeToRemove);
                    if (e != null) g2.removeEdge(e);

                    return g2;
                }

                @Override
                public String key() {
                    return "REM:" + stableEdgeKey(edgeToRemove);
                }

                @Override
                public Edge getEdge() {
                    return edgeToRemove;
                }
            };
        }

        private static Edge getEdgeByNames(Graph g, Edge e) {
            if (g == null || e == null) return null;
            String a = e.getNode1() == null ? null : e.getNode1().getName();
            String b = e.getNode2() == null ? null : e.getNode2().getName();
            if (a == null || b == null) return null;
            Node ga = g.getNode(a);
            Node gb = g.getNode(b);
            if (ga == null || gb == null) return null;
            return g.getEdge(ga, gb);
        }

        static CandidateEdit replaceEdge(Edge oldEdge, Edge newEdge) {
            Objects.requireNonNull(oldEdge, "oldEdge");
            Objects.requireNonNull(newEdge, "newEdge");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Replace " + oldEdge + " with " + newEdge;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge eOld = getEdgeByNames(g2, oldEdge);
                    if (eOld != null) g2.removeEdge(eOld);

                    Edge eNew = rebindEdgeToGraph(g2, newEdge);
                    if (eNew != null) g2.addEdge(eNew);

                    return g2;
                }

                @Override
                public String key() {
                    return "REP:" + stableEdgeKey(oldEdge) + "->" + stableEdgeKey(newEdge);
                }

                @Override
                public Edge getEdge() {
                    return newEdge;
                }
            };
        }

        /**
         * Multi-edge replace: removes every old edge’s pair, then adds every new edge.
         */
        static CandidateEdit replaceEdges(String label, List<Edge> oldEdges, List<Edge> newEdges) {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(oldEdges, "oldEdges");
            Objects.requireNonNull(newEdges, "newEdges");

            // defensively copy for stable key/description
            List<Edge> olds = List.copyOf(oldEdges);
            List<Edge> news = List.copyOf(newEdges);

            return new CandidateEdit() {
                @Override
                public String description() {
                    return label;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    // remove by *names* (node identity differs across graph copies)
                    for (Edge oe : olds) {
                        if (oe == null) continue;

                        Node a0 = oe.getNode1();
                        Node b0 = oe.getNode2();
                        if (a0 == null || b0 == null) continue;

                        String an = a0.getName();
                        String bn = b0.getName();
                        if (an == null || bn == null) continue;

                        Node a = g2.getNode(an);
                        Node b = g2.getNode(bn);
                        if (a == null || b == null) continue;

                        Edge e = g2.getEdge(a, b);
                        if (e != null) g2.removeEdge(e);
                    }

                    for (Edge ne : news) {
                        if (ne == null) continue;
                        Edge rebound = rebindEdgeToGraph(g2, ne);
                        if (rebound != null) g2.addEdge(rebound);
                    }

                    return g2;
                }

                @Override
                public String key() {
                    List<String> parts = new ArrayList<>();
                    for (Edge oe : olds) parts.add("O:" + stableEdgeKey(oe));
                    for (Edge ne : news) parts.add("N:" + stableEdgeKey(ne));
                    Collections.sort(parts);
                    return "MULTI:" + label + ":" + String.join("|", parts);
                }

                /** For legacy code paths; return first “new” edge if any. */
                @Override
                public Edge getEdge() {
                    return news.isEmpty() ? null : news.getFirst();
                }

                @Override
                public List<Edge> getEdges() {
                    return news;
                }
            };
        }

        private static String stableEdgeKey(Edge e) {
            if (e == null) return "null";
            Node a = e.getNode1();
            Node b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            Endpoint ea = e.getEndpoint1();
            Endpoint eb = e.getEndpoint2();
            return an + ":" + bn + ":" + ea + ":" + eb;
        }

        String description();

        Graph applyTo(Graph g);

        default boolean isNoOp() {
            return false;
        }

        default String key() {
            return description();
        }

        /**
         * Legacy single-edge accessor.
         */
        Edge getEdge();

        /**
         * New multi-edge accessor (defaults to singleton or empty).
         */
        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }
    }


    public VertexRepairSearch(IndependenceTest test, Graph start, Knowledge knowledge,
                              ConditioningSetType conditioningSetType) {

    }

    @Override
    public Graph search() throws InterruptedException {
        return null;
    }
}