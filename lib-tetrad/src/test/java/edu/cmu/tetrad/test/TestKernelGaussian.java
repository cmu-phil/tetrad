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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.kernel.KernelGaussian;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Tests the KernelGaussian class.
 *
 * @author Robert Tillman
 */
public class TestKernelGaussian extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestKernelGaussian(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
        TetradLogger.getInstance().setLogging(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    /**
     * Tests the bandwidth setting to the median distance between points in the sample
     */
    public void testMedianBandwidth() {
        Node X = new ContinuousVariable("X");
        DataSet dataset = new ColtDataSet(5, Arrays.asList(X));
        dataset.setDouble(0, 0, 1);
        dataset.setDouble(1, 0, 2);
        dataset.setDouble(2, 0, 3);
        dataset.setDouble(3, 0, 4);
        dataset.setDouble(4, 0, 5);
        KernelGaussian kernel = new KernelGaussian(dataset, X);
        assertTrue(kernel.getBandwidth() == 2);

    }

    public void testEval() {
        KernelGaussian kernel = new KernelGaussian(2.6851);
        System.out.println(kernel.eval(10.2381, 11.3825));
    }
}


