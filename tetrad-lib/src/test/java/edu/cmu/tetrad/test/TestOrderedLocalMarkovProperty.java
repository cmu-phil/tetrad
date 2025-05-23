package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.OrderedLocalMarkovProperty;
import edu.cmu.tetrad.search.test.IndTestIndependenceFacts;
import org.junit.Test;

import java.util.Set;

public class TestOrderedLocalMarkovProperty {

    @Test
    public void test1() {
        Graph g = RandomGraph.randomGraph(10, 2, 10, 100, 100, 100, false);
        Graph pag = GraphTransforms.dagToPag(g);
        Graph mag = GraphTransforms.magFromPag(pag);

        Set<IndependenceFact> im = OrderedLocalMarkovProperty.getModel(mag);

        for (IndependenceFact fact : im) {
            System.out.println(fact);
        }
    }

    @Test
    public void test2() {
        Graph mag = RandomGraph.randomGraph(10, 0, 0, 100, 100, 100, false);

        System.out.println(mag);

        mag.addDirectedEdge(mag.getNode("X1"), mag.getNode("X2"));
        mag.addBidirectedEdge(mag.getNode("X2"), mag.getNode("X3"));
        mag.addBidirectedEdge(mag.getNode("X3"), mag.getNode("X4"));
        mag.addBidirectedEdge(mag.getNode("X4"), mag.getNode("X5"));
        mag.addDirectedEdge(mag.getNode("X6"), mag.getNode("X5"));
        mag.addDirectedEdge(mag.getNode("X7"), mag.getNode("X2"));
        mag.addDirectedEdge(mag.getNode("X7"), mag.getNode("X3"));

        Set<IndependenceFact> im = OrderedLocalMarkovProperty.getModel(mag);

        for (IndependenceFact fact : im) {
            System.out.println(fact);
        }
    }

    @Test
    public void test3() {
        Graph[] mags = new Graph[]{
                GraphUtils.convert("a-->b,b<->c,c<--d"),
                GraphUtils.convert("a-->b,b<->c,c<->d,b-->d"),
                GraphUtils.convert("e-->d,a-->b,b<->c,c<->d"),
                GraphUtils.convert("a<->b,b<->c,c<->d,d<->e")
        };

        for (Graph mag : mags) {
            System.out.println("\nmag = " + mag);
            Set<IndependenceFact> im = OrderedLocalMarkovProperty.getModel(mag);
            System.out.println("im = ");

            for (IndependenceFact fact : im) {
                System.out.println(fact);
            }
        }
    }

    @Test
    public void test4() {

        try {
            Graph[] mags = new Graph[]{
                    GraphUtils.convert("a-->b,b<->c,c<--d"),
                    GraphUtils.convert("a-->b,b<->c,c<->d,b-->d"),
                    GraphUtils.convert("e-->d,a-->b,b<->c,c<->d"),
                    GraphUtils.convert("a<->b,b<->c,c<->d,d<->e")
            };

            for (Graph mag : mags) {
                System.out.println("\nTrue MAG = " + mag);

                Set<IndependenceFact> im = OrderedLocalMarkovProperty.getModel(mag);

                IndependenceFacts facts = new IndependenceFacts();

                for (IndependenceFact fact : im) {
                    facts.add(fact);
                }

                System.out.println("IM:");
                System.out.println(facts);

                Fci fci = new Fci(new IndTestIndependenceFacts(facts));
                fci.setStable(false);

                Graph pag = fci.search();

                System.out.println("\nEstimated PAG = " + pag);

                Graph truePag = GraphTransforms.dagToPag(mag);

                System.out.println("True PAG = " + truePag);

                System.out.println("\n-----------------------------------------");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
