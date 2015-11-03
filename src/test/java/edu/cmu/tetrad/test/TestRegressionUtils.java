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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.regression.RegressionUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the new regression classes. There is a tabular linear regression
 * model as well as a correlation linear regression model. (Space for more
 * in the future.)
 *
 * @author Joseph Ramsey
 */
public class TestRegressionUtils extends TestCase {
    DataSet data;

    public TestRegressionUtils(String name) {
        super(name);
    }

    // Residuals for guys with no parents should be identical to the original data values.
    public void testRegresssionUtils() {
        Dag graph = new Dag(new Dag(GraphUtils.randomGraph(5, 0, 3, 3,
                3, 3, false)));

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        
        DataSet dataSet = im.simulateData(1000, false);

//        System.out.println(dataSet);

        DataSet residuals = RegressionUtils.residuals(dataSet, graph);
//
//        System.out.println(residuals);

//        for (Node node : graph.getNodes()) {
//            if (!graph.getParents(node).isEmpty()) {
//                continue;
//            }
//
//            Node nodeDataSet = dataSet.getVariable(node.getName());
//            int j = dataSet.getVariables().indexOf(nodeDataSet);
//
//            for (int i = 0; i < dataSet.getNumRows(); i++) {
//                System.out.println(i);
//
//                assertEquals(dataSet.getDouble(i, j), residuals.getDouble(i, j), 0.001);
//            }
//        }
    }

    public static Test suite() {
        return new TestSuite(TestRegressionUtils.class);
    }
}



