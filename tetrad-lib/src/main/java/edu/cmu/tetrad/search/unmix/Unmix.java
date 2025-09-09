package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.work_in_progress.unmix.LinearQRRegressor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * The Unmix class provides methods to perform clustering and structure learning on datasets.
 * It combines Gaussian Mixture Models (GMMs) and graph learning techniques to partition data
 * into clusters and compute cluster-specific causal graphs.
 */
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

    /**
     * Computes the unmixing result by clustering and structure learning on the provided dataset.
     *
     * @param data the dataset to be analyzed, containing observations to be clustered.
     * @return an UnmixResult object, which contains the clustering results,
     *         including labels, cluster-specific datasets, and learned graphs.
     */
    public static @NotNull UnmixResult getUnmixResult(DataSet data) {
        TestUnmix.LabeledData labeledData = new TestUnmix.LabeledData();
        labeledData.labels = null;
        labeledData.data = data;

        return getUnmixResult(labeledData);
    }

    /**
     * Computes the unmixing result by performing clustering and structure learning on the provided dataset
     * and its associated labels.
     *
     * @param data   the dataset to be analyzed, containing observations to be clustered.
     * @param labels an array of integers representing the initial labels for the dataset,
     *               where each value corresponds to the cluster assignment of a data point.
     * @return an UnmixResult object, which contains the clustering results,
     *         including updated labels, cluster-specific datasets, and learned graphs.
     */
    public static @NotNull UnmixResult getUnmixResult(DataSet data, int[] labels) {
        TestUnmix.LabeledData labeledData = new TestUnmix.LabeledData();
        labeledData.labels = labels;
        labeledData.data = data;

        return getUnmixResult(labeledData);
    }

    /**
     * Creates a function that processes a DataSet to generate a causal graph using the PC algorithm,
     * based on a Fisher Z-test of independence. The function imposes a row limit to ensure the dataset
     * has a sufficient number of observations to produce meaningful results. If the dataset contains
     * fewer than 50 rows, it returns null. If processing is interrupted, a RuntimeException is thrown.
     *
     * @return a Function that takes a DataSet as input and produces a Graph as output, or null if
     *         the dataset does not meet the minimum size requirement.
     */
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

    /**
     * Creates a function that processes a DataSet to generate a causal graph for each cluster
     * by leveraging clustering and structure learning algorithms. This method is intended
     * to be used on clustered datasets to produce cluster-specific graphs.
     *
     * @return a Function that takes a DataSet as input and produces a Graph as output for each cluster.
     */
    public static Function<DataSet, Graph> perCluster() {
        return pooled();
    }
}
