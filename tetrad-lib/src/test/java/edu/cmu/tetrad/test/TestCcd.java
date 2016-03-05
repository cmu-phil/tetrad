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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Tests the Ccd algorithm.
 *
 * @author Frank Wimberly
 */
public class TestCcd {

    /**
     * From "CcdTester".
     */
    public void testCcd() {
        Node a = new ContinuousVariable("A");
        Node b = new ContinuousVariable("B");
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");

        Graph graph = new EdgeListGraph();
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(x);
        graph.addNode(y);
        graph.addDirectedEdge(a, x);
        graph.addDirectedEdge(b, y);
        graph.addDirectedEdge(x, y);
        graph.addDirectedEdge(y, x);

        IndTestDSep test = new IndTestDSep(graph);

        Ccd ccd = new Ccd(test);
        Graph outPag = ccd.search();

        boolean b0 = PagUtils.graphInPagStep0(outPag, graph);
        if (!b0) {
            fail();
        }

        boolean b1 = PagUtils.graphInPagStep1(outPag, graph);
        if (!b1) {
            fail();
        }

        boolean b2 = PagUtils.graphInPagStep2(outPag, graph);
        if (!b2) {
            fail();
        }

        boolean b3 = PagUtils.graphInPagStep3(outPag, graph);
        if (!b3) {
            fail();
        }

        boolean b4 = PagUtils.graphInPagStep4(outPag, graph);
        if (!b4) {
            fail();
        }

        boolean b5 = PagUtils.graphInPagStep5(outPag, graph);
        if (!b5) {
            fail();
        }
    }

    /**
     * From CcdTesterC.
     */
//    @Test
    public void testCcdC() {

        Node a = new ContinuousVariable("A");
        Node b = new ContinuousVariable("B");
        Node c = new ContinuousVariable("C");
        Node d = new ContinuousVariable("D");
        Node e = new ContinuousVariable("E");

        a.setNodeType(NodeType.MEASURED);
        b.setNodeType(NodeType.MEASURED);
        c.setNodeType(NodeType.MEASURED);
        d.setNodeType(NodeType.MEASURED);
        e.setNodeType(NodeType.MEASURED);

        Graph graph = new EdgeListGraph();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);
        graph.addNode(e);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(b, c);
        graph.addDirectedEdge(c, b);
        graph.addDirectedEdge(c, d);
        graph.addDirectedEdge(d, c);
        graph.addDirectedEdge(e, d);

        IndTestDSep test = new IndTestDSep(graph);

        Ccd ccd = new Ccd(test);
        Graph outPag = ccd.search();

        boolean b1 = PagUtils.graphInPagStep0(outPag, graph);
        if (!b1) {
            fail();
        }

        boolean b2 = PagUtils.graphInPagStep1(outPag, graph);
        if (!b2) {
            fail();
        }

        boolean b3 = PagUtils.graphInPagStep2(outPag, graph);
        if (!b3) {
            fail();
        }

        boolean b4 = PagUtils.graphInPagStep3(outPag, graph);
        if (!b4) {
            fail();
        }

        boolean b5 = PagUtils.graphInPagStep4(outPag, graph);
        if (!b5) {
            fail();
        }

        boolean b6 = PagUtils.graphInPagStep5(outPag, graph);
        if (!b6) {
            fail();
        }
    }
}





