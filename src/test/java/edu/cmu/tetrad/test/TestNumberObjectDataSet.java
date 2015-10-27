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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Tests the various functions of a rectangular data set. The tests must make
 * use of interface methods only; calls to underlying methods of implementing
 * classes will undermine generality. The point of this is to allow a transition
 * to be made from Column-based DataSets to COLT-matrix-based datasets. All
 * tests should work for both.
 *
 * @author Joseph Ramsey
 */
public final class TestNumberObjectDataSet extends TestCase {
    public TestNumberObjectDataSet(String name) {
        super(name);
    }

    public final void testContinuous() {
        int rows = 10;
        int cols = 5;
        List<Node> _variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            _variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new ColtDataSet(rows, _variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        System.out.println(dataSet);

        List<Node> variables = dataSet.getVariables();
        List<Node> newVars = new LinkedList<Node>();
        newVars.add(variables.get(2));
        newVars.add(variables.get(4));

        DataSet _dataSet = dataSet.subsetColumns(newVars);

        System.out.println(_dataSet);

        assertTrue(dataSet.equals(dataSet));
    }

    public static void testDiscrete() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            DiscreteVariable variable = new DiscreteVariable("X" + i, 10);
            variables.add(variable);
        }

        DataSet dataSet = new ColtDataSet(rows, variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setInt(i, j, randomUtil.nextInt(10));
            }
        }

        System.out.println(dataSet);
    }

    public static void testInitialization() {
        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < 5; i++) {
            DiscreteVariable variable = new DiscreteVariable("X" + i, 10);
            variables.add(variable);
        }

        DataSet dataSet = new ColtDataSet(5, variables);

        System.out.println(dataSet);
    }

    public static void testDiscreteFromScratch() {
        DataSet dataSet = new ColtDataSet(0, Collections.EMPTY_LIST);

        DiscreteVariable x1 = new DiscreteVariable("X1");
        dataSet.addVariable(x1);
        dataSet.setInt(0, 0, 0);
        dataSet.setInt(1, 0, 2);
        dataSet.setInt(2, 0, 1);

        DiscreteVariable x2 = new DiscreteVariable("X2");
        dataSet.addVariable(x2);
        dataSet.setInt(0, 1, 0);
        dataSet.setInt(1, 1, 2);
        dataSet.setInt(2, 1, 1);

        System.out.println(dataSet);
    }


    public static void testMixed() {
        List<Node> variables = new LinkedList<Node>();

        DiscreteVariable x1 = new DiscreteVariable("X1");
        variables.add(x1);

        ContinuousVariable x2 = new ContinuousVariable("X2");
        variables.add(x2);

        DataSet dataSet = new ColtDataSet(5, variables);

        System.out.println(dataSet);
    }

    public static void testRemoveColumn() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new ColtDataSet(rows, variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        System.out.println(dataSet);

        int[] _cols = new int[2];
        _cols[0] = 1;
        _cols[1] = 2;

        dataSet.removeCols(_cols);

        System.out.println(dataSet);
    }


    public static void testRemoveRows() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new ColtDataSet(rows, variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        System.out.println(dataSet);

        int[] _rows = new int[2];
        _rows[0] = 1;
        _rows[1] = 2;

        dataSet.removeRows(_rows);

        System.out.println(dataSet);
    }

    public static void testRowSubset() {
        int rows = 10;
        int cols = 5;

        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < cols; i++) {
            variables.add(new ContinuousVariable("X" + i));
        }

        DataSet dataSet = new ColtDataSet(rows, variables);
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                dataSet.setDouble(i, j, randomUtil.nextDouble());
            }
        }

        System.out.println(dataSet);

        DataSet _dataSet = dataSet.subsetRows(new int[]{0, 1, 2});

        System.out.println(_dataSet);
    }

    public static void testPermuteRows() {
        DataSet dataSet = new ColtDataSet(0, Collections.EMPTY_LIST);

        DiscreteVariable x1 = new DiscreteVariable("X1");
        dataSet.addVariable(x1);
        dataSet.setInt(0, 0, 0);
        dataSet.setInt(1, 0, 2);
        dataSet.setInt(2, 0, 1);

        DiscreteVariable x2 = new DiscreteVariable("X2");
        dataSet.addVariable(x2);
        dataSet.setInt(0, 1, 0);
        dataSet.setInt(1, 1, 2);
        dataSet.setInt(2, 1, 1);

        System.out.println(dataSet);

        dataSet.permuteRows();

        System.out.println(dataSet);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestColtDataSet.class);
    }
}


