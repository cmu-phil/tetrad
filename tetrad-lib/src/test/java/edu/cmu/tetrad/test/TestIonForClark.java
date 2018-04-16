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
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;

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

        data1 = keepVariables(data1, "B", "A", "D");
        data2 = keepVariables(data2, "B", "C", "D");

        SemBicScore score1 = new SemBicScore(new CovarianceMatrixOnTheFly(data1));
        SemBicScore score2 = new SemBicScore(new CovarianceMatrixOnTheFly(data2));

        Fci fci = new Fci(new IndTestScore(score1));
        Graph g1 = fci.search();

        fci = new Fci(new IndTestScore(score2));
        Graph g2 = fci.search();

        System.out.println("g1 = " + g1);
        System.out.println("g2 = " + g2);

        List<Graph> graphs = new ArrayList<>();
        graphs.add(g1);
        graphs.add(g2);

        Ion ion = new Ion(graphs);
        List<Graph> outGraphs = ion.search();

        System.out.println(outGraphs);

//        DataSet concat = DataUtils.concatenate(data1, data2);
//
//
//        System.out.println(concat);
//
//        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(concat));
//        Fask fask = new Fask(concat, score);
//
//        System.out.println(fask.search());
//
//        Fges fges = new Fges(score);
//
//        System.out.println(fges.search());

    }

    public void test2a() {

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
        fges.setVerbose(true);

        System.out.println(fges.search());

//        IndependenceTest test = new IndTestScore(score);
//        Pc pc = new Pc(test);
//        pc.setVerbose(true);
//        System.out.println(pc.search());

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
        fges.setVerbose(true);
        System.out.println(fges.search());

        IndependenceTest test = new IndTestScore(score);
        Pc pc = new Pc(test);
        pc.setVerbose(true);
        System.out.println(pc.search());


    }

    public void test3a() {
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

        data1 = keepVariables(data1, "X", "Y", "A");
        data2 = keepVariables(data2, "X", "Y", "B");
        data3 = keepVariables(data3, "X", "Y", "C");

        System.out.println(data1);
        System.out.println(data2);
        System.out.println(data3);

        SemBicScore score1 = new SemBicScore(new CovarianceMatrixOnTheFly(data1));
        SemBicScore score2 = new SemBicScore(new CovarianceMatrixOnTheFly(data2));
        SemBicScore score3 = new SemBicScore(new CovarianceMatrixOnTheFly(data3));

        Fci fci = new Fci(new IndTestScore(score1));
        Graph g1 = fci.search();

        fci = new Fci(new IndTestScore(score2));
        Graph g2 = fci.search();

        fci = new Fci(new IndTestScore(score3));
        Graph g3 = fci.search();

        System.out.println("g1 = " + g1);
        System.out.println("g2 = " + g2);
        System.out.println("g3 = " + g3);

        List<Graph> graphs = new ArrayList<>();
        graphs.add(g1);
        graphs.add(g2);
        graphs.add(g3);

        Ion ion = new Ion(graphs);
        List<Graph> outGraphs = ion.search();

        int index = 1;

        for (Graph graph : outGraphs) {
            String s = graph.toString();

            if (!s.contains("<->")) {
                System.out.println((index++) + ". \n" + s.replace("o", "-"));
            }
        }

    }

    public void test3b() {

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
        fges.setVerbose(true);
        final Graph P = fges.search();
        System.out.println(P);

        List<IndependenceFact> facts = new ArrayList<>(fges.getIndeterminateFacts());

        int index = 1;

        for (IndependenceFact fact : facts) {
            System.out.println(index++ + ". " + fact);
        }

        final List<Node> nodes = P.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                List<List<Node>> paths = GraphUtils.semidirectedPathsFromTo(P, nodes.get(i), nodes.get(j), 3);

                for (List<Node> path : paths) {
                    Node x = path.get(0);
                    Node y = path.get(path.size() - 1);

                    List<Node> intermediaries = new ArrayList<>();

                    for (int k = 1; k < path.size() - 1; k++) {
                        intermediaries.add(path.get(k));
                    }

                    DepthChoiceGenerator gen = new DepthChoiceGenerator(intermediaries.size(), intermediaries.size());
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        List<Node> these = GraphUtils.asList(choice, intermediaries);

                        double v = score.localScoreDiff(nodes.indexOf(x), nodes.indexOf(y), varIndices(these, nodes));

                        if (Double.isNaN(v)) {
                            List<Node> _path = new ArrayList<>(path);
                            _path.removeAll(these);
                            System.out.println(GraphUtils.pathString(_path, P));
                        }
                    }
                }

            }
        }
    }

    private int[] varIndices(List<Node> z, List<Node> variables) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = variables.indexOf(z.get(i));
        }

        return indices;
    }

    public static void main(String... args) {
        new TestIonForClark().test3a();
    }

}




