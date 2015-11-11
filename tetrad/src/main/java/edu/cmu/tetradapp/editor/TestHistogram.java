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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.LinkedList;
import java.util.List;


/**
 * Tests data loaders against sample files.
 *
 * @author Joseph Ramsey
 */
public class TestHistogram extends TestCase {
    public TestHistogram(String name) {
        super(name);
    }

    public void test1() {
        List<Node> nodes = new LinkedList<Node>();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        nodes.add(x1);
        nodes.add(x2);

        TetradMatrix dataMatrix = new TetradMatrix(10, 2);

        dataMatrix.set(0, 0, 0);
        dataMatrix.set(1, 0, 0);
        dataMatrix.set(2, 0, 0);
        dataMatrix.set(3, 0, 0);
        dataMatrix.set(4, 0, 0);
        dataMatrix.set(5, 0, 1);
        dataMatrix.set(6, 0, 1);
        dataMatrix.set(7, 0, 1);
        dataMatrix.set(8, 0, 1);
        dataMatrix.set(9, 0, 1);

        dataMatrix.set(0, 1, 0);
        dataMatrix.set(1, 1, 1);
        dataMatrix.set(2, 1, 1);
        dataMatrix.set(3, 1, 1);
        dataMatrix.set(4, 1, 1);
        dataMatrix.set(5, 1, 0);
        dataMatrix.set(6, 1, 0);
        dataMatrix.set(7, 1, 0);
        dataMatrix.set(8, 1, 0);
        dataMatrix.set(9, 1, 1);


        DataSet dataSet = ColtDataSet.makeContinuousData(nodes, dataMatrix);

//        Histogram histogram = new Histogram(dataSet, );
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to
     *
     * the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestHistogram.class);
    }
}


