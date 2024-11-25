package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The PolynomialBasisScore class calculates the polynomial basis score for a given dataset. It implements the Score
 * interface and leverages the SemBicScore for its scoring mechanism.
 * <p>
 * This extends the functionality of the Degenerate Gaussian score so that for continuous variables, the score is
 * calculated as the sum of the scores of the first few powers of the variable. For discrete variables, the score is
 * calculated as the sum of the scores of the indicator variables for the levels of the discrete variable.
 *
 * @author josephramsey
 */
public class PolynomialBasisScore implements Score {
    /**
     * A list containing nodes that represent the variables in the polynomial basis score.
     */
    private final List<Node> variables;
    /**
     * A mapping used to store the embeddings of polynomial basis functions. The key is an integer representing the
     * index of the polynomial basis function. The value is a list of integers that represent the coefficients for the
     * corresponding polynomial basis function.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * An instance of SemBicScore used to compute the BIC (Bayesian Information Criterion) score for evaluating the fit
     * of a statistical model to a data set within the context of structural equation modeling (SEM).
     */
    private final SemBicScore bic;
    /**
     * Specifies the truncation limit for the polynomial basis used in the score calculation.
     * The truncation limit defines the degree up to which the polynomial terms are included
     * in the basis, effectively controlling the complexity of the score model.
     */
    private final int truncationLimit;

    /**
     * Constructs a PolynomialBasisScore object with the specified parameters.
     *
     * @param dataSet               the data set on which the score is to be calculated.
     * @param precomputeCovariances flag indicating whether covariances should be precomputed.
     * @param truncationLimit       the truncation limit of the polynomial basis.
     * @param usePseudoInverse      flag indicating whether to use the pseudo-inverse for matrix operations.
     */
    public PolynomialBasisScore(DataSet dataSet, boolean precomputeCovariances, int truncationLimit, boolean usePseudoInverse) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.truncationLimit = truncationLimit;

//        dataSet = DataTransforms.standardizeData(dataSet);
//
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            if (dataSet.getVariables().get(j) instanceof ContinuousVariable) {
                scale(dataSet, j);
            }
        }

        this.variables = dataSet.getVariables();
        int n = dataSet.getNumRows();
        this.embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        int index = 0;
        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {
            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {
                Map<List<Integer>, Integer> keys = new HashMap<>();
                Map<Integer, List<Integer>> keysReverse = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    List<Integer> key = new ArrayList<>();
                    key.add(dataSet.getInt(j, i_));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        keysReverse.put(i, key);
                        Node v_ = new ContinuousVariable("V__" + ++index);
                        A.add(v_);
                        B.add(new double[n]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                i--;
                keys.remove(keysReverse.get(i));
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));
            } else {
                List<Integer> indexList = new ArrayList<>();
                for (int p = 1; p <= truncationLimit; p++) {
                    Node vPower = new ContinuousVariable("V__" + ++index);
                    A.add(vPower);
                    double[] bPower = new double[n];
                    for (int j = 0; j < n; j++) {
                        bPower[j] = Math.pow(dataSet.getDouble(j, i_), p);
                    }
                    B.add(bPower);
                    indexList.add(i);
                    i++;
                }
                this.embedding.put(i_, indexList);
            }
            i_++;
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        RealMatrix D = MatrixUtils.createRealMatrix(B_);
        BoxDataSet dataSet1 = new BoxDataSet(new DoubleDataBox(D.getData()), A);
        this.bic = new SemBicScore(dataSet1, precomputeCovariances);
        this.bic.setUsePseudoInverse(usePseudoInverse);
        this.bic.setStructurePrior(0);
    }

    /**
     * Scales the specified column of the data set by normalizing the values. The scaling process normalizes values by
     * the maximum absolute value observed.
     *
     * @param dataSet the data set containing the values to be scaled.
     * @param col     the index of the column to be scaled in the data set.
     */
    private void scale(DataSet dataSet, int col) {
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            double d = dataSet.getDouble(i, col);
            if (Double.isNaN(d)) continue;
            if (d > max) max = d;
            if (d < min) min = d;
        }

        double biggest = Math.max(Math.abs(min), Math.abs(max));

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            double d = dataSet.getDouble(i, col);
            if (Double.isNaN(d)) continue;
//            dataSet.setDouble(i, col, min + (d - min) / (max - min));
            dataSet.setDouble(i, col, d / biggest);
        }
    }

    /**
     * Calculates the local score for a given node and its parent nodes.
     *
     * @param i       The index of the node whose score is being calculated.
     * @param parents The indices of the parent nodes of the given node.
     * @return The calculated local score as a double value.
     */
    public double localScore(int i, int... parents) {
        double score = 0;

        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        for (Integer i_ : A) {
            int[] parents_ = new int[B.size()];
            for (int i__ = 0; i__ < B.size(); i__++) {
                parents_[i__] = B.get(i__);
            }
            score += this.bic.localScore(i_, parents_);
            B.add(i_);
        }

        return score;
    }

    /*
     * Calculates the difference in the local score when a node `x` is added to the set of parent nodes `z` for a node
     * `y`.
     *
     * @param x The index of the node to be added.
     * @param y The index of the node whose score difference is being calculated.
     * @param z The indices of the parent nodes of the node `y`.
     * @return The difference in the local score as a double value.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves the list of nodes representing the variables in the polynomial basis score.
     *
     * @return a list containing the nodes that represent the variables in the polynomial basis score.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines if the given bump value represents an effect edge.
     *
     * @param bump the bump value to be evaluated.
     * @return true if the bump is an effect edge, false otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return this.bic.isEffectEdge(bump);
    }

    /**
     * Retrieves the sample size from the underlying BIC score component.
     *
     * @return the sample size as an integer
     */
    @Override
    public int getSampleSize() {
        return this.bic.getSampleSize();
    }

    /**
     * Retrieves the maximum degree from the underlying BIC score component.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return this.bic.getMaxDegree();
    }

    /**
     * Returns a string representation of the PolynomialBasisScore object.
     *
     * @return A string detailing the degenerate Gaussian score penalty with the penalty discount formatted to two
     * decimal places.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Polynomial Basis Score Penalty " + nf.format(this.bic.getPenaltyDiscount()
            + " truncation = " + this.truncationLimit);
    }

    /**
     * Retrieves the penalty discount value from the underlying BIC score component.
     *
     * @return the penalty discount as a double value.
     */
    public double getPenaltyDiscount() {
        return this.bic.getPenaltyDiscount();
    }

    /**
     * Sets the penalty discount value.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }
}