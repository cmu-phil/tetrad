package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

/**
 * Simple façade for EM-based unmixing + per-cluster structure learning. Defaults: K=2, robust residual scaling,
 * parent-superset, PC-Max graphs.
 */
public class CausalUnmixer {

    /**
     * Convenience: run EM unmixing with sane defaults (K=2).
     */
    public static @NotNull UnmixResult getUnmixedResult(DataSet data) {
        return getUnmixedResult(data, null, defaults());
    }

    /**
     * Optional warm start: if labels != null and EM supports it, they’ll be used.
     */
    public static @NotNull UnmixResult getUnmixedResult(DataSet data, int[] labels) {
        return getUnmixedResult(data, labels, defaults());
    }

    public static @NotNull UnmixResult getUnmixedResult(DataSet data, @NotNull Config cfg) {
        return getUnmixedResult(data, null, cfg);
    }

    /**
     * Full control entry point.
     */
    public static @NotNull UnmixResult getUnmixedResult(
            DataSet data,
            int[] initLabels,
            @NotNull Config cfg
    ) {
        Objects.requireNonNull(data, "data");

        // ----- Build EM config from high-level config -----
        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 1));  // safe default
        ec.useParentSuperset = cfg.useParentSuperset;
        ec.supersetCfg.topM = cfg.supersetTopM;
        ec.supersetCfg.scoreType = cfg.supersetScore;
        ec.robustScaleResiduals = cfg.robustScaleResiduals;

        // Covariance policy
        int n = data.getNumRows();
        int p = data.getNumColumns();
        int K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 2));
        boolean okFull = (n / Math.max(1, K)) >= (p + cfg.fullSigmaSafetyMargin);
        ec.covType = okFull ? GaussianMixtureEM.CovarianceType.FULL
                : GaussianMixtureEM.CovarianceType.DIAGONAL;

        // EM stability knobs
        ec.kmeansRestarts = cfg.kmeansRestarts;
        ec.emMaxIters = cfg.emMaxIters;
        ec.covRidgeRel = cfg.covRidgeRel;
        ec.covShrinkage = cfg.covShrinkage;
        ec.annealSteps = cfg.annealSteps;
        ec.annealStartT = cfg.annealStartT;

//        // Optional warm start: if EmUnmix supports initLabels, set them here.
//        // (No-op if the field/method doesn’t exist in your EmUnmix.)
//        try {
//            ec.initLabels = initLabels; // comment this if your EmUnmix.Config doesn’t have it
//        } catch (Throwable ignore) { /* compatible with older EmUnmix */ }

        LinearQRRegressor reg = new LinearQRRegressor().setRidgeLambda(cfg.ridgeLambda);

        // ----- Run EM (fixed K or selectK by BIC) -----
        UnmixResult result;
        if (cfg.K != null) {
            result = EmUnmix.run(data, ec, reg, cfg.pooledGraphFn.apply(cfg), cfg.perClusterGraphFn.apply(cfg));
        } else {
            // select K in [Kmin, Kmax] via BIC using the same graph functions & EM defaults
            result = EmUnmix.selectK(data, cfg.Kmin, cfg.Kmax, reg,
                    cfg.pooledGraphFn.apply(cfg), cfg.perClusterGraphFn.apply(cfg), ec);
        }

        return result;
    }

    // --------- Defaults & Graph builders ---------

    public static @NotNull Config defaults() {
        Config c = new Config();

        c.K = 2;                          // set to null to enable selectK
        c.Kmin = 1;
        c.Kmax = 4;

        c.useParentSuperset = true;
        c.supersetTopM = 12;
        c.supersetScore = ParentSupersetBuilder.ScoreType.KENDALL;
        c.robustScaleResiduals = true;

        c.kmeansRestarts = 20;
        c.emMaxIters = 300;
        c.covRidgeRel = 1e-3;
        c.covShrinkage = 0.10;
        c.annealSteps = 15;
        c.annealStartT = 0.8;
        c.fullSigmaSafetyMargin = 10;     // requires n/K >= p + 10 for FULL Σ
        c.ridgeLambda = 1e-3;

        // Graphers
        c.pooledGraphFn = CausalUnmixer::pcMaxGrapher;
        c.perClusterGraphFn = CausalUnmixer::pcMaxGrapher;

        // PC alpha / style
        c.pcAlpha = 0.01;
        c.pcColliderStyle = Pc.ColliderOrientationStyle.MAX_P;

        return c;
    }

    /**
     * Returns a Function<DataSet, Graph> that runs PC-Max with FisherZ on the dataset.
     */
    private static Function<DataSet, Graph> pcMaxGrapher(Config cfg) {
        return ds -> {
            // Avoid NPEs downstream: return empty graph instead of null for tiny datasets.
            if (ds.getNumRows() < 50) return new EdgeListGraph(ds.getVariables());

            try {
                IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), cfg.pcAlpha);
                Pc pc = new Pc(test);
                pc.setColliderOrientationStyle(cfg.pcColliderStyle);
                return pc.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Function<DataSet, Graph> pooled() {
        return pcMaxGrapher(defaults());
    }

    public static Function<DataSet, Graph> perCluster() {
        return pcMaxGrapher(defaults());
    }

    // --------- Config holder ---------

    public static class Config {
        // K handling
        public Integer K = 2;          // if null, selectK is used
        public int Kmin = 1, Kmax = 4;

        // Residual-EM options
        public boolean useParentSuperset = true;
        public int supersetTopM = 12;
        public ParentSupersetBuilder.ScoreType supersetScore = ParentSupersetBuilder.ScoreType.KENDALL;
        public boolean robustScaleResiduals = true;

        // EM stability
        public int kmeansRestarts = 20;
        public int emMaxIters = 300;
        public double covRidgeRel = 1e-3;
        public double covShrinkage = 0.10;
        public int annealSteps = 15;
        public double annealStartT = 0.8;
        public int fullSigmaSafetyMargin = 10; // FULL Σ if n/K >= p + margin

        // Regression
        public double ridgeLambda = 1e-3;

        // Graphers
        public java.util.function.Function<Config, Function<DataSet, Graph>> pooledGraphFn;
        public java.util.function.Function<Config, Function<DataSet, Graph>> perClusterGraphFn;

        // PC settings
        public double pcAlpha = 0.01;
        public Pc.ColliderOrientationStyle pcColliderStyle = Pc.ColliderOrientationStyle.MAX_P;
    }
}