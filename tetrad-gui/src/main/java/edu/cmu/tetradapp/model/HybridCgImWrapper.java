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
 * for the HybridCgPm (CPDs / CG params). - Provides cloning &amp; light metadata for GUI editors.
 * <p>
 * This wrapper delegates estimation &amp; binning policy to {@link HybridCgEstimator} to avoid duplication and policy
 * drift.
 */
public class HybridCgImWrapper implements SessionModel, Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private HybridCgIm im;
    private String name = "Hybrid CG IM";
    private String notes = "";

    /**
     * Constructs a HybridCgImWrapper with the specified HybridCgIm instance.
     *
     * @param im the HybridCgIm instance to be wrapped, must not be null
     */
    public HybridCgImWrapper(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
    }

    /**
     * Constructs a HybridCgImWrapper using a specified HybridCgPmWrapper and default parameters.
     *
     * @param pmWrapper the HybridCgPmWrapper instance used to construct this HybridCgImWrapper
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper) {
        this(pmWrapper, new Parameters());
    }

    /**
     * Constructs a HybridCgImWrapper using a specified HybridCgPmWrapper and parameter settings.
     * Performs initialization and validation of required settings for creating the wrapper.
     *
     * @param pmWrapper the HybridCgPmWrapper instance used to construct this HybridCgImWrapper; must not be null.
     * @param params the Parameters instance containing configuration and settings for construction; must not be null.
     * @throws NullPointerException if pmWrapper or params is null.
     * @throws IllegalStateException if discrete children with continuous parents lack required cutpoints.
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
     * Constructs a HybridCgImWrapper using a specified HybridCgPmWrapper, a data set, and parameter settings.
     * Initializes the wrapper by estimating the internal model (IM) using the provided HybridCgPmWrapper, data, and parameters.
     *
     * @param pmWrapper the HybridCgPmWrapper instance used to construct this HybridCgImWrapper; must not be null.
     * @param data the DataSet instance used for estimation during construction; must not be null.
     * @param params the Parameters instance containing configuration and settings for construction; if null, default parameters are used.
     * @throws NullPointerException if pmWrapper or data is null.
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
                    im.setMean(y, r, rng.nextGaussian() * 0.25);
                    for (int j = 0; j < m; j++) {
                        im.setCoefficient(y, r, j, RandomUtil.getInstance().nextUniform(-1, 1));
                    }
                    double var = 0.25 + 0.75 * rng.nextDouble(); // (0.25, 1.0]
                    im.setVariance(y, r, var);
                }
            }
        }
    }

    /**
     * Retrieves the instance of HybridCgIm associated with this wrapper.
     *
     * @return the HybridCgIm instance currently wrapped by this HybridCgImWrapper
     */
    public HybridCgIm getIm() {
        return im;
    }

    /**
     * Sets the instance of HybridCgIm associated with this wrapper. The provided
     * instance must not be null.
     *
     * @param im the HybridCgIm instance to be set, must not be null
     * @throws NullPointerException if the specified HybridCgIm instance is null
     */
    public void setIm(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
    }

    /**
     * Retrieves the instance of HybridCgPm associated with the wrapped HybridCgIm,
     * if available. If the HybridCgIm is null, this method returns null.
     *
     * @return the HybridCgPm instance currently associated with the wrapped HybridCgIm, or null if the wrapped HybridCgIm is null
     */
    public HybridCgPm getPm() {
        return im != null ? im.getPm() : null;
    }

    /**
     * Retrieves the graphical representation associated with the current instance.
     * If the associated HybridCgPm object is null, this method returns null.
     *
     * @return the Graph instance associated with the current HybridCgImWrapper, or null if the associated HybridCgPm is null
     */
    public Graph getGraph() {
        return getPm() != null ? getPm().getGraph() : null;
    }

    /**
     * Retrieves the name associated with this instance.
     *
     * @return the name associated with this instance, or null if not set
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name associated with this instance. If the provided name is null or
     * blank, a default name "Hybrid CG IM" will be assigned.
     *
     * @param name the name to set; if null or blank, the default value will be used
     */
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG IM" : name;
    }

    /**
     * Retrieves the notes associated with this instance.
     *
     * @return the notes associated with this instance
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets the notes associated with this instance. If the provided notes
     * parameter is null, an empty string will be assigned instead.
     *
     * @param notes the notes to set; if null, an empty string will be used
     */
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

    /**
     * Creates a deep copy of this HybridCgImWrapper instance.
     *
     * @return a new HybridCgImWrapper instance with the same HybridCgIm and settings
     */
    @Override
    public HybridCgImWrapper clone() {
        return deepCopy();
    }

    /**
     * Returns a string representation of this HybridCgImWrapper instance.
     *
     * @return a string representation of this HybridCgImWrapper instance
     */
    @Override
    public String toString() {
        Graph g = getGraph();
        String gname = (g == null ? "null" : g.toString());
        return String.format(Locale.US, "HybridCgImWrapper{name='%s', graph=%s}", name, gname);
    }

    /**
     * Retrieves the HybridCgIm instance associated with this wrapper.
     *
     * @return the HybridCgIm instance associated with this wrapper
     */
    public HybridCgIm getHybridCgIm() {
        return im;
    }
}