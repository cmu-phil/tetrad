package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.work_in_progress.unmix.LinearQRRegressor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class Unmix {
    private static @NotNull UnmixResult getUnmixResult(TestUnmix.LabeledData mix) {
        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = 2;
        ec.useParentSuperset = true;
        ec.supersetCfg.topM = 12;
        ec.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        ec.robustScaleResiduals = true;
        ec.covType = GaussianMixtureEM.CovarianceType.FULL;
        ec.emMaxIters = 200;
        ec.kmeansRestarts = 10;

        return EmUnmix.run(mix.data, ec, new LinearQRRegressor(), pooled(), perCluster());
    }

    public static @NotNull UnmixResult getUnmixResult(DataSet data) {
        TestUnmix.LabeledData labeledData = new TestUnmix.LabeledData();
        labeledData.labels = null;
        labeledData.data = data;

        return getUnmixResult(labeledData);
    }

    public static @NotNull UnmixResult getUnmixResult(DataSet data, int[] labels) {
        TestUnmix.LabeledData labeledData = new TestUnmix.LabeledData();
        labeledData.labels = labels;
        labeledData.data = data;

        return getUnmixResult(labeledData);
    }

    public static Function<DataSet, Graph> pooled() {
        return ds -> {
            if (ds.getNumRows() < 50) {
                return null;
            }

            try {
                IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), 0.01);
                Pc pc = new Pc(test);
                pc.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
                return pc.search();

//                edu.cmu.tetrad.search.score.SemBicScore score = new edu.cmu.tetrad.search.score.SemBicScore(new CovarianceMatrix(ds));
//                score.setPenaltyDiscount(2);
//                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Function<DataSet, Graph> perCluster() {
        return pooled();
    }
}
