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

import java.util.*;

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestIonForClark {
    IKnowledge knowledge = new Knowledge2();

    public void test2(boolean ion) {
        IKnowledge knowledge = new Knowledge2();
        knowledge.addToTier(1, "A");
        knowledge.addToTier(2, "B");
        knowledge.addToTier(2, "C");
        knowledge.addToTier(2, "D");

        this.knowledge = knowledge;

        Graph graph1 = GraphConverter.convert("A-->B,B-->C,A-->D,D-->C");
        runModel(graph1, new String[][]{{"B", "A", "D"}, {"B", "C", "D"}}, ion);
    }

    public void test3(boolean ion) {
        Graph graph1 = GraphConverter.convert("X-->C,C-->B,A-->B,Y-->A");
        runModel(graph1, new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}}, ion);
    }

    public void test3a(boolean ion) {
        Graph graph1 = GraphConverter.convert("X-->C,C-->B,A-->B,Y-->A");
        runModel(graph1, new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"},  {"A", "B", "C"}}, ion);
    }

    public void test4(boolean ion) {
        Graph graph1 = GraphConverter.convert("A-->B,B-->C,A-->D,D-->C");
        runModel(graph1, new String[][]{{"A", "B", "C"}, {"A", "D", "C"}}, ion);
    }

    public void test5(boolean ion) {
        Graph graph1 = GraphConverter.convert("R-->S,T-->S,T-->W");
        runModel(graph1, new String[][]{{"R", "S", "T"}, {"S", "W"}}, ion);
    }

    public void test6(boolean ion) {
        Graph graph1 = GraphUtils.randomGraph(10, 0, 10, 100, 100, 100, false);
//        runModel(graph1, new String[][]{{"X1", "X2", "X3"}, {"X2", "X3", "X4"}, {"X3", "X4", "X5"}}, ion);
        runModel(graph1, new String[][]{{"X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8"}, { "X2", "X3", "X4", "X5", "X6", "X7", "X8", "X10"}}, ion);
    }

    private void runModel(Graph graph, String[][] groupings, boolean ion) {
        if (ion) {
            runModelIon(graph, groupings);
        } else {
            runModelFgesIon(graph, groupings);
        }
    }

    private void runModelFgesIon(Graph graph, String[][] groupings) {
        int sampleSize = 10000;

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        List<DataSet> dataSets = new ArrayList<>();

        for (int i = 0; i < groupings.length; i++) {
            final DataSet dataSet = im.simulateData(sampleSize / groupings.length, false);
            keep(dataSet, groupings[i]);
            dataSets.add(dataSet);
        }

        DataSet concat = DataUtils.concatenate(dataSets);

        FgesIon ion = new FgesIon();
        ion.setKnowledge(knowledge);
        Graph p = ion.search(concat);

        System.out.println("FGES model = " + p);

        System.out.println("Augmented = " + ion.getAugmented());

        List<Edge> removableEdges = ion.getRemoveableEdges();

        System.out.println("RemoveableEdges");

        int i = 1;

        for (Edge edge : removableEdges) {
            System.out.println(i++ + ". " + edge);
        }

        List<Graph> models = ion.allModels(p, ion.getAugmented(), ion.getRemoveableEdges());
        List<List<Edge>> edgesRemoved = ion.getEdgesRemoved();

        FgesIon.printModels(models, edgesRemoved);
    }

    private void runModelIon(Graph graph, String[][] groupings) {
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        List<Graph> graphs = new ArrayList<>();

        for (int i = 0; i < groupings.length; i++) {
            DataSet dataSet = im.simulateData(1000, false);
            dataSet = keepVariables(dataSet, groupings[i]);
            SemBicScore score1 = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score1.setPenaltyDiscount(4);
            Fci fci = new edu.cmu.tetrad.search.Fci(new IndTestScore(score1));
            Graph g1 = fci.search();
            graphs.add(g1);
        }

        IonJoeModifications ion = new IonJoeModifications(graphs);
        List<Graph> outGraphs = ion.search();

        Set<Graph> out2 = new LinkedHashSet<>();

        G:
        for (Graph g : outGraphs) {
            for (Edge edge : g.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    continue G;
                }

                if (edge.getEndpoint1() == Endpoint.CIRCLE) edge.setEndpoint1(Endpoint.TAIL);
                if (edge.getEndpoint2() == Endpoint.CIRCLE) edge.setEndpoint2(Endpoint.TAIL);
            }

            out2.add(g);
        }

        outGraphs = new ArrayList<>(out2);

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

        new TestIonForClark().test2(ion);
//        new TestIonForClark().test3(ion);
//        new TestIonForClark().test3a(ion);
//        new TestIonForClark().test4(ion);
//        new TestIonForClark().test5(ion);
//        new TestIonForClark().test6(ion);

    }

}




