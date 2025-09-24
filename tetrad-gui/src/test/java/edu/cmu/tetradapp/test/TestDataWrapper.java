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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.model.DataWrapper;
import org.junit.Test;

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the basic functionality of the DataWrapper.
 *
 * @author josephramsey
 */
public class TestDataWrapper {

    DataWrapper dataWrapper;

    @Test
    public void testConstruction() {

        this.dataWrapper = new DataWrapper(new Parameters());

        assertNotNull(this.dataWrapper);
    }

    @Test
    public void testDataModelList() {
        DataModelList modelList = new DataModelList();

        List<Node> variables1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            variables1.add(new ContinuousVariable("X" + i));
        }

        List<Node> variables2 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            variables2.add(new ContinuousVariable("X" + i));
        }

        DataSet first = new BoxDataSet(new VerticalDoubleDataBox(10, variables1.size()), variables1);
        first.setName("first");

        DataSet second = new BoxDataSet(new VerticalDoubleDataBox(10, variables2.size()), variables2);
        second.setName("second");

        modelList.add(first);
        modelList.add(second);

        assertTrue(modelList.contains(first));
        assertTrue(modelList.contains(second));

        modelList.setSelectedModel(second);

        try {
            DataModelList modelList2 = new MarshalledObject<>(modelList).get();
            assertEquals("second", modelList2.getSelectedModel().getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDoubleDataBox() {
        Graph graph = RandomGraph.randomGraph(5, 0, 10, 100, 100, 100, false);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(200, false);

        DataBox box = new DoubleDataBox(dataSet.getDoubleData().toArray());

        List<Node> vars = dataSet.getVariables();
        List<Node> shuffled = new ArrayList<>(vars);
        RandomUtil.shuffle(shuffled);

        int[] rows = new int[dataSet.getNumRows()];
        for (int i = 0; i < rows.length; i++) rows[i] = i;

        int[] _shuffled = new int[dataSet.getNumColumns()];
        for (int j = 0; j < vars.size(); j++) _shuffled[j] = shuffled.indexOf(vars.get(j));

        int[] inverse = new int[dataSet.getNumColumns()];
        for (int j = 0; j < vars.size(); j++) inverse[j] = vars.indexOf(shuffled.get(j));

        DataBox box2 = box.viewSelection(rows, _shuffled);
        DataBox box3 = box2.viewSelection(rows, inverse);

        DataSet dataSet1 = new BoxDataSet(box3, dataSet.getVariables());

        assert (dataSet.equals(dataSet1));
    }
}






