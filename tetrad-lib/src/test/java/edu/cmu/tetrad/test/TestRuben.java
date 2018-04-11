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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.CStaS;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the BayesIm.
 *
 * @author Joseph Ramsey
 */
public final class TestRuben {

    public void test1() {
        try {
            File dir = new File("//Users/user/Downloads");
            NumberFormat nf = new DecimalFormat("00");

            for (int d = 1; d <= 10; d++) {
                Dataset dataset = new ContinuousTabularDataFileReader(new File(dir, "data." + nf.format(d) + ".csv"), Delimiter.TAB).readInData();
                final DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

                List<Node> nodes = dataSet.getVariables();

                double[][] pi = new double[nodes.size()][nodes.size()];

                int K = 100;

                for (int k = 0; k < K; k++) {
                    BootstrapSampler sampler = new BootstrapSampler();
                    DataSet sample;

                    sampler.setWithoutReplacements(true);
                    sample = sampler.sample(dataSet, dataSet.getNumRows() / 2);

                    SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(sample));
                    score.setPenaltyDiscount(2);
                    Fask fask = new Fask(dataSet, score);

                    Graph dg = fask.search();

                    for (int i = 0; i < nodes.size(); i++) {
                        for (int j = 0; j < nodes.size(); j++) {
                            Node x = nodes.get(i);
                            Node y = nodes.get(j);

                            if (dg.isParentOf(x, y)) pi[i][j]++;
                        }
                    }
                }

                for (int i = 0; i < nodes.size(); i++) {
                    for (int j = 0; j < nodes.size(); j++) {
                        Node x = nodes.get(i);
                        Node y = nodes.get(j);

                        pi[i][j] /= (double) K;
                    }
                }

                DoubleDataBox box = new DoubleDataBox(pi);
                BoxDataSet outData = new BoxDataSet(box, nodes);

                PrintStream out = new PrintStream(new FileOutputStream(new File(dir, "outPi." + nf.format(d) + ".txt")));

                out.println(outData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        new TestRuben().test1();
    }
}




