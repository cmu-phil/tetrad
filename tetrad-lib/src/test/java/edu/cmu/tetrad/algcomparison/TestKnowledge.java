package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

public class TestKnowledge {

    private static void testKnowledge(DataSet dataSet, Knowledge knowledge, Parameters parameters, HasKnowledge algorithm) {
        algorithm.setKnowledge(knowledge);
        Graph _graph = ((Algorithm) algorithm).search(dataSet, parameters);
        _graph = GraphUtils.replaceNodes(_graph, dataSet.getVariables());
        Node x1 = _graph.getNode("X1");
        List<Node> innodes = _graph.getNodesOutTo(x1, Endpoint.ARROW);
        assertTrue(innodes.isEmpty());
    }

    // Tests to make sure knowledge gets passed into the algcomparison wrappers for
    // all methods that take knowledge.
    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(3848283L);

        Graph graph = RandomGraph.randomGraph(10, 0, 10, 100, 1090, 100, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(100, false);

        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(1, "X1");
        for (int i = 2; i <= 10; i++) knowledge.addToTier(0, "X" + i);

        System.out.println(knowledge);

        IndependenceWrapper test = new FisherZ();
        ScoreWrapper score = new SemBicScore();
        Parameters parameters = new Parameters();

        testKnowledge(dataSet, knowledge, parameters, new Boss(score));
        testKnowledge(dataSet, knowledge, parameters, new Cpc(test));
        testKnowledge(dataSet, knowledge, parameters, new Fges(score));
        testKnowledge(dataSet, knowledge, parameters, new Grasp(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Pc(test));
        testKnowledge(dataSet, knowledge, parameters, new Sp(score));

        testKnowledge(dataSet, knowledge, parameters, new Bfci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Fci(test));
        testKnowledge(dataSet, knowledge, parameters, new FciMax(test));
        testKnowledge(dataSet, knowledge, parameters, new Gfci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new GraspFci(test, score));
        testKnowledge(dataSet, knowledge, parameters, new Rfci(test));
        testKnowledge(dataSet, knowledge, parameters, new SpFci(test, score));
    }
}
