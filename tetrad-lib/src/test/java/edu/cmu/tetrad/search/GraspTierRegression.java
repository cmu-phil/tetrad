package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.List;

/** Regression: GRaSP on data, no knowledge vs tiers; tiers must not be violated in the output. */
public final class GraspTierRegression {
    private GraspTierRegression() {
    }

    public static void main(String[] args) throws Exception {
        int fails = 0;

        for (long seed : new long[]{3L, 17L, 271L}) {
            RandomUtil.getInstance().setSeed(seed);
            Graph trueDag = RandomGraph.randomGraph(10, 0, 14, 6, 4, 4, false);
            DataSet data = new SemIm(new SemPm(trueDag)).simulateData(1500, false);

            // Tiers from a valid order of the true DAG, split in half.
            List<Node> order = trueDag.paths().getValidOrder(trueDag.getNodes(), true);
            Knowledge tiers = new Knowledge();
            for (int i = 0; i < order.size(); i++) tiers.addToTier(i < order.size() / 2 ? 0 : 1, order.get(i).getName());

            // No knowledge.
            IndTestFisherZ t1 = new IndTestFisherZ(data, 0.01);
            SemBicScore s1 = new SemBicScore(new CovarianceMatrix(data));
            s1.setPenaltyDiscount(2);
            Grasp g1 = new Grasp(t1, s1);
            g1.setNumStarts(1);
            g1.bestOrder(s1.getVariables());
            Graph free = g1.getGraph(true);

            // Tiers.
            IndTestFisherZ t2 = new IndTestFisherZ(data, 0.01);
            SemBicScore s2 = new SemBicScore(new CovarianceMatrix(data));
            s2.setPenaltyDiscount(2);
            Grasp g2 = new Grasp(t2, s2);
            g2.setNumStarts(1);
            g2.setKnowledge(tiers);
            g2.bestOrder(s2.getVariables());
            Graph tiered = g2.getGraph(true);

            boolean violated = tiers.isViolatedBy(tiered);
            System.out.println("seed=" + seed + " noKnow edges=" + free.getNumEdges()
                    + " tiered edges=" + tiered.getNumEdges() + " tierViolated=" + violated);
            if (violated || tiered.getNumEdges() == 0) fails++;
        }

        System.out.println(fails == 0 ? "REGRESSION OK" : fails + " REGRESSION FAILURES");
        System.exit(fails == 0 ? 0 : 1);
    }
}
