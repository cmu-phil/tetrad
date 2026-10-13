///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.RicfEjml;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exact Gaussian BIC for a MAG (more generally, for any ADMG over the data's variables with directed and
 * bidirected edges only), computed by maximum likelihood via RICF ({@link RicfEjml}), with a district-wise
 * decomposition and a cache so that searches re-scoring many similar graphs pay only for the districts
 * that changed.
 *
 * <p><b>The score.</b> {@code score(mag) = 2 * logLik(mag) - penaltyDiscount * numEdges * log(n)}, higher
 * is better. The likelihood is the Gaussian log-likelihood at the RICF-fitted implied covariance, constants
 * included, so on a DAG this agrees with {@link edu.cmu.tetrad.search.score.SemBicScorer#scoreDag} exactly.
 * Following {@link edu.cmu.tetrad.search.score.SemBicScore}, only edge parameters are penalized; the p
 * error-variance parameters are present in every model over the same variables and so contribute a constant,
 * which is omitted.</p>
 *
 * <p><b>Score equivalence.</b> Markov equivalent MAGs parameterize the same set of Gaussian covariance
 * matrices (Richardson &amp; Spirtes, 2002) and have the same number of edges, so this score is constant on a
 * Markov equivalence class: scoring a PAG means scoring any representative MAG. (Numerically, equality holds
 * to RICF's convergence tolerance.)</p>
 *
 * <p><b>The decomposition.</b> With {@code B} the directed-edge coefficients and {@code Omega} the error
 * covariance (support = bidirected edges plus the diagonal), the density factors exactly as a product over
 * districts D (connected components of the bidirected part) of local factors
 * {@code f_D(y_D, y_pa(D))}, each the Gaussian density of the district's error vector
 * {@code eps_D = (I - B)_{D,.} y} &mdash; a change of variables with unit Jacobian. The factors have disjoint
 * parameters (the B-rows for D and the Omega-block for D), so the maximized log-likelihood is the sum of the
 * maximized factors. Each factor's maximum is computed as
 * {@code ll(G_D) - llSaturated(pa(D))}, where {@code G_D} is the subgraph over {@code D + pa(D)} consisting
 * of the directed edges into D, the bidirected edges within D, and a complete DAG over {@code pa(D)}
 * (saturating the parents' marginal, whose contribution then cancels exactly). Note the subtraction is an
 * identity about likelihood factors; it does not require {@code eps_D} to be independent of
 * {@code y_pa(D)}, which can fail when a parent of the district is also a descendant of it.</p>
 *
 * <p>Singleton districts with parents are fitted in closed form (OLS), bypassing RICF.</p>
 *
 * <p><b>Cache.</b> District subproblems are keyed by their variable names and edges; since the covariance
 * matrix is fixed per scorer instance, a district whose local subgraph is unchanged between two scored
 * graphs is never refitted.</p>
 *
 * <p><b>Caveats, stated plainly.</b> RICF converges to a solution of the likelihood equations, which for
 * MAG models may be a local maximum; the score is exact only up to that and to the convergence tolerance.
 * The graph's directed part must be acyclic and its edges all directed or bidirected; ancestrality and
 * maximality are not checked here (RICF fits any such ADMG), so legality is the caller's responsibility
 * when the score is to be read as a MAG score.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see RicfEjml
 */
@Experimental
public class MagRicfBicScorer {

    /**
     * The covariance matrix all scoring is done against.
     */
    private final ICovarianceMatrix cov;

    /**
     * Sample size.
     */
    private final int n;

    /**
     * log(n), cached.
     */
    private final double logN;

    /**
     * Variable name to covariance index, built once; getValue lookups go through it.
     */
    private final Map<String, Integer> indexByName = new HashMap<>();

    /**
     * Cache of district subproblem fits, keyed by a canonical description of the subproblem
     * (district variables, parent variables, and the local edges). Values are {logLik, edgeCount}.
     */
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    /**
     * Number of district subproblems answered from the cache, for diagnostics.
     */
    private final AtomicLong cacheHits = new AtomicLong();

    /**
     * Number of district subproblems actually fitted, for diagnostics.
     */
    private final AtomicLong cacheMisses = new AtomicLong();

    /**
     * Multiplier on the BIC penalty term.
     */
    private double penaltyDiscount = 1.0;

    /**
     * RICF convergence tolerance.
     */
    private double tolerance = 1e-10;

    /**
     * RICF maximum sweeps.
     */
    private int maxIters = RicfEjml.DEFAULT_MAX_ITERS;

    /**
     * When true, {@link #score(Graph)} skips the per-call node replacement and validation (the graph
     * copy and the cycle check), trusting the caller to supply graphs whose nodes are the covariance
     * variables and whose edges are directed or bidirected with an acyclic directed part. Searches
     * that maintain those invariants themselves (such as the order projection) set this; the per-call
     * copy and cycle check otherwise dominate the cost of candidate scans.
     */
    private boolean skipChecks = false;

    /**
     * Constructs the scorer for a covariance matrix.
     *
     * @param cov the sample covariance matrix; must be positive definite on every scored submatrix.
     */
    public MagRicfBicScorer(ICovarianceMatrix cov) {
        if (cov == null) throw new NullPointerException("Covariance matrix not specified.");
        this.cov = cov;
        this.n = cov.getSampleSize();
        this.logN = Math.log(this.n);

        List<String> names = cov.getVariableNames();
        for (int i = 0; i < names.size(); i++) this.indexByName.put(names.get(i), i);
    }

    /**
     * Constructs the scorer for a continuous data set (its covariance matrix is taken).
     *
     * @param dataSet a continuous data set.
     */
    public MagRicfBicScorer(DataSet dataSet) {
        this(new CovarianceMatrix(Objects.requireNonNull(dataSet, "Data set not specified.")));
    }

    /**
     * Scores the given MAG/ADMG: {@code 2 * logLik - penaltyDiscount * numEdges * log(n)}, higher is better.
     * Computed district by district with caching.
     *
     * @param mag the graph; directed and bidirected edges only, directed part acyclic. Nodes are matched to
     *            the covariance variables by name.
     * @return the BIC score.
     */
    public double score(Graph mag) {
        double[] llAndDof = logLikAndDof(mag);
        return 2.0 * llAndDof[0] - this.penaltyDiscount * llAndDof[1] * this.logN;
    }

    /**
     * The maximized Gaussian log-likelihood of the graph, constants included, computed district by district
     * with caching.
     *
     * @param mag the graph; directed and bidirected edges only, directed part acyclic.
     * @return the log-likelihood.
     */
    public double logLikelihood(Graph mag) {
        return logLikAndDof(mag)[0];
    }

    /**
     * The number of penalized parameters of the graph: its number of edges. (Error variances are a constant
     * p for every model over these variables and are omitted, as in SemBicScore.)
     *
     * @param mag the graph.
     * @return the number of edges.
     */
    public int dof(Graph mag) {
        return mag.getNumEdges();
    }

    /**
     * Scores the graph with a single RICF fit of the whole graph, no decomposition and no cache. Intended
     * for validation against {@link #score(Graph)}; the two agree up to RICF's convergence tolerance.
     *
     * @param mag the graph; directed and bidirected edges only, directed part acyclic.
     * @return the BIC score.
     */
    public double fullScore(Graph mag) {
        Graph g;
        if (this.skipChecks) {
            if (mag == null) throw new NullPointerException("Graph not specified.");
            g = mag;
        } else {
            g = replaced(mag);
            validate(g);
        }
        RicfEjml.RicfResult r = new RicfEjml().ricf(g, this.cov, this.tolerance, this.maxIters);
        return 2.0 * r.getLogLik() - this.penaltyDiscount * g.getNumEdges() * this.logN;
    }

    // ------------------------------------------------------------------
    // Decomposition
    // ------------------------------------------------------------------

    private double[] logLikAndDof(Graph mag) {
        Graph g;
        if (this.skipChecks) {
            if (mag == null) throw new NullPointerException("Graph not specified.");
            g = mag;
        } else {
            g = replaced(mag);
            validate(g);
        }

        double ll = 0.0;
        int dof = 0;

        for (List<Node> district : districts(g)) {
            double[] entry = districtEntry(g, district);
            ll += entry[0];
            dof += (int) entry[1];
        }

        return new double[]{ll, dof};
    }

    /**
     * The districts of the graph: connected components of its bidirected part, singletons included.
     * Deterministic order (components discovered in sorted-name order; members sorted by name).
     */
    private List<List<Node>> districts(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        Set<Node> seen = new HashSet<>();
        List<List<Node>> out = new ArrayList<>();

        for (Node start : nodes) {
            if (seen.contains(start)) continue;

            List<Node> comp = new ArrayList<>();
            Deque<Node> stack = new ArrayDeque<>();
            stack.push(start);
            seen.add(start);

            while (!stack.isEmpty()) {
                Node v = stack.pop();
                comp.add(v);

                for (Edge e : g.getEdges(v)) {
                    if (Edges.isBidirectedEdge(e)) {
                        Node w = e.getDistalNode(v);
                        if (!seen.contains(w)) {
                            seen.add(w);
                            stack.push(w);
                        }
                    }
                }
            }

            comp.sort(Comparator.comparing(Node::getName));
            out.add(comp);
        }

        return out;
    }

    /**
     * The {log-likelihood contribution, edge count} of one district, from the cache when possible.
     */
    private double[] districtEntry(Graph g, List<Node> district) {
        Set<Node> dSet = new HashSet<>(district);

        // Parents of the district, outside it.
        SortedSet<String> parentNames = new TreeSet<>();
        for (Node d : district) {
            for (Node par : g.getParents(d)) {
                if (!dSet.contains(par)) parentNames.add(par.getName());
            }
        }

        // The local edges: directed into the district, bidirected within it. Canonical strings for the key.
        SortedSet<String> edgeSpecs = new TreeSet<>();
        for (Node d : district) {
            for (Node par : g.getParents(d)) {
                edgeSpecs.add(par.getName() + ">" + d.getName());
            }
            for (Edge e : g.getEdges(d)) {
                if (Edges.isBidirectedEdge(e)) {
                    Node w = e.getDistalNode(d);
                    String a = d.getName(), b = w.getName();
                    edgeSpecs.add(a.compareTo(b) < 0 ? a + "<>" + b : b + "<>" + a);
                }
            }
        }

        List<String> dNames = district.stream().map(Node::getName).toList();
        String key = "D=" + dNames + "|P=" + parentNames + "|E=" + edgeSpecs;

        double[] cached = this.cache.get(key);
        if (cached != null) {
            this.cacheHits.incrementAndGet();
            return cached;
        }
        this.cacheMisses.incrementAndGet();

        double[] entry = fitDistrict(g, district, new ArrayList<>(parentNames));
        this.cache.put(key, entry);
        return entry;
    }

    /**
     * Fits one district subproblem and returns {log-likelihood contribution, edge count}.
     *
     * <p>The contribution is {@code ll(G_D) - llSaturated(parents)}, with {@code G_D} the graph over the
     * district and its parents described in the class Javadoc. A singleton district with parents is fitted
     * in closed form by OLS.</p>
     */
    private double[] fitDistrict(Graph g, List<Node> district, List<String> parentNames) {
        int edgeCount = 0;
        Set<Node> dSet = new HashSet<>(district);

        for (Node d : district) {
            edgeCount += g.getParents(d).size();
            for (Edge e : g.getEdges(d)) {
                if (Edges.isBidirectedEdge(e) && dSet.contains(e.getDistalNode(d))) edgeCount++;
            }
        }
        // Each bidirected edge within the district was counted at both endpoints.
        int bidirWithin = 0;
        for (Node d : district) {
            for (Edge e : g.getEdges(d)) {
                if (Edges.isBidirectedEdge(e) && dSet.contains(e.getDistalNode(d))) bidirWithin++;
            }
        }
        edgeCount -= bidirWithin / 2;

        // ---- singleton district: closed-form OLS conditional likelihood ----
        if (district.size() == 1) {
            Node v = district.getFirst();
            double ll = olsConditionalLogLik(v.getName(), parentNames);
            return new double[]{ll, edgeCount};
        }

        // ---- general district: RICF on G_D, minus the saturated parents ----
        List<String> subNames = new ArrayList<>(district.stream().map(Node::getName).toList());
        subNames.addAll(parentNames);

        ICovarianceMatrix subCov = this.cov.getSubmatrix(subNames.toArray(new String[0]));

        // Build G_D on fresh nodes named like the originals.
        Map<String, Node> byName = new HashMap<>();
        List<Node> subNodes = new ArrayList<>();
        for (String name : subNames) {
            Node node = new GraphNode(name);
            byName.put(name, node);
            subNodes.add(node);
        }
        Graph gd = new EdgeListGraph(subNodes);

        for (Node d : district) {
            Node dSub = byName.get(d.getName());
            for (Node par : g.getParents(d)) {
                gd.addEdge(Edges.directedEdge(byName.get(par.getName()), dSub));
            }
            for (Edge e : g.getEdges(d)) {
                if (Edges.isBidirectedEdge(e)) {
                    Node w = e.getDistalNode(d);
                    if (dSet.contains(w) && d.getName().compareTo(w.getName()) < 0) {
                        gd.addEdge(Edges.bidirectedEdge(dSub, byName.get(w.getName())));
                    }
                }
            }
        }

        // Complete DAG over the parents, saturating their marginal. Its likelihood contribution is the
        // saturated likelihood of the parents, which is subtracted below; any order does.
        for (int i = 0; i < parentNames.size(); i++) {
            for (int j = i + 1; j < parentNames.size(); j++) {
                gd.addEdge(Edges.directedEdge(byName.get(parentNames.get(i)), byName.get(parentNames.get(j))));
            }
        }

        RicfEjml.RicfResult r = new RicfEjml().ricf(gd, subCov, this.tolerance, this.maxIters);
        double ll = r.getLogLik() - saturatedLogLik(parentNames);

        return new double[]{ll, edgeCount};
    }

    /**
     * Closed-form conditional Gaussian log-likelihood of one variable given its parents, at the OLS fit:
     * {@code -(n/2) * (log(2 pi sigma^2) + 1)} with {@code sigma^2} the residual variance from the
     * covariance matrix. Matches SemBicScore's likelihood term.
     */
    private double olsConditionalLogLik(String v, List<String> parents) {
        double s2;

        if (parents.isEmpty()) {
            s2 = value(v, v);
        } else {
            int k = parents.size();
            SimpleMatrix spp = new SimpleMatrix(k, k);
            SimpleMatrix spv = new SimpleMatrix(k, 1);
            for (int i = 0; i < k; i++) {
                spv.set(i, 0, value(parents.get(i), v));
                for (int j = 0; j < k; j++) {
                    spp.set(i, j, value(parents.get(i), parents.get(j)));
                }
            }
            s2 = value(v, v) - spv.transpose().mult(spp.solve(spv)).get(0, 0);
        }

        if (!(s2 > 0.0)) {
            throw new IllegalArgumentException("Nonpositive residual variance for " + v + " given " + parents
                                               + "; the covariance submatrix is singular.");
        }

        return -0.5 * this.n * (Math.log(2.0 * Math.PI * s2) + 1.0);
    }

    /**
     * Saturated Gaussian log-likelihood of the given variables: {@code -(n/2) * (log|S| + k * (1 + log(2 pi)))}.
     * Zero for an empty set.
     */
    private double saturatedLogLik(List<String> names) {
        int k = names.size();
        if (k == 0) return 0.0;

        SimpleMatrix s = new SimpleMatrix(k, k);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                s.set(i, j, value(names.get(i), names.get(j)));
            }
        }

        double logDet = logDetSPD(s, names);
        return -0.5 * this.n * (logDet + k * (1.0 + Math.log(2.0 * Math.PI)));
    }

    private double value(String a, String b) {
        Integer i = this.indexByName.get(a);
        Integer j = this.indexByName.get(b);
        if (i == null || j == null) {
            throw new IllegalArgumentException("Variable not in the covariance matrix: " + (i == null ? a : b));
        }
        return this.cov.getValue(i, j);
    }

    private static double logDetSPD(SimpleMatrix a, List<String> names) {
        int k = a.numRows();
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(k, true);
        if (chol.decompose(a.getDDRM().copy())) {
            DMatrixRMaj l = chol.getT(null);
            double logDet = 0.0;
            for (int i = 0; i < k; i++) logDet += 2.0 * Math.log(l.get(i, i));
            return logDet;
        }
        throw new IllegalArgumentException("Covariance submatrix over " + names + " is not positive definite.");
    }

    // ------------------------------------------------------------------
    // Validation and bookkeeping
    // ------------------------------------------------------------------

    /**
     * Matches the graph's nodes to the covariance variables by name, as SemBicScorer does.
     */
    private Graph replaced(Graph mag) {
        if (mag == null) throw new NullPointerException("Graph not specified.");
        return GraphUtils.replaceNodes(mag, this.cov.getVariables());
    }

    private void validate(Graph g) {
        for (Node node : g.getNodes()) {
            if (!this.cov.getVariableNames().contains(node.getName())) {
                throw new IllegalArgumentException("Graph node not among the covariance variables: " + node.getName());
            }
        }

        for (Edge e : g.getEdges()) {
            if (!Edges.isDirectedEdge(e) && !Edges.isBidirectedEdge(e)) {
                throw new IllegalArgumentException("Only directed and bidirected edges can be scored; found " + e
                                                   + ". (Undirected edges would need the selection-bias "
                                                   + "parameterization, which this scorer does not implement.)");
            }
        }

        if (g.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException("The directed part of the graph is cyclic; this scorer is for "
                                               + "MAGs/ADMGs with an acyclic directed part.");
        }
    }

    /**
     * Returns the multiplier on the BIC penalty term.
     *
     * @return the penalty discount.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * Sets the multiplier on the BIC penalty term.
     *
     * @param penaltyDiscount the penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the RICF convergence tolerance.
     *
     * @param tolerance a positive tolerance on the entrywise L1 parameter change.
     */
    public void setTolerance(double tolerance) {
        if (!(tolerance > 0.0)) throw new IllegalArgumentException("Tolerance must be positive.");
        this.tolerance = tolerance;
    }

    /**
     * Sets the RICF maximum number of sweeps.
     *
     * @param maxIters a positive sweep cap.
     */
    public void setMaxIters(int maxIters) {
        if (maxIters < 1) throw new IllegalArgumentException("Max iterations must be at least 1.");
        this.maxIters = maxIters;
    }

    /**
     * Turns the trusted-caller fast path on or off; see the field. Default off.
     *
     * @param skipChecks true to skip the per-call node replacement and validation.
     */
    public void setSkipChecks(boolean skipChecks) {
        this.skipChecks = skipChecks;
    }

    /**
     * Whether the trusted-caller fast path is on.
     *
     * @return true iff per-call node replacement and validation are skipped.
     */
    public boolean isSkipChecks() {
        return this.skipChecks;
    }

    /**
     * Clears the district cache.
     */
    public void clearCache() {
        this.cache.clear();
        this.cacheHits.set(0);
        this.cacheMisses.set(0);
    }

    /**
     * The number of district subproblems answered from the cache since construction or the last clear.
     *
     * @return the hit count.
     */
    public long getCacheHits() {
        return this.cacheHits.get();
    }

    /**
     * The number of district subproblems actually fitted since construction or the last clear.
     *
     * @return the miss count.
     */
    public long getCacheMisses() {
        return this.cacheMisses.get();
    }

    /**
     * The sample size of the covariance matrix.
     *
     * @return the sample size.
     */
    public int getSampleSize() {
        return this.n;
    }

    /**
     * The variables of the covariance matrix all scoring is done against.
     *
     * @return the variables.
     */
    public List<Node> getVariables() {
        return this.cov.getVariables();
    }
}
