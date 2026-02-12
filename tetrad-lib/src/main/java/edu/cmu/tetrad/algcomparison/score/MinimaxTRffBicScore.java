package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
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

@edu.cmu.tetrad.annotation.Score(
        name = "Minimax tRFF BIC Score",
        command = "minimax-trff-bic-score",
        dataType = {DataType.Mixed}
)
@General
@Experimental
public class MinimaxTRffBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    private DataModel dataSet;

    public MinimaxTRffBicScore() { }

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        final edu.cmu.tetrad.search.score.MinimaxTRffBicScore score;
        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.MinimaxTRffBicScore((DataSet) dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        // Exposed knobs (stable + meaningful):
        score.setRidge(parameters.getDouble(Params.MINIMAX_RIDGE));
        score.setRffFeatures(parameters.getInt(Params.MINIMAX_FF_FEATURES));

        // Optional: expose nu if you truly want users to tune heavy-tail robustness.
        // If not, delete this line and just leave nu at the score's internal default.
        score.setNu(parameters.getDouble(Params.MINIMAX_NU));

        // Hidden knobs: set to sane internal defaults (do NOT expose in UI).
        // Keep these deterministic so runs are reproducible.
        score.setIrlsIters(8);

        // Prefer internal sigma selection (median heuristic etc.) rather than a UI parameter.
        // If the score currently REQUIRES sigma to be set, pick a safe sentinel and have the score auto-compute.
        // score.setRffSigma(Double.NaN); // recommended pattern if your score treats NaN as "auto"

        // Likewise: scale should be initialization / fallback only.
        // score.setScale(1.0);

        // If you want a penalty discount at all, make it a fixed policy, not a tuning knob.
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        return score;
    }

    @Override
    public String getDescription() {
        return "Minimax tRFF BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.MINIMAX_RIDGE);
        parameters.add(Params.MINIMAX_FF_FEATURES);
        parameters.add(Params.PENALTY_DISCOUNT);

        // Optional (keep only if you want this exposed):
        parameters.add(Params.MINIMAX_NU);

        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}