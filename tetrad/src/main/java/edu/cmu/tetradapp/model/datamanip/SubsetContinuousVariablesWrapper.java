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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Add description
 *
 * @author Tyler Gibson
 */
public class SubsetContinuousVariablesWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;


    public SubsetContinuousVariablesWrapper(DataWrapper data) {
        if (data == null) {
            throw new NullPointerException("The givan data must not be null");
        }
        DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The given dataset must be tabular");
        }
        this.setDataModel(createModel((DataSet) model));
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data restricted to continuous variables only.", getDataModelList());

    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new SubsetContinuousVariablesWrapper(new DataWrapper(DataUtils.continuousSerializableInstance()));
    }

    //=========================== Private Methods =================================//


    private static DataModel createModel(DataSet data) {
//        for (int i = data.getNumColumns() -1; i >= 0; i--) {
//            if (!(data.getVariable(i) instanceof ContinuousVariable)) {
//                data.removeColumn(i);
//            }
//        }
//        return data;

        List<Node> variables = data.getVariables();

        int n = 0;
        for (Node variable : variables) {
            if (variable instanceof ContinuousVariable) {
                n++;
            }
        }
        if (n == 0) {
            return new ColtDataSet(0, new ArrayList<Node>());
        }

        int[] indices = new int[n];
        int m = 0;
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i) instanceof ContinuousVariable) {
                indices[m++] = i;
            }
        }

        return data.subsetColumns(indices);
    }


}




