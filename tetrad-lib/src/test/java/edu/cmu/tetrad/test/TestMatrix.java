///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.util.*;
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

        A.view(new int[]{1, 2}, new int[]{1, 2}).view(new int[]{1}, new int[]{1, 2}).set(new Matrix(new double[][]{{-2, -3}}));

        System.out.println(MatrixUtils.toString(A.toArray(), new DecimalFormat("0.###")));


        // Calculation.

        System.out.println(A.view(new int[]{1, 2}, new int[]{1, 2}).matrix().times(A.view(new int[]{1, 2}, new int[]{1, 2}).matrix().inverse()));


    }
}





