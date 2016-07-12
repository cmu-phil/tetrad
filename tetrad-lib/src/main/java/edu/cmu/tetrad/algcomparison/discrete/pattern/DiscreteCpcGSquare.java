package edu.cmu.tetrad.algcomparison.discrete.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteCpcGSquare implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestGSquare(dataSet, parameters.getDouble("alpha"));
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC using the G Square test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> usesParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
