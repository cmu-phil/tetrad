package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

// Not working yet.
public interface PythonCiService extends Closeable {
    void initializeIfNeeded(DataSet data, List<Node> vars, Map<String, Object> params);
    double pValue(int xIndex, int yIndex, int[] zIndices, double alpha) throws InterruptedException;

    default void setVerbose(boolean verbose) {}
    default void updateParams(Map<String, Object> params) {}
}