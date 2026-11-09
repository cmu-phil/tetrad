package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BossTest {

    @Test
    public void testBossBehavior() throws InterruptedException {
        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_RUNS, 1);
        parameters.set(Params.NUM_MEASURES, 20);
        parameters.set(Params.AVG_DEGREE, 3);
        parameters.set(Params.SAMPLE_SIZE, 1000);
        parameters.set(Params.COEF_LOW, 0.5);
        parameters.set(Params.COEF_HIGH, 1.5);
        parameters.set(Params.VAR_LOW, 1.0);
        parameters.set(Params.VAR_HIGH, 3.0);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);

        SemSimulation simulation = new SemSimulation(new RandomForward());
        simulation.createData(parameters, true);
        DataSet dataSet = (DataSet) simulation.getDataModel(0);

        SemBicScore score = new SemBicScore(dataSet, true);
        score.setPenaltyDiscount(2.0);

        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setNumThreads(1); // Sequential first

        List<Node> variables = dataSet.getVariables();
        List<Node> suborder = new ArrayList<>(variables);
        Map<Node, GrowShrinkTree> gsts = new HashMap<>();
        Map<Node, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), i);
        }

        for (Node node : variables) {
            gsts.put(node, new GrowShrinkTree(score, indexMap, node));
        }

        List<Node> suborder1 = new ArrayList<>(suborder);
        boss.searchSuborder(new ArrayList<>(), suborder1, gsts);
        double score1 = scoreSuborder(score, suborder1);
        System.out.println("[DEBUG_LOG] Sequential score: " + score1);

        // Reset GSTs for parallel run
        for (GrowShrinkTree gst : gsts.values()) {
            gst.reset();
        }

        boss.setNumThreads(4);
        List<Node> suborder2 = new ArrayList<>(suborder);
        boss.searchSuborder(new ArrayList<>(), suborder2, gsts);
        double score2 = scoreSuborder(score, suborder2);
        System.out.println("[DEBUG_LOG] Parallel score: " + score2);

        // For this small case with useDataOrder=true and numStarts=1, they should be exactly the same
        assertEquals(score1, score2, 1e-6);
    }

    private double scoreSuborder(SemBicScore score, List<Node> suborder) {
        double totalScore = 0;
        List<Node> prefix = new ArrayList<>();
        for (Node node : suborder) {
            totalScore += score.localScore(score.getVariables().indexOf(node), getIndices(score, prefix));
            prefix.add(node);
        }
        return totalScore;
    }

    private int[] getIndices(SemBicScore score, List<Node> prefix) {
        int[] indices = new int[prefix.size()];
        for (int i = 0; i < prefix.size(); i++) {
            indices[i] = score.getVariables().indexOf(prefix.get(i));
        }
        return indices;
    }
}
