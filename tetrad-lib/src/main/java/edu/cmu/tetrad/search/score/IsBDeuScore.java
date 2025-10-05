package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Objects;

/**
 * Instance-Specific BDeu score (discrete), following the Dirichlet–multinomial posterior predictive for the chosen
 * instance (Fattaneh's construction).
 * <p>
 * Local score: IS-local(i | Pa) = BDeu-local(i | Pa) + alpha * log P_instance
 * <p>
 * where the instance term is: log P_instance = log( (N_ijk + ESS/(r_i q)) / (N_ij + ESS/q) ) - i: child index - Pa:
 * parents' indices - j: parent configuration index of the instance row - k: categorical value of X_i in the instance
 * row - r_i: number of categories for X_i - q: number of distinct parent configurations (product of parent arities) -
 * N_ijk, N_ij: counts from the training data (ignoring rows with missing in i or Pa)
 * <p>
 * Missing values: if the instance row has missing for i or any parent, the instance term is 0.
 * <p>
 * Assumes the instance DataSet is a single row, already aligned to the training schema (same variable names and
 * category label sets).
 */
//@Deprecated
public final class IsBDeuScore implements Score {

    /**
     * A constant used to represent a missing value within the context of computations in the IsBDeuScore class. The
     * value of -99 is used as a placeholder for missing or undefined data points in scenarios where certain values are
     * expected but cannot be provided or calculated.
     */
    private static final int MISSING = -99;
    /**
     * A constant representing an empty array of integers.
     * <p>
     * This variable is used as a shared immutable representation of an empty integer array to avoid creating multiple
     * instances. It can be employed in scenarios where a method or operation requires an empty array as a placeholder
     * or default value.
     */
    private static final int[] EMPTY = new int[0];
    /**
     * Represents the discrete training data used for scoring calculations. This dataset is expected to be aligned with
     * other data structures by name and categories. It forms the baseline data for evaluating scores in the IsBDeuScore
     * class.
     */
    private final DataSet train;       // discrete training data
    /**
     * A discrete dataset containing exactly one row, used for scoring purposes. The dataset must be aligned by names
     * and categories with the training dataset. This is intended to represent a single instance for evaluation within
     * the scoring framework.
     */
    private final DataSet inst;        // 1-row discrete dataset (aligned)
    /**
     * Stores the BDeuScore calculated over the training dataset. Represents the population score for Bayesian network
     * structure learning. It is initialized with the discrete training data and used for scoring purposes.
     */
    private final BDeuScore base;      // population BDeu over 'train'
    /**
     * The variable `isAlpha` represents the weight assigned to the instance term in the scoring calculation. It is a
     * double value, and its default value is 1.0.
     * <p>
     * This variable is used in the calculation of certain scoring terms where a weighted influence is necessary for a
     * given instance or data-specific component.
     */
    private double isAlpha = 1.0;        // weight for instance term

    /* -------- Configuration -------- */

    /**
     * Constructs an IsBDeuScore object that validates and initializes a training dataset and a single-instance dataset
     * for Bayesian scoring. The training and instance datasets must be discrete and share the same schema,
     * including variable names and categories.
     *
     * @param train the training dataset, which must be discrete and contain the variables for scoring.
     *              It must have the same schema as the instance dataset.
     * @param instanceOneRow the single-instance dataset with exactly one row, which must be discrete.
     *                       Its schema (variable names and categories) must align with the training dataset.
     * @throws NullPointerException if the training dataset or instance dataset is null.
     * @throws IllegalArgumentException if the training dataset is not discrete, if the instance dataset is not discrete
     *                                  or does not have exactly one row, if the number of columns in the training dataset
     *                                  and the instance dataset differ, or if variable names and categories in the datasets
     *                                  do not align.
     */
    public IsBDeuScore(DataSet train, DataSet instanceOneRow) {
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

    /**
     * Retrieves the value of the isAlpha field.
     *
     * @return the current value of the isAlpha field as a double.
     */
    public double getIsAlpha() {
        return isAlpha;
    }

    /**
     * Sets the value of the isAlpha field.
     *
     * @param isAlpha the new value to be assigned to the isAlpha field.
     */
    public void setIsAlpha(double isAlpha) {
        this.isAlpha = isAlpha;
    }

    /* -------- Score interface (minimal) -------- */

    /**
     * Retrieves the prior equivalent sample size used for Bayesian scoring.
     * This value represents an effective sample size proxy that influences the score computation.
     *
     * @return the prior equivalent sample size as a double.
     */
    public double getPriorEquivalentSampleSize() {
        return base.getPriorEquivalentSampleSize();
    }

    /**
     * Sets the prior equivalent sample size to be used for Bayesian scoring.
     * This value serves as an effective sample size proxy that influences
     * the computations within the scoring process.
     *
     * @param ess the prior equivalent sample size to be set, typically a positive double value.
     *            It controls the relative strength of the prior in Bayesian score calculations.
     */
    public void setPriorEquivalentSampleSize(double ess) {
        base.setPriorEquivalentSampleSize(ess);
    }

    /**
     * Retrieves the list of variables (nodes) associated with the model or scoring process.
     *
     * @return a list of {@code Node} objects representing the variables, as obtained from the underlying base implementation.
     */
    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    /**
     * Retrieves the sample size of the data by delegating to the underlying base implementation.
     *
     * @return the sample size of the data as an integer.
     */
    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    /* -------- Instance posterior predictive -------- */

    /**
     * Computes the local score for a given node in the graph, incorporating both the base score
     * and an instance-specific term weighted by the isAlpha parameter.
     *
     * @param i the index of the node for which the local score is computed.
     * @return the computed local score, which combines the base score and the instance-specific contribution.
     */
    @Override
    public double localScore(int i) {
        double pop = base.localScore(i);
        double instTerm = instanceTerm(i, EMPTY);
        return pop + isAlpha * instTerm;
    }

    /**
     * Computes the local score for a given node and its parents by combining the base local score
     * and an instance-specific term influenced by the isAlpha parameter.
     *
     * @param i        the index of the node for which the local score is being computed.
     * @param parents  an array of node indices representing the parents of the node.
     * @return the local score calculated as the sum of the base score and the instance-specific term
     *         weighted by the isAlpha parameter.
     */
    @Override
    public double localScore(int i, int[] parents) {
        double pop = base.localScore(i, parents);
        double instTerm = instanceTerm(i, parents);
        return pop + isAlpha * instTerm;
    }

    /**
     * log( (N_ijk + ess/(r_i q)) / (N_ij + ess/q) ) for the instance’s (j,k). Returns 0 if the instance row is missing
     * in i or any parent.
     *
     * @param i the index of the node for which the local score is being computed.
     * @param parents the array of node indices representing the parents of the node.
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

        double ess = getPriorEquivalentSampleSize();
//        double alpha_ijk = (q == 0) ? 0.0 : ess / (r_i * (q == 0 ? 1 : q));
//        double alpha_ij  = (q == 0) ? 0.0 : ess / (q == 0 ? 1 : q);

        double alpha_ijk = ess / (r_i * q);
        double alpha_ij = ess / q;

        double num = N_ijk + alpha_ijk;
        double den = N_ij + alpha_ij;

        // Safety against pathological zeros (shouldn’t occur with ess > 0)
        if (num <= 0 || den <= 0) return 0.0;

        return Math.log(num) - Math.log(den);
    }
}