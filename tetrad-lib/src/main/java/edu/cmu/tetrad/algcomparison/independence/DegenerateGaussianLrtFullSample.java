package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrtFullSample;
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
        name = "DG-LRT-FS (Degenerate Gaussian Likelihood Ratio Test Full Sample)",
        command = "dg-lr-test-fs",
        dataType = DataType.Mixed
)
@Mixed
public class DegenerateGaussianLrtFullSample implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLRT class.
     */
    public DegenerateGaussianLrtFullSample() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestDegenerateGaussianLrtFullSample test = new IndTestDegenerateGaussianLrtFullSample(
                SimpleDataLoader.getMixedDataSet(dataSet));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Degenerate Gaussian Likelihood Ratio Test Full Sample";
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
        return parameters;
    }

}
