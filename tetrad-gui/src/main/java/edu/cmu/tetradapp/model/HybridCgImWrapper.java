package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * GUI wrapper for a {@link HybridCgIm}.
 * Mirrors BayesImWrapper responsibilities:
 *  - Holds the instantiated parameters for the HybridCgPm (CPDs / CG params).
 *  - Provides cloning & light metadata for GUI editors.
 *
 * NOTE: This wrapper does not modify the core HybridCgModel classes.
 */
public class HybridCgImWrapper implements SessionModel, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private HybridCgIm im;
    private String name = "Hybrid CG IM";
    private String notes = "";

//    /** No-arg for reflection/persistence. Caller must set the IM before use. */
//    public HybridCgImWrapper() { }

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
     * Supported optional keys:
     *  - "hybridcg.randomizeIm" : boolean, if true initializes IM with random parameters
     *  - "hybridcg.randomSeed"  : long seed used when randomizing (default 123)
     */
    public HybridCgImWrapper(HybridCgPmWrapper pmWrapper, Parameters params) {
        Objects.requireNonNull(pmWrapper, "pmWrapper");
        Objects.requireNonNull(params, "params");
        HybridCgPm pm = pmWrapper.getHybridCgPm();
        this.im = new HybridCgIm(pm);

        if (params.getBoolean("hybridcg.randomizeIm", false)) {
            long seed = params.getLong("hybridcg.randomSeed", 123L);
            randomize(this.im, seed);
        }
    }

    // ---------- Accessors ----------
    public HybridCgIm getIm() { return im; }
    public void setIm(HybridCgIm im) { this.im = Objects.requireNonNull(im, "im"); }

    public HybridCgPm getPm() { return im != null ? im.getPm() : null; }

    public Graph getGraph() {
        HybridCgPm pm = getPm();
        return pm != null ? pm.getGraph() : null;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = (name == null || name.isBlank()) ? "Hybrid CG IM" : name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = (notes == null) ? "" : notes; }

    /**
     * Deep-ish copy: allocates a fresh IM bound to the same PM.
     * (Parameter tensors are not copied here — extend if needed.)
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

    // ---------- Utilities ----------
    /**
     * Randomize the IM parameters for testing or initialization.
     * Discrete CPT rows get Dirichlet(1) draws; continuous rows get small random coefficients
     * and positive variance.
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
                    // Dirichlet(1) ~ normalized iid Exp(1)
                    double sum = 0.0;
                    double[] tmp = new double[K];
                    for (int k = 0; k < K; k++) {
                        double v = -Math.log(1.0 - rng.nextDouble()); // Exp(1)
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

    public HybridCgIm getHybridCgIm() {
        return im;
    }
}