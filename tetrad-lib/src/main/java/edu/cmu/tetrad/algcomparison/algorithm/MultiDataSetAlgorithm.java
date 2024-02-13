package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * Implements an algorithm that takes multiple data sets as input.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface MultiDataSetAlgorithm extends Algorithm {

    /**
     * Runs the search.
     */
    Graph search(List<DataModel> dataSets, Parameters parameters);

    /**
     * Sets a score wrapper if not null.
     *
     * @param score The wrapper
     * @see edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable
     */
    void setScoreWrapper(ScoreWrapper score);

    /**
     * Sets a test wrapper if not null.
     *
     * @param test The wrapper
     * @see edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable
     */
    void setIndTestWrapper(IndependenceWrapper test);
}
