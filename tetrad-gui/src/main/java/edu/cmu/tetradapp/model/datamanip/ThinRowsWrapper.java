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
 * Thins rows by keeping every kth row of each data set, in file order, starting at a given
 * offset. When the input data box contains multiple data sets, each data set is thinned
 * separately, so that (for example) stacked subject or session files can be thinned within
 * subject before concatenation, without rows being kept across file boundaries.
 * <p>
 * This transform is intended as a simple mitigation for serial dependence of rows (as flagged
 * by the SERIAL_DEPENDENCE data audit finding): keeping every kth row of an approximately AR(1)
 * series with lag-1 autocorrelation r reduces the autocorrelation of the retained rows to
 * approximately r^k, at the cost of reducing the sample size by a factor of k. It does not
 * remove serial dependence; it trades sample size for reduced dependence.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ThinRowsWrapper extends DataWrapper {

    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new thinned data wrapper, keeping every kth row of each data set in the
     * given wrapper, starting at the given offset. The interval k is given by the parameter
     * "thinRowsK" (default 2, minimum 1) and the offset by "thinRowsOffset" (default 0); the
     * offset is reduced modulo k, so any nonnegative offset is accepted.
     *
     * @param wrapper the data wrapper to thin.
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object containing "thinRowsK"
     *                and "thinRowsOffset".
     */
    public ThinRowsWrapper(DataWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }

        int k = params.getInt("thinRowsK", 2);
        int offset = params.getInt("thinRowsOffset", 0);

        if (k < 1) {
            throw new IllegalArgumentException("The thinning interval k must be at least 1: " + k);
        }

        if (offset < 0) {
            throw new IllegalArgumentException("The offset must be nonnegative: " + offset);
        }

        offset = offset % k;

        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet data)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            int numRows = data.getNumRows();
            int numKept = numRows > offset ? 1 + (numRows - offset - 1) / k : 0;

            int[] rows = new int[numKept];
            for (int i = 0; i < numKept; i++) {
                rows[i] = offset + i * k;
            }

            DataSet thinned = data.subsetRows(rows);
            thinned.setName(model.getName());
            outList.add(thinned);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data thinned by keeping every kth row of each data set.",
                getDataModelList());
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
