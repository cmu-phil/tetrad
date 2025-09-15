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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * Add description
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class SubsetSelectedVariablesWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for SubsetSelectedVariablesWrapper.</p>
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SubsetSelectedVariablesWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The givan data must not be null");
        }

        DataModel model = data.getSelectedDataModel();

        if (model instanceof DataSet) {
            this.setDataModel(SubsetSelectedVariablesWrapper.createRectangularModel(((DataSet) model).copy()));
        } else if (model instanceof ICovarianceMatrix) {
            this.setDataModel(SubsetSelectedVariablesWrapper.createCovarianceModel((ICovarianceMatrix) model));
        } else {
            throw new IllegalArgumentException("Expecting a rectangular data " +
                                               "set or a covariance matrix.");
        }

        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data restricted to selected variables only.", getDataModelList());

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

    //=========================== Private Methods =================================//


    private static DataModel createRectangularModel(DataSet data) {
        for (int i = data.getNumColumns() - 1; i >= 0; i--) {
            if (!data.isSelected(data.getVariable(i))) {
                data.removeColumn(i);
            }
        }
        return data;
    }

    private static DataModel createCovarianceModel(ICovarianceMatrix data) {
        int numSelected = 0;

        for (Node node : data.getVariables()) {
            if (data.isSelected(node)) {
                numSelected++;
            }
        }

        int[] selectedIndices = new int[numSelected];
        String[] nodeNames = new String[numSelected];
        int index = -1;

        for (int i = 0; i < data.getVariables().size(); i++) {
            Node node = data.getVariables().get(i);
            if (data.isSelected(node)) {
                ++index;
                selectedIndices[index] = i;
                nodeNames[index] = node.getName();
            }
        }

        Matrix matrix = data.getMatrix();

        Matrix newMatrix = matrix.view(selectedIndices, selectedIndices).mat().copy();


        return new CovarianceMatrix(DataUtils.createContinuousVariables(nodeNames), newMatrix, matrix.getNumRows());
    }


}




