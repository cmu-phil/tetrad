package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Utility class for creating a {@link DataSet} instance from various inputs.
 */
public class DataSetHelper {

    /**
     * Creates a new {@link DataSet} instance from the specified inputs, including
     * continuous and discrete data along with variable definitions.
     *
     * @param vars the list of variables, represented as {@link Node} objects
     * @param nrows the number of rows in the resulting dataset
     * @param cont a 2D array of continuous data values
     * @param disc a 2D array of discrete data values
     * @return a {@link DataSet} instance containing the specified data
     */
    public static DataSet fromR(List<Node> vars, int nrows, double[][] cont, int[][] disc) {
        MixedDataBox box = new MixedDataBox(vars, nrows, cont, disc);
        return new BoxDataSet(box, vars);
    }
}

