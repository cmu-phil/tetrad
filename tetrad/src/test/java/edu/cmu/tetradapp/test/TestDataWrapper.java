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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.DataWrapper;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the basic functionality of the DataWrapper.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TestDataWrapper extends TestCase {

    DataWrapper dataWrapper;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDataWrapper(String name) {
        super(name);
    }

    public void testConstruction() {

        this.dataWrapper = new DataWrapper();

        assertNotNull(dataWrapper);
    }

    public void testDataModelList() {
        DataModelList modelList = new DataModelList();

        List<Node> variables1 = new ArrayList<Node>();

        for (int i = 0; i < 10; i++) {
            variables1.add(new ContinuousVariable("X" + i));
        }

        List<Node> variables2 = new ArrayList<Node>();

        for (int i = 0; i < 10; i++) {
            variables2.add(new ContinuousVariable("X" + i));
        }

        DataSet first = new ColtDataSet(10, variables1);
        first.setName("first");

        DataSet second = new ColtDataSet(10, variables2);
        second.setName("second");

        modelList.add(first);
        modelList.add(second);

        System.out.println(modelList.contains(first));
        System.out.println(modelList.contains(second));

        modelList.setSelectedModel(second);

        try {
            DataModelList modelList2 = (DataModelList) new MarshalledObject(modelList).get();
            System.out.println(modelList2.getSelectedModel().getName());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
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
        return new TestSuite(TestDataWrapper.class);
    }
}





