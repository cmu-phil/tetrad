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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Ion;
import edu.cmu.tetrad.search.IonJoeModifications;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests CStaS.
 *
 * @author Joseph Ramsey
 */
public class TestIonForClark {
    public void test1() {
        List<Graph> in = new ArrayList<>();

        Graph graph1 = new EdgeListGraph();
        Graph graph2 = new EdgeListGraph();

        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        graph1.addNode(A);
        graph1.addNode(B);
        graph1.addNode(C);
        graph1.addNode(D);

        graph2.addNode(A);
        graph2.addNode(B);
        graph2.addNode(C);
        graph2.addNode(D);

        Edge e1 = new Edge(A, B, Endpoint.CIRCLE, Endpoint.TAIL);
        Edge e2 = new Edge(A, D, Endpoint.CIRCLE, Endpoint.TAIL);

        graph1.addEdge(e1);
        graph1.addEdge(e2);

//        graph1.addPartiallyOrientedEdge(A, B);
//        graph1.addPartiallyOrientedEdge(A, D);

        graph2.addNondirectedEdge(B, C);
        graph2.addNondirectedEdge(B, D);
        graph2.addNondirectedEdge(C, D);

        System.out.println("Graph 1 = " + graph1);
        System.out.println("Graph 2 = " + graph2);

        in.add(graph1);
        in.add(graph2);

        IonJoeModifications ion = new IonJoeModifications(in);
//        Ion ion = new Ion(in);
        List<Graph> result = ion.search();

        System.out.println("ION outputs:\n");

        for (Graph graph : result) {
            System.out.println(graph);
        }
    }

    public static void main(String... args) {
        new TestIonForClark().test1();
    }
}



