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
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

/**
 * GUI model for the permute rows function in RectangularDataSet.
 *
 * @author Tyler Gibson
 */
public class FirstDifferencesWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    /**
     * Constructs the wrapper given some data and the params.
     */
    public FirstDifferencesWrapper(DataWrapper wrapper) {
        LogDataUtils.logDataModelList("Parent data in which rows have been randomly permuted.", getDataModelList());

        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            DataSet data = (DataSet) model;

            if (!(data.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + data.getName());
            }

            DataSet firstDiff = new ColtDataSet(data.getNumRows() - 1, data.getVariables());

            for (int j = 0; j < data.getNumColumns(); j++) {
                for (int i = 0; i < data.getNumRows() - 1; i++) {
                    double d2 = data.getDouble(i + 1, j);
                    double d1 = data.getDouble(i, j);
                    firstDiff.setDouble(i, j, d2 - d1);
                }
            }

            outList.add(firstDiff);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());


    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new FirstDifferencesWrapper(DataWrapper.serializableInstance());
    }
}



