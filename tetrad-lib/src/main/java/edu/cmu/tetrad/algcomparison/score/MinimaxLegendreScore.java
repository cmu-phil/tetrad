package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Minimax Legendre BIC score (mixed).
 * <p>
 * Exposed parameters (pared down):
 * - minimaxLegendreDegree (int, default 8)
 * - minimaxLegendreClip   (double, default 3.0)   [optional but practical]
 * - minimaxLegendreRidge  (double, default 1e-3)
 * - penaltyDiscount       (double)
 * - effectiveSampleSize   (int, optional; if &lt;= 0, uses dataset n)
 * <p>
 * Hidden/internal:
 * - minimaxLegendreNu (default 5.0)
 * - minimaxLegendreInitScale (default 1.0)
 * - minimaxLegendreIrlsIters (default 8)
 * - minimaxLegendreIrlsTol   (default 1e-6)
 */
//@edu.cmu.tetrad.annotation.Score(
//        name = "Minimax Legendre Score",
//        command = "minimax-legendre-score",
//        dataType = {DataType.Mi xed}
//)
//@General
//@Mixed
//@Experimental
public class MinimaxLegendreScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Represents the dataset used within the Minimax Legendre scoring framework.
     * The dataset must adhere to the {@code DataModel} interface, which allows for
     * the handling of diverse data types, including continuous, discrete, and mixed data.
     * This field serves as a core component for initializing and performing
     * calculations related to the Minimax Legendre scoring methodology.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the MinimaxLegendreScore.
     * This is a score wrapper for calculating measures based on the
     * Minimax Legendre properties, designed for specific applications
     * involving mixed data types.
     * <p>
     * The class serves as an intermediary layer for initializing
     * and managing parameters and datasets required for score calculations.
     */
    public MinimaxLegendreScore() {
    }

    /**
     * Calculates and returns a scoring object based on the provided dataset and parameters.
     * This implementation utilizes the Minimax Legendre scoring approach, allowing configuration
     * of key parameters such as degree, ridge, clip value, and penalty discount.
     *
     * @param dataSet    The dataset for which the score will be calculated. Must be an instance of {@code DataSet}.
     * @param parameters A {@code Parameters} object containing configuration values for the scoring process.
     *                   This includes:
     *                   - {@code MINIMAX_LEGENDRE_DEGREE}: Integer parameter to set the degree. Defaults to 8 if not provided or invalid.
     *                   - {@code MINIMAX_LEGENDRE_CLIP}: Double parameter for clipping level. Defaults to 3.0 if not provided or invalid.
     *                   - {@code MINIMAX_LEGENDRE_RIDGE}: Double parameter defining the ridge value. Defaults to 1e-3 if not provided or invalid.
     *                   - {@code EFFECTIVE_SAMPLE_SIZE}: Integer parameter for effective sample size. Applied only if > 0.
     *                   - {@code PENALTY_DISCOUNT}: Double parameter for penalty discount applied during score computation.
     * @return An instance of {@code Score}, configured based on the provided dataset and parameters.
     * @throws IllegalArgumentException If the provided dataset is not an instance of {@code DataSet}.
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        if (!(dataSet instanceof DataSet ds)) {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        edu.cmu.tetrad.search.score.MinimaxLegendreScore score =
                new edu.cmu.tetrad.search.score.MinimaxLegendreScore(ds);

        // ---- Keep exposed: Degree (t) ----
        int degree = parameters.getInt(Params.MINIMAX_LEGENDRE_DEGREE);
        if (degree <= 0) degree = 8;
        score.setLegendreDegree(degree);

//        // ---- Keep exposed (optional but practical): Clip ----
//        double clip = parameters.getDouble(Params.MINIMAX_LEGENDRE_CLIP);
//        if (!(clip > 0.0) || !Double.isFinite(clip)) clip = 3.0;
//        score.setLegendreClip(clip);

//        // ---- Keep exposed: Ridge ----
//        double ridge = parameters.getDouble(Params.MINIMAX_LEGENDRE_RIDGE);
//        if (!(ridge > 0.0) || !Double.isFinite(ridge)) ridge = 1e-3;
//        score.setRidge(ridge);

        // ---- Hidden/internal defaults ----
        score.setNu(5.0);          // Student-t df
        score.setScale(1.0);       // init scale
        score.setIrlsIters(8);     // IRLS
        score.setIrlsTol(1e-6);

        // ---- Effective sample size (optional) ----
        int nEff = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
        if (nEff > 0) score.setEffectiveSampleSize(nEff);

        // ---- Keep exposed ----
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT_DEFAULT_1));

        return score;
    }

    /**
     * Provides a description for the Minimax Legendre BIC score with mixed data.
     *
     * @return a string representing the description of the Minimax Legendre BIC score for mixed data
     */
    @Override
    public String getDescription() {
        return "Minimax Legendre BIC score";
    }

    /**
     * Retrieves the data type associated with the Minimax Legendre score.
     * This method determines whether the data type is continuous, discrete,
     * or mixed based on the nature of the variables in the dataset.
     *
     * @return the data type of the score, which is {@code DataType.Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves a list of parameter names required for the Minimax Legendre score computations.
     * These parameters are used to configure various aspects of the scoring process, including
     * degree, clipping level, ridge value, effective sample size, and penalty discount.
     *
     * @return a list of parameter names as strings, including:
     * - MINIMAX_LEGENDRE_DEGREE: Degree parameter for minimax legendre calculations.
     * - MINIMAX_LEGENDRE_CLIP: Clip level used during computations.
     * - MINIMAX_LEGENDRE_RIDGE: Ridge value for regularization.
     * - PENALTY_DISCOUNT: Penalty discount applied in score calculations.
     * - EFFECTIVE_SAMPLE_SIZE: Effective sample size for adjustments in scoring.
     */
    @Override
    public List<String> getParameters() {
        List<String> p = new ArrayList<>();
        p.add(Params.MINIMAX_LEGENDRE_DEGREE);
//        p.add(Params.MINIMAX_LEGENDRE_CLIP);
//        p.add(Params.MINIMAX_LEGENDRE_RIDGE);
        p.add(Params.PENALTY_DISCOUNT_DEFAULT_1);
        p.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return p;
    }

    /**
     * Retrieves a variable by its name from the underlying dataset.
     *
     * @param name the name of the variable to be retrieved
     * @return the {@code Node} corresponding to the specified variable name, or {@code null}
     * if the variable does not exist in the dataset
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}