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
import edu.cmu.tetrad.graph.*;
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
        graph1.addNondirectedEdge(B, A);
        graph1.addNondirectedEdge(A, D);

        Graph graph2 = new EdgeListGraph();
        graph2.addNode(C);
        graph2.addNode(B);
        graph2.addNode(D);
        graph2.addNondirectedEdge(C, B);
        graph2.addNondirectedEdge(B, D);
        graph2.addNondirectedEdge(C, D);

        System.out.println("graph1 = " + graph1);
        System.out.println("graph2 = " + graph2);

        List<Graph> graphs = new ArrayList<>();
        graphs.add(graph1);
        graphs.add(graph2);

        Ion ion = new Ion(graphs);
        List<Graph> outGraphs = ion.search();

        System.out.println(outGraphs);

    }

    public void test2(boolean ion) {

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

        runModel(graph1, new String[][]{{"B", "A", "D"}, {"B", "C", "D"}}, ion);
    }


    public void test3(boolean ion) {

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

        runModel(graph1, new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}}, ion);

    }

    public void test4(boolean ion) {

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

        runModel(graph1, new String[][]{{"A", "B", "C"}, {"A", "D", "C"}}, ion);
    }

    private void runModel(Graph graph, String[][] groupings, boolean ion) {
        if (ion) {
            runModelIon(graph, groupings);
        } else {
            runModelFgesIon(graph, groupings);
        }
    }

    private void runModelFgesIon(Graph graph, String[][] groupings) {
        //        System.out.println(concat);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        List<DataSet> dataSets = new ArrayList<>();

        for (int i = 0; i < groupings.length; i++) {
            final DataSet dataSet = im.simulateData(500, false);
            keep(dataSet, groupings[i]);
            dataSets.add(dataSet);
        }

        DataSet concat = DataUtils.concatenate(dataSets);

        FgesIon ion = new FgesIon();
        Graph p = ion.search(concat);

        System.out.println("FGES model = " + p);

        System.out.println("Augmented = " + ion.getAugmented());

        List<Edge> removeableEdges = ion.getRemoveableEdges();

        System.out.println("RemoveableEdges");

        int i = 1;

        for (Edge edge : removeableEdges) {
            System.out.println(i++ + ". " + edge);
        }

        List<Graph> models = ion.allModels(p, ion.getAugmented(), ion.getRemoveableEdges());
        List<List<Edge>> edgesREmoved = ion.getEdgesRemoved();

        FgesIon.printModels(models, edgesREmoved);
    }

    private void runModelIon(Graph graph, String[][] groupings) {
        //        System.out.println(concat);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        List<DataSet> dataSets = new ArrayList<>();
        List<Graph> graphs = new ArrayList<>();

        for (int i = 0; i < groupings.length; i++) {
            DataSet dataSet = im.simulateData(500, false);
            dataSet = keepVariables(dataSet, groupings[i]);
            SemBicScore score1 = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            Fci fci = new Fci(new IndTestScore(score1));
            Graph g1 = fci.search();
            graphs.add(g1);
        }

        IonJoeModifications ion = new IonJoeModifications(graphs);
        List<Graph> outGraphs = ion.search();

        int i = 1;

        for (Graph g : outGraphs) {
            System.out.println(i++ + ". " + g);
        }
    }


    private void keep(DataSet data1, String... vars) {
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

    private DataSet keepVariables(DataSet data1, String... vars) {
        List<Node> nodes = new ArrayList<>();

        for (String s : vars) {
            nodes.add(data1.getVariable(s));
        }

        return data1.subsetColumns(nodes);
    }

    public static void main(String... args) {
        boolean ion = false;

//        new TestIonForClark().test2(ion);
//        new TestIonForClark().test3(ion);
        new TestIonForClark().test4(ion);

    }

}




