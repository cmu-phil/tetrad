/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.ejml.data.DMatrixRMaj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An estimator of an identified total effect when the causal structure is known only up to a partial ancestral
 * graph (PAG), so that latent confounding is allowed. Carries over the idea behind G-regression, not the
 * construction.
 * <p>
 * <b>What this is, and what it is not.</b> {@link GRegression} implements Guo and Perković (2022) for an MPDAG
 * under causal sufficiency. That estimator is not a covariate adjustment: the buckets of the MPDAG give a
 * block-triangular coefficient matrix Lambda whose blocks are ordinary least squares fits of each bucket on its
 * external parents, and the total effect is read off {@code Lambda_{A,D} (I - Lambda_{D,D})^{-1}} (their
 * Definition 13, Eq. 19). It is the Gaussian maximum likelihood estimate of the graph's coefficient matrix,
 * plugged into the identification formula, and its advantage over adjustment comes from using every constraint
 * the graph puts on the covariance matrix rather than one marginal regression. Two things make that work, and
 * <i>both</i> fail under latent confounding:
 * <ol>
 * <li>The closed form for Lambda exists because of the restrictive property (their Corollary 4), which holds
 * because an MPDAG is closed under Meek's rules: every node of a bucket has the same external parents. Their
 * Remark 12 says that when the zero constraints on Lambda violate that property the simple form is unavailable
 * and the likelihood can be multimodal, citing the seemingly-unrelated-regressions literature. Bidirected edges
 * are exactly that case, and Gaussian ancestral graph models are curved exponential families (Richardson and
 * Spirtes, 2002, Section 8.4). A PAG has no bucket decomposition with the restrictive property, so there is no
 * Lambda to estimate.</li>
 * <li>In a MAG, the coefficient of an <i>invisible</i> directed edge is not a causal parameter: it conflates the
 * direct effect with latent confounding. So a path-product or {@code (I - B)^{-1}} reading of a fitted MAG is
 * wrong even when the effect is identified. (Example: the DAG {@code L -> M, L -> Y, X -> M -> Y} with {@code L}
 * latent has MAG {@code X -> M -> Y}, in which {@code M -> Y} is invisible; the fitted coefficient of
 * {@code M -> Y} is not {@code beta_MY}, while regressing {@code Y} on {@code X} does recover the total
 * effect.)</li>
 * </ol>
 * What is left is the principle. Fit the Gaussian ancestral graph model by Residual Iterative Conditional Fitting
 * to get the maximum likelihood estimate {@code Sigma-hat}, then evaluate an adjustment functional (the
 * generalized adjustment criterion of Perković, Textor, Kalisch and Maathuis, 2018) at {@code Sigma-hat} instead
 * of at the sample covariance {@code S}:
 * <pre>
 *     PAG --(amenability check)--&gt; Zhang MAG --(RICF)--&gt; Sigma-hat --(any valid adjustment set)--&gt; tau
 * </pre>
 * Use {@link #totalEffectSampleCov(Node, Node)} for the ordinary adjustment estimator with the same adjustment
 * set, which is what Tetrad's "Linear Adjustment Effects" tool computes, and is the baseline this class has to
 * beat to be worth anything. {@code PagSigmaHatHarness} runs that comparison.
 * <p>
 * <b>Status of the claims.</b> Stated separately on purpose.
 * <ul>
 * <li><i>Proved.</i> Consistency, given that the PAG is correct, the PAG is amenable with respect to (X, Y), the
 * adjustment set returned satisfies the generalized adjustment criterion, and RICF returns a point of the model.
 * This does not need Gaussian errors: the Gaussian likelihood is being used as a quasi-likelihood whose
 * estimating equations involve only first and second moments, so a correctly specified covariance structure gives
 * a consistent {@code Sigma-hat} whatever the error distribution. (Standard for covariance-structure models; not
 * a citation to the ancestral graph literature.)</li>
 * <li><i>Proved, and checked numerically by {@link #adjustmentSpread(Node, Node, int)}.</i> The estimate does not
 * depend on <i>which</i> valid adjustment set is used, since validity holds throughout the model and
 * {@code Sigma-hat} lies in the model. This is the property a single-adjustment-set estimator lacks, and it is
 * where any efficiency gain comes from. It holds only to the accuracy with which RICF has converged, so a large
 * spread is a convergence diagnostic rather than a refutation.</li>
 * <li><i>Follows from published results, not proved here.</i> The estimate does not depend on which MAG in the
 * Markov equivalence class is fitted: Markov equivalent ancestral graphs are different parameterizations of the
 * same Gaussian model and have identical likelihoods (Ali, Richardson and Spirtes, 2009), so the MLE
 * {@code Sigma-hat} is a function of the PAG. The caveat is real: the likelihood of a Gaussian ancestral graph
 * model may be multimodal (Drton and Richardson, 2004), so RICF returns a local maximum and two MAGs in one class
 * could in principle land in different modes. {@link #getLogLik()} is exposed so this can be compared across
 * MAGs from {@link GraphTransforms#zhangMagsFromPag(Graph)}.</li>
 * <li><i>Conjectured, and Gaussian only.</i> Efficiency. Under Gaussian errors this is the maximum likelihood
 * plug-in for a smooth identified functional, so by the delta method it should be efficient among regular
 * estimators based on {@code S}, and in particular asymptotically no worse than any fixed valid adjustment set,
 * including the optimal adjustment set where that exists. We have not proved it and know of no published proof
 * for the PAG case.</li>
 * <li><i>Expected to be false.</i> The distribution-free part of Guo and Perković's Theorem 15. Their efficiency
 * result holds without assuming Gaussianity because the effect is a function of an unconstrained block of
 * {@code Sigma} (a bucket's OLS coefficients). Under latent confounding the effect is a function of a constrained
 * part of the model, so the optimal way to combine the entries of {@code S} depends on fourth moments. Expect the
 * Gaussian MLE plug-in to remain consistent but not to be efficient under non-Gaussian errors. Since
 * distribution-freeness is the striking part of their theorem, this is the main thing that does not carry
 * over.</li>
 * </ul>
 * <b>Scope limits of this implementation.</b> Point interventions only. Joint interventions would need the
 * set-valued generalized adjustment criterion, and they are the case where adjustment can fail to exist at all
 * (Guo and Perković's completeness desideratum), so they are where a PAG analogue would be worth the most and
 * where the theory is thinnest. Effects identified but not by adjustment -- for instance by the PAG do-calculus
 * of Jaber, Zhang and Bareinboim -- are also out of scope; adjustment-completeness and identification-
 * completeness come apart once there are latents. PAGs with undirected edges (selection bias) are refused, since
 * RICF here fits no undirected block. A non-amenable (X, Y) is reported, with a witness path, rather than
 * estimated.
 * <p>
 * References:
 * <ul>
 * <li>Guo, F. R., and Perković, E. (2022). Efficient least squares for estimating total effects under linearity
 * and causal sufficiency. Journal of Machine Learning Research, 23(104), 1-41.</li>
 * <li>Perković, E., Textor, J., Kalisch, M., and Maathuis, M. H. (2018). Complete graphical characterization and
 * construction of adjustment sets in Markov equivalence classes of ancestral graphs. Journal of Machine Learning
 * Research, 18(220), 1-62.</li>
 * <li>Richardson, T., and Spirtes, P. (2002). Ancestral graph Markov models. Annals of Statistics, 30(4),
 * 962-1030.</li>
 * <li>Drton, M., and Richardson, T. S. (2004). Multimodality of the likelihood in the bivariate seemingly
 * unrelated regressions model. Biometrika, 91(2), 383-392.</li>
 * <li>Ali, R. A., Richardson, T. S., and Spirtes, P. (2009). Markov equivalence for ancestral graphs. Annals of
 * Statistics, 37(5B), 2808-2837.</li>
 * <li>Malinsky, D., and Spirtes, P. (2017). Estimating causal effects with ancestral graph Markov models.
 * Proceedings of Machine Learning Research (PGM), 52, 299-309.</li>
 * </ul>
 *
 * @author josephramsey
 * @see GRegression
 * @see RicfEjml
 * @see PdagPagIda
 */
public final class GRegressionPag {

    /**
     * The validated PAG.
     */
    private final Graph pag;
    /**
     * The MAG in the PAG's Markov equivalence class that was fitted (Zhang 2008, Theorem 2).
     */
    private final Graph mag;
    /**
     * The sample (or population) covariance matrix.
     */
    private final ICovarianceMatrix cov;
    /**
     * PAG nodes in covariance-variable order.
     */
    private final List<Node> nodes;
    /**
     * PAG node to covariance index.
     */
    private final Map<Node, Integer> index = new HashMap<>();
    /**
     * The RICF maximum likelihood estimate of Sigma under the Gaussian ancestral graph model, indexed like the
     * covariance matrix.
     */
    private final Matrix sigmaHat;
    /**
     * Gaussian log likelihood at the fit.
     */
    private final double logLik;
    /**
     * RICF sweeps used.
     */
    private final int iterations;
    /**
     * Final RICF parameter change.
     */
    private final double fitDiff;
    /**
     * RICF tolerance, kept so {@link #converged()} can compare against it.
     */
    private final double tolerance;
    /**
     * Nodes b for which an edge X -&gt; b is treated as visible by fiat. Setting this to all nodes recovers the
     * causally sufficient reading of the graph.
     */
    private Set<Node> forceVisibility = Set.of();
    /**
     * Maximum path length for the amenability and adjustment searches; -1 for no limit.
     */
    private int maxPathLength = -1;

    /**
     * Constructs the estimator with default RICF settings.
     *
     * @param pag A PAG over the covariance matrix's variables.
     * @param cov A covariance matrix (variables matched to graph nodes by name).
     */
    public GRegressionPag(Graph pag, ICovarianceMatrix cov) {
        this(pag, cov, RicfEjml.DEFAULT_TOL, RicfEjml.DEFAULT_MAX_ITERS);
    }

    /**
     * Constructs the estimator, fitting the Gaussian ancestral graph model once.
     *
     * @param pag      A PAG whose edges are all of the form o-o, o-&gt;, --&gt; or &lt;-&gt;.
     * @param cov      A covariance matrix over the same variables (matched by name); must be positive definite.
     * @param tol      RICF convergence tolerance.
     * @param maxIters RICF maximum sweeps.
     * @throws IllegalArgumentException if the graph is not a usable PAG or the variables do not match.
     */
    public GRegressionPag(Graph pag, ICovarianceMatrix cov, double tol, int maxIters) {
        if (pag == null) throw new IllegalArgumentException("Graph is null.");
        if (cov == null) throw new IllegalArgumentException("Covariance matrix is null.");

        String problem = pagProblem(pag);
        if (problem != null) {
            throw new IllegalArgumentException("Graph is not a PAG this estimator can use: " + problem);
        }

        this.pag = pag;
        this.cov = cov;
        this.tolerance = tol;

        List<Node> covVars = cov.getVariables();
        this.nodes = new ArrayList<>(covVars.size());

        for (int i = 0; i < covVars.size(); i++) {
            Node covVar = covVars.get(i);
            Node graphNode = pag.getNode(covVar.getName());

            if (graphNode == null) {
                throw new IllegalArgumentException("Covariance variable " + covVar.getName()
                                                  + " is not a node in the graph.");
            }

            this.nodes.add(graphNode);
            this.index.put(graphNode, i);
        }

        if (this.index.size() != pag.getNumNodes()) {
            throw new IllegalArgumentException("The graph has nodes that are not covariance variables.");
        }

        // Any MAG in the class will do; see the class documentation on invariance.
        this.mag = GraphTransforms.zhangMagFromPag(pag);

        for (Edge edge : this.mag.getEdges()) {
            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge))) {
                throw new IllegalStateException("The MAG obtained from the PAG has edge " + edge
                                               + ", which is neither directed nor bidirected; the input is "
                                               + "probably not a legal PAG.");
            }
        }

        RicfEjml.RicfResult result = new RicfEjml().ricf(this.mag, cov, tol, maxIters);
        DMatrixRMaj s = result.getSigmaHat();

        int p = this.nodes.size();
        Matrix sigma = new Matrix(p, p);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                sigma.set(i, j, s.get(i, j));
            }
        }

        this.sigmaHat = sigma;
        this.logLik = result.getLogLik();
        this.iterations = result.getIterations();
        this.fitDiff = result.getDiff();
    }

    // -------------------------------------------------------------------------------------------------------------
    // Graph validation.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Returns null if the graph can be used by this estimator, or else a description of why it cannot. The edges
     * must all be o-o, o-&gt;, --&gt; or &lt;-&gt;. Undirected edges are refused: they encode selection bias, which
     * the RICF fit here does not model. This is a cheap edge-type screen, not a full PAG legality check; call
     * {@code graph.paths().isLegalPag()} for that.
     *
     * @param graph The graph.
     * @return null if usable; otherwise a reason.
     */
    public static String pagProblem(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                return "edge " + edge + " is undirected (selection bias), which this estimator does not model.";
            }

            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge)
                  || Edges.isNondirectedEdge(edge) || Edges.isPartiallyOrientedEdge(edge))) {
                return "edge " + edge + " is not of a PAG edge type.";
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------------------------------------------
    // Identification.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Returns true if the PAG is amenable with respect to (x, y), i.e. every proper possibly directed path from x
     * to y starts with a visible directed edge out of x. Under linearity this is the condition for the total
     * effect to be identified by covariate adjustment (Perković et al., 2018).
     *
     * @param x The treatment.
     * @param y The outcome.
     * @return True if amenable.
     */
    public boolean isAmenable(Node x, Node y) {
        Node a = resolve(x);
        Node b = resolve(y);
        return new RecursiveAdjustment(pag).isGraphAmenable(a, b, "PAG", maxPathLength, forceVisibility);
    }

    /**
     * Returns true if x is a possible ancestor of y in the PAG. If not, every DAG in the class gives x no effect
     * on y and {@link #totalEffect(Node, Node)} returns zero without needing amenability.
     *
     * @param x The treatment.
     * @param y The outcome.
     * @return True if some possibly directed path runs from x to y.
     */
    public boolean isPossibleAncestor(Node x, Node y) {
        return pag.paths().possibleAncestor(resolve(x), resolve(y));
    }

    /**
     * Returns a witness to non-amenability: a possibly directed path from x to y whose first edge is not a visible
     * directed edge out of x. Returns null if the PAG is amenable with respect to (x, y) or if x is not a possible
     * ancestor of y. This is the diagnostic analogue of {@link GRegression#nonIdentificationWitness(Graph, Set,
     * Node)}: the missing information is the status of that first edge, so background knowledge making it a
     * visible directed edge (or ruling the path out) is what would make the effect estimable.
     * <p>
     * Enumerates possibly directed paths, so it can be slow on a dense graph; set {@link
     * #setMaxPathLength(int)} first if that matters.
     *
     * @param x The treatment.
     * @param y The outcome.
     * @return A witness path, or null.
     */
    public List<Node> nonAmenabilityWitness(Node x, Node y) {
        Node a = resolve(x);
        Node b = resolve(y);

        if (isAmenable(a, b)) return null;

        for (List<Node> path : pag.paths().potentiallyDirectedPaths(a, b, maxPathLength)) {
            if (path.size() < 2) continue;
            Node first = path.get(1);
            Edge edge = pag.getEdge(a, first);
            boolean visibleOut = edge != null && edge.pointsTowards(first)
                                 && (pag.paths().defVisiblePag(a, first) || forceVisibility.contains(first));
            if (!visibleOut) return path;
        }

        return null;
    }

    /**
     * Returns a valid adjustment set for (x, y) in the PAG, or null if none is found. Uses the same recursive
     * adjustment machinery and settings as {@link PdagPagIda}: one set, full radius, hugging both endpoints,
     * non-colliders first.
     *
     * @param x The treatment.
     * @param y The outcome.
     * @return An adjustment set, or null.
     */
    public Set<Node> adjustmentSet(Node x, Node y) {
        List<Set<Node>> sets = adjustmentSets(x, y, 1);
        return sets.isEmpty() ? null : sets.getFirst();
    }

    /**
     * Returns up to {@code maxNumSets} valid adjustment sets for (x, y) in the PAG.
     *
     * @param x          The treatment.
     * @param y          The outcome.
     * @param maxNumSets The maximum number of sets to return.
     * @return The adjustment sets found, possibly empty.
     */
    public List<Set<Node>> adjustmentSets(Node x, Node y, int maxNumSets) {
        Node a = resolve(x);
        Node b = resolve(y);

        return new RecursiveAdjustment(pag).adjustmentSets(a, b, "PAG", maxNumSets,
                pag.getNumNodes(), 3, maxPathLength,
                RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST, true, null, null, forceVisibility);
    }

    // -------------------------------------------------------------------------------------------------------------
    // Estimation.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Returns the estimated total effect of x on y: the adjustment functional evaluated at the fitted
     * {@code Sigma-hat} rather than at the sample covariance.
     *
     * @param x The treatment.
     * @param y The outcome, not equal to x.
     * @return The estimated total effect; exactly zero if x is not a possible ancestor of y.
     * @throws IllegalArgumentException if the PAG is not amenable with respect to (x, y), or no adjustment set is
     *                                  found.
     */
    public double totalEffect(Node x, Node y) {
        return totalEffect(x, y, true);
    }

    /**
     * Returns the same estimate computed from the sample covariance instead of {@code Sigma-hat}, using the same
     * adjustment set: the ordinary adjustment estimator, provided for comparison. Both are consistent; the point
     * of this class is that this one should have the larger variance.
     *
     * @param x The treatment.
     * @param y The outcome.
     * @return The ordinary least squares adjustment estimate.
     */
    public double totalEffectSampleCov(Node x, Node y) {
        return totalEffect(x, y, false);
    }

    private double totalEffect(Node x, Node y, boolean useSigmaHat) {
        Node a = resolve(x);
        Node b = resolve(y);

        if (a.equals(b)) throw new IllegalArgumentException("Outcome must not be the treatment.");

        if (!isPossibleAncestor(a, b)) return 0.0;

        if (!isAmenable(a, b)) {
            throw new IllegalArgumentException("The PAG is not amenable with respect to (" + a + ", " + b
                                               + "), so the total effect is not identified by adjustment.");
        }

        Set<Node> z = adjustmentSet(a, b);

        if (z == null) {
            throw new IllegalArgumentException("No valid adjustment set was found for (" + a + ", " + b + ").");
        }

        return effectFrom(useSigmaHat ? sigmaHat : cov.getMatrix(), a, z, b);
    }

    /**
     * Diagnostic: the spread (maximum minus minimum) of the estimate over up to {@code maxNumSets} valid
     * adjustment sets, all evaluated at {@code Sigma-hat}. In exact arithmetic with a converged fit this is zero,
     * which is the property that distinguishes this estimator from adjustment on the sample covariance. A spread
     * that is not near zero indicates that RICF has not converged, or that one of the sets does not satisfy the
     * generalized adjustment criterion.
     *
     * @param x          The treatment.
     * @param y          The outcome.
     * @param maxNumSets The number of adjustment sets to compare.
     * @return The spread, or 0.0 if fewer than two sets were found.
     */
    public double adjustmentSpread(Node x, Node y, int maxNumSets) {
        Node a = resolve(x);
        Node b = resolve(y);

        List<Set<Node>> sets = adjustmentSets(a, b, maxNumSets);
        if (sets.size() < 2) return 0.0;

        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;

        for (Set<Node> z : sets) {
            double tau = effectFrom(sigmaHat, a, z, b);
            lo = Math.min(lo, tau);
            hi = Math.max(hi, tau);
        }

        return hi - lo;
    }

    /**
     * The coefficient of x in the least squares regression of y on {x} union z, computed from the given
     * covariance matrix.
     */
    private double effectFrom(Matrix sigma, Node x, Set<Node> z, Node y) {
        List<Node> w = new ArrayList<>();
        w.add(x);

        for (Node node : z) {
            Node resolved = resolve(node);
            if (!resolved.equals(x) && !resolved.equals(y)) w.add(resolved);
        }

        int m = w.size();
        Matrix sww = new Matrix(m, m);
        Matrix swy = new Matrix(m, 1);

        for (int i = 0; i < m; i++) {
            int wi = index.get(w.get(i));
            for (int j = 0; j < m; j++) {
                sww.set(i, j, sigma.get(wi, index.get(w.get(j))));
            }
            swy.set(i, 0, sigma.get(wi, index.get(y)));
        }

        return sww.solve(swy).get(0, 0);
    }

    // -------------------------------------------------------------------------------------------------------------
    // Bootstrap.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The result of a bootstrapped estimate.
     *
     * @param treatment     The treatment.
     * @param outcome       The outcome.
     * @param effect        The estimate on the full sample.
     * @param standardError The bootstrap standard error.
     * @param numFailed     Replications discarded because the fit or the adjustment search failed.
     */
    public record BootstrapResult(Node treatment, Node outcome, double effect, double standardError,
                                  int numFailed) {
    }

    /**
     * Estimates a total effect on the full data set and its standard error by the nonparametric bootstrap. Each
     * replication refits the ancestral graph model, so this costs one RICF fit per replication; no standard error
     * formula for this estimator is available in closed form.
     *
     * @param pag           The PAG.
     * @param data          A continuous data set over the graph's variables.
     * @param x             The treatment.
     * @param y             The outcome.
     * @param numBootstraps The number of replications (at least 2).
     * @return The estimate and its bootstrap standard error.
     */
    public static BootstrapResult bootstrap(Graph pag, DataSet data, Node x, Node y, int numBootstraps) {
        if (numBootstraps < 2) throw new IllegalArgumentException("Need at least 2 bootstrap replications.");

        double effect = new GRegressionPag(pag, new CovarianceMatrix(data)).totalEffect(x, y);

        int n = data.getNumRows();
        List<Double> draws = new ArrayList<>();
        RandomUtil random = RandomUtil.getInstance();
        int numFailed = 0;

        for (int b = 0; b < numBootstraps; b++) {
            int[] rows = new int[n];
            for (int i = 0; i < n; i++) rows[i] = random.nextInt(n);

            try {
                DataSet resampled = data.subsetRows(rows);
                draws.add(new GRegressionPag(pag, new CovarianceMatrix(resampled)).totalEffect(x, y));
            } catch (Exception e) {
                numFailed++;
            }
        }

        if (draws.size() < 2) {
            throw new IllegalStateException("Too few bootstrap replications succeeded.");
        }

        double mean = 0.0;
        for (double d : draws) mean += d / draws.size();

        double var = 0.0;
        for (double d : draws) var += (d - mean) * (d - mean) / (draws.size() - 1);

        return new BootstrapResult(x, y, effect, Math.sqrt(var), numFailed);
    }

    // -------------------------------------------------------------------------------------------------------------
    // Accessors.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Returns a copy of the fitted Sigma-hat, indexed in the covariance matrix's variable order.
     *
     * @return The fitted covariance matrix.
     */
    public Matrix getSigmaHat() {
        return new Matrix(sigmaHat);
    }

    /**
     * Returns the MAG that was fitted.
     *
     * @return The MAG.
     */
    public Graph getMag() {
        return mag;
    }

    /**
     * Returns the PAG.
     *
     * @return The PAG.
     */
    public Graph getPag() {
        return pag;
    }

    /**
     * Returns the Gaussian log likelihood at the fit. Comparable across MAGs in the same Markov equivalence
     * class, which is how to check that RICF has not landed in different modes for different MAGs.
     *
     * @return The log likelihood.
     */
    public double getLogLik() {
        return logLik;
    }

    /**
     * Returns the number of RICF sweeps used.
     *
     * @return The iteration count.
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * Returns the final RICF parameter change.
     *
     * @return The change at the last sweep.
     */
    public double getFitDiff() {
        return fitDiff;
    }

    /**
     * Returns true if the RICF fit met its tolerance. If false, treat the estimates as unconverged; the
     * adjustment-set invariance the estimator relies on holds only at a point of the model.
     *
     * @return True if converged.
     */
    public boolean converged() {
        return fitDiff <= tolerance;
    }

    /**
     * Returns the nodes b for which an edge X -&gt; b is treated as visible by fiat.
     *
     * @return The forced-visibility set.
     */
    public Set<Node> getForceVisibility() {
        return forceVisibility;
    }

    /**
     * Sets nodes b for which a directed edge X -&gt; b is to be treated as visible, as background knowledge that
     * X and b are not confounded. Passing all of the graph's nodes amounts to assuming causal sufficiency, under
     * which this estimator should agree with {@link GRegression}.
     *
     * @param forceVisibility The nodes; may be empty, not null.
     */
    public void setForceVisibility(Set<Node> forceVisibility) {
        if (forceVisibility == null) throw new IllegalArgumentException("Set is null.");
        Set<Node> resolved = new LinkedHashSet<>();
        for (Node node : forceVisibility) resolved.add(resolve(node));
        this.forceVisibility = resolved;
    }

    /**
     * Returns the maximum path length used by the amenability and adjustment searches.
     *
     * @return The maximum path length, -1 for no limit.
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * Sets the maximum path length for the amenability and adjustment searches. Use a finite value on dense
     * graphs, where enumerating possibly directed paths is expensive; note that a limit can only make the
     * amenability check more permissive, so it trades correctness for speed.
     *
     * @param maxPathLength The maximum path length, or -1 for no limit.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) throw new IllegalArgumentException("Max path length must be -1 or nonnegative.");
        this.maxPathLength = maxPathLength;
    }

    /**
     * The variable order indexing {@link #getSigmaHat()}: the covariance matrix's order, mapped to graph nodes.
     *
     * @return The nodes in Sigma-hat's index order.
     */
    public List<Node> getVariableOrder() {
        return List.copyOf(nodes);
    }

    // -------------------------------------------------------------------------------------------------------------
    // Helpers.
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Maps a node (possibly a data variable with the same name) to the corresponding graph node.
     */
    private Node resolve(Node node) {
        if (node == null) throw new IllegalArgumentException("Node is null.");
        if (index.containsKey(node)) return node;
        Node byName = pag.getNode(node.getName());
        if (byName == null || !index.containsKey(byName)) {
            throw new IllegalArgumentException("Node " + node.getName() + " is not a variable of this estimator.");
        }
        return byName;
    }
}
