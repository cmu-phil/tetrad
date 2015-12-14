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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests CovarianceMatrix.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class TestCovarianceMatrix {

    /**
     * Tests construction.
     */
    @Test
    public void testConstruction() {
        RandomUtil.getInstance().setSeed(4828384834L);

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < 5; i++) {
            ContinuousVariable var = new ContinuousVariable("X" + i);
            variables.add(var);
        }

        DataSet dataSet = new ColtDataSet(10, variables);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 5; j++) {
                dataSet.setDouble(i, j, RandomUtil.getInstance().nextDouble());
            }
        }

        DataSet _dataSet = new ColtDataSet((ColtDataSet) dataSet);

        ICovarianceMatrix c1 = new CovarianceMatrix(dataSet);
        ICovarianceMatrix c2 = new CovarianceMatrixOnTheFly(dataSet);
        CorrelationMatrix c3 = new CorrelationMatrix(c1);

        assertEquals(.089, c1.getValue(0, 0), 0.001);
        assertEquals(.089, c2.getValue(0, 0), 0.001);
        assertEquals(1, c3.getValue(0, 0), 0.001);

        // In place should modify the original covariance matrix.
        CorrelationMatrix c4 = new CorrelationMatrix(c1, true);
        assertEquals(1, c1.getValue(0, 0), 0.001);

        c1 = new CovarianceMatrix(_dataSet);
        c2 = new CovarianceMatrixOnTheFly(_dataSet);
        c3 = new CorrelationMatrix(c1);

        assertEquals(-.051, c1.getValue(0, 1), 0.001);
        assertEquals(-.051, c2.getValue(0, 1), 0.001);
        assertEquals(-.609, c3.getValue(0, 1), 0.001);
    }
}





