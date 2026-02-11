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
 * Exposed parameters:
 * - minimaxLegendreDegree (int, default 8)
 * - minimaxLegendreClip (double, default 3.0)
 * - minimaxLegendreRidge (double, default 1e-3)
 * - minimaxLegendreNu (double, default 5.0)
 * - minimaxLegendreInitScale (double, default 1.0)
 * - minimaxLegendreIrlsIters (int, default 8)
 * - minimaxLegendreIrlsTol (double, default 1e-6)
 * - effectiveSampleSize (int, optional; if <= 0, uses dataset n)
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Minimax Legendre Score",
        command = "minimax-legendre-score",
        dataType = {DataType.Mixed}
)
@General
@Mixed
@Experimental
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

        edu.cmu.tetrad.search.score.MinimaxLegendreScore score = new edu.cmu.tetrad.search.score.MinimaxLegendreScore(ds);

        // ---- Degree (t): features per continuous parent ----
        int degree = parameters.getInt(Params.MINIMAX_LEGENDRE_DEGREE);
        if (degree <= 0) degree = 8;
        score.setLegendreDegree(degree);

        // ---- Clip: maps z to [-1,1] via clamp(z/clip) ----
        double clip = parameters.getDouble(Params.MINIMAX_LEGENDRE_CLIP);
        if (!(clip > 0.0) || !Double.isFinite(clip)) clip = 3.0;
        score.setLegendreClip(clip);

        // ---- Ridge ----
        double ridge = parameters.getDouble(Params.MINIMAX_LEGENDRE_RIDGE);
        if (!(ridge > 0.0) || !Double.isFinite(ridge)) ridge = 1e-3;
        score.setRidge(ridge);

        // ---- Student-t df (nu) ----
        double nu = parameters.getDouble(Params.MINIMAX_LEGENDRE_NU);
        if (!(nu > 2.0) || !Double.isFinite(nu)) nu = 5.0;
        score.setNu(nu);

        // ---- Initial scale guess (used inside IRLS) ----
        double initScale = parameters.getDouble(Params.MINIMAX_LEGENDRE_INIT_SCALE);
        if (!(initScale > 0.0) || !Double.isFinite(initScale)) initScale = 1.0;
        score.setScale(initScale);

        // ---- IRLS controls ----
        int iters = parameters.getInt(Params.MINIMAX_LEGENDRE_IRLS_ITERS);
        if (iters <= 0) iters = 8;
        score.setIrlsIters(iters);

        double tol = parameters.getDouble(Params.MINIMAX_LEGENDRE_IRLS_TOL);
        if (!(tol >= 0.0) || !Double.isFinite(tol)) tol = 1e-6;
        score.setIrlsTol(tol);

        // ---- Effective sample size (optional) ----
        int nEff = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
        if (nEff > 0) score.setEffectiveSampleSize(nEff);

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
        p.add(Params.MINIMAX_LEGENDRE_NU);
        p.add(Params.MINIMAX_LEGENDRE_INIT_SCALE);
        p.add(Params.MINIMAX_LEGENDRE_IRLS_ITERS);
        p.add(Params.MINIMAX_LEGENDRE_IRLS_TOL);
        p.add(Params.PENALTY_DISCOUNT);

        p.add(Params.EFFECTIVE_SAMPLE_SIZE);

        return p;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}