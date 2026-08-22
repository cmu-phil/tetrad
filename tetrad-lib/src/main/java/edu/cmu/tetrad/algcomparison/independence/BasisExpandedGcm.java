///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.search.test.IndTestBasisExpandedGcm;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for BE-GCM (Basis-Expanded Generalised Covariance Measure Test).
 * <p>
 * Tests X _||_ Y | Z by residualizing the basis-expanded blocks of X and Y on the basis expansion of Z with
 * ridge-stabilized OLS and referring the maximum studentized residual cross-covariance over the basis-pair grid to a
 * Rademacher multiplier bootstrap null (Sidak bound if the number of multiplier samples is 0). Doubly robust in the
 * GCM sense: the Z-regressions' complexity does not enter the null distribution, only the product of their errors.
 * Heteroskedasticity-robust via self-normalization. Assumes independent rows.
 * <p>
 * The basis type is fixed to Legendre, matching BF-LRT. With adaptive basis selection enabled, uninformative
 * higher-order basis columns are pruned by the BIC-crossing screen, controlling the grid size as the truncation limit
 * grows.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "BE-GCM (Basis-Expanded GCM Test)",
        command = "be-gcm-test",
        dataType = DataType.Mixed
)
@Mixed
@General
@Experimental
public class BasisExpandedGcm implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Legendre basis, matching BF-LRT; see StatUtils.basisFunctionValue.
     */
    private static final int LEGENDRE_BASIS_TYPE = 1;

    /**
     * Initializes a new instance of the BasisExpandedGcm wrapper.
     */
    public BasisExpandedGcm() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "BE-GCM (Basis-Expanded GCM Test)");
        IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(
                SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT),
                parameters.getInt(Params.GCM_Z_TRUNCATION_LIMIT),
                LEGENDRE_BASIS_TYPE,
                parameters.getDouble(Params.SINGULARITY_LAMBDA),
                parameters.getBoolean(Params.ADAPTIVE_BASIS_SELECTION));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setNumMultiplierSamples(parameters.getInt(Params.GCM_MULTIPLIER_SAMPLES));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BE-GCM";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.TRUNCATION_LIMIT);
        parameters.add(Params.ADAPTIVE_BASIS_SELECTION);
        parameters.add(Params.SINGULARITY_LAMBDA);
        parameters.add(Params.GCM_Z_TRUNCATION_LIMIT);
        parameters.add(Params.GCM_MULTIPLIER_SAMPLES);
        parameters.add(Params.MISSING_DATA_POLICY);
        return parameters;
    }
}
