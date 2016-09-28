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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the MatrixUtils class.
 *
 * @author Joseph Ramsey
 */
public class TestMatrixUtils {

    @Test
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

    @Test
    public void testSum() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.0, 27.0},};

        double[][] m2 = {{3.0, 7.0, 12.0, 14.0, 18.0, 21.0},
                {8.0, 9.0, 16.0, 18.0, 24.05, 27.0},};

        double[][] m3 = MatrixUtils.sum(m1, m2);

        assertEquals(32., m3[1][2], .01);
    }

    @Test
    public void testDeterminant() {
        double[][] m3 = {{6.0, 7.0}, {8.0, 9.0},};

        double determinant = MatrixUtils.determinant(m3);
        assertEquals(-2.0, determinant, 0.01);
    }

    @Test
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

    @Test
    public void testConcatenate() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0}, {8.0, 9.0, 16.0, 18.0}};

        double[] result = {6.0, 7.0, 12.0, 14.0, 8.0, 9.0, 16.0, 18.0};

        double[] concat = MatrixUtils.concatenate(m1);
        assertTrue(MatrixUtils.equals(concat, result));
    }

    @Test
    public void testProduct() {
        double[][] m1 = {{6.0, 7.0, 12.0, 14.0}, {8.0, 9.0, 16.0, 18.0}};

        double[][] m2 = {{6.0, 7.0, 12.0}, {8.0, 9.0, 16.0}, {3.0, -2.0, 4.0},
                {1.0, -4.0, 5.0}};

        double[][] result = {{142.0, 25.0, 302.0}, {186.0, 33.0, 394.0}};

        double[][] product = MatrixUtils.product(m1, m2);
        assertTrue(MatrixUtils.equals(product, result));
    }

    @Test
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
    @Test
    public void testVechToVecLeft() {
        double[][] n = {{6.0, 8.0, 6.0, 8.0}, {8.0, 9.0, 7.0, 9.0},
                {6.0, 7.0, 12.0, 16.0}, {8.0, 9.0, 16.0, 18.0}};

        double[][] n1 = MatrixUtils.vec(n);
        double[][] n2 = MatrixUtils.vech(n);
        double[][] n3 = MatrixUtils.vechToVecLeft(4);
        double[][] n4 = MatrixUtils.product(n3, n2);
        assertTrue(MatrixUtils.equals(n4, n1));
    }

    @Test
    public void testImpiedCovar() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 10, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        TetradMatrix err = im.getErrCovar();
        TetradMatrix coef = im.getEdgeCoef();

        TetradMatrix implied = MatrixUtils.impliedCovar(coef, err);

        assertTrue(MatrixUtils.isPositiveDefinite(implied));

        TetradMatrix corr = MatrixUtils.convertCovToCorr(new TetradMatrix(implied));

        assertTrue(MatrixUtils.isPositiveDefinite(corr));
    }
}





