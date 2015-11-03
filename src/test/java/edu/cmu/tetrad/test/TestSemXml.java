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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.SemXmlParser;
import edu.cmu.tetrad.sem.SemXmlRenderer;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;

import java.io.IOException;

/**
 * Tests the Bayes XML parsing/rendering.
 *
 * @author Joseph Ramsey
 */
public final class TestSemXml extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemXml(String name) {
        super(name);
    }

    public static void testRoundtrip() {
        SemIm semIm = sampleSemIm1();
        Element element = SemXmlRenderer.getElement(semIm);

        System.out.println("Started with this semIm: " + semIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        SemXmlParser parser = new SemXmlParser();
        SemIm semIm2 = parser.getSemIm(element);

        System.out.println(semIm2.getSemPm().getGraph());
        System.out.println(semIm2);
    }

    public void testRoundtrip2() {
        SemIm semIm = sampleSemIm1();
        Element element = SemXmlRenderer.getElement(semIm);

        System.out.println("Started with this semIm: " + semIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        SemXmlParser parser = new SemXmlParser();
        SemIm semIm2 = parser.getSemIm(element);

        System.out.println(semIm2.getSemPm().getGraph());
        System.out.println(semIm2);
    }

    public void testRoundtrip3() {
        SemIm semIm = sampleSemIm1();
        Element element = SemXmlRenderer.getElement(semIm);

        System.out.println("Started with this semIm: " + semIm);
        System.out.println("\nGot this XML for it:");
        printElement(element);

        SemXmlParser parser = new SemXmlParser();
        SemIm semIm2 = parser.getSemIm(element);

        System.out.println(semIm2.getSemPm().getGraph());
        System.out.println(semIm2);
    }

    /**
     * Tests to make sure that a particular file produced by the renderer on 6/26/04 remains parsable. VERY IMPORTANT
     * THIS DOES NOT BREAK!!!
     */
//    public void testLoadFromFile() {
//        try {
//            Builder builder = new Builder();
//            Document document =
//                    builder.build(new File("sample_data/parsableSemNet.xml"));
//            printDocument(document);
//
//            SemXmlParser parser = new SemXmlParser();
//            SemIm bayesIm = parser.getEstIm(document.getRootElement());
//            System.out.println(bayesIm);
//        }
//        catch (ParsingException e) {
//            e.printStackTrace();
//            fail("The file referred to cannot be parsed as a SEM IM." +
//                    " The file referred to MUST LOAD!! PLEASE FIX IMMEDIATELY!!!" +
//                    " (Ask Joe Ramsey jdramsey@andrew.cmu.edu for details.");
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//            fail("The file referred to cannot be opened (or doesn't exist). " +
//                    "Maybe the working directory is not set correctly.");
//        }
//    }

    private static SemIm sampleSemIm1() {
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, true));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        return im;
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
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSemXml.class);
    }
}





