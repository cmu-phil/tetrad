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

import edu.cmu.tetrad.util.MathUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Tests some extra mathematical functions not contained in java.lang.Math.
 *
 * @author Joseph Ramsey
 */
public class TestMathUtils extends TestCase {

    public TestMathUtils(String name) {
        super(name);
    }

    public void testLogistic() {
//        for (double d = -10.; d < 10.; d+= 0.5) {
//            System.out.println(MathUtils.logistic(d));
//        }

        assertTrue(MathUtils.logistic(0.) == 0.5);
        assertTrue(MathUtils.logistic(-10.) < 1.e-4);
        assertTrue(MathUtils.logistic(+10.) > 1. - 1.e-4);
    }

//    public void testTemp() {
//        TetradMatrix iMinusB = TetradMatrix.instance(3, 3);
//        iMinusB.set(0, 0, 1);
//        iMinusB.set(0, 1, 0);
//        iMinusB.set(0, 2, 0);
//        iMinusB.set(1, 0, -1);
//        iMinusB.set(1, 1, 1);
//        iMinusB.set(1, 2, 0);
//        iMinusB.set(2, 0, -1);
//        iMinusB.set(2, 1, -1);
//        iMinusB.set(2, 2, 1);
//
//        System.out.println(TetradAlgebra.inverse(iMinusB));
//    }

//    public void testBigExp() {
//        double d = 20000;
//
//        System.out.println(d);
//        System.out.println(Math.exp(d));
//        System.out.println(MathUtils.bigExp(d));
//        System.out.println(Math.log(MathUtils.bigExp(d)));
//    }

    public void testExpSums() {
        double d = 100.0;

        System.out.println(Math.exp(d + d));
        System.out.println(Math.exp(d) * Math.exp(d));
    }

    public static Test suite() {
        return new TestSuite(TestMathUtils.class);
    }
}


