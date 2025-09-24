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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.List;

/**
 * Add description
 *
 * @author Tyler Gibson
 */
class SubsetForSelectionVariables implements DataFilter {


    /**
     * {@inheritDoc}
     */
    public DataSet filter(DataSet data) {
        List<Node> variables = data.getVariables();
        int numRows = 0;

        ROWS:
        for (int row = 0; row < data.getNumRows(); row++) {
            for (int col = 0; col < data.getNumColumns(); col++) {
                Node variable = data.getVariable(col);

                if (variable.getNodeType() == NodeType.SELECTION) {
                    if (data.getDouble(row, col) < 0) {
                        continue ROWS;
                    }
                }
            }

            numRows++;
        }

        DataSet newDataSet = new BoxDataSet(new DoubleDataBox(numRows, variables.size()), variables);
        int newRow = 0;

        ROWS:
        for (int row = 0; row < data.getNumRows(); row++) {
            for (int col = 0; col < data.getNumColumns(); col++) {
                Node variable = data.getVariable(col);
//                if (((Variable) variable).isMissingValue(data.getObject(row, col))) {
//                    continue ROWS;
//                }

                if (variable.getNodeType() == NodeType.SELECTION) {
                    if (data.getDouble(row, col) < 0) {
                        continue ROWS;
                    }
                }
            }

            for (int col = 0; col < data.getNumColumns(); col++) {
                newDataSet.setObject(newRow, col, data.getObject(row, col));
            }

            newRow++;
        }


        for (int col = 0; col < newDataSet.getNumColumns(); col++) {
            Node variable = newDataSet.getVariable(col);
            if (variable.getNodeType() == NodeType.SELECTION) {
                newDataSet.removeColumn(variable);
                variables.remove(variable);
            }
        }

        return newDataSet;
    }
}




