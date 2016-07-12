package edu.cmu.tetrad.algcomparison.continuous.pag;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousfciMaxFz implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestFisherZ(dataSet, parameters.getDouble("alpha"));
        FciMax pc = new FciMax(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "RFCI using the Fisher Z test.";
    }


    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
