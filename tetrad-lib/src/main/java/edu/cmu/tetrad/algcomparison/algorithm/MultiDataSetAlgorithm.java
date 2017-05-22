package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * Implements an algorithm that takes multiple data sets as input.
 *
 * @author jdramsey
 */
public interface MultiDataSetAlgorithm extends Algorithm {

    /**
     * Runs the search.
     *
     * @param dataSet    The data set to run to the search on.
     * @param parameters The paramters of the search.
     * @return The result graph.
     */
    Graph search(List<DataModel> dataSet, Parameters parameters);}
