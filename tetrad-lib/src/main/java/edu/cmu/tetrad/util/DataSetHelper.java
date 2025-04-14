package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

public class DataSetHelper {
    public static DataSet fromR(List<Node> vars, int nrows, double[][] cont, int[][] disc) {
        MixedDataBox box = new MixedDataBox(vars, nrows, cont, disc);
        return new BoxDataSet(box, vars);
    }
}

