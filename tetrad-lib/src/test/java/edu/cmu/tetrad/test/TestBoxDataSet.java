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
import edu.cmu.tetrad.util.TetradVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the various functions of a rectangular data set. The tests must make
 * use of interface methods only; calls to underlying methods of implementing
 * classes will undermine generality. The point of this is to allow a transition
 * to be made from Column-based DataSets to COLT-matrix-based datasets. All
 * tests should work for both.
 *
 * @author Joseph Ramsey
 */
public final class TestBoxDataSet {

    @Test
    public final void testContinuous() {
        int rows = 10;
        int cols = 5;
        List<Node> _variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            _variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(rows, _variables.size()), _variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        List<Node> variables = dataSet.getVariables();
        List<Node> newVars = new LinkedList<Node>();
        newVars.add(variables.get(2));
        newVars.add(variables.get(4));

        DataSet _dataSet = dataSet.subsetColumns(newVars);

        assertEquals(dataSet.getDoubleData().getColumn(2).get(0), _dataSet.getDoubleData().getColumn(0).get(0), .001);
        assertEquals(dataSet.getDoubleData().getColumn(4).get(0), _dataSet.getDoubleData().getColumn(1).get(0), .001);
    }

    @Test
    public void testDiscrete() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            DiscreteVariable variable = new DiscreteVariable("X" + (i + 1), 3);
            variables.add(variable);
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(rows, variables.size()), variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setInt(i, j, randomUtil.nextInt(3));
            }
        }

        BoxDataSet _dataSet = new BoxDataSet((BoxDataSet) dataSet);

        assertEquals(dataSet, _dataSet);
    }

//    @Test
//    public void testDiscreteFromScratch() {
//        DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, 0), Collections.EMPTY_LIST);
//
//        DiscreteVariable x1 = new DiscreteVariable("X1");
//        dataSet.addVariable(x1);
//        dataSet.setInt(0, 0, 0);
//        dataSet.setInt(1, 0, 2);
//        dataSet.setInt(2, 0, 1);
//
//        DiscreteVariable x2 = new DiscreteVariable("X2");
//        dataSet.addVariable(x2);
//        dataSet.setInt(0, 1, 0);
//        dataSet.setInt(1, 1, 2);
//        dataSet.setInt(2, 1, 1);
//
//        BoxDataSet _dataSet = new BoxDataSet((BoxDataSet) dataSet);
//
//        assertEquals(dataSet, _dataSet);
//
//        assertEquals(dataSet.getInt(1, 1), 2);
//    }

    @Test
    public void testMixed() {
        List<Node> variables = new LinkedList<Node>();

        DiscreteVariable x1 = new DiscreteVariable("X1");
        variables.add(x1);

        ContinuousVariable x2 = new ContinuousVariable("X2");
        variables.add(x2);

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(5, variables.size()), variables);

        assertTrue(dataSet.getVariables().get(0) instanceof DiscreteVariable);
        assertTrue(dataSet.getVariables().get(1) instanceof ContinuousVariable);
        assertTrue(dataSet.getInt(0, 0) == -99);
        assertTrue(Double.isNaN(dataSet.getDouble(1, 0)));
    }

    @Test
    public void testRemoveColumn() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(rows, variables.size()), variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        int[] _cols = new int[2];
        _cols[0] = 1;
        _cols[1] = 2;

        dataSet.removeCols(_cols);

        List<Node> _variables = new LinkedList<>(variables);
        _variables.remove(2);
        _variables.remove(1);

        assertEquals(dataSet.getVariables(), _variables);
    }

    @Test
    public void testRemoveRows() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(rows, variables.size()), variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        int numRows = dataSet.getNumRows();
        double d = dataSet.getDouble(3, 0);

        int[] _rows = new int[2];
        _rows[0] = 1;
        _rows[1] = 2;

        dataSet.removeRows(_rows);

        assertEquals(numRows - 2, dataSet.getNumRows());
        assertEquals(d, dataSet.getDouble(1, 0), 0.001);
    }

    @Test
    public void testRowSubset() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(rows, variables.size()), variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        double d = dataSet.getDouble(2, 0);

        DataSet _dataSet = dataSet.subsetRows(new int[]{2, 3, 4});

        assertEquals(3, _dataSet.getNumRows());
        assertEquals(d, _dataSet.getDouble(0, 0), 0.001);
    }

    @Test
    public void testPermuteRows() {
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");

        List<Node> nodes = new ArrayList<>();
        nodes.add(x1);
        nodes.add(x2);

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(3, nodes.size()), nodes);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        BoxDataSet _dataSet = new BoxDataSet((BoxDataSet) dataSet);

        dataSet.permuteRows();

        I:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            TetradVector v = _dataSet.getDoubleData().getRow(i);

            for (int j = 0; j < dataSet.getNumRows(); j++) {
                TetradVector w = dataSet.getDoubleData().getRow(j);

                if (v.equals(w)) {
                    continue I;
                }
            }

            fail("Missing row in permutation.");
        }
    }
}





