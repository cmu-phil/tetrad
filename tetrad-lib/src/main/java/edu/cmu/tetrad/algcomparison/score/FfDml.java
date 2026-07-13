///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.FfDmlContinuous;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>algcomparison score wrapper for {@link FfDmlContinuous} (FF-DML: Feature-Function Distributional
 * Marginal Likelihood).</p>
 *
 * <p>FF-DML generalizes the FF-ML GP-marginal-likelihood score: instead of scoring only the conditional
 * mean of the child (which, like a covariance/Fisher-Z score, is blind to parents that move only the
 * conditional variance, skew, or tails), it sums GP log marginal likelihoods over a battery of
 * orthogonal response channels {@code He_k(Y)/sqrt(k!)} (and optionally normal-scored ranks) sharing
 * the same parent features. Selecting {@code channels = MEAN} (with {@code exactFfmlBaseline = true})
 * recovers FF-ML. This score is continuous-only and general (nonlinear, non-Gaussian); it requires the
 * raw sample (no covariance-matrix branch).</p>
 *
 * <p>The FF-DML knobs below are read via defaulted getters under local parameter names, so this wrapper
 * compiles and runs against stock Tetrad without editing {@code Params.java}. To surface them in the
 * GUI, promote the names in {@link #getParameters()} to {@code Params} constants with matching
 * {@code ParamDescriptions} (defaults/min/max are documented on each constant below).</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Score(
//        name = "FF-DML (Distributional Marginal Likelihood)",
//        command = "ff-dml",
//        dataType = {DataType.Continuous}
//)
//@General
public class FfDml implements ScoreWrapper, Serializable {

    private static final long serialVersionUID = 23L;

    // -------------------- local parameter names (promote to Params for GUI) --------------------
    /** Number of random features m. Int, default 256, min 1. */
    private static final String FFDML_NUM_FEATURES = "ffdmlNumFeatures";
    /** Noise variance sigma^2 (the "lambda" ridge/noise knob). Double, default 1.0, min &gt; 0. */
    private static final String FFDML_LAMBDA = "ffdmlLambda";
    /** Bandwidth multiplier on the median heuristic. Double, default 1.0, min &gt; 0. */
    private static final String FFDML_BW_MULT = "ffdmlBandwidthMultiplier";
    /** Max rows used to estimate the median bandwidth. Int, default 400, min 50. */
    private static final String FFDML_BW_MAX_ROWS = "ffdmlBwMaxRows";
    /** Feature type: 1 = RFF, 2 = ORF. Int, default 2 (ORF). */
    private static final String FFDML_FEATURE_TYPE = "ffdmlFeatureType";
    /** Comma-separated channels from {MEAN, VAR, SKEW, KURT, RANK}. String, default "MEAN,VAR". */
    private static final String FFDML_CHANNELS = "ffdmlChannels";
    /** Comma-separated per-channel weights, aligned with the (de-duplicated) channel list. String,
     * default "1.0,1.0"; if it cannot be parsed to the right length, all weights fall back to 1.0. */
    private static final String FFDML_CHANNEL_WEIGHTS = "ffdmlChannelWeights";
    /** Use FF-ML's fixed-sigma^2 no-parent baseline (only for exact FF-ML reproduction with MEAN
     * alone). Boolean, default false (profiled, calibrated baseline). */
    private static final String FFDML_EXACT_BASELINE = "ffdmlExactFfmlBaseline";

    private DataModel dataSet;

    /**
     * Builds an {@link FfDmlContinuous} score from the data and parameters.
     *
     * @param dataSet    the (continuous) data set.
     * @param parameters the parameters.
     * @return the configured score.
     */
    @Override
    public edu.cmu.tetrad.search.score.Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        if (!(dataSet instanceof DataSet data)) {
            throw new IllegalArgumentException(
                    "FF-DML requires a tabular continuous DataSet (it needs the raw sample, not a covariance matrix).");
        }

        FfDmlContinuous score = new FfDmlContinuous(data);

        score.setNumFeatures(256);//parameters.getInt(FFDML_NUM_FEATURES, 256));
        score.setLambda(1.0);//parameters.getDouble(FFDML_LAMBDA, 1.0));
        score.setBandwidthMultiplier(1.0);//parameters.getDouble(FFDML_BW_MULT, 1.0));
        score.setBwMaxRows(400);//parameters.getInt(FFDML_BW_MAX_ROWS, 400));

        int ft = 2;//parameters.getInt(FFDML_FEATURE_TYPE, 2);
        score.setFeatureType(ft == 1
                ? FfDmlContinuous.FeatureType.RFF
                : FfDmlContinuous.FeatureType.ORF);

        // Channels first (this resets weights to 1.0), then read back the de-duplicated set so the
        // weight vector length always matches, then weights.
        FfDmlContinuous.Channel[] channels =
//                parseChannels(parameters.getString(FFDML_CHANNELS, "MEAN,VAR"));
                parseChannels("MEAN,VAR");
        score.setChannels(channels);

        FfDmlContinuous.Channel[] effective = score.getChannels();
        double[] weights = parseWeights("1.0,1.0", effective.length);
//                parseWeights(
//                parameters.getString(FFDML_CHANNEL_WEIGHTS, "1.0,1.0"), effective.length);
        score.setChannelWeights(weights);

        score.setExactFfmlBaseline(false);//parameters.getBoolean(FFDML_EXACT_BASELINE, false));

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FF-DML: Feature-Function Distributional Marginal Likelihood (GP form, continuous)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(FFDML_CHANNELS);
        parameters.add(FFDML_CHANNEL_WEIGHTS);
        parameters.add(FFDML_NUM_FEATURES);
        parameters.add(FFDML_LAMBDA);
        parameters.add(FFDML_BW_MULT);
        parameters.add(FFDML_BW_MAX_ROWS);
        parameters.add(FFDML_FEATURE_TYPE);
        parameters.add(FFDML_EXACT_BASELINE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        if (dataSet instanceof DataSet data) {
            for (Node node : data.getVariables()) {
                if (node.getName().equals(name)) {
                    return node;
                }
            }
        }
        return null;
    }

    // -------------------- parsing helpers --------------------

    private static FfDmlContinuous.Channel[] parseChannels(String s) {
        List<FfDmlContinuous.Channel> out = new ArrayList<>();
        if (s != null) {
            for (String tok : s.split(",")) {
                String t = tok.trim().toUpperCase();
                if (t.isEmpty()) continue;
                try {
                    out.add(FfDmlContinuous.Channel.valueOf(t));
                } catch (IllegalArgumentException ignored) {
                    // skip unrecognized channel names
                }
            }
        }
        if (out.isEmpty()) {
            out.add(FfDmlContinuous.Channel.MEAN);
            out.add(FfDmlContinuous.Channel.VAR);
        }
        return out.toArray(new FfDmlContinuous.Channel[0]);
    }

    private static double[] parseWeights(String s, int nChannels) {
        double[] fallback = new double[nChannels];
        for (int i = 0; i < nChannels; i++) fallback[i] = 1.0;
        if (s == null) return fallback;

        List<Double> vals = new ArrayList<>();
        for (String tok : s.split(",")) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            try {
                double d = Double.parseDouble(t);
                if (!(d >= 0) || !Double.isFinite(d)) return fallback;
                vals.add(d);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        if (vals.size() != nChannels) return fallback;

        double[] w = new double[nChannels];
        for (int i = 0; i < nChannels; i++) w[i] = vals.get(i);
        return w;
    }
}
