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
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class CopyAllDatasetsWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for CopyAllDatasetsWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CopyAllDatasetsWrapper(DataWrapper wrapper, Parameters params) {
        LogDataUtils.logDataModelList("Parent data in which constant columns have been removed.", getDataModelList());


        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            this.setDataModel(model);
            outList.add(model);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());

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


}




