package edu.pitt.dbmi.algo.resampling.task;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A runnable for a single search over either a single- or multi-data set algorithm, for use in a thread pool.
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * @author josephramsey Cleanup.
 * @version $Id: $Id
 */
public class GeneralResamplingSearchRunnable implements Callable<Graph> {
    /**
     * The parameters for the search.
     */
    private final Parameters parameters;
    /**
     * Whether to print out verbose output.
     */
    private DataModel dataModel;
    /**
     * A list of data models to search over, for multi-data set algorithms.
     */
    private List<DataModel> dataModels = new ArrayList<>();
    /**
     * The algorithm to use for the search, for single-data set algorithms.
     */
    private Algorithm algorithm;
    /**
     * The algorithm to use for the search, for multi-data set algorithms.
     */
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The output stream that output (except for log output) should be sent to.
     */
    private PrintStream out = System.out;
    /**
     * The score wrapper to pass to multi-data set algorithms.
     */
    private ScoreWrapper scoreWrapper = null;
    /**
     * The independence test wrapper to pass to multi-data set algorithms.
     */
    private IndependenceWrapper independenceWrapper = null;
    /**
     * Whether to print out verbose output.
     */
    private boolean verbose = false;

    /**
     * Constructor for single-data set algorithms.
     *
     * @param dataModel  a {@link DataModel} object
     * @param algorithm  a {@link Algorithm} object
     * @param parameters a {@link Parameters} object
     */
    public GeneralResamplingSearchRunnable(DataModel dataModel, Algorithm algorithm, Parameters parameters) {
        if (dataModel == null) throw new NullPointerException("Data model null.");
        if (algorithm == null) throw new NullPointerException("Algorithm null.");
        if (parameters == null) throw new NullPointerException("Parameters null.");

        this.dataModel = dataModel.copy();
        this.algorithm = algorithm;
        this.parameters = parameters;
    }

    /**
     * Constructor for multi-data set algorithms.
     *
     * @param dataModel  a {@link List} object
     * @param algorithm  a {@link MultiDataSetAlgorithm} object
     * @param parameters a {@link Parameters} object
     */
    public GeneralResamplingSearchRunnable(List<DataModel> dataModel, MultiDataSetAlgorithm algorithm, Parameters parameters) {
        if (dataModel == null) throw new NullPointerException("Data model null.");
        if (algorithm == null) throw new NullPointerException("Algorithm null.");
        if (parameters == null) throw new NullPointerException("Parameters null.");

        this.dataModels = dataModel;
        this.multiDataSetAlgorithm = algorithm;
        this.parameters = parameters;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By default, System.out.
     *
     * @param out a {@link java.io.PrintStream} object
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Runs the search over the data model or data models, using the algorithm and parameters.
     *
     * @return The graph discovered by the search.
     */
    @Override
    public Graph call() {
        if (this.verbose) {
            this.out.println("thread started ... ");
        }

        try {
            Graph graph;

            if (this.dataModel != null) {
                if (this.algorithm instanceof HasKnowledge) {
                    ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge);
                    if (this.verbose) {
                        this.out.println("knowledge being set ... ");
                    }
                }

                graph = this.algorithm.search(this.dataModel, this.parameters);
            } else {
                if (this.multiDataSetAlgorithm instanceof HasKnowledge) {
                    ((HasKnowledge) this.multiDataSetAlgorithm).setKnowledge(this.knowledge);
                    if (this.verbose) {
                        this.out.println("knowledge being set ... ");
                    }
                }

                if (scoreWrapper != null) {
                    this.multiDataSetAlgorithm.setScoreWrapper(this.scoreWrapper);
                }

                if (independenceWrapper != null) {
                    this.multiDataSetAlgorithm.setIndTestWrapper(this.independenceWrapper);
                }

                graph = this.multiDataSetAlgorithm.search(this.dataModels, this.parameters);
            }

            return graph;
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage("Exception in bootstrapping runnable: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Sets the score wrapper, for multi-data set algorithms.
     *
     * @param scoreWrapper a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public void setScoreWrapper(ScoreWrapper scoreWrapper) {
        this.scoreWrapper = scoreWrapper;
    }

    /**
     * Sets the independence wrapper, for multi-data set algorithms.
     *
     * @param independenceWrapper a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.independenceWrapper = independenceWrapper;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
