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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.*;

/**
 * GUI wrapper for a {@link HybridCgIm}. Mirrors BayesImWrapper responsibilities:
 * - Holds the instantiated parameters for the HybridCgPm (CPDs / CG params).
 * - Provides cloning & light metadata for GUI editors.
 *
 * NOTE: This wrapper does not modify the core HybridCgModel classes.
 */
public class HybridCgImWrapper implements SessionModel, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;
    private DataSet data;

    private HybridCgIm im;
    private String name = "Hybrid CG IM";
    private String notes = "";

    /** Construct from an existing IM. */
    public HybridCgImWrapper(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
    }

    /** Convenience: build a new HybridCgIm from a HybridCgPmWrapper. */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper) {
        this(pmWrapper, new Parameters());
    }

    /**
     * Construct from a HybridCgPmWrapper and Parameters.
     *
     * Supported keys:
     * - "hybridcg.defaultBins"       (int, default 3)  : #bins for default cutpoints when needed
     * - "hybridcg.defaultRangeLow"   (double, default -1.0)
     * - "hybridcg.defaultRangeHigh"  (double, default  1.0)
     * - "hybridcg.randomizeIm"       (boolean, default true)
     * - "hybridcg.randomSeed"        (long, default RandomUtil.nextLong)
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper, Parameters params) {
        Objects.requireNonNull(pmWrapper, "pmWrapper");
        Objects.requireNonNull(params, "params");

        HybridCgPm pm = pmWrapper.getHybridCgPm();

        // Ensure the PM has cutpoints for every discrete child with continuous parents,
        // otherwise HybridCgIm(pm) will fail when it queries PM shapes.
        final int bins = Math.max(2, params.getInt("hybridcg.defaultBins", 3));
        final double lo = params.getDouble("hybridcg.defaultRangeLow", -1.0);
        final double hi = params.getDouble("hybridcg.defaultRangeHigh", 1.0);
        ensureDefaultCutpoints(pm, bins, lo, hi);

        this.im = new HybridCgIm(pm);

        if (params.getBoolean("hybridcg.randomizeIm", true)) {
            long seed = params.getLong("hybridcg.randomSeed", RandomUtil.getInstance().nextLong());
            randomize(this.im, seed);
        }
    }

    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper, edu.cmu.tetrad.data.DataSet data, edu.cmu.tetrad.util.Parameters params) {
        Objects.requireNonNull(pmWrapper, "pmWrapper");
        HybridCgPm pm = pmWrapper.getHybridCgPm();

        // ---- read UI/estimator params with sane defaults ----
        final String binPolicy = params.getString("hybridcg.binPolicy", "equal_frequency"); // equal_frequency | equal_interval | none
        final int bins = Math.max(2, params.getInt("hybridcg.bins", 3));
        final double alpha = params.getDouble("hybridcg.alpha", 1.0);
        final boolean shareVar = params.getBoolean("hybridcg.shareVariance", false);

        // For "none", or if data is absent, we still must ensure cutpoints exist
        final int defaultBins = Math.max(2, params.getInt("hybridcg.defaultBins", 3));
        final double defLo = params.getDouble("hybridcg.defaultRangeLow", -1.0);
        final double defHi = params.getDouble("hybridcg.defaultRangeHigh",  1.0);

        // ---- ensure cutpoints on the PM ----
        try {
            if (data != null) {
                switch (binPolicy.toLowerCase(Locale.ROOT)) {
                    case "equal_interval" -> setCutpointsFromData(pm, data, bins, false);
                    case "equal_frequency" -> setCutpointsFromData(pm, data, bins, true);
                    case "none" -> ensureDefaultCutpoints(pm, defaultBins, defLo, defHi);
                    default -> setCutpointsFromData(pm, data, bins, true);
                }
            } else {
                // no data: make sure PM is still usable
                ensureDefaultCutpoints(pm, defaultBins, defLo, defHi);
            }
        } catch (Exception ex) {
            // As a last resort, keep the PM usable
            ensureDefaultCutpoints(pm, defaultBins, defLo, defHi);
        }

        // ---- build IM ----
        if (data != null) {
            // estimate from data
            HybridCgIm.HybridEstimator est = new HybridCgIm.HybridEstimator(alpha, shareVar);
            this.im = est.mle(pm, data);
        } else {
            // no data: create empty IM then (optionally) randomize
            this.im = new HybridCgIm(pm);
            if (params.getBoolean("hybridcg.randomizeIm", true)) {
                long seed = params.getLong("hybridcg.randomSeed", edu.cmu.tetrad.util.RandomUtil.getInstance().nextLong());
                randomize(this.im, seed); // uses your existing randomize(...) helper
            }
        }
    }

    // Ensure PM has uniform cutpoints per continuous parent of each discrete child
    private static void ensureDefaultCutpoints(HybridCgPm pm, int bins, double lo, double hi) {
        final double[] cuts = new double[bins - 1];
        for (int i = 0; i < cuts.length; i++) {
            cuts[i] = lo + (i + 1) * (hi - lo) / bins;
        }
        final edu.cmu.tetrad.graph.Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;
            java.util.Map<edu.cmu.tetrad.graph.Node, double[]> map = new java.util.LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                map.put(pm.getNodes()[cps[t]], cuts.clone());
            }
            pm.setContParentCutpointsForDiscreteChild(nodes[y], map);
        }
    }

    // Set cutpoints from data: equal-frequency (quantile-ish) or equal-interval per continuous parent
    private static void setCutpointsFromData(HybridCgPm pm, edu.cmu.tetrad.data.DataSet data, int bins, boolean equalFrequency) {
        final edu.cmu.tetrad.graph.Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            java.util.Map<edu.cmu.tetrad.graph.Node, double[]> map = new java.util.LinkedHashMap<>();

            for (int t = 0; t < cps.length; t++) {
                int parentIndex = cps[t];
                int col = data.getColumn(nodes[parentIndex]);
                double[] colData = new double[data.getNumRows()];
                for (int r = 0; r < data.getNumRows(); r++) {
                    colData[r] = data.getDouble(r, col);
                }

                double[] cuts;
                if (equalFrequency) {
                    // reuse Discretizer helper (returns length = bins-1)
                    cuts = edu.cmu.tetrad.data.Discretizer.getEqualFrequencyBreakPoints(colData, bins);
                } else {
                    double min = edu.cmu.tetrad.util.StatUtils.min(colData);
                    double max = edu.cmu.tetrad.util.StatUtils.max(colData);
                    cuts = new double[bins - 1];
                    double step = (max - min) / bins;
                    for (int k = 0; k < cuts.length; k++) cuts[k] = min + (k + 1) * step;
                }
                // guard: strictly increasing (nudge duplicates if any)
                for (int k = 1; k < cuts.length; k++) {
                    if (!(cuts[k] > cuts[k - 1])) cuts[k] = Math.nextUp(cuts[k - 1]);
                }
                map.put(nodes[parentIndex], cuts);
            }
            pm.setContParentCutpointsForDiscreteChild(nodes[y], map);
        }
    }

//    /**
//     * Ensure cutpoints exist for every DISCRETE child that has >=1 continuous parent.
//     * If none are present, we create equal-interval cutpoints on [low, high].
//     *
//     * @param bins number of bins (>=2). We will create (bins - 1) cutpoints.
//     */
//    private static void ensureDefaultCutpoints(HybridCgPm pm, int bins, double low, double high) {
//        if (bins < 2) bins = 2;
//        if (!(high > low)) { // degenerate range, fall back
//            low = -1.0; high = 1.0;
//        }
//
//        final var nodes = pm.getNodes();
//
//        for (int y = 0; y < nodes.length; y++) {
//            if (!pm.isDiscrete(y)) continue;
//
//            int[] cps = pm.getContinuousParents(y);
//            if (cps.length == 0) continue;
//
//            // Already has cutpoints?
//            if (pm.getContParentCutpointsForDiscreteChild(y).isPresent()) continue;
//
//            // Build equal-interval cutpoints for each continuous parent
//            Map<edu.cmu.tetrad.graph.Node, double[]> cpMap = new LinkedHashMap<>();
//            double[] edgesTemplate = new double[bins - 1];
//            double step = (high - low) / bins;
//            for (int i = 0; i < bins - 1; i++) edgesTemplate[i] = low + (i + 1) * step;
//
//            for (int t = 0; t < cps.length; t++) {
//                edu.cmu.tetrad.graph.Node p = nodes[cps[t]];
//                cpMap.put(p, edgesTemplate.clone());
//            }
//
//            try {
//                pm.setContParentCutpointsForDiscreteChild(nodes[y], cpMap);
//            } catch (IllegalArgumentException | IllegalStateException ignored) {
//                // If the PM rejects these (shouldn't), we skip; IM construction may still fail,
//                // but we tried to provide a safe default.
//            }
//        }
//    }

    /**
     * Randomize the IM parameters for testing or initialization.
     * Discrete CPT rows get Dirichlet(1) draws; continuous rows get small random coefficients and positive variance.
     */
    private static void randomize(HybridCgIm im, long seed) {
        Random rng = new Random(seed);
        HybridCgPm pm = im.getPm();
        int n = pm.getNodes().length;

        for (int y = 0; y < n; y++) {
            int rows = pm.getNumRows(y);
            if (pm.isDiscrete(y)) {
                int K = pm.getCardinality(y);
                for (int r = 0; r < rows; r++) {
                    // Dirichlet(1): normalize iid Exp(1)
                    double sum = 0.0;
                    double[] tmp = new double[K];
                    for (int k = 0; k < K; k++) {
                        double v = -Math.log(1.0 - rng.nextDouble());
                        tmp[k] = v;
                        sum += v;
                    }
                    for (int k = 0; k < K; k++) {
                        im.setProbability(y, r, k, tmp[k] / sum);
                    }
                }
            } else {
                int m = pm.getContinuousParents(y).length;
                for (int r = 0; r < rows; r++) {
                    im.setIntercept(y, r, rng.nextGaussian() * 0.25);
                    for (int j = 0; j < m; j++) {
                        im.setCoefficient(y, r, j, rng.nextGaussian() * 0.15);
                    }
                    double var = 0.25 + 0.75 * rng.nextDouble(); // (0.25, 1.0]
                    im.setVariance(y, r, var);
                }
            }
        }
    }

    // ---------- Accessors ----------
    public HybridCgIm getIm() { return im; }
    public void setIm(HybridCgIm im) { this.im = Objects.requireNonNull(im, "im"); }

    public HybridCgPm getPm() { return im != null ? im.getPm() : null; }
    public Graph getGraph() { return getPm() != null ? getPm().getGraph() : null; }

    public String getName() { return name; }
    public void setName(String name) { this.name = (name == null || name.isBlank()) ? "Hybrid CG IM" : name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = (notes == null) ? "" : notes; }

    /** Deep-ish copy: fresh IM bound to same PM (parameters not copied). */
    public HybridCgImWrapper deepCopy() {
        HybridCgPm pm = getPm();
        HybridCgIm newIm = new HybridCgIm(pm);
        HybridCgImWrapper w = new HybridCgImWrapper(newIm);
        w.name = this.name;
        w.notes = this.notes;
        return w;
    }

    @Override public HybridCgImWrapper clone() { return deepCopy(); }

    @Override
    public String toString() {
        Graph g = getGraph();
        String gname = (g == null ? "null" : g.toString());
        return String.format(Locale.US, "HybridCgImWrapper{name='%s', graph=%s}", name, gname);
    }

    // For your editors
    public HybridCgIm getHybridCgIm() { return im; }
}