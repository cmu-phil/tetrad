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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestGSquare;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the IndTestTimeSeries class.
 *
 * @author josephramsey
 */
public class TestIndTestGSquare {
    private final String[] discreteFiles = {
            "src/test/resources/embayes_l1x1x2x3MD.dat",
            "src/test/resources/determinationtest.dat"};

    @Test
    public void testIsIndependent() {
        try {
            DataSet dataSet = getDataSet();

            IndTestGSquare test = new IndTestGSquare(dataSet, 0.05);
            List<Node> v = test.getVariables();

            Node x = v.get(0);
            Node y = v.get(1);
            Set<Node> z = new HashSet<>();
            z.add(v.get(2));
            assertTrue(test.checkIndependence(x, y, z).isIndependent());

            test.setDeterminationP(0.99);
            assertFalse(test.determines(z, x));
            assertFalse(test.determines(z, y));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Test
    public void testDetermination() {
        try {
            DataSet dataSet = getDataSet();

            IndTestGSquare test = new IndTestGSquare(dataSet, 0.05);

            Node x = dataSet.getVariable("X4");
            Set<Node> z = new HashSet<>();

            test.setDeterminationP(0.99);
            assertFalse(test.determines(z, x));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private DataSet getDataSet() throws IOException {
        String filename = this.discreteFiles[1];
        System.out.println("Loading " + filename);

        return SimpleDataLoader.loadDiscreteData(new File(filename),
                "//", '\"', "-99", true, Delimiter.TAB,
                false);
    }
}






