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

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;

/**
 * @author Frank Wimberly
 */
public final class TestDiscreteBootstrap extends TestCase {

    public TestDiscreteBootstrap(String name) {
        super(name);
    }

    public void testBootstrap1() {

        try {
            //String fileD = "c:/tetrad-4.2/test_data/embayes_l1x1x2x3MD.dat";
            String fileD = "src/test/resources/embayes_l1x1x2x3MD.dat";
            File file = new File(fileD);

            DataReader reader = new DataReader();
            DataSet dds = reader.parseTabular(file);

            // ddsNew not used.
//        RectangularDataSet ddsNew =
         //   BootstrapSampler.sample(dds, 200);

            /*
            assertEquals(0.4925, condProbs[3][0][0], 0.001);
            assertEquals(0.8977, condProbs[3][1][0], 0.001);
            assertEquals(0.6372, condProbs[3][2][0], 0.001);
            assertEquals(0.2154, condProbs[3][3][0], 0.001);
            */}
        catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDiscreteBootstrap.class);
    }
}





