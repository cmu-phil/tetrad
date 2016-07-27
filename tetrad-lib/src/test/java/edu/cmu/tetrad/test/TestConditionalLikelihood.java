package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditionalGaussianLikelihood;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.pitt.csb.mgm.MixedUtils;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * @author jdramsey
 */
public class TestConditionalLikelihood {

    @Test
    public void test1() {

        // Make a DAG 1->2 with 1 discrete and 2 continuous.
        Graph dag = new EdgeListGraph();

        Node n1 = new GraphNode("X0");
        Node n2 = new GraphNode("X1");

        dag.addNode(n1);
        dag.addNode(n2);

        dag.addDirectedEdge(n1, n2);

        // Simulate data from it using Lee & Hastie method.
        HashMap<String, Integer> nd = new HashMap<>();

        nd.put(n1.getName(), 3);
        nd.put(n2.getName(), 0);

        Graph graph = MixedUtils.makeMixedGraph(dag, nd);
        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(graph, "Split(-1.5,-.5,.5,1.5)");
        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
        DataSet data = MixedUtils.makeMixedData(im.simulateDataAvoidInfinity(100, false), nd);

        // Calculate lik and dof for 1 | 2, 2, 2 | 1, and 1.
        ConditionalGaussianLikelihood lik = new ConditionalGaussianLikelihood(data);

        ConditionalGaussianLikelihood.Ret ret1 = lik.getLikelihoodRatio(0, new int[]{1});
        ConditionalGaussianLikelihood.Ret ret2 = lik.getLikelihoodRatio(1, new int[]{});
        ConditionalGaussianLikelihood.Ret ret3 = lik.getLikelihoodRatio(1, new int[]{0});
        ConditionalGaussianLikelihood.Ret ret4 = lik.getLikelihoodRatio(0, new int[]{});

//        // Print out these likelihoods.
//        System.out.println(ret1);
//        System.out.println(ret2);
//        System.out.println(ret3);
//        System.out.println(ret4);
//
//        System.out.println();
//
//        // Print sum of 1 | 2 and 2 and sum of 2 | 1 and 1
//        System.out.println("SUM 1, 2 lik = " + (ret1.getLik() + ret2.getLik()) + " dof = " + (ret1.getDof() + ret2.getDof()));
//        System.out.println("SUM 3, 4 lik = " + (ret3.getLik() + ret4.getLik()) + " dof = " + (ret3.getDof() + ret4.getDof()));

        assertEquals(ret1.getLik() + ret2.getLik(), ret3.getLik() + ret4.getLik(), 0.001);
        assertEquals(ret1.getDof() + ret2.getDof(), ret3.getDof() + ret4.getDof(), 0.001);
    }
}
