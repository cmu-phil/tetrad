///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Vector;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.text.DecimalFormat;

import static org.junit.Assert.assertEquals;

/**
 * Tests basic functionality of the tetrad.util.Point class.
 *
 * @author josephramsey
 */
public class TestMatrix {

    @Test
    public void test1() {
        Matrix x = new Matrix(4, 0);

        Matrix xT = x.transpose();
        Matrix xTx = xT.times(x);
        Matrix xTxInv = xTx.inverse();

        assertEquals(0, xTx.trace(), 0.01);
        assertEquals(0, xTxInv.trace(), 0.01);
    }

    @Test
    public void test2() {
        Matrix A = new Matrix(SimpleMatrix.random_DDRM(4, 4));
        NumberFormatUtil.getInstance().setNumberFormat(new DecimalFormat("0.###"));

        System.out.println(A);

        A.set(0, 0, 1);
        A.set(0, 1, 2);

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));

        A.view(new int[]{2, 3}, new int[]{2, 3}).set(new Matrix(new double[][]{{-2, -3}, {-1, -2}}));

        A.view().setRow(0, new Vector(new double[]{2, 3, 4, 5}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));

        A.view().setColumn(3, new Vector(new double[]{2, 3, 4, 5}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));

        A.viewRow(2).setRow(2, new Vector(new double[]{-5, -6, -7, -8}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));

        A.viewColumn(2).setColumn(2, new Vector(new double[]{5, 6, 7, 8}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));

        A.view(new int[]{1, 2}, new int[]{1, 2}).view(new int[]{0}, new int[]{0, 1}).set(new Matrix(new double[][]{{-2, -3}}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));


        // Calculation.

        System.out.println(A.view(new int[]{1, 2}, new int[]{1, 2}).mat().times(A.view(new int[]{1, 2}, new int[]{1, 2}).mat().inverse()));

        System.out.println("1. " + A.viewRow(1).mat());

        System.out.println(A.row(1));

        System.out.println("2. " + A.viewColumn(3).mat());

        System.out.println(A.col(1));
    }
}






