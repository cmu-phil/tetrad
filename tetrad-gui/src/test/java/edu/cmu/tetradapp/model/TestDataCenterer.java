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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The Center Data box must accept mixed data, centering the continuous columns and copying the discrete ones.
 */
public class TestDataCenterer {

    @Test
    public void testMixedDataIsCenteredWithDiscreteColumnsCopied() {
        DiscreteVariable g = new DiscreteVariable("G", Arrays.asList("a", "b", "c"));
        ContinuousVariable x = new ContinuousVariable("X");
        ContinuousVariable y = new ContinuousVariable("Y");
        List<Node> vars = Arrays.asList(g, x, y);

        DataSet data = new BoxDataSet(new MixedDataBox(vars, 4), vars);
        int[] gv = {0, 2, 1, DiscreteVariable.MISSING_VALUE};
        double[] xv = {1, 2, 3, 6};                  // mean 3
        double[] yv = {10, Double.NaN, 30, 20};      // mean over non-missing 20
        for (int i = 0; i < 4; i++) {
            data.setInt(i, 0, gv[i]);
            data.setDouble(i, 1, xv[i]);
            data.setDouble(i, 2, yv[i]);
        }
        data.setName("mixed");

        DataCenterer centerer = new DataCenterer(new DataWrapper(data), new Parameters());
        DataSet out = (DataSet) centerer.getDataModelList().getFirst();

        assertEquals("mixed", out.getName());
        assertEquals(Arrays.asList("G", "X", "Y"), out.getVariables().stream().map(Node::getName).toList());
        assertTrue(out.getVariable("G") instanceof DiscreteVariable);

        for (int i = 0; i < 4; i++) {
            assertEquals(gv[i], out.getInt(i, 0));
            assertEquals(xv[i] - 3, out.getDouble(i, 1), 1e-12);
            if (Double.isNaN(yv[i])) assertTrue(Double.isNaN(out.getDouble(i, 2)));
            else assertEquals(yv[i] - 20, out.getDouble(i, 2), 1e-12);
        }

        // The parent is untouched.
        assertEquals(1.0, data.getDouble(0, 1), 0);
    }

    @Test
    public void testEachDataSetInAListIsCenteredSeparately() {
        ContinuousVariable x = new ContinuousVariable("X");
        List<Node> vars = List.of(x);

        DataSet a = new BoxDataSet(new VerticalDoubleDataBox(2, 1), vars);
        a.setDouble(0, 0, 100);
        a.setDouble(1, 0, 102);
        a.setName("a");
        DataSet b = new BoxDataSet(new VerticalDoubleDataBox(2, 1), vars);
        b.setDouble(0, 0, -5);
        b.setDouble(1, 0, -7);
        b.setName("b");

        DataModelList list = new DataModelList();
        list.add(a);
        list.add(b);
        DataWrapper wrapper = new DataWrapper();
        wrapper.setDataModel(list);

        DataCenterer centerer = new DataCenterer(wrapper, new Parameters());
        DataModelList out = centerer.getDataModelList();
        assertEquals(2, out.size());
        assertEquals("a", out.get(0).getName());
        assertEquals(-1.0, ((DataSet) out.get(0)).getDouble(0, 0), 1e-12);
        assertEquals(1.0, ((DataSet) out.get(0)).getDouble(1, 0), 1e-12);
        assertEquals("b", out.get(1).getName());
        assertEquals(1.0, ((DataSet) out.get(1)).getDouble(0, 0), 1e-12);
        assertEquals(-1.0, ((DataSet) out.get(1)).getDouble(1, 0), 1e-12);
    }
}
