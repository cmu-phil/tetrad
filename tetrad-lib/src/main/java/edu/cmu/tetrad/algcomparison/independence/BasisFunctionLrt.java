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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for BF-LRT (Basis Function Likelihood Ratio Test).
 * <p>
 * Changes from the pre-2026-8 implementation: this wrapper now delegates to
 * {@link IndTestBasisFunctionBlocks}, which tests block-level conditional independence using the
 * Wilks-lambda / Bartlett chi-square statistic over the basis-embedded blocks, with degrees of
 * freedom |X-block| x |Y-block| (rank-aware). The previous implementation
 * ({@link edu.cmu.tetrad.search.test.IndTestBasisFunctionLrt}, now deprecated) computed a
 * trace-averaged residual-variance ratio referred to chi-square with |Y-block| degrees of freedom;
 * that statistic is not a likelihood ratio for the block regression and its p-values are not
 * uniformly distributed under the null, which distorts consumers of the p-values such as PC-Max
 * sepset selection and the Markov Checker. The Wilks form is also symmetric in X and Y, whereas
 * the previous statistic was not.
 * <p>
 * The basis type is fixed to Legendre (basisType = 1), matching the published description of
 * BF-LRT; use the BF-Blocks-Test wrapper to select other basis families. The SINGULARITY_LAMBDA
 * parameter of the previous implementation no longer applies: the Wilks path handles
 * near-singularity via rank-aware whitening with an internal ridge. This wrapper now supports
 * EFFECTIVE_SAMPLE_SIZE.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "BF-LRT (Basis Function Likelihood Ratio Test)",
        command = "bf-lr-test",
        dataType = DataType.Mixed
)
@Mixed
@General
public class BasisFunctionLrt implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Legendre basis, as described for BF-LRT; see StatUtils.basisFunctionValue.
     */
    private static final int LEGENDRE_BASIS_TYPE = 1;

    /**
     * Initializes a new instance of the BasisFunctionLrt wrapper.
     */
    public BasisFunctionLrt() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "BF-LRT (Basis Function Likelihood Ratio Test)");
        IndTestBasisFunctionBlocks test = new IndTestBasisFunctionBlocks(
                SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT),
                LEGENDRE_BASIS_TYPE);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BF-LRT";
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
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.MISSING_DATA_POLICY);
        return parameters;
    }
}
