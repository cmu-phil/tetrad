/*
 * Copyright (C) 2026
 *
 * Exact inference for discrete Bayes nets using Variable Elimination (VE).
 * This class provides the API expected by JunctionTreeUpdater.
 *
 * NOTE:
 *  - Despite the name, this implementation does NOT build a junction tree;
 *    it performs exact inference by factor multiplication + elimination.
 *  - It is exact but may be slower than calibrated junction-tree message passing
 *    for very large cliques. For typical Tetrad use and unit tests it is fine.
 */

package edu.cmu.tetrad.bayes;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exact inference engine over a {@link BayesIm} supporting:
 * <ul>
 *   <li>Soft evidence via allowed categories ({@link Proposition})</li>
 *   <li>Hard evidence via node=category</li>
 *   <li>Marginals, conditionals, and joint marginals (all conditional on evidence)</li>
 * </ul>
 *
 * This class is designed specifically to satisfy the methods required by
 * {@link JunctionTreeUpdater}.
 *
 * 2026-02-21 jdramsey + (VE-based implementation provided here)
 */
public final class VariableEliminationInference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BayesIm bayesIm;

    // Soft evidence: allowed categories mask per node/category.
    // If null, treat as all allowed.
    private boolean[][] allowedMask;

    // Hard evidence: -1 means none, else fixed category.
    private final int[] hardEvidence;

    // Cache for P(E). Invalidated on any evidence change.
    private boolean normalizerDirty = true;
    private double cachedEvidenceProb = Double.NaN;

    /**
     * Construct inference over the given BayesIm.
     */
    public VariableEliminationInference(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException("bayesIm");
        this.bayesIm = bayesIm;

        this.hardEvidence = new int[bayesIm.getNumNodes()];
        Arrays.fill(this.hardEvidence, -1);

        // Default: no soft evidence restrictions.
        this.allowedMask = null;

        this.normalizerDirty = true;
    }

    /**
     * Soft evidence: set allowed categories for each variable.
     * Proposition must be indexed to this BayesIm.
     */
    public void setAllowedCategories(Proposition allowedCategories) {
        if (allowedCategories == null) throw new NullPointerException("allowedCategories");

        // Defensive copy (and re-index if Proposition comes from compatible source).
        Proposition p = new Proposition(this.bayesIm, allowedCategories);

        boolean[][] mask = new boolean[bayesIm.getNumNodes()][];
        for (int v = 0; v < bayesIm.getNumNodes(); v++) {
            int k = bayesIm.getNumColumns(v);
            mask[v] = new boolean[k];
            for (int c = 0; c < k; c++) {
                mask[v][c] = p.isAllowed(v, c);
            }
        }

        this.allowedMask = mask;
        invalidateCaches();
    }

    /**
     * Hard evidence: set node = category. Pass a negative category to clear hard evidence for that node.
     */
    public void setEvidence(int node, int category) {
        if (node < 0 || node >= bayesIm.getNumNodes()) {
            throw new IllegalArgumentException("node out of range: " + node);
        }

        if (category < 0) {
            hardEvidence[node] = -1;
            invalidateCaches();
            return;
        }

        if (category >= bayesIm.getNumColumns(node)) {
            throw new IllegalArgumentException("category out of range for node " + node + ": " + category);
        }

        hardEvidence[node] = category;
        invalidateCaches();
    }

    /**
     * Conditional marginal P(node=category | evidence).
     */
    public double getMarginal(int node, int category) {
        if (node < 0 || node >= bayesIm.getNumNodes()) {
            throw new IllegalArgumentException("node out of range: " + node);
        }
        if (category < 0 || category >= bayesIm.getNumColumns(node)) {
            throw new IllegalArgumentException("category out of range: " + category);
        }

        double denom = evidenceProbability();
        if (!(denom > 0.0) || Double.isNaN(denom)) return Double.NaN;

        double numer = probabilityWithEvidence(new int[]{node}, new int[]{category});
        if (Double.isNaN(numer)) return Double.NaN;

        return numer / denom;
    }

    /**
     * Conditional distribution P(node | parents=parentValues, evidence).
     * This returns an array of length numCategories(node).
     */
    public double[] getConditional(int node, int[] parents, int[] parentValues) {
        if (parents == null || parentValues == null) throw new NullPointerException();
        if (parents.length != parentValues.length) {
            throw new IllegalArgumentException("parents and parentValues length mismatch.");
        }
        if (node < 0 || node >= bayesIm.getNumNodes()) {
            throw new IllegalArgumentException("node out of range: " + node);
        }

        // Denominator: P(parents=parentValues | evidence) up to evidence scaling.
        // We'll compute numerator for each category and normalize.
        int k = bayesIm.getNumColumns(node);
        double[] out = new double[k];

        int m = parents.length;
        int[] vars = new int[m + 1];
        int[] vals = new int[m + 1];

        for (int i = 0; i < m; i++) {
            int p = parents[i];
            int pv = parentValues[i];
            if (p < 0 || p >= bayesIm.getNumNodes()) {
                throw new IllegalArgumentException("parent out of range: " + p);
            }
            if (pv < 0 || pv >= bayesIm.getNumColumns(p)) {
                throw new IllegalArgumentException("parent value out of range for parent " + p + ": " + pv);
            }
            vars[i] = p;
            vals[i] = pv;
        }
        vars[m] = node;

        double sum = 0.0;
        for (int c = 0; c < k; c++) {
            vals[m] = c;
            double p = getJointProbability(vars, vals); // conditional on evidence
            out[c] = p;
            if (!Double.isNaN(p)) sum += p;
        }

        if (!(sum > 0.0) || Double.isNaN(sum)) {
            Arrays.fill(out, Double.NaN);
            return out;
        }

        // Normalize to ensure it sums to 1 (numerical safety).
        for (int c = 0; c < k; c++) {
            out[c] = out[c] / sum;
        }

        return out;
    }

    /**
     * Conditional joint marginal P(vars=values | evidence).
     *
     * (This is what callers typically mean by "joint marginal".)
     */
    public double getJointProbability(int[] vars, int[] values) {
        if (vars == null || values == null) throw new NullPointerException();
        if (vars.length != values.length) {
            throw new IllegalArgumentException("vars and values length mismatch.");
        }

        // If requested assignment contradicts evidence, it's 0 (or NaN if evidence impossible).
        if (contradictsEvidence(vars, values)) return 0.0;

        double denom = evidenceProbability();
        if (!(denom > 0.0) || Double.isNaN(denom)) return Double.NaN;

        double numer = probabilityWithEvidence(vars, values);
        if (Double.isNaN(numer)) return Double.NaN;

        return numer / denom;
    }

    // =========================================================
    // Core VE computations
    // =========================================================

    /**
     * Compute P(E) where E is (soft evidence ∩ hard evidence).
     */
    private double evidenceProbability() {
        if (!normalizerDirty) return cachedEvidenceProb;

        // Compute probability with only evidence, no extra assignments.
        double pe = probabilityWithEvidence(new int[0], new int[0]);

        cachedEvidenceProb = pe;
        normalizerDirty = false;
        return cachedEvidenceProb;
    }

    /**
     * Compute P(assignments AND evidence).
     *
     * Returns 0.0 if inconsistent with evidence.
     * Returns NaN if evidence itself is impossible (no allowed categories somewhere),
     * or if numeric issues arise.
     */
    private double probabilityWithEvidence(int[] queryVars, int[] queryVals) {
        if (queryVars.length != queryVals.length) {
            throw new IllegalArgumentException("queryVars/queryVals length mismatch.");
        }

        // Fast contradiction check (hard + soft).
        if (contradictsEvidence(queryVars, queryVals)) return 0.0;

        // Build initial factor list: CPTs + evidence unary factors.
        List<Factor> factors = new ArrayList<>(bayesIm.getNumNodes() + bayesIm.getNumNodes());

        // CPT factor for each node: vars = parents + node
        for (int node = 0; node < bayesIm.getNumNodes(); node++) {
            factors.add(buildCptFactor(node));
        }

        // Evidence as unary factors (soft+hard+query assignments).
        // Start from current soft/hard evidence.
        boolean[][] mask = effectiveAllowedMask(queryVars, queryVals);
        for (int v = 0; v < bayesIm.getNumNodes(); v++) {
            Factor ev = buildUnaryEvidenceFactor(v, mask[v]);
            // If ev is all zeros => impossible evidence.
            if (ev.isAllZero()) return 0.0;
            factors.add(ev);
        }

        // Variables to eliminate: all except queryVars.
        boolean[] keep = new boolean[bayesIm.getNumNodes()];
        for (int v : queryVars) {
            if (v < 0 || v >= bayesIm.getNumNodes()) {
                throw new IllegalArgumentException("query var out of range: " + v);
            }
            keep[v] = true;
        }

        // Simple elimination order: 0..n-1 excluding keep vars.
        for (int elim = 0; elim < bayesIm.getNumNodes(); elim++) {
            if (keep[elim]) continue;

            // Collect factors that mention elim.
            List<Factor> bucket = new ArrayList<>();
            for (int i = factors.size() - 1; i >= 0; i--) {
                Factor f = factors.get(i);
                if (f.contains(elim)) {
                    bucket.add(f);
                    factors.remove(i);
                }
            }

            if (bucket.isEmpty()) continue;

            // Multiply bucket into one factor.
            Factor prod = bucket.get(0);
            for (int i = 1; i < bucket.size(); i++) {
                prod = prod.multiply(bucket.get(i));
                if (prod.isAllZero()) return 0.0;
            }

            // Sum out elim.
            Factor summed = prod.sumOut(elim);
            factors.add(summed);
        }

        // Multiply remaining factors; result is over query vars only.
        if (factors.isEmpty()) return 1.0;

        Factor result = factors.get(0);
        for (int i = 1; i < factors.size(); i++) {
            result = result.multiply(factors.get(i));
            if (result.isAllZero()) return 0.0;
        }

        // Now result should be a constant factor (if queryVars empty) or still over queryVars.
        // Since we applied query assignment via evidence mask, summing over remaining vars gives desired probability.
        return result.totalSum();
    }

    private boolean contradictsEvidence(int[] vars, int[] vals) {
        for (int i = 0; i < vars.length; i++) {
            int v = vars[i];
            int x = vals[i];

            if (v < 0 || v >= bayesIm.getNumNodes()) {
                throw new IllegalArgumentException("var out of range: " + v);
            }
            if (x < 0 || x >= bayesIm.getNumColumns(v)) {
                throw new IllegalArgumentException("value out of range for var " + v + ": " + x);
            }

            // Hard evidence
            int he = hardEvidence[v];
            if (he >= 0 && he != x) return true;

            // Soft evidence
            if (allowedMask != null && !allowedMask[v][x]) return true;
        }
        return false;
    }

    /**
     * Combine soft evidence + hard evidence + query assignments into one allowed-mask.
     */
    private boolean[][] effectiveAllowedMask(int[] queryVars, int[] queryVals) {
        int n = bayesIm.getNumNodes();
        boolean[][] mask = new boolean[n][];

        for (int v = 0; v < n; v++) {
            int k = bayesIm.getNumColumns(v);
            mask[v] = new boolean[k];

            // Start from soft evidence if present, else all allowed.
            if (allowedMask == null) {
                Arrays.fill(mask[v], true);
            } else {
                System.arraycopy(allowedMask[v], 0, mask[v], 0, k);
            }

            // Apply hard evidence
            int he = hardEvidence[v];
            if (he >= 0) {
                for (int c = 0; c < k; c++) mask[v][c] = (c == he);
            }
        }

        // Apply query assignments
        for (int i = 0; i < queryVars.length; i++) {
            int v = queryVars[i];
            int x = queryVals[i];
            int k = bayesIm.getNumColumns(v);

            for (int c = 0; c < k; c++) mask[v][c] = (c == x) && mask[v][c];
        }

        return mask;
    }

    private void invalidateCaches() {
        this.normalizerDirty = true;
        this.cachedEvidenceProb = Double.NaN;
    }

    // =========================================================
    // Factor construction
    // =========================================================

    /**
     * Build factor for CPT of node: vars = parents + node.
     * Parent order is exactly bayesIm.getParents(node).
     */
    private Factor buildCptFactor(int node) {
        int[] parents = bayesIm.getParents(node);
        int[] vars = new int[parents.length + 1];
        System.arraycopy(parents, 0, vars, 0, parents.length);
        vars[vars.length - 1] = node;

        int[] cards = new int[vars.length];
        for (int i = 0; i < vars.length; i++) {
            cards[i] = bayesIm.getNumColumns(vars[i]);
        }

        Factor f = new Factor(vars, cards);

        // Iterate over all parent assignments (rows) and node categories (cols).
        int numRows = bayesIm.getNumRows(node);
        int numCols = bayesIm.getNumColumns(node);

        // For each row, we can get parentValues from bayesIm.
        // That aligns with bayesIm.getParents(node) order (our vars[0..p-1] order).
        for (int row = 0; row < numRows; row++) {
            int[] parentVals = bayesIm.getParentValues(node, row);

            for (int col = 0; col < numCols; col++) {
                // assignment vector in vars order: parents..., node
                int[] assign = new int[vars.length];
                System.arraycopy(parentVals, 0, assign, 0, parentVals.length);
                assign[assign.length - 1] = col;

                double p = bayesIm.getProbability(node, row, col);
                f.set(assign, p);
            }
        }

        return f;
    }

    /**
     * Unary evidence factor for a variable v with allowed categories mask.
     */
    private Factor buildUnaryEvidenceFactor(int v, boolean[] allowed) {
        int[] vars = new int[]{v};
        int[] cards = new int[]{bayesIm.getNumColumns(v)};
        Factor f = new Factor(vars, cards);

        for (int c = 0; c < cards[0]; c++) {
            f.set(new int[]{c}, allowed[c] ? 1.0 : 0.0);
        }

        return f;
    }

    // =========================================================
    // Internal Factor class
    // =========================================================

    private static final class Factor {
        private final int[] vars;
        private final int[] cards;
        private final int[] strides;
        private final double[] table;

        Factor(int[] vars, int[] cards) {
            this.vars = vars.clone();
            this.cards = cards.clone();
            this.strides = new int[cards.length];

            int size = 1;
            for (int i = cards.length - 1; i >= 0; i--) {
                strides[i] = size;
                size *= cards[i];
            }
            this.table = new double[size];
        }

        boolean contains(int var) {
            for (int v : vars) if (v == var) return true;
            return false;
        }

        void set(int[] assignment, double value) {
            table[indexOf(assignment)] = value;
        }

        double get(int[] assignment) {
            return table[indexOf(assignment)];
        }

        int indexOf(int[] assignment) {
            int idx = 0;
            for (int i = 0; i < assignment.length; i++) {
                idx += assignment[i] * strides[i];
            }
            return idx;
        }

        boolean isAllZero() {
            for (double x : table) {
                if (x != 0.0 && !Double.isNaN(x)) return false;
            }
            return true;
        }

        double totalSum() {
            double s = 0.0;
            for (double x : table) {
                if (!Double.isNaN(x)) s += x;
            }
            return s;
        }

        Factor multiply(Factor other) {
            // Union variables
            int[] uVars = unionVars(this.vars, other.vars);
            int[] uCards = new int[uVars.length];

            for (int i = 0; i < uVars.length; i++) {
                int v = uVars[i];
                int c1 = cardOf(this, v);
                int c2 = cardOf(other, v);
                if (c1 < 0) uCards[i] = c2;
                else if (c2 < 0) uCards[i] = c1;
                else {
                    if (c1 != c2) {
                        throw new IllegalStateException("Cardinality mismatch for var " + v + ": " + c1 + " vs " + c2);
                    }
                    uCards[i] = c1;
                }
            }

            Factor out = new Factor(uVars, uCards);

            // Precompute index maps from union assignment to each factor assignment
            int[] mapThis = indexMap(uVars, this.vars);
            int[] mapOther = indexMap(uVars, other.vars);

            int[] assignU = new int[uVars.length];
            int total = out.table.length;

            for (int linear = 0; linear < total; linear++) {
                decode(linear, out.cards, out.strides, assignU);

                double a = this.table[this.indexFromUnion(assignU, mapThis)];
                double b = other.table[other.indexFromUnion(assignU, mapOther)];

                out.table[linear] = a * b;
            }

            return out;
        }

        Factor sumOut(int var) {
            int pos = positionOfVar(this.vars, var);
            if (pos < 0) return this; // nothing to sum

            int[] nVars = new int[this.vars.length - 1];
            int[] nCards = new int[this.cards.length - 1];

            for (int i = 0, j = 0; i < this.vars.length; i++) {
                if (i == pos) continue;
                nVars[j] = this.vars[i];
                nCards[j] = this.cards[i];
                j++;
            }

            Factor out = new Factor(nVars, nCards);

            int[] assignThis = new int[this.vars.length];
            int[] assignOut = new int[out.vars.length];

            // Iterate over all assignments of THIS, add into OUT on projection.
            for (int linear = 0; linear < this.table.length; linear++) {
                decode(linear, this.cards, this.strides, assignThis);

                // project
                for (int i = 0, j = 0; i < assignThis.length; i++) {
                    if (i == pos) continue;
                    assignOut[j++] = assignThis[i];
                }

                int outIdx = out.indexOf(assignOut);
                double x = this.table[linear];
                if (!Double.isNaN(x)) out.table[outIdx] += x;
            }

            return out;
        }

        private int indexFromUnion(int[] unionAssign, int[] map) {
            // map[i] = position in union for i-th var of THIS factor
            int idx = 0;
            for (int i = 0; i < this.vars.length; i++) {
                idx += unionAssign[map[i]] * this.strides[i];
            }
            return idx;
        }

        private static int cardOf(Factor f, int var) {
            for (int i = 0; i < f.vars.length; i++) {
                if (f.vars[i] == var) return f.cards[i];
            }
            return -1;
        }

        private static int positionOfVar(int[] vars, int var) {
            for (int i = 0; i < vars.length; i++) if (vars[i] == var) return i;
            return -1;
        }

        private static int[] unionVars(int[] a, int[] b) {
            int[] tmp = new int[a.length + b.length];
            int n = 0;

            for (int v : a) tmp[n++] = v;

            outer:
            for (int v : b) {
                for (int i = 0; i < a.length; i++) {
                    if (a[i] == v) continue outer;
                }
                tmp[n++] = v;
            }

            return Arrays.copyOf(tmp, n);
        }

        private static int[] indexMap(int[] unionVars, int[] subVars) {
            int[] map = new int[subVars.length];
            for (int i = 0; i < subVars.length; i++) {
                int v = subVars[i];
                int pos = -1;
                for (int j = 0; j < unionVars.length; j++) {
                    if (unionVars[j] == v) {
                        pos = j;
                        break;
                    }
                }
                if (pos < 0) throw new IllegalStateException("Var not found in union: " + v);
                map[i] = pos;
            }
            return map;
        }

        private static void decode(int linear, int[] cards, int[] strides, int[] outAssign) {
            // Given strides defined as product of later cards, we can decode by division.
            // Safer: repeated division using strides.
            for (int i = 0; i < cards.length; i++) {
                int s = strides[i];
                outAssign[i] = (linear / s) % cards[i];
            }
        }
    }
}