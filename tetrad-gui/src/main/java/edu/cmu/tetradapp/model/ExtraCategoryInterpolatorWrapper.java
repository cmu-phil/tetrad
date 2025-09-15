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

import edu.cmu.tetrad.bayes.ExtraCategoryInterpolator;
import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Wraps a data model so that a random sample will automatically be drawn on construction from a BayesIm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ExtraCategoryInterpolatorWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for ExtraCategoryInterpolatorWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ExtraCategoryInterpolatorWrapper(DataWrapper wrapper, Parameters params) {
        DataFilter interpolator = new ExtraCategoryInterpolator();
        DataSet columnDataModel =
                (DataSet) wrapper.getSelectedDataModel();
        setDataModel(interpolator.filter(columnDataModel));
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Replacement of missing values in parent data with extra categories.", getDataModelList());

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






