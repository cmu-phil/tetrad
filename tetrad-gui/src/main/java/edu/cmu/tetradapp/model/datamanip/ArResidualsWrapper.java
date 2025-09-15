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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * <p>ArResidualsWrapper class.</p>
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class ArResidualsWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     * @param params - The parameters.
     */
    public ArResidualsWrapper(DataWrapper data, Parameters params) {
        DataModelList list = data.getDataModelList();
        DataModelList convertedList = new DataModelList();
        DataModelList dataSets = data.getDataModelList();

        for (int i = 0; i < list.size(); i++) {
            DataModel selectedModel = dataSets.get(i);

            if (!(selectedModel instanceof DataSet)) {
                continue;
            }

            DataModel model = TsUtils.ar2((DataSet) selectedModel, params.getInt("numTimeLags", 1));
            model.setKnowledge(selectedModel.getKnowledge());
            convertedList.add(model);
            setSourceGraph(data.getSourceGraph());
        }

        setDataModelList(convertedList);


        LogDataUtils.logDataModelList("Result data from an AR residual calculation.", getDataModelList());

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




