package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Pc;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

public class CausalUnmixer {

    public static @NotNull UnmixResult getUnmixedResult(DataSet data) {
        return getUnmixedResult(data, defaults());
    }

    /**
     * NEW: graph-less entry point
     */
    public static @NotNull UnmixResult getUnmixedResult(DataSet data, @NotNull Config cfg) {
        Objects.requireNonNull(data, "data");

        EmUnmix.Config ec = toEmConfig(cfg, data);
        LinearQRRegressor reg = new LinearQRRegressor().setRidgeLambda(cfg.ridgeLambda);

        if (cfg.K != null) {
            return EmUnmix.run(data, ec, reg);                    // no graphs
        } else {
            return EmUnmix.selectK(data, cfg.Kmin, cfg.Kmax, reg, ec); // no graphs
        }
    }

    // If you still want the older API with graphers, keep your previous overloads here.

    private static EmUnmix.Config toEmConfig(Config cfg, DataSet data) {
        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 1));

        // Covariance policy
        int n = data.getNumRows();
        int p = data.getNumColumns();
        int K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 2));
        boolean okFull = (n / Math.max(1, K)) >= (p + cfg.fullSigmaSafetyMargin);
        ec.covType = okFull ? GaussianMixtureEM.CovarianceType.FULL
                : GaussianMixtureEM.CovarianceType.DIAGONAL;

        // Residual options
        ec.useParentSuperset = cfg.useParentSuperset;
        ec.supersetCfg.topM = cfg.supersetTopM;
        ec.supersetCfg.scoreType = cfg.supersetScore;
        ec.robustScaleResiduals = cfg.robustScaleResiduals;

        // EM stability
        ec.kmeansRestarts = cfg.kmeansRestarts;
        ec.emMaxIters = cfg.emMaxIters;
        ec.ridge = cfg.covRidgeRel;          // absolute ridge (kept for compat)
        ec.covRidgeRel = cfg.covRidgeRel;    // relative ridge
        ec.covShrinkage = cfg.covShrinkage;
        ec.annealSteps = cfg.annealSteps;
        ec.annealStartT = cfg.annealStartT;

        return ec;
    }

    // defaults() & Config unchanged (you can keep them as you already have)
    public static @NotNull Config defaults() { /* … your existing defaults … */
        return new Config();
    }

    public static class Config {
        public Integer K = 2;
        public int Kmin = 1, Kmax = 4;
        public boolean useParentSuperset = true;
        public int supersetTopM = 12;
        public ParentSupersetBuilder.ScoreType supersetScore = ParentSupersetBuilder.ScoreType.KENDALL;
        public boolean robustScaleResiduals = true;

        public int kmeansRestarts = 20, emMaxIters = 300;
        public double covRidgeRel = 1e-3, covShrinkage = 0.10;
        public int annealSteps = 15;
        public double annealStartT = 0.8;
        public int fullSigmaSafetyMargin = 10;

        public double ridgeLambda = 1e-3;

        // (graph fields can stay but won’t be used by the graph-less path)
        public java.util.function.Function<Config, Function<DataSet, Graph>> pooledGraphFn;
        public java.util.function.Function<Config, Function<DataSet, Graph>> perClusterGraphFn;

        public double pcAlpha = 0.01;
        public Pc.ColliderOrientationStyle pcColliderStyle = Pc.ColliderOrientationStyle.MAX_P;
    }
}