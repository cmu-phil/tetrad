///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.calculator.Transformation;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>CalculatorWrapper class.</p>
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class CalculatorWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;


    /**
     * Constructs the <code>DiscretizationWrapper</code> by discretizing the select
     * <code>DataModel</code>.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CalculatorWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

        DataModelList list = new DataModelList();

        DataModelList originals = data.getDataModelList();

        for (DataModel model : originals) {
            DataSet copy = CalculatorWrapper.copy((DataSet) model);

            List<String> equations = new ArrayList<>();

            int size = ((List<String>) params.get("equations", null)).size();
            String[] displayEquations = ((List<String>) params.get("equations", null)).toArray(new String[size]);

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
                    Transformation.transform(copy, equations.toArray(new String[0]));
                } catch (ParseException e) {
                    throw new IllegalStateException("Was given unparsable expressions.");
                }
            }
            list.add(copy);
        }

        setDataModel(list);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Result data from a calculator operation.", getDataModelList());

    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //=============================== Private Methods =========================//


    private static DataSet copy(DataSet data) {
        DataSet copy = new BoxDataSet(new DoubleDataBox(data.getNumRows(), data.getVariables().size()), data.getVariables());
        int cols = data.getNumColumns();
        int rows = data.getNumRows();
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                copy.setDouble(row, col, data.getDouble(row, col));
            }
        }
        return copy;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }


}




