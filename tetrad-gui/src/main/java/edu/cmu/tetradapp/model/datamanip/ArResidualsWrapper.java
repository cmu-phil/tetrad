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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

/**
 * @author Tyler
 */
public class ArResidualsWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     * @param params - The parameters.
     */
    public ArResidualsWrapper(DataWrapper data, ArResidualsParams params) {
        DataModelList list = data.getDataModelList();
        DataModelList convertedList = new DataModelList();
        DataModelList dataSets = data.getDataModelList();

        for (int i = 0; i < list.size(); i++) {
            DataModel selectedModel = dataSets.get(i);

            if (!(selectedModel instanceof DataSet)) {
                continue;
            }

            DataModel model = TimeSeriesUtils.ar2((DataSet) selectedModel, params.getNumTimeLags());
            model.setKnowledge(selectedModel.getKnowledge());
            convertedList.add(model);
            setSourceGraph(data.getSourceGraph());
        }

        setDataModelList(convertedList);


//        DataModel model = data.getSelectedDataModel();
//        if (!(model instanceof DataSet)) {
//            throw new IllegalArgumentException("The data model must be a rectangular dataset");
//        }
//        model = TimeSeriesUtils.ar2((DataSet) model, params.getNumOfTimeLags());
//        this.setDataModel(model);
//        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Result data from an AR residual calculation.", getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new ArResidualsWrapper(DataWrapper.serializableInstance(), ArResidualsParams.serializableInstance());
    }
}



