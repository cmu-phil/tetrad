package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TestUnmix {

    @Test
    public void testUnmix() {
        // somewhere in your driver:
        ResidualRegressor reg = new LinearQRRegressor(); // or your Legendre version

        Function<DataSet, Graph> pooled = ds -> {
            try {
                // e.g., PC-Max on pooled data
                // return new PcMax(ds, new IndTest...(...)).search();
                IndTestFisherZ test = new IndTestFisherZ(ds, 0.01);
                Pc pc = new Pc(test);
                pc.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
                return pc.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Function<DataSet, Graph> perCluster = ds -> {
            if (ds.getNumRows() < 20) {
                // fall back to pooled graph or a lighter variant
                return pooled.apply(ds);
            }
            // e.g., BOSS on the subset
            SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
            score.setPenaltyDiscount(2);
            try {
                return new PermutationSearch(new Boss(score)).search();
//            return pooled.apply(ds);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;                    // or try selectK(...)
        cfg.doOneReassign = true;
        cfg.robustScaleResiduals = true;

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph g1 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        Graph g2 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);

        SemPm pm1 = new SemPm(g1);
        SemPm pm2 = new SemPm(g2);

        SemIm im1 = new SemIm(pm1);
        SemIm im2 = new SemIm(pm2);

        DataSet d1 = im1.simulateData(500, false);
        DataSet d2 = im2.simulateData(500, false);

        DataSet concat = DataTransforms.concatenate(d1, d2);

        UnmixResult result = UnmixCausalProcesses.run(concat, cfg, reg, pooled, perCluster);

        // Use results:
        for (int k = 0; k < result.K; k++) {
            DataSet dk = result.clusterData.get(k);
            Graph Gk = result.clusterGraphs.get(k);

            System.out.println("dataset = " + k);
            System.out.println(dk.getNumRows());
            System.out.println("graph = " + Gk);
        }
        // result.labels is length n: cluster id per original row
    }
}
