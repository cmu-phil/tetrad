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

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestIonForClark {

    private double penaltyDiscount = 2;
    private int sampleSizePerDataset = 1000;

    public void test1() {
        runModel("A-->B,B-->C,A-->D,D-->C", new String[][]{{"B", "A", "D"}, {"B", "C", "D"}});
    }

    public void test2() {
        runModel("X-->A,A-->B,C-->B,Y-->C", new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}});
    }

    public void test3() {
        runModel("X-->A,A-->B,C-->B,Y-->C", new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}, {"A", "B", "C"}});
    }

    public void test4() {
        runModel("A-->B,B-->C,A-->D,D-->C", new String[][]{{"A", "B", "C"}, {"A", "D", "C"}});
    }

    public void test5() {
        runModel("T-->W, R-->S,T-->S", new String[][]{{"R", "S", "T"}, {"T", "W"}});
    }

    public void test6() {
        final int numNodes = 8;
        Graph graph1 = GraphUtils.randomGraph(numNodes, 0, numNodes, 100, 100, 100, false);
        List<Edge> edges = new ArrayList<>(graph1.getEdges());

        StringBuilder graphSpec = new StringBuilder();
        for (int i = 0; i < graph1.getNumEdges(); i++) {
            graphSpec.append(edges.get(i).getNode1()).append("-->").append(edges.get(i).getNode2());
            if (i < edges.size() - 1) graphSpec.append(",");
        }

        runModel(graphSpec.toString(),
                new String[][]{
                        {"X1", "X3", "X4", "X5", "X6", "X7", "X8", "X8", "10"},
                        {"X2", "X3", "X4", "X5", "X6", "X7", "X8", "X8", "10"},
        });
    }

    public void test7() {
        final String graphSpec = "A-->B,A-->C,C-->B";
        final String[][] groupings = {{"A", "B", "C"}};

        runModel(graphSpec, groupings);
    }

    public void test8() {
        runModel("A-->B,B-->C,D-->C", new String[][]{{"A", "B", "C"}, {"A", "D", "C"}});
    }

    private void runModel(String graphSpec, String[][] groupings) {

        System.out.println("\n\n**********************************************************");

        System.out.println();

        System.out.println("Graph spec = " + graphSpec);

        System.out.println("\nGroupings: ");
        int i = 1;

        for (String[] grouping : groupings) {
            System.out.print(i++ + ": {");
            for (int g = 0; g < grouping.length; g++) {
                System.out.print(grouping[g]);
                if (g < grouping.length - 1) System.out.print(",");
            }
            System.out.println("}");
        }

        System.out.println("\n**********************************************************\n");


        Graph graph = GraphConverter.convert(graphSpec);
        runModel(graph, groupings);
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    private int getSampleSizePerDataset() {
        return sampleSizePerDataset;
    }

    //===================================PRIVATE METHODS===================================//

    private void runModel(Graph graph, String[][] groupings) {
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        List<DataSet> dataSets = new ArrayList<>();

        for (String[] grouping : groupings) {
            final DataSet dataSet = im.simulateData(getSampleSizePerDataset(), false);
            setMIssing(dataSet, grouping);
            dataSets.add(dataSet);
        }

        DataSet concat = DataUtils.concatenate(dataSets);

        List<Graph> modelsIon = new ArrayList<>();
        List<Graph> modelsPcIon = new ArrayList<>();

        try {
            modelsIon = runModelIon(dataSets, groupings);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            modelsPcIon = runModelPcIon(dataSets);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Graph> modelsIon2 = new ArrayList<>();

        for (Graph graph2 : modelsIon) {
            modelsIon2.add(GraphUtils.replaceNodes(graph2, concat.getVariables()));
        }

        List<Graph> modelsPcIon2 = new ArrayList<>();

        for (Graph graph2 : modelsPcIon) {
            modelsPcIon2.add(GraphUtils.replaceNodes(graph2, concat.getVariables()));
        }

        List<Graph> models2 = new ArrayList<>(modelsIon2);
        models2.removeAll(modelsPcIon2);

        System.out.println("In ION but not PC ION = " + models2.size());

        for (int i = 0; i < models2.size(); i++) {
            System.out.println("\n[" + (i + 1) + "]. " + models2.get(i));
        }

        List<Graph> models3 = new ArrayList<>(modelsPcIon2);
        models3.removeAll(modelsIon2);

        System.out.println("In PC ION but not ION = " + models3.size());

        for (int i = 0; i < models3.size(); i++) {
            System.out.println("\n[" + (i + 1) + "]. " + models3.get(i));
        }
    }

    private List<Graph> runModelIon(List<DataSet> dataSets, String[][] groupings) {
        System.out.println("\n================================================");
        System.out.println("====================== ION =====================");
        System.out.println("================================================\n");
        List<Graph> graphs = new ArrayList<>();

        for (int i = 0; i < groupings.length; i++) {
            DataSet dataSet = keepVariables(dataSets.get(i), groupings[i]);
            SemBicScore score1 = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score1.setPenaltyDiscount(getPenaltyDiscount());
            Fci fci = new edu.cmu.tetrad.search.Fci(new IndTestScore(score1));
            Graph g1 = fci.search();
            graphs.add(g1);
        }

        Ion ion = new Ion(graphs);
        List<Graph> models = ion.search();
        List<Graph> outGraphs = new ArrayList<>();

        G:
        for (Graph g : models) {
            for (Edge edge : g.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    continue G;
                }

                if (edge.getEndpoint1() == Endpoint.CIRCLE) edge.setEndpoint1(Endpoint.TAIL);
                if (edge.getEndpoint2() == Endpoint.CIRCLE) edge.setEndpoint2(Endpoint.TAIL);
            }

            SearchGraphUtils.basicPattern(g, false);
            new MeekRules().orientImplied(g);
            outGraphs.add(g);
        }

        int i = 1;

        for (Graph g : outGraphs) {
            System.out.println("\n(" + i++ + "). " + g);
        }

        return outGraphs;
    }

    private List<Graph> runModelPcIon(List<DataSet> dataSets) {
        System.out.println("\n================================================");
        System.out.println("===================== PC ION ===================");
        System.out.println("================================================\n");

        DataSet concat = DataUtils.concatenate(dataSets);

        FgesIon ion = new FgesIon();
        ion.setPatternAlgorithm(FgesIon.PatternAlgorithm.PC);
        ion.setPenaltyDiscount(penaltyDiscount);
        List<Graph> models = ion.search(concat);

        int i = 1;

        for (Graph g : models) {
            System.out.println("\n(" + i++ + "). " + g);
        }

        return models;
    }

    private void setMIssing(DataSet data1, String... vars) {
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
            if (data1.getVariable(s) != null) {
                nodes.add(data1.getVariable(s));
            }
        }

        return data1.subsetColumns(nodes);
    }

    //==================================MAIN======================================;/

    public static void main(String... args) {
//        new TestIonForClark().test1();
        new TestIonForClark().test2();
//        new TestIonForClark().test3();
//        new TestIonForClark().test4();
//        new TestIonForClark().test5();
//        new TestIonForClark().test6();
//        new TestIonForClark().test7();
//        new TestIonForClark().test8();

//        new TestIonForClark().testG();
    }

}




