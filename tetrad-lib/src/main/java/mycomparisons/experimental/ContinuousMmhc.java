package mycomparisons.experimental;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.mb.Mmhc;
import edu.cmu.tetrad.util.Experimental;

import java.util.Collections;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousMmhc implements Algorithm, Experimental {
    public Graph search(DataSet dataSet, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("alpha"));
        IndependenceTest test = new IndTestScore(score);
        Mmhc pc = new Mmhc(test, dataSet);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "MMHC using the SEM BIC score. (Not optimized.)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return Collections.singletonList("alpha");
    }
}
