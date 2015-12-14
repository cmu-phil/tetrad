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
    @Test
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
    @Test
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


    public void rtestRandom() {
//        RandomUtil.getInstance().setSeed(502938L);

        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 20; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes, 0, 20,
                3, 3, 4, false));
        Graph graph = GraphUtils.addCycles(dag, 7, 2);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

        Ccd ccd = new Ccd(new IndTestFisherZ(data, 0.01));

        Graph pag = ccd.search();

        System.out.println(pag);
    }

    //
    @Ignore
    public void rtestLoop() {
        NumberFormat nf = new DecimalFormat("0.0000");

        boolean nonpranormal = false;
        boolean ar = false;
        boolean r3 = false;
        boolean compareToTrue = true;
        double alpha = 0.01;
        int depth = 3;

        int lag = 2;

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            indices.add(i);
        }

        try {
            for (boolean ccd : new boolean[]{true}) {  // Images = false
                for (int cohortSize : new int[]{5}) {
                    for (int sepsetType : new int[]{3}) {
                        if (!ccd && sepsetType > 1) continue;

                        String test = (cohortSize == 1 ? "FisherZ" : "FisherZFisher");
//                        String test = (cohortSize == 1 ? "FisherZConcatenateResiduals" : "FisherZConcatenateResiduals");
//                        String test = (cohortSize == 1 ? "Regression" : "FisherZFisher");
//                        String test = (cohortSize == 1 ? "LaggedRegression" : "FisherZFisher");

                        String dir = "/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2014.02.20/ccd/test";
                        String fileName = "ccdtest4."
                                + (ccd ? "CCD." : "IMaGES.")
                                + (r3 ? "R3." : ".")
                                + (nonpranormal ? "Nonparanormal." : "")
                                + (ar ? "AR." : "")
                                + "CohortSize." + (cohortSize) + "."
                                + (compareToTrue ? "CompareToTrue." : "CompareToCCDGraph.")
                                + (ccd ? ("SepsetType." + sepsetType + ".") : "")
                                + (ccd ? test + "." : "")
                                + (ccd && lag > 1 ? "Lag" + lag + "." : "")
                                + (ccd ? "Alpha" + alpha + "." : "")
                                + "txt";

                        File file = new File(dir, fileName);
                        PrintStream out = new PrintStream(file);

                        out.println("\nSIM\tAdjPrec\tAdjRec\tArrPrec\tArrRec\tArrZero\tSHD");

                        for (int i = 1; i <= 28; i++) {
                            double[] sums = new double[10];
                            int[] counts = new int[10];

                            List<DataSet> dataSets = loadSmithSim(i);

                            for (int j = 0; j < 50; j++) {
                                Collections.shuffle(indices);
                                List<DataSet> cohort = new ArrayList<>();

                                for (int y = 0; y < cohortSize; y++) {
                                    DataSet dataSet;

                                    if (cohortSize == 1) {
                                        dataSet = dataSets.get(indices.get(y));
                                    } else {
                                        dataSet = dataSets.get(y);
                                    }

                                    if (nonpranormal) {
                                        dataSet = DataUtils.getNonparanormalTransformed(dataSet);
                                    }
                                    if (ar) {
                                        dataSet = TimeSeriesUtils.ar2(dataSet, 1);
                                    }

                                    System.out.println(dataSet.getVariables());

//                                    Node time = new ContinuousVariable("Time");

//                                    if (!dataSet.getVariables().contains(time)) {
//                                        dataSet.addVariable(time);
//                                    }
//
//                                    int timeIndex = dataSet.getColumn(time);
//
//                                    for (int e = 0; e < dataSet.getNumRows(); e++) {
//                                        dataSet.setDouble(e, timeIndex, e);
//                                    }

                                    cohort.add(dataSet);
                                }

                                IndependenceTest _test = null;

                                if (cohortSize > 1) {
//                                   _test = new IndTestFisherZConcatenateResiduals(cohort, 0.01);
                                    _test = new IndTestFisherZFisherPValue(cohort, alpha);
                                } else if (cohortSize == 1) {
                                    _test = new IndTestFisherZ(cohort.get(0), alpha);
//                                    _test = new IndTestConditionalCorrelation(cohort.get(0), alpha);
//                                    _test = new IndTestRegression(cohort.get(0), alpha);
//                                    _test = new IndTestLaggedRegression(cohort.get(0), alpha, lag);
                                }

                                Graph graph;
//
                                if (ccd) {
                                    Ccd _ccd = new Ccd(_test);
                                    _ccd.setDepth(depth);
                                    _ccd.setVerbose(false);
                                    graph = _ccd.search();
                                } else {
                                    FastImages images = new FastImages(cohort, true);
                                    graph = images.search();

                                    if (r3) {
                                        Lofs2 lofs2 = new Lofs2(graph, cohort);
                                        lofs2.setRule(Lofs2.Rule.R3);
                                        lofs2.setAlpha(1.0);
                                        graph = lofs2.orient();
                                    }
                                }

//                                graph.removeNode(graph.getNode("Time"));

                                System.out.println("CCD estimated graph");
                                System.out.println(graph);
                                System.out.println();


                                Graph trueGraph = readGraph(i, 2);

                                GraphUtils.GraphComparison comparison;

                                if (compareToTrue) {
                                    if (ccd) {
                                        comparison = SearchGraphUtils.getGraphComparison3(graph, trueGraph, out);
                                    } else {
                                        comparison = SearchGraphUtils.getGraphComparison3a(graph, trueGraph);
                                    }
                                } else {
                                    final Ccd ccd2 = new Ccd(new IndTestDSep(trueGraph));
                                    ccd2.setDepth(depth);
                                    ccd2.setVerbose(false);

                                    Graph trueCcdGraph = ccd2.search();
                                    comparison = SearchGraphUtils.getGraphComparison3(graph, trueCcdGraph, out);
                                }

                                int adjCorrect = comparison.getAdjCorrect();
                                int adjFp = comparison.getAdjFp();
                                int adjFn = comparison.getAdjFn();
                                int arrowPtCorrect = comparison.getArrowptCorrect();
                                int arrowPtFp = comparison.getArrowptFp();
                                int arrowPtFn = comparison.getArrowptFn();
                                int shd = comparison.getShd();

                                double adjPrec = adjCorrect / (double) (adjCorrect + adjFp);
                                double adjRec = adjCorrect / (double) (adjCorrect + adjFn);

                                double arrowPrec = arrowPtCorrect / (double) (arrowPtCorrect + arrowPtFp);
                                double arrowRec = arrowPtCorrect / (double) (arrowPtCorrect + arrowPtFn);

                                System.out.println("Arrow precision = " + arrowPrec);

                                if (!Double.isNaN(adjPrec)) {
                                    sums[3] += adjPrec;
                                    counts[3]++;
                                }
                                if (!Double.isNaN(adjRec)) {
                                    sums[4] += adjRec;
                                    counts[4]++;
                                }
                                if (!Double.isNaN(arrowPrec)) {
                                    sums[5] += arrowPrec;
                                    counts[5]++;
                                }
                                if (!Double.isNaN(arrowRec)) {
                                    sums[6] += arrowRec;
                                    counts[6]++;
                                }
                                if (arrowPtCorrect == 0) {
                                    sums[7]++;
                                }
                                if (!Double.isNaN(shd)) {
                                    sums[8] += shd;
                                    counts[8]++;
                                }
                            }

                            counts[7] = 50;

                            for (int k = 0; k < sums.length; k++) {
                                sums[k] /= (double) counts[k];
                            }

                            out.println(i + "\t" + nf.format(sums[3]) + "\t" + nf.format(sums[4]) + "\t" + nf.format(sums[5]) + "\t" + nf.format(sums[6])
                                    + "\t" + nf.format(sums[7]) + "\t" + nf.format(sums[8]));


                        }
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Ignore
    public List<DataSet> loadSmithSim(int simIndex) {
        try {
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(false);
            reader.setDelimiter(DelimiterType.COMMA);

            List<DataSet> dataSets = new ArrayList<DataSet>();

            for (int dataIndex = 1; dataIndex <= 50; dataIndex++) {
                System.out.println("sim index " + simIndex + " data index " + dataIndex);

                //                    File file = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2012.10.27/pwdd3/simulation_1/" + prefix + dataIndex + ".txt");
                //                    File file = new File("/home/jdramsey/NETSIMdist/pwdd7/50_simulation_" + simIndex + "/" + prefix + "_" + dataIndex + ".txt");
                File file;

                file = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/smithsim/data/sim" + simIndex + "." + dataIndex + ".txt");

                if (!file.exists()) {
                    throw new RuntimeException(file.getAbsolutePath());
                }

                DataSet d = reader.parseTabular(file);

                dataSets.add(d);
            }

            return dataSets;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Graph readGraph(int simulation, int subject) throws IOException {

//        File infile = new File("/home/jdramsey/Downloads/smithsim/models/sim" + simulation + "." + subject + ".model.txt");
        File infile = new File("src/test/resources/smithsim/models/sim" + simulation + "." + subject + ".model.txt");

        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.COMMA);

        DataSet data = reader.parseTabular(infile);
        List<Node> variables = data.getVariables();
        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = 0; j < variables.size(); j++) {
                if (i == j) continue;

                if (data.getDouble(i, j) != 0) {
                    graph.addDirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        return graph;
    }
}





