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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import static org.junit.Assert.assertEquals;

/**
 * @author Joseph Ramsey
 */
public final class TestFaskOrientationForRuben {

    public void test1() {
        File dir = new File("/Users/user/Downloads/MacaqueFull_Joe");

        Graph trues = GraphUtils.loadGraphTxt(new File(dir, "graph/Markov_Complex_Full.txt"));

//        System.out.println(trues);


        for (int i = 1; i <= 60; i++) {
            try {
                NumberFormat nf = new DecimalFormat("00");

//                final File skeletonFile = new File(dir,
//                        "Alasso_MarkovFull/BOLDfslfilter_dataconcat_" + nf.format(i) + "_lambda_0.5_graph.txt");

                final File skeletonFile = new File(dir,
                        "TwoStep_testing/Cnct_" + nf.format(i) + "_2SMB_lambda_010_Bthresh_0.005.txt");


                Graph skeleton = GraphUtils.loadGraphTxt(skeletonFile);
//                System.out.println(skeleton);

                final File dataFile = new File(dir,
                        "data_fslfilter_concat/concat_BOLDfslfilter_" + nf.format(i) + ".txt");

                Dataset dataset = new ContinuousTabularDataFileReader(dataFile, Delimiter.TAB).readInData();

                DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

//                System.out.println(dataSet);

                SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));

                Fask fask = new Fask(dataSet, score);
                fask.setAlpha(0.10);
                fask.setDelta(-0.2);
                fask.setInitialGraph(skeleton);
                Graph positives = fask.search();
//
//                Graph positives = new EdgeListGraph(skeleton);

//                System.out.println("Positives " + positives);

                positives = GraphUtils.replaceNodes(skeleton, trues.getNodes());

//                Set<Edge> truePositives = new HashSet<>(trues.getEdges());
//                truePositives.retainAll(positives.getEdges());
//
//                Set<Edge> trueNegatives = new HashSet<>(trues.getEdges());
//                trueNegatives.removeAll(positives.getEdges());
//
//                Set<Edge> falsePositives = new HashSet<>(positives.getEdges());
//                falsePositives.removeAll(trues.getEdges());

//                double precision = truePositives.size() / (double) (truePositives.size() + falsePositives.size());
//                double recall = truePositives.size() / (double) (truePositives.size() + trueNegatives.size());

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(positives, trues);

                int count = 0;

                for (Edge edge : positives.getEdges()) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();

                    if (trues.isAncestorOf(x, y)) {
//                        System.out.println(edge);
                        ++count;
                    }
                }

                final int ahdCor = count;//comparison.getAhdCor();
                int diff = count - comparison.getAhdCor();

//                System.out.println("# extra transitive = " + diff);

                NumberFormat nf2 = new DecimalFormat("0.00");

//                final int ahdFp = comparison.getAhdFp() - diff;
                double prec = ahdCor / (double) (positives.getNumEdges());
                double rec = ahdCor / (double) (trues.getNumEdges());

                System.out.println(i +
                        "\t" + nf2.format(comparison.getAdjPrec()) +
                        "\t" + nf2.format(comparison.getAdjRec()) +
                        "\t" + nf2.format(prec) +
                        "\t" + nf2.format(rec)
                );


            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    public static void main(String... args) {
        new TestFaskOrientationForRuben().test1();
    }
}




