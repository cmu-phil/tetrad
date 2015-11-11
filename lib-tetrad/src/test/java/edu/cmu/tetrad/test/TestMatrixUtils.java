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

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the MatrixUtils class.
 *
 * @author Joseph Ramsey
 */
public class TestMatrixUtils extends TestCase {

    public TestMatrixUtils(String name) {
        super(name);
    }

    public void testEquals() {
        double[][] m1 = {{1.0, 2.0, 3.0}};

        double[][] m2 = {{6.0, 7.0}, {8.0, 9.0}};

        double[][] m3 = {{6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.0, 27.0}};

        double[][] m4 = {{6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.05, 27.0}};

        assertTrue(!MatrixUtils.equals(m1, m2));
        assertTrue(MatrixUtils.equals(m3, m3));
        assertTrue(MatrixUtils.equals(m3, m4, 0.1));
    }

    public void testSum() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.0, 27.0},};

        double[][] m2 = {{3.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.05, 27.0},};

        double[][] m3 = MatrixUtils.sum(m1, m2);

        System.out.println(MatrixUtils.toString(m3));
    }

    public void testDeterminant() {
        double[][] m3 = {{6.0, 7.0}, {8.0, 9.0},};

        double determinant = MatrixUtils.determinant(m3);
        assertEquals(-2.0, determinant, 0.01);
    }

    public void testDirectProduct() {
        double[][] m1 = {{1.0, 2.0, 3.0}, {1.0, 2.0, 3.0}};

        double[][] m2 = {{6.0, 7.0}, {8.0, 9.0}};

        double[][] result = {{6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.0, 27.0},
                {6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.0, 27.0}};

        double[][] product = MatrixUtils.directProduct(m1, m2);
        assertTrue(MatrixUtils.equals(product, result));
    }

    public void testConcatenate() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0}, {8.0, 9.0, 16.0, 18.0}};

        double[] result = {6.0, 7.0, 12.0, 14.0, 8.0, 9.0, 16.0, 18.0};

        double[] concat = MatrixUtils.concatenate(m1);
        assertTrue(MatrixUtils.equals(concat, result));
    }

    public void testProduct() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0}, {8.0, 9.0, 16.0, 18.0}};

        double[][] m2 = {{6.0, 7.0, 12.0}, {8.0, 9.0, 16.0}, {3.0, -2.0, 4.0},
                {1.0, -4.0, 5.0}};

        double[][] result = {{142.0, 25.0, 302.0}, {186.0, 33.0, 394.0}};

        double[][] product = MatrixUtils.product(m1, m2);
        assertTrue(MatrixUtils.equals(product, result));
    }

    public void testSum0ToN() {
        assertEquals(0, MatrixUtils.sum0ToN(0));
        assertEquals(1, MatrixUtils.sum0ToN(1));
        assertEquals(3, MatrixUtils.sum0ToN(2));
        assertEquals(6, MatrixUtils.sum0ToN(3));
        assertEquals(10, MatrixUtils.sum0ToN(4));
    }

    /**
     * When premultiplied by vech, this should outerProduct vec.
     */
    public void testVechToVecLeft() {
        double[][] n = {{6.0, 8.0, 6.0, 8.0}, {8.0, 9.0, 7.0, 9.0},
                {6.0, 7.0, 12.0, 16.0}, {8.0, 9.0, 16.0, 18.0}};

        double[][] n1 = MatrixUtils.vec(n);
        double[][] n2 = MatrixUtils.vech(n);
        double[][] n3 = MatrixUtils.vechToVecLeft(4);
        double[][] n4 = MatrixUtils.product(n3, n2);
        assertTrue(MatrixUtils.equals(n4, n1));
    }


    public void testConvertCovMatrixToCorrMatrix() {
        double[][] data = {
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
        double[][] dataT = MatrixUtils.transpose(data);

        int size = 5;

        TetradMatrix m = new TetradMatrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                m.set(i, j, Descriptive.covariance(
                        new DoubleArrayList(dataT[i]),
                        new DoubleArrayList(dataT[j])));
            }
        }


        System.out.println("\n\nCovariance matrix: ");
        System.out.println(MatrixUtils.toString(m.toArray()));

        TetradMatrix corr = MatrixUtils.convertCovToCorr(new TetradMatrix(m));

        System.out.println("\n\nCorrelation matrix:");
        System.out.println(MatrixUtils.toString(corr.toArray()));
    }

    public void test5() {
        int[][] m = new int[][]{{1, 2}, {3, 4}};
        List<String> nodes = new ArrayList<String>();
        nodes.add("X1");
        nodes.add("X2");

        System.out.println(MatrixUtils.toString(m, nodes));

    }

    public void testImpiedCovar() {
        Graph graph = new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        TetradMatrix err = im.getErrCovar();
        TetradMatrix coef = im.getEdgeCoef();

        TetradMatrix implied = MatrixUtils.impliedCovar(coef, err);

//        for (int i = 0; i < implied.rows(); i++) {
//            for (int j = 0; j < implied.columns(); j++) {
//                if (abs(implied.get(i,j)) < 1e-10) implied.set(i, j, 0);
//            }
//        }

        System.out.println(MatrixUtils.toString(implied.toArray()));

        // Why the heck is this not positive definite?
        System.out.println("Positive definite implied: " + MatrixUtils.isPositiveDefinite(implied));

        TetradMatrix corr = MatrixUtils.convertCovToCorr(new TetradMatrix(implied));

        System.out.println(MatrixUtils.toString(corr.toArray()));

        System.out.println("Positive definite data corr: " + MatrixUtils.isPositiveDefinite(corr));
//
//        System.out.println(MatrixUtils.toString(err.toArray()));
//
//        System.out.println("Positive definite err: " + MatrixUtils.isPositiveDefinite(err));
    }

    public static Test suite() {
        return new TestSuite(TestMatrixUtils.class);
    }
}





