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

import edu.cmu.tetrad.util.StatUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Implements some tests of the FDR (False Discovery Rate) test.
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings({"UnusedDeclaration"})
public class TestFdr extends TestCase {
    public TestFdr(String name) {
        super(name);
    }

    public void testSimpleCase() {
        double[] p = new double[]{
                .8, .01, .2, .07, .003, .9, .05, .03, .0001
        };

        List<Double> pValues = new ArrayList<Double>();
        for (double _p : p) pValues.add(_p);

        double alpha = 0.05;
        boolean negativelyCorrelated = false;

        double cutoff = StatUtils.fdrCutoff(alpha, pValues, negativelyCorrelated);

        System.out.println("Cutoff = " + cutoff);

        Collections.sort(pValues);

        for (int i = 0; i < pValues.size(); i++) {
            if (pValues.get(i) <= cutoff) {
                System.out.println(i + ": " + pValues.get(i));
            }
        }

        assertEquals(.01, cutoff);

        negativelyCorrelated = true;
        cutoff = StatUtils.fdrCutoff(alpha, pValues, negativelyCorrelated);
        assertEquals(0.003, cutoff);

    }

    public static Test suite() {
        return new TestSuite(TestFdr.class);
    }
}


