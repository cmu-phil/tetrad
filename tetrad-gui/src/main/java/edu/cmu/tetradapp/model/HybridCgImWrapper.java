/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgEstimator;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * GUI wrapper for a {@link HybridCgIm}. Mirrors BayesImWrapper responsibilities: - Holds the instantiated parameters
 * for the HybridCgPm (CPDs / CG params). - Provides cloning & light metadata for GUI editors.
 * <p>
 * This wrapper delegates estimation & binning policy to {@link HybridCgEstimator} to avoid duplication and policy
 * drift.
 */
public class HybridCgImWrapper implements SessionModel, Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private HybridCgIm im;
    private String name = "Hybrid CG IM";
    private String notes = "";

//    /**
//     * No-arg for reflection/serialization frameworks.
//     */
//    public HybridCgImWrapper() {
//    }

    /**
     * Construct from an existing IM.
     */
    public HybridCgImWrapper(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
    }

    /**
     * Convenience: build a new HybridCgIm from a HybridCgPmWrapper using defaults (no data).
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper) {
        this(pmWrapper, new Parameters());
    }

    /**
     * Construct from a HybridCgPmWrapper and Parameters (no data).
     * <p>
     * Supported keys: - "hybridcg.randomizeIm"  (boolean, default true) - "hybridcg.randomSeed"   (long, default
     * RandomUtil.nextLong)
     * <p>
     * Cutpoint policy is not applied here (no data available). We require the PM to already have cutpoints wherever
     * needed, or we seed simple defaults. If you prefer to disallow seeding here, set "hybridcg.seedDefaults" to false
     * in the PM wrapper and ensure cutpoints before constructing the IM.
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper, Parameters params) {
        Objects.requireNonNull(pmWrapper, "pmWrapper");
        Objects.requireNonNull(params, "params");
        HybridCgPm pm = pmWrapper.getHybridCgPm();

        // If the PM lacks required cutpoints, try to seed simple defaults via the PM wrapper
        if (pmWrapper.hasDiscreteChildrenNeedingCutpoints()) {
            // Fail fast with a helpful message; the GUI can then open the cutpoint editor.
            throw new IllegalStateException("Cannot create HybridCgIm: some discrete children have continuous parents but no cutpoints.\n" + "Use the PM editor to set cutpoints from data or seed defaults.");
        }

        this.im = new HybridCgIm(pm);

        if (params.getBoolean("hybridcg.randomizeIm", true)) {
            long seed = params.getLong("hybridcg.randomSeed", RandomUtil.getInstance().nextLong());
            randomize(this.im, seed);
        }
    }

    /**
     * Construct from PM + Data + Parameters: estimate parameters using {@link HybridCgEstimator}.
     * <p>
     * Recognized keys (passed through to the estimator): - "hybridcg.alpha"         : double  (Dirichlet pseudocount;
     * default 1.0) - "hybridcg.shareVariance" : boolean (share variance across rows for cont child; default false) -
     * "hybridcg.binPolicy"     : String  ("equal_frequency", "equal_interval", "none"; default "equal_frequency") -
     * "hybridcg.bins"          : int     (#bins for each cont parent of a discrete child; default 3; min 2)
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper, DataSet data, Parameters params) {
        Objects.requireNonNull(pmWrapper, "pmWrapper");
        Objects.requireNonNull(data, "data");
        if (params == null) params = new Parameters();

        HybridCgPm pm = pmWrapper.getHybridCgPm();
        // Estimation: sets cutpoints (per policy) and returns the fitted IM
        this.im = HybridCgEstimator.estimate(pm, data, params);
    }

    // ---------- Utilities ----------

    /**
     * Randomize the IM parameters for testing or initialization. Discrete CPT rows get Dirichlet(1) draws; continuous
     * rows get small random coefficients and positive variance.
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
    public HybridCgIm getIm() {
        return im;
    }

    public void setIm(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
    }

    public HybridCgPm getPm() {
        return im != null ? im.getPm() : null;
    }

    public Graph getGraph() {
        return getPm() != null ? getPm().getGraph() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG IM" : name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = (notes == null) ? "" : notes;
    }

    /**
     * Deep-ish copy: fresh IM bound to the same PM (parameters not copied).
     */
    public HybridCgImWrapper deepCopy() {
        HybridCgPm pm = getPm();
        HybridCgIm newIm = new HybridCgIm(pm);
        HybridCgImWrapper w = new HybridCgImWrapper(newIm);
        w.name = this.name;
        w.notes = this.notes;
        return w;
    }

    @Override
    public HybridCgImWrapper clone() {
        return deepCopy();
    }

    @Override
    public String toString() {
        Graph g = getGraph();
        String gname = (g == null ? "null" : g.toString());
        return String.format(Locale.US, "HybridCgImWrapper{name='%s', graph=%s}", name, gname);
    }

    // Back-compat aliases for editors (optional)
    public HybridCgIm getHybridCgIm() {
        return im;
    }
}