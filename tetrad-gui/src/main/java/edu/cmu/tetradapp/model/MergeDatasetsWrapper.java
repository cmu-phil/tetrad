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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class MergeDatasetsWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for MergeDatasetsWrapper.</p>
     *
     * @param data   an array of {@link edu.cmu.tetradapp.model.DataWrapper} objects
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MergeDatasetsWrapper(DataWrapper[] data, Parameters params) {
        construct(data);
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

    private void construct(DataWrapper... dataWrappers) {
        for (DataWrapper wrapper : dataWrappers) {
            if (wrapper == null) {
                throw new NullPointerException("The given data must not be null");
            }
        }

        DataModelList merged = new DataModelList();

        for (DataWrapper wrapper : dataWrappers) {
            merged.addAll(wrapper.getDataModelList());
        }

        this.setDataModel(merged);

        LogDataUtils.logDataModelList("Parent data in which constant columns have been removed.", getDataModelList());
    }


}




