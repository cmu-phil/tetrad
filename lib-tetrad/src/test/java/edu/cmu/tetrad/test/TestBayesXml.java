///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import nu.xom.*;

import java.io.File;
import java.io.IOException;

/**
 * Tests the Bayes XML parsing/rendering.
 *
 * @author Joseph Ramsey
 */
public final class TestBayesXml extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestBayesXml(String name) {
        super(name);
    }

    public static void testRoundtrip() {
        BayesIm bayesIm = sampleBayesIm1();
        Element element = BayesXmlRenderer.getElement(bayesIm);

        System.out.println("Started with this bayesIm: " + bayesIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        BayesXmlParser parser = new BayesXmlParser();
        BayesIm bayesIm2 = parser.getBayesIm(element);

        System.out.println(bayesIm2.getDag());
        System.out.println(bayesIm2);
    }

    public void testRoundtrip2() {
        BayesIm bayesIm = sampleBayesIm2();
        Element element = BayesXmlRenderer.getElement(bayesIm);

        System.out.println("Started with this bayesIm: " + bayesIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        BayesXmlParser parser = new BayesXmlParser();
        BayesIm bayesIm2 = parser.getBayesIm(element);

        System.out.println(bayesIm2.getDag());
        System.out.println(bayesIm2);
    }

    public void testRoundtrip3() {
        BayesIm bayesIm = sampleBayesIm3();
        Element element = BayesXmlRenderer.getElement(bayesIm);

        System.out.println("Started with this bayesIm: " + bayesIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        BayesXmlParser parser = new BayesXmlParser();
        BayesIm bayesIm2 = parser.getBayesIm(element);

        System.out.println(bayesIm2.getDag());
        System.out.println(bayesIm2);
    }

//    public void testMakeFile() {
//        BayesIm
//    }

    /**
     * Tests to make sure that a particular file produced by the renderer on
     * 6/26/04 remains parsable. VERY IMPORTANT THIS DOES NOT BREAK!!!
     */
    public void testLoadFromFile() {
        try {
            Builder builder = new Builder();
            Document document =
                    builder.build(new File("src/test/resources/parsableBayesNet.xml"));
            printDocument(document);

            BayesXmlParser parser = new BayesXmlParser();
            BayesIm bayesIm = parser.getBayesIm(document.getRootElement());
            System.out.println(bayesIm);
        }
        catch (ParsingException e) {
            e.printStackTrace();
            fail("The file referred to cannot be parsed as a Bayes IM." +
                    " The file referred to MUST LOAD!! PLEASE FIX IMMEDIATELY!!!" +
                    " (Ask Joe Ramsey jdramsey@andrew.cmu.edu for details.");
        }
        catch (IOException e) {
            e.printStackTrace();
            fail("The file referred to cannot be opened (or doesn't exist). " +
                    "Maybe the working directory is not set correctly.");
        }
    }

    private static BayesIm sampleBayesIm1() {
        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Dag graph;

        graph = new Dag();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(a, c);
        graph.addDirectedEdge(b, c);

        BayesPm bayesPm = new BayesPm(graph);
        bayesPm.setNumCategories(b, 3);

        BayesIm bayesIm1 = new MlBayesIm(bayesPm);
        bayesIm1.setProbability(0, 0, 0, .3);
        bayesIm1.setProbability(0, 0, 1, .7);

        bayesIm1.setProbability(1, 0, 0, .3);
        bayesIm1.setProbability(1, 0, 1, .4);
        bayesIm1.setProbability(1, 0, 2, .3);

        bayesIm1.setProbability(1, 1, 0, .6);
        bayesIm1.setProbability(1, 1, 1, .1);
        bayesIm1.setProbability(1, 1, 2, .3);

        bayesIm1.setProbability(2, 0, 0, .9);
        bayesIm1.setProbability(2, 0, 1, .1);

        bayesIm1.setProbability(2, 1, 0, .1);
        bayesIm1.setProbability(2, 1, 1, .9);

        bayesIm1.setProbability(2, 2, 0, .5);
        bayesIm1.setProbability(2, 2, 1, .5);

        bayesIm1.setProbability(2, 3, 0, .2);
        bayesIm1.setProbability(2, 3, 1, .8);

        bayesIm1.setProbability(2, 4, 0, .6);
        bayesIm1.setProbability(2, 4, 1, .4);

        bayesIm1.setProbability(2, 5, 0, .7);
        bayesIm1.setProbability(2, 5, 1, .3);
        return bayesIm1;
    }

    private static BayesIm sampleBayesIm2() {
        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Dag graph = new Dag();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(a, c);
        graph.addDirectedEdge(b, c);

        BayesPm bayesPm = new BayesPm(graph);
        bayesPm.setNumCategories(b, 3);

        return new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
    }


    private static BayesIm sampleBayesIm3() {
        Node a = new GraphNode("a");
        a.setNodeType(NodeType.LATENT);
        a.setCenterX(5);
        a.setCenterY(5);
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Dag graph;

        graph = new Dag();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(a, c);
        graph.addDirectedEdge(b, c);

        BayesPm bayesPm = new BayesPm(graph);
        bayesPm.setNumCategories(b, 3);

        return new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
    }

    /**
     * Prints an arbitrary XML element to System.out.
     *
     * @param element the element to print.
     */
    private static void printElement(Element element) {
        printDocument(new Document(element));
    }

    private static void printDocument(Document document) {
        Serializer serializer = new Serializer(System.out);

        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBayesXml.class);
    }
}





