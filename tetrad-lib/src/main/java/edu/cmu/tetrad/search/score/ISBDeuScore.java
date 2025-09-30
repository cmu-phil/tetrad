package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Objects;

/**
 * Instance-Specific BDeu score (discrete), following the Dirichlet–multinomial
 * posterior predictive for the chosen instance (Fattaneh's construction).
 *
 * Local score:
 *   IS-local(i | Pa) = BDeu-local(i | Pa) + alpha * log P_instance
 *
 * where the instance term is:
 *   log P_instance = log( (N_ijk + ESS/(r_i q)) / (N_ij + ESS/q) )
 *   - i: child index
 *   - Pa: parents' indices
 *   - j: parent configuration index of the instance row
 *   - k: categorical value of X_i in the instance row
 *   - r_i: number of categories for X_i
 *   - q: number of distinct parent configurations (product of parent arities)
 *   - N_ijk, N_ij: counts from the training data (ignoring rows with missing in i or Pa)
 *
 * Missing values: if the instance row has missing for i or any parent, the instance term is 0.
 *
 * Assumes the instance DataSet is a single row, already aligned to the training schema
 * (same variable names and category label sets).
 */
@Deprecated
public final class ISBDeuScore implements Score {

    // sentinel used by Tetrad discrete tables; adjust if your build differs
    private static final int MISSING = -99;

    private final DataSet train;       // discrete training data
    private final DataSet inst;        // 1-row discrete dataset (aligned)
    private final BDeuScore base;      // population BDeu over 'train'
    private double isAlpha = 1.0;        // weight for instance term

    /**
     * @param train discrete training data
     * @param instanceOneRow discrete dataset with exactly 1 row, aligned by name & categories to 'train'
     */
    public ISBDeuScore(DataSet train, DataSet instanceOneRow) {
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

        // Verify variables are aligned by name and categories (robustness; you already did this upstream)
        List<Node> tvars = train.getVariables();
        for (int j = 0; j < tvars.size(); j++) {
            String name = tvars.get(j).getName();
            if (!name.equals(inst.getVariable(j).getName())) {
                throw new IllegalArgumentException("Variable name mismatch at column " + j + ": " + name +
                                                   " vs " + inst.getVariable(j).getName());
            }
            var tv = tvars.get(j);
            if (tv instanceof DiscreteVariable tdv) {
                var iv = inst.getVariable(j);
                if (!(iv instanceof DiscreteVariable idv)) {
                    throw new IllegalArgumentException("Variable " + name + " is discrete in train but not in instance.");
                }
                if (!tdv.getCategories().equals(idv.getCategories())) {
                    throw new IllegalArgumentException("Category labels differ for " + name +
                                                       " (ensure you remapped before constructing ISBDeuScore).");
                }
            }
        }

        this.base = new BDeuScore(train);
    }

    /* -------- Configuration -------- */

    public double getIsAlpha() { return isAlpha; }
    public void setIsAlpha(double isAlpha) { this.isAlpha = isAlpha; }

    /** Equivalent sample size proxy (delegates to BDeuScore). */
    public void setPriorEquivalentSampleSize(double ess) { base.setPriorEquivalentSampleSize(ess); }
    public double getPriorEquivalentSampleSize() { return base.getPriorEquivalentSampleSize(); }

    /* -------- Score interface (minimal) -------- */

    @Override
    public List<Node> getVariables() { return base.getVariables(); }

    @Override
    public int getSampleSize() { return base.getSampleSize(); }

    @Override
    public double localScore(int i) {
        double pop = base.localScore(i);
        double instTerm = instanceTerm(i, EMPTY);
        return pop + isAlpha * instTerm;
    }

    @Override
    public double localScore(int i, int[] parents) {
        double pop = base.localScore(i, parents);
        double instTerm = instanceTerm(i, parents);
        return pop + isAlpha * instTerm;
    }

    /* -------- Instance posterior predictive -------- */

    private static final int[] EMPTY = new int[0];

    /**
     * log( (N_ijk + ess/(r_i q)) / (N_ij + ess/q) ) for the instance’s (j,k).
     * Returns 0 if the instance row is missing in i or any parent.
     */
    private double instanceTerm(int i, int[] parents) {
        if (parents == null) parents = EMPTY;

        // instance values
        int xi = inst.getInt(0, i);
        if (xi == MISSING) return 0.0;

        // parent values in instance
        int[] paVals;
        if (parents.length == 0) {
            paVals = EMPTY;
        } else {
            paVals = new int[parents.length];
            for (int p = 0; p < parents.length; p++) {
                int val = inst.getInt(0, parents[p]);
                if (val == MISSING) return 0.0; // no instance contribution
                paVals[p] = val;
            }
        }

        // arities
        int r_i = ((DiscreteVariable) train.getVariable(i)).getCategories().size();
        int[] r_pa = new int[parents.length];
        int q = 1;
        for (int p = 0; p < parents.length; p++) {
            r_pa[p] = ((DiscreteVariable) train.getVariable(parents[p])).getCategories().size();
            q *= r_pa[p];
        }

        // parent config indexer (mixed radix)
        int jIndex = (parents.length == 0) ? 0 : parentConfigIndex(paVals, r_pa);

        // counts N_ij (parent config total) and N_ijk (child value within that config)
        long N_ij = 0;
        long N_ijk = 0;

        final int nRows = train.getNumRows();
        final int P = parents.length;

        for (int r = 0; r < nRows; r++) {
            int xv = train.getInt(r, i);
            if (xv == MISSING) continue;

            int j = 0;
            int mult = 1;
            boolean ok = true;
            for (int p = P - 1; p >= 0; p--) {
                int col = parents[p];
                int pv = train.getInt(r, col);
                if (pv == MISSING) { ok = false; break; }
                j += pv * mult;
                mult *= r_pa[p];
            }
            if (!ok) continue;

            if (j == jIndex) {
                N_ij++;
                if (xv == xi) N_ijk++;
            }
        }

        double ess = getPriorEquivalentSampleSize();
//        double alpha_ijk = (q == 0) ? 0.0 : ess / (r_i * (q == 0 ? 1 : q));
//        double alpha_ij  = (q == 0) ? 0.0 : ess / (q == 0 ? 1 : q);

        double alpha_ijk = ess / (r_i * q);
        double alpha_ij  = ess / q;

        double num = N_ijk + alpha_ijk;
        double den = N_ij + alpha_ij;

        // Safety against pathological zeros (shouldn’t occur with ess > 0)
        if (num <= 0 || den <= 0) return 0.0;

        return Math.log(num) - Math.log(den);
    }

    private static int parentConfigIndex(int[] paVals, int[] arities) {
        // Mixed-radix encoder: paVals in base 'arities' => integer index
        int idx = 0;
        int mult = 1;
        for (int k = arities.length - 1; k >= 0; k--) {
            idx += paVals[k] * mult;
            mult *= arities[k];
        }
        return idx;
    }
}