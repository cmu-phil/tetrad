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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestIonForClark {

    public void test1() {

        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Graph graph1 = new EdgeListGraph();
        graph1.addNode(A);
        graph1.addNode(B);
        graph1.addNode(D);
        graph1.addUndirectedEdge(B, A);
        graph1.addUndirectedEdge(A, D);

        Graph graph2 = new EdgeListGraph();
        graph1.addNode(C);
        graph1.addNode(B);
        graph1.addNode(D);
        graph1.addUndirectedEdge(C, B);
        graph1.addUndirectedEdge(C, D);
        graph1.addUndirectedEdge(B, D);

        List<Graph> graphs = new ArrayList<>();
        graphs.add(graph1);
        graphs.add(graph2);

        Ion ion = new Ion(graphs);
        List<Graph> outGraphs = ion.search();

        System.out.println(outGraphs);

    }

    public void test2() {

        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Graph graph1 = new EdgeListGraph();
        graph1.addNode(A);
        graph1.addNode(B);
        graph1.addNode(C);
        graph1.addNode(D);
        graph1.addDirectedEdge(A, B);
        graph1.addDirectedEdge(B, C);
        graph1.addDirectedEdge(A, D);
        graph1.addDirectedEdge(D, C);

        SemPm pm = new SemPm(graph1);
        SemIm im = new SemIm(pm);

        DataSet data1 = im.simulateData(500, false);
        DataSet data2 = im.simulateData(500, false);

        keep(data1, "B", "A", "D");
        keep(data2, "B", "C", "D");

        DataSet concat = DataUtils.concatenate(data1, data2);


        System.out.println(concat);

        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(concat));
        Fask fask = new Fask(concat, score);

        System.out.println(fask.search());

        Fges fges = new Fges(score);

        System.out.println(fges.search());

    }

    private void keep(DataSet data1, String...vars) {
        Set<String> names = new HashSet<>(Arrays.asList(vars));

        for (int i = 0; i < data1.getNumRows(); i++) {
            for (int j = 0; j < data1.getNumColumns(); j++) {
                final String name = data1.getVariable(j).getName();

                if (!names.contains(name)) {
                    data1.setDouble(i, j, Double.NaN);
                }
            }
        }
    }

    public void test3() {

        Node X = new GraphNode("X");
        Node C = new GraphNode("C");
        Node B = new GraphNode("B");
        Node A = new GraphNode("A");
        Node Y = new GraphNode("Y");

        Graph graph1 = new EdgeListGraph();
        graph1.addNode(X);
        graph1.addNode(C);
        graph1.addNode(B);
        graph1.addNode(A);
        graph1.addNode(Y);
        graph1.addDirectedEdge(X, C);
        graph1.addDirectedEdge(C, B);
        graph1.addDirectedEdge(A, B);
        graph1.addDirectedEdge(Y, A);

        SemPm pm = new SemPm(graph1);
        SemIm im = new SemIm(pm);

        DataSet data1 = im.simulateData(500, false);
        DataSet data2 = im.simulateData(500, false);
        DataSet data3 = im.simulateData(500, false);

        keep(data1, "X", "Y", "A");
        keep(data2, "X", "Y", "B");
        keep(data3, "X", "Y", "C");

        DataSet concat = DataUtils.concatenate(data1, data2, data3);

        System.out.println(concat);

        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(concat));
        Fges fges = new Fges(score);
        System.out.println(fges.search());

    }

    public static void main(String...args) {
        new TestIonForClark().test3();
    }

}




