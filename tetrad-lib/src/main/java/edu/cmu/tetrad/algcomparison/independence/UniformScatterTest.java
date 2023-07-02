package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.work_in_progress.IndTestCramerT;
import edu.cmu.tetrad.search.work_in_progress.IndTestUniformScatter;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "Uniform Scatter Test",
        command = "uniform-scatter-test",
        dataType = {DataType.Continuous}
)
@LinearGaussian
public class UniformScatterTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);
        double avg = parameters.getDouble(Params.AVG_DEGREE);
        int numCondCategories = parameters.getInt(Params.GRASP_DEPTH);
        return new IndTestUniformScatter((DataSet) dataSet, alpha, avg, numCondCategories);
    }

    @Override
    public String getDescription() {
        return "Uniform Scatter Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.DEPTH);
        params.add(Params.AVG_DEGREE);
        params.add(Params.GRASP_DEPTH);
        return params;
    }
}
