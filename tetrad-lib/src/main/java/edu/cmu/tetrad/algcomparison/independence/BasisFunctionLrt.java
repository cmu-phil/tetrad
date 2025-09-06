package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionLrt;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

// Using the Blocks BIC score version instead.

/**
 * Wrapper for BF-LRT.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
//@TestOfIndependence(
//        name = "BF-LRT (Basis Function Likelihood Ratio Test)",
//        command = "bf-lr-test",
//        dataType = DataType.Mixed
//)
//@Mixed
@Deprecated(since = "7.9", forRemoval = false)
public class BasisFunctionLrt implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BasisFunctionLrt() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestBasisFunctionLrt test = new IndTestBasisFunctionLrt(SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT), parameters.getDouble(Params.SINGULARITY_LAMBDA));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setDoOneEquationOnly(parameters.getBoolean(Params.DO_ONE_EQUATION_ONLY));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Basis Function Likelihood Ratio Test";
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
        parameters.add(Params.SINGULARITY_LAMBDA);
        parameters.add(Params.DO_ONE_EQUATION_ONLY);
        return parameters;
    }
}
