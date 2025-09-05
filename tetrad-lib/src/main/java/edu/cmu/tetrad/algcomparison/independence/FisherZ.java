package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Fisher Z Test",
        command = "fisher-z-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class FisherZ implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public FisherZ() {
    }

    /**
     * Gets an independence test based on the given data model and parameters.
     *
     * @param dataModel  The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object.
     * @throws IllegalArgumentException if the dataModel is not a dataset or a covariance matrix.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        IndTestFisherZ test;

        if (dataModel instanceof ICovarianceMatrix) {
            test = new IndTestFisherZ((ICovarianceMatrix) dataModel, alpha);
        } else if (dataModel instanceof DataSet) {
            test = new IndTestFisherZ((DataSet) dataModel, alpha);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        test.setLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));
        test.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        return test;
    }

    /**
     * Retrieves the description of the Fisher Z test.
     *
     * @return The description of the Fisher Z test.
     */
    @Override
    public String getDescription() {
        return "Fisher Z test";
    }

    /**
     * Retrieves the data type of the independence test.
     *
     * @return The data type of the independence test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters of the Fisher Z test.
     *
     * @return A list of strings representing the parameters of the Fisher Z test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.SINGULARITY_LAMBDA);
        params.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return params;
    }
}
