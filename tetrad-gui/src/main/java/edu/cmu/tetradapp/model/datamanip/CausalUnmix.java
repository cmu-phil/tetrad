package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.unmix.CausalUnmixer;
import edu.cmu.tetrad.search.unmix.ParentSupersetBuilder;
import edu.cmu.tetrad.search.unmix.UnmixResult;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.io.Serial;
import java.util.List;
import java.util.function.Function;

/**
 * Causally unmixes data from mixed distributions, producing multiple datasets.
 */
public class CausalUnmix extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    public CausalUnmix(DataWrapper data, Parameters params) {
        if (data == null) throw new NullPointerException("The given data must not be null");

        DataModelList dataSets = data.getDataModelList();
        if (dataSets.size() != 1) {
            throw new IllegalArgumentException(
                    "For causal unmixing, need exactly one data set. This will split the dataset into multiple components."
            );
        }

        DataModel first = dataSets.getModelList().getFirst();
        if (!(first instanceof DataSet ds)) {
            throw new IllegalArgumentException("Only tabular data sets can be split.");
        }

        UnmixSpec spec = (UnmixSpec) params.get("unmixSpec");
        if (spec == null) {
            // fall back to defaults if UI didn't seed spec
            spec = new UnmixSpec();
            params.set("unmixSpec", spec);
        }

        final UnmixSpec theSpec = spec; // effectively final for inner class

        new WatchedProcess() {
            @Override
            public void watch() {
                // Build config from spec
                CausalUnmixer.Config cfg = mapSpecToConfig(theSpec);

                // Run the unmixer
                UnmixResult result = CausalUnmixer.getUnmixedResult(ds, cfg);

                // Name and return the split datasets
                List<DataSet> parts = result.clusterData;
                for (int i = 0; i < parts.size(); i++) {
                    parts.get(i).setName("Component " + (i + 1));
                }
                DataModelList out = new DataModelList();
                out.addAll(parts);
                setDataModel(out);
            }
        };
    }

    // ---------- Mapping UnmixSpec -> CausalUnmixer.Config ----------

    private static CausalUnmixer.Config mapSpecToConfig(UnmixSpec s) {
        CausalUnmixer.Config c = new CausalUnmixer.Config();

        // K handling
        if (s.isAutoSelectK()) {
            c.K = null;                 // enable selectK in CausalUnmixer
            c.Kmin = s.getKmin();
            c.Kmax = s.getKmax();
        } else {
            c.K = s.getK();
        }

        // Residual-EM options
        c.useParentSuperset = s.isUseParentSuperset();
        c.supersetTopM = s.getSupersetTopM();
        c.supersetScore = mapSupersetScore(s.getSupersetScore());
        c.robustScaleResiduals = s.isRobustScaleResiduals();

        // EM stability (names aligned to your Config)
        c.kmeansRestarts = s.getKmeansRestarts();
        c.emMaxIters     = s.getEmMaxIters();
        c.covRidgeRel    = s.getRidge();       // ridge on covariance
        c.covShrinkage   = s.getShrinkage();
        c.annealSteps    = s.getAnnealSteps();
        c.annealStartT   = s.getAnnealStartT();
        c.fullSigmaSafetyMargin = s.getFullSigmaSafetyMargin();

        // Regression (ridge for residual regressor)
        c.ridgeLambda = s.getRidge();

        // Graph learner
        switch (s.getGraphLearner()) {
            case PC_MAX -> {
                c.pcAlpha = s.getPcAlpha();
                c.pcColliderStyle = mapColliderStyle(s.getPcColliderStyle());
                c.pooledGraphFn = cfg -> makePcMax(cfg.pcAlpha, cfg.pcColliderStyle);
                c.perClusterGraphFn = cfg -> makePcMax(cfg.pcAlpha, cfg.pcColliderStyle);
            }
            case BOSS -> {
                double penalty = s.getBossPenaltyDiscount();
                c.pooledGraphFn = cfg -> makeBoss(penalty);
                c.perClusterGraphFn = cfg -> makeBoss(penalty);
            }
        }

        return c;
    }

    private static ParentSupersetBuilder.ScoreType mapSupersetScore(UnmixSpec.SupersetScore s) {
        return switch (s) {
            case KENDALL -> ParentSupersetBuilder.ScoreType.KENDALL;
            case SPEARMAN -> ParentSupersetBuilder.ScoreType.SPEARMAN;
        };
    }

    private static Pc.ColliderOrientationStyle mapColliderStyle(UnmixSpec.ColliderStyle s) {
        return switch (s) {
            case SEPSETS   -> Pc.ColliderOrientationStyle.SEPSETS;
            case CONSERVATIVE-> Pc.ColliderOrientationStyle.CONSERVATIVE;
            case MAX_P    -> Pc.ColliderOrientationStyle.MAX_P;
        };
    }

    // ---------- Graph builders (lightweight, no app dependencies) ----------

    private static Function<DataSet, Graph> makePcMax(double alpha, Pc.ColliderOrientationStyle style) {
        return ds -> {
            // Return an empty graph for tiny datasets to avoid fragile tests
            if (ds.getNumRows() < 10) return new EdgeListGraph(ds.getVariables());
            try {
                IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), alpha);
                Pc pc = new Pc(test);
                pc.setColliderOrientationStyle(style);
                return pc.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Function<DataSet, Graph> makeBoss(double penaltyDiscount) {
        return ds -> {
            try {
                if (ds.getNumRows() < 10) return new EdgeListGraph(ds.getVariables());
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(penaltyDiscount);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }
}