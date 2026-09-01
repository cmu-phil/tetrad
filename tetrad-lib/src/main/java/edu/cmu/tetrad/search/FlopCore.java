/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The algorithmic core of the FLOP (Fast Learning of Order and Parents) algorithm of Wienobst, Henckel, and Weichwald
 * (2026), "Embracing Discrete Search: A Reasonable Approach to Causal Structure Learning." This class is intentionally
 * free of any Tetrad dependencies; it operates entirely on a correlation matrix given as a double[][] and represents
 * variables as integer indices. The public-facing class {@link Flop} wraps this core, handling data conversion and
 * graph construction.
 * <p>
 * The search is a reinsertion-based local search over topological orders (Algorithm 1 of the paper, following BOSS),
 * with the following FLOP modifications: (a) parent selection uses a non-greedy grow-shrink warm-started from the
 * parent set for the previous prefix, with early exits when the changed prefix node cannot affect the parent set
 * (Algorithm 3); (b) an iterated local search (ILS) metaheuristic perturbs the best-found order with about ln(p)
 * random transpositions and restarts the local search (Section 4.2); and (c) the initial order is constructed by a
 * pivoted Cholesky decomposition of the correlation matrix so that strongly correlated variables are adjacent
 * (Section 4.1).
 * <p>
 * Local scores are the linear Gaussian BIC in the "smaller is better" orientation used in the paper: for node v with
 * parent set P, score = n * ln(residual variance of v given P) + penaltyDiscount * ln(n) * |P|. Residual variances
 * are computed from the bottom-right entry of the Cholesky factor of the correlation submatrix over (P, v) (Appendix
 * B of the paper). In this version each local score is computed with a fresh Cholesky factorization; incremental
 * rank-one updates (Section 3.2 of the paper) are a planned optimization confined to this class.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
final class FlopCore {

    /**
     * Minimum improvement for a score change to be accepted; guards against floating-point cycling.
     */
    private static final double EPS = 1e-10;

    /**
     * Floor for residual variances, guarding against numerically singular submatrices.
     */
    private static final double MIN_VAR = 1e-12;

    /**
     * The correlation matrix.
     */
    private final double[][] r;

    /**
     * The number of variables.
     */
    private final int p;

    /**
     * The sample size.
     */
    private final double n;

    /**
     * The penalty per parent: penaltyDiscount * ln(n).
     */
    private final double penaltyPerParent;

    /**
     * order[t] is the variable at position t.
     */
    private int[] order;

    /**
     * pos[v] is the position of variable v in the order.
     */
    private int[] pos;

    /**
     * parents.get(v) is the current parent set of variable v.
     */
    private final List<List<Integer>> parents;

    /**
     * scores[v] is the current local score of variable v (smaller is better).
     */
    private final double[] scores;

    /**
     * Constructs the core for the given correlation matrix.
     *
     * @param r               The correlation matrix, p x p. Not modified.
     * @param sampleSize      The sample size n.
     * @param penaltyDiscount The multiplier on the ln(n) * |P| BIC penalty term (lambda in the paper; 2 is the
     *                        default recommended there, following Foygel and Drton, 2010).
     */
    FlopCore(double[][] r, int sampleSize, double penaltyDiscount) {
        this.r = r;
        this.p = r.length;
        this.n = sampleSize;
        this.penaltyPerParent = penaltyDiscount * Math.log(sampleSize);
        this.parents = new ArrayList<>(p);
        for (int v = 0; v < p; v++) this.parents.add(new ArrayList<>());
        this.scores = new double[p];
    }

    /**
     * The result of a search: the best order found, the parent sets of the corresponding DAG, and its total score.
     *
     * @param order   The best order found.
     * @param parents The parent sets of the corresponding DAG; parents.get(v) lists the parents of variable v.
     * @param score   The total BIC score (smaller is better).
     */
    record Result(int[] order, List<List<Integer>> parents, double score) {
    }

    /**
     * Runs the FLOP search: an initial local search from the pivoted-Cholesky initial order, followed by numRestarts
     * rounds of iterated local search.
     *
     * @param numRestarts The number of ILS restarts (0 for a single local search).
     * @param seed        A random seed for the ILS perturbations, or -1 for a nondeterministic seed.
     * @param log         An optional consumer for progress messages; may be null.
     * @return The best result found.
     * @throws InterruptedException If the thread is interrupted.
     */
    Result search(int numRestarts, long seed, Consumer<String> log) throws InterruptedException {
        this.order = initialOrder(this.r);
        this.pos = new int[p];
        for (int t = 0; t < p; t++) this.pos[this.order[t]] = t;

        double score = localSearch();

        int[] bestOrder = this.order.clone();
        List<List<Integer>> bestParents = deepCopy(this.parents);
        double bestScore = score;

        if (log != null) log.accept("FLOP: initial local search complete, score = " + bestScore);

        Random rng = (seed == -1L) ? new Random() : new Random(seed);
        int numSwaps = Math.max(1, (int) Math.round(Math.log(p)));

        for (int restart = 1; restart <= numRestarts; restart++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            // Perturb the best-found order with numSwaps random transpositions.
            this.order = bestOrder.clone();

            for (int s = 0; s < numSwaps; s++) {
                int i = rng.nextInt(p);
                int j = rng.nextInt(p);
                int tmp = this.order[i];
                this.order[i] = this.order[j];
                this.order[j] = tmp;
            }

            for (int t = 0; t < p; t++) this.pos[this.order[t]] = t;

            score = localSearch();

            if (score < bestScore - EPS) {
                bestScore = score;
                bestOrder = this.order.clone();
                bestParents = deepCopy(this.parents);
            }

            if (log != null) {
                log.accept("FLOP: restart " + restart + " of " + numRestarts + ", score = " + score
                           + ", best = " + bestScore);
            }
        }

        return new Result(bestOrder, bestParents, bestScore);
    }

    /**
     * Computes the FLOP initial order (Section 4.1 of the paper) via a pivoted Cholesky decomposition of the given
     * correlation matrix: the first two positions go to the most correlated pair; thereafter the pivot is the
     * unplaced variable with the smallest residual variance given the placed variables. (This duplicates the private
     * routine in edu.cmu.tetrad.search.utils.FlopInitialOrder so that this class remains free of dependencies.)
     *
     * @param r A correlation matrix, as a p x p array. Not modified.
     * @return A permutation of {0, ..., p - 1}.
     */
    static int[] initialOrder(double[][] r) {
        int p = r.length;
        int[] order = new int[p];

        if (p < 3) {
            for (int i = 0; i < p; i++) order[i] = i;
            return order;
        }

        double[][] L = new double[p][p]; // Partial Cholesky columns; row i = variable i.
        double[] d = new double[p];      // Current residual variances.
        boolean[] placed = new boolean[p];

        for (int i = 0; i < p; i++) d[i] = r[i][i];

        // Find the most correlated pair (a, b).
        int a = 0;
        int b = 1;
        double best = -1.0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double abs = Math.abs(r[i][j]);
                if (abs > best) {
                    best = abs;
                    a = i;
                    b = j;
                }
            }
        }

        int next = a;

        for (int t = 0; t < p; t++) {
            int j = next;
            placed[j] = true;
            order[t] = j;

            double ljj = Math.sqrt(Math.max(d[j], MIN_VAR));
            L[j][t] = ljj;

            for (int i = 0; i < p; i++) {
                if (placed[i]) continue;
                double s = r[i][j];
                for (int k = 0; k < t; k++) s -= L[i][k] * L[j][k];
                L[i][t] = s / ljj;
                d[i] -= L[i][t] * L[i][t];
            }

            if (t + 1 == p) break;

            if (t == 0) {
                next = b;
            } else {
                next = -1;
                double min = Double.POSITIVE_INFINITY;

                for (int i = 0; i < p; i++) {
                    if (!placed[i] && d[i] < min) {
                        min = d[i];
                        next = i;
                    }
                }
            }
        }

        return order;
    }

    /**
     * The reinsertion-based local search (Algorithm 1 of the paper): initialize parent sets by grow-shrink from
     * scratch for the current order, then repeatedly reinsert each variable at its best position until no
     * improvement is found.
     *
     * @return The total score of the resulting DAG.
     * @throws InterruptedException If the thread is interrupted.
     */
    private double localSearch() throws InterruptedException {
        for (int t = 0; t < p; t++) growShrinkScratch(this.order[t]);

        boolean improved = true;

        while (improved) {
            improved = false;
            int[] sweep = this.order.clone();

            for (int v : sweep) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (reinsert(v)) improved = true;
            }
        }

        return sum(this.scores);
    }

    /**
     * Finds the best reinsertion position for variable v (Algorithm 2 of the paper) and moves it there. The search
     * sweeps v rightward to the end, restores, sweeps leftward to the front, restores, and then replays the moves to
     * the best position found (grow-shrink is deterministic, so the replay reproduces the swept state exactly).
     *
     * @param v The variable to reinsert.
     * @return True if v was moved to a strictly better position.
     */
    private boolean reinsert(int v) {
        Snapshot snap = snapshot();
        double bestSum = sum(this.scores);
        int bestDir = 0;
        int bestSteps = 0;

        // Sweep right.
        int steps = 0;

        while (this.pos[v] < p - 1) {
            moveRight(v);
            steps++;
            double s = sum(this.scores);

            if (s < bestSum - EPS) {
                bestSum = s;
                bestDir = 1;
                bestSteps = steps;
            }
        }

        restore(snap);

        // Sweep left.
        steps = 0;

        while (this.pos[v] > 0) {
            moveLeft(v);
            steps++;
            double s = sum(this.scores);

            if (s < bestSum - EPS) {
                bestSum = s;
                bestDir = -1;
                bestSteps = steps;
            }
        }

        restore(snap);

        if (bestDir == 0) return false;

        for (int k = 0; k < bestSteps; k++) {
            if (bestDir > 0) moveRight(v);
            else moveLeft(v);
        }

        return true;
    }

    /**
     * Swaps v one position to the right, past the variable w that followed it. The candidate prefix of v gains w;
     * the candidate prefix of w loses v. Parent sets and scores are updated by warm-started grow-shrink.
     *
     * @param v The variable to move; must not be at the last position.
     */
    private void moveRight(int v) {
        int t = this.pos[v];
        int w = this.order[t + 1];
        this.order[t] = w;
        this.order[t + 1] = v;
        this.pos[w] = t;
        this.pos[v] = t + 1;
        growShrinkRemove(w, v);
        growShrinkAdd(v, w);
    }

    /**
     * Swaps v one position to the left, past the variable w that preceded it. The candidate prefix of v loses w; the
     * candidate prefix of w gains v. Parent sets and scores are updated by warm-started grow-shrink.
     *
     * @param v The variable to move; must not be at the first position.
     */
    private void moveLeft(int v) {
        int t = this.pos[v];
        int w = this.order[t - 1];
        this.order[t - 1] = v;
        this.order[t] = w;
        this.pos[v] = t - 1;
        this.pos[w] = t;
        growShrinkRemove(v, w);
        growShrinkAdd(w, v);
    }

    /**
     * Warm-started grow-shrink for the case where variable 'added' has just been added to the candidate prefix of v
     * (Algorithm 3 of the paper, delta &gt; 0). If adding 'added' to the previous parent set does not improve the
     * local score, the previous parent set is returned immediately (this early exit is justified by Theorem 3.3 of
     * the paper). Otherwise 'added' is accepted and the full grow and shrink phases are run.
     *
     * @param v     The variable whose parents are updated.
     * @param added The variable newly added to v's candidate prefix.
     */
    private void growShrinkAdd(int v, int added) {
        List<Integer> pa = this.parents.get(v);
        double lPrev = this.scores[v];
        pa.add(added);
        double lNew = localScore(v, pa);

        if (lNew >= lPrev - EPS) {
            pa.remove(pa.size() - 1);
            return;
        }

        this.scores[v] = lNew;
        grow(v);
        shrink(v);
    }

    /**
     * Warm-started grow-shrink for the case where variable 'removed' has just been removed from the candidate prefix
     * of v (Algorithm 3 of the paper, delta &lt; 0). If 'removed' was not a parent of v, the previous parent set is
     * returned immediately. Otherwise 'removed' is deleted and the full grow and shrink phases are run.
     *
     * @param v       The variable whose parents are updated.
     * @param removed The variable newly removed from v's candidate prefix.
     */
    private void growShrinkRemove(int v, int removed) {
        List<Integer> pa = this.parents.get(v);
        int idx = pa.indexOf(removed);
        if (idx < 0) return;
        pa.remove(idx);
        this.scores[v] = localScore(v, pa);
        grow(v);
        shrink(v);
    }

    /**
     * Grow-shrink from scratch (empty parent set) for variable v, relative to its current candidate prefix.
     *
     * @param v The variable whose parents are computed.
     */
    private void growShrinkScratch(int v) {
        List<Integer> pa = this.parents.get(v);
        pa.clear();
        this.scores[v] = localScore(v, pa);
        grow(v);
        shrink(v);
    }

    /**
     * The non-greedy grow phase: repeatedly pass over the candidate prefix of v, adding any variable whose addition
     * improves the local score (not necessarily the best such variable), until a full pass makes no change.
     *
     * @param v The variable whose parents are grown.
     */
    private void grow(int v) {
        List<Integer> pa = this.parents.get(v);
        boolean changed = true;

        while (changed) {
            changed = false;

            for (int t = 0; t < this.pos[v]; t++) {
                int u = this.order[t];
                if (pa.contains(u)) continue;
                pa.add(u);
                double l = localScore(v, pa);

                if (l < this.scores[v] - EPS) {
                    this.scores[v] = l;
                    changed = true;
                } else {
                    pa.remove(pa.size() - 1);
                }
            }
        }
    }

    /**
     * The non-greedy shrink phase: repeatedly pass over the parents of v, removing any parent whose removal improves
     * the local score, until a full pass makes no change.
     *
     * @param v The variable whose parents are shrunk.
     */
    private void shrink(int v) {
        List<Integer> pa = this.parents.get(v);
        boolean changed = true;

        while (changed) {
            changed = false;

            for (int i = pa.size() - 1; i >= 0; i--) {
                int u = pa.remove(i);
                double l = localScore(v, pa);

                if (l < this.scores[v] - EPS) {
                    this.scores[v] = l;
                    changed = true;
                } else {
                    pa.add(i, u);
                }
            }
        }
    }

    /**
     * The local linear Gaussian BIC score of v given parent set pa, smaller is better: n * ln(conditional variance)
     * + penaltyDiscount * ln(n) * |pa|. The conditional variance is the square of the bottom-right entry of the
     * Cholesky factor of the correlation submatrix over (pa, v); see Appendix B of the paper. The factorization is
     * computed fresh for each call in this version.
     *
     * @param v  The child variable.
     * @param pa The parent set.
     * @return The local score.
     */
    private double localScore(int v, List<Integer> pa) {
        int k = pa.size();
        int m = k + 1;
        int[] idx = new int[m];
        for (int i = 0; i < k; i++) idx[i] = pa.get(i);
        idx[k] = v;

        double[][] s = new double[m][m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                s[i][j] = this.r[idx[i]][idx[j]];
            }
        }

        // In-place lower Cholesky; the final diagonal pivot (before its square root) is the conditional variance.
        double condVar = 1.0;

        for (int j = 0; j < m; j++) {
            double diag = s[j][j];
            for (int q = 0; q < j; q++) diag -= s[j][q] * s[j][q];
            diag = Math.max(diag, MIN_VAR);
            if (j == m - 1) {
                condVar = diag;
                break;
            }
            double ljj = Math.sqrt(diag);
            s[j][j] = ljj;

            for (int i = j + 1; i < m; i++) {
                double val = s[i][j];
                for (int q = 0; q < j; q++) val -= s[i][q] * s[j][q];
                s[i][j] = val / ljj;
            }
        }

        return this.n * Math.log(condVar) + this.penaltyPerParent * k;
    }

    /**
     * Sums an array of doubles.
     *
     * @param a The array.
     * @return The sum.
     */
    private static double sum(double[] a) {
        double s = 0.0;
        for (double x : a) s += x;
        return s;
    }

    /**
     * Deep-copies a list of parent lists.
     *
     * @param lists The lists to copy.
     * @return The copy.
     */
    private static List<List<Integer>> deepCopy(List<List<Integer>> lists) {
        List<List<Integer>> copy = new ArrayList<>(lists.size());
        for (List<Integer> list : lists) copy.add(new ArrayList<>(list));
        return copy;
    }

    /**
     * A deep copy of the mutable search state, used to restore after the sweeps in reinsert().
     *
     * @param order   The order.
     * @param pos     The positions.
     * @param parents The parent sets.
     * @param scores  The local scores.
     */
    private record Snapshot(int[] order, int[] pos, List<List<Integer>> parents, double[] scores) {
    }

    /**
     * Takes a snapshot of the current search state.
     *
     * @return The snapshot.
     */
    private Snapshot snapshot() {
        return new Snapshot(this.order.clone(), this.pos.clone(), deepCopy(this.parents), this.scores.clone());
    }

    /**
     * Restores the search state from a snapshot.
     *
     * @param snap The snapshot.
     */
    private void restore(Snapshot snap) {
        System.arraycopy(snap.order(), 0, this.order, 0, p);
        System.arraycopy(snap.pos(), 0, this.pos, 0, p);
        System.arraycopy(snap.scores(), 0, this.scores, 0, p);

        for (int v = 0; v < p; v++) {
            List<Integer> pa = this.parents.get(v);
            pa.clear();
            pa.addAll(snap.parents().get(v));
        }
    }
}
