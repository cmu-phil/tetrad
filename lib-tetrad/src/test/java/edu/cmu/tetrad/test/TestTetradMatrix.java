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

import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests basic functionality of the tetrad.util.Point class.
 *
 * @author Joseph Ramsey
 */
public class TestTetradMatrix extends TestCase {
    public TestTetradMatrix(String name) {
        super(name);
    }

    public void test1() {
        TetradMatrix x = new TetradMatrix(4, 0);

//        x.set(0, 0, 1.0);
//        x.set(1, 0, 1.5);
//        x.set(2, 0, 1.3);
//        x.set(3, 0, 1.1);

        TetradMatrix y = new TetradMatrix(4, 1);

        TetradMatrix xT = x.transpose();
        TetradMatrix xTx = xT.times(x);
        TetradMatrix xTxInv = xTx.inverse();
        TetradMatrix xTy = xT.times(y);
        TetradMatrix b = xTxInv.times(xTy);
//        if (b.columns() == 0) {
//            b = y.like();
//            for (int i = 0; i < b.rows(); i++) b.set(i, 0, 0.0);
//        }

        TetradMatrix yHat = x.times(b);
        if (yHat.columns() == 0) yHat = y.like();

        TetradMatrix res = y.minus(yHat); //  y.copy().assign(yHat, PlusMult.plusMult(-1));

        TetradVector _yHat = yHat.getColumn(0);
        TetradVector _res = res.getColumn(0);
    }

    public void test2() {
        TetradMatrix m = new TetradMatrix(0, 0);
    }

    public static Test suite() {
        return new TestSuite(TestTetradMatrix.class);
    }
}





