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

import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;
import edu.cmu.tetrad.util.dist.Uniform;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Arrays;

public class TestDistributions extends TestCase {
    public TestDistributions(String name) {
        super(name);
    }

    public void testNormal() {
        Distribution normal = new Normal(10, 1);
        printSortedRandoms(normal);
    }

    public void testUniform() {
        Distribution distribution = new Uniform(-2, 2);
        printSortedRandoms(distribution);
    }

    private void printSortedRandoms(Distribution distribution) {
        double[] values = new double[100];

        for (int i = 0; i < 100; i++) {
            values[i] = distribution.nextRandom();
        }

        Arrays.sort(values);

        for (int i = 0; i < 100; i++) {
            System.out.println(values[i]);
        }
    }

    public static Test suite() {
        return new TestSuite(TestDistributions.class);
    }
}



