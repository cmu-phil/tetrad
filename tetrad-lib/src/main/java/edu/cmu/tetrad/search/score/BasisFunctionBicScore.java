package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.log;

/**
 * Calculates the basis function BIC score for a given dataset. This is a generalization of the Degenerate Gaussian
 * score by adding basis functions of the continuous variables and retains the function of the degenerate Gaussian for
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
     * Represents the penalty discount factor used in the Basis Function BIC (Bayesian Information Criterion) score
     * calculations. This value modifies the penalty applied for model complexity in BIC scoring, allowing for
     * adjustments in the likelihood penalty term.
     */
    private double penaltyDiscount = 2;

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet         the data set on which the score is to be calculated.
     * @param truncationLimit the truncation limit of the basis.
     * @param basisType       the type of basis function used in the BIC score computation.
     * @param basisScale      the basisScale factor used in the calculation of the BIC score for basis functions. All
     *                        variables are scaled to [-basisScale, basisScale], or standardized if 0.
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScore(DataSet dataSet, int truncationLimit,
                                 int basisType, double basisScale) {
        this.variables = dataSet.getVariables();

        boolean usePseudoInverse = false;

        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, basisType, basisScale,
                usePseudoInverse);
        this.embedding = result.embedding();
        DataSet embeddedData = result.embeddedData();

        // We will zero out the correlations that are very close to zero.
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(embeddedData);

        this.bic = new SemBicScore(correlationMatrix);
        this.bic.setPenaltyDiscount(penaltyDiscount);

        // We will be using the pseudo-inverse in the BIC score calculation so we don't get singularity exceptions.
        this.bic.setUsePseudoInverse(usePseudoInverse);

        // We will be modifying the penalty term in the BIC score calculation, so we set the structure prior to 0.
        this.bic.setStructurePrior(0);

    }

    /**
     * Calculates the local score for a given node and its parent nodes.
     *
     * @param i       The index of the node whose score is being calculated.
     * @param parents The indices for the parent nodes of the given node.
     * @return The calculated local score as a double value.
     */
    public double localScore(int i, int... parents) {
        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        double sumLik = 0.0;
        int sumDof = 0;
        double c = 0.0;
        int n = 0;

        for (Integer i_ : A) {
            int[] parents_ = new int[B.size()];
            for (int i__ = 0; i__ < B.size(); i__++) {
                parents_[i__] = B.get(i__);
            }

            SemBicScore.LikelihoodResult result = this.bic.getLikelihoodAndDof(i_, parents_);

            sumLik += result.lik();
            sumDof += result.dof();
            c = result.penaltyDiscount();
            n = result.sampleSize();
            B.add(i_);
        }

        return 2 * sumLik - embedding.get(i).size() * c * sumDof * log(n);
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
        return "Basis Function Score (BFS)";
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
