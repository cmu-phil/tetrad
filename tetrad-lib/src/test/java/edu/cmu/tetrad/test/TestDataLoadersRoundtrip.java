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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertTrue;

/**
 * Tests data loaders against sample files.
 *
 * @author Joseph Ramsey
 */
public class TestDataLoadersRoundtrip {

    public void setUp() {
        RandomUtil.getInstance().setSeed(302040392L);

        File file = new File("target/test_data");

        if (!file.exists()) {
            file.mkdir();
        }
    }

    @Test
    public void testContinuousRoundtrip() {
        setUp();

        try {
            List<Node> nodes = new ArrayList<Node>();

            for (int i = 0; i < 5; i++) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            }

            Graph randomGraph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                    30, 15, 15, false));
            SemPm semPm1 = new SemPm(randomGraph);
            SemIm semIm1 = new SemIm(semPm1);
            DataSet dataSet = semIm1.simulateData(10, false);

            FileWriter fileWriter = new FileWriter("target/test_data/roundtrip.dat");
            Writer writer = new PrintWriter(fileWriter);
            DataWriter.writeRectangularData(dataSet, writer, ',');
            writer.close();
//
            new File("test_data").mkdir();

            File file = new File("target/test_data/roundtrip.dat");
            DataReader reader = new DataReader();
            reader.setDelimiter(DelimiterType.COMMA);
            DataSet _dataSet = reader.parseTabular(file);

            assertTrue(dataSet.equals(_dataSet));
        }
        catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testDiscreteRoundtrip() {
        setUp();

        try {
            for (int i = 0; i < 1; i++) {
                List<Node> nodes = new ArrayList<>();

                for (int j = 0; j < 5; j++) {
                    nodes.add(new ContinuousVariable("X" + (j + 1)));
                }

                Graph randomGraph = new Dag(GraphUtils.randomGraph(nodes, 0, 8, 30, 15, 15, false));
                Dag dag = new Dag(randomGraph);
                BayesPm bayesPm1 = new BayesPm(dag);
                MlBayesIm bayesIm1 = new MlBayesIm(bayesPm1, MlBayesIm.RANDOM);
                DataSet dataSet = bayesIm1.simulateData(10, false);

                new File("target/test_data").mkdir();

                FileWriter fileWriter =
                        new FileWriter("target/test_data/roundtrip.dat");
                Writer writer = new PrintWriter(fileWriter);
                DataWriter.writeRectangularData(dataSet, writer, '\t');
                writer.close();

                File file = new File("target/test_data/roundtrip.dat");

                DataReader reader = new DataReader();
                reader.setKnownVariables(dataSet.getVariables());
                DataSet _dataSet = reader.parseTabular(file);

                assertTrue(dataSet.equals(_dataSet));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}





