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
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

public class TestKnowledge {

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

        testKnowledge(dataSet, knowledge, parameters, new BOSS(score));
        testKnowledge(dataSet, knowledge, parameters, new CPC(test));
        testKnowledge(dataSet, knowledge, parameters, new CPC_STABLE(test));
        testKnowledge(dataSet, knowledge, parameters, new FGES(score));
        testKnowledge(dataSet, knowledge, parameters, new GRASP(test, score));
        testKnowledge(dataSet, knowledge, parameters, new PC_STABLE(test));
        testKnowledge(dataSet, knowledge, parameters, new PC(test));
        testKnowledge(dataSet, knowledge, parameters, new PCMAX(test));
        testKnowledge(dataSet, knowledge, parameters, new SP(score));

        testKnowledge(dataSet, knowledge, parameters, new BFCI(test, score));
        testKnowledge(dataSet, knowledge, parameters, new CFCI(test));
        testKnowledge(dataSet, knowledge, parameters, new FCI(test));
        testKnowledge(dataSet, knowledge, parameters, new FCI_MAX(test));
        testKnowledge(dataSet, knowledge, parameters, new GFCI(test, score));
        testKnowledge(dataSet, knowledge, parameters, new SP(score));
        testKnowledge(dataSet, knowledge, parameters, new GRASP_FCI(test, score));
        testKnowledge(dataSet, knowledge, parameters, new RFCI(test));
        testKnowledge(dataSet, knowledge, parameters, new SP_FCI(test, score));
    }

    private static void testKnowledge(DataSet dataSet, Knowledge knowledge, Parameters parameters, HasKnowledge algorithm) {
        algorithm.setKnowledge(knowledge);
        Graph _graph = ((Algorithm) algorithm).search(dataSet, parameters);
        Node x1 = _graph.getNode("X1");
        List<Node> innodes = _graph.getNodesOutTo(x1, Endpoint.ARROW);
        assertTrue(innodes.isEmpty());
    }
}
