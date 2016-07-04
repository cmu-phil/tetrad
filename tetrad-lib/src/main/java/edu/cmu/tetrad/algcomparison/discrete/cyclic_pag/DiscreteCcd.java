package edu.cmu.tetrad.algcomparison.discrete.cyclic_pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteCcd implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        Score score = new BDeScore(dataSet);
        Ccd2 pc = new Ccd2(score);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "CCD using the BDeu score (incorrect)";
    }


    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }
}
