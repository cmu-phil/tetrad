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
 * @author Tyler
 */
public class BoxCoxWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     * @param params - The parameters.
     */
    public BoxCoxWrapper(DataWrapper data, BoxCoxParams params) {
        DataModelList list = data.getDataModelList();
        DataModelList convertedList = new DataModelList();
        DataModelList dataSets = data.getDataModelList();

        for (int i = 0; i < list.size(); i++) {
            DataModel selectedModel = dataSets.get(i);

            if (!(selectedModel instanceof DataSet)) {
                continue;
            }

//            DataModel model = boxCox((DataSet) selectedModel, params.getLambda());
            DataModel model = yeoJohnson((DataSet) selectedModel, params.getLambda());
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

    private DataModel boxCox(DataSet dataSet, double lambda) {
        DataSet transformedData = new ColtDataSet(dataSet.getNumRows(), dataSet.getVariables());

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double y = dataSet.getDouble(i, j);
                double d2;

                if (lambda == 0.0) {
                    d2 = Math.log(y);
                }
                else {
                    d2 = (Math.pow(y, lambda) - 1.0) / lambda;
                }

                transformedData.setDouble(i, j, d2);
            }
        }

        return transformedData;
    }

    private DataModel yeoJohnson(DataSet dataSet, double lambda) {
        DataSet transformedData = new ColtDataSet(dataSet.getNumRows(), dataSet.getVariables());

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double y = dataSet.getDouble(i, j);
                double d2;

                if (lambda != 0 && y >= 0.0) {
                    d2 = (Math.pow(y + 1.0, lambda) - 1.0) / lambda;
                }
                else if (lambda == 0 && y >= 0.0) {
                    d2 = Math.log(y + 1.0);
                }
                else if (lambda != 2 && y < 0.0) {
                    d2 = (Math.pow(1.0 - y, 2.0 - lambda) - 1) / (lambda - 2.0);
                }
                else if (lambda == 2 && y < 0.0) {
                    d2 = -Math.log(1.0 - y);
                }
                else {
                    throw new IllegalStateException("Impossible state.");
                }

                transformedData.setDouble(i, j, d2);
            }
        }

        return transformedData;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new BoxCoxWrapper(DataWrapper.serializableInstance(), BoxCoxParams.serializableInstance());
    }
}


