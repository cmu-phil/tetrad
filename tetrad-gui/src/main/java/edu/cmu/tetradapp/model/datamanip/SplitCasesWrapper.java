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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class SplitCasesWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the wrapper given some data and the params.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SplitCasesWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }
        DataSet originalData = (DataSet) data.getSelectedDataModel();
        DataModel model = SplitCasesWrapper.createSplits(originalData, params);
        this.setDataModel(model);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("One split of the parent data.", getDataModelList());

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

    /**
     * @return the splitNames selected by the editor.
     */
    private static DataModel createSplits(DataSet dataSet, Parameters params) {
        List<Integer> indices = new ArrayList<>(dataSet.getNumRows());
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            indices.add(i);
        }

        if (params.getBoolean("dataShuffled", true)) {
            RandomUtil.shuffle(indices);
        }

        SplitCasesSpec spec = (SplitCasesSpec) params.get("splitCasesSpec", null);
        int numSplits = params.getInt("numSplits", 2);
        int sampleSize = spec.getSampleSize();
        int[] breakpoints = spec.getBreakpoints();
        List<String> splitNames = spec.getSplitNames();

        int[] _breakpoints = new int[breakpoints.length + 2];
        _breakpoints[0] = 0;
        _breakpoints[_breakpoints.length - 1] = sampleSize;
        System.arraycopy(breakpoints, 0, _breakpoints, 1, breakpoints.length);

        DataModelList list = new DataModelList();
        int ncols = dataSet.getNumColumns();
        for (int n = 0; n < numSplits; n++) {
            int _sampleSize = _breakpoints[n + 1] - _breakpoints[n];

            DataSet _data =
                    new BoxDataSet(new VerticalDoubleDataBox(_sampleSize, dataSet.getVariables().size()), dataSet.getVariables());
            _data.setName(splitNames.get(n));

            for (int i = 0; i < _sampleSize; i++) {
                int oldCase = indices.get(i + _breakpoints[n]);

                for (int j = 0; j < ncols; j++) {
                    _data.setObject(i, j, dataSet.getObject(oldCase, j));
                }
            }

            list.add(_data);
        }

        return list;
    }


}




