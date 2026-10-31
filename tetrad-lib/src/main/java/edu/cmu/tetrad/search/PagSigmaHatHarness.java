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
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.ejml.data.DMatrixRMaj;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides, empirically, whether evaluating a covariate-adjustment functional at the RICF-fitted covariance matrix
 * beats evaluating it at the sample covariance matrix, when the causal structure is known as a PAG.
 * <p>
 * The question this answers. Covariate adjustment in a PAG uses one adjustment set and one regression, and so
 * discards the other constraints the PAG imposes on the covariance matrix. The alternative is to fit the Gaussian
 * ancestral graph model by RICF, obtaining the maximum likelihood estimate Sigma-hat, and to evaluate the same
 * adjustment functional there. That is the only route by which the idea behind G-regression -- use the whole
 * model-implied covariance rather than one marginal regression (Guo and Perković, 2022) -- can be carried to the
 * PAG case, since the bucket decomposition their estimator relies on has no PAG analogue. Under Gaussian errors
 * the fitted estimator should be at least as efficient; the point of this harness is to find out whether the
 * margin is large enough to be worth having. If the ratio comes out near one, there is no case for it.
 * <p>
 * What is held fixed, so that only the estimator is being compared. Both estimators are given the <i>same</i>
 * adjustment set, found once per pair in the true PAG, and the same data. The true PAG is used rather than the
 * output of a search, so that search error does not enter; errors are Gaussian, which is where the fitted
 * estimator's claim is strongest. Both are therefore consistent, and the comparison is purely one of variance.
 * Only pairs amenable with respect to the PAG are used, since neither estimator is defined otherwise.
 * <p>
 * What is reported. The root mean squared error of each estimator against the true total effect, their ratio,
 * and -- following Table 1 of Guo and Perković -- the geometric mean and median of the per-pair ratio of squared
 * errors. Also the spread of the estimate across several valid adjustment sets, which should be zero at
 * Sigma-hat and is generally not at S: that spread is the mechanism of any gain, so if it is near zero at S too,
 * the pairs being drawn are not discriminating and the simulation needs denser graphs or smaller samples.
 * Finally the bookkeeping: pairs skipped as not amenable, pairs with no adjustment set found, replications with
 * no bidirected edge in the PAG, and RICF failures to converge.
 * <p>
 * This class is a {@code main}; it fits no model into Tetrad's search interfaces and nothing calls it.
 * <pre>
 *   java edu.cmu.tetrad.search.PagSigmaHatHarness --reps 20 --n 500 --nodes 14 --latents 3 --edges 24
 * </pre>
 * Options: {@code --reps}, {@code --n} (sample size), {@code --nodes} (total, including latents),
 * {@code --latents}, {@code --edges}, {@code --seed}, {@code --maxSets} (adjustment sets for the spread
 * diagnostic), {@code --tol} and {@code --maxIters} (RICF), and {@code --allowNoArcs} to keep replications whose
 * PAG has no bidirected edge (by default those are skipped, since they are not the case of interest).
 *
 * @author josephramsey
 * @see RicfEjml
 * @see RecursiveAdjustment
 */
public final class PagSigmaHatHarness {

    /**
     * Private constructor; this class is a main.
     */
    private PagSigmaHatHarness() {
    }

    /**
     * Runs the comparison.
     *
     * @param args see the class documentation.
     * @throws ParseException if {@link SemIm#simulateData(int, boolean)} does, which it declares.
     */
    public static void main(String[] args) throws ParseException {
        int reps = 20;
        int n = 500;
        int nodes = 14;
        int latents = 3;
        int edges = 24;
        long seed = 1848291L;
        int maxSets = 5;
        double tol = RicfEjml.DEFAULT_TOL;
        int maxIters = RicfEjml.DEFAULT_MAX_ITERS;
        boolean allowNoArcs = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--reps" -> reps = Integer.parseInt(args[++i]);
                case "--n" -> n = Integer.parseInt(args[++i]);
                case "--nodes" -> nodes = Integer.parseInt(args[++i]);
                case "--latents" -> latents = Integer.parseInt(args[++i]);
                case "--edges" -> edges = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--maxSets" -> maxSets = Integer.parseInt(args[++i]);
                case "--tol" -> tol = Double.parseDouble(args[++i]);
                case "--maxIters" -> maxIters = Integer.parseInt(args[++i]);
                case "--allowNoArcs" -> allowNoArcs = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        RandomUtil.getInstance().setSeed(seed);

        System.out.printf(Locale.ROOT,
                "PAG Sigma-hat vs S, same adjustment set.%n"
                + "reps=%d n=%d nodes=%d (latents=%d) edges=%d seed=%d maxSets=%d%n%n",
                reps, n, nodes, latents, edges, seed, maxSets);
        System.out.println("Each pair: regress y on {x} + Z, once with S and once with the RICF fit Sigma-hat.");
        System.out.println("Truth is the total effect in the generating DAG. Gaussian errors throughout.");
        System.out.println();

        double sseS = 0.0;
        double sseSigma = 0.0;
        int pairs = 0;

        List<Double> ratios = new ArrayList<>();
        double spreadSumS = 0.0;
        double spreadSumSigma = 0.0;
        int spreadPairs = 0;

        int skippedNoArcs = 0;
        int skippedBadMag = 0;
        int notAmenable = 0;
        int noAdjustmentSet = 0;
        int ricfNotConverged = 0;

        for (int rep = 1; rep <= reps; rep++) {
            Graph dag = RandomGraph.randomDag(nodes, latents, edges, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            List<Node> all = im.getVariableNodes();
            Matrix truth = Matrix.identity(all.size()).minus(new Matrix(im.getEdgeCoef())).inverse();

            Graph pag = GraphTransforms.dagToPag(dag, false);

            int arcs = 0;
            for (Edge edge : pag.getEdges()) if (Edges.isBidirectedEdge(edge)) arcs++;

            if (arcs == 0 && !allowNoArcs) {
                skippedNoArcs++;
                System.out.printf(Locale.ROOT, "rep %3d: skipped, no bidirected edge in the PAG%n", rep);
                continue;
            }

            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            boolean magOk = true;
            for (Edge edge : mag.getEdges()) {
                if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge))) magOk = false;
            }

            if (!magOk) {
                skippedBadMag++;
                System.out.printf(Locale.ROOT, "rep %3d: skipped, MAG from PAG has an unusable edge%n", rep);
                continue;
            }

            DataSet data = im.simulateData(n, false);
            ICovarianceMatrix cov = new CovarianceMatrix(data);
            Matrix s = cov.getMatrix();

            Map<String, Integer> idx = new HashMap<>();
            List<String> names = cov.getVariableNames();
            for (int i = 0; i < names.size(); i++) idx.put(names.get(i), i);

            RicfEjml.RicfResult fit = new RicfEjml().ricf(mag, cov, tol, maxIters);
            if (fit.getDiff() > tol) ricfNotConverged++;

            DMatrixRMaj fitted = fit.getSigmaHat();
            int p = names.size();
            Matrix sigmaHat = new Matrix(p, p);
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < p; j++) sigmaHat.set(i, j, fitted.get(i, j));
            }

            double repSseS = 0.0;
            double repSseSigma = 0.0;
            int repPairs = 0;

            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y)) continue;
                    if (!pag.paths().possibleAncestor(x, y)) continue;

                    if (!pag.paths().isGraphAmenable(x, y, "PAG", -1, Set.of())) {
                        notAmenable++;
                        continue;
                    }

                    List<Set<Node>> sets = adjustmentSets(pag, x, y, maxSets);

                    if (sets.isEmpty()) {
                        noAdjustmentSet++;
                        continue;
                    }

                    Set<Node> z = sets.get(0);
                    double tauS = effect(s, idx, x, z, y);
                    double tauSigma = effect(sigmaHat, idx, x, z, y);
                    double t = truth.get(all.indexOf(dag.getNode(x.getName())),
                            all.indexOf(dag.getNode(y.getName())));

                    double eS = (tauS - t) * (tauS - t);
                    double eSigma = (tauSigma - t) * (tauSigma - t);

                    repSseS += eS;
                    repSseSigma += eSigma;
                    repPairs++;

                    if (eS > 0.0) ratios.add(eSigma / eS);

                    if (sets.size() >= 2) {
                        spreadSumS += spread(s, idx, x, sets, y);
                        spreadSumSigma += spread(sigmaHat, idx, x, sets, y);
                        spreadPairs++;
                    }
                }
            }

            sseS += repSseS;
            sseSigma += repSseSigma;
            pairs += repPairs;

            if (repPairs == 0) {
                System.out.printf(Locale.ROOT, "rep %3d: arcs=%2d, no usable pairs%n", rep, arcs);
            } else {
                System.out.printf(Locale.ROOT,
                        "rep %3d: arcs=%2d pairs=%3d  rmse(S)=%.4f  rmse(Sigma-hat)=%.4f  ratio=%.3f  "
                        + "ricf iters=%d%n",
                        rep, arcs, repPairs, Math.sqrt(repSseS / repPairs),
                        Math.sqrt(repSseSigma / repPairs),
                        Math.sqrt(repSseSigma / Math.max(repSseS, 1e-300)), fit.getIterations());
            }
        }

        System.out.println();
        System.out.println("---- summary ----");

        if (pairs == 0) {
            System.out.println("No usable pairs. Try more replications, denser graphs, or --allowNoArcs.");
            return;
        }

        double rmseS = Math.sqrt(sseS / pairs);
        double rmseSigma = Math.sqrt(sseSigma / pairs);

        System.out.printf(Locale.ROOT, "pairs compared:            %d%n", pairs);
        System.out.printf(Locale.ROOT, "RMSE, adjustment at S:     %.5f%n", rmseS);
        System.out.printf(Locale.ROOT, "RMSE, adjustment at Sigma: %.5f%n", rmseSigma);
        System.out.printf(Locale.ROOT, "RMSE ratio (Sigma/S):      %.4f   (< 1 favors Sigma-hat)%n",
                rmseSigma / rmseS);

        if (!ratios.isEmpty()) {
            List<Double> sorted = new ArrayList<>(ratios);
            Collections.sort(sorted);
            double logSum = 0.0;
            for (double r : ratios) logSum += Math.log(Math.max(r, 1e-300));
            System.out.printf(Locale.ROOT,
                    "per-pair squared-error ratio: geometric mean %.4f, median %.4f (n=%d)%n",
                    Math.exp(logSum / ratios.size()), sorted.get(sorted.size() / 2), ratios.size());
        }

        if (spreadPairs > 0) {
            System.out.printf(Locale.ROOT,
                    "mean spread over valid adjustment sets: at S %.5f, at Sigma-hat %.3g (%d pairs)%n",
                    spreadSumS / spreadPairs, spreadSumSigma / spreadPairs, spreadPairs);
            System.out.println("  The spread at Sigma-hat should be ~0: every valid set agrees there, which is");
            System.out.println("  where any gain comes from. A spread near 0 at S too means the pairs drawn are");
            System.out.println("  not discriminating -- use denser graphs or a smaller sample.");
        } else {
            System.out.println("No pair had two or more adjustment sets, so the spread diagnostic is empty.");
        }

        System.out.printf(Locale.ROOT,
                "skipped: %d reps with no bidirected edge, %d with an unusable MAG%n",
                skippedNoArcs, skippedBadMag);
        System.out.printf(Locale.ROOT,
                "pairs dropped: %d not amenable, %d with no adjustment set found%n",
                notAmenable, noAdjustmentSet);
        System.out.printf(Locale.ROOT, "RICF did not reach tolerance in %d replication(s)%n", ricfNotConverged);
        System.out.println();
        System.out.println("Read this as: a ratio near 1 means the fitted covariance buys nothing here and the");
        System.out.println("existing Linear Adjustment Effects tool is all that is needed. Note that these are");
        System.out.println("Gaussian errors, which is the case most favorable to Sigma-hat; under non-Gaussian");
        System.out.println("errors the fitted estimator stays consistent but loses its efficiency argument.");
    }

    /**
     * Up to maxNumSets valid adjustment sets for (x, y) in the PAG, with the settings PdagPagIda uses.
     */
    private static List<Set<Node>> adjustmentSets(Graph pag, Node x, Node y, int maxNumSets) {
        try {
            return new RecursiveAdjustment(pag).adjustmentSets(x, y, "PAG", maxNumSets,
                    pag.getNumNodes(), 3, -1,
                    RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST, true, null, null, Set.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * The coefficient of x in the least squares regression of y on {x} union z, computed from the given
     * covariance matrix.
     */
    private static double effect(Matrix sigma, Map<String, Integer> idx, Node x, Set<Node> z, Node y) {
        List<String> w = new ArrayList<>();
        w.add(x.getName());

        Set<String> seen = new LinkedHashSet<>();
        seen.add(x.getName());
        seen.add(y.getName());

        for (Node node : z) {
            if (seen.add(node.getName())) w.add(node.getName());
        }

        int m = w.size();
        Matrix sww = new Matrix(m, m);
        Matrix swy = new Matrix(m, 1);

        for (int i = 0; i < m; i++) {
            int wi = idx.get(w.get(i));
            for (int j = 0; j < m; j++) sww.set(i, j, sigma.get(wi, idx.get(w.get(j))));
            swy.set(i, 0, sigma.get(wi, idx.get(y.getName())));
        }

        return sww.solve(swy).get(0, 0);
    }

    /**
     * The spread (maximum minus minimum) of the estimate over the given adjustment sets, at one covariance
     * matrix.
     */
    private static double spread(Matrix sigma, Map<String, Integer> idx, Node x, List<Set<Node>> sets, Node y) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;

        for (Set<Node> z : sets) {
            double tau = effect(sigma, idx, x, z, y);
            lo = Math.min(lo, tau);
            hi = Math.max(hi, tau);
        }

        return hi - lo;
    }
}
