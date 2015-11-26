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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the new regression classes. There is a tabular linear regression
 * model as well as a correlation linear regression model. (Space for more
 * in the future.)
 *
 * @author Joseph Ramsey
 */
public class TestRegression extends TestCase {
    DataSet data;

    public TestRegression(String name) {
        super(name);
    }

    public void setUp() {
        RandomUtil.getInstance().setSeed(342233L);
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 3,
                3, 3, false));

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        data = im.simulateDataReducedForm(1000, false);
    }

    /**
     * This tests whether the answer to a rather arbitrary problem changes.
     * At one point, this was the answer being returned.
     */
    public void testTabular() {
        List<Node> nodes = data.getVariables();

        Node target = nodes.get(0);
        List<Node> regressors = new ArrayList<Node>();

        for (int i = 1; i < nodes.size(); i++) {
            regressors.add(nodes.get(i));
        }

        Regression regression = new RegressionDataset(data);
        RegressionResult result = regression.regress(target, regressors);

        System.out.println(result);

        double[] coeffs = result.getCoef();
        assertEquals(-.01, coeffs[0], 0.01);
        assertEquals(-.1, coeffs[1], 0.01);
        assertEquals(-0.01, coeffs[2], 0.01);
        assertEquals(0.17, coeffs[3], 0.01);
        assertEquals(-.23, coeffs[4], 0.01);
    }

    /**
     * Same problem, using the covariance matrix.
     */
    public void testCovariance() {
        ICovarianceMatrix cov = new CovarianceMatrix(data);
        List<Node> nodes = cov.getVariables();

        Node target = nodes.get(0);
        List<Node> regressors = new ArrayList<Node>();

        for (int i = 1; i < nodes.size(); i++) {
            regressors.add(nodes.get(i));
        }

        Regression regression = new RegressionCovariance(cov);
        RegressionResult result = regression.regress(target, regressors);

        System.out.println(result);

        double[] coeffs = result.getCoef();
        assertEquals(0.0, coeffs[0], 0.01);
        assertEquals(-.16, coeffs[1], 0.01);
        assertEquals(-0.02, coeffs[2], 0.01);
        assertEquals(.41, coeffs[3], 0.01);
        assertEquals(-.4, coeffs[4], 0.01);
    }

    private char[] fileToCharArray(File file) {
        try {
            FileReader reader = new FileReader(file);
            CharArrayWriter writer = new CharArrayWriter();
            int c;

            while ((c = reader.read()) != -1) {
                writer.write(c);
            }

            return writer.toCharArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DataSet loadCarsFile() {
        File file = new File("test_data/cars.dat");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);

        return reader.parseTabular(chars);
    }

    private DataSet loadRegressionDataFile() {
        File file = new File("test_data/regressiondata.dat");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);

        DataSet data = reader.parseTabular(chars);
        return data;
    }

    public static Test suite() {
        return new TestSuite(TestRegression.class);
    }
}




