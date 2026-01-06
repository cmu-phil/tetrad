package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.Score;

import java.util.List;
import java.util.Objects;

/**
 * Instance‑Specific BDeu score (discrete), using the Dirichlet–multinomial posterior predictive term for the
 * <em>instance row</em> (Jabbari).
 *
 * <p>Local score:
 * <pre>
 *   IS(i | Pa) = BDeu(i | Pa) + alpha * log P_instance
 * </pre>
 * where
 * <pre>
 *   log P_instance = log( (N_ijk + ESS/(r_i q)) / (N_ij + ESS/q) )
 * </pre>
 * for the instance’s (j, k). Missing in the instance for i or any Pa yields 0 instance contribution.
 */
public final class IsBDeuScore implements Score {

    private static final int MISSING = -99;
    private static final int[] EMPTY = new int[0];

    private final DataSet train;     // discrete, aligned with inst
    private final DataSet inst;      // single row, discrete, aligned
    private final BDeuScore base;    // population score over train

    /**
     * Weight on the instance term.
     */
    private double isAlpha = 1.0;

    /**
     * Constructs an instance of the IsBDeuScore class, which validates and compares the schema and data consistency
     * between a training dataset and a single-row instance dataset, ensuring they meet the criteria for Bayesian scoring.
     *
     * @param train the training dataset, which must be discrete and provides the base variables and schema for comparison.
     * @param instanceOneRow the single-row instance dataset, which must be discrete, have exactly one row,
     *                       and match the schema of the training dataset.
     * @throws IllegalArgumentException if the training dataset is not discrete, if the instance dataset is not discrete,
     *                                  if the instance dataset does not have exactly one row, if the column count or variable
     *                                  names do not match between the two datasets, or if the category labels differ for
     *                                  discrete variables.
     * @throws NullPointerException if either the training dataset or the instance dataset is null.
     */
    public IsBDeuScore(final DataSet train, final DataSet instanceOneRow) {
        if (!Objects.requireNonNull(train, "train").isDiscrete()) {
            throw new IllegalArgumentException("Training data must be discrete.");
        }
        this.train = train;

        this.inst = Objects.requireNonNull(instanceOneRow, "instanceOneRow");
        if (!inst.isDiscrete() || inst.getNumRows() != 1) {
            throw new IllegalArgumentException("Instance dataset must be discrete and have exactly 1 row.");
        }
        if (train.getNumColumns() != inst.getNumColumns()) {
            throw new IllegalArgumentException("Schema mismatch: column count differs (train vs instance).");
        }

        // Verify name & category alignment
        final List<Node> tvars = train.getVariables();
        for (int j = 0; j < tvars.size(); j++) {
            final String name = tvars.get(j).getName();
            if (!name.equals(inst.getVariable(j).getName())) {
                throw new IllegalArgumentException("Variable name mismatch at column " + j + ": " + name +
                                                   " vs " + inst.getVariable(j).getName());
            }
            Node tv = tvars.get(j);
            if (tv instanceof DiscreteVariable tdv) {
                Node iv = inst.getVariable(j);
                if (!(iv instanceof DiscreteVariable idv)) {
                    throw new IllegalArgumentException("Variable " + name + " is discrete in train but not in instance.");
                }
                if (!tdv.getCategories().equals(idv.getCategories())) {
                    throw new IllegalArgumentException("Category labels differ for " + name +
                                                       " (ensure you remapped before constructing IsBDeuScore).");
                }
            }
        }

        this.base = new BDeuScore(train);
    }

    // ---------------- Score plumbing ----------------

    /**
     * Mixed‑radix encoder: paVals in base 'arities' → integer index.
     */
    private static int parentConfigIndex(int[] paVals, int[] arities) {
        int idx = 0, mult = 1;
        for (int k = arities.length - 1; k >= 0; k--) {
            idx += paVals[k] * mult;
            mult *= arities[k];
        }
        return idx;
    }

    /**
     * Retrieves the prior equivalent sample size, which is a parameter used in Bayesian scoring to incorporate prior
     * knowledge or assumptions about the data into the scoring function.
     *
     * @return the prior equivalent sample size as a double value.
     */
    public double getPriorEquivalentSampleSize() {
        return base.getPriorEquivalentSampleSize();
    }

    /**
     * Sets the prior equivalent sample size (ESS), which is used in Bayesian scoring to incorporate prior knowledge or
     * assumptions about the data into the scoring function.
     *
     * @param ess the prior equivalent sample size to be set, represented as a double value
     */
    public void setPriorEquivalentSampleSize(double ess) {
        base.setPriorEquivalentSampleSize(ess);
    }

    /**
     * Retrieves the list of variables represented by nodes in the score computation.
     *
     * @return a list of nodes representing the variables used in the score computation
     */
    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    /**
     * Retrieves the sample size of the data being scored.
     *
     * @return the sample size as an integer value.
     */
    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    /**
     * Retrieves the current value of the isAlpha property, which likely represents a parameter or constant used in
     * scoring or computation within the class.
     *
     * @return the isAlpha value as a double.
     */
    public double getIsAlpha() {
        return isAlpha;
    }

    // ---------------- Local scores ----------------

    /**
     * Sets the value of the isAlpha property, which likely represents a parameter or constant used in the scoring or
     * computation within the class.
     *
     * @param isAlpha the value to set for the isAlpha property, represented as a double.
     */
    public void setIsAlpha(double isAlpha) {
        this.isAlpha = isAlpha;
    }

    @Override
    public double localScore(int i) {
        return base.localScore(i) + isAlpha * instanceTerm(i, EMPTY);
    }

    // ---------------- Instance term ----------------

    /**
     * Computes the local score of a given node based on its parents, factoring in an additional term weighted by a
     * parameter `isAlpha`.
     *
     * @param i       The index of the node whose local score is to be computed.
     * @param parents An array of indices representing the parent nodes of the given node.
     * @return The computed local score as a double, which is the sum of the base score and an additional instance-based
     * term scaled by `isAlpha`.
     */
    @Override
    public double localScore(int i, int[] parents) {
        return base.localScore(i, parents) + isAlpha * instanceTerm(i, parents);
    }

    /**
     * log( (N_ijk + ess/(r_i q)) / (N_ij + ess/q) ) for the instance’s (j,k), or 0 if the instance row is missing at i
     * or any parent.
     */
    private double instanceTerm(final int i, int[] parents) {
        if (parents == null) parents = EMPTY;

        // instance value of child
        final int xi = inst.getInt(0, i);
        if (xi == MISSING) return 0.0;

        // instance parent values
        final int P = parents.length;
        final int[] paVals = (P == 0) ? EMPTY : new int[P];
        if (P > 0) {
            for (int p = 0; p < P; p++) {
                final int val = inst.getInt(0, parents[p]);
                if (val == MISSING) return 0.0; // no contribution
                paVals[p] = val;
            }
        }

        // arities
        final int r_i = ((DiscreteVariable) train.getVariable(i)).getNumCategories();
        final int[] r_pa = new int[P];
        int q = 1;
        for (int p = 0; p < P; p++) {
            r_pa[p] = ((DiscreteVariable) train.getVariable(parents[p])).getNumCategories();
            q *= r_pa[p];
        }
        if (q == 0) q = 1; // defensive, though r_pa should all be >0

        // index of the instance’s parent configuration
        final int jIndex = (P == 0) ? 0 : parentConfigIndex(paVals, r_pa);

        // counts within that configuration (ignoring rows with missing in i or Pa)
        long N_ij = 0L;
        long N_ijk = 0L;
        final int n = train.getNumRows();
        for (int r = 0; r < n; r++) {
            final int xv = train.getInt(r, i);
            if (xv == MISSING) continue;

            int j = 0, mult = 1;
            boolean ok = true;
            for (int p = P - 1; p >= 0; p--) {
                final int pv = train.getInt(r, parents[p]);
                if (pv == MISSING) {
                    ok = false;
                    break;
                }
                j += pv * mult;
                mult *= r_pa[p];
            }
            if (!ok) continue;
            if (j == jIndex) {
                N_ij++;
                if (xv == xi) N_ijk++;
            }
        }

        final double ess = Math.max(0.0, getPriorEquivalentSampleSize());
        final double alpha_ijk = (q == 0 ? 0.0 : ess / (r_i * q));
        final double alpha_ij = (q == 0 ? 0.0 : ess / q);

        final double num = N_ijk + alpha_ijk;
        final double den = N_ij + alpha_ij;
        if (num <= 0.0 || den <= 0.0) return 0.0; // safe guard
        return Math.log(num) - Math.log(den);
    }
}
