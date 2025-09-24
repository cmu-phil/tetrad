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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.List;


/**
 * Wraps a data model so that a random sample will automatically be drawn on construction from a SemIm. Measured
 * variables only.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ImpliedCovarianceDataWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The SEM IM.
     */
    private SemIm semIm;

    //==============================CONSTRUCTORS=============================//

    /**
     * <p>Constructor for ImpliedCovarianceDataWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ImpliedCovarianceDataWrapper(SemEstimatorWrapper wrapper, Parameters params) {
        SemEstimator semEstimator = wrapper.getSemEstimator();
        SemIm semIm1 = semEstimator.getEstimatedSem();

        if (semIm1 != null) {

            Matrix matrix2D = semIm1.getImplCovarMeas();
            int sampleSize = semIm1.getSampleSize();
            List<Node> variables = wrapper.getSemEstimator().getEstimatedSem().getSemPm().getMeasuredNodes();
            CovarianceMatrix cov = new CovarianceMatrix(variables, matrix2D, sampleSize);
            setDataModel(cov);
            setSourceGraph(wrapper.getSemEstimator().getEstimatedSem().getSemPm().getGraph());
            this.semIm = wrapper.getEstimatedSemIm();
        }

        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
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
     * <p>Getter for the field <code>semIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getSemIm() {
        return this.semIm;
    }
}


