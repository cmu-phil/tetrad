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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.reveal;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test the Reveal evaluator.
 *
 * @author Frank Wimberly
 * @author Joseph Ramsey (translation to unit test)
 */
public class TestRevealEvaluator extends TestCase {
    private static final int ngenes = 6;
    private static final int ntimes = 9;
    private static final int[][] cases = new int[TestRevealEvaluator.ntimes][TestRevealEvaluator.ngenes];
    private static final double TOLERANCE = 0.000001;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestRevealEvaluator(final String name) {
        super(name);
    }

    /**
     * Tests whether the calculations in Liang, Figure 6 come out to the correct
     * values.
     */
    public void testLiangFigure6() {
        final int[] a = {0, 0, 0, 0, 1, 1, 1, 1};
        final int[] b = {0, 0, 1, 1, 0, 0, 1, 1};
        final int[] c = {0, 1, 0, 1, 0, 1, 0, 1};
        final int[] ap = {0, 0, 1, 1, 0, 0, 1, 1};
        final int[] bp = {0, 1, 0, 1, 1, 1, 1, 1};
        final int[] cp = {0, 0, 0, 1, 0, 1, 1, 1};

        for (int i = 0; i < TestRevealEvaluator.ntimes - 1; i++) {
            TestRevealEvaluator.cases[i][0] = a[i];
            TestRevealEvaluator.cases[i][1] = b[i];
            TestRevealEvaluator.cases[i][2] = c[i];
            TestRevealEvaluator.cases[i + 1][3] = ap[i];
            TestRevealEvaluator.cases[i + 1][4] = bp[i];
            TestRevealEvaluator.cases[i + 1][5] = cp[i];
        }
        TestRevealEvaluator.cases[8][0] = 0;
        TestRevealEvaluator.cases[8][1] = 0;
        TestRevealEvaluator.cases[8][2] = 0;
        TestRevealEvaluator.cases[0][3] = 0;
        TestRevealEvaluator.cases[0][4] = 0;
        TestRevealEvaluator.cases[0][5] = 0;

        final RevealEvaluator re = new RevealEvaluator(TestRevealEvaluator.cases);

        final double rea = re.entropy(a);
        TestCase.assertEquals(1.0, rea, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(a) = " + rea);  //Should be 1.0

        final double reb = re.entropy(b);
        TestCase.assertEquals(1.0, reb, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(b) = " + reb);  //Should be 1.0

        final double rec = re.entropy(c);
        TestCase.assertEquals(1.0, rec, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(c) = " + rec);  //Should be 1.0

        final double reab = re.jointEntropy(a, b);
        TestCase.assertEquals(2.0, reab, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(a,b) = " + reab);  //Should be 2.0

        final double reap = re.entropy(ap);
        TestCase.assertEquals(1.0, reap, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(ap) = " + reap);   //Should be 1.0

        final double rebp = re.entropy(bp);
        TestCase.assertEquals(0.8112781244591328, rebp, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(bp) = " + rebp);   //Should be 0.8112781244591328

        final double recp = re.entropy(cp);
        TestCase.assertEquals(1.0, recp, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(cp) = " + recp);   //Should be 1.0

        final double reapa = re.jointEntropy(ap, a);
        TestCase.assertEquals(2.0, reapa, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(ap, a) = " + reapa);  //Should be 2.0

        final double rebpb = re.jointEntropy(bp, b);
        TestCase.assertEquals(1.8112781244591327, rebpb, TestRevealEvaluator.TOLERANCE);
        System.out.println(
                "H(bp, b) = " + rebpb); //Should be 1.8112781244591327

        final double recpb = re.jointEntropy(cp, b);
        TestCase.assertEquals(1.8112781244591327, recpb, TestRevealEvaluator.TOLERANCE);
        System.out.println(
                "H(cp, b) = " + recpb); //Should be 1.8112781244591327

        final int[][] ab = new int[2][8];
        for (int i = 0; i < 8; i++) {
            ab[0][i] = a[i];
            ab[1][i] = b[i];
        }

        final double rebpab = re.jointEntropy(bp, ab);
        TestCase.assertEquals(2.5, rebpab, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(bp, a, b) = " + rebpab); //Should be 2.5

        final double recpab = re.jointEntropy(cp, ab);
        TestCase.assertEquals(2.5, recpab, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(cp, a, b) = " + recpab); //Should be 2.5

        final int[][] abc = new int[3][8];
        for (int i = 0; i < 8; i++) {
            abc[0][i] = a[i];
            abc[1][i] = b[i];
            abc[2][i] = c[i];
        }

        final double recpabc = re.jointEntropy(cp, abc);
        TestCase.assertEquals(3.0, recpabc, TestRevealEvaluator.TOLERANCE);
        System.out.println("H(cp, a, b, c) = " + recpabc); //Should be 3.0

        //Setup array cases and test mutualInformation
        final int[] p = new int[1];
        p[0] = 0;

        final double rembpa = re.mutualInformation(4, p, 1);
        TestCase.assertEquals(0.31127812445913294, rembpa, TestRevealEvaluator.TOLERANCE);
        System.out.println(
                "M(Bp, A) = " + rembpa); //Should be 0.31127812445913294

        final int[] pp = new int[2];
        pp[0] = 0;
        pp[1] = 1;

        final double rmcpab = re.mutualInformation(5, pp, 1);
        TestCase.assertEquals(0.5, rmcpab, TestRevealEvaluator.TOLERANCE);
        System.out.println("M(Cp, [A,B]) = " + rmcpab); //Should be 0.5

        final int[] ppp = new int[3];
        ppp[0] = 0;
        ppp[1] = 1;
        ppp[2] = 2;

        final double rmcpabc = re.mutualInformation(5, ppp, 1);
        TestCase.assertEquals(1.0, rmcpabc, TestRevealEvaluator.TOLERANCE);
        System.out.println("M(Cp, [A,B,C]) = " + rmcpabc); //Should be 1.0

    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestRevealEvaluator.class);
    }
}





