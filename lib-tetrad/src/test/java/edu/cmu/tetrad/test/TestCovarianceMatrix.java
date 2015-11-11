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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.LinkedList;
import java.util.List;

/**
 * Tests CovarianceMatrix.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class TestCovarianceMatrix extends TestCase {

    /**
     * This is a randomly generated data set with 5 variables and 10 records.
     */
    private final double[][] data = {
            {-0.377133, -1.480267, -1.696021, 1.195592, -0.345426},
            {-0.694507, -2.568514, -4.654334, 0.094623, -6.081831},
            {1.819202, 0.693551, 4.626220, 1.228998, 8.082000},
            {-0.131759, -0.256599, -1.319799, 0.304622, -2.588121},
            {-1.407105, -1.455764, -2.185402, -3.848737, -4.357246},
            {-1.099269, -1.892556, -1.639330, -1.156234, -4.174009},
            {-0.273420, -0.079434, 0.226354, 0.919383, 1.151157},
            {0.358854, -0.982877, 0.890740, 1.850120, 1.504533},
            {-0.407574, -0.316400, -1.423396, 0.991819, -0.956139},
            {1.243824, 1.690462, 4.045195, 1.346460, 5.247904}};

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestCovarianceMatrix(final String name) {
        super(name);
    }

    /**
     * Tests construction.
     */
    public void testConstruction() {
        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < 5; i++) {
            ContinuousVariable var = new ContinuousVariable("X" + i);
            variables.add(var);
        }

        DataSet dataSet = new ColtDataSet(10, variables);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 5; j++) {
                dataSet.setDouble(i, j, this.data[i][j]);
            }
        }

        System.out.println(dataSet);
        ICovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        System.out.println("covMatrix = " + covMatrix);
        CorrelationMatrix corrMatrix = new CorrelationMatrix(covMatrix);
        System.out.println("corrMatrix = " + corrMatrix);
        CorrelationMatrix corrMatrix2 = new CorrelationMatrix(dataSet);
        System.out.println("corrMatrix2 = " + corrMatrix2);

        ICovarianceMatrix cov3 = new CovarianceMatrix(dataSet);

        System.out.println("cov3 = " + cov3);
    }

    public static void testPositiveDefinite() {
        String[] varNames = new String[]{"X1", "X2", "X3"};
        double[][] mUpper = new double[][]{{1.0}, {.3, 1.0}, {0.8, -.2, 1.0}};

        TetradMatrix m =
                new TetradMatrix(mUpper.length, mUpper.length);

        for (int i = 0; i < mUpper.length; i++) {
            for (int j = 0; j < mUpper.length; j++) {
                if (j <= i) {
                    m.set(i, j, mUpper[i][j]);
                }
                else {
                    m.set(i, j, mUpper[j][i]);
                }
            }
        }

        new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), m, 30);
    }

    public static void testEditing() {

    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestCovarianceMatrix.class);
    }
}





