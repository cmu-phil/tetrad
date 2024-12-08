package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates the basis function BIC score for a given dataset. This is a modification of the Degenerate Gaussian score
 * by adding basis functions of the continuous variables and retains the function of the degenerate Gaussian for
 * discrete variables by adding indicator variables per category.
 *
 * @author bandrews
 * @author josephramsey
 * @see DegenerateGaussianScore
 */
public class BasisFunctionBicScore implements Score {
    /**
     * A list containing nodes that represent the variables in the basis function score.
     */
    private final List<Node> variables;
    /**
     * A mapping used to store the embeddings of basis functions for continuous variables and indicator variables per
     * category for discrete variables. The key is an integer representing the index of the basis function variable or
     * indicator variable.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * An instance of SemBicScore used to compute the BIC (Bayesian Information Criterion) score for evaluating the fit
     * of a statistical model to a data set within the context of structural equation modeling (SEM).
     */
    private final SemBicScore bic;
    /**
     * Represents the truncation limit of the basis.
     */
    private final int truncationLimit;
    /**
     * Represents the penalty discount factor used in the Basis Function BIC (Bayesian Information Criterion) score
     * calculations. This value modifies the penalty applied for model complexity in BIC scoring, allowing for
     * adjustments in the likelihood penalty term.
     */
    private double penaltyDiscount = 2;

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet               the data set on which the score is to be calculated.
     * @param precomputeCovariances flag indicating whether covariances should be precomputed.
     * @param truncationLimit       the truncation limit of the basis.
     * @param basisType             the type of basis function used in the BIC score computation.
     * @param basisScale                 the basisScale factor used in the calculation of the BIC score for basis functions.
     *                              All variables are scaled to [-basisScale, basisScale], or standardized if 0.
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScore(DataSet dataSet, boolean precomputeCovariances, int truncationLimit,
                                 int basisType, double basisScale) {
        Map<Integer, List<Integer>> embedding;
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.truncationLimit = truncationLimit;

        if (basisScale == 0.0) {
            dataSet = DataTransforms.standardizeData(dataSet);
        } else if (basisScale > 0.0) {
            dataSet = DataTransforms.scale(dataSet, basisScale);
        } else {
            throw new IllegalArgumentException("Basis scale must be a positive number, or 0 if the data should be standardized.");
        }

        this.variables = dataSet.getVariables();
        int n = dataSet.getNumRows();
        embedding = new HashMap<>();

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

                embedding.put(i_, new ArrayList<>(keys.values()));
            } else {
                List<Integer> indexList = new ArrayList<>();
                for (int p = 1; p <= truncationLimit; p++) {
                    Node vPower = new ContinuousVariable("V__" + ++index);
                    A.add(vPower);
                    double[] functional = new double[n];
                    for (int j = 0; j < n; j++) {
                        functional[j] = StatUtils.basisFunctionValue(basisType, p, dataSet.getDouble(j, i_));
                    }
                    B.add(functional);
                    indexList.add(i);
                    i++;
                }
                embedding.put(i_, indexList);
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
        this.bic.setPenaltyDiscount(penaltyDiscount);

        // We will be using the pseudo-inverse in the BIC score calculation so we don't get exceptions.
        this.bic.setUsePseudoInverse(true);

        // We will be modifying the penalty term in the BIC score calculation, so we set the structure prior to 0.
        this.bic.setStructurePrior(0);
        this.embedding = embedding;
    }

    /**
     * Calculates the local score for a given node and its parent nodes.
     *
     * @param i       The index of the node whose score is being calculated.
     * @param parents The indices for the parent nodes of the given node.
     * @return The calculated local score as a double value.
     */
    public double localScore(int i, int... parents) {
        double score = 0;

        // Count the number of continuous parents
        int numContinuousParents = 0;

        for (int parent : parents) {
            if (variables.get(parent) instanceof ContinuousVariable) {
                numContinuousParents++;
            }
        }

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

            double score1 = this.bic.localScore(i_, parents_);

            // Extra penalty for higher-degree continuous parents
            double[] weights1 = new double[numContinuousParents * truncationLimit];

            for (int k = 0; k < numContinuousParents; k++) {
                for (int j = 0; j < truncationLimit; j++) {
                    weights1[k * truncationLimit + j] = 0.5 * (1 + j);
                }
            }

            int n = this.bic.getSampleSize();

            double penalty = 0.0;
            for (int j = 1; j <= weights1.length; j++) {
                if (weights1[j - 1] > .5) {
                    penalty += (weights1[j - 1]) * Math.log(n);
                }
            }

            // Return the modified BIC
            double score2 = score1 + penalty;

            if (!Double.isNaN(score2)) {
                score += score2;
                B.add(i_);
            }

//            score += score2;
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
     * Retrieves the list of nodes representing the variables in the basis function score.
     *
     * @return a list containing the nodes that represent the variables in the basis function score.
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
     * Returns a string representation of the BasisFunctionBicScore object.
     *
     * @return A string detailing the degenerate Gaussian score penalty with the penalty discount formatted to two
     * decimal places.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Basis Function BIC Score (Basis-BIC) Penalty " + nf.format(this.bic.getPenaltyDiscount()) + " truncation = " + this.truncationLimit;
    }

    /**
     * Sets the penalty discount value, which is used to adjust the penalty term in the BIC score calculation.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }

}
