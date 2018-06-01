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
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestIonForClark {
    public void test2(boolean ion) {
        IKnowledge knowledge = new Knowledge2();
        knowledge.addToTier(1, "A");
        knowledge.addToTier(2, "B");
        knowledge.addToTier(2, "C");
        knowledge.addToTier(2, "D");

        knowledge = new Knowledge2();

        Graph graph1 = GraphConverter.convert("A-->B,B-->C,A-->D,D-->C");
        runModel(graph1, new String[][]{{"B", "A", "D"}, {"B", "C", "D"}},
                ion,
                knowledge);
    }

    public void test3(boolean ion) {
        Graph graph1 = GraphConverter.convert("X-->C,C-->B,A-->B,Y-->A");
        runModel(graph1, new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}},
                ion,
                new Knowledge2());
    }

    public void test3a(boolean ion) {
        Graph graph1 = GraphConverter.convert("X-->C,C-->B,A-->B,Y-->A");
        runModel(graph1, new String[][]{{"X", "Y", "A"}, {"X", "Y", "B"}, {"X", "Y", "C"}, {"A", "B", "C"}},
                ion,
                new Knowledge2());
    }

    public void test4(boolean ion) {
        Graph graph1 = GraphConverter.convert("A-->B,B-->C,A-->D,D-->C");
        runModel(graph1, new String[][]{{"A", "B", "C"}, {"A", "D", "C"}},
                ion,
                new Knowledge2());
    }

    public void test5(boolean ion) {
        Graph graph1 = GraphConverter.convert("T-->W, R-->S,T-->S");
        runModel(graph1, new String[][]{{"R", "S", "T"}, {"T", "W"}},
                ion,
                new Knowledge2());
    }

    public void test6(boolean ion) {
        Graph graph1 = GraphUtils.randomGraph(10, 0, 10, 100, 100, 100, false);
//        runModel(graph1, new String[][]{{"X1", "X2", "X3"}, {"X2", "X3", "X4"}, {"X3", "X4", "X5"}}, ion);
        runModel(graph1,
                new String[][]{{"X1", "X2", "X3", "X4", "X5", "X6", "X7", "X9"},
                        {"X2", "X3", "X4", "X5", "X6", "X7", "X8", "X10"}},
                ion,
                new Knowledge2());
    }

    public void test7(boolean ion) {
        Graph graph1 = GraphConverter.convert("A-->B,A-->C,C-->B");
//        Graph graph1 = GraphConverter.convert("A-->B,A-->C,C<--B");
        runModel(graph1, new String[][]{{"A", "B", "C"}},
                ion,
                new Knowledge2());
    }

    private void runModel(Graph graph, String[][] groupings, boolean ion, IKnowledge knowledge) {
        if (ion) {
            runModelIon(graph, groupings, knowledge);
        } else {
            runModelFgesIon(graph, groupings, knowledge);
        }
    }

    private void runModelFgesIon(Graph graph, String[][] groupings, IKnowledge knowledge) {
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

        final List<Node> nodes = concat.getVariables();

        for (Graph model : models) {
            model = GraphUtils.replaceNodes(model, nodes);
        }

        ICovarianceMatrix cov = new CorrelationMatrixOnTheFly2(concat);

        System.out.println("\nCovariance = " + cov);

        List<List<Edge>> edgesRemoved = ion.getEdgesRemoved();

        int l = 1;

        for (Graph model : new ArrayList<>(models)) {

            System.out.println("Model " + (l++));

            II:
            for (int ii = 0; ii < nodes.size(); ii++) {
                for (int jj = 0; jj < nodes.size(); jj++) {
                    if (ii == jj)  continue;

                    Node x = nodes.get(ii);
                    Node y = nodes.get(jj);

                    List<List<Node>> treks = GraphUtils.semidirectedTreks(model, x, y, 4);
                    if (treks.size() == 0) continue;

                    double sum = 0.0;

                    for (List<Node> trek : treks) {
                        System.out.println("Semidirected trek: " + GraphUtils.pathString(trek, model));

                        double prod = 1.0;

                        for (int m = 0; m < trek.size() - 1; m++) {
                            final Node m1 = trek.get(m);
                            final Node m2 = trek.get(m + 1);

                            double r = cov.getValue(nodes.indexOf(m1), nodes.indexOf(m2));
                            prod *= r;
                        }

                        System.out.println("prod = " + prod);

                        if (abs(sum + prod) > 1.0) {
                            System.out.println("Sum > 1");
                            continue;
                        }

                        sum += prod;
                    }

                    double rr = cov.getValue(ii, jj);

                    if (Double.isNaN(sum) || Double.isNaN(rr)) continue;

                    double ncsum = sampleSize;
                    double ncr = sampleSize;
                    double alpha = 0.000001;

                    double zsum = 0.5 * (log(1.0 + sum) - log(1.0 - sum));
                    double zr = 0.5 * (log(1.0 + rr) - log(1.0 - rr));

                    double z = (zsum - zr) / sqrt((1.0 / ((double) ncsum - 3) + 1.0 / ((double) ncr - 3)));
                    double cutoff = StatUtils.getZForAlpha(alpha);

                    System.out.println("sum = " + sum + " zsum = " + zsum + " zr = " + zr + " z = " +  " x = " + x + " y = " + y + " z = " + z + " cutoff = " + cutoff + " rr = " + rr + " sum = " + sum);

                    boolean rejected = abs(z) > cutoff;

                    if (rejected) {
                        models.remove(model);
                        System.out.println("Rejected");
                        break II;
                    }
                }
            }
        }

        FgesIon.printModels(models, edgesRemoved);
    }

    private void runModelIon(Graph graph, String[][] groupings, IKnowledge knowledge) {
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
        ion.setKnowledge(knowledge);
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

//        new TestIonForClark().test2(ion);
//        new TestIonForClark().test3(ion);
//        new TestIonForClark().test3a(ion);
//        new TestIonForClark().test4(ion);
//        new TestIonForClark().test5(ion);
//        new TestIonForClark().test6(ion);
        new TestIonForClark().test7(ion);

    }

}




