package edu.cmu.tetrad.comparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.PagAlgorithm;
import edu.cmu.tetrad.util.PatternAlgorithm;

/**
 * Created by jdramsey on 3/24/16.
 */
public class Comparison {


    public Comparison() {

    }

    /**
     * Runs the algorithm on the data set and prints out error statistics.
     * @param dataSet A dataset that is either continuous or discrete.
     * @param algorithm And algorithm appropriate the dataset.
     * @param trueGraph The true graph the algorithm should find the pattern for.
     *                  This should be a DAG.
     */
    public void comparePattern(DataSet dataSet, PatternAlgorithm algorithm, Graph trueGraph) {

    }

    /**
     * Runs the algorithm on the dag makes sure it returns the pattern of the DAG.=
     */
    public void comparePattern(Graph dag, PatternAlgorithm algorithm) {

    }

    /**
     * Runs the algorithm on the data set and prints out error statistics.
     * @param dataSet A dataset that is either continuous or discrete.
     * @param algorithm And algorithm appropriate the dataset.
     * @param trueGraph The true graph the algorithm should find the PAG for.
     *                  This should be a DAG.
     */
    public void comparePag(DataSet dataSet, PagAlgorithm algorithm, Graph trueGraph) {

    }

    /**
     * Runs the algorithm on the dag makes sure it returns the PAG of the given DAG.
     */
    public void comparePag(Graph dag, PatternAlgorithm algorithm) {

    }
}
