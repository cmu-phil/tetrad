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
public class RemoveSelectedVariablesWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for RemoveSelectedVariablesWrapper.</p>
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveSelectedVariablesWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The givan data must not be null");
        }

        DataModel model = data.getSelectedDataModel();

        if (model instanceof DataSet) {
            this.setDataModel(RemoveSelectedVariablesWrapper.createRectangularModel(((DataSet) model).copy()));
        } else if (model instanceof ICovarianceMatrix) {
            this.setDataModel(RemoveSelectedVariablesWrapper.createCovarianceModel((ICovarianceMatrix) model));
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

    private static DataModel createRectangularModel(DataSet data) {
        for (int i = data.getNumColumns() - 1; i >= 0; i--) {
            if (data.isSelected(data.getVariable(i))) {
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
            if (!data.isSelected(node)) {
                ++index;
                selectedIndices[index] = i;
                nodeNames[index] = node.getName();
            }
        }

        Matrix matrix = data.getMatrix();

        Matrix newMatrix = matrix.getSelection(
                selectedIndices, selectedIndices).copy();


        return new CovarianceMatrix(DataUtils.createContinuousVariables(nodeNames), newMatrix, data.getSampleSize());
    }


}



