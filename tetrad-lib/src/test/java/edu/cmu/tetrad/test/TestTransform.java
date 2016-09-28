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

import edu.cmu.tetrad.calculator.Transformation;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Tyler Gibson
 */
public final class TestTransform {

    @Test
    public void testTransformWithNewColumnVariable(){
        List<Node> list = Arrays.asList((Node) new ContinuousVariable("x"),
                new ContinuousVariable("y"));
        DataSet data = new ColtDataSet(1, list);
        data.setDouble(0, 0, 1);
        data.setDouble(1, 0, 1);

        data.setDouble(0, 1, 1);
        data.setDouble(1, 1, 1);

        try {
            String eq = "w = (x + y) * x";
            Transformation.transform(data, eq);
            assertTrue(data.getDouble(0, 2) == 2.0);
            assertTrue(data.getDouble(0, 2) == 2.0);
        } catch(Exception ex){
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test
    public void testSingleTransforms() {
        // build a dataset.
        List<Node> list = Arrays.asList((Node) new ContinuousVariable("x"),
                new ContinuousVariable("y"),
                new ContinuousVariable("z"));
        DataSet data = new ColtDataSet(2, list);
        data.setDouble(0, 0, 2);
        data.setDouble(1, 0, 3);
        data.setDouble(2, 0, 4);

        data.setDouble(0, 1, 1);
        data.setDouble(1, 1, 6);
        data.setDouble(2, 1, 5);

        data.setDouble(0, 2, 8);
        data.setDouble(1, 2, 8);
        data.setDouble(2, 2, 8);

        DataSet copy = new ColtDataSet((ColtDataSet) data);
        // test transforms on it.
        try {
            String eq = "z = (x + y)";
            Transformation.transform(copy, eq);
            assertTrue(copy.getDouble(0, 2) == 3.0);
            assertTrue(copy.getDouble(1, 2) == 9.0);
            assertTrue(copy.getDouble(2, 2) == 9.0);

            copy = new ColtDataSet((ColtDataSet) data);
            eq = "x = x + 3";
            Transformation.transform(copy, eq);
            assertTrue(copy.getDouble(0, 0) == 5.0);
            assertTrue(copy.getDouble(1, 0) == 6.0);
            assertTrue(copy.getDouble(2, 0) == 7.0);


            copy = new ColtDataSet((ColtDataSet) data);
            eq = "x = pow(x, 2) + y + z";
            Transformation.transform(copy, eq);
            assertTrue(copy.getDouble(0, 0) == 13.0);
            assertTrue(copy.getDouble(1, 0) == 23.0);
            assertTrue(copy.getDouble(2, 0) == 29.0);

        } catch (ParseException ex) {
            fail(ex.getMessage());
        }
    }
}




