///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
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
 * @author josephramsey
 */
public final class TestSemXml extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemXml(String name) {
        super(name);
    }

    private static SemIm sampleSemIm1() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 5,
                30, 15, 15, true));
        SemPm pm = new SemPm(graph);
        return new SemIm(pm);
    }

    /**
     * Prints an arbitrary XML element to System.out.
     *
     * @param element the element to print.
     */
    private static void printElement(Element element) {
        TestSemXml.printDocument(new Document(element));
    }

    private static void printDocument(Document document) {
        Serializer serializer = new Serializer(System.out);

        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (IOException e) {
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

    public void testRoundtrip2() {
        SemIm semIm = TestSemXml.sampleSemIm1();
        Element element = SemXmlRenderer.getElement(semIm);

        SemXmlParser parser = new SemXmlParser();
        SemIm semIm2 = SemXmlParser.getSemIm(element);
    }

    public void testRoundtrip3() {
        SemIm semIm = TestSemXml.sampleSemIm1();
        Element element = SemXmlRenderer.getElement(semIm);

        SemXmlParser parser = new SemXmlParser();
        SemIm semIm2 = SemXmlParser.getSemIm(element);
    }
}






