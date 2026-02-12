package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
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
 * Wrapper for Minimax-t Legendre BIC score (mixed).
 *
 * Exposed parameters (pared down):
 * - minimaxLegendreDegree (int, default 8)
 * - minimaxLegendreClip   (double, default 3.0)   [optional but practical]
 * - minimaxLegendreRidge  (double, default 1e-3)
 * - penaltyDiscount       (double)
 * - effectiveSampleSize   (int, optional; if <= 0, uses dataset n)
 *
 * Hidden/internal:
 * - minimaxLegendreNu (default 5.0)
 * - minimaxLegendreInitScale (default 1.0)
 * - minimaxLegendreIrlsIters (default 8)
 * - minimaxLegendreIrlsTol   (default 1e-6)
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Minimax Legendre Score",
        command = "minimax-legendre-score",
        dataType = {DataType.Mixed}
)
@General
@Mixed
//@Experimental
public class MinimaxLegendreScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    private DataModel dataSet;

    public MinimaxLegendreScore() {
    }

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

        // ---- Keep exposed (optional but practical): Clip ----
        double clip = parameters.getDouble(Params.MINIMAX_LEGENDRE_CLIP);
        if (!(clip > 0.0) || !Double.isFinite(clip)) clip = 3.0;
        score.setLegendreClip(clip);

        // ---- Keep exposed: Ridge ----
        double ridge = parameters.getDouble(Params.MINIMAX_LEGENDRE_RIDGE);
        if (!(ridge > 0.0) || !Double.isFinite(ridge)) ridge = 1e-3;
        score.setRidge(ridge);

        // ---- Hidden/internal defaults ----
        score.setNu(5.0);          // Student-t df
        score.setScale(1.0);       // init scale
        score.setIrlsIters(8);     // IRLS
        score.setIrlsTol(1e-6);

        // ---- Effective sample size (optional) ----
        int nEff = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
        if (nEff > 0) score.setEffectiveSampleSize(nEff);

        // ---- Keep exposed ----
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        return score;
    }

    @Override
    public String getDescription() {
        return "Minimax-t Legendre BIC score (mixed)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> p = new ArrayList<>();
        p.add(Params.MINIMAX_LEGENDRE_DEGREE);
        p.add(Params.MINIMAX_LEGENDRE_CLIP);
        p.add(Params.MINIMAX_LEGENDRE_RIDGE);
        p.add(Params.PENALTY_DISCOUNT);
        p.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return p;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}