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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the Bayes XML parsing/rendering.
 *
 * @author Joseph Ramsey
 */
public final class TestSemXml extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemXml(final String name) {
        super(name);
    }

    public static void testRosemIm2undtrip() {
        final SemIm semIm = TestSemXml.sampleSemIm1();
        final Element element = SemXmlRenderer.getElement(semIm);

        final SemXmlParser parser = new SemXmlParser();
        final SemIm semIm2 = SemXmlParser.getSemIm(element);
    }

    public void testRoundtrip2() {
        final SemIm semIm = TestSemXml.sampleSemIm1();
        final Element element = SemXmlRenderer.getElement(semIm);

        final SemXmlParser parser = new SemXmlParser();
        final SemIm semIm2 = SemXmlParser.getSemIm(element);
    }

    public void testRoundtrip3() {
        final SemIm semIm = TestSemXml.sampleSemIm1();
        final Element element = SemXmlRenderer.getElement(semIm);

        final SemXmlParser parser = new SemXmlParser();
        final SemIm semIm2 = SemXmlParser.getSemIm(element);
    }

    private static SemIm sampleSemIm1() {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, true));
        final SemPm pm = new SemPm(graph);
        return new SemIm(pm);
    }

    /**
     * Prints an arbitrary XML element to System.out.
     *
     * @param element the element to print.
     */
    private static void printElement(final Element element) {
        TestSemXml.printDocument(new Document(element));
    }

    private static void printDocument(final Document document) {
        final Serializer serializer = new Serializer(System.out);

        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (final IOException e) {
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





