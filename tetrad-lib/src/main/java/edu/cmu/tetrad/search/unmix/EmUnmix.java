package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;

import java.util.*;
import java.util.function.Function;

public final class EmUnmix {

    public static final class Config {
        public int K;
        public boolean useParentSuperset = false;
        public ParentSupersetBuilder.Config supersetCfg = new ParentSupersetBuilder.Config();
        public boolean robustScaleResiduals = true;

        public GaussianMixtureEM.CovarianceType covType = GaussianMixtureEM.CovarianceType.DIAGONAL;
        public int emMaxIters = 200;
        public double emTol = 1e-5;
        public long seed = 13L;
        public double ridge = 1e-6;
        public int kmeansRestarts = 5;
        public boolean useMAP = true; // hard-assign by MAP; set false if you plan weighted searches
        public double covRidgeRel;
        public double covShrinkage;
        public int annealSteps;
        public double annealStartT;
    }

    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null if useParentSuperset=true
            Function<DataSet, Graph> perClusterSearch
    ) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(regressor);
        Objects.requireNonNull(perClusterSearch);

        // 1) Build residual signatures (same initializer options as your pipeline)
        double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
        if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

        // 2) EM on residuals
        GaussianMixtureEM.Config emc = new GaussianMixtureEM.Config();
        emc.K = cfg.K;
        emc.covType = cfg.covType;
        emc.maxIters = cfg.emMaxIters;
        emc.tol = cfg.emTol;
        emc.seed = cfg.seed;
        emc.ridge = cfg.ridge;
        emc.kmeansRestarts = cfg.kmeansRestarts;

        GaussianMixtureEM.Model model = GaussianMixtureEM.fit(R, emc);
        int[] z = cfg.useMAP ? EmUtils.mapLabels(model.responsibilities)
                : EmUtils.mapLabels(model.responsibilities); // (placeholder if you wire weighted search later)

        // 3) Split & per-cluster search
        List<DataSet> parts = splitByLabels(data, z, cfg.K);
        List<Graph> graphs = searchPerCluster(parts, perClusterSearch);

        return new UnmixResult(z, cfg.K, parts, graphs);
    }

    /** Optional: scan K by BIC on residuals. */
    public static UnmixResult selectK(
            DataSet data, int Kmin, int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch,
            Config base
    ) {
        double bestBIC = Double.NEGATIVE_INFINITY;
        UnmixResult best = null;
        for (int K = Kmin; K <= Kmax; K++) {
            Config cfg = copy(base); cfg.K = K;
            // Build residuals once (so BIC compares apples-to-apples)
            double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
            if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

            GaussianMixtureEM.Config emc = new GaussianMixtureEM.Config();
            emc.K = K; emc.covType = cfg.covType; emc.maxIters = cfg.emMaxIters; emc.tol = cfg.emTol;
            emc.seed = cfg.seed; emc.ridge = cfg.ridge; emc.kmeansRestarts = cfg.kmeansRestarts;

            GaussianMixtureEM.Model m = GaussianMixtureEM.fit(R, emc);
            double bic = EmUtils.bic(m, R.length);
            if (bic > bestBIC) {
                bestBIC = bic;
                int[] z = EmUtils.mapLabels(m.responsibilities);
                List<DataSet> parts = splitByLabels(data, z, K);
                List<Graph> graphs = searchPerCluster(parts, perClusterSearch);
                best = new UnmixResult(z, K, parts, graphs);
            }
        }
        return best;
    }

    // ----- helpers (mirror your existing ones) -----

    private static double[][] buildResiduals(
            DataSet data, Config cfg, ResidualRegressor regressor, Function<DataSet, Graph> pooledSearch) {
        if (cfg.useParentSuperset) {
            var pa = ParentSupersetBuilder.build(data, cfg.supersetCfg);
            return ResidualUtils.residualMatrix(data, pa, regressor);
        } else {
            Objects.requireNonNull(pooledSearch, "pooledSearch must be provided when useParentSuperset=false");
            Graph gPool = pooledSearch.apply(data);
            return ResidualUtils.residualMatrix(data, gPool, regressor);
        }
    }

    private static List<Graph> searchPerCluster(List<DataSet> parts, Function<DataSet, Graph> perClusterSearch) {
        List<Graph> graphs = new ArrayList<>(parts.size());
        for (DataSet dk : parts) {
            if (dk == null || dk.getNumRows() == 0) graphs.add(null);
            else graphs.add(perClusterSearch.apply(dk));
        }
        return graphs;
    }

    private static List<DataSet> splitByLabels(DataSet data, int[] z, int K) {
        int n = data.getNumRows();
        List<List<Integer>> idx = new ArrayList<>(K);
        for (int k = 0; k < K; k++) idx.add(new ArrayList<>());
        for (int i = 0; i < n; i++) { int lab = z[i]; if (lab >= 0 && lab < K) idx.get(lab).add(i); }
        List<DataSet> out = new ArrayList<>(K);
        for (int k = 0; k < K; k++) {
            List<Integer> rows = idx.get(k);
            if (rows.isEmpty()) {
                DoubleDataBox box = new DoubleDataBox(0, data.getVariables().size());
                out.add(new BoxDataSet(box, data.getVariables()));
            } else {
                int[] rowsArray = rows.stream().mapToInt(Integer::intValue).toArray();
                out.add(data.subsetRows(rowsArray));
            }
        }
        return out;
    }

    private static Config copy(Config c) {
        Config d = new Config();
        d.K = c.K;
        d.useParentSuperset = c.useParentSuperset;
        d.supersetCfg = ParentSupersetBuilderCopy.copy(c.supersetCfg);
        d.robustScaleResiduals = c.robustScaleResiduals;
        d.covType = c.covType; d.emMaxIters = c.emMaxIters; d.emTol = c.emTol;
        d.seed = c.seed; d.ridge = c.ridge; d.kmeansRestarts = c.kmeansRestarts; d.useMAP = c.useMAP;
        return d;
    }

    /** small internal copier to avoid sharing superset config across trials */
    private static final class ParentSupersetBuilderCopy {
        static ParentSupersetBuilder.Config copy(ParentSupersetBuilder.Config c) {
            ParentSupersetBuilder.Config d = new ParentSupersetBuilder.Config();
            d.topM = c.topM; d.scoreType = c.scoreType; d.useBagging = c.useBagging;
            d.bags = c.bags; d.bagFraction = c.bagFraction; d.seed = c.seed; d.shallowSearch = c.shallowSearch;
            return d;
        }
    }
}