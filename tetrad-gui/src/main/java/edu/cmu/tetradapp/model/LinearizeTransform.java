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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Applies the linearizing ("un-warping") transform to a continuous dataset: each
 * variable is passed through a monotone Yeo-Johnson transform chosen to make its
 * relations to the other variables as linear as possible. Intended for data suspected
 * to follow a post-nonlinear model (a linear system viewed through per-variable
 * monotone distortions such as sensor saturation). Unlike the nonparanormal
 * transform, this preserves marginal skewness to the extent the post-nonlinear model
 * holds, so skew-based orientation (FASK) remains applicable to the transformed data.
 * Fitted lambdas and per-variable before/after nonlinearity are written to the log.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.data.Linearizer
 */
public class LinearizeTransform extends DataWrapper {

    private static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    /**
     * <p>Constructor for LinearizeTransform.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public LinearizeTransform(DataWrapper wrapper, Parameters params) {
        DataModel dataModel = wrapper.getSelectedDataModel();

        if (dataModel instanceof ICovarianceMatrix) {
            throw new IllegalArgumentException("Data model must be a tabular continuous data set, not a covariance matrix.");
        }

        DataSet linearized = DataTransforms.getLinearizedTransformed((DataSet) dataModel);
        linearized.setKnowledge(dataModel.getKnowledge().copy());

        setDataModel(linearized);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Linearizing (un-warp) transform of parent data.", getDataModelList());
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
