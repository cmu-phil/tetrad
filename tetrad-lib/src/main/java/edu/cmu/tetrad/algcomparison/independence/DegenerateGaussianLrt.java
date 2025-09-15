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

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for DG LRT.
 *
 * @author bandrews
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "DG-LRT (Degenerate Gaussian Likelihood Ratio Test)",
        command = "dg-lr-test",
        dataType = DataType.Mixed
)
@Mixed
public class DegenerateGaussianLrt implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public DegenerateGaussianLrt() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt(SimpleDataLoader.getMixedDataSet(dataSet));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));
        test.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Degenerate Gaussian Likelihood Ratio Test";
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
        parameters.add(Params.SINGULARITY_LAMBDA);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return parameters;
    }

}

