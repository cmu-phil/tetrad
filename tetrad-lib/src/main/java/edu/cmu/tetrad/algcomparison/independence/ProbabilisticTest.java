/**
 * 
 */
package edu.cmu.tetrad.algcomparison.independence;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

/**
 * Dec 17, 2018 3:44:46 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
@TestOfIndependence(
        name = "Probabilistic Test",
        command = "prob",
        dataType = DataType.Discrete
)
public class ProbabilisticTest implements IndependenceWrapper {

	private static final long serialVersionUID = 23L;
	
	@Override
	public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
		IndTestProbabilistic test = new IndTestProbabilistic(DataUtils.getDiscreteDataSet(dataSet));
		test.setThreshold(parameters.getBoolean("randomized"));
		return test;
	}

	@Override
	public String getDescription() {
		return "Probabilistic Conditional Independence Test";
	}

	@Override
	public DataType getDataType() {
		return DataType.Discrete;
	}

	@Override
	public List<String> getParameters() {
		List<String> parameters = new ArrayList<>();
        parameters.add("randomized");
        return parameters;
	}

}
