/**
 * Created by jdramsey on 11/18/15.
 */
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

import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.RegexTokenizer;
import edu.cmu.tetrad.search.ClusterUtils;
import edu.cmu.tetrad.search.FastIca;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * JUnit test for the regression classes.
 *
 * @author Frank Wimberly
 */
public class TestFastIca extends TestCase {
    public TestFastIca(String name) {
        super(name);
    }

    public void test() {
        // placeholder.
    }

//    public void test1C() {
//        try {
//            TetradMatrix X = readFromFile("test_data/icaTestX.txt", 10, 5);
//            TetradMatrix wInit = readFromFile("test_data/icaTestWInit.txt", 3, 3);
//
//            System.out.println("X");
//            System.out.println("w.init " + wInit);
//
//            FastIca fastIca = new FastIca(new DenseDoubleMatrix2D(X.transpose().toArray()), 3);
//            fastIca.setWInit(new DenseDoubleMatrix2D(wInit.toArray()));
//            fastIca.setAlgorithmType(FastIca.PARALLEL);
//            fastIca.setFunction(FastIca.EXP);
//            fastIca.setVerbose(true);
//
//            FastIca.IcaResult r = fastIca.findComponents();
//
//            System.out.println(r);
//
//            TetradMatrix s = new TetradMatrix(r.getS().toArray());
//
//            System.out.println(s.transpose().times(s));
//
////            System.out.println("SA " + TetradAlgebra.times(r.getS(), r.getA()));
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public void rtest2() {
//        int n = 54000;
//        int p = 400;
//        int m = 40;
//
//        TetradMatrix X = new TetradMatrix(n, p);
//        ClusterUtils.initRandomly(X);
//
////        System.out.println("X" + X);
//
//        FastIca fastIca = new FastIca(new DenseDoubleMatrix2D(X.toArray()), m);
//        fastIca.setAlgorithmType(FastIca.PARALLEL);
//        fastIca.setFunction(FastIca.EXP);
//        fastIca.setVerbose(true);
//
//        FastIca.IcaResult r = fastIca.findComponents();
//
////        System.out.println(r);
//
////        System.out.println("SA " + TetradAlgebra.times(r.getS(), r.getA()));
//    }

//    public void rtest3() {
//        try {
//            int n = 1000;
//            int p = 400;
//            int m = 40;
//
//            TetradMatrix X = new TetradMatrix(n, p);
//            ClusterUtils.initRandomly(X);
//
////            System.out.println("X" + X);
//
//            FastIca fastIca = new FastIca(new DenseDoubleMatrix2D(X.transpose().toArray()), m);
//
//            TetradMatrix S = new TetradMatrix(fastIca.getICVectors());
//            TetradMatrix A = new TetradMatrix(fastIca.getMixingMatrix());
//
////            System.out.println("SA " + TetradAlgebra.times(S.transpose(), A.transpose()));
//
//        } catch (FastIcaException e) {
//            e.printStackTrace();
//        }
//    }

//    public void test1D() {
//        try {
//            TetradMatrix X = readFromFile("test_data/icaTestX.txt", 10, 5);
//            TetradMatrix wInit = readFromFile("test_data/icaTestWInit.txt", 3, 3);
//
//            System.out.println(X);
//            System.out.println(wInit);
//
//            FastIca fastIca = new FastIca(X.transpose().toArray(), 3);
//
//            TetradMatrix mixingMatrix = new TetradMatrix(fastIca.getMixingMatrix());
//
//            System.out.println(mixingMatrix);
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (FastIcaException e) {
//            e.printStackTrace();
//        }
//
//    }

    private TetradMatrix readFromFile(String s, int rows, int columns)
            throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(s));
        TetradMatrix m = new TetradMatrix(rows, columns);

        for (int i = 0; i < rows; i++) {
            String line = in.readLine();
            RegexTokenizer tokenizer = new RegexTokenizer(line,
                    DelimiterType.WHITESPACE.getPattern(), '\"');

            for (int j = 0; j < columns; j++) {
                m.set(i, j, Double.parseDouble(tokenizer.nextToken()));
            }
        }

        return m;
    }

    public static Test suite() {
        return new TestSuite(TestFastIca.class);
    }
}




