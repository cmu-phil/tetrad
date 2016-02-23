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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 */
public class ConcatenateDatasetsWrapper extends DataWrapper {
       static final long serialVersionUID = 23L;

    public ConcatenateDatasetsWrapper(DataWrapper data1) {
        construct(data1);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2) {
        construct(data1, data2);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3) {
        construct(data1, data2, data3);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3,
                                DataWrapper data4) {
        construct(data1, data2, data3, data4);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3,
                                DataWrapper data4, DataWrapper data5) {
        construct(data1, data2, data3, data4, data5);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3,
                                DataWrapper data4, DataWrapper data5, DataWrapper data6) {
        construct(data1, data2, data3, data4, data5, data6);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3,
                                DataWrapper data4, DataWrapper data5, DataWrapper data6,
                                DataWrapper data7) {
        construct(data1, data2, data3, data4, data5, data6, data7);
    }

    public ConcatenateDatasetsWrapper(DataWrapper data1, DataWrapper data2, DataWrapper data3,
                                DataWrapper data4, DataWrapper data5, DataWrapper data6,
                                DataWrapper data7, DataWrapper data8) {
        construct(data1, data2, data3, data4, data5, data6, data7, data8);
    }

    private void construct(DataWrapper...dataWrappers) {
        for (DataWrapper wrapper : dataWrappers) {
            if (wrapper == null) {
                throw new NullPointerException("The given data must not be null");
            }
        }

        List<DataSet> dataSets = new ArrayList<DataSet>();

        for (DataWrapper wrapper : dataWrappers) {
            for (DataModel model : wrapper.getDataModelList()) {
                DataSet dataSet = (DataSet) model;
                dataSets.add(dataSet);
            }
        }

        DataSet concatenated = DataUtils.concatenate(dataSets);

        this.setDataModel(concatenated);

        LogDataUtils.logDataModelList("Parent data in which constant columns have been removed.", getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static ConcatenateDatasetsWrapper serializableInstance() {
        return new ConcatenateDatasetsWrapper(DataWrapper.serializableInstance(),
                DataWrapper.serializableInstance());
    }





}

