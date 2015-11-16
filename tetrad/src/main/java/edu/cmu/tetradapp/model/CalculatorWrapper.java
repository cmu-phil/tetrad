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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.calculator.CalculatorParams;
import edu.cmu.tetrad.calculator.Transformation;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tyler
 */
public class CalculatorWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;


    /**
     * Constructs the <code>DiscretizationWrapper</code> by discretizing the select
     * <code>DataModel</code>.
     *
     * @param data
     * @param params
     */
    public CalculatorWrapper(DataWrapper data, CalculatorParams params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

        DataModelList list = new DataModelList();

        DataModelList originals = data.getDataModelList();

        for (DataModel model : originals) {
            DataSet copy = copy((DataSet) model);

            List<String> equations = new ArrayList<String>();

            int size = params.getEquations().size();
            String[] displayEquations = params.getEquations().toArray(new String[size]);

            for (String equation : displayEquations) {
                if (equation.contains("$")) {
                    for (Node node : copy.getVariables()) {
                        equations.add(equation.replace("$", node.getName()));
                    }
                } else {
                    equations.add(equation);
                }
            }

            if (!equations.isEmpty()) {
                try {
                    Transformation.transform(copy, equations.toArray(new String[equations.size()]));
                } catch (ParseException e) {
                    throw new IllegalStateException("Was given unparsable expressions.");
                }
                list.add(copy);
            } else {
                list.add(copy);
            }
        }

        setDataModel(list);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Result data from a calculator operation.", getDataModelList());

    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static CalculatorWrapper serializableInstance() {
        return new CalculatorWrapper(DataWrapper.serializableInstance(),
                CalculatorParams.serializableInstance());
    }

    //=============================== Private Methods =========================//


    private static DataSet copy(DataSet data) {
        if (data instanceof ColtDataSet) {
            return new ColtDataSet((ColtDataSet) data);
        }
        DataSet copy = new ColtDataSet(data.getNumRows(), data.getVariables());
        int cols = data.getNumColumns();
        int rows = data.getNumRows();
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                copy.setDouble(row, col, data.getDouble(row, col));
            }
        }
        return copy;
    }


    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings({"MethodMayBeStatic"})
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }


}




