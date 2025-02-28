package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.OrderedLocalMarkovProperty;
import edu.cmu.tetrad.util.SublistGenerator;
import org.junit.Test;

import java.util.Set;

public class TestOrderedLocalMarkovProperty {

    @Test
    public void test1() {
        Graph g = RandomGraph.randomGraph(10, 2, 10, 100, 100, 100, false);
        Graph pag = GraphTransforms.dagToPag(g);
        Graph mag = GraphTransforms.zhangMagFromPag(pag);

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
        {
            Graph mag1 = GraphUtils.convert("a-->b,b<->c,c<--d");

            System.out.println("\nmag1 = " + mag1);

            Set<IndependenceFact> im1 = OrderedLocalMarkovProperty.getModel(mag1);

            System.out.println("im1 = ");

            for (IndependenceFact fact : im1) {
                System.out.println(fact);
            }
        }

        {
            Graph mag2 = GraphUtils.convert("a-->b,b<->c,c<->d,b-->d");

            System.out.println("\n\nmag2 = " + mag2);

            Set<IndependenceFact> im2 = OrderedLocalMarkovProperty.getModel(mag2);

            System.out.println("im = ");

            for (IndependenceFact fact : im2) {
                System.out.println(fact);
            }
        }

        {
            Graph mag3 = GraphUtils.convert("e-->d,a-->b,b<->c,c<->d");

            System.out.println("\n\nmag3 = " + mag3);

            Set<IndependenceFact> im3 = OrderedLocalMarkovProperty.getModel(mag3);

            System.out.println("im = ");

            for (IndependenceFact fact : im3) {
                System.out.println(fact);
            }
        }

        {
            Graph mag4 = GraphUtils.convert("a-->b,b<->c,c-->d,d<->a");

            System.out.println("\n\nmag4 = " + mag4);

            Set<IndependenceFact> im3 = OrderedLocalMarkovProperty.getModel(mag4);

            System.out.println("im = ");

            for (IndependenceFact fact : im3) {
                System.out.println(fact);
            }
        }

        {
            Graph mag5 = GraphUtils.convert("a<->b,b<->c,c<->d,d<->e");

            System.out.println("\n\nmag5 = " + mag5);

            Set<IndependenceFact> im3 = OrderedLocalMarkovProperty.getModel(mag5);

            System.out.println("im = ");

            for (IndependenceFact fact : im3) {
                System.out.println(fact);
            }
        }

    }
}
