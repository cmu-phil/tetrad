package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.search.unmix.CausalUnmixer;
import edu.cmu.tetrad.search.unmix.ParentSupersetBuilder;
import edu.cmu.tetrad.search.unmix.UnmixResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.io.Serial;
import java.util.List;

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
            throw new IllegalArgumentException("For causal unmixing, need exactly one data set.");
        }

        DataModel first = dataSets.getModelList().getFirst();
        if (!(first instanceof DataSet ds)) {
            throw new IllegalArgumentException("Only tabular data sets can be split.");
        }

        UnmixSpec spec = (UnmixSpec) params.get("unmixSpec");
        if (spec == null) {
            spec = new UnmixSpec();
            params.set("unmixSpec", spec);
        }

        final UnmixSpec theSpec = spec;

        new WatchedProcess() {
            @Override
            public void watch() {
                // Map UI spec -> low-level config
                CausalUnmixer.Config cfg = UnmixSpecMapper.mapSpecToConfig(theSpec);

                // Graph-less unmixing
                UnmixResult result = CausalUnmixer.getUnmixedResult(ds, cfg);

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

    /**
     * Simple mapper (extract what you need from the spec; no graph fields).
     */
    static final class UnmixSpecMapper {
        static CausalUnmixer.Config mapSpecToConfig(UnmixSpec s) {
            CausalUnmixer.Config c = new CausalUnmixer.Config();

            if (s.isAutoSelectK()) {
                c.K = null;
                c.Kmin = s.getKmin();
                c.Kmax = s.getKmax();
            } else {
                c.K = s.getK();
            }

            c.useParentSuperset = s.isUseParentSuperset();
            c.supersetTopM = s.getSupersetTopM();
            c.supersetScore = switch (s.getSupersetScore()) {
                case KENDALL -> ParentSupersetBuilder.ScoreType.KENDALL;
                case SPEARMAN -> ParentSupersetBuilder.ScoreType.SPEARMAN;
            };
            c.robustScaleResiduals = s.isRobustScaleResiduals();

            c.kmeansRestarts = s.getKmeansRestarts();
            c.emMaxIters = s.getEmMaxIters();
            c.covRidgeRel = s.getRidge();
            c.covShrinkage = s.getShrinkage();
            c.annealSteps = s.getAnnealSteps();
            c.annealStartT = s.getAnnealStartT();
            c.fullSigmaSafetyMargin = s.getFullSigmaSafetyMargin();

            c.ridgeLambda = s.getRidge();

            return c;
        }
    }
}