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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI model for the permute rows function in RectangularDataSet.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class ShiftDataWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the wrapper given some data and the params.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ShiftDataWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList dataModelList = data.getDataModelList();

        final int rows = -1;
        final int cols = -1;

        for (DataModel model : dataModelList) {
            if (!(model instanceof DataSet dataSet)) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "For the shift search, all of the data in the data box must be in the form of data sets.");
                return;
            }

        }

        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel dataModel : dataModelList) {
            dataSets.add((DataSet) dataModel);
        }

        int[] backshifts = (int[]) params.get("shifts", null);

        if (backshifts.length < dataSets.get(0).getNumColumns()) {
            return;
        }

        List<DataSet> backshiftedDataSets = shiftDataSets(dataSets, backshifts);

        DataModelList _list = new DataModelList();

        _list.addAll(backshiftedDataSets);

        this.setDataModel(_list);
        this.setSourceGraph(data.getSourceGraph());
        params.set("shifts", backshifts);

        LogDataUtils.logDataModelList("Data in which variables have been shifted in time.", getDataModelList());
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

    private List<DataSet> shiftDataSets(List<DataSet> dataSets, int[] shifts) {
        List<DataSet> shiftedDataSets = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            shiftedDataSets.add(TsUtils.createShiftedData(dataSet, shifts));
        }
        return shiftedDataSets;
    }
}



