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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.io.Serial;

/**
 * Creates a subset of a given dataset using the DataSubsetEditor (a subset of the variables, plus a subset
 * of the rows). Allows for bootstrap sampling and subsetting both.
 */
public class DataSubsetModel extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    public DataSubsetModel(DataWrapper data, Parameters params) {
        if (data == null) throw new NullPointerException("The given data must not be null");

        DataModelList dataSets = data.getDataModelList();
        if (dataSets.size() != 1) {
            throw new IllegalArgumentException("For causal unmixing, you need exactly one data set.");
        }

        Object dataSubsetParamsEditorSubset = params.get("dataSubsetParamsEditorSubset");

        if (!(dataSubsetParamsEditorSubset instanceof DataSet subset)) {
            throw new IllegalArgumentException("The data subset parameter must be a DataSet");
        }

        new WatchedProcess() {
            @Override
            public void watch() {
                DataModelList out = new DataModelList();
                out.add(subset);
                out.getFirst().setName("Data Subset");
                setDataModel(out);
            }
        };
    }
}
