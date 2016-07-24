package mycomparisons.experimental;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.WGfci;
import edu.cmu.tetrad.util.Experimental;

import java.util.Collections;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWgfci implements Algorithm, Experimental {
    public Graph search(DataSet dataSet, Parameters parameters) {
        WGfci fgs = new WGfci(dataSet);
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "WGFCI using the SEM BIC score (oriented assuming no latents)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        return Collections.singletonList("penaltyDiscount");
    }
}
